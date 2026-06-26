package overskam.projectH;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import overskam.projectH.controller.AnnotationController;
import overskam.projectH.model.AnnotatedFrame;

public class MainApp extends Application {

    private static final double CANVAS_WIDTH = 900;
    private static final double CANVAS_HEIGHT = 600;
    private static final double SIDE_PANEL_WIDTH = 200;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Button openVideoButton = new Button("Open Video");
        Button previousFrameButton = new Button("Previous");
        Button nextFrameButton = new Button("Next");
        Button goToFrameButton = new Button("Go");
        Button resetViewButton = new Button("Reset View");
        Button reloadFrameButton = new Button("Reload Frame");
        Button markProblemFrameButton = new Button("Mark Problem Frame");
        Button closePolygonButton = new Button("Close Polygon");
        Button reopenPolygonButton = new Button("Reopen Last Polygon");
        Button deleteSelectedButton = new Button("Delete Selected");
        Button clearFrameButton = new Button("Clear Frame");
        Button saveProjectButton = new Button("Save Project");
        Button loadProjectButton = new Button("Load Project");
        Button importCocoButton = new Button("Import COCO");
        Button exportCocoButton = new Button("Export COCO");
        Button addCategoryButton = new Button("Add");
        Button bindCategoryKeyButton = new Button("Bind Key");

        TextField frameInput = new TextField();
        frameInput.setPromptText("Frame #");

        TextField operationIdInput = new TextField();
        operationIdInput.setPromptText("Operation ID");

        TextField newCategoryInput = new TextField();
        newCategoryInput.setPromptText("New class name");

        ToggleButton drawModeButton = new ToggleButton("Draw");
        ToggleButton editModeButton = new ToggleButton("Edit");
        ToggleGroup modeToggleGroup = new ToggleGroup();
        drawModeButton.setToggleGroup(modeToggleGroup);
        editModeButton.setToggleGroup(modeToggleGroup);
        drawModeButton.setSelected(true);

        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> frameQualityComboBox = new ComboBox<>();
        frameQualityComboBox.getItems().addAll("ok", "decode_artifact", "blurred", "occluded");
        frameQualityComboBox.setValue("ok");
        frameQualityComboBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> confidenceComboBox = new ComboBox<>();
        confidenceComboBox.getItems().addAll("high", "medium", "low");
        confidenceComboBox.setValue("high");
        confidenceComboBox.setMaxWidth(Double.MAX_VALUE);

        TextField uncertaintyInput = new TextField();
        uncertaintyInput.setPromptText("Uncertainty reason");
        uncertaintyInput.setText("none");

        ListView<AnnotatedFrame> annotatedFramesList = new ListView<>();

        Label frameLabel = new Label("Frame: -");
        Label frameStatusLabel = new Label("Frame status: ok");
        Label zoomLabel = new Label("Zoom: 100%");
        Label modeLabel = new Label("Mode: DRAW");
        Label selectionLabel = new Label("Selected: none");
        Label statusLabel = new Label("Ready");
        Label categoryBindingLabel = new Label("Key: none");

