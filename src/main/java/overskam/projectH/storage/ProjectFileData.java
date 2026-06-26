package overskam.projectH.storage;

import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ProjectFileData {

    private final File videoFile;
    private final String operationId;
    private final int currentFrameIndex;
    private final int imageWidth;
    private final int imageHeight;
    private final List<String> categories;
    private final List<AnnotationPolygon> polygons;
    private final List<Point2D> currentPolygonPoints;
    private final Set<Integer> problemFrameIndexes;
    private final Map<Integer, String> frameQualities;

    public ProjectFileData(
            File videoFile,
            String operationId,
            int currentFrameIndex,
            int imageWidth,
            int imageHeight,
            List<String> categories,
            List<AnnotationPolygon> polygons,
            List<Point2D> currentPolygonPoints,
            Set<Integer> problemFrameIndexes,
            Map<Integer, String> frameQualities
    ) {
        this.videoFile = videoFile;
        this.operationId = operationId;
        this.currentFrameIndex = currentFrameIndex;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.categories = new ArrayList<>(categories);
        this.polygons = new ArrayList<>(polygons);
        this.currentPolygonPoints = new ArrayList<>(currentPolygonPoints);
        this.problemFrameIndexes = new LinkedHashSet<>(problemFrameIndexes);
        this.frameQualities = new LinkedHashMap<>(frameQualities);
    }

    public File getVideoFile() {
        return videoFile;
    }

    public String getOperationId() {
        return operationId;
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<AnnotationPolygon> getPolygons() {
        return polygons;
    }

    public List<Point2D> getCurrentPolygonPoints() {
        return currentPolygonPoints;
    }

    public Set<Integer> getProblemFrameIndexes() {
        return problemFrameIndexes;
    }

    public Map<Integer, String> getFrameQualities() {
        return frameQualities;
    }
}