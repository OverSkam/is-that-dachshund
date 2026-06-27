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
import javafx.scene.input.MouseButton;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import overskam.projectH.export.CocoExporter;
import overskam.projectH.export.CocoImportResult;
import overskam.projectH.export.CocoImporter;
import overskam.projectH.export.CocoExportValidationResult;
import overskam.projectH.export.CocoExportValidator;
import javafx.util.Duration;
import overskam.projectH.model.AnnotatedFrame;
import overskam.projectH.model.AnnotationPolygon;
import overskam.projectH.model.AnnotationProject;
import overskam.projectH.model.CategoryKeyBindingStore;
import overskam.projectH.model.CategoryStore;
import overskam.projectH.model.SelectedPolygonEdge;
import overskam.projectH.model.SelectedPolygonPoint;
import overskam.projectH.render.CanvasRenderer;
import overskam.projectH.render.ImageViewport;
import overskam.projectH.storage.ProjectFileData;
import overskam.projectH.storage.ProjectFileStore;
import overskam.projectH.video.VideoFrameReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

public class AnnotationController {

    private final VideoFrameReader videoFrameReader = new VideoFrameReader();
    private final CanvasRenderer canvasRenderer = new CanvasRenderer();
    private final AnnotationProject annotationProject = new AnnotationProject();
    private final CocoExporter cocoExporter = new CocoExporter();
    private final CocoImporter cocoImporter = new CocoImporter();
    private final CocoExportValidator cocoExportValidator = new CocoExportValidator();
    private final CategoryStore categoryStore = new CategoryStore();
    private final CategoryKeyBindingStore categoryKeyBindingStore = new CategoryKeyBindingStore();
    private final ProjectFileStore projectFileStore = new ProjectFileStore();
    private final Map<String, String> categoryKeyBindings = new LinkedHashMap<>();

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
    private Timeline autosaveTimeline;
    private File currentProjectFile;
    private TextField operationIdControl;
    private Label frameStatusLabel;
    private Label zoomLabel;
    private ComboBox<String> frameQualityControl;
    private boolean panningCanvas;
    private double lastPanX;
    private double lastPanY;
    private Label categoryBindingLabel;
    private boolean waitingForCategoryKeyBinding;
    private boolean updatingMetadataControls;

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
        canvas.setOnScroll(event -> {
            if (currentImage == null) {
                return;
            }

            double factor = event.getDeltaY() > 0 ? 1.15 : 1.0 / 1.15;
            canvasRenderer.zoomAt(event.getX(), event.getY(), factor);
            redrawCanvas();
            updateZoomLabel();
            event.consume();
        });


        canvas.setOnMousePressed(event -> {
            canvas.requestFocus();
            if (event.getButton() == MouseButton.SECONDARY || event.getButton() == MouseButton.MIDDLE) {
                panningCanvas = true;
                lastPanX = event.getX();
                lastPanY = event.getY();
                event.consume();
                return;
            }

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
            if (panningCanvas) {
                canvasRenderer.pan(event.getX() - lastPanX, event.getY() - lastPanY);
                lastPanX = event.getX();
                lastPanY = event.getY();
                redrawCanvas();
                event.consume();
                return;
            }

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

        canvas.setOnMouseReleased(event -> {
            draggedPoint = null;
            panningCanvas = false;
        });
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
            if (waitingForCategoryKeyBinding) {
                handleCategoryKeyBinding(event.getCode());
                event.consume();
                return;
            }

            if (isTextInputFocused(event)) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    scene.getRoot().requestFocus();
                    event.consume();
                }
                return;
            }

