package net.kurobako.gesturefx;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;

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


	@FXML private SplitPane root;
	@FXML private ListView<SampleEntry> samples;
	@FXML private TitledPane samplePane;

	@Override
	public void initialize(URL location, ResourceBundle resources) {

		samples.setItems(FXCollections.observableArrayList(
				new SampleEntry("Lena(ImageView)", LenaSample::new),
				new SampleEntry("Arbitrary Node", ArbitraryNodeSample::new),
				new SampleEntry("WebView", WebViewSample::new)
		));

		samples.setCellFactory(param -> new ListCell<SampleEntry>() {
			@Override
			protected void updateItem(SampleEntry item, boolean empty) {
				super.updateItem(item, empty);
				if (item != null) setText(item.name);
				else setText(null);
			}
		});


		MultipleSelectionModel<SampleEntry> selectionModel = samples.getSelectionModel();
		selectionModel.setSelectionMode(SelectionMode.SINGLE);
		selectionModel.selectedItemProperty().addListener((o, p, n) -> {
			if (n == null) return;
			samplePane.setText(n.name);
			samplePane.setContent(n.sampleFactory.get().mkRoot());
		});
		selectionModel.selectFirst();


	}

}
