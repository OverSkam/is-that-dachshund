package overskam.projectH;

import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AnnotationController {

    private final VideoFrameReader videoFrameReader = new VideoFrameReader();
    private final CanvasRenderer canvasRenderer = new CanvasRenderer();
    private final AnnotationProject annotationProject = new AnnotationProject();
    private final CocoExporter cocoExporter = new CocoExporter();

    private final GraphicsContext gc;
    private final double canvasWidth;
    private final double canvasHeight;

    private Image currentImage;
    private File currentVideoFile;
    private double currentImageWidth;
    private double currentImageHeight;
    private final List<Point2D> currentPolygonPoints = new ArrayList<>();
    private SelectedPolygonPoint draggedPoint;
    private SelectedPolygonPoint hoveredPoint;

    private InteractionMode interactionMode = InteractionMode.DRAW;

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
        canvas.setOnMousePressed(event -> {
            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (viewport == null) {
                return;
            }

            if (!viewport.containsCanvasPoint(event.getX(), event.getY())) {
                return;
            }

            Point2D imagePoint = viewport.canvasToImage(event.getX(), event.getY());
            int frameIndex = videoFrameReader.getCurrentFrameIndex();

            if (interactionMode == InteractionMode.EDIT) {
                if (frameIndex < 0) {
                    return;
                }

                double maxDistance = 10.0 / viewport.getScale();
                draggedPoint = annotationProject.findNearestPoint(frameIndex, imagePoint, maxDistance);

                if (draggedPoint == null) {
                    return;
                }

                if (event.isAltDown() && event.isShiftDown()) {
                    removeExistingPolygon(draggedPoint.getPolygon());
                    draggedPoint = null;
                    return;
                }

                if (event.isAltDown()) {
                    removeExistingPoint(draggedPoint);
                    draggedPoint = null;
                    return;
                }

                return;
            }

            if (interactionMode == InteractionMode.DRAW) {
                currentPolygonPoints.add(imagePoint);
                redrawCanvas();
            }
        });

        canvas.setOnMouseDragged(event -> {
            if (interactionMode != InteractionMode.EDIT) {
                return;
            }

            if (draggedPoint == null) {
                return;
            }

            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (viewport == null) {
                return;
            }

            if (!viewport.containsCanvasPoint(event.getX(), event.getY())) {
                return;
            }

            Point2D imagePoint = viewport.canvasToImage(event.getX(), event.getY());

            draggedPoint.getPolygon().setPoint(
                    draggedPoint.getPointIndex(),
                    imagePoint
            );

            redrawCanvas();
        });

        canvas.setOnMouseMoved(event -> {
            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (interactionMode != InteractionMode.EDIT || viewport == null) {
                hoveredPoint = null;
                redrawCanvas();
                return;
            }

            if (!viewport.containsCanvasPoint(event.getX(), event.getY())) {
                hoveredPoint = null;
                redrawCanvas();
                return;
            }

            int frameIndex = videoFrameReader.getCurrentFrameIndex();

            if (frameIndex < 0) {
                hoveredPoint = null;
                redrawCanvas();
                return;
            }

            Point2D imagePoint = viewport.canvasToImage(event.getX(), event.getY());
            double maxDistance = 10.0 / viewport.getScale();

            hoveredPoint = annotationProject.findNearestPoint(frameIndex, imagePoint, maxDistance);
            redrawCanvas();
        });

        canvas.setOnMouseReleased(event -> draggedPoint = null);
    }

    public void connectKeyboard(
            Scene scene,
            ComboBox<String> categoryComboBox,
            Label modeLabel,
            ToggleButton drawModeButton,
            ToggleButton editModeButton,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.C) {
                closeCurrentPolygon(
                        categoryComboBox,
                        confidenceComboBox,
                        uncertaintyInput
                );
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

            if (event.getCode() == KeyCode.D) {
                drawModeButton.setSelected(true);
                setInteractionMode(InteractionMode.DRAW, modeLabel);
                event.consume();
            }

            if (event.getCode() == KeyCode.E) {
                editModeButton.setSelected(true);
                setInteractionMode(InteractionMode.EDIT, modeLabel);
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
            Button exportCocoButton,
            ToggleButton drawModeButton,
            ToggleButton editModeButton,
            Label frameLabel,
            Label modeLabel,
            ComboBox<String> categoryComboBox,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        openVideoButton.setOnAction(event -> openVideo(stage, frameLabel));
        previousFrameButton.setOnAction(event -> showPreviousFrame(frameLabel));
        nextFrameButton.setOnAction(event -> showNextFrame(frameLabel));
        goToFrameButton.setOnAction(event -> goToFrame(frameInput, frameLabel));
        closePolygonButton.setOnAction(event -> closeCurrentPolygon(
                categoryComboBox,
                confidenceComboBox,
                uncertaintyInput
        ));
        exportCocoButton.setOnAction(event -> exportCoco(stage));
        drawModeButton.setOnAction(event -> setInteractionMode(InteractionMode.DRAW, modeLabel));
        editModeButton.setOnAction(event -> setInteractionMode(InteractionMode.EDIT, modeLabel));
    }

    private void removeExistingPoint(SelectedPolygonPoint selectedPoint) {
        AnnotationPolygon polygon = selectedPoint.getPolygon();

        polygon.removePoint(selectedPoint.getPointIndex());

        if (polygon.getPointCount() < 3) {
            annotationProject.removePolygon(polygon);
        }

        redrawCanvas();
    }

    private void removeExistingPolygon(AnnotationPolygon polygon) {
        annotationProject.removePolygon(polygon);
        redrawCanvas();
    }

    private void exportCoco(Stage stage) {
        if (currentVideoFile == null) {
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export COCO JSON");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("COCO JSON", "*.json")
        );
        fileChooser.setInitialFileName("annotations.json");

        File outputFile = fileChooser.showSaveDialog(stage);

        if (outputFile == null) {
            return;
        }

        try {
            String operationId = buildOperationIdFromVideoFile();

            exportAnnotatedFrames(outputFile, operationId);

            Image restoredFrame = videoFrameReader.readCurrentFrameAgain();

            if (restoredFrame != null) {
                currentImage = restoredFrame;
                updateCurrentImageInfo(currentImage);
                redrawCanvas();
            }

            cocoExporter.export(
                    annotationProject,
                    outputFile,
                    operationId,
                    (int) currentImageWidth,
                    (int) currentImageHeight
            );

            System.out.println("COCO exported to: " + outputFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void exportAnnotatedFrames(File jsonOutputFile, String operationId) throws Exception {
        File exportDirectory = jsonOutputFile.getParentFile();

        if (exportDirectory == null) {
            exportDirectory = new File(".");
        }

        File framesDirectory = new File(exportDirectory, "frames");

        if (!framesDirectory.exists()) {
            boolean created = framesDirectory.mkdirs();

            if (!created) {
                throw new IOException("Could not create frames directory: " + framesDirectory.getAbsolutePath());
            }
        }

        for (int frameIndex : annotationProject.getAnnotatedFrameIndexes()) {
            File frameFile = new File(
                    framesDirectory,
                    String.format("%s_frame_%07d.jpg", operationId, frameIndex)
            );

            videoFrameReader.saveFrameAsJpeg(frameIndex, frameFile);
        }
    }

    private void openVideo(Stage stage, Label frameLabel) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Video File");

        File videoFile = fileChooser.showOpenDialog(stage);

        if (videoFile == null) {
            return;
        }

        try {
            currentVideoFile = videoFile;
            currentImage = videoFrameReader.openVideoAndReadFirstFrame(videoFile);
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateCurrentImageInfo(Image image) {
        currentImageWidth = image.getWidth();
        currentImageHeight = image.getHeight();
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
            updateCurrentImageInfo(currentImage);
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
            updateCurrentImageInfo(currentImage);
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
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();

            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid frame number");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void closeCurrentPolygon(
            ComboBox<String> categoryComboBox,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        if (currentPolygonPoints.size() < 3) {
            return;
        }

        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0) {
            return;
        }

        String selectedCategory = categoryComboBox.getValue();
        String confidence = confidenceComboBox.getValue();
        String uncertaintyReason = uncertaintyInput.getText();

        if (selectedCategory == null || confidence == null) {
            return;
        }

        if (uncertaintyReason == null || uncertaintyReason.isBlank()) {
            uncertaintyReason = "none";
        }

        annotationProject.addPolygon(
                new AnnotationPolygon(
                        frameIndex,
                        currentPolygonPoints,
                        selectedCategory,
                        confidence,
                        uncertaintyReason
                )
        );

        currentPolygonPoints.clear();

        redrawCanvas();
    }

    private void updateFrameLabel(Label frameLabel) {
        frameLabel.setText("Frame: " + videoFrameReader.getCurrentFrameIndex());
    }

    private void setInteractionMode(InteractionMode newMode, Label modeLabel) {
        interactionMode = newMode;

        if (interactionMode == InteractionMode.DRAW) {
            hoveredPoint = null;
            draggedPoint = null;
            modeLabel.setText("Mode: DRAW");
        }

        if (interactionMode == InteractionMode.EDIT) {
            currentPolygonPoints.clear();
            modeLabel.setText("Mode: EDIT");
        }

        redrawCanvas();
    }

    private String buildOperationIdFromVideoFile() {
        if (currentVideoFile == null) {
            return "UNKNOWN_OPERATION";
        }

        String fileName = currentVideoFile.getName();
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }

        return fileName;
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
                currentPolygonPoints,
                hoveredPoint
        );
    }
}