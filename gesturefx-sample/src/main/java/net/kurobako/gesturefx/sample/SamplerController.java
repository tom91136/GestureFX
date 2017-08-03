package net.kurobako.gesturefx.sample;

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.VBox;

public class SamplerController implements Initializable {


	static class SampleEntry {
		final String name;
		final Supplier<? extends Sample> sampleFactory;

		SampleEntry(String name, Supplier<? extends Sample> sampleFactory) {
			this.name = name;
			this.sampleFactory = sampleFactory;
		}
	}

	interface Sample {
		Node mkRoot();
	}

	@FXML private VBox root;
	@FXML private Hyperlink link;
	@FXML private TabPane tabs;

	@Override
	public void initialize(URL location, ResourceBundle resources) {

		List<SampleEntry> samples = Arrays.asList(
				new SampleEntry("Lena(ImageView)", LenaSample::new),
				new SampleEntry("FXML(ImageView)", FXMLSample::new),
				new SampleEntry("ViewportRect(ImageView)", ViewportRectSample::new),
				new SampleEntry("Arbitrary Node", ArbitraryNodeSample::new),
				new SampleEntry("WebView", WebViewSample::new)
        );


		samples.forEach(s -> {
			tabs.getTabs().add(new Tab(s.name, s.sampleFactory.get().mkRoot()));
		});


	}

}
