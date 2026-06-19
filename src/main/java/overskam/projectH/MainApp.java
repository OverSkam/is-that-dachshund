package overskam.projectH;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.scene.control.ComboBox;

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

        TextField frameInput = new TextField();
        frameInput.setPromptText("Frame #");
        frameInput.setPrefWidth(80);

        Button goToFrameButton = new Button("Go");

        Label frameLabel = new Label("Frame: -");

        ComboBox<String> categoryComboBox = new ComboBox<>();
        categoryComboBox.getItems().addAll(
                "gallbladder",
                "cystic_duct",
                "cystic_artery",
                "liver",
                "instrument"
        );
        categoryComboBox.setValue("gallbladder");

        HBox toolbar = new HBox(
                8,
                openVideoButton,
                previousFrameButton,
                nextFrameButton,
                frameInput,
                goToFrameButton,
                categoryComboBox,
                closePolygonButton,
                frameLabel
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
        controller.connectKeyboard(scene, categoryComboBox);
        controller.connectButtons(
                stage,
                openVideoButton,
                previousFrameButton,
                nextFrameButton,
                frameInput,
                goToFrameButton,
                closePolygonButton,
                frameLabel,
                categoryComboBox
        );

        stage.setTitle("Medical Video Annotator");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}