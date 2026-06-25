package overskam.projectH.controller;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
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
import javafx.stage.Window;
import overskam.projectH.export.CocoExporter;
import overskam.projectH.export.CocoImportResult;
import overskam.projectH.export.CocoImporter;
import javafx.util.Duration;
import overskam.projectH.model.AnnotatedFrame;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.AnnotationProject;
import overskam.projectH.model.CategoryStore;
import overskam.projectH.model.SelectedPolygonEdge;
import overskam.projectH.model.SelectedPolygonPoint;
import overskam.projectH.render.CanvasRenderer;
import overskam.projectH.render.ImageViewport;
import overskam.projectH.video.VideoFrameReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class AnnotationController {

    private final VideoFrameReader videoFrameReader = new VideoFrameReader();
    private final CanvasRenderer canvasRenderer = new CanvasRenderer();
    private final AnnotationProject annotationProject = new AnnotationProject();
    private final CocoExporter cocoExporter = new CocoExporter();
    private final CocoImporter cocoImporter = new CocoImporter();
    private final CategoryStore categoryStore = new CategoryStore();

    private final GraphicsContext gc;
    private final double canvasWidth;
    private final double canvasHeight;

    private Image currentImage;
    private File currentVideoFile;
    private String importedOperationId;
    private double currentImageWidth;
    private double currentImageHeight;
    private final List<Point2D> currentPolygonPoints = new ArrayList<>();
    private SelectedPolygonPoint draggedPoint;
    private SelectedPolygonPoint hoveredPoint;
    private AnnotationPolygon selectedPolygon;

    private ComboBox<String> categoryControl;
    private ComboBox<String> confidenceControl;
    private TextField uncertaintyControl;
    private Label selectionLabel;
    private Label statusLabel;
    private Label frameLabelControl;
    private ListView<AnnotatedFrame> annotatedFramesList;
    private Timeline previousFrameTimeline;
    private Timeline nextFrameTimeline;

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
        canvas.setFocusTraversable(true);

        canvas.setOnMousePressed(event -> {
            canvas.requestFocus();
            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (viewport == null || !viewport.containsCanvasPoint(event.getX(), event.getY())) {
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

                if (draggedPoint != null) {
                    selectPolygon(draggedPoint.getPolygon());

                    if (event.isAltDown() && event.isShiftDown()) {
                        removeSelectedPolygon();
                        return;
                    }

                    if (event.isAltDown()) {
                        removeExistingPoint(draggedPoint);
                        return;
                    }

                    redrawCanvas();
                    return;
                }

                if (event.isShiftDown() && !event.isAltDown()) {
                    SelectedPolygonEdge edge = annotationProject.findNearestEdge(
                            frameIndex,
                            imagePoint,
                            8.0 / viewport.getScale()
                    );

                    if (edge != null) {
                        edge.getPolygon().insertPoint(edge.getInsertIndex(), edge.getImagePoint());
                        selectPolygon(edge.getPolygon());
                        redrawCanvas();
                        setStatus("Added point to selected polygon");
                        return;
                    }
                }

                clearSelection();
                redrawCanvas();
                return;
            }

            currentPolygonPoints.add(imagePoint);
            redrawCanvas();
        });

        canvas.setOnMouseDragged(event -> {
            if (interactionMode != InteractionMode.EDIT || draggedPoint == null) {
                return;
            }

            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (viewport == null || !viewport.containsCanvasPoint(event.getX(), event.getY())) {
                return;
            }

            Point2D imagePoint = viewport.canvasToImage(event.getX(), event.getY());
            draggedPoint.getPolygon().setPoint(draggedPoint.getPointIndex(), imagePoint);
            redrawCanvas();
        });

        canvas.setOnMouseMoved(event -> {
            ImageViewport viewport = canvasRenderer.getCurrentViewport();

            if (interactionMode != InteractionMode.EDIT || viewport == null
                    || !viewport.containsCanvasPoint(event.getX(), event.getY())) {
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
            hoveredPoint = annotationProject.findNearestPoint(
                    frameIndex,
                    imagePoint,
                    10.0 / viewport.getScale()
            );
            redrawCanvas();
        });

        canvas.setOnMouseReleased(event -> draggedPoint = null);
    }

    public void connectAnnotatedFramesList(ListView<AnnotatedFrame> annotatedFramesList) {
        this.annotatedFramesList = annotatedFramesList;
        annotatedFramesList.setOnMouseClicked(event -> {
            AnnotatedFrame selectedFrame = annotatedFramesList.getSelectionModel().getSelectedItem();

            if (selectedFrame != null) {
                goToAnnotatedFrame(selectedFrame.getFrameIndex());
            }
        });
        refreshAnnotatedFrames();
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
            if (isTextInputFocused(event)) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    scene.getRoot().requestFocus();
                    event.consume();
                }
                return;
            }

            if (event.getCode() == KeyCode.C) {
                closeCurrentPolygon(categoryComboBox, confidenceComboBox, uncertaintyInput);
                event.consume();
            } else if (event.getCode() == KeyCode.BACK_SPACE) {
                removeLastCurrentPolygonPoint();
                event.consume();
            } else if (event.getCode() == KeyCode.DELETE) {
                if (selectedPolygon != null) {
                    removeSelectedPolygon();
                } else {
                    removeLastCompletedPolygonOnCurrentFrame();
                }
                event.consume();
            } else if (event.getCode() == KeyCode.D) {
                drawModeButton.setSelected(true);
                setInteractionMode(InteractionMode.DRAW, modeLabel);
                event.consume();
            } else if (event.getCode() == KeyCode.E) {
                if (requestSetInteractionMode(InteractionMode.EDIT, modeLabel)) {
                    editModeButton.setSelected(true);
                } else {
                    restoreModeButtons(drawModeButton, editModeButton);
                }
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
            Button reopenPolygonButton,
            Button applyMetadataButton,
            Button deleteSelectedButton,
            Button clearFrameButton,
            Button importCocoButton,
            Button exportCocoButton,
            ToggleButton drawModeButton,
            ToggleButton editModeButton,
            Label frameLabel,
            Label modeLabel,
            Label selectionLabel,
            Label statusLabel,
            TextField operationIdInput,
            ComboBox<String> categoryComboBox,
            TextField newCategoryInput,
            Button addCategoryButton,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        categoryControl = categoryComboBox;
        confidenceControl = confidenceComboBox;
        uncertaintyControl = uncertaintyInput;
        this.selectionLabel = selectionLabel;
        this.statusLabel = statusLabel;
        frameLabelControl = frameLabel;
        loadCategories(categoryComboBox);

        openVideoButton.setOnAction(event -> openVideo(stage, frameLabel, operationIdInput));
        configurePressAndHoldButton(previousFrameButton, () -> showPreviousFrame(frameLabel), true);
        configurePressAndHoldButton(nextFrameButton, () -> showNextFrame(frameLabel), false);
        goToFrameButton.setOnAction(event -> goToFrame(frameInput, frameLabel));
        addCategoryButton.setOnAction(event -> addCategory(categoryComboBox, newCategoryInput));
        newCategoryInput.setOnAction(event -> addCategory(categoryComboBox, newCategoryInput));
        closePolygonButton.setOnAction(event -> closeCurrentPolygon(
                categoryComboBox, confidenceComboBox, uncertaintyInput
        ));
        reopenPolygonButton.setOnAction(event -> reopenLastPolygon(modeLabel, drawModeButton, editModeButton));
        applyMetadataButton.setOnAction(event -> applySelectedMetadata());
        deleteSelectedButton.setOnAction(event -> removeSelectedPolygon());
        clearFrameButton.setOnAction(event -> clearCurrentFrame(stage));
        importCocoButton.setOnAction(event -> importCoco(stage, operationIdInput));
        exportCocoButton.setOnAction(event -> exportCoco(stage, operationIdInput));
        drawModeButton.setOnAction(event -> setInteractionMode(InteractionMode.DRAW, modeLabel));
        editModeButton.setOnAction(event -> {
            if (!requestSetInteractionMode(InteractionMode.EDIT, modeLabel)) {
                restoreModeButtons(drawModeButton, editModeButton);
                event.consume();
            }
        });
    }


    private void configurePressAndHoldButton(Button button, Runnable frameAction, boolean previousDirection) {
        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(80), event -> frameAction.run()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        boolean[] mousePressHandled = {false};

        if (previousDirection) {
            previousFrameTimeline = timeline;
        } else {
            nextFrameTimeline = timeline;
        }

        button.setOnMousePressed(event -> {
            mousePressHandled[0] = true;
            frameAction.run();
            timeline.playFromStart();
            event.consume();
        });
        button.setOnMouseReleased(event -> timeline.stop());
        button.setOnMouseExited(event -> timeline.stop());
        button.setOnAction(event -> {
            if (mousePressHandled[0]) {
                mousePressHandled[0] = false;
                return;
            }

            frameAction.run();
        });
    }

    private void loadCategories(ComboBox<String> categoryComboBox) {
        List<String> categories = categoryStore.loadCategories();
        categoryComboBox.getItems().setAll(categories);

        if (!categories.isEmpty() && categoryComboBox.getValue() == null) {
            categoryComboBox.setValue(categories.getFirst());
        }
    }

    private void addCategory(ComboBox<String> categoryComboBox, TextField newCategoryInput) {
        String categoryName = categoryStore.normalizeCategoryName(newCategoryInput.getText());

        if (categoryName.isBlank()) {
            setStatus("Enter class name");
            return;
        }

        if (!categoryComboBox.getItems().contains(categoryName)) {
            categoryComboBox.getItems().add(categoryName);
            saveCategories(categoryComboBox.getItems());
        }

        categoryComboBox.setValue(categoryName);
        newCategoryInput.clear();
        setStatus("Added class: " + categoryName);
    }

    private void addImportedCategories(List<AnnotationPolygon> polygons) {
        if (categoryControl == null) {
            return;
        }

        boolean changed = false;

        for (AnnotationPolygon polygon : polygons) {
            String categoryName = categoryStore.normalizeCategoryName(polygon.getCategoryName());

            if (!categoryName.isBlank() && !categoryControl.getItems().contains(categoryName)) {
                categoryControl.getItems().add(categoryName);
                changed = true;
            }
        }

        if (changed) {
            saveCategories(categoryControl.getItems());
        }
    }

    private void saveCategories(List<String> categories) {
        try {
            categoryStore.saveCategories(categories);
        } catch (IOException e) {
            setStatus("Could not save classes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<String> getCategoryNames() {
        if (categoryControl == null) {
            return categoryStore.loadCategories();
        }

        return new ArrayList<>(categoryControl.getItems());
    }
    private void goToAnnotatedFrame(int frameIndex) {
        try {
            Image frame = videoFrameReader.readFrameAt(frameIndex);

            if (frame == null) {
                setStatus("Frame not found: " + frameIndex);
                return;
            }

            currentImage = frame;
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();
            clearSelection();
            redrawCanvas();
            updateFrameLabel(frameLabelControl);
        } catch (Exception e) {
            setStatus("Open annotated frame failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void refreshAnnotatedFrames() {
        if (annotatedFramesList == null) {
            return;
        }

        List<AnnotatedFrame> frames = new ArrayList<>();

        for (int frameIndex : annotationProject.getAnnotatedFrameIndexes()) {
            frames.add(new AnnotatedFrame(
                    frameIndex,
                    annotationProject.getPolygonCountForFrame(frameIndex)
            ));
        }

        annotatedFramesList.getItems().setAll(frames);

        int currentFrameIndex = videoFrameReader.getCurrentFrameIndex();

        for (AnnotatedFrame frame : frames) {
            if (frame.getFrameIndex() == currentFrameIndex) {
                annotatedFramesList.getSelectionModel().select(frame);
                break;
            }
        }
    }
    private void selectPolygon(AnnotationPolygon polygon) {
        selectedPolygon = polygon;

        if (categoryControl != null) {
            categoryControl.setValue(polygon.getCategoryName());
            confidenceControl.setValue(polygon.getConfidence());
            uncertaintyControl.setText(polygon.getUncertaintyReason());
        }

        if (selectionLabel != null) {
            selectionLabel.setText("Selected: " + polygon.getCategoryName());
        }
    }

    private void applySelectedMetadata() {
        if (selectedPolygon == null) {
            setStatus("Select a polygon in Edit mode first");
            return;
        }

        String category = categoryControl.getValue();
        String confidence = confidenceControl.getValue();
        String uncertainty = normalizeUncertainty(uncertaintyControl.getText());

        if (category == null || confidence == null) {
            setStatus("Choose category and confidence");
            return;
        }

        selectedPolygon.setCategoryName(category);
        selectedPolygon.setConfidence(confidence);
        selectedPolygon.setUncertaintyReason(uncertainty);

        if (selectionLabel != null) {
            selectionLabel.setText("Selected: " + category);
        }

        redrawCanvas();
        setStatus("Updated selected polygon metadata");
    }

    private void clearCurrentFrame(Stage stage) {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();
        int polygonCount = annotationProject.getPolygonCountForFrame(frameIndex);

        if (frameIndex < 0 || polygonCount == 0) {
            setStatus("No annotations on current frame");
            return;
        }

        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Delete " + polygonCount + " polygon(s) from frame " + frameIndex + "?",
                ButtonType.CANCEL,
                ButtonType.OK
        );
        alert.initOwner(stage);
        alert.setTitle("Clear frame?");
        alert.setHeaderText("Current frame annotations will be removed");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        annotationProject.removePolygonsForFrame(frameIndex);
        clearSelection();
        refreshAnnotatedFrames();
        redrawCanvas();
        setStatus("Cleared frame " + frameIndex);
    }
    private void removeExistingPoint(SelectedPolygonPoint selectedPoint) {
        AnnotationPolygon polygon = selectedPoint.getPolygon();
        polygon.removePoint(selectedPoint.getPointIndex());

        if (polygon.getPointCount() < 3) {
            annotationProject.removePolygon(polygon);
        }

        clearSelection();
        refreshAnnotatedFrames();
        redrawCanvas();
    }

    private void removeSelectedPolygon() {
        if (selectedPolygon == null) {
            setStatus("No polygon selected");
            return;
        }

        annotationProject.removePolygon(selectedPolygon);
        clearSelection();
        refreshAnnotatedFrames();
        redrawCanvas();
        setStatus("Deleted selected polygon");
    }

    private void clearSelection() {
        hoveredPoint = null;
        draggedPoint = null;
        selectedPolygon = null;

        if (selectionLabel != null) {
            selectionLabel.setText("Selected: none");
        }
    }

    private boolean isTextInputFocused(KeyEvent event) {
        return event.getTarget() instanceof TextInputControl;
    }

    private void exportCoco(Stage stage, TextField operationIdInput) {
        if (currentVideoFile == null) {
            setStatus("Open a video before export");
            return;
        }
        if (annotationProject.getAllPolygons().isEmpty()) {
            setStatus("No completed polygons to export");
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
            String operationId = resolveOperationId(operationIdInput);

            if (operationId == null) {
                setStatus("Enter a valid Operation ID");
                return;
            }

            operationIdInput.setText(operationId);
            exportAnnotatedFrames(outputFile, operationId);
            cocoExporter.export(
                    annotationProject,
                    outputFile,
                    operationId,
                    (int) currentImageWidth,
                    (int) currentImageHeight,
                    getCategoryNames()
            );

            Image restoredFrame = videoFrameReader.readCurrentFrameAgain();
            if (restoredFrame != null) {
                currentImage = restoredFrame;
                updateCurrentImageInfo(currentImage);
                redrawCanvas();
            }

            setStatus("Exported " + annotationProject.getPolygonCount()
                    + " polygons on " + annotationProject.getAnnotatedFrameCount() + " frames");
        } catch (Exception e) {
            setStatus("Export failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void importCoco(Stage stage, TextField operationIdInput) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import COCO JSON");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("COCO JSON", "*.json")
        );
        File inputFile = fileChooser.showOpenDialog(stage);

        if (inputFile == null) {
            return;
        }

        try {
            CocoImportResult importResult = cocoImporter.importProject(inputFile);
            annotationProject.clear();
            annotationProject.addAllPolygons(importResult.getPolygons());
            importedOperationId = importResult.getOperationId();
            operationIdInput.setText(importedOperationId);

            if (importResult.getImageWidth() > 0) {
                currentImageWidth = importResult.getImageWidth();
            }
            if (importResult.getImageHeight() > 0) {
                currentImageHeight = importResult.getImageHeight();
            }

            currentPolygonPoints.clear();
            clearSelection();
            refreshAnnotatedFrames();
            redrawCanvas();
            setStatus("Imported " + annotationProject.getPolygonCount()
                    + " polygons on " + annotationProject.getAnnotatedFrameCount() + " frames");
        } catch (Exception e) {
            setStatus("Import failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void exportAnnotatedFrames(File jsonOutputFile, String operationId) throws Exception {
        File exportDirectory = jsonOutputFile.getParentFile();
        if (exportDirectory == null) {
            exportDirectory = new File(".");
        }

        File framesDirectory = new File(exportDirectory, "frames");
        if (!framesDirectory.exists() && !framesDirectory.mkdirs()) {
            throw new IOException("Could not create frames directory: " + framesDirectory.getAbsolutePath());
        }

        for (int frameIndex : annotationProject.getAnnotatedFrameIndexes()) {
            File frameFile = new File(
                    framesDirectory,
                    String.format("%s_frame_%07d.jpg", operationId, frameIndex)
            );
            videoFrameReader.saveFrameAsJpeg(frameIndex, frameFile);
        }
    }

    private void openVideo(Stage stage, Label frameLabel, TextField operationIdInput) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Video File");
        File videoFile = fileChooser.showOpenDialog(stage);

        if (videoFile == null) {
            return;
        }

        if (currentVideoFile != null && !annotationProject.getAllPolygons().isEmpty() && !confirmDiscardAnnotations(stage)) {
            setStatus("Kept current annotation project");
            return;
        }

        try {
            boolean keepImportedAnnotations = currentVideoFile == null
                    && !annotationProject.getAllPolygons().isEmpty();

            if (!keepImportedAnnotations) {
                annotationProject.clear();
            }

            currentPolygonPoints.clear();
            clearSelection();
            currentVideoFile = videoFile;

            if (keepImportedAnnotations) {
                operationIdInput.setText(importedOperationId);
            } else {
                importedOperationId = null;
                operationIdInput.setText(buildOperationIdFromVideoFile());
            }

            currentImage = videoFrameReader.openVideoAndReadFirstFrame(videoFile);
            updateCurrentImageInfo(currentImage);
            refreshAnnotatedFrames();
            redrawCanvas();
            updateFrameLabel(frameLabel);
            setStatus("Opened video: " + videoFile.getName());
        } catch (Exception e) {
            setStatus("Open video failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean confirmDiscardAnnotations(Stage stage) {
        Alert alert = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Opening another video will discard the current annotations.",
                ButtonType.CANCEL,
                ButtonType.OK
        );
        alert.initOwner(stage);
        alert.setTitle("Discard annotations?");
        alert.setHeaderText("Current annotations will be removed");

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }
    private void showPreviousFrame(Label frameLabel) {
        try {
            Image previousFrame = videoFrameReader.readPreviousFrame();
            if (previousFrame == null) {
                setStatus("No previous frame");
                return;
            }
            currentImage = previousFrame;
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();
            clearSelection();
            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            setStatus("Previous frame failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void showNextFrame(Label frameLabel) {
        try {
            Image nextFrame = videoFrameReader.readNextFrame();
            if (nextFrame == null) {
                setStatus("No next frame");
                return;
            }
            currentImage = nextFrame;
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();
            clearSelection();
            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (Exception e) {
            setStatus("Next frame failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void goToFrame(TextField frameInput, Label frameLabel) {
        try {
            int requestedFrameIndex = Integer.parseInt(frameInput.getText());
            Image frame = videoFrameReader.readFrameAt(requestedFrameIndex);
            if (frame == null) {
                setStatus("Frame not found: " + requestedFrameIndex);
                return;
            }
            currentImage = frame;
            updateCurrentImageInfo(currentImage);
            currentPolygonPoints.clear();
            clearSelection();
            redrawCanvas();
            updateFrameLabel(frameLabel);
        } catch (NumberFormatException e) {
            setStatus("Please enter a valid frame number");
        } catch (Exception e) {
            setStatus("Go to frame failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean closeCurrentPolygon(
            ComboBox<String> categoryComboBox,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        if (currentPolygonPoints.size() < 3) {
            setStatus("Add at least 3 points before closing");
            return false;
        }

        int frameIndex = videoFrameReader.getCurrentFrameIndex();
        if (frameIndex < 0) {
            setStatus("Open a video before annotation");
            return false;
        }

        String category = categoryComboBox.getValue();
        String confidence = confidenceComboBox.getValue();
        if (category == null || confidence == null) {
            setStatus("Choose category and confidence");
            return false;
        }

        annotationProject.addPolygon(new AnnotationPolygon(
                frameIndex,
                currentPolygonPoints,
                category,
                confidence,
                normalizeUncertainty(uncertaintyInput.getText())
        ));

        currentPolygonPoints.clear();
        refreshAnnotatedFrames();
        redrawCanvas();
        setStatus("Closed polygon: " + category);
        return true;
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
        if (annotationProject.removeLastPolygonForFrame(frameIndex)) {
            clearSelection();
            refreshAnnotatedFrames();
            redrawCanvas();
            setStatus("Deleted last polygon on current frame");
        }
    }

    private String normalizeUncertainty(String uncertainty) {
        return uncertainty == null || uncertainty.isBlank() ? "none" : uncertainty.trim();
    }

    private void updateCurrentImageInfo(Image image) {
        currentImageWidth = image.getWidth();
        currentImageHeight = image.getHeight();
    }

    private void updateFrameLabel(Label frameLabel) {
        frameLabel.setText("Frame: " + videoFrameReader.getCurrentFrameIndex());
        refreshAnnotatedFrames();
    }

    private void setInteractionMode(InteractionMode newMode, Label modeLabel) {
        interactionMode = newMode;
        if (interactionMode == InteractionMode.DRAW) {
            clearSelection();
            modeLabel.setText("Mode: DRAW");
        } else {
            currentPolygonPoints.clear();
            modeLabel.setText("Mode: EDIT");
        }
        redrawCanvas();
    }


    private boolean requestSetInteractionMode(InteractionMode newMode, Label modeLabel) {
        if (newMode != InteractionMode.EDIT || currentPolygonPoints.isEmpty()) {
            setInteractionMode(newMode, modeLabel);
            return true;
        }

        ButtonType closeButton = new ButtonType("Close Polygon");
        ButtonType editButton = new ButtonType("Edit Without Closing");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Unclosed polygon");
        alert.setHeaderText("You have an unclosed polygon on the current frame");
        alert.setContentText("Close it before switching to Edit mode?");
        alert.getButtonTypes().setAll(closeButton, editButton, cancelButton);

        Window owner = modeLabel.getScene() == null ? null : modeLabel.getScene().getWindow();
        if (owner != null) {
            alert.initOwner(owner);
        }

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isEmpty() || result.get() == cancelButton) {
            setStatus("Stayed in Draw mode");
            return false;
        }

        if (result.get() == closeButton && !closeCurrentPolygon(categoryControl, confidenceControl, uncertaintyControl)) {
            return false;
        }

        setInteractionMode(InteractionMode.EDIT, modeLabel);
        return true;
    }

    private void restoreModeButtons(ToggleButton drawModeButton, ToggleButton editModeButton) {
        if (interactionMode == InteractionMode.DRAW) {
            drawModeButton.setSelected(true);
        } else {
            editModeButton.setSelected(true);
        }
    }

    private void reopenLastPolygon(
            Label modeLabel,
            ToggleButton drawModeButton,
            ToggleButton editModeButton
    ) {
        if (!currentPolygonPoints.isEmpty()) {
            setStatus("Close or clear current polygon before reopening another one");
            return;
        }

        int frameIndex = videoFrameReader.getCurrentFrameIndex();
        if (frameIndex < 0) {
            setStatus("Open a video before reopening polygons");
            return;
        }

        AnnotationPolygon polygon = annotationProject.removeLastPolygonForFrameAndReturn(frameIndex);
        if (polygon == null) {
            setStatus("No closed polygon on current frame");
            return;
        }

        currentPolygonPoints.addAll(polygon.getPoints());

        if (categoryControl != null) {
            categoryControl.setValue(polygon.getCategoryName());
            confidenceControl.setValue(polygon.getConfidence());
            uncertaintyControl.setText(polygon.getUncertaintyReason());
        }

        drawModeButton.setSelected(true);
        setInteractionMode(InteractionMode.DRAW, modeLabel);
        clearSelection();
        refreshAnnotatedFrames();
        redrawCanvas();
        setStatus("Reopened polygon: " + polygon.getCategoryName());
    }
    private String buildOperationIdFromVideoFile() {
        if (importedOperationId != null && !importedOperationId.isBlank()) {
            return importedOperationId;
        }
        if (currentVideoFile == null) {
            return "UNKNOWN_OPERATION";
        }
        String fileName = currentVideoFile.getName();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private String resolveOperationId(TextField operationIdInput) {
        String candidate = operationIdInput.getText();
        if (candidate == null || candidate.isBlank()) {
            candidate = buildOperationIdFromVideoFile();
        }
        String normalized = candidate.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        return normalized.isBlank() ? null : normalized;
    }

    private void setStatus(String message) {
        if (statusLabel != null) {
            statusLabel.setText(message);
        }
    }

    private void redrawCanvas() {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();
        List<AnnotationPolygon> polygonsForCurrentFrame = frameIndex >= 0
                ? annotationProject.getPolygonsForFrame(frameIndex)
                : Collections.emptyList();

        canvasRenderer.redraw(
                gc,
                canvasWidth,
                canvasHeight,
                currentImage,
                polygonsForCurrentFrame,
                currentPolygonPoints,
                selectedPolygon,
                hoveredPoint
        );
    }
}
