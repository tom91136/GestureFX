package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.SamplerController.Sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;

import static javafx.collections.FXCollections.*;

public class LenaSample implements Sample {

	public static final String FORMAT = "%.5f";
	@Override
	public Node mkRoot() {
		try {
			return FXMLLoader.load(getClass().getResource("/Lena.fxml"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public static class GesturePropertyController implements Initializable {

		@FXML private HBox root;
		@FXML private StackPane viewport;
		@FXML private MenuItem lena;
		@FXML private MenuItem selectFile;
		@FXML private ComboBox<FitMode> fitMode;
		@FXML private ComboBox<ScrollMode> scrollMode;
		@FXML private CheckBox gesture;
		@FXML private CheckBox verticalScrollBar;
		@FXML private CheckBox horizontalScrollBar;
		@FXML private Label minScale;
		@FXML private Label maxScale;
		@FXML private Label currentScale;
		@FXML private Label zoomFactor;
		@FXML private Slider zoomFactorSlider;
		@FXML private Slider minScaleSlider;
		@FXML private Slider maxScaleSlider;
		@FXML private Slider currentScaleSlider;

		@FXML private Button reset;


		private static final String LENA = LenaSample.class.getResource("/lena.png")
				                                   .toExternalForm();

		@Override
		public void initialize(URL location, ResourceBundle resources) {
			ImageView view = new ImageView(LENA);
			GesturePane gesturePane = new GesturePane(view);

			viewport.getChildren().add(gesturePane);


			lena.setOnAction(e -> view.setImage(new Image(LENA)));
			selectFile.setOnAction(e -> {
				FileChooser chooser = new FileChooser();
				chooser.setTitle("Select image");
				chooser.setSelectedExtensionFilter(new ExtensionFilter("JavaFX supported image",
				                                                       "*.png", "*.jpg", "*.gif"));
				File selected = chooser.showOpenDialog(root.getScene().getWindow());
				try {
					view.setImage(new Image(new FileInputStream(selected)));
				} catch (FileNotFoundException ex) {
					new Alert(
							         AlertType.ERROR,
							         "Unable to open image file: " + ex.getMessage(),
							         ButtonType.OK)
							.showAndWait();
					view.setImage(new Image(LENA));
				}

			});

			fitMode.setItems(observableArrayList(FitMode.values()));
			fitMode.setValue(gesturePane.getFitMode());
			gesturePane.fitModeProperty().bind(fitMode.valueProperty());

			scrollMode.setItems(observableArrayList(ScrollMode.values()));
			scrollMode.setValue(gesturePane.getScrollMode());
			gesturePane.scrollModeProperty().bind(scrollMode.valueProperty());

			gesture.setSelected(gesturePane.isGestureEnabled());
			gesturePane.gestureEnabledProperty().bind(gesture.selectedProperty());

			verticalScrollBar.setSelected(gesturePane.isVBarEnabled());
			gesturePane.vBarEnabledProperty().bind(verticalScrollBar.selectedProperty());

			horizontalScrollBar.setSelected(gesturePane.isHBarEnabled());
			gesturePane.hBarEnabledProperty().bind(horizontalScrollBar.selectedProperty());

			minScale.textProperty().bind(gesturePane.minScaleProperty().asString(FORMAT));
			maxScale.textProperty().bind(gesturePane.maxScaleProperty().asString(FORMAT));
			currentScale.textProperty().bind(gesturePane.currentScaleProperty().asString(FORMAT));
			zoomFactor.textProperty().bind(gesturePane.scrollZoomFactorProperty().asString(FORMAT));

			minScaleSlider.setValue(gesturePane.getMinScale());
			gesturePane.minScaleProperty().bind(minScaleSlider.valueProperty());
			maxScaleSlider.setValue(gesturePane.getMinScale());
			gesturePane.maxScaleProperty().bind(maxScaleSlider.valueProperty());
//			currentScaleSlider.setValue(gesturePane.getCurrentScale());
//			gesturePane.currentScaleProperty().bind(currentScaleSlider.valueProperty());
			zoomFactorSlider.setValue(gesturePane.getScrollZoomFactor());
			gesturePane.scrollZoomFactorProperty().bind(zoomFactorSlider.valueProperty());

			reset.setOnAction(e -> gesturePane.reset());

		}


	}

}
