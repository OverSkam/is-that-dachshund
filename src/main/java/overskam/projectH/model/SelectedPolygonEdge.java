package overskam.projectH.model;

import javafx.geometry.Point2D;

public class SelectedPolygonEdge {

    private final AnnotationPolygon polygon;
    private final int insertIndex;
    private final Point2D imagePoint;

    public SelectedPolygonEdge(AnnotationPolygon polygon, int insertIndex, Point2D imagePoint) {
        this.polygon = polygon;
        this.insertIndex = insertIndex;
        this.imagePoint = imagePoint;
    }

    public AnnotationPolygon getPolygon() {
        return polygon;
    }

    public int getInsertIndex() {
        return insertIndex;
    }

    public Point2D getImagePoint() {
        return imagePoint;
    }
}