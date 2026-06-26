package overskam.projectH.model;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class AnnotationProject {

    private final List<AnnotationPolygon> polygons = new ArrayList<>();
    private final Set<Integer> problemFrameIndexes = new TreeSet<>();
    private final Map<Integer, String> frameQualities = new TreeMap<>();

    public void addPolygon(AnnotationPolygon polygon) {
        polygons.add(polygon);
    }

    public void addAllPolygons(List<AnnotationPolygon> importedPolygons) {
        polygons.addAll(importedPolygons);
    }

    public void clear() {
        polygons.clear();
        problemFrameIndexes.clear();
        frameQualities.clear();
    }

    public List<AnnotationPolygon> getPolygonsForFrame(int frameIndex) {
        List<AnnotationPolygon> result = new ArrayList<>();

        for (AnnotationPolygon polygon : polygons) {
            if (polygon.getFrameIndex() == frameIndex) {
                result.add(polygon);
            }
        }

        return result;
    }

    public List<AnnotationPolygon> getAllPolygons() {
        return Collections.unmodifiableList(polygons);
    }

    public int getPolygonCount() {
        return polygons.size();
    }

    public int getPolygonCountForFrame(int frameIndex) {
        int count = 0;

        for (AnnotationPolygon polygon : polygons) {
            if (polygon.getFrameIndex() == frameIndex) {
                count++;
            }
        }

        return count;
    }

    public int getAnnotatedFrameCount() {
        return getAnnotatedFrameIndexes().size();
    }

    public boolean removeLastPolygonForFrame(int frameIndex) {
        for (int i = polygons.size() - 1; i >= 0; i--) {
            AnnotationPolygon polygon = polygons.get(i);

            if (polygon.getFrameIndex() == frameIndex) {
                polygons.remove(i);
                return true;
            }
        }

        return false;
    }


    public AnnotationPolygon removeLastPolygonForFrameAndReturn(int frameIndex) {
        for (int i = polygons.size() - 1; i >= 0; i--) {
            AnnotationPolygon polygon = polygons.get(i);

            if (polygon.getFrameIndex() == frameIndex) {
                polygons.remove(i);
                return polygon;
            }
        }

        return null;
    }
    public int removePolygonsForFrame(int frameIndex) {
        int polygonCountBefore = polygons.size();
        polygons.removeIf(polygon -> polygon.getFrameIndex() == frameIndex);
        return polygonCountBefore - polygons.size();
    }
    public void removePolygon(AnnotationPolygon polygon) {
        polygons.remove(polygon);
    }


    public boolean toggleProblemFrame(int frameIndex) {
        if (problemFrameIndexes.contains(frameIndex)) {
            problemFrameIndexes.remove(frameIndex);
            return false;
        }

        problemFrameIndexes.add(frameIndex);
        return true;
    }

    public void setProblemFrame(int frameIndex, boolean problemFrame) {
        if (problemFrame) {
            problemFrameIndexes.add(frameIndex);
        } else {
            problemFrameIndexes.remove(frameIndex);
        }
    }

    public boolean isProblemFrame(int frameIndex) {
        return problemFrameIndexes.contains(frameIndex);
    }

    public Set<Integer> getProblemFrameIndexes() {
        return Collections.unmodifiableSet(problemFrameIndexes);
    }

    public void setFrameQuality(int frameIndex, String quality) {
        if (quality == null || quality.isBlank() || "ok".equals(quality)) {
            frameQualities.remove(frameIndex);
            return;
        }

        frameQualities.put(frameIndex, quality);
    }

    public String getFrameQuality(int frameIndex) {
        return frameQualities.getOrDefault(frameIndex, "ok");
    }

    public Map<Integer, String> getFrameQualities() {
        return Collections.unmodifiableMap(frameQualities);
    }

    public void setFrameMetadata(Set<Integer> problemFrames, Map<Integer, String> qualities) {
        problemFrameIndexes.clear();
        problemFrameIndexes.addAll(problemFrames);
        frameQualities.clear();
        frameQualities.putAll(qualities);
    }
    public List<Integer> getAnnotatedFrameIndexes() {
        List<Integer> frameIndexes = new ArrayList<>();

        for (AnnotationPolygon polygon : polygons) {
            int frameIndex = polygon.getFrameIndex();

            if (!frameIndexes.contains(frameIndex)) {
                frameIndexes.add(frameIndex);
            }
        }

        Collections.sort(frameIndexes);

        return frameIndexes;
    }

    public SelectedPolygonEdge findNearestEdge(
            int frameIndex,
            Point2D imagePoint,
            double maxDistance
    ) {
        SelectedPolygonEdge nearestEdge = null;
        double nearestDistance = maxDistance;

        for (AnnotationPolygon polygon : polygons) {
            if (polygon.getFrameIndex() != frameIndex) {
                continue;
            }

            List<Point2D> points = polygon.getPoints();

            if (points.size() < 2) {
                continue;
            }

            for (int i = 0; i < points.size(); i++) {
                Point2D start = points.get(i);
                Point2D end = points.get((i + 1) % points.size());
                Point2D projectedPoint = projectPointOnSegment(imagePoint, start, end);
                double distance = projectedPoint.distance(imagePoint);

                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    int insertIndex = i + 1;
                    nearestEdge = new SelectedPolygonEdge(polygon, insertIndex, projectedPoint);
                }
            }
        }

        return nearestEdge;
    }

    private Point2D projectPointOnSegment(Point2D point, Point2D start, Point2D end) {
        double deltaX = end.getX() - start.getX();
        double deltaY = end.getY() - start.getY();
        double segmentLengthSquared = deltaX * deltaX + deltaY * deltaY;

        if (segmentLengthSquared == 0) {
            return start;
        }

        double projection = ((point.getX() - start.getX()) * deltaX
                + (point.getY() - start.getY()) * deltaY) / segmentLengthSquared;
        double clampedProjection = Math.max(0, Math.min(1, projection));

        return new Point2D(
                start.getX() + clampedProjection * deltaX,
                start.getY() + clampedProjection * deltaY
        );
    }
    public SelectedPolygonPoint findNearestPoint(
            int frameIndex,
            Point2D imagePoint,
            double maxDistance
    ) {
        SelectedPolygonPoint nearestPoint = null;
        double nearestDistance = maxDistance;

        for (AnnotationPolygon polygon : polygons) {
            if (polygon.getFrameIndex() != frameIndex) {
                continue;
            }

            List<Point2D> points = polygon.getPoints();

            for (int i = 0; i < points.size(); i++) {
                Point2D point = points.get(i);
                double distance = point.distance(imagePoint);

                if (distance <= nearestDistance) {
                    nearestDistance = distance;
                    nearestPoint = new SelectedPolygonPoint(polygon, i);
                }
            }
        }

        return nearestPoint;
    }
}