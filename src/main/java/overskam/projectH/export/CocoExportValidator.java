package overskam.projectH.export;

import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.AnnotationProject;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CocoExportValidator {

    private static final double MIN_POLYGON_AREA = 10.0;

    public CocoExportValidationResult validate(
            AnnotationProject annotationProject,
            int imageWidth,
            int imageHeight,
            List<String> categoryNames,
            boolean hasOpenPolygon
    ) {
        CocoExportValidationResult result = new CocoExportValidationResult();

        if (imageWidth <= 0 || imageHeight <= 0) {
            result.addError("Image size is missing or invalid.");
        }

        if (annotationProject.getAllPolygons().isEmpty()) {
            result.addError("No completed polygons to export.");
        }

        if (categoryNames == null || categoryNames.isEmpty()) {
            result.addError("No categories are available for export.");
        }

        Set<String> knownCategories = buildKnownCategories(categoryNames);
        Set<Integer> frameIndexes = new HashSet<>(annotationProject.getAnnotatedFrameIndexes());
        int polygonNumber = 1;

        for (AnnotationPolygon polygon : annotationProject.getAllPolygons()) {
            validatePolygon(result, polygon, polygonNumber, imageWidth, imageHeight, knownCategories, frameIndexes);
            polygonNumber++;
        }

        if (hasOpenPolygon) {
            result.addWarning("Current polygon is not closed and will not be exported.");
        }

        return result;
    }

    private Set<String> buildKnownCategories(List<String> categoryNames) {
        Set<String> knownCategories = new HashSet<>();

        if (categoryNames == null) {
            return knownCategories;
        }

        for (String categoryName : categoryNames) {
            if (categoryName != null && !categoryName.isBlank()) {
                knownCategories.add(categoryName);
            }
        }

        return knownCategories;
    }

    private void validatePolygon(
            CocoExportValidationResult result,
            AnnotationPolygon polygon,
            int polygonNumber,
            int imageWidth,
            int imageHeight,
            Set<String> knownCategories,
            Set<Integer> frameIndexes
    ) {
        String label = "Polygon " + polygonNumber + " on frame " + polygon.getFrameIndex();

        if (polygon.getFrameIndex() < 0) {
            result.addError(label + " has a negative frame index.");
        }

        if (!frameIndexes.contains(polygon.getFrameIndex())) {
            result.addError(label + " has no matching COCO image entry.");
        }

        String categoryName = polygon.getCategoryName();
        if (categoryName == null || categoryName.isBlank()) {
            result.addError(label + " has an empty category.");
        } else if (!knownCategories.contains(categoryName)) {
            result.addWarning(label + " uses category '" + categoryName + "' that is not in the current category list.");
        }

        List<Point2D> points = polygon.getPoints();
        if (points.size() < 3) {
            result.addError(label + " has fewer than 3 points.");
            return;
        }

        for (int i = 0; i < points.size(); i++) {
            Point2D point = points.get(i);
            validatePoint(result, label, point, i, imageWidth, imageHeight);
        }

        double area = calculatePolygonArea(points);
        if (area <= 0) {
            result.addError(label + " has zero polygon area.");
        } else if (area < MIN_POLYGON_AREA) {
            result.addWarning(label + " is very small: area " + String.format("%.2f", area) + ".");
        }

        double[] bbox = calculateBoundingBox(points);
        if (bbox[2] <= 0 || bbox[3] <= 0) {
            result.addError(label + " has an invalid bounding box.");
        }

        if (hasSelfIntersection(points)) {
            result.addWarning(label + " appears to self-intersect.");
        }
    }

    private void validatePoint(
            CocoExportValidationResult result,
            String label,
            Point2D point,
            int pointIndex,
            int imageWidth,
            int imageHeight
    ) {
        if (!Double.isFinite(point.getX()) || !Double.isFinite(point.getY())) {
            result.addError(label + " has a non-finite point at index " + pointIndex + ".");
            return;
        }

        if (point.getX() < 0 || point.getY() < 0 || point.getX() > imageWidth || point.getY() > imageHeight) {
            result.addWarning(label + " has a point outside the image at index " + pointIndex + ".");
        }
    }

    private double calculatePolygonArea(List<Point2D> points) {
        double sum = 0;

        for (int i = 0; i < points.size(); i++) {
            Point2D current = points.get(i);
            Point2D next = points.get((i + 1) % points.size());
            sum += current.getX() * next.getY();
            sum -= next.getX() * current.getY();
        }

        return Math.abs(sum) / 2.0;
    }

    private double[] calculateBoundingBox(List<Point2D> points) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point2D point : points) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        return new double[]{minX, minY, maxX - minX, maxY - minY};
    }

    private boolean hasSelfIntersection(List<Point2D> points) {
        for (int i = 0; i < points.size(); i++) {
            Point2D a1 = points.get(i);
            Point2D a2 = points.get((i + 1) % points.size());

            for (int j = i + 1; j < points.size(); j++) {
                if (Math.abs(i - j) <= 1 || i == 0 && j == points.size() - 1) {
                    continue;
                }

                Point2D b1 = points.get(j);
                Point2D b2 = points.get((j + 1) % points.size());

                if (segmentsIntersect(a1, a2, b1, b2)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean segmentsIntersect(Point2D a1, Point2D a2, Point2D b1, Point2D b2) {
        double d1 = direction(b1, b2, a1);
        double d2 = direction(b1, b2, a2);
        double d3 = direction(a1, a2, b1);
        double d4 = direction(a1, a2, b2);

        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
    }

    private double direction(Point2D a, Point2D b, Point2D c) {
        return (c.getX() - a.getX()) * (b.getY() - a.getY())
                - (b.getX() - a.getX()) * (c.getY() - a.getY());
    }
}
