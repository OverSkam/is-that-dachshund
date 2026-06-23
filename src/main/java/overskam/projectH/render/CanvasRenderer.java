package overskam.projectH.render;

import javafx.geometry.Point2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.SelectedPolygonPoint;

import java.util.List;

public class CanvasRenderer {

    private ImageViewport currentViewport;

    public void redraw(
            GraphicsContext gc,
            double canvasWidth,
            double canvasHeight,
            Image currentImage,
            List<AnnotationPolygon> completedPolygons,
            List<Point2D> currentPolygonPoints,
            AnnotationPolygon selectedPolygon,
            SelectedPolygonPoint hoveredPoint
    ) {
        gc.clearRect(0, 0, canvasWidth, canvasHeight);

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
        double scale = Math.min(canvasWidth / imageWidth, canvasHeight / imageHeight);
        double drawWidth = imageWidth * scale;
        double drawHeight = imageHeight * scale;
        double drawX = (canvasWidth - drawWidth) / 2;
        double drawY = (canvasHeight - drawHeight) / 2;

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

    private Color getColorForCategory(String categoryName) {
        if ("gallbladder".equals(categoryName)) {
            return Color.LIME;
        }
        if ("cystic_duct".equals(categoryName)) {
            return Color.CYAN;
        }
        if ("cystic_artery".equals(categoryName)) {
            return Color.RED;
        }
        if ("liver".equals(categoryName)) {
            return Color.ORANGE;
        }
        if ("instrument".equals(categoryName)) {
            return Color.WHITE;
        }
        return Color.YELLOW;
    }

    public ImageViewport getCurrentViewport() {
        return currentViewport;
    }
}