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
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;

import static javafx.collections.FXCollections.*;
import static javafx.scene.control.SpinnerValueFactory.*;

public class LenaSample implements Sample {

	static final String FORMAT = "%.5f";
	static final String LENA = LenaSample.class.getResource("/lena.png").toExternalForm();
	public static final Duration DURATION = Duration
			                                        .millis(300);

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
		@FXML private Slider currentXSlider;
		@FXML private Slider currentYSlider;
		@FXML private Label currentX;
		@FXML private Label currentY;

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
				if (selected == null) return;
				try {
					view.setImage(new Image(new FileInputStream(selected)));
				} catch (FileNotFoundException ex) {
					new Alert(AlertType.ERROR,
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
			zoomFactor.textProperty().bind(gesturePane.scrollZoomFactorProperty().asString
					                                                                      (FORMAT));

			minScaleSlider.setValue(gesturePane.getMinScale());
			gesturePane.minScaleProperty().bind(minScaleSlider.valueProperty());
			maxScaleSlider.setValue(gesturePane.getMaxScale());
			gesturePane.maxScaleProperty().bind(maxScaleSlider.valueProperty());
//			currentScaleSlider.setValue(gesturePane.getCurrentScale());
//			gesturePane.currentScaleProperty().bind(currentScaleSlider.valueProperty());
			zoomFactorSlider.setValue(gesturePane.getScrollZoomFactor());
			gesturePane.scrollZoomFactorProperty().bind(zoomFactorSlider.valueProperty());

			reset.setOnAction(e -> gesturePane.reset());


//			view.fitWidthProperty().addListener((o, p, n) -> {
//				double v = n.doubleValue();
//			});
//			view.fitHeightProperty().addListener((o, p, n) -> {
//				double v = n.doubleValue();
//			});


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
			zoomFactory.maxProperty().bind(gesturePane.maxScaleProperty());
			zoomFactory.setWrapAround(true);
			zoomFactory.setAmountToStepBy(0.25);
			scale.setValueFactory(zoomFactory);


			scale.disableProperty().bind(translate.selectedProperty());

			apply.setOnAction(e -> {
				Toggle toggle = type.getSelectedToggle();
				Point2D d = new Point2D(x.getValue(), y.getValue());
				if (toggle == translate) {
					gesturePane.translateTo(d, DURATION, null);
				} else if (toggle == zoom) {
					gesturePane.zoomTo(scale.getValue(), DURATION, null);
				}
			});


			// TODO wire up x and y translation

		}


	}

}
