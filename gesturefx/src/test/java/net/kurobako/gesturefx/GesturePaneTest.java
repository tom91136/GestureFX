package net.kurobako.gesturefx;


import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.GesturePane.Transformable;

import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Offset;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.matcher.base.NodeMatchers;
import org.testfx.service.query.impl.BoundsPointQuery;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.fxml.FXMLLoader;
import javafx.geometry.BoundingBox;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ZoomEvent;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import static javafx.geometry.Orientation.HORIZONTAL;
import static javafx.geometry.Orientation.VERTICAL;
import static net.kurobako.gesturefx.GesturePaneSkin.DEFAULT_SCROLL_FACTOR;
import static org.assertj.core.api.Assertions.assertThat;


// test will spawn actual window and take control of the mouse and keyboard!
@RunWith(Parameterized.class)
public class GesturePaneTest {


	private static abstract class TestTarget {
		final String name;
		TestTarget(String name) { this.name = name; }
		abstract GesturePane createPane();
		abstract Transform captureTransform();
		@Override
		public String toString() { return name; }
	}

	private static final String LENA = GesturePaneTest.class
			                                   .getResource("/lena_512.jpg")
			                                   .toExternalForm();

	private static final UncaughtExceptionHandler HANDLER = (t, e) -> {
		throw new AssertionError("Thread " + t.getName() + " crashed while testing", e);
	};


