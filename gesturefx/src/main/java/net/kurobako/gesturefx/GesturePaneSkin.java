package net.kurobako.gesturefx;

import javafx.beans.InvalidationListener;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.When;
import javafx.beans.property.SimpleObjectProperty;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.BoundingBox;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.SkinBase;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
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

		// setup scrollbars
		getChildren().addAll(vBar, hBar);
		hBar.setOrientation(Orientation.HORIZONTAL);
		vBar.setOrientation(Orientation.VERTICAL);
		hBar.visibleProperty().bind(hBar.managedProperty());
		vBar.visibleProperty().bind(vBar.managedProperty());

		DoubleBinding scaledWidth = pane.targetWidth.multiply(affine.mxxProperty());
		DoubleBinding scaledHeight = pane.targetHeight.multiply(affine.myyProperty());

		// offset from top left corner so translation is negative
		hBar.minProperty().bind(scaledWidth.subtract(pane.widthProperty()).negate());
		vBar.minProperty().bind(scaledHeight.subtract(pane.heightProperty()).negate());
		hBar.setMax(0);
		vBar.setMax(0);
		// (barMax - barMin) * (bound/targetBound)
		hBar.visibleAmountProperty().bind(
				hBar.maxProperty().subtract(hBar.minProperty())
						.multiply(pane.widthProperty().divide(scaledWidth)));
		vBar.visibleAmountProperty().bind(
				vBar.maxProperty().subtract(vBar.minProperty())
						.multiply(pane.heightProperty().divide(scaledHeight)));
		// bind scrollbars to translation
		affine.txProperty().addListener(o -> {
			hBar.setValue(hBar.getMin() - affine.getTx());
		});
		affine.tyProperty().addListener(o -> {
			vBar.setValue(vBar.getMin() - affine.getTy());
		});
		hBar.valueProperty().addListener(o -> {
//			if(!Double.isFinite(hBar.getValue())) return;
			affine.setTx(hBar.getMin() - hBar.getValue());});
		vBar.valueProperty().addListener(o -> {
//			if(!Double.isFinite(vBar.getValue())) return;
			affine.setTy(vBar.getMin() - vBar.getValue());});

		// bind visibility to managed prop
		hBar.managedProperty().bind(pane.hBarEnabled);
		vBar.managedProperty().bind(pane.vBarEnabled);

		affine.setOnTransformChanged(e -> fireAffineEvent(CHANGED));

		// bind bounds to target dimension
		InvalidationListener l = o -> {
			double width = pane.getWidth() - (vBar.isVisible() ? vBar.getWidth() : 0);
			double height = pane.getHeight() - (hBar.isVisible() ? hBar.getHeight() : 0);
			pane.bounds.set(new BoundingBox(0, 0, width, height));
		};
		pane.widthProperty().addListener(l);
		pane.heightProperty().addListener(l);
		vBar.layoutBoundsProperty().addListener(l);
		hBar.layoutBoundsProperty().addListener(l);
		pane.target.addListener(l);
		pane.content.addListener(l);

		InvalidationListener targetVPL = o -> {
			double mxx = affine.getMxx();
			double myy = affine.getMyy();
			pane.targetViewport.set(new BoundingBox(-affine.getTx() / mxx,
					                                       -affine.getTy() / myy,
					                                       pane.getViewportWidth() / mxx,
					                                       pane.getViewportHeight() / myy));
		};

		pane.bounds.addListener(targetVPL);
		pane.affine.txProperty().addListener(targetVPL);
		pane.affine.tyProperty().addListener(targetVPL);
		pane.affine.mxxProperty().addListener(targetVPL);
		pane.affine.myyProperty().addListener(targetVPL);

		pane.fitWidth.addListener(o -> pane.requestLayout());
		pane.fitHeight.addListener(o -> pane.requestLayout());

		setupGestures();
		pane.scrollMode.addListener(o -> pane.clampAtBound(false));
	}

	private <T extends Event> EventHandler<T> consumeThenFireIfEnabled(EventHandler<T> handler) {
		return e -> {
			e.consume();
			if (pane.gestureEnabled.get()) handler.handle(e);
		};
	}

	private Point2D lastPosition;

	private void setupGestures() {
		// translate
		pane.addEventHandler(MouseEvent.MOUSE_PRESSED,
				consumeThenFireIfEnabled(e -> {
					lastPosition = new Point2D(e.getX(), e.getY());
					cache(true);
					fireAffineEvent(CHANGE_STARTED);
				}));
		pane.addEventHandler(MouseEvent.MOUSE_RELEASED,
				consumeThenFireIfEnabled(e -> {
					cache(false);
					fireAffineEvent(CHANGE_FINISHED);
				}));
		pane.addEventHandler(MouseEvent.MOUSE_DRAGGED,
				consumeThenFireIfEnabled(e -> {
					pane.translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
					lastPosition = new Point2D(e.getX(), e.getY());
					fireAffineEvent(CHANGED);
				}));

		// zoom via touch
		pane.addEventHandler(ZoomEvent.ZOOM_STARTED,
				consumeThenFireIfEnabled(e -> fireAffineEvent(CHANGE_STARTED)));
		pane.addEventHandler(ZoomEvent.ZOOM_FINISHED,
				consumeThenFireIfEnabled(e -> fireAffineEvent(CHANGE_FINISHED)));
		pane.addEventHandler(ZoomEvent.ZOOM,
				consumeThenFireIfEnabled(e -> {
					pane.scale(e.getZoomFactor(), fromGesture(e));
					fireAffineEvent(CHANGED);
				}));

		// translate+zoom via mouse/touchpad
		pane.addEventHandler(ScrollEvent.SCROLL_STARTED, consumeThenFireIfEnabled(e -> {
			cache(true);
			fireAffineEvent(CHANGE_STARTED);
		}));
		pane.addEventHandler(ScrollEvent.SCROLL_FINISHED, consumeThenFireIfEnabled(e -> {
			cache(false);
			fireAffineEvent(CHANGE_FINISHED);
		}));
		pane.addEventHandler(ScrollEvent.SCROLL, consumeThenFireIfEnabled(e -> {
			// mouse scroll events only
			if (e.getTouchCount() > 0) return;

			// TODO might be driver and platform specific
			// TODO test on Linux
			// TODO test on different Windows versions
			// TODO test on machines with different touchpad vendor

			// pinch to zoom on trackpad
			if (e.isShortcutDown()) {
				double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.getScrollZoomFactor();
				if (e.getDeltaY() < 0) zoomFactor *= -1;
				pane.scale(1 + zoomFactor, fromGesture(e));
				fireAffineEvent(CHANGED);
				return;
			}
			switch (pane.scrollMode.get()) {
				case ZOOM:
					double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.getScrollZoomFactor();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					pane.scale(1 + zoomFactor, fromGesture(e));
					return;
				case PAN:
					pane.translate(e.getDeltaX(), e.getDeltaY());
					break;
			}
			fireAffineEvent(CHANGED);
		}));
	}

	private void fireAffineEvent(EventType<AffineEvent> type) {
		Dimension2D dimension = new Dimension2D(pane.getTargetWidth(), pane.getTargetHeight());
		pane.fireEvent(new AffineEvent(type, new Affine(affine), dimension));
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
		super.layoutChildren(contentX, contentY, contentWidth, contentHeight);
		// round 2
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
		Node content = pane.getContent();
		if (content != null) {
			layoutInArea(content,
					contentX, contentY,
					contentWidth, contentHeight,
					-1,
					HPos.LEFT, VPos.TOP);
		}
		pane.clampAtBound(false);
		fireAffineEvent(CHANGED);
	}
}
