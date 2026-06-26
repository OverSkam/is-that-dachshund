package overskam.projectH.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.AnnotationProject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ProjectFileStore {

    private static final String FORMAT = "MedicalVideoAnnotatorProject";
    private static final int VERSION = 1;

    public void save(
            File outputFile,
            AnnotationProject annotationProject,
            List<Point2D> currentPolygonPoints,
            File videoFile,
            String operationId,
            int currentFrameIndex,
            int imageWidth,
            int imageHeight,
            List<String> categories
    ) throws IOException {
        Map<String, Object> project = new LinkedHashMap<>();
        project.put("format", FORMAT);
        project.put("version", VERSION);
        project.put("video_path", videoFile == null ? null : videoFile.getAbsolutePath());
        project.put("operation_id", operationId);
        project.put("current_frame_index", currentFrameIndex);
        project.put("image_width", imageWidth);
        project.put("image_height", imageHeight);
        project.put("categories", categories);
        project.put("current_polygon", buildPoints(currentPolygonPoints));
        project.put("polygons", buildPolygons(annotationProject.getAllPolygons()));
        project.put("problem_frames", annotationProject.getProblemFrameIndexes());
        project.put("frame_quality", annotationProject.getFrameQualities());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        objectMapper.writeValue(outputFile, project);
    }

    public ProjectFileData load(File inputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> project = objectMapper.readValue(
                inputFile,
                new TypeReference<Map<String, Object>>() {}
        );

        String videoPath = getString(project, "video_path", null);
        File videoFile = videoPath == null || videoPath.isBlank() ? null : new File(videoPath);
        String operationId = getString(project, "operation_id", "UNKNOWN_OPERATION");
        int currentFrameIndex = getInt(project, "current_frame_index", 0);
        int imageWidth = getInt(project, "image_width", 0);
        int imageHeight = getInt(project, "image_height", 0);
        List<String> categories = parseCategories(project.get("categories"));
        List<Point2D> currentPolygonPoints = parsePoints(project.get("current_polygon"));
        List<AnnotationPolygon> polygons = parsePolygons(project.get("polygons"));
        Set<Integer> problemFrames = parseIntegerSet(project.get("problem_frames"));
        Map<Integer, String> frameQualities = parseStringMap(project.get("frame_quality"));

        return new ProjectFileData(
                videoFile,
                operationId,
                currentFrameIndex,
                imageWidth,
                imageHeight,
                categories,
                polygons,
                currentPolygonPoints,
                problemFrames,
                frameQualities
        );
    }

    private List<Map<String, Object>> buildPolygons(List<AnnotationPolygon> polygons) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (AnnotationPolygon polygon : polygons) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("frame_index", polygon.getFrameIndex());
            item.put("category", polygon.getCategoryName());
            item.put("confidence", polygon.getConfidence());
            item.put("uncertainty_reason", polygon.getUncertaintyReason());
            item.put("points", buildPoints(polygon.getPoints()));
            result.add(item);
        }

        return result;
    }

    private List<List<Double>> buildPoints(List<Point2D> points) {
        List<List<Double>> result = new ArrayList<>();

        for (Point2D point : points) {
            result.add(List.of(point.getX(), point.getY()));
        }

        return result;
    }

    private List<AnnotationPolygon> parsePolygons(Object value) {
        List<AnnotationPolygon> result = new ArrayList<>();

        if (!(value instanceof List<?> items)) {
            return result;
        }

        for (Object item : items) {
            if (!(item instanceof Map<?, ?> polygonMap)) {
                continue;
            }

            int frameIndex = getInt(polygonMap, "frame_index", -1);
            String category = getString(polygonMap, "category", "");
            String confidence = getString(polygonMap, "confidence", "high");
            String uncertainty = getString(polygonMap, "uncertainty_reason", "none");
            List<Point2D> points = parsePoints(polygonMap.get("points"));

            if (frameIndex >= 0 && !category.isBlank() && points.size() >= 3) {
                result.add(new AnnotationPolygon(frameIndex, points, category, confidence, uncertainty));
            }
        }

        return result;
    }

    private List<Point2D> parsePoints(Object value) {
        List<Point2D> result = new ArrayList<>();

        if (!(value instanceof List<?> points)) {
            return result;
        }

        for (Object pointValue : points) {
            if (!(pointValue instanceof List<?> point) || point.size() < 2) {
                continue;
            }

            Object x = point.get(0);
            Object y = point.get(1);

            if (x instanceof Number xNumber && y instanceof Number yNumber) {
                result.add(new Point2D(xNumber.doubleValue(), yNumber.doubleValue()));
            }
        }

        return result;
    }

    private List<String> parseCategories(Object value) {
        List<String> result = new ArrayList<>();

        if (!(value instanceof List<?> categories)) {
            return result;
        }

        for (Object category : categories) {
            if (category instanceof String text && !text.isBlank()) {
                result.add(text);
            }
        }

        return result;
    }

    private Set<Integer> parseIntegerSet(Object value) {
        Set<Integer> result = new TreeSet<>();

        if (!(value instanceof List<?> items)) {
            return result;
        }

        for (Object item : items) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            }
        }

        return result;
    }

    private Map<Integer, String> parseStringMap(Object value) {
        Map<Integer, String> result = new TreeMap<>();

        if (!(value instanceof Map<?, ?> map)) {
            return result;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            try {
                int frameIndex = Integer.parseInt(String.valueOf(entry.getKey()));
                Object itemValue = entry.getValue();

                if (itemValue instanceof String text && !text.isBlank() && !"ok".equals(text)) {
                    result.put(frameIndex, text);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed frame index in project file.
            }
        }

        return result;
    }

    private int getInt(Map<?, ?> map, String key, int defaultValue) {
        Object value = map.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return defaultValue;
    }

    private String getString(Map<?, ?> map, String key, String defaultValue) {
        Object value = map.get(key);

        if (value instanceof String text) {
            return text;
        }

        return defaultValue;
    }
}