	@Parameters(name = "{index}:{0}")
	public static Collection<TestTarget> data() {
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

	private static final String ID = "target";
	private GesturePane pane;

	@Parameter public TestTarget target;

	@Before
	public void setup() throws Exception {
		if (Platform.isFxApplicationThread()) throw new AssertionError("Invalid test state");
		Thread.setDefaultUncaughtExceptionHandler(HANDLER);
		FxToolkit.registerPrimaryStage();
//		FxToolkit.registerStage(() -> new Stage(StageStyle.TRANSPARENT));
		FxToolkit.setupSceneRoot(() -> {
			if (!Platform.isFxApplicationThread()) throw new AssertionError("Invalid test state");
			Thread.currentThread().setUncaughtExceptionHandler(HANDLER);
			pane = target.createPane();
			pane.setId(ID);
			return pane;
		});
		FxToolkit.setupStage(Window::sizeToScene);
		FxToolkit.showStage();
	}

	@After
	public void tearDown() throws Exception {
		FxToolkit.cleanupStages();
	}
	private static Condition<Node> createBarCondition(Orientation orientation, boolean visible) {
		return new Condition<>(n -> {
			if (n instanceof ScrollBar) {
				ScrollBar bar = (ScrollBar) n;
				return ((ScrollBar) n).getOrientation() == orientation &&
						       visible == bar.isManaged() &&
						       visible == bar.isVisible();
			} else return false;
		}, "Is ScrollBar " + orientation + " and " + (visible ? "visible" : "hidden"));
	}

	@Test
	public void testInitialisation() throws Exception {
		FxAssert.verifyThat("#" + ID, NodeMatchers.isNotNull());
	}

	@Test
	public void testInitialTransformSet() throws Exception {
		assertThat(target.captureTransform()).isNotNull();
	}

	@Test
	public void testInitialTransformIsIdentity() throws Exception {
		assertThat(target.captureTransform().isIdentity()).isTrue();
	}

	@Test
	public void testScrollBarEnabled() throws Exception {
		// enabled by default
		assertThat(pane.lookupAll("*"))
				.haveExactly(1, createBarCondition(HORIZONTAL, true))
				.haveExactly(1, createBarCondition(VERTICAL, true));
	}

	@Test
	public void testScrollBarDisabled() throws Exception {
		pane.setScrollBarEnabled(false);
		assertThat(pane.lookupAll("*"))
				.haveExactly(1, createBarCondition(HORIZONTAL, false))
				.haveExactly(1, createBarCondition(VERTICAL, false));
	}

	@Test
	public void testHBar() throws Exception {
		pane.setHBarEnabled(true);
		pane.setVBarEnabled(false);
		assertThat(pane.lookupAll("*"))
				.haveExactly(1, createBarCondition(HORIZONTAL, true))
				.haveExactly(1, createBarCondition(VERTICAL, false));
	}

	@Test
	public void testVBar() throws Exception {
		pane.setHBarEnabled(false);
		pane.setVBarEnabled(true);
		assertThat(pane.lookupAll("*"))
				.haveExactly(1, createBarCondition(HORIZONTAL, false))
				.haveExactly(1, createBarCondition(VERTICAL, true));
	}

	@Test
	public void testSetTarget() throws Exception {
		pane.setTarget(new Transformable() {
			@Override
			public double width() { return 128; }
			@Override
			public double height() { return 128; }
			@Override
			public void setTransform(Affine affine) { }
		});
		pane.setTarget(new Transformable() {
			@Override
			public double width() { return 1014; }
			@Override
			public double height() { return 1024; }
			@Override
			public void setTransform(Affine affine) { }
		});
	}

	@Test
	public void testSetContent() throws Exception {
		pane.setContent(new Rectangle(128, 128));
		pane.setContent(new Rectangle(1024, 1024));
	}

	@Test
	public void testContentBoundChanged() throws Exception {
		Rectangle rect = new Rectangle(128, 128, Color.RED);
		pane.setContent(rect);
		Thread.sleep(50);
		rect.setWidth(1000);
		rect.setHeight(1000);
		Thread.sleep(50);
		rect.setHeight(0);
		rect.setHeight(0);
	}

	@Test
	public void testContainerBoundChanged() throws Exception {
		pane.setScrollBarEnabled(false);
		pane.getScene().getWindow().setWidth(256);
		pane.getScene().getWindow().setHeight(256);
		assertThat(pane.getViewportBound()).isEqualTo(new BoundingBox(0, 0, 256, 256));
		assertThat(pane.viewportCentre()).isEqualTo(new Point2D(128, 128));
	}

	@Test
	public void testDragAndDrop() throws Exception {
		pane.zoomTarget(2, false);
		Transform expected = target.captureTransform();
		FxRobot robot = new FxRobot();
		robot.moveTo(pane)
				.scroll(2, VerticalDirection.UP)
				.scroll(2, VerticalDirection.DOWN)
				.drag(MouseButton.PRIMARY).dropBy(100, 100);
		Transform actual = target.captureTransform();
		assertThat(actual).isEqualToComparingOnlyGivenFields(expected,
				"xx", "xy", "xz",
				"yx", "yy", "yz",
				"zx", "zy", "zz",
				/* "xt", "yt", */ "zt");
		assertThat(actual.getTx()).isEqualTo(expected.getTx() + 100);
		assertThat(actual.getTy()).isEqualTo(expected.getTy() + 100);
	}

	@Test
	public void testGestureDisabling() throws Exception {
		pane.setGestureEnabled(false);
		pane.zoomTarget(2, false);
		Transform expected = target.captureTransform();
		FxRobot robot = new FxRobot();
		robot.moveTo(pane)
				.scroll(2, VerticalDirection.UP)
				.scroll(2, VerticalDirection.DOWN)
				.drag(MouseButton.PRIMARY).dropBy(100, 100);
		assertThat(target.captureTransform()).isEqualToComparingOnlyGivenFields(expected,
				"xx", "xy", "xz",
				"yx", "yy", "yz",
				"zx", "zy", "zz",
				"xt", "yt", "zt");
	}

	@Test
	public void testViewportCentre() throws Exception {
		pane.setScrollBarEnabled(false);
		//  we got a 512*512 image
		assertThat(pane.viewportCentre()).isEqualTo(new Point2D(256, 256));
	}

	@Test
	public void testTargetPointAtViewportPoint() throws Exception {
		pane.setScrollBarEnabled(false);
		//  we got an 512*512 image and the window is exactly 512*512
		final int d = 512;
		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(pane.targetPointAt(Point2D.ZERO)).contains(Point2D.ZERO);
		softly.assertThat(pane.targetPointAt(new Point2D(d, d))).contains(new Point2D(d, d));
		softly.assertThat(pane.targetPointAt(new Point2D(-1, -1))).isEmpty();
		softly.assertThat(pane.targetPointAt(new Point2D(1000, 1000))).isEmpty();
		softly.assertAll();
	}

	@Test
	public void testTargetPointAtViewportCentre() throws Exception {
		pane.setScrollBarEnabled(false);
		// a completely valid point in viewport
		Point2D expected = pane.targetPointAt(pane.viewportCentre())
				                   .orElseThrow(AssertionError::new);
		assertThat(pane.targetPointAtViewportCentre()).isEqualTo(expected);
	}

	@Test
	public void testScale() throws Exception {
		pane.zoomTarget(2, false);
		assertThat(pane.getCurrentScale()).isEqualTo(2d);
	}

	@Test
	public void testScaleRelative() throws Exception {
		pane.zoomTarget(2, true);
		assertThat(pane.getCurrentScale()).isEqualTo(3d);
	}

	@Test
	public void testScaleByTouch() throws Exception {
		double factor = 4.2;
		pane.fireEvent(new ZoomEvent(ZoomEvent.ZOOM, 0, 0, 0, 0, false, false, false, false,
				                            false, false, factor, factor, null));
		assertThat(pane.getCurrentScale()).isEqualTo(factor);
	}

	@Test
	public void testScaleByScroll() throws Exception {
		pane.scrollModeProperty().set(ScrollMode.ZOOM);
		FxRobot robot = new FxRobot();
		assertThat(pane.getCurrentScale()).isEqualTo(1d);
		Thread.sleep(50);
		robot.moveTo(pane);
		robot.scroll(5, VerticalDirection.UP);
		double expected = Math.pow(1 + DEFAULT_SCROLL_FACTOR, 5);
		assertThat(pane.getCurrentScale()).isCloseTo(expected, Offset.offset(0.0001));
		Transform t = target.captureTransform();
		assertThat(t.getMxx()).isCloseTo(t.getMyy(), Offset.offset(0.00000001));
		assertThat(t.getMxx()).isCloseTo(expected, Offset.offset(0.0001));

	}

	@Test
	public void testMinScaleRespected() throws Exception {
		pane.setMinScale(1);
		pane.zoomTarget(0.1, false);
		assertThat(pane.getCurrentScale()).isEqualTo(1d);
	}

	@Test
	public void testMinScaleRelativeRespected() throws Exception {
		pane.setMinScale(1);
		pane.zoomTarget(-1, true);
		assertThat(pane.getCurrentScale()).isEqualTo(1d);
	}

	@Test
	public void testMaxScaleRespected() throws Exception {
		pane.setMaxScale(2);
		pane.zoomTarget(10, false);
		assertThat(pane.getCurrentScale()).isEqualTo(2d);
	}

	@Test
	public void testMaxScaleRelativeRespected() throws Exception {
		pane.setMaxScale(2);
		pane.zoomTarget(2, true);
		assertThat(pane.getCurrentScale()).isEqualTo(2d);
	}

	@Test
	public void testAnimatedScale() throws Exception {
		throw new RuntimeException();
//		pane.zoomTarget(2, Duration.millis(100), );
	}

	@Test
	public void testAnimatedScaleClamps() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testTranslate() throws Exception {
		final double zoom = 2d;
		final double dx = 300d;
		final double dy = 200d;
		pane.setScrollBarEnabled(false);
		pane.zoomTarget(zoom, false);
		final Transform last = target.captureTransform();
		pane.centreOn(new Point2D(dx, dy), false);
		final Transform now = target.captureTransform();
		assertThat(now.getTx()).isEqualTo(-last.getTx() - dx * zoom);
		assertThat(now.getTy()).isEqualTo(-last.getTy() - dy * zoom);
	}

	@Test
	public void testTranslateRelative() throws Exception {
		pane.centreOn(new Point2D(10, 10), false);
		pane.centreOn(new Point2D(20, 20), true);
		final Transform now = target.captureTransform();
		assertThat(now.getTx()).isEqualTo(30d);
		assertThat(now.getTy()).isEqualTo(30d);
	}

	@Test
	public void testAnimatedTranslate() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testAnimatedTranslateClamps() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testHorizontalTranslateClamp() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testVerticalTranslateClamp() throws Exception {
		throw new RuntimeException();
	}


	// just for sanity, things can get confusing when many of the property types are the same
	@Test
	public void testProperties() throws Exception {
		SoftAssertions softly = new SoftAssertions();

		// for some general read-write properties
		class Prop<R> {

			private final Supplier<R> getter;
			private final Consumer<? super R> setter;
			private final Supplier<Property<? super R>> property;
			private final R expected;

			private Prop(Supplier<R> getter,
			             Consumer<? super R> setter,
			             Supplier<Property<? super R>> property,
			             R expected) {
				this.getter = getter;
				this.setter = setter;
				this.property = property;
				this.expected = expected;
			}


			private void assertProperty() {
				try {
					setter.accept(expected);
					softly.assertThat(getter.get()).isEqualTo(expected);
					softly.assertThat(property.get().getValue()).isEqualTo(expected);
				} catch (Exception e) {
					softly.fail("Unexpected exception while resetting property:" + expected, e);
				}
			}
		}
		GesturePane p = this.pane;
		Arrays.asList(
				new Prop<>(p::getTarget, this.pane::setTarget, this.pane::targetProperty,
						          new Transformable() {
							          @Override
							          public double width() { return 0; }
							          @Override
							          public double height() { return 0; }
							          @Override
							          public void setTransform(Affine affine) { }
						          }),
				new Prop<>(p::getContent, p::setContent, p::contentProperty,
						          new Rectangle(512, 512)),
				new Prop<>(p::isVBarEnabled, p::setVBarEnabled, p::vBarEnabledProperty, false),
				new Prop<>(p::isHBarEnabled, p::setHBarEnabled, p::hBarEnabledProperty, false),
				new Prop<>(p::isGestureEnabled, p::setGestureEnabled, p::gestureEnabledProperty,
						          false),
				new Prop<>(p::isClipEnabled, p::setClipEnabled, p::clipEnabledProperty, false),
				new Prop<>(p::isFitWidth, p::setFitWidth, p::fitWidthProperty, false),
				new Prop<>(p::isFitHeight, p::setFitHeight, p::fitHeightProperty, false),
				new Prop<>(p::getFitMode, p::setFitMode, p::fitModeProperty,
						          FitMode.CENTER),
				new Prop<>(p::getScrollMode, p::setScrollMode, p::scrollModeProperty,
						          ScrollMode.ZOOM),
				new Prop<>(p::getMinScale, p::setMinScale, p::minScaleProperty, 42d),
				new Prop<>(p::getMaxScale, p::setMaxScale, p::maxScaleProperty, 42d),
				new Prop<>(p::getScrollZoomFactor, p::setScrollZoomFactor,
						          p::scrollZoomFactorProperty, 42d))
				.forEach(Prop::assertProperty);

		// for read-only properties
		softly.assertThat(p.getCurrentScale()).isEqualTo(p.currentScaleProperty().get());
		softly.assertThat(p.getCurrentX()).isEqualTo(p.currentXProperty().get());
		softly.assertThat(p.getCurrentY()).isEqualTo(p.currentYProperty().get());
		softly.assertThat(p.getTargetViewport()).isEqualTo(p.targetViewportProperty().get());
		softly.assertThat(p.getViewportBound()).isEqualTo(p.viewportBoundProperty().get());
		softly.assertThat(p.getViewportWidth()).isEqualTo(p.getViewportBound().getWidth());
		softly.assertThat(p.getViewportHeight()).isEqualTo(p.getViewportBound().getHeight());

		softly.assertAll();
	}
}