        Label appTitle = new Label("Medical Video Annotator");
        appTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox header = new HBox(appTitle);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(0, 0, 8, 0));

        VBox videoPanel = new VBox(
                8,
                sectionLabel("VIDEO"),
                openVideoButton,
                labeledControl("Operation ID", operationIdInput),
                new Separator(),
                sectionLabel("NAVIGATION"),
                new HBox(6, previousFrameButton, nextFrameButton),
                new HBox(6, frameInput, goToFrameButton),
                new HBox(6, resetViewButton, reloadFrameButton),
                markProblemFrameButton,
                labeledControl("Frame Quality", frameQualityComboBox),
                frameLabel,
                frameStatusLabel,
                zoomLabel,
                new Separator(),
                sectionLabel("ANNOTATED FRAMES"),
                annotatedFramesList
        );
        videoPanel.setPrefWidth(SIDE_PANEL_WIDTH);
        videoPanel.setPadding(new Insets(0, 10, 0, 0));
        VBox.setVgrow(annotatedFramesList, Priority.ALWAYS);

        HBox modeButtons = new HBox(6, drawModeButton, editModeButton);
        HBox.setHgrow(drawModeButton, Priority.ALWAYS);
        HBox.setHgrow(editModeButton, Priority.ALWAYS);
        drawModeButton.setMaxWidth(Double.MAX_VALUE);
        editModeButton.setMaxWidth(Double.MAX_VALUE);

        HBox addCategoryRow = new HBox(6, newCategoryInput, addCategoryButton);
        HBox.setHgrow(newCategoryInput, Priority.ALWAYS);

        HBox categoryKeyRow = new HBox(6, bindCategoryKeyButton, categoryBindingLabel);
        categoryKeyRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(categoryBindingLabel, Priority.ALWAYS);

        VBox annotationPanel = new VBox(
                8,
                sectionLabel("ANNOTATION"),
                modeButtons,
                modeLabel,
                new Separator(),
                labeledControl("Category", categoryComboBox),
                addCategoryRow,
                categoryKeyRow,
                labeledControl("Confidence", confidenceComboBox),
                labeledControl("Uncertainty", uncertaintyInput),
                closePolygonButton,
                reopenPolygonButton,
                new Separator(),
                sectionLabel("SELECTED POLYGON"),
                selectionLabel,
                deleteSelectedButton,
                clearFrameButton,
                new Separator(),
                sectionLabel("PROJECT"),
                saveProjectButton,
                loadProjectButton,
                new Separator(),
                sectionLabel("DATASET"),
                importCocoButton,
                exportCocoButton
        );
        annotationPanel.setPrefWidth(SIDE_PANEL_WIDTH);
        annotationPanel.setPadding(new Insets(0, 0, 0, 10));

        makeFullWidth(
                openVideoButton,
                closePolygonButton,
                reopenPolygonButton,
                deleteSelectedButton,
                clearFrameButton,
                saveProjectButton,
                loadProjectButton,
                importCocoButton,
                exportCocoButton,
                addCategoryButton,
                bindCategoryKeyButton,
                frameQualityComboBox,
                operationIdInput,
                newCategoryInput,
                uncertaintyInput
        );
        HBox.setHgrow(frameInput, Priority.ALWAYS);

        StackPane canvasPane = new StackPane(canvas);
        canvasPane.setPadding(new Insets(0, 8, 0, 8));
        canvasPane.setAlignment(Pos.TOP_CENTER);

        HBox statusBar = new HBox(statusLabel);
        statusBar.setPadding(new Insets(8, 0, 0, 0));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        BorderPane workspace = new BorderPane();
        workspace.setLeft(videoPanel);
        workspace.setCenter(canvasPane);
        workspace.setRight(annotationPanel);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(header);
        root.setCenter(workspace);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1340, 760);
        scene.getStylesheets().add(getClass().getResource("/dark-theme.css").toExternalForm());

        AnnotationController controller = new AnnotationController(gc, CANVAS_WIDTH, CANVAS_HEIGHT);
        controller.showInitialImage("/tb.png");
        controller.connectMouse(canvas);
        controller.connectAnnotatedFramesList(annotatedFramesList);
        controller.connectKeyboard(
                scene,
                categoryComboBox,
                modeLabel,
                drawModeButton,
                editModeButton,
                confidenceComboBox,
                uncertaintyInput
        );
        controller.connectButtons(
                stage,
                openVideoButton,
                previousFrameButton,
                nextFrameButton,
                frameInput,
                goToFrameButton,
                resetViewButton,
                reloadFrameButton,
                markProblemFrameButton,
                closePolygonButton,
                reopenPolygonButton,
                deleteSelectedButton,
                clearFrameButton,
                saveProjectButton,
                loadProjectButton,
                importCocoButton,
                exportCocoButton,
                drawModeButton,
                editModeButton,
                frameLabel,
                frameStatusLabel,
                zoomLabel,
                modeLabel,
                selectionLabel,
                statusLabel,
                operationIdInput,
                frameQualityComboBox,
                categoryComboBox,
                newCategoryInput,
                addCategoryButton,
                bindCategoryKeyButton,
                categoryBindingLabel,
                confidenceComboBox,
                uncertaintyInput
        );

        stage.setTitle("Medical Video Annotator");
        stage.setMinWidth(1180);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        return label;
    }

    private VBox labeledControl(String labelText, Control control) {
        Label label = new Label(labelText);
        return new VBox(3, label, control);
    }

    private void makeFullWidth(Control... controls) {
        for (Control control : controls) {
            control.setMaxWidth(Double.MAX_VALUE);
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
