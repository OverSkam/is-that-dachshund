package overskam.projectH;

import javafx.application.Application;
import overskam.projectH.controller.AnnotationController;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class MainApp extends Application {

    private static final double CANVAS_WIDTH = 900;
    private static final double CANVAS_HEIGHT = 600;

    @Override
    public void start(Stage stage) {
        Canvas canvas = new Canvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        Button openVideoButton = new Button("Open Video");
        Button previousFrameButton = new Button("Prev Frame");
        Button nextFrameButton = new Button("Next Frame");
        Button closePolygonButton = new Button("Close Polygon");
        Button exportCocoButton = new Button("Export COCO");
        Button importCocoButton = new Button("Import COCO");
        TextField uncertaintyInput = new TextField();
        uncertaintyInput.setPromptText("Uncertainty");
        uncertaintyInput.setPrefWidth(120);
        uncertaintyInput.setText("none");

        ToggleButton drawModeButton = new ToggleButton("Draw");
        ToggleButton editModeButton = new ToggleButton("Edit");

        ToggleGroup modeToggleGroup = new ToggleGroup();
        drawModeButton.setToggleGroup(modeToggleGroup);
        editModeButton.setToggleGroup(modeToggleGroup);

        drawModeButton.setSelected(true);

        TextField frameInput = new TextField();
        frameInput.setPromptText("Frame #");
        frameInput.setPrefWidth(80);

        Button goToFrameButton = new Button("Go");

        Label frameLabel = new Label("Frame: -");
        Label modeLabel = new Label("Mode: DRAW");

        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll(
                "gallbladder",
                "cystic_duct",
                "cystic_artery",
                "liver",
                "instrument"
        );
        categoryComboBox.setValue("gallbladder");

        ComboBox<String> confidenceComboBox = new ComboBox<>();
        confidenceComboBox.getItems().addAll(
                "high",
                "medium",
                "low"
        );
        confidenceComboBox.setValue("high");

        HBox toolbar = new HBox(
                8,
                openVideoButton,
                previousFrameButton,
                nextFrameButton,
                frameInput,
                goToFrameButton,
                drawModeButton,
                editModeButton,
                categoryComboBox,
                confidenceComboBox,
                uncertaintyInput,
                closePolygonButton,
                importCocoButton,
                exportCocoButton,
                frameLabel,
                modeLabel
        );

        BorderPane root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(canvas);

        Scene scene = new Scene(root);

        AnnotationController controller = new AnnotationController(
                gc,
                CANVAS_WIDTH,
                CANVAS_HEIGHT
        );

        controller.showInitialImage("/tb.png");
        controller.connectMouse(canvas);
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
                importCocoButton,
                exportCocoButton,
                drawModeButton,
                editModeButton,
                frameLabel,
                modeLabel,
                categoryComboBox,
                confidenceComboBox,
                uncertaintyInput
        );

        stage.setTitle("Medical Video Annotator");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
