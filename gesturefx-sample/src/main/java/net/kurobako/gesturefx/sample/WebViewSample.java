package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import javafx.scene.Node;
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
		return new GesturePane(webview);
	}
}
