package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class ViewportRectSample implements Sample {
	@Override
	public Node mkRoot() {


		Image image = new Image(LenaSample.LENA);

		GesturePane gesturePane = new GesturePane(new ImageView(image));
		gesturePane.setMaxSize(image.getWidth(), image.getHeight());

		ImageView background = new ImageView(LenaSample.LENA);
		background.setFitWidth(image.getWidth());
		background.setFitHeight(image.getHeight());

		Rectangle shade = new Rectangle(image.getWidth(),
				                               image.getHeight(),
				                               Color.grayRgb(0, 0.5));
		Rectangle viewRect = new Rectangle();
		viewRect.setStroke(Color.WHITE);
		viewRect.setStrokeWidth(2);
		viewRect.setFill(Color.WHITE);
		// shade * rect
		Group group = new Group(shade, viewRect);
		group.setBlendMode(BlendMode.MULTIPLY);
		Pane viewportSim = new Pane(background, group);
		viewportSim.maxWidthProperty().bind(background.fitWidthProperty());
		viewportSim.maxHeightProperty().bind(background.fitHeightProperty());

		gesturePane.targetViewportProperty().addListener((o, p, n) -> {
			viewRect.setTranslateX(n.getMinX());
			viewRect.setTranslateY(n.getMinY());
			viewRect.setWidth(n.getWidth());
			viewRect.setHeight(n.getHeight());
		});

		HBox box = new HBox(gesturePane, viewportSim);
		box.setAlignment(Pos.CENTER);
		VBox.setVgrow(box, Priority.ALWAYS);
		Label description = new Label("Zoom and scroll on the left image(wrapped in a GesturePane)" +
				                              "; the right image will reflect the actual viewport " +
				                              "of the current transformation");
		description.setWrapText(true);
		description.setPadding(new Insets(16));
		return new VBox(description, box);
	}
}
