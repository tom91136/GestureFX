package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.SamplerController.Sample;

import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class LenaSample implements Sample {

	@Override
	public Node mkRoot() {
		Node node = new ImageView(getClass().getResource("/lena.png").toExternalForm());
		GesturePane gesturePane = new GesturePane(node, ScrollMode.ZOOM);
		gesturePane.setMaxSize(500, 300);

		GridPane pane = new GridPane();
		pane.add(new Label("FitMode"), 0, 1);
		ComboBox<FitMode> fitMode = new ComboBox<>(FXCollections.observableArrayList(FitMode.values()));
		gesturePane.fitModeProperty().bind(fitMode.valueProperty());
		fitMode.getSelectionModel().selectFirst();
		pane.add(fitMode, 0, 2);
		return new HBox(gesturePane, pane);
	}

}
