package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.ScrollMode;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) throws Exception {
		StackPane root = new StackPane();
		Pane node = new Pane(new ImageView(getClass().getResource("/lena.png").toExternalForm()));
		root.getChildren().add(new GesturePane(node , ScrollMode.PAN));
		primaryStage.setScene(new Scene(root, 600,600));
		primaryStage.show();
	}
}
