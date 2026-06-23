package overskam.projectH.export;

import overskam.projectH.model.AnnotationPolygon;

import java.util.List;

public class CocoImportResult {

    private final List<AnnotationPolygon> polygons;
    private final String operationId;
    private final int imageWidth;
    private final int imageHeight;

    public CocoImportResult(
            List<AnnotationPolygon> polygons,
            String operationId,
            int imageWidth,
            int imageHeight
    ) {
        this.polygons = polygons;
        this.operationId = operationId;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
    }

    public List<AnnotationPolygon> getPolygons() {
        return polygons;
    }

    public String getOperationId() {
        return operationId;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }
}