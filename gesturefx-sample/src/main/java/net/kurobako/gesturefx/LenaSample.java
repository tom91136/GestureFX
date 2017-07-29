package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.SamplerController.Sample;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;

import static javafx.collections.FXCollections.*;

public class LenaSample implements Sample {

	@Override
	public Node mkRoot() {
		try {
			return FXMLLoader.load(getClass().getResource("/Lena.fxml"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public static class GesturePropertyController implements Initializable {

		@FXML private StackPane viewport;
		@FXML private ComboBox<FitMode> fitMode;
		@FXML private ComboBox<ScrollMode> scrollMode;
		@FXML private CheckBox gesture;
		@FXML private CheckBox verticalScrollBar;
		@FXML private CheckBox horizontalScrollBar;
		@FXML private Label minScale;
		@FXML private Label maxScale;
		@FXML private Label currentScale;
		@FXML private Button reset;


		@Override
		public void initialize(URL location, ResourceBundle resources) {
			Node node = new ImageView(getClass().getResource("/lena.png").toExternalForm());
			GesturePane gesturePane = new GesturePane(node, ScrollMode.ZOOM);

			viewport.getChildren().add(gesturePane);

			gesturePane.fitModeProperty().bind(fitMode.valueProperty());
			fitMode.setItems(observableArrayList(FitMode.values()));
			fitMode.getSelectionModel().selectFirst();

			gesturePane.scrollModeProperty().bind(scrollMode.valueProperty());
			scrollMode.setItems(observableArrayList(ScrollMode.values()));
			scrollMode.getSelectionModel().selectFirst();

			gesturePane.gestureProperty().bind(gesture.selectedProperty());
			gesturePane.verticalScrollBarEnabledProperty()
					.bind(verticalScrollBar.selectedProperty());
			gesturePane.horizontalScrollBarEnabledProperty()
					.bind(horizontalScrollBar.selectedProperty());

			minScale.textProperty().bind(gesturePane.minScaleProperty().asString());
			maxScale.textProperty().bind(gesturePane.maxScaleProperty().asString());
			currentScale.textProperty().bind(gesturePane.currentScaleProperty().asString());

			reset.setOnAction(e -> gesturePane.reset());

		}


	}

}
