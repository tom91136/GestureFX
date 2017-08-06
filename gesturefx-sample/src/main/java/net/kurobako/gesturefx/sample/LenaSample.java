package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.AffineEvent;
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
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Affine;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.util.Duration;

import static javafx.collections.FXCollections.observableArrayList;

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
		@FXML private Label zoomFactor;
		@FXML private Slider zoomFactorSlider;
		@FXML private Slider minScaleSlider;
		@FXML private Slider maxScaleSlider;
		@FXML private Label affine;


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

			// zoom*2 on double-click
			pane.setOnMouseClicked(e -> {
				if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
					Point2D pivotOnTarget = pane.targetPointAt(new Point2D(e.getX(), e.getY()))
							                        .orElse(pane.targetPointAtViewportCentre());
					// increment of scale makes more sense exponentially instead of linearly
					pane.animate(DURATION)
							.interpolateWith(Interpolator.EASE_BOTH)
							.zoomBy(pane.getCurrentScale(), pivotOnTarget);
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
			zoomFactor.textProperty().bind(pane.scrollZoomFactorProperty().asString(FORMAT));

			minScaleSlider.setValue(pane.getMinScale());
			pane.minScaleProperty().bind(minScaleSlider.valueProperty());
			maxScaleSlider.setValue(pane.getMaxScale());
			pane.maxScaleProperty().bind(maxScaleSlider.valueProperty());
//			currentScaleSlider.setValue(gesturePane.getCurrentScale());
//			gesturePane.currentScaleProperty().bind(currentScaleSlider.valueProperty());
			zoomFactorSlider.setValue(pane.getScrollZoomFactor());
			pane.scrollZoomFactorProperty().bind(zoomFactorSlider.valueProperty());

			reset.setOnAction(e -> pane.reset());

			x.setTextFormatter(createDecimalFormatter());
			y.setTextFormatter(createDecimalFormatter());
			scale.setTextFormatter(createDecimalFormatter());

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
				double _zoom = parseDouble(scale.getText()).orElse(1);

				x.setText(String.valueOf(d.getX()));
				y.setText(String.valueOf(d.getY()));
				scale.setText(String.valueOf(_zoom));
				if (toggle == translate) {
					if (!relative.isSelected()) ops.centreOn(d);
					else ops.translateBy(new Dimension2D(d.getX(), d.getY()));
				} else if (toggle == zoom) {
					if (!relative.isSelected()) ops.zoomTo(_zoom, d);
					else ops.zoomBy(_zoom, d);
				}
			});

			pane.addEventHandler(AffineEvent.CHANGED, e -> {
				Affine a = e.getAffine();
				String str = String.format("\n\t%.5f, %.5f, %.5f, %.5f" +
						                           "\n\t%.5f, %.5f, %.5f, %.5f" +
						                           "\n\t%.5f, %.5f, %.5f, %.5f",
						a.getMxx(), a.getMxy(), a.getMxz(), a.getTx(),
						a.getMyx(), a.getMyy(), a.getMyz(), a.getTy(),
						a.getMzx(), a.getMzy(), a.getMzz(), a.getTz());
				this.affine.setText(str);
			});
		}


	}


	private static TextFormatter<String> createDecimalFormatter() {
		DecimalFormat format = new DecimalFormat("#.0");
		format.setNegativePrefix("-");
		return new TextFormatter<>(c -> {
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
