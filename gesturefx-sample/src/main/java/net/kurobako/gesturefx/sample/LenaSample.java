package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.GesturePaneOps;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.OptionalDouble;
import java.util.ResourceBundle;

import javafx.animation.Interpolator;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Dimension2D;
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
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;
import sun.security.krb5.internal.PAData;

import static javafx.collections.FXCollections.*;

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

		@FXML private TextField x;
		@FXML private TextField y;
		@FXML private TextField scale;
		@FXML private Button apply;
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
					view.setImage(new Image(LENA, true));
				}

			});

			pane.animate(Duration.millis(200))
					.interpolateWith(Interpolator.EASE_BOTH)
					.beforeStart(() -> System.out.println("Starting..."))
					.afterFinished(() -> System.out.println("Done!"))
					.centreOn(new Point2D(42, 42));

			pane.animate(Duration.millis(200)).zoomTo(1);

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

			x.setTextFormatter(createDecimalFormatter(new DecimalFormat("#.0")));
			y.setTextFormatter(createDecimalFormatter(new DecimalFormat("#.0")));
			scale.setTextFormatter(createDecimalFormatter(new DecimalFormat("#.0")));

			scale.disableProperty().bind(translate.selectedProperty());

			apply.setOnAction(e -> {
				Toggle toggle = type.getSelectedToggle();

				OptionalDouble xOp = parseDouble(x.getText());
				OptionalDouble yOp = parseDouble(y.getText());


				GesturePaneOps ops = !animated.isSelected() ?
						                     pane :
						                     pane.animate(DURATION)
								                     .interpolateWith(Interpolator.EASE_BOTH);

				Point2D d = new Point2D(xOp.orElse(0), yOp.orElse(0));
				if (toggle == translate) {
					if (!relative.isSelected()) ops.centreOn(d);
					else ops.translateBy(new Dimension2D(d.getX(), d.getY()));
				} else if (toggle == zoom) {
					double _zoom = parseDouble(zoom.getText()).orElse(1);
					if (!relative.isSelected()) ops.zoomTo(_zoom);
					else ops.zoomBy(_zoom);
				}
			});


			// TODO wire up x and y translation


			currentX.textProperty().bind(pane.currentXProperty().asString(FORMAT));
			currentY.textProperty().bind(pane.currentYProperty().asString(FORMAT));
		}


	}


	private static TextFormatter<String> createDecimalFormatter(DecimalFormat format) {
		return new TextFormatter<String>(c -> {
			if (c.getControlNewText().isEmpty()) return c;
			ParsePosition pos = new ParsePosition(0);
			Number result = format.parse(c.getControlNewText(), pos);
			if (result == null || pos.getIndex() < c.getControlNewText().length()) {
				return null;
			} else return c;
		});
	}

	private static OptionalDouble parseDouble(String text) {
		try {
			return OptionalDouble.of(Double.parseDouble(text));
		} catch (NumberFormatException e) {
			return OptionalDouble.empty();
		}
	}

}
