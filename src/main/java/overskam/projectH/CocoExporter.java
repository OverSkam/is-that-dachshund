package overskam.projectH;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.geometry.Point2D;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CocoExporter {

    private static final Map<String, Integer> CATEGORY_IDS = Map.of(
            "gallbladder", 1,
            "cystic_duct", 2,
            "cystic_artery", 3,
            "liver", 4,
            "instrument", 5
    );

    public void export(
            AnnotationProject annotationProject,
            File outputFile,
            String operationId,
            int imageWidth,
            int imageHeight
    ) throws IOException {
        Map<String, Object> coco = new LinkedHashMap<>();

        Map<Integer, Integer> frameIndexToImageId = buildFrameIndexToImageId(annotationProject);

        coco.put("info", buildInfo());
        coco.put("licenses", List.of());
        coco.put("images", buildImages(
                annotationProject,
                operationId,
                imageWidth,
                imageHeight,
                frameIndexToImageId
        ));
        coco.put("annotations", buildAnnotations(annotationProject, frameIndexToImageId));
        coco.put("categories", buildCategories());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(outputFile, coco);
    }

    private Map<String, Object> buildInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("description", "Laparoscopic cholecystectomy segmentation dataset");
        info.put("version", "1.0");
        return info;
    }

    private Map<Integer, Integer> buildFrameIndexToImageId(AnnotationProject annotationProject) {
        List<Integer> frameIndexes = annotationProject.getAnnotatedFrameIndexes();

        Map<Integer, Integer> result = new LinkedHashMap<>();

        int imageId = 1;

        for (int frameIndex : frameIndexes) {
            result.put(frameIndex, imageId);
            imageId++;
        }

        return result;
    }

    private List<Map<String, Object>> buildImages(
            AnnotationProject annotationProject,
            String operationId,
            int imageWidth,
            int imageHeight,
            Map<Integer, Integer> frameIndexToImageId
    ) {
        List<Map<String, Object>> images = new ArrayList<>();

        for (int frameIndex : annotationProject.getAnnotatedFrameIndexes()) {
            Map<String, Object> image = new LinkedHashMap<>();

            image.put("id", frameIndexToImageId.get(frameIndex));
            image.put("file_name", buildFrameFileName(operationId, frameIndex));
            image.put("width", imageWidth);
            image.put("height", imageHeight);
            image.put("operation_id", operationId);
            image.put("frame_index", frameIndex);

            images.add(image);
        }

        return images;
    }

    private String buildFrameFileName(String operationId, int frameIndex) {
        return String.format("frames/%s_frame_%07d.jpg", operationId, frameIndex);
    }

    private List<Map<String, Object>> buildAnnotations(
            AnnotationProject annotationProject,
            Map<Integer, Integer> frameIndexToImageId
    ) {
        List<Map<String, Object>> annotations = new ArrayList<>();

        int annotationId = 1;

        for (AnnotationPolygon polygon : annotationProject.getAllPolygons()) {
            Integer imageId = frameIndexToImageId.get(polygon.getFrameIndex());

            if (imageId == null) {
                continue;
            }

            Map<String, Object> annotation = new LinkedHashMap<>();

            annotation.put("id", annotationId);
            annotation.put("image_id", imageId);
            annotation.put("category_id", CATEGORY_IDS.get(polygon.getCategoryName()));
            annotation.put("segmentation", List.of(buildSegmentation(polygon)));
            annotation.put("area", calculatePolygonArea(polygon));
            annotation.put("bbox", calculateBoundingBox(polygon));
            annotation.put("iscrowd", 0);
            annotation.put("confidence", polygon.getConfidence());
            annotation.put("uncertainty_reason", polygon.getUncertaintyReason());

            annotations.add(annotation);
            annotationId++;
        }

        return annotations;
    }

    private List<Double> buildSegmentation(AnnotationPolygon polygon) {
        List<Double> segmentation = new ArrayList<>();

        for (Point2D point : polygon.getPoints()) {
            segmentation.add(point.getX());
            segmentation.add(point.getY());
        }

        return segmentation;
    }

    private double calculatePolygonArea(AnnotationPolygon polygon) {
        List<Point2D> points = polygon.getPoints();

        if (points.size() < 3) {
            return 0;
        }

        double sum = 0;

        for (int i = 0; i < points.size(); i++) {
            Point2D current = points.get(i);
            Point2D next = points.get((i + 1) % points.size());

            sum += current.getX() * next.getY();
            sum -= next.getX() * current.getY();
        }

        return Math.abs(sum) / 2.0;
    }

    private List<Double> calculateBoundingBox(AnnotationPolygon polygon) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (Point2D point : polygon.getPoints()) {
            minX = Math.min(minX, point.getX());
            minY = Math.min(minY, point.getY());
            maxX = Math.max(maxX, point.getX());
            maxY = Math.max(maxY, point.getY());
        }

        return List.of(
                minX,
                minY,
                maxX - minX,
                maxY - minY
        );
    }

    private List<Map<String, Object>> buildCategories() {
        List<Map<String, Object>> categories = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : CATEGORY_IDS.entrySet()) {
            Map<String, Object> category = new LinkedHashMap<>();
            category.put("id", entry.getValue());
            category.put("name", entry.getKey());
            category.put("supercategory", "medical");

            categories.add(category);
        }

        categories.sort(Comparator.comparing(category -> (Integer) category.get("id")));

        return categories;
    }
}