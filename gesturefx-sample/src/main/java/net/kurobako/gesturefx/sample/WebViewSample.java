package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.AffineEvent;
import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.GesturePane.Transformable;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import static java.lang.String.*;

public class WebViewSample implements Sample {
	public static final String INTERESTING_CSS = "https://upload.wikimedia" +
			".org/wikipedia/commons/6/6c/Perlshaper_orthographic_example1.svg";
	@Override
	public Node mkRoot() {

		GesturePane pane = new GesturePane();

		WebView webview = new WebView();
		WebEngine engine = webview.getEngine();
		TextField bar = new TextField(INTERESTING_CSS);
		bar.setOnAction(e -> engine.load(bar.getText()));
		engine.load(bar.getText());
		engine.documentProperty().addListener((o, p, n) -> {
			if (n == null) return;

			// if body is undefined we're probably dealing with a SVG file
			String root = (boolean) engine.executeScript("document.body != undefined") ?
					"document.body" :
					"document.rootElement";

			engine.executeScript(format("%s.transformOrigin = '0 0 0';", root));
			engine.executeScript(format("%s.overflow = 'hidden';", root));
			double w =
					Double.parseDouble(engine.executeScript(format("%s.scrollWidth", root)).toString());
			double h =
					Double.parseDouble(engine.executeScript(format("%s.scrollHeight", root)).toString());

			pane.setTarget(new Transformable() {
				@Override public double width() { return w; }
				@Override public double height() { return h; }
			});
			pane.zoomTo(1, Point2D.ZERO);


			pane.addEventHandler(AffineEvent.CHANGED, e -> {
				String script = format(
						format("%s && (%s.style.transform = 'matrix(%%s,0,0,%%s,%%s,%%s)');",
								root, root),
						e.namedCurrent().scaleX(),
						e.namedCurrent().scaleY(),
						e.namedCurrent().translateX(),
						e.namedCurrent().translateY());
				try {
					engine.executeScript(script);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			});

		});

		Label description = new Label("GesturePane supports Transformable implementation. This " +
				"sample shows a WebView behind an empty GesturePane " +
				"listening for AffineEvents. The Affine matrix is " +
				"translated to CSS matrix and applied the the body " +
				"element. \nBe aware that all mouse events will be " +
				"consumed by the pane so you cannot click any " +
				"links.");
		description.setWrapText(true);
		description.setPadding(new Insets(16));
		StackPane glass = new StackPane(webview, pane);
		glass.setPrefSize(0, 0);
		VBox.setVgrow(glass, Priority.ALWAYS);
		return new VBox(description, bar, glass);

	}
}
