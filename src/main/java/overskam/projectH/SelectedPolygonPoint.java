package overskam.projectH;

public class SelectedPolygonPoint {

    private final AnnotationPolygon polygon;
    private final int pointIndex;

    public SelectedPolygonPoint(AnnotationPolygon polygon, int pointIndex) {
        this.polygon = polygon;
        this.pointIndex = pointIndex;
    }

    public AnnotationPolygon getPolygon() {
        return polygon;
    }

    public int getPointIndex() {
        return pointIndex;
    }
}