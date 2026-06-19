package overskam.projectH.render;

import javafx.geometry.Point2D;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.SelectedPolygonPoint;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

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
            SelectedPolygonPoint hoveredPoint
    ) {
        gc.clearRect(0, 0, canvasWidth, canvasHeight);

        if (currentImage != null)
            drawImageKeepingAspectRatio(gc, canvasWidth, canvasHeight, currentImage);
        else
            currentViewport = null;

        drawCompletedPolygons(gc, completedPolygons);
        drawCurrentPolygon(gc, currentPolygonPoints);
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

        double scaleX = canvasWidth / imageWidth;
        double scaleY = canvasHeight / imageHeight;
        double scale = Math.min(scaleX, scaleY);

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
        gc.setLineWidth(2);

        for (AnnotationPolygon polygon : completedPolygons) {
            Color polygonColor = getColorForCategory(polygon.getCategoryName());

            gc.setStroke(polygonColor);
            gc.setFill(polygonColor);

            List<Point2D> points = polygon.getPoints();


            for (int i = 0; i < points.size(); i++) {
                Point2D currentPoint = currentViewport.imageToCanvas(points.get(i));
                Point2D nextPoint = currentViewport.imageToCanvas(points.get((i + 1) % points.size()));

                gc.strokeLine(
                        currentPoint.getX(),
                        currentPoint.getY(),
                        nextPoint.getX(),
                        nextPoint.getY()
                );
            }

            drawPoints(gc, points, polygonColor);
            drawCategoryLabel(gc, polygon.getCategoryName(), points, polygonColor);
        }
    }

    private void drawCategoryLabel(
            GraphicsContext gc,
            String categoryName,
            List<Point2D> points,
            Color color
    ) {
        if (categoryName == null || points.isEmpty()) {
            return;
        }

        Point2D firstPoint = currentViewport.imageToCanvas(points.getFirst());

        gc.setFont(Font.font(14));
        gc.setFill(color);
        gc.fillText(
                categoryName,
                firstPoint.getX() + 6,
                firstPoint.getY() - 6
        );
    }

    private void drawCurrentPolygon(GraphicsContext gc, List<Point2D> currentPolygonPoints) {
        if (currentViewport == null)
            return;

        gc.setStroke(Color.LIME);
        gc.setLineWidth(2);

        for (int i = 0; i < currentPolygonPoints.size() - 1; i++) {
            Point2D currentPoint = currentViewport.imageToCanvas(currentPolygonPoints.get(i));
            Point2D nextPoint = currentViewport.imageToCanvas(currentPolygonPoints.get(i + 1));

            gc.strokeLine(
                    currentPoint.getX(),
                    currentPoint.getY(),
                    nextPoint.getX(),
                    nextPoint.getY()
            );
        }

        drawPoints(gc, currentPolygonPoints, Color.YELLOW);
    }

    private void drawPoints(GraphicsContext gc, List<Point2D> points, Color color) {
        if (currentViewport == null) {
            return;
        }

        gc.setFill(color);

        for (Point2D imagePoint : points) {
            Point2D point = currentViewport.imageToCanvas(imagePoint);

            double radius = 4;

            gc.fillOval(
                    point.getX() - radius,
                    point.getY() - radius,
                    radius * 2,
                    radius * 2
            );
        }
    }

    private void drawHoveredPoint(GraphicsContext gc, SelectedPolygonPoint hoveredPoint) {
        if (hoveredPoint == null || currentViewport == null) {
            return;
        }

        AnnotationPolygon polygon = hoveredPoint.getPolygon();
        Point2D imagePoint = polygon.getPoints().get(hoveredPoint.getPointIndex());
        Point2D canvasPoint = currentViewport.imageToCanvas(imagePoint);

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

    private void drawHoveredPolygon(GraphicsContext gc, SelectedPolygonPoint hoveredPoint) {
        if (hoveredPoint == null || currentViewport == null) {
            return;
        }

        AnnotationPolygon polygon = hoveredPoint.getPolygon();
        List<Point2D> points = polygon.getPoints();

        if (points.size() < 2) {
            return;
        }

        gc.setStroke(Color.rgb(255, 255, 255, 0.75));
        gc.setLineWidth(2);

        for (int i = 0; i < points.size(); i++) {
            Point2D currentPoint = currentViewport.imageToCanvas(points.get(i));
            Point2D nextPoint = currentViewport.imageToCanvas(points.get((i + 1) % points.size()));

            gc.strokeLine(
                    currentPoint.getX(),
                    currentPoint.getY(),
                    nextPoint.getX(),
                    nextPoint.getY()
            );
        }
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
