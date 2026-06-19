package overskam.projectH.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CocoImporter {

    private static final Map<Integer, String> CATEGORY_NAMES = Map.of(
            1, "gallbladder",
            2, "cystic_duct",
            3, "cystic_artery",
            4, "liver",
            5, "instrument"
    );

    public List<AnnotationPolygon> importPolygons(File inputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> coco = objectMapper.readValue(
                inputFile,
                new TypeReference<Map<String, Object>>() {}
        );

        Map<Integer, Integer> imageIdToFrameIndex = buildImageIdToFrameIndex(coco);
        List<Map<String, Object>> annotations = getList(coco, "annotations");

        List<AnnotationPolygon> polygons = new ArrayList<>();

        for (Map<String, Object> annotation : annotations) {
            AnnotationPolygon polygon = parseAnnotation(annotation, imageIdToFrameIndex);

            if (polygon != null) {
                polygons.add(polygon);
            }
        }

        return polygons;
    }

    private Map<Integer, Integer> buildImageIdToFrameIndex(Map<String, Object> coco) {
        List<Map<String, Object>> images = getList(coco, "images");
        Map<Integer, Integer> result = new HashMap<>();

        for (Map<String, Object> image : images) {
            Integer imageId = getInt(image, "id");
            Integer frameIndex = getInt(image, "frame_index");

            if (imageId != null && frameIndex != null) {
                result.put(imageId, frameIndex);
            }
        }

        return result;
    }

    private AnnotationPolygon parseAnnotation(
            Map<String, Object> annotation,
            Map<Integer, Integer> imageIdToFrameIndex
    ) {
        Integer imageId = getInt(annotation, "image_id");
        Integer categoryId = getInt(annotation, "category_id");

        if (imageId == null || categoryId == null) {
            return null;
        }

        Integer frameIndex = imageIdToFrameIndex.get(imageId);
        String categoryName = CATEGORY_NAMES.get(categoryId);

        if (frameIndex == null || categoryName == null) {
            return null;
        }

        List<Point2D> points = parseSegmentation(annotation);

        if (points.size() < 3) {
            return null;
        }

        String confidence = getString(annotation, "confidence", "high");
        String uncertaintyReason = getString(annotation, "uncertainty_reason", "none");

        return new AnnotationPolygon(
                frameIndex,
                points,
                categoryName,
                confidence,
                uncertaintyReason
        );
    }

    private List<Point2D> parseSegmentation(Map<String, Object> annotation) {
        Object segmentationObject = annotation.get("segmentation");

        if (!(segmentationObject instanceof List<?> segmentationList) || segmentationList.isEmpty()) {
            return Collections.emptyList();
        }

        Object firstPolygonObject = segmentationList.getFirst();

        if (!(firstPolygonObject instanceof List<?> coordinateList)) {
            return Collections.emptyList();
        }

        List<Point2D> points = new ArrayList<>();

        for (int i = 0; i < coordinateList.size() - 1; i += 2) {
            Object xObject = coordinateList.get(i);
            Object yObject = coordinateList.get(i + 1);

            if (xObject instanceof Number xNumber && yObject instanceof Number yNumber) {
                points.add(new Point2D(
                        xNumber.doubleValue(),
                        yNumber.doubleValue()
                ));
            }
        }

        return points;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(Map<String, Object> map, String key) {
        Object value = map.get(key);

        if (value instanceof List<?>) {
            return (List<Map<String, Object>>) value;
        }

        return Collections.emptyList();
    }

    private Integer getInt(Map<String, Object> map, String key) {
        Object value = map.get(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);

        if (value instanceof String text && !text.isBlank()) {
            return text;
        }

        return defaultValue;
    }
}