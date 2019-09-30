package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;

import java.util.Arrays;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.When;
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

	private final ScrollBar hBar = new ScrollBar();
	private final ScrollBar vBar = new ScrollBar();
	private final StackPane corner = new StackPane();

	private boolean hBarDown = false;
	private boolean vBarDown = false;

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

		// bind visibility to managed prop
		BooleanBinding isNotUnbounded = pane.fitMode.isNotEqualTo(FitMode.UNBOUNDED);
		hBar.managedProperty().bind(pane.hBarEnabled.and(isNotUnbounded));
		vBar.managedProperty().bind(pane.vBarEnabled.and(isNotUnbounded));
		corner.managedProperty().bind(hBar.managedProperty().and(vBar.managedProperty()));

		// setup scrollbars
		getChildren().addAll(vBar, hBar, corner);
		hBar.setOrientation(Orientation.HORIZONTAL);
		vBar.setOrientation(Orientation.VERTICAL);
		hBar.visibleProperty().bind(hBar.managedProperty());
		vBar.visibleProperty().bind(vBar.managedProperty());
		corner.visibleProperty().bind(corner.managedProperty());
		corner.getStyleClass().setAll("corner");

		DoubleBinding scaledWidth = pane.targetWidth.multiply(pane.scale);
		DoubleBinding scaledHeight = pane.targetHeight.multiply(pane.scale);

		// offset from top left corner so translation is negative
		// XXX changing min/max properties causes a full layout pass (requestLayout) to propagate from the scrollbars
		// this behavior is potentially decremental during animations and continuous zooming
		hBar.minProperty().bind(scaledWidth.subtract(pane.widthProperty()).add(vBar.widthProperty()).negate());
		vBar.minProperty().bind(scaledHeight.subtract(pane.heightProperty()).add(hBar.heightProperty()).negate());
		hBar.setMax(0);
		vBar.setMax(0);

		// bind scrollbars to translation
		Runnable setHBarX = () -> hBar.setValue(hBar.getMin() - affine.getTx());
		Runnable setVBarY = () -> vBar.setValue(vBar.getMin() - affine.getTy());
		affine.txProperty().addListener(o -> setHBarX.run());
		affine.tyProperty().addListener(o -> setVBarY.run());
		hBar.valueProperty().addListener(o -> {
			if (!hBarDown) return;
			affine.setTx(hBar.getMin() - hBar.getValue());
			markChanged();
		});
		vBar.valueProperty().addListener(o -> {
			if (!vBarDown) return;
			affine.setTy(vBar.getMin() - vBar.getValue());
			markChanged();
		});

		// (barMax - barMin) * (bound/targetBound)
		hBar.visibleAmountProperty().bind(
				hBar.maxProperty().subtract(hBar.minProperty())
						.multiply(pane.widthProperty().divide(scaledWidth)));
		vBar.visibleAmountProperty().bind(
				vBar.maxProperty().subtract(vBar.minProperty())
						.multiply(pane.heightProperty().divide(scaledHeight)));

		// fire start and finish events for scrollbars
		hBar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {hBarDown = true; markStart();});
		vBar.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {vBarDown = true; markStart();});
		hBar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {hBarDown = false; markEnd();});
		vBar.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {vBarDown = false; markEnd();});

		// bind viewport to target dimension
		Arrays.asList(
				pane.widthProperty(),
				pane.heightProperty(),
				hBar.layoutBoundsProperty(),
				vBar.layoutBoundsProperty(),
				vBar.managedProperty(),
				hBar.managedProperty(),
				pane.target,
				pane.content).forEach(p -> p.addListener(o -> {
			double width = pane.getWidth() - (vBar.isManaged() ? vBar.getWidth() : 0);
			double height = pane.getHeight() - (hBar.isManaged() ? hBar.getHeight() : 0);
			pane.viewport.set(new BoundingBox(0, 0, width, height));
		}));

		// bind affine/bound changes to viewport
		Arrays.asList(pane.viewport,
				pane.affine.txProperty(),
				pane.affine.tyProperty(),
				pane.scale).forEach(p -> p.addListener(o -> {
			double scale = pane.scale.get();
			pane.targetRect.set(new BoundingBox(-affine.getTx() / scale,
					-affine.getTy() / scale,
					pane.getViewportWidth() / scale,
					pane.getViewportHeight() / scale));
		}));

		pane.fitWidth.addListener(o -> pane.requestLayout());
		pane.fitHeight.addListener(o -> pane.requestLayout());
		pane.scrollMode.addListener(o -> pane.clampAtBound(false));
		pane.fitMode.addListener(o -> pane.clampAtBound(false));
//		affine.setOnTransformChanged(e -> pane.fireAffineEvent(CHANGED));
		setHBarX.run();
		setVBarY.run();
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
					pane.translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
					lastPosition = new Point2D(e.getX(), e.getY());
				}));

		// zoom via touch
		pane.addEventHandler(ZoomEvent.ZOOM_STARTED,
				consumeThenFireIfEnabled(e -> markStart()));
		pane.addEventHandler(ZoomEvent.ZOOM_FINISHED,
				consumeThenFireIfEnabled(e -> markEnd()));
		pane.addEventHandler(ZoomEvent.ZOOM,
				consumeThenFireIfEnabled(e -> pane.scale(e.getZoomFactor(), fromGesture(e))));

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
					pane.translate(e.getDeltaX(), e.getDeltaY());
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
		if (hBar.isManaged()) {
			layoutInArea(hBar, 0, 0,
					contentWidth - (vBar.isManaged() ? vBar.getWidth() : 0),
					contentHeight,
					0, HPos.CENTER, VPos.BOTTOM);
		}
		if (vBar.isManaged()) {
			layoutInArea(vBar, 0, 0,
					contentWidth,
					contentHeight - (hBar.isManaged() ? hBar.getHeight() : 0),
					0, HPos.RIGHT, VPos.CENTER);
		}

		// draw corner on bottom right where two scrollbar meets
		if (hBar.isManaged() && vBar.isManaged()) {
			corner.resizeRelocate(hBar.getWidth(), vBar.getHeight(),
					hBar.getHeight(), vBar.getWidth());
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
