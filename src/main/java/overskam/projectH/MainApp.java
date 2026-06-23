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
        Button closePolygonButton = new Button("Close Polygon");
        Button applyMetadataButton = new Button("Apply Metadata");
        Button deleteSelectedButton = new Button("Delete Selected");
        Button clearFrameButton = new Button("Clear Frame");
        Button importCocoButton = new Button("Import COCO");
        Button exportCocoButton = new Button("Export COCO");

        TextField frameInput = new TextField();
        frameInput.setPromptText("Frame #");

        TextField operationIdInput = new TextField();
        operationIdInput.setPromptText("Operation ID");

        ToggleButton drawModeButton = new ToggleButton("Draw");
        ToggleButton editModeButton = new ToggleButton("Edit");
        ToggleGroup modeToggleGroup = new ToggleGroup();
        drawModeButton.setToggleGroup(modeToggleGroup);
        editModeButton.setToggleGroup(modeToggleGroup);
        drawModeButton.setSelected(true);

        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll(
                "gallbladder",
                "cystic_duct",
                "cystic_artery",
                "liver",
                "instrument"
        );
        categoryComboBox.setValue("gallbladder");
        categoryComboBox.setMaxWidth(Double.MAX_VALUE);

        ComboBox<String> confidenceComboBox = new ComboBox<>();
        confidenceComboBox.getItems().addAll("high", "medium", "low");
        confidenceComboBox.setValue("high");
        confidenceComboBox.setMaxWidth(Double.MAX_VALUE);

        TextField uncertaintyInput = new TextField();
        uncertaintyInput.setPromptText("Uncertainty reason");
        uncertaintyInput.setText("none");

        ListView<AnnotatedFrame> annotatedFramesList = new ListView<>();

        Label frameLabel = new Label("Frame: -");
        Label modeLabel = new Label("Mode: DRAW");
        Label selectionLabel = new Label("Selected: none");
        Label statusLabel = new Label("Ready");

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
                frameLabel,
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

        VBox annotationPanel = new VBox(
                8,
                sectionLabel("ANNOTATION"),
                modeButtons,
                modeLabel,
                new Separator(),
                labeledControl("Category", categoryComboBox),
                labeledControl("Confidence", confidenceComboBox),
                labeledControl("Uncertainty", uncertaintyInput),
                closePolygonButton,
                new Separator(),
                sectionLabel("SELECTED POLYGON"),
                selectionLabel,
                applyMetadataButton,
                deleteSelectedButton,
                clearFrameButton,
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
                applyMetadataButton,
                deleteSelectedButton,
                clearFrameButton,
                importCocoButton,
                exportCocoButton,
                operationIdInput,
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
                closePolygonButton,
                applyMetadataButton,
                deleteSelectedButton,
                clearFrameButton,
                importCocoButton,
                exportCocoButton,
                drawModeButton,
                editModeButton,
                frameLabel,
                modeLabel,
                selectionLabel,
                statusLabel,
                operationIdInput,
                categoryComboBox,
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
        VBox box = new VBox(3, label, control);
        return box;
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