package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.ScrollBarPolicy;

import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.NumberBinding;
import javafx.beans.binding.When;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SkinBase;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;

import static net.kurobako.gesturefx.AffineEvent.CHANGED;
import static net.kurobako.gesturefx.AffineEvent.CHANGE_FINISHED;
import static net.kurobako.gesturefx.AffineEvent.CHANGE_STARTED;

/**
 * Private API
 * <br>
 * Skin for the {@link GesturePane}, responsible for the following:
 * <ul>
 * <li>Content layout (vbar, hbar, target)</li>
 * <li>Event firing for affine change</li>
 * <li>Event handling for interactions</li>
 * <li>Clipping</li>
 * <li>Conditional optimisations</li>
 * </ul>
 */
final class GesturePaneSkin extends SkinBase<GesturePane> {

	// An arbitrary scroll factor that seems to work well(hopefully)
	static final double DEFAULT_SCROLL_FACTOR = 0.095;

	private final ScrollBar hbar = new ScrollBar();
	private final ScrollBar vbar = new ScrollBar();
	private final StackPane corner = new StackPane();

	private boolean hbarDown = false;
	private boolean vbarDown = false;

	private final GesturePane pane;
	private final Affine affine;

	GesturePaneSkin(GesturePane control) {
		super(control);
		pane = getSkinnable();
		affine = pane.affine;
		pane.setFocusTraversable(true);
		cache(false);

		// clip stuff that goes out of bound
		Rectangle rectangle = new Rectangle();
		rectangle.heightProperty().bind(pane.heightProperty());
		rectangle.widthProperty().bind(pane.widthProperty());
		pane.clipProperty().bind(new When(pane.clipEnabled)
				.then(rectangle)
				.otherwise(new SimpleObjectProperty<>(null)));

		// allow min size to be less than pref size
		hbar.setMinHeight(0);
		vbar.setMinWidth(0);

		// bind visibility to managed prop
		BooleanBinding isNotUnbounded = pane.fitMode.isNotEqualTo(FitMode.UNBOUNDED);

		hbar.managedProperty().bind(isNotUnbounded.and(
				pane.hbarPolicy.isEqualTo(ScrollBarPolicy.AS_NEEDED)
						.and((hbar.visibleAmountProperty().greaterThan(vbar.widthProperty())))
						.or(pane.hbarPolicy.isEqualTo(ScrollBarPolicy.ALWAYS))));

		vbar.managedProperty().bind(isNotUnbounded.and(
				pane.vbarPolicy.isEqualTo(ScrollBarPolicy.AS_NEEDED)
						.and(vbar.visibleAmountProperty().greaterThan(hbar.heightProperty()))
						.or(pane.vbarPolicy.isEqualTo(ScrollBarPolicy.ALWAYS))));

		corner.managedProperty().bind(hbar.managedProperty().and(vbar.managedProperty()));

		// setup scrollbars
		getChildren().addAll(vbar, hbar, corner);
		hbar.setOrientation(Orientation.HORIZONTAL);
		vbar.setOrientation(Orientation.VERTICAL);
		hbar.visibleProperty().bind(hbar.managedProperty());
		vbar.visibleProperty().bind(vbar.managedProperty());
		corner.visibleProperty().bind(corner.managedProperty());
		corner.getStyleClass().setAll("corner");

		DoubleBinding scaledWidth = pane.targetWidth.multiply(pane.scaleX);
		DoubleBinding scaledHeight = pane.targetHeight.multiply(pane.scaleY);

		DoubleProperty hbarHeight = new SimpleDoubleProperty(0);
		DoubleProperty vbarWidth = new SimpleDoubleProperty(0);

		Runnable updateHBarHeight = () -> hbarHeight.set(hbar.isManaged() ? hbar.getHeight() : 0);
		Runnable updateVBarWidth = () -> vbarWidth.set(vbar.isManaged() ? vbar.getWidth() : 0);

		hbar.managedProperty().addListener(o -> updateHBarHeight.run());
		vbar.managedProperty().addListener(o -> updateVBarWidth.run());
		updateHBarHeight.run();
		updateVBarWidth.run();

		// offset from top left corner so translation is negative
		// XXX changing min/max properties causes a full layout pass (requestLayout) to propagate
		// from the scrollbars
		// this behavior is potentially detrimental during animations and continuous zooming
		vbar.setMax(0);
		hbar.setMax(0);

		// bind scrollbars to translation
		Runnable setHbarX = () -> hbar.setValue(hbar.getMin() - affine.getTx());
		Runnable setVbarY = () -> vbar.setValue(vbar.getMin() - affine.getTy());
		vbar.minProperty().bind(scaledHeight.subtract(pane.heightProperty()).add(hbarHeight).negate());
		hbar.minProperty().bind(scaledWidth.subtract(pane.widthProperty()).add(vbarWidth).negate());
		hbar.minProperty().addListener(o -> setHbarX.run());
		vbar.minProperty().addListener(o -> setVbarY.run());
		affine.txProperty().addListener(o -> setHbarX.run());
		affine.tyProperty().addListener(o -> setVbarY.run());
		hbar.valueProperty().addListener(o -> {
			if (!hbarDown) return;
			affine.setTx(hbar.getMin() - hbar.getValue());
			markChanged();
		});
		vbar.valueProperty().addListener(o -> {
			if (!vbarDown) return;
			affine.setTy(vbar.getMin() - vbar.getValue());
			markChanged();
		});

		// (barMax - barMin) * (bound/targetBound)
		hbar.visibleAmountProperty().bind(hbar.minProperty().negate()
				.multiply(pane.widthProperty().divide(scaledWidth)));
		vbar.visibleAmountProperty().bind(vbar.minProperty().negate()
				.multiply(pane.heightProperty().divide(scaledHeight)));

		// fire start and finish events for scrollbars
		hbar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {hbarDown = true; markStart();});
		vbar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {vbarDown = true; markStart();});
		hbar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {hbarDown = false; markEnd();});
		vbar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {vbarDown = false; markEnd();});

		// bind viewport to target dimension
		Arrays.asList(
				pane.widthProperty(),
				pane.heightProperty(),
				hbar.layoutBoundsProperty(),
				vbar.layoutBoundsProperty(),
				vbar.managedProperty(),
				hbar.managedProperty(),
				pane.target,
				pane.content).forEach(p -> p.addListener(o -> {
			double width = pane.getWidth() - (vbar.isManaged() ? vbar.getWidth() : 0);
			double height = pane.getHeight() - (hbar.isManaged() ? hbar.getHeight() : 0);
			pane.viewport.set(new BoundingBox(0, 0, width, height));
		}));

		// bind affine/bound changes to viewport
		Arrays.asList(pane.viewport,
				pane.affine.txProperty(),
				pane.affine.tyProperty(),
				pane.scaleX, pane.scaleY).forEach(p -> p.addListener(o -> {
			double scaleX = pane.scaleX.get();
			double scaleY = pane.scaleY.get();


			pane.targetRect.set(new BoundingBox(-affine.getTx() / scaleX,
					-affine.getTy() / scaleY,
					pane.getViewportWidth() / scaleX,
					pane.getViewportHeight() / scaleY));
		}));

		pane.fitWidth.addListener(o -> pane.requestLayout());
		pane.fitHeight.addListener(o -> pane.requestLayout());
		pane.scrollMode.addListener(o -> pane.clampAtBound(false));
		pane.fitMode.addListener(o -> pane.clampAtBound(false));
