package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import java.awt.image.VolatileImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;

import static javafx.collections.FXCollections.*;
import static javafx.scene.control.SpinnerValueFactory.*;

public class LenaSample implements Sample {

	static final String FORMAT = "%.5f";
	static final Duration DURATION = Duration.millis(300);
	static final String LENA = LenaSample.class.getResource("/lena.png").toExternalForm();
	static final String WIDE_PANAMA_URL =
			"https://upload.wikimedia.org/wikipedia/commons/6/65/Jokulsarlon_Panorama.jpg";

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
		@FXML private MenuItem wide;
		@FXML private MenuItem selectFile;
		@FXML private ProgressBar progress;

		@FXML private ComboBox<FitMode> fitMode;
		@FXML private ComboBox<ScrollMode> scrollMode;

		@FXML private CheckBox gesture;
		@FXML private CheckBox verticalScrollBar;
		@FXML private CheckBox horizontalScrollBar;

		@FXML private CheckBox clip;
		@FXML private CheckBox fitWidth;
		@FXML private CheckBox fitHeight;

		@FXML private Button reset;

		@FXML private Spinner<Double> x;
		@FXML private Spinner<Double> y;
		@FXML private Button apply;
		@FXML private Spinner<Double> scale;
		@FXML private ToggleGroup type;
		@FXML private RadioButton translate;
		@FXML private RadioButton zoom;
		@FXML private CheckBox relative;
		@FXML private CheckBox animated;

		@FXML private Label minScale;
		@FXML private Label maxScale;
		@FXML private Label currentScale;
		@FXML private Label zoomFactor;
		@FXML private Slider zoomFactorSlider;
		@FXML private Slider minScaleSlider;
		@FXML private Slider maxScaleSlider;
		// TODO wire up
		@FXML private Slider currentScaleSlider;
		@FXML private Slider currentXSlider;
		@FXML private Slider currentYSlider;
		@FXML private Label currentX;
		@FXML private Label currentY;


		@Override
		public void initialize(URL location, ResourceBundle resources) {
			ImageView view = new ImageView(LENA);
			GesturePane pane = new GesturePane(view);
			// so that extremely large images does not push things offscreen
			pane.setFitWidth(false);
			pane.setFitHeight(false);

			viewport.getChildren().add(pane);


			lena.setOnAction(e -> view.setImage(new Image(LENA, true)));
			wide.setOnAction(e -> view.setImage(new Image(WIDE_PANAMA_URL, true)));
			view.imageProperty().addListener((l, p, n) -> {
				if (n == null) return;
				progress.progressProperty().bind(n.progressProperty());
			});
			selectFile.setOnAction(e -> {
				FileChooser chooser = new FileChooser();
				chooser.setTitle("Select image");
				chooser.setSelectedExtensionFilter(new ExtensionFilter("JavaFX supported image",
						                                                      "*.png", "*.jpg", "*.gif"));
				File selected = chooser.showOpenDialog(root.getScene().getWindow());
				if (selected == null) return;
				try {
					view.setImage(new Image(new FileInputStream(selected)));
				} catch (FileNotFoundException ex) {
					new Alert(AlertType.ERROR,
							         "Unable to open image file: " + ex.getMessage(),
							         ButtonType.OK)
							.showAndWait();
					view.setImage(new Image(LENA,true));
				}

			});

			fitMode.setItems(observableArrayList(FitMode.values()));
			fitMode.setValue(pane.getFitMode());
			pane.fitModeProperty().bind(fitMode.valueProperty());

			scrollMode.setItems(observableArrayList(ScrollMode.values()));
			scrollMode.setValue(pane.getScrollMode());
			pane.scrollModeProperty().bind(scrollMode.valueProperty());

			clip.setSelected(pane.isClipEnabled());
			pane.clipEnabledProperty().bind(clip.selectedProperty());
			fitWidth.setSelected(pane.isFitWidth());
			pane.fitWidthProperty().bind(fitWidth.selectedProperty());
			fitHeight.setSelected(pane.isFitHeight());
			pane.fitHeightProperty().bind(fitHeight.selectedProperty());

			gesture.setSelected(pane.isGestureEnabled());
			pane.gestureEnabledProperty().bind(gesture.selectedProperty());
			verticalScrollBar.setSelected(pane.isVBarEnabled());
			pane.vBarEnabledProperty().bind(verticalScrollBar.selectedProperty());
			horizontalScrollBar.setSelected(pane.isHBarEnabled());
			pane.hBarEnabledProperty().bind(horizontalScrollBar.selectedProperty());

			minScale.textProperty().bind(pane.minScaleProperty().asString(FORMAT));
			maxScale.textProperty().bind(pane.maxScaleProperty().asString(FORMAT));
			currentScale.textProperty().bind(pane.currentScaleProperty().asString(FORMAT));
			zoomFactor.textProperty().bind(pane.scrollZoomFactorProperty().asString
					                                                               (FORMAT));

			minScaleSlider.setValue(pane.getMinScale());
			pane.minScaleProperty().bind(minScaleSlider.valueProperty());
			maxScaleSlider.setValue(pane.getMaxScale());
			pane.maxScaleProperty().bind(maxScaleSlider.valueProperty());
//			currentScaleSlider.setValue(gesturePane.getCurrentScale());
//			gesturePane.currentScaleProperty().bind(currentScaleSlider.valueProperty());
			zoomFactorSlider.setValue(pane.getScrollZoomFactor());
			pane.scrollZoomFactorProperty().bind(zoomFactorSlider.valueProperty());

			reset.setOnAction(e -> pane.reset());


			DoubleSpinnerValueFactory xFactory = new DoubleSpinnerValueFactory(0, 1);
			xFactory.maxProperty().bind(view.getImage().widthProperty());
			xFactory.setWrapAround(true);
			xFactory.setAmountToStepBy(1);
			x.setValueFactory(xFactory);

			DoubleSpinnerValueFactory yFactory = new DoubleSpinnerValueFactory(0, 1);
			yFactory.maxProperty().bind(view.getImage().heightProperty());
			yFactory.setWrapAround(true);
			yFactory.setAmountToStepBy(1);
			y.setValueFactory(yFactory);

			DoubleSpinnerValueFactory zoomFactory = new DoubleSpinnerValueFactory(1, 1);
			zoomFactory.maxProperty().bind(pane.maxScaleProperty());
			zoomFactory.setWrapAround(true);
			zoomFactory.setAmountToStepBy(0.25);
			scale.setValueFactory(zoomFactory);


			scale.disableProperty().bind(translate.selectedProperty());

			apply.setOnAction(e -> {
				Toggle toggle = type.getSelectedToggle();
				Point2D d = new Point2D(x.getValue(), y.getValue());
				if (toggle == translate) {
					if (animated.isSelected()) pane.translateTarget(d, DURATION, null);
					else pane.translateTarget(d, relative.isSelected());
				} else if (toggle == zoom) {
					if (animated.isSelected()) pane.zoomTarget(scale.getValue(), DURATION, null);
					else pane.zoomTarget(scale.getValue(), relative.isSelected());
				}
			});


			// TODO wire up x and y translation


			currentX.textProperty().bind(pane.currentXProperty().asString(FORMAT));
			currentY.textProperty().bind(pane.currentYProperty().asString(FORMAT));
		}


	}

}
