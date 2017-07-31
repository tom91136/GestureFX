package net.kurobako.gesturefx;

import net.kurobako.gesturefx.SamplerController.Sample;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.BlendMode;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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

		Rectangle shade = new Rectangle(image.getWidth(), image.getHeight(), Color.grayRgb(0,
				0.5));
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


		gesturePane.addEventHandler(AffineEvent.CHANGED, e -> {
			viewRect.setTranslateX(-e.translateX() / e.scaleX());
			viewRect.setTranslateY(-e.translateY() / e.scaleY());
			viewRect.setWidth(gesturePane.getViewportWidth() / e.scaleY());
			viewRect.setHeight(gesturePane.getViewportHeight() / e.scaleY());
		});


		HBox box = new HBox(gesturePane, viewportSim);
		box.setAlignment(Pos.CENTER);
		return box;
	}
}
