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

public class WebViewSample implements Sample {
	public static final String INTERESTING_CSS = "http://purecss3.net/wind/Wind_Electricity.html";
	@Override
	public Node mkRoot() {

		GesturePane pane = new GesturePane();

		WebView webview = new WebView();
		WebEngine engine = webview.getEngine();
		pane.addEventHandler(AffineEvent.CHANGED, e -> {
			String script = String.format(
					"document.getElementsByTagName('body')[0].style.transform = " +
							"'matrix(%s,0,0,%s,%s,%s)';",
					e.getAffine().getMxx(),
					e.getAffine().getMyy(),
					e.getAffine().getTx(),
					e.getAffine().getTy());
			engine.executeScript(script);
		});
		TextField bar = new TextField(INTERESTING_CSS);
		bar.setOnAction(e -> engine.load(bar.getText()));
		engine.load(bar.getText());
		engine.documentProperty().addListener((o, p, n) -> {
			if (n == null) return;
			pane.zoomTo(1, Point2D.ZERO);
			pane.setTarget(new Transformable() {
				@Override
				public double width() {
					return Double.valueOf(engine.executeScript("document.body.scrollWidth")
							                      .toString());
				}
				@Override
				public double height() {
					return Double.valueOf(engine.executeScript("document.body.scrollHeight")
							                      .toString());
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
