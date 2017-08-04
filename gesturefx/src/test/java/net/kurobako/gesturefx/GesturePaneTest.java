package net.kurobako.gesturefx;


import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollMode;
import net.kurobako.gesturefx.GesturePane.Transformable;

import org.assertj.core.api.Condition;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Offset;
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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.beans.property.Property;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VerticalDirection;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.image.ImageView;
import javafx.scene.input.ZoomEvent;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Transform;
import javafx.util.Duration;

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
			Affine transform;
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
		FxToolkit.setupSceneRoot(() -> {
			if (!Platform.isFxApplicationThread()) throw new AssertionError("Invalid test state");
			Thread.currentThread().setUncaughtExceptionHandler(HANDLER);
			pane = target.createPane();
			pane.setId(ID);
			return pane;
		});
		FxToolkit.showStage();
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
		// by default enabled
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

//		pane.zoomTarget(2, Duration.millis(100), );
	}

	@Test
	public void testAnimatedScaleClamp() throws Exception {

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
		class PropertyAssertion<R> {

			private final Supplier<R> getter;
			private final Consumer<? super R> setter;
			private final Supplier<Property<? super R>> property;
			private final R expected;

			private PropertyAssertion(Supplier<R> getter,
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
		Arrays.asList(
				new PropertyAssertion<>(pane::getContent, pane::setContent,
						                       pane::contentProperty, new Rectangle()),
				new PropertyAssertion<>(pane::isVBarEnabled, pane::setVBarEnabled,
						                       pane::vBarEnabledProperty, false),
				new PropertyAssertion<>(pane::isHBarEnabled, pane::setHBarEnabled,
						                       pane::hBarEnabledProperty, false),
				new PropertyAssertion<>(pane::isGestureEnabled, pane::setGestureEnabled,
						                       pane::gestureEnabledProperty, false),
				new PropertyAssertion<>(pane::isClipEnabled, pane::setClipEnabled,
						                       pane::clipEnabledProperty, false),
				new PropertyAssertion<>(pane::isFitWidth, pane::setFitWidth,
						                       pane::fitWidthProperty, false),
				new PropertyAssertion<>(pane::isFitHeight, pane::setFitHeight,
						                       pane::fitHeightProperty, false),
				new PropertyAssertion<>(pane::getFitMode, pane::setFitMode,
						                       pane::fitModeProperty, FitMode.CENTER),
				new PropertyAssertion<>(pane::getScrollMode, pane::setScrollMode,
						                       pane::scrollModeProperty, ScrollMode.ZOOM),
				new PropertyAssertion<>(pane::getMinScale, pane::setMinScale,
						                       pane::minScaleProperty, 42d),
				new PropertyAssertion<>(pane::getMaxScale, pane::setMaxScale,
						                       pane::maxScaleProperty, 42d),
				new PropertyAssertion<>(pane::getScrollZoomFactor, pane::setScrollZoomFactor,
						                       pane::scrollZoomFactorProperty, 42d))
				.forEach(PropertyAssertion::assertProperty);

		// for read-only properties
		softly.assertThat(pane.getCurrentScale()).isEqualTo(pane.currentScaleProperty().get());
		softly.assertThat(pane.getCurrentX()).isEqualTo(pane.currentXProperty().get());
		softly.assertThat(pane.getCurrentY()).isEqualTo(pane.currentYProperty().get());
		softly.assertThat(pane.getTargetViewport()).isEqualTo(pane.targetViewportProperty().get());


		softly.assertAll();
	}
}