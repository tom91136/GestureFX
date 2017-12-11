package net.kurobako.gesturefx;

import java.util.Arrays;

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
		hBar.managedProperty().bind(pane.hBarEnabled);
		vBar.managedProperty().bind(pane.vBarEnabled);
		corner.managedProperty().bind(pane.hBarEnabled.and(pane.vBarEnabled));

		// setup scrollbars
		getChildren().addAll(vBar, hBar, corner);
		hBar.setOrientation(Orientation.HORIZONTAL);
		vBar.setOrientation(Orientation.VERTICAL);
		hBar.visibleProperty().bind(hBar.managedProperty());
		vBar.visibleProperty().bind(vBar.managedProperty());
		corner.visibleProperty().bind(corner.managedProperty());
		corner.getStyleClass().setAll("corner");

		DoubleBinding scaledWidth = pane.targetWidth.multiply(affine.mxxProperty());
		DoubleBinding scaledHeight = pane.targetHeight.multiply(affine.myyProperty());

		// offset from top left corner so translation is negative
		hBar.minProperty().bind(scaledWidth.subtract(pane.widthProperty()).negate());
		vBar.minProperty().bind(scaledHeight.subtract(pane.heightProperty()).negate());
		hBar.setMax(0);
		vBar.setMax(0);

		// bind scrollbars to translation
		Runnable setHBarX = () -> hBar.setValue(hBar.getMin() - affine.getTx());
		Runnable setVBarY = () -> vBar.setValue(vBar.getMin() - affine.getTy());
		affine.txProperty().addListener(o -> setHBarX.run());
		affine.tyProperty().addListener(o -> setVBarY.run());
		hBar.valueProperty().addListener(o -> affine.setTx(hBar.getMin() - hBar.getValue()));
		vBar.valueProperty().addListener(o -> affine.setTy(vBar.getMin() - vBar.getValue()));

		// (barMax - barMin) * (bound/targetBound)
		hBar.visibleAmountProperty().bind(
				hBar.maxProperty().subtract(hBar.minProperty())
						.multiply(pane.widthProperty().divide(scaledWidth)));
		vBar.visibleAmountProperty().bind(
				vBar.maxProperty().subtract(vBar.minProperty())
						.multiply(pane.heightProperty().divide(scaledHeight)));

		// fire start and finish events for scrollbars
		EventHandler<MouseEvent> pressedHandler = e ->
				pane.fireAffineEvent(AffineEvent.CHANGE_STARTED);
		EventHandler<MouseEvent> releasedHandler = e ->
				pane.fireAffineEvent(AffineEvent.CHANGE_FINISHED);
		hBar.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler);
		hBar.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler);
		vBar.addEventFilter(MouseEvent.MOUSE_PRESSED, pressedHandler);
		vBar.addEventFilter(MouseEvent.MOUSE_RELEASED, releasedHandler);

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
				pane.affine.mxxProperty(),
				pane.affine.myyProperty()).forEach(p -> p.addListener(o -> {
			double mxx = affine.getMxx();
			double myy = affine.getMyy();
			pane.targetRect.set(new BoundingBox(-affine.getTx() / mxx,
					-affine.getTy() / myy,
					pane.getViewportWidth() / mxx,
					pane.getViewportHeight() / myy));
		}));

		pane.fitWidth.addListener(o -> pane.requestLayout());
		pane.fitHeight.addListener(o -> pane.requestLayout());
		pane.scrollMode.addListener(o -> pane.clampAtBound(false));
		affine.setOnTransformChanged(e -> pane.fireAffineEvent(CHANGED));
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

	private void setupGestures() {
		// translate
		pane.addEventHandler(MouseEvent.MOUSE_PRESSED,
				consumeThenFireIfEnabled(e -> {
					lastPosition = new Point2D(e.getX(), e.getY());
					cache(true);
					pane.fireAffineEvent(CHANGE_STARTED);
				}));
		pane.addEventHandler(MouseEvent.MOUSE_RELEASED,
				consumeThenFireIfEnabled(e -> {
					cache(false);
					pane.fireAffineEvent(CHANGE_FINISHED);
				}));
		pane.addEventHandler(MouseEvent.MOUSE_DRAGGED,
				consumeThenFireIfEnabled(e -> {
					pane.translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
					lastPosition = new Point2D(e.getX(), e.getY());
				}));

		// zoom via touch
		pane.addEventHandler(ZoomEvent.ZOOM_STARTED,
				consumeThenFireIfEnabled(e -> pane.fireAffineEvent(CHANGE_STARTED)));
		pane.addEventHandler(ZoomEvent.ZOOM_FINISHED,
				consumeThenFireIfEnabled(e -> pane.fireAffineEvent(CHANGE_FINISHED)));
		pane.addEventHandler(ZoomEvent.ZOOM,
				consumeThenFireIfEnabled(e -> {
					double factor = e.getZoomFactor();
					pane.scale(factor, factor, fromGesture(e));
				}));

		// translate+zoom via mouse/touchpad
		pane.addEventHandler(ScrollEvent.SCROLL_STARTED, consumeThenFireIfEnabled(e -> {
			cache(true);
			pane.fireAffineEvent(CHANGE_STARTED);
		}));
		pane.addEventHandler(ScrollEvent.SCROLL_FINISHED, consumeThenFireIfEnabled(e -> {
			cache(false);
			pane.fireAffineEvent(CHANGE_FINISHED);
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
				// XXX literally no way to tell start and finish for this kind of scroll :(
				double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.getScrollZoomFactorX();
				if (e.getDeltaY() < 0) zoomFactor *= -1;
				pane.scale(1 + zoomFactor,1 + zoomFactor, fromGesture(e));
				return;
			}
			switch (pane.scrollMode.get()) {
				case ZOOM:
					double zoomFactor = DEFAULT_SCROLL_FACTOR * pane.getScrollZoomFactorX();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					pane.scale(1 + zoomFactor, 1 + zoomFactor, fromGesture(e));
					return;
				case PAN:
					pane.translate(e.getDeltaX(), e.getDeltaY());
					break;
			}
		}));
	}

//	private void fireAffineEvent(EventType<AffineEvent> type) {
//		Dimension2D dimension = new Dimension2D(pane.getTargetWidth(), pane.getTargetHeight());
//		pane.fireEvent(new AffineEvent(type, new Affine(affine), dimension));
//	}

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

		// draw corner on bottom right where two scrollbar meets
		if(hBar.isManaged() && vBar.isManaged()){
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
//		pane.fireAffineEvent(CHANGED);
	}
}
