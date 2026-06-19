package overskam.projectH;

import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationController {

    private final VideoFrameReader videoFrameReader = new VideoFrameReader();
    private final CanvasRenderer canvasRenderer = new CanvasRenderer();
    private final AnnotationProject annotationProject = new AnnotationProject();

    private final GraphicsContext gc;
    private final double canvasWidth;
    private final double canvasHeight;

    private Image currentImage;
    private final List<Point2D> currentPolygonPoints = new ArrayList<>();

    private File currentVideoFile;
    private double currentImageWidth;
    private double currentImageHeight;

    public AnnotationController(GraphicsContext gc, double canvasWidth, double canvasHeight) {
        this.gc = gc;
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
    }

    public void showInitialImage(String resourcePath) {
        currentImage = new Image(getClass().getResourceAsStream(resourcePath));
        redrawCanvas();
    }

    public void connectMouse(Canvas canvas) {
        canvas.setOnMouseClicked(event -> {
            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (viewport == null) {
                return;
            }

            if (!viewport.containsCanvasPoint(event.getX(), event.getY())) {
                return;
            }

            Point2D imagePoint = viewport.canvasToImage(event.getX(), event.getY());
            currentPolygonPoints.add(imagePoint);

            redrawCanvas();
        });
    }

    public void connectKeyboard(Scene scene, ComboBox<String> categoryComboBox) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.C) {
                closeCurrentPolygon(categoryComboBox);
                event.consume();
            }

            if (event.getCode() == KeyCode.BACK_SPACE) {
                removeLastCurrentPolygonPoint();
                event.consume();
            }

            if (event.getCode() == KeyCode.DELETE) {
                removeLastCompletedPolygonOnCurrentFrame();
                event.consume();
            }
        });
    }

    public void connectButtons(
            Stage stage,
            Button openVideoButton,
            Button previousFrameButton,
            Button nextFrameButton,
            TextField frameInput,
            Button goToFrameButton,
            Button closePolygonButton,
            Label frameLabel,
            ComboBox<String> categoryComboBox
    ) {
        openVideoButton.setOnAction(event -> openVideo(stage, frameLabel));
        previousFrameButton.setOnAction(event -> showPreviousFrame(frameLabel));
        nextFrameButton.setOnAction(event -> showNextFrame(frameLabel));
        goToFrameButton.setOnAction(event -> goToFrame(frameInput, frameLabel));
        closePolygonButton.setOnAction(event -> closeCurrentPolygon(categoryComboBox));
    }

    private void openVideo(Stage stage, Label frameLabel) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Video File");

        File videoFile = fileChooser.showOpenDialog(stage);

        if (videoFile == null) {
            return;
        }

        try {
            currentImage = videoFrameReader.openVideoAndReadFirstFrame(videoFile);
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeLastCurrentPolygonPoint() {
        if (currentPolygonPoints.isEmpty()) {
            return;
        }

        currentPolygonPoints.removeLast();
        redrawCanvas();
    }

    private void removeLastCompletedPolygonOnCurrentFrame() {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0) {
            return;
        }

        boolean removed = annotationProject.removeLastPolygonForFrame(frameIndex);

        if (removed) {
            redrawCanvas();
        }
    }

    private void showPreviousFrame(Label frameLabel) {
        try {
            Image previousFrame = videoFrameReader.readPreviousFrame();

            if (previousFrame == null) {
                return;
            }

            currentImage = previousFrame;
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showNextFrame(Label frameLabel) {
        try {
            Image nextFrame = videoFrameReader.readNextFrame();

            if (nextFrame == null) {
                return;
            }

            currentImage = nextFrame;
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void goToFrame(TextField frameInput, Label frameLabel) {
        try {
            int requestedFrameIndex = Integer.parseInt(frameInput.getText());

            Image frame = videoFrameReader.readFrameAt(requestedFrameIndex);

            if (frame == null) {
                return;
            }

            currentImage = frame;
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid frame number");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeCurrentPolygon(ComboBox<String> categoryComboBox) {
        if (currentPolygonPoints.size() < 3) {
            return;
        }

        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0) {
            return;
        }

        String selectedCategory = categoryComboBox.getValue();

        if (selectedCategory == null) {
            return;
        }

        annotationProject.addPolygon(
                new AnnotationPolygon(
                        frameIndex,
                        currentPolygonPoints,
                        selectedCategory
                )
        );

        currentPolygonPoints.clear();

        redrawCanvas();
    }

    private void updateFrameLabel(Label frameLabel) {
        frameLabel.setText("Frame: " + videoFrameReader.getCurrentFrameIndex());
    }

    private void redrawCanvas() {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        List<AnnotationPolygon> polygonsForCurrentFrame;

        if (frameIndex >= 0) {
            polygonsForCurrentFrame = annotationProject.getPolygonsForFrame(frameIndex);
        } else {
            polygonsForCurrentFrame = Collections.emptyList();
        }

        canvasRenderer.redraw(
                gc,
                canvasWidth,
                canvasHeight,
                currentImage,
                polygonsForCurrentFrame,
                currentPolygonPoints
        );
    }
}