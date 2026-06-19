package overskam.projectH;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationProject {

    private final List<AnnotationPolygon> polygons = new ArrayList<>();

    public void addPolygon(AnnotationPolygon polygon) {
        polygons.add(polygon);
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

    public List<AnnotationPolygon> getAllPolygons() {
        return Collections.unmodifiableList(polygons);
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

    public void removePolygon(AnnotationPolygon polygon) {
        polygons.remove(polygon);
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

}