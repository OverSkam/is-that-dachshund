package overskam.projectH.model;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class AnnotationPolygon {

    private final int frameIndex;
    private final List<Point2D> points;
    private final String categoryName;
    private final String confidence;
    private final String uncertaintyReason;

    public AnnotationPolygon(
            int frameIndex,
            List<Point2D> points,
            String categoryName,
            String confidence,
            String uncertaintyReason
    ) {
        this.frameIndex = frameIndex;
        this.points = new ArrayList<>(points);
        this.categoryName = categoryName;
        this.confidence = confidence;
        this.uncertaintyReason = uncertaintyReason;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public List<Point2D> getPoints() {
        return points;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getConfidence() {
        return confidence;
    }

    public String getUncertaintyReason() {
        return uncertaintyReason;
    }

    public void setPoint(int index, Point2D point) {
        points.set(index, point);
    }

    public void removePoint(int index) {
        points.remove(index);
    }

    public int getPointCount() {
        return points.size();
    }
}
