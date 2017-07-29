package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.SamplerController.Sample;

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.web.WebView;

public class WebViewSample implements Sample {
	@Override
	public Node mkRoot() {
		WebView webview = new WebView();
		webview.setPrefWidth(600);
		webview.setPrefHeight(600);
		webview.setPickOnBounds(true);
		webview.setMouseTransparent(true);
		webview.getEngine().load("http://purecss3.net/wind/Wind_Electricity.html");
		return new GesturePane(webview, ScrollMode.ZOOM);
	}
}
