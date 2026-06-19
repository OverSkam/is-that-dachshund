package overskam.projectH;

import javafx.geometry.Point2D;

import java.util.ArrayList;
import java.util.List;

public class AnnotationPolygon {

    private final int frameIndex;
    private final List<Point2D> points;
    private final String categoryName;

    public AnnotationPolygon(int frameIndex, List<Point2D> points, String categoryName) {
        this.frameIndex = frameIndex;
        this.points = new ArrayList<>(points);
        this.categoryName = categoryName;
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
}