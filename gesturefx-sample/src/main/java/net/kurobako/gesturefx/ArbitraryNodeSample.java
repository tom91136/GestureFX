package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.SamplerController.Sample;

import java.io.IOException;

import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.web.WebView;

public class ArbitraryNodeSample implements Sample{
	@Override
	public Node mkRoot() {
		try {
			Parent node  = FXMLLoader.load(getClass().getResource("/ComplexScene.fxml"));
			node.setPickOnBounds(true);
			node.setMouseTransparent(true);
			WebView webview = (WebView) node.lookup("webview");
			if(webview != null){
				webview.getEngine().load("http://purecss3.net/doraemon/doraemon_css3.html");
			}
			return new GesturePane(node);

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
