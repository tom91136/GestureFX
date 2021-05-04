package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.Transformable;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;

final class GesturePaneTests {

	private GesturePaneTests() {}


	public static Image readImageFromIIO(String resource) {
		try {
			BufferedImage image = ImageIO.read(Objects.requireNonNull(GesturePaneTests.class.getResourceAsStream(resource)));
			WritableImage writableImage = SwingFXUtils.toFXImage(image, null);
			if (writableImage.isError()) {
				throw new RuntimeException(writableImage.getException());
			}
			return writableImage;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final String LENA = "/lena_512.jpg";
	static final UncaughtExceptionHandler HANDLER = (t, e) -> {
		throw new AssertionError("Thread " + t.getName() + " crashed while testing", e);
	};

	static final String ID = "target";

	static abstract class TestTarget {
		final String name;
		TestTarget(String name) { this.name = name; }
		abstract GesturePane createPane();
		abstract Transform captureTransform();
		@Override public String toString() { return name; }
	}

	static List<TestTarget> basicTestCases() {
		abstract class ImageTest extends TestTarget {
			ImageView view;
			ImageTest(String name) { super(name); }
			@Override
			Transform captureTransform() {
				try {
					return new Affine(view.getTransforms().get(0));
				} catch (Exception e) {
					throw new AssertionError("Unable to find/capture transformation", e);
				}
			}
		}
		abstract class EmptyTest extends TestTarget {
			private Affine transform;
			EmptyTest(String name) { super(name); }
			Transformable mkTransformable() {
				return new Transformable() {
					@Override
					public double width() { return 512; }
					@Override
					public double height() { return 512; }
					@Override
					public void setTransform(Affine affine) { transform = affine; }
				};
			}
			@Override
			Transform captureTransform() { return new Affine(transform); }
		}
		return Arrays.asList(
				new ImageTest("ImageView(Content)") {
					@Override
					GesturePane createPane() {
						view = new ImageView(readImageFromIIO(LENA));
						return new GesturePane(view);
					}
				},
				new ImageTest("ImageView(Content,Injected)") {
					@Override
					GesturePane createPane() {
						GesturePane pane = new GesturePane(view);
						view = new ImageView(readImageFromIIO(LENA));
						pane.setContent(view);
						return pane;
					}
				},
				new EmptyTest("Empty(Target)") {

					@Override
					GesturePane createPane() { return new GesturePane(mkTransformable()); }
				},
				new EmptyTest("Empty(Target),Injected") {
					@Override
					GesturePane createPane() {
						GesturePane pane = new GesturePane();
						pane.setTarget(mkTransformable());
						return pane;
					}
				});
	}


	// headful test will spawn actual window and take control of the mouse and keyboard!
	static void setupProperties() {
		if (!Boolean.getBoolean("headful")) {
			System.out.println("Testing using Monocle");
			System.setProperty("testfx.robot", "glass");
			System.setProperty("testfx.headless", "true");
			System.setProperty("prism.order", "sw");
			System.setProperty("prism.text", "t2k");
			System.setProperty("prism.verbose", "true");
		} else {
			System.out.println("Testing headful with real windows, " +
					"please do not touch keyboard or mouse until tests are " +
					"complete.");
		}
	}

}
