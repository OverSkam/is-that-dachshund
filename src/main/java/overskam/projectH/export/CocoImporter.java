package overskam.projectH.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.CategoryStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CocoImporter {

    public CocoImportResult importProject(File inputFile) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> coco = objectMapper.readValue(
                inputFile,
                new TypeReference<Map<String, Object>>() {}
        );

        Map<Integer, Integer> imageIdToFrameIndex = buildImageIdToFrameIndex(coco);
        Map<Integer, String> categoryNames = buildCategoryNames(coco);
        List<Map<String, Object>> annotations = getList(coco, "annotations");
        List<AnnotationPolygon> polygons = new ArrayList<>();

        for (Map<String, Object> annotation : annotations) {
            AnnotationPolygon polygon = parseAnnotation(annotation, imageIdToFrameIndex, categoryNames);

            if (polygon != null) {
                polygons.add(polygon);
            }
        }

        List<Map<String, Object>> images = getList(coco, "images");
        String operationId = "UNKNOWN_OPERATION";
        int imageWidth = 0;
        int imageHeight = 0;

        if (!images.isEmpty()) {
            Map<String, Object> firstImage = images.get(0);

            operationId = getString(firstImage, "operation_id", operationId);

            if ("UNKNOWN_OPERATION".equals(operationId)) {
                operationId = inferOperationIdFromFileName(getString(firstImage, "file_name", ""));
            }

            Integer width = getInt(firstImage, "width");
            Integer height = getInt(firstImage, "height");

            if (width != null) {
                imageWidth = width;
            }

            if (height != null) {
                imageHeight = height;
            }
        }

        return new CocoImportResult(
                polygons,
                operationId,
                imageWidth,
                imageHeight
        );
    }

    private Map<Integer, Integer> buildImageIdToFrameIndex(Map<String, Object> coco) {
        List<Map<String, Object>> images = getList(coco, "images");
        Map<Integer, Integer> result = new HashMap<>();

        for (Map<String, Object> image : images) {
            Integer imageId = getInt(image, "id");
            Integer frameIndex = getInt(image, "frame_index");

            if (imageId != null) {
                if (frameIndex == null) {
                    frameIndex = imageId;
                }

                result.put(imageId, frameIndex);
            }
        }

        return result;
    }

    private Map<Integer, String> buildCategoryNames(Map<String, Object> coco) {
        List<Map<String, Object>> categories = getList(coco, "categories");
        Map<Integer, String> result = new HashMap<>();

        for (Map<String, Object> category : categories) {
            Integer categoryId = getInt(category, "id");
            String categoryName = getString(category, "name", "");

            if (categoryId != null && !categoryName.isBlank()) {
                result.put(categoryId, categoryName);
            }
        }

        if (result.isEmpty()) {
            int categoryId = 1;

            for (String defaultCategory : CategoryStore.DEFAULT_CATEGORIES) {
                result.put(categoryId, defaultCategory);
                categoryId++;
            }
        }

        return result;
    }

    private AnnotationPolygon parseAnnotation(
            Map<String, Object> annotation,
            Map<Integer, Integer> imageIdToFrameIndex,
            Map<Integer, String> categoryNames
    ) {
        Integer imageId = getInt(annotation, "image_id");
        Integer categoryId = getInt(annotation, "category_id");

        if (imageId == null || categoryId == null) {
            return null;
        }

        Integer frameIndex = imageIdToFrameIndex.get(imageId);
        String categoryName = categoryNames.get(categoryId);

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

    private String inferOperationIdFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "UNKNOWN_OPERATION";
        }

        String normalized = fileName.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');

        if (slashIndex >= 0) {
            normalized = normalized.substring(slashIndex + 1);
        }

        int frameMarkerIndex = normalized.indexOf("_frame_");

        if (frameMarkerIndex > 0) {
            return normalized.substring(0, frameMarkerIndex);
        }

        int dotIndex = normalized.lastIndexOf('.');

        if (dotIndex > 0) {
            return normalized.substring(0, dotIndex);
        }

        return normalized;
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