package overskam.projectH;

import javafx.geometry.Point2D;

public class ImageViewport {

    private final double imageWidth;
    private final double imageHeight;
    private final double drawX;
    private final double drawY;
    private final double drawWidth;
    private final double drawHeight;

    public ImageViewport(
            double imageWidth,
            double imageHeight,
            double drawX,
            double drawY,
            double drawWidth,
            double drawHeight
    ) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.drawX = drawX;
        this.drawY = drawY;
        this.drawWidth = drawWidth;
        this.drawHeight = drawHeight;
    }

    public boolean containsCanvasPoint(double canvasX, double canvasY) {
        return canvasX >= drawX
                && canvasX <= drawX + drawWidth
                && canvasY >= drawY
                && canvasY <= drawY + drawHeight;
    }

    public Point2D canvasToImage(double canvasX, double canvasY) {
        double relativeX = canvasX - drawX;
        double relativeY = canvasY - drawY;

        double imageX = relativeX * imageWidth / drawWidth;
        double imageY = relativeY * imageHeight / drawHeight;

        return new Point2D(imageX, imageY);
    }

    public Point2D imageToCanvas(Point2D imagePoint) {
        double canvasX = drawX + imagePoint.getX() * drawWidth / imageWidth;
        double canvasY = drawY + imagePoint.getY() * drawHeight / imageHeight;

        return new Point2D(canvasX, canvasY);
    }
}