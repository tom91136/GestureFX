package net.kurobako.gesturefx.sample;

import net.kurobako.gesturefx.GesturePane;
import net.kurobako.gesturefx.sample.SamplerController.Sample;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;

import javafx.animation.Interpolator;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class SwingSample implements Sample{
	@Override
	public Node mkRoot() {
		Label description = new Label("GesturePane works in a JFXPanel too!");
		description.setWrapText(true);
		description.setPadding(new Insets(16));
		Button button = new Button("Open Swing window");
		button.setOnAction(e -> {

			SwingUtilities.invokeLater(() -> {
				try {
					UIManager.setLookAndFeel(
							UIManager.getSystemLookAndFeelClassName());
				} catch (ClassNotFoundException |
						InstantiationException |
						IllegalAccessException |
						UnsupportedLookAndFeelException ignored) {
					// give up, doesn't matter
				}

				JLabel label = new JLabel("This window is a Swing JFrame with JFXPanel embedded " +
						"in a BorderLayout");
				label.setBorder(new EmptyBorder(10, 10, 10, 10));

				JPanel ops = new JPanel();
				JButton centre = new JButton("Centre image");
				JButton x2 = new JButton("Zoom x2");
				ops.add(centre);
				ops.add(x2);

				JFXPanel fxPanel = new JFXPanel();
				JFrame frame = new JFrame("GesturePane in Swing");
				frame.getContentPane().add(label, BorderLayout.NORTH);
				frame.getContentPane().add(fxPanel, BorderLayout.CENTER);
				frame.getContentPane().add(ops, BorderLayout.SOUTH);
				frame.setSize(500, 500);
				frame.setVisible(true);
				frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

				Platform.runLater(() -> {
					GesturePane pane = new GesturePane(new ImageView(LenaSample.LENA));
					Scene scene = new Scene(pane, frame.getWidth(), frame.getHeight());
					fxPanel.setScene(scene);
					centre.addActionListener(ae -> pane.animate(LenaSample.DURATION)
							.interpolateWith(Interpolator.EASE_BOTH)
							.centreOn(pane.viewportCentre()));
					x2.addActionListener(ae -> pane.animate(LenaSample.DURATION)
							.interpolateWith(Interpolator.EASE_BOTH)
							.zoomBy(2, pane.targetPointAtViewportCentre()));
				});

			});
		});
		VBox.setVgrow(button, Priority.ALWAYS);
		VBox box = new VBox(description, button);
		box.setAlignment(Pos.CENTER);
		return box;
	}
}