            if (applyCategoryKeyBinding(event.getCode())) {
                event.consume();
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
            Button resetViewButton,
            Button reloadFrameButton,
            Button markProblemFrameButton,
            Button closePolygonButton,
            Button reopenPolygonButton,
            Button deleteSelectedButton,
            Button clearFrameButton,
            Button saveProjectButton,
            Button loadProjectButton,
            Button importCocoButton,
            Button exportCocoButton,
            ToggleButton drawModeButton,
            ToggleButton editModeButton,
            Label frameLabel,
            Label frameStatusLabel,
            Label zoomLabel,
            Label modeLabel,
            Label selectionLabel,
            Label statusLabel,
            TextField operationIdInput,
            ComboBox<String> frameQualityComboBox,
            ComboBox<String> categoryComboBox,
            TextField newCategoryInput,
            Button addCategoryButton,
            Button bindCategoryKeyButton,
            Label categoryBindingLabel,
            ComboBox<String> confidenceComboBox,
            TextField uncertaintyInput
    ) {
        categoryControl = categoryComboBox;
        confidenceControl = confidenceComboBox;
        uncertaintyControl = uncertaintyInput;
        this.selectionLabel = selectionLabel;
        this.statusLabel = statusLabel;
        frameLabelControl = frameLabel;
        this.frameStatusLabel = frameStatusLabel;
        this.zoomLabel = zoomLabel;
        frameQualityControl = frameQualityComboBox;
        operationIdControl = operationIdInput;
        this.categoryBindingLabel = categoryBindingLabel;
        loadCategoryKeyBindings();
        loadCategories(categoryComboBox);

        openVideoButton.setOnAction(event -> openVideo(stage, frameLabel, operationIdInput));
        configurePressAndHoldButton(previousFrameButton, () -> showPreviousFrame(frameLabel), true);
        configurePressAndHoldButton(nextFrameButton, () -> showNextFrame(frameLabel), false);
        goToFrameButton.setOnAction(event -> goToFrame(frameInput, frameLabel));
        resetViewButton.setOnAction(event -> resetView());
        reloadFrameButton.setOnAction(event -> reloadCurrentFrame());
        markProblemFrameButton.setOnAction(event -> toggleProblemFrame());
        frameQualityComboBox.setOnAction(event -> updateCurrentFrameQuality(frameQualityComboBox.getValue()));
        addCategoryButton.setOnAction(event -> addCategory(categoryComboBox, newCategoryInput));
        newCategoryInput.setOnAction(event -> addCategory(categoryComboBox, newCategoryInput));
        bindCategoryKeyButton.setOnAction(event -> startCategoryKeyBinding());
        categoryComboBox.valueProperty().addListener((observable, oldValue, newValue) -> updateCategoryBindingLabel());
        updateCategoryBindingLabel();
        categoryComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySelectedMetadata());
        confidenceComboBox.valueProperty().addListener((observable, oldValue, newValue) -> applySelectedMetadata());
        uncertaintyInput.textProperty().addListener((observable, oldValue, newValue) -> applySelectedMetadata());
        closePolygonButton.setOnAction(event -> closeCurrentPolygon(
                categoryComboBox, confidenceComboBox, uncertaintyInput
        ));
        reopenPolygonButton.setOnAction(event -> reopenLastPolygon(modeLabel, drawModeButton, editModeButton));
        deleteSelectedButton.setOnAction(event -> removeSelectedPolygon());
        clearFrameButton.setOnAction(event -> clearCurrentFrame(stage));
        saveProjectButton.setOnAction(event -> saveProject(stage, operationIdInput));
        loadProjectButton.setOnAction(event -> loadProject(stage, operationIdInput, frameLabel));
        importCocoButton.setOnAction(event -> importCoco(stage, operationIdInput));
        exportCocoButton.setOnAction(event -> exportCoco(stage, operationIdInput));
        drawModeButton.setOnAction(event -> setInteractionMode(InteractionMode.DRAW, modeLabel));
        startAutosave();
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
        updateCategoryBindingLabel();
        newCategoryInput.clear();
        setStatus("Added class: " + categoryName);
    }


    private void loadCategoryKeyBindings() {
        categoryKeyBindings.clear();
        categoryKeyBindings.putAll(categoryKeyBindingStore.loadBindings());
    }

    private void startCategoryKeyBinding() {
        String category = categoryControl == null ? null : categoryControl.getValue();

        if (category == null || category.isBlank()) {
            setStatus("Choose category before binding a key");
            return;
        }

        waitingForCategoryKeyBinding = true;
        setStatus("Press a key for category: " + category);
    }

    private void handleCategoryKeyBinding(KeyCode keyCode) {
        if (keyCode == KeyCode.ESCAPE) {
            clearCurrentCategoryKeyBinding();
            waitingForCategoryKeyBinding = false;
            return;
        }

        if (isReservedCategoryBindingKey(keyCode)) {
            waitingForCategoryKeyBinding = false;
            setStatus("Reserved key. Binding cancelled");
            return;
        }

        String category = categoryControl == null ? null : categoryControl.getValue();

        if (category == null || category.isBlank()) {
            waitingForCategoryKeyBinding = false;
            setStatus("Choose category before binding a key");
            return;
        }

        String keyName = keyCode.name();
        categoryKeyBindings.entrySet().removeIf(entry -> entry.getKey().equals(keyName)
                || entry.getValue().equals(category));
        categoryKeyBindings.put(keyName, category);
        saveCategoryKeyBindings();
        waitingForCategoryKeyBinding = false;
        updateCategoryBindingLabel();
        setStatus("Bound " + displayKeyName(keyName) + " to " + category);
    }


    private void clearCurrentCategoryKeyBinding() {
        String category = categoryControl == null ? null : categoryControl.getValue();

        if (category == null || category.isBlank()) {
            setStatus("Choose category before clearing key binding");
            return;
        }

        boolean removed = categoryKeyBindings.entrySet().removeIf(entry -> entry.getValue().equals(category));

        if (removed) {
            saveCategoryKeyBindings();
            updateCategoryBindingLabel();
            setStatus("Removed key binding for " + category);
        } else {
            updateCategoryBindingLabel();
            setStatus("No key binding for " + category);
        }
    }
    private boolean applyCategoryKeyBinding(KeyCode keyCode) {
        String category = categoryKeyBindings.get(keyCode.name());

        if (category == null || categoryControl == null) {
            return false;
        }

        if (!categoryControl.getItems().contains(category)) {
            setStatus("Key is bound to missing category: " + category);
            return true;
        }

        categoryControl.setValue(category);
        setStatus("Selected category: " + category);
        return true;
    }

    private boolean isReservedCategoryBindingKey(KeyCode keyCode) {
        return Set.of(
                KeyCode.C,
                KeyCode.D,
                KeyCode.E,
                KeyCode.BACK_SPACE,
                KeyCode.DELETE,
                KeyCode.ENTER,
                KeyCode.TAB,
                KeyCode.SPACE,
                KeyCode.SHIFT,
                KeyCode.CONTROL,
                KeyCode.ALT,
                KeyCode.META,
                KeyCode.WINDOWS
        ).contains(keyCode);
    }

    private void saveCategoryKeyBindings() {
        try {
            categoryKeyBindingStore.saveBindings(categoryKeyBindings);
        } catch (IOException e) {
            setStatus("Could not save key bindings: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateCategoryBindingLabel() {
        if (categoryBindingLabel == null || categoryControl == null) {
            return;
        }

        String category = categoryControl.getValue();

        if (category == null) {
            categoryBindingLabel.setText("Key: none");
            return;
        }

        for (Map.Entry<String, String> entry : categoryKeyBindings.entrySet()) {
            if (entry.getValue().equals(category)) {
                categoryBindingLabel.setText("Key: " + displayKeyName(entry.getKey()));
                return;
            }
        }

        categoryBindingLabel.setText("Key: none");
    }

    private String displayKeyName(String keyName) {
        try {
            return KeyCode.valueOf(keyName).getName();
        } catch (IllegalArgumentException e) {
            return keyName;
        }
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
            updatingMetadataControls = true;
            categoryControl.setValue(polygon.getCategoryName());
            confidenceControl.setValue(polygon.getConfidence());
            uncertaintyControl.setText(polygon.getUncertaintyReason());
            updatingMetadataControls = false;
        }

        if (selectionLabel != null) {
            selectionLabel.setText("Selected: " + polygon.getCategoryName());
        }
    }

    private void applySelectedMetadata() {
        if (updatingMetadataControls || selectedPolygon == null) {
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


    private void saveProject(Stage stage, TextField operationIdInput) {
        if (currentVideoFile == null) {
            setStatus("Open a video before saving project");
            return;
        }

        File outputFile = currentProjectFile;

        if (outputFile == null) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Annotation Project");
            fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Medical Video Annotator Project", "*.mvap.json")
            );
            fileChooser.setInitialFileName(resolveOperationId(operationIdInput) + ".mvap.json");
            outputFile = fileChooser.showSaveDialog(stage);

            if (outputFile == null) {
                return;
            }
        }

        try {
            saveProjectToFile(outputFile, operationIdInput);
            currentProjectFile = outputFile;
            setStatus("Saved project: " + outputFile.getName());
        } catch (Exception e) {
            setStatus("Save project failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadProject(Stage stage, TextField operationIdInput, Label frameLabel) {
        if ((currentVideoFile != null || !annotationProject.getAllPolygons().isEmpty() || !currentPolygonPoints.isEmpty())
                && !confirmDiscardAnnotations(stage)) {
            setStatus("Kept current annotation project");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Load Annotation Project");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Medical Video Annotator Project", "*.mvap.json", "*.json")
        );
        File inputFile = fileChooser.showOpenDialog(stage);

        if (inputFile == null) {
            return;
        }

        try {
            ProjectFileData projectData = projectFileStore.load(inputFile);
            File videoFile = projectData.getVideoFile();

            if (videoFile == null || !videoFile.exists()) {
                setStatus("Project video not found. Expected: " + (videoFile == null ? "empty path" : videoFile.getAbsolutePath()));
                return;
            }

            annotationProject.clear();
            annotationProject.addAllPolygons(projectData.getPolygons());
            annotationProject.setFrameMetadata(projectData.getProblemFrameIndexes(), projectData.getFrameQualities());
            currentPolygonPoints.clear();
            currentPolygonPoints.addAll(projectData.getCurrentPolygonPoints());
            clearSelection();

            currentProjectFile = inputFile;
            currentVideoFile = videoFile;
            importedOperationId = projectData.getOperationId();
            operationIdInput.setText(projectData.getOperationId());

            if (!projectData.getCategories().isEmpty()) {
                categoryControl.getItems().setAll(projectData.getCategories());
                categoryControl.setValue(projectData.getCategories().getFirst());
                saveCategories(projectData.getCategories());
            }
            addImportedCategories(projectData.getPolygons());

            currentImage = videoFrameReader.openVideoAndReadFirstFrame(videoFile);
            int frameIndex = Math.max(0, projectData.getCurrentFrameIndex());
            if (frameIndex > 0) {
                Image restoredFrame = videoFrameReader.readFrameAt(frameIndex);
                if (restoredFrame != null) {
                    currentImage = restoredFrame;
                }
            }

            updateCurrentImageInfo(currentImage);
            if (projectData.getImageWidth() > 0) {
                currentImageWidth = projectData.getImageWidth();
            }
            if (projectData.getImageHeight() > 0) {
                currentImageHeight = projectData.getImageHeight();
            }

            refreshAnnotatedFrames();
            redrawCanvas();
            updateFrameLabel(frameLabel);
            setStatus("Loaded project: " + inputFile.getName());
        } catch (Exception e) {
            setStatus("Load project failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startAutosave() {
        if (autosaveTimeline != null) {
            return;
        }

        autosaveTimeline = new Timeline(new KeyFrame(Duration.seconds(60), event -> autosaveProject()));
        autosaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autosaveTimeline.play();
    }

    private void autosaveProject() {
        if (currentVideoFile == null) {
            return;
        }
        if (annotationProject.getAllPolygons().isEmpty() && currentPolygonPoints.isEmpty()) {
            return;
        }

        try {
            File autosaveTarget = currentProjectFile == null ? buildAutosaveFile() : currentProjectFile;
            saveProjectToFile(autosaveTarget, operationIdControl);
        } catch (Exception e) {
            setStatus("Autosave failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private File buildAutosaveFile() throws IOException {
        File autosaveDirectory = new File(
                System.getProperty("user.home"),
                ".medical-video-annotator" + File.separator + "autosave"
        );

        if (!autosaveDirectory.exists() && !autosaveDirectory.mkdirs()) {
            throw new IOException("Could not create autosave directory: " + autosaveDirectory.getAbsolutePath());
        }

        String operationId = operationIdControl == null ? buildOperationIdFromVideoFile() : resolveOperationId(operationIdControl);
        if (operationId == null || operationId.isBlank()) {
            operationId = "autosave";
        }

        return new File(autosaveDirectory, operationId + ".autosave.mvap.json");
    }

    private void saveProjectToFile(File outputFile, TextField operationIdInput) throws IOException {
        String operationId = operationIdInput == null ? buildOperationIdFromVideoFile() : resolveOperationId(operationIdInput);

        if (operationId == null) {
            operationId = "UNKNOWN_OPERATION";
        }

        if (operationIdInput != null) {
            operationIdInput.setText(operationId);
        }

        projectFileStore.save(
                outputFile,
                annotationProject,
                currentPolygonPoints,
                currentVideoFile,
                operationId,
                videoFrameReader.getCurrentFrameIndex(),
                (int) currentImageWidth,
                (int) currentImageHeight,
                getCategoryNames()
        );
    }

    private void resetView() {
        canvasRenderer.resetView();
        redrawCanvas();
        updateZoomLabel();
        setStatus("Reset view");
    }

    private void reloadCurrentFrame() {
        try {
            Image frame = videoFrameReader.readCurrentFrameAgain();

            if (frame == null) {
                setStatus("Could not reload current frame");
                return;
            }

            currentImage = frame;
            updateCurrentImageInfo(currentImage);
            redrawCanvas();
            setStatus("Reloaded current frame safely");
        } catch (Exception e) {
            setStatus("Reload frame failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void toggleProblemFrame() {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0) {
            setStatus("Open a video before marking frames");
            return;
        }

        boolean problem = annotationProject.toggleProblemFrame(frameIndex);
        updateFrameMetadataControls();
        setStatus((problem ? "Marked" : "Unmarked") + " problem frame: " + frameIndex);
    }

    private void updateCurrentFrameQuality(String quality) {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0 || quality == null) {
            return;
        }

        annotationProject.setFrameQuality(frameIndex, quality);
        updateFrameMetadataControls();
    }

    private void updateFrameMetadataControls() {
        int frameIndex = videoFrameReader.getCurrentFrameIndex();

        if (frameIndex < 0) {
            if (frameStatusLabel != null) {
                frameStatusLabel.setText("Frame status: -");
            }
            return;
        }

        boolean problem = annotationProject.isProblemFrame(frameIndex);
        String quality = annotationProject.getFrameQuality(frameIndex);

        if (frameQualityControl != null && !quality.equals(frameQualityControl.getValue())) {
            frameQualityControl.setValue(quality);
        }

        if (frameStatusLabel != null) {
            frameStatusLabel.setText("Frame status: " + (problem ? "problem" : "ok") + ", quality: " + quality);
        }
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setText(String.format("Zoom: %.0f%%", canvasRenderer.getZoom() * 100));
        }
    }

    private boolean confirmExportValidation(Stage stage) {
        CocoExportValidationResult validationResult = cocoExportValidator.validate(
                annotationProject,
                (int) currentImageWidth,
                (int) currentImageHeight,
                getCategoryNames(),
                !currentPolygonPoints.isEmpty()
        );
        StringBuilder message = new StringBuilder();
        message.append("Annotated frames: ").append(annotationProject.getAnnotatedFrameCount()).append("\n");
        message.append("Polygons: ").append(annotationProject.getPolygonCount()).append("\n");
        message.append("Problem frames: ").append(annotationProject.getProblemFrameIndexes().size()).append("\n");
        message.append("Non-ok frame quality: ").append(annotationProject.getFrameQualities().size()).append("\n");
        appendCategorySummary(message);

        if (validationResult.hasErrors()) {
            appendValidationMessages(message, "Errors", validationResult.getErrors());
            Alert alert = new Alert(Alert.AlertType.ERROR, message.toString(), ButtonType.OK);
            alert.initOwner(stage);
            alert.setTitle("COCO export validation");
            alert.setHeaderText("Fix validation errors before export");
            alert.showAndWait();
            return false;
        }

        if (validationResult.hasWarnings()) {
            appendValidationMessages(message, "Warnings", validationResult.getWarnings());
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message.toString(), ButtonType.CANCEL, ButtonType.OK);
        alert.initOwner(stage);
        alert.setTitle("COCO export validation");
        alert.setHeaderText(validationResult.hasWarnings()
                ? "Review warnings before COCO export"
                : "COCO export validation passed");

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private void appendCategorySummary(StringBuilder message) {
        message.append("\nBy category:\n");
        boolean hasCategoryRows = false;

        for (String category : getCategoryNames()) {
            int count = 0;
            for (AnnotationPolygon polygon : annotationProject.getAllPolygons()) {
                if (category.equals(polygon.getCategoryName())) {
                    count++;
                }
            }
            if (count > 0) {
                message.append(category).append(": ").append(count).append("\n");
                hasCategoryRows = true;
            }
        }

        if (!hasCategoryRows) {
            message.append("No category counts.\n");
        }
    }

    private void appendValidationMessages(StringBuilder message, String title, List<String> validationMessages) {
        message.append("\n").append(title).append(":\n");
        int maxVisibleMessages = 12;

        for (int i = 0; i < validationMessages.size() && i < maxVisibleMessages; i++) {
            message.append("- ").append(validationMessages.get(i)).append("\n");
        }

        if (validationMessages.size() > maxVisibleMessages) {
            message.append("- ... and ").append(validationMessages.size() - maxVisibleMessages).append(" more\n");
        }
    }
    private double calculatePolygonArea(AnnotationPolygon polygon) {
        List<Point2D> points = polygon.getPoints();

        if (points.size() < 3) {
            return 0;
        }

        double sum = 0;

        for (int i = 0; i < points.size(); i++) {
            Point2D current = points.get(i);
            Point2D next = points.get((i + 1) % points.size());
            sum += current.getX() * next.getY();
            sum -= next.getX() * current.getY();
        }

        return Math.abs(sum) / 2.0;
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
        if (!confirmExportValidation(stage)) {
            setStatus("Export cancelled");
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
            addImportedCategories(importResult.getPolygons());
            currentProjectFile = null;
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
            currentProjectFile = null;

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
        updateFrameMetadataControls();
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
                getCategoryNames(),
                polygonsForCurrentFrame,
                currentPolygonPoints,
                selectedPolygon,
                hoveredPoint
        );
    }
}

