package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.Transformable;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.List;

import javafx.scene.image.ImageView;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;

final class GesturePaneTests {

	private GesturePaneTests() {}

	private static final String LENA = GesturePaneTest.class
			.getResource("/lena_512.jpg")
			.toExternalForm();
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
						view = new ImageView(LENA);
						return new GesturePane(view);
					}
				},
				new ImageTest("ImageView(Content,Injected)") {
					@Override
					GesturePane createPane() {
						GesturePane pane = new GesturePane(view);
						view = new ImageView(LENA);
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
		} else {
			System.out.println("Testing headful with real windows, " +
					"please do not touch keyboard or mouse until tests are " +
					"complete.");
		}
	}

}