//		affine.setOnTransformChanged(e -> pane.fireAffineEvent(CHANGED));
		setHbarX.run();
		setVbarY.run();
		setupGestures();
	}

	private <T extends Event> EventHandler<T> consumeThenFireIfEnabled(EventHandler<T> handler) {
		return e -> {
			e.consume();
			if (pane.gestureEnabled.get()) handler.handle(e);
		};
	}

	private Point2D lastPosition;

	private void markStart() {
		// XXX windows might give us mouse + scroll events if using touchscreen
		if (pane.isChanging()) return;
		pane.requestFocus();
		pane.changing.set(true);
		pane.fireAffineEvent(CHANGE_STARTED);
	}
	private void markChanged() { pane.fireAffineEvent(CHANGED); }
	private void markEnd() {
		// XXX windows might give us mouse + scroll events if using touchscreen
		if (!pane.isChanging()) return;
		pane.fireAffineEvent(CHANGE_FINISHED);
		pane.changing.set(false);
	}

	private void setupGestures() {
		// translate
		pane.addEventHandler(MouseEvent.MOUSE_PRESSED,
				consumeThenFireIfEnabled(e -> {
					lastPosition = new Point2D(e.getX(), e.getY());
					cache(true);
					markStart();
				}));
		pane.addEventHandler(MouseEvent.MOUSE_RELEASED,
				consumeThenFireIfEnabled(e -> {
					cache(false);
					markEnd();
				}));
		pane.addEventHandler(MouseEvent.MOUSE_DRAGGED,
				consumeThenFireIfEnabled(e -> {
					if (lastPosition != null) {
						pane.translate(
								e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
						lastPosition = new Point2D(e.getX(), e.getY());
					}
				}));

		// zoom via touch
		pane.addEventHandler(ZoomEvent.ZOOM_STARTED,
				consumeThenFireIfEnabled(e -> markStart()));
		pane.addEventHandler(ZoomEvent.ZOOM_FINISHED,
				consumeThenFireIfEnabled(e -> markEnd()));
		pane.addEventHandler(ZoomEvent.ZOOM,
				consumeThenFireIfEnabled(e -> pane.scale(e.getZoomFactor(),e.getZoomFactor(), fromGesture(e))));

		// translate+zoom via mouse/touchpad
		pane.addEventHandler(ScrollEvent.SCROLL_STARTED, consumeThenFireIfEnabled(e -> {
			cache(true);
			markStart();
		}));
		pane.addEventHandler(ScrollEvent.SCROLL_FINISHED, consumeThenFireIfEnabled(e -> {
			cache(false);
			markEnd();
		}));
		pane.addEventHandler(KeyEvent.KEY_RELEASED, e -> {
			if ((!e.isShortcutDown() && pane.isChanging())) markEnd();
		});
		pane.addEventHandler(ScrollEvent.SCROLL, consumeThenFireIfEnabled(e -> {
			// mouse scroll events only
			if (e.getTouchCount() > 0) return;
			// TODO might be driver and platform specific
			// TODO test on Linux
			// TODO test on different Windows versions
			// TODO test on machines with different touchpad vendor

			// pinch to zoom on trackpad
			if (e.isShortcutDown()) {
				if (!pane.isChanging()) markStart();
				double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.scrollZoomFactor.get();
				if (e.getDeltaY() < 0) zoomFactor *= -1;
				pane.scale(1 + zoomFactor, fromGesture(e));
				return;
			}
			switch (pane.scrollMode.get()) {
				case ZOOM:
					double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.scrollZoomFactor.get();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					pane.scale(1 + zoomFactor, fromGesture(e));
					break;
				case PAN:
					boolean invert = pane.invertScrollTranslate.get();
					pane.translate(
							invert ? e.getDeltaY() : e.getDeltaX(),
							invert ? e.getDeltaX() : e.getDeltaY());
					break;
			}
		}));
	}

	private void cache(boolean enable) {
		pane.setCacheHint(enable ? CacheHint.SPEED : CacheHint.QUALITY);
	}

	private static Point2D fromGesture(GestureEvent event) {
		return new Point2D(event.getX(), event.getY());
	}

	@Override
	protected double computeMinWidth(double height, double topInset, double rightInset,
	                                 double bottomInset, double leftInset) {
		return 0;
	}

	@Override
	protected double computeMinHeight(double width, double topInset, double rightInset,
	                                  double bottomInset, double leftInset) {
		return 0;
	}

	@Override
	protected double computePrefWidth(double height, double topInset, double rightInset,
	                                  double bottomInset, double leftInset) {
		return leftInset + (pane.fitWidth.get() ? pane.getTargetWidth() : 0) + rightInset;
	}

	@Override
	protected double computePrefHeight(double width, double topInset, double rightInset,
	                                   double bottomInset, double leftInset) {
		return topInset + (pane.fitHeight.get() ? pane.getTargetHeight() : 0) + bottomInset;
	}


	@Override
	protected void layoutChildren(double contentX, double contentY,
	                              double contentWidth, double contentHeight) {
		// XXX do not call super.layoutChildren as that causes infinite layout passes in OpenJFX11
		if (hbar.isManaged()) {
			layoutInArea(hbar, 0, 0, contentWidth -
							(vbar.isManaged() ? (vbar.prefWidth(ScrollBar.USE_COMPUTED_SIZE)) : 0),
					contentHeight,
					0, HPos.CENTER, VPos.BOTTOM);
		}
		if (vbar.isManaged()) {
			layoutInArea(vbar, 0, 0,
					contentWidth, contentHeight -
							(hbar.isManaged() ? hbar.prefHeight(ScrollBar.USE_COMPUTED_SIZE) : 0),
					0, HPos.RIGHT, VPos.CENTER);
		}

		// draw corner on bottom right where two scrollbar meets
		if (hbar.isManaged() && vbar.isManaged()) {
			corner.resizeRelocate(hbar.getWidth(), vbar.getHeight(),
					hbar.getHeight(), vbar.getWidth());
		}

		Node content = pane.getContent();
		if (content != null) {
			layoutInArea(content,
					contentX, contentY,
					contentWidth, contentHeight,
					-1,
					HPos.LEFT, VPos.TOP);
		}
		pane.clampAtBound(false);
	}
}
