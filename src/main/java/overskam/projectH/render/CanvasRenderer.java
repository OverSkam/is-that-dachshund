package overskam.projectH.render;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.SelectedPolygonPoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CanvasRenderer {
    private static final Color[] CATEGORY_PALETTE = {
            Color.rgb(144, 255, 144),  // 1 light green
            Color.rgb(255, 0, 0),      // 2 red
            Color.rgb(255, 128, 0),    // 3 orange
            Color.rgb(190, 140, 255),  // 4 light purple
            Color.rgb(255, 64, 192),   // 5 pink
            Color.rgb(0, 220, 255),    // 6 light blue
            Color.rgb(0, 64, 255),     // 7 bright blue
            Color.rgb(0, 220, 0),      // 8 green
            Color.rgb(255, 255, 0),    // 9 yellow
            Color.rgb(255, 0, 255)     // 10 magenta
    };

    private final Map<String, Integer> categoryColorIndexes = new LinkedHashMap<>();


    private ImageViewport currentViewport;
    private double zoom = 1.0;
    private double panX;
    private double panY;

    public void redraw(
            GraphicsContext gc,
            double canvasWidth,
            double canvasHeight,
            Image currentImage,
            List<String> categoryNames,
            List<AnnotationPolygon> completedPolygons,
            List<Point2D> currentPolygonPoints,
            AnnotationPolygon selectedPolygon,
            SelectedPolygonPoint hoveredPoint
    ) {
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        updateCategoryColorIndexes(categoryNames);


        if (currentImage != null) {
            drawImageKeepingAspectRatio(gc, canvasWidth, canvasHeight, currentImage);
        } else {
            currentViewport = null;
        }

        drawCompletedPolygons(gc, completedPolygons);
        drawCurrentPolygon(gc, currentPolygonPoints);
        drawSelectedPolygon(gc, selectedPolygon);
        drawHoveredPolygon(gc, hoveredPoint);
        drawHoveredPoint(gc, hoveredPoint);
    }

    private void drawImageKeepingAspectRatio(
            GraphicsContext gc,
            double canvasWidth,
            double canvasHeight,
            Image image
    ) {
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double scale = Math.min(canvasWidth / imageWidth, canvasHeight / imageHeight) * zoom;
        double drawWidth = imageWidth * scale;
        double drawHeight = imageHeight * scale;
        double drawX = (canvasWidth - drawWidth) / 2 + panX;
        double drawY = (canvasHeight - drawHeight) / 2 + panY;

        currentViewport = new ImageViewport(
                imageWidth,
                imageHeight,
                drawX,
                drawY,
                drawWidth,
                drawHeight
        );

        gc.drawImage(image, drawX, drawY, drawWidth, drawHeight);
    }

    private void drawCompletedPolygons(GraphicsContext gc, List<AnnotationPolygon> completedPolygons) {
        for (AnnotationPolygon polygon : completedPolygons) {
            Color polygonColor = getColorForCategory(polygon.getCategoryName());
            drawClosedPolygon(gc, polygon.getPoints(), polygonColor, 2);
            drawPoints(gc, polygon.getPoints(), polygonColor);
            drawCategoryLabel(gc, polygon.getCategoryName(), polygon.getPoints(), polygonColor);
        }
    }

    private void drawCurrentPolygon(GraphicsContext gc, List<Point2D> currentPolygonPoints) {
        if (currentViewport == null) {
            return;
        }

        gc.setStroke(Color.LIME);
        gc.setLineWidth(2);

        for (int i = 0; i < currentPolygonPoints.size() - 1; i++) {
            Point2D currentPoint = currentViewport.imageToCanvas(currentPolygonPoints.get(i));
            Point2D nextPoint = currentViewport.imageToCanvas(currentPolygonPoints.get(i + 1));
            gc.strokeLine(currentPoint.getX(), currentPoint.getY(), nextPoint.getX(), nextPoint.getY());
        }

        drawPoints(gc, currentPolygonPoints, Color.YELLOW);
    }

    private void drawSelectedPolygon(GraphicsContext gc, AnnotationPolygon selectedPolygon) {
        if (selectedPolygon == null) {
            return;
        }

        drawClosedPolygon(gc, selectedPolygon.getPoints(), Color.rgb(255, 230, 0, 0.9), 2);
    }

    private void drawHoveredPolygon(GraphicsContext gc, SelectedPolygonPoint hoveredPoint) {
        if (hoveredPoint == null) {
            return;
        }

        drawClosedPolygon(gc, hoveredPoint.getPolygon().getPoints(), Color.rgb(255, 255, 255, 0.7), 1.5);
    }

    private void drawClosedPolygon(
            GraphicsContext gc,
            List<Point2D> points,
            Color color,
            double lineWidth
    ) {
        if (currentViewport == null || points.size() < 2) {
            return;
        }

        gc.setStroke(color);
        gc.setLineWidth(lineWidth);

        for (int i = 0; i < points.size(); i++) {
            Point2D currentPoint = currentViewport.imageToCanvas(points.get(i));
            Point2D nextPoint = currentViewport.imageToCanvas(points.get((i + 1) % points.size()));
            gc.strokeLine(currentPoint.getX(), currentPoint.getY(), nextPoint.getX(), nextPoint.getY());
        }
    }

    private void drawPoints(GraphicsContext gc, List<Point2D> points, Color color) {
        if (currentViewport == null) {
            return;
        }

        gc.setFill(color);

        for (Point2D imagePoint : points) {
            Point2D point = currentViewport.imageToCanvas(imagePoint);
            double radius = 4;
            gc.fillOval(point.getX() - radius, point.getY() - radius, radius * 2, radius * 2);
        }
    }

    private void drawCategoryLabel(
            GraphicsContext gc,
            String categoryName,
            List<Point2D> points,
            Color color
    ) {
        if (currentViewport == null || categoryName == null || points.isEmpty()) {
            return;
        }

        Point2D firstPoint = currentViewport.imageToCanvas(points.getFirst());
        gc.setFont(Font.font(14));
        gc.setFill(color);
        gc.fillText(categoryName, firstPoint.getX() + 6, firstPoint.getY() - 6);
    }

    private void drawHoveredPoint(GraphicsContext gc, SelectedPolygonPoint hoveredPoint) {
        if (hoveredPoint == null || currentViewport == null) {
            return;
        }

        List<Point2D> points = hoveredPoint.getPolygon().getPoints();
        int pointIndex = hoveredPoint.getPointIndex();

        if (pointIndex < 0 || pointIndex >= points.size()) {
            return;
        }

        Point2D canvasPoint = currentViewport.imageToCanvas(points.get(pointIndex));
        double radius = 7;

        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1.5);
        gc.strokeOval(
                canvasPoint.getX() - radius,
                canvasPoint.getY() - radius,
                radius * 2,
                radius * 2
        );
    }

    private void updateCategoryColorIndexes(List<String> categoryNames) {
        categoryColorIndexes.clear();

        if (categoryNames == null) {
            return;
        }

        for (String categoryName : categoryNames) {
            if (categoryName == null || categoryName.isBlank() || categoryColorIndexes.containsKey(categoryName)) {
                continue;
            }

            categoryColorIndexes.put(categoryName, categoryColorIndexes.size());
        }
    }

    private Color getColorForCategory(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return CATEGORY_PALETTE[0];
        }

        Integer colorIndex = categoryColorIndexes.get(categoryName);

        if (colorIndex == null) {
            colorIndex = categoryColorIndexes.size();
            categoryColorIndexes.put(categoryName, colorIndex);
        }

        return CATEGORY_PALETTE[colorIndex % CATEGORY_PALETTE.length];
    }

    private void clampPan(double canvasWidth, double canvasHeight, double drawWidth, double drawHeight) {
        if (zoom <= 1.0001) {
            panX = 0;
            panY = 0;
            return;
        }

        panX = clampAxisPan(panX, canvasWidth, drawWidth);
        panY = clampAxisPan(panY, canvasHeight, drawHeight);
    }

    private double clampAxisPan(double pan, double canvasSize, double drawSize) {
        if (drawSize <= canvasSize) {
            return 0;
        }

        double maxPan = (drawSize - canvasSize) / 2;
        return Math.max(-maxPan, Math.min(maxPan, pan));
    }
    public void zoomAt(double canvasX, double canvasY, double factor) {
        double oldZoom = zoom;
        zoom = Math.max(1.0, Math.min(8.0, zoom * factor));

        if (Math.abs(zoom - oldZoom) < 0.0001) {
            return;
        }

        if (zoom <= 1.0001) {
            resetView();
            return;
        }

        double zoomRatio = zoom / oldZoom;
        panX *= zoomRatio;
        panY *= zoomRatio;
    }

    public void pan(double deltaX, double deltaY) {
        if (zoom <= 1.0) {
            return;
        }

        panX += deltaX;
        panY += deltaY;
    }

    public void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
    }

    public double getZoom() {
        return zoom;
    }

    public ImageViewport getCurrentViewport() {
        return currentViewport;
    }
}
