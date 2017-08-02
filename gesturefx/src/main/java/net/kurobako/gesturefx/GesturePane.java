package net.kurobako.gesturefx;

import java.util.Optional;
import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.DefaultProperty;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.When;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.WritableValue;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Dimension2D;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.util.Duration;

import static net.kurobako.gesturefx.AffineEvent.*;
import static net.kurobako.gesturefx.GesturePane.FitMode.*;
import static net.kurobako.gesturefx.GesturePane.ScrollMode.*;

/**
 * Pane that applies transformations to some implementation of {@link Transformable} when a
 * gesture is applied
 * <p>
 * Terms:
 * <ol>
 * <li>Target - the actual object that receives transformation</li>
 * <li>Viewport - the view area of the target(not counting vertical and horizontal scrollbars)</li>
 * </ol>
 * <p>
 * The measured size of the node defaults the the target's size, you can use the usual
 * {@link Region#setPrefSize(double, double)} and related methods/bindings to
 * provide alternative dimensions.
 * <p>
 * To listen for transformation changes, register event listeners for {@link AffineEvent}, for
 * example:
 * <p>
 * <pre>
 *     {@code
 *
 *     GesturePane pane = //...
 *     pane.addEventHandler(AffineEvent.CHANGED, e ->{ ... });
 *
 *     }
 * </pre>
 * <p>
 * See documentation for the {@link AffineEvent} for more information on the methods provided in
 * the event
 */
@SuppressWarnings({"unused", "WeakerAccess"})
@DefaultProperty("content")
public class GesturePane extends Control {

	// An arbitrary scroll factor that seems to work well(hopefully)
	static final double DEFAULT_SCROLL_FACTOR = 0.095;

	/**
	 * Modes for different minimum scales
	 */
	public enum FitMode {
		/**
		 * Node will be scaled to cover the entire pane
		 */
		COVER,
		/**
		 * Node will be scaled such that any of the edge touches the pane
		 */
		FIT,
		/**
		 * Node will not be scaled
		 */
		CENTER
	}

	/**
	 * Modes for interpreting scroll events
	 */
	public enum ScrollMode {
		/**
		 * Treat scroll as zoom
		 */
		ZOOM,
		/**
		 * Treat scroll as pan
		 */
		PAN
	}


	private ObjectProperty<Transformable> target = new SimpleObjectProperty<>();
	private ObjectProperty<Node> content = new SimpleObjectProperty<>();


//	private Transformable target;

	private final Affine affine = new Affine();
	private final ScrollBar hBar = new ScrollBar();
	private final ScrollBar vBar = new ScrollBar();

	private final BooleanProperty gestureEnabled = new SimpleBooleanProperty(true);
	private final BooleanProperty clipEnabled = new SimpleBooleanProperty(true);
	private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FIT);
	private final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(PAN);

	private final SimpleDoubleProperty minScale = new SimpleDoubleProperty(0.55);
	private final SimpleDoubleProperty maxScale = new SimpleDoubleProperty(10);

	private final SimpleDoubleProperty scrollZoomFactor = new SimpleDoubleProperty(1);

	private final DoubleBinding viewportWidth = widthProperty().subtract(
			new When(vBar.managedProperty())
					.then(vBar.widthProperty())
					.otherwise(0));

	private final DoubleBinding viewportHeight = heightProperty().subtract(
			new When(hBar.managedProperty())
					.then(hBar.heightProperty())
					.otherwise(0));

	private Point2D lastPosition;

	/**
	 * A target that can be transformed
	 */
	public interface Transformable {
		/**
		 * @return the target width, must not be negative
		 */
		double width();
		/**
		 * @return the target height, must no be negative
		 */
		double height();
		/**
		 * Sets the transformation for the target, only happens once
		 *
		 * @param affine the transformation; never null
		 */
		void setTransform(Affine affine);
	}

	private static final class NodeTransformable implements Transformable {
		private final Node node;
		private NodeTransformable(Node node) {this.node = node;}
		@Override
		public double width() { return node.getLayoutBounds().getWidth(); }
		@Override
		public double height() { return node.getLayoutBounds().getHeight(); }
		@Override
		public void setTransform(Affine affine) { node.getTransforms().add(affine); }
	}

	/**
	 * Create a new {@link GesturePane} with the specified {@link Transformable}
	 *
	 * @param target the transformable to apply the transforms to
	 */
	public GesturePane(Transformable target) {
		super();
		setTarget(target);
	}

	@Override
	protected Skin<?> createDefaultSkin() {
		return new SkinBase<GesturePane>(this) {};
	}

	/**
	 * Creates a new {@link GesturePane} with the specified node as children(i.e the node gets
	 * added to the pane)
	 *
	 * @param target the node to apply transforms to
	 */
	public GesturePane(Node target) {
		this();
		content.set(target);
	}


	public GesturePane() {
		super();
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setMinSize(0, 0);
		setFocusTraversable(true);
		cache(false);


		// clip stuff that goes out of bound
		Rectangle rectangle = new Rectangle();
		rectangle.heightProperty().bind(heightProperty());
		rectangle.widthProperty().bind(widthProperty());
//		setClip(rectangle);

		clipProperty().bind(new When(clipEnabled)
				                    .then(rectangle)
				                    .otherwise(new SimpleObjectProperty<>(null)));


		this.target.addListener((o, p, n) -> {
			if (n == null) return;
			this.content.set(null);
			getChildren().removeIf(x -> !(x instanceof ScrollBar));
			n.setTransform(affine);
		});
		this.content.addListener((o, p, n) -> {
			if (n == null) return;
			this.target.set(null);
			getChildren().add(0, n);
			n.getTransforms().add(affine);
		});

		setupGestures();

		hBar.setOrientation(Orientation.HORIZONTAL);
		vBar.setOrientation(Orientation.VERTICAL);
		getChildren().addAll(vBar, hBar);


		hBar.visibleProperty().bind(hBar.managedProperty());
		vBar.visibleProperty().bind(vBar.managedProperty());


		DoubleBinding targetWidth = new DoubleBinding() {
			@Override
			protected double computeValue() { return getTargetWidth(); }
		};
		DoubleBinding targetHeight = new DoubleBinding() {
			@Override
			protected double computeValue() { return getTargetHeight(); }
		};


		DoubleBinding scaledWidth = affine.mxxProperty().multiply(targetWidth);
		DoubleBinding scaledHeight = affine.myyProperty().multiply(targetHeight);
		// offset from top left corner so translation is negative
		hBar.minProperty().bind(scaledWidth.subtract(widthProperty()).negate());
		vBar.minProperty().bind(scaledHeight.subtract(heightProperty()).negate());
		hBar.setMax(0);
		vBar.setMax(0);

		// (barMax - barMin) * (bound/targetBound)
		hBar.visibleAmountProperty().bind(
				hBar.maxProperty().subtract(hBar.minProperty())
						.multiply(widthProperty().divide(scaledWidth)));
		vBar.visibleAmountProperty().bind(
				vBar.maxProperty().subtract(vBar.minProperty())
						.multiply(heightProperty().divide(scaledHeight)));

		hBar.valueProperty().bindBidirectional(affine.txProperty());
		vBar.valueProperty().bindBidirectional(affine.tyProperty());

		affine.setOnTransformChanged(e -> fireAffineEvent(AffineEvent.CHANGED));
	}


	private <T extends Event> EventHandler<T> consumeThenFireIfEnabled(EventHandler<T> handler) {
		return e -> {
			e.consume();
			if (gestureEnabled.get()) handler.handle(e);
		};
	}


	private void setupGestures() {
		boolean disabled = !gestureEnabled.get();

		// translate
		addEventHandler(MouseEvent.MOUSE_PRESSED,
				consumeThenFireIfEnabled(e -> {
					lastPosition = new Point2D(e.getX(), e.getY());
					cache(true);
					fireAffineEvent(CHANGE_STARTED);
				}));
		addEventHandler(MouseEvent.MOUSE_RELEASED,
				consumeThenFireIfEnabled(e -> {
					cache(false);
					fireAffineEvent(CHANGE_FINISHED);
				}));
		addEventHandler(MouseEvent.MOUSE_DRAGGED,
				consumeThenFireIfEnabled(e -> {
					translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
					lastPosition = new Point2D(e.getX(), e.getY());
					fireAffineEvent(CHANGED);
				}));


		// zoom via touch
		addEventHandler(ZoomEvent.ZOOM_STARTED,
				consumeThenFireIfEnabled(e -> fireAffineEvent(CHANGE_STARTED)));
		addEventHandler(ZoomEvent.ZOOM_FINISHED,
				consumeThenFireIfEnabled(e -> fireAffineEvent(CHANGE_FINISHED)));
		addEventHandler(ZoomEvent.ZOOM,
				consumeThenFireIfEnabled(e -> {
					scale(e.getZoomFactor(), fromGesture(e));
					fireAffineEvent(CHANGED);
				}));

		// translate+zoom via mouse/touchpad
		addEventFilter(ScrollEvent.SCROLL_STARTED, consumeThenFireIfEnabled(e -> {
			cache(true);
			fireAffineEvent(CHANGE_STARTED);
		}));
		addEventFilter(ScrollEvent.SCROLL_FINISHED, consumeThenFireIfEnabled(e -> {
			cache(false);
			fireAffineEvent(CHANGE_FINISHED);
		}));
		addEventFilter(ScrollEvent.SCROLL, consumeThenFireIfEnabled(e -> {
			// mouse scroll events only
			if (e.getTouchCount() > 0) return;

			// TODO might be driver and platform specific
			// TODO test on Linux
			// TODO test on different Windows versions
			// TODO test on machines with different touchpad vendor

			// pinch to zoom on trackpad
			if (e.isShortcutDown()) {
				double zoomFactor = DEFAULT_SCROLL_FACTOR * getScrollZoomFactor();
				if (e.getDeltaY() < 0) zoomFactor *= -1;
				scale(1 + zoomFactor, fromGesture(e));
				fireAffineEvent(CHANGED);
				return;
			}

			switch (scrollMode.get()) {
				case ZOOM:
					double zoomFactor = DEFAULT_SCROLL_FACTOR * getScrollZoomFactor();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					scale(1 + zoomFactor, fromGesture(e));
					return;
				case PAN:
					translate(e.getDeltaX(), e.getDeltaY());
					break;
			}
			fireAffineEvent(CHANGED);
		}));

	}
	private void fireAffineEvent(EventType<AffineEvent> type) {
		Transformable t = target.get();
		if (t == null) return;
		fireEvent(new AffineEvent(type, new Affine(affine),
				                         new Dimension2D(t.width(), t.height())));
	}

	private void cache(boolean enable) {
		setCacheHint(enable ? CacheHint.SPEED : CacheHint.QUALITY);
	}

	public double getTargetWidth() {
		if (target.get() != null) return target.get().width();
		else if (content.get() != null) return content.get().getLayoutBounds().getWidth();
		else return 0;
	}
	public double getTargetHeight() {
		if (target.get() != null) return target.get().height();
		else if (content.get() != null) return content.get().getLayoutBounds().getHeight();
		else return 0;
	}

	public double getViewportWidth() { return viewportWidth.get(); }
	public DoubleBinding viewportWidthProperty() { return viewportWidth; }
	public double getViewportHeight() { return viewportHeight.get(); }
	public DoubleBinding viewportHeightProperty() { return viewportHeight; }

	@Override
	protected final void layoutChildren() {
		super.layoutChildren();
		clampAtBound(false);
		fireAffineEvent(AffineEvent.CHANGED);

		if (vBar.isManaged())
			layoutInArea(vBar, 0, 0,
					getWidth(),
					getHeight() - (hBar.isVisible() ? hBar.getHeight() : 0),
					0, HPos.RIGHT, VPos.CENTER);
		if (hBar.isManaged())
			layoutInArea(hBar, 0, 0,
					getWidth() - (vBar.isVisible() ? vBar.getWidth() : 0),
					getHeight(),
					0, HPos.CENTER, VPos.BOTTOM);
	}

	/**
	 * Centre of the current viewport.
	 * <br>
	 * This equivalent to :
	 * <pre>
	 * {@code new Point2D(getViewportWidth()/2, getViewportHeight()/2) })
	 * </pre>
	 *
	 * @return a new point located at the centre of the viewport
	 */
	public Point2D viewportCentre() {
		return new Point2D(getViewportWidth() / 2, viewportHeight.get() / 2);
	}

	/**
	 * The point on the target at which the current centre point of the viewport is.
	 *
	 * @return a point on the target using target's coordinate system
	 */
	public Point2D targetPointAtViewportCentre() {
		try {
			return affine.inverseTransform(viewportCentre());
		} catch (NonInvertibleTransformException e) {
			// TODO what can be done?
			throw new RuntimeException(e);
		}
	}

	/**
	 * Computes the point on the target at the given viewport point.
	 *
	 * @param viewportPoint a point on the viewport
	 * @return a point on the target that corresponds to the viewport point
	 */
	public Optional<Point2D> targetPointAt(Point2D viewportPoint) {
		try {
			return Optional.of(affine.inverseTransform(viewportPoint));
		} catch (NonInvertibleTransformException e) {
			// TODO does this ever happen with just translate and scale?
			return Optional.empty();
		}
	}

	/**
	 * Zooms the target to some scale, the actual effect is bounded by {@link #getMinScale()},
	 * {@link #getMaxScale()}, and dependent on {@link #getFitMode()}
	 *
	 * @param scale the scale
	 * @param relative whether the amount is relative; false means the target will be zoomed to
	 * the specified scale, true means the target will be scaled to the current zoom + the given
	 * scale
	 */
	public void zoomTarget(double scale, boolean relative) {
		scale((relative ? scale + affine.getMxx() : scale) / affine.getMxx(), viewportCentre());
	}

	/**
	 * Zooms the target to the specified scale while animating the scale steps, the actual effect
	 * is bounded by {@link #getMinScale()}, {@link #getMaxScale()}, and dependent on
	 * {@link #getFitMode()}
	 *
	 * @param scale the scale
	 * @param duration the duration of the animation
	 * @param endAction action handler when the animation is finished
	 */
	public void zoomTarget(double scale, Duration duration, EventHandler<ActionEvent> endAction) {
		double mxx = affine.getMxx();
		Point2D offset = targetPointAtViewportCentre().subtract(affine.getTx(), affine.getTy());
		double delta = scale - mxx;


		animateValue(0, 1, duration, v -> {
//			double step = mxx + (delta * v);


//			affine.setTx(affine.getTx() + offset.getX() * affine.getMxx() * v);
//			affine.setTy(affine.getTy() + offset.getY() * affine.getMyy() * v);
//			affine.setMxx(step);
//			affine.setMyy(step);
		}, endAction);

	}

	/**
	 * Translates the target to some point, , the actual effect is dependent on
	 * {@link #getFitMode()}
	 *
	 * @param pointOnTarget a point on the target using the target's coordinate system
	 * @param relative if relative, the {@code pointOnTarget} parameter becomes a delta value
	 */
	public void translateTarget(Point2D pointOnTarget, boolean relative) {
		// move to centre point and apply scale
		Point2D delta = relative ? targetPointAtViewportCentre().add(pointOnTarget) :
				                targetPointAtViewportCentre().subtract(pointOnTarget);
		affine.setTx(affine.getTx() + delta.getX() * affine.getMxx());
		affine.setTy(affine.getTy() + delta.getY() * affine.getMyy());
		clampAtBound(true);
	}


	// TODO incomplete
	public void translateTarget(Point2D pointOnTarget,
	                            Duration duration,
	                            EventHandler<ActionEvent> handler) {
		// move to centre point and apply scale
		Point2D delta = targetPointAtViewportCentre().subtract(pointOnTarget);
		double ttx = delta.getX() * affine.getMxx();
		double tty = delta.getY() * affine.getMyy();
		double tx = affine.getTx();
		double ty = affine.getTy();
		animateValue(0, 1, duration, v -> {
			affine.setTx(tx + ttx * v);
			affine.setTy(ty + tty * v);
			clampAtBound(true);
		}, handler);
	}


	/**
	 * Resets scale to {@code 1.0} and conditionally centres the image depending on the current
	 * {@link FitMode}
	 */
	public final void reset() {
		zoomTarget(1, false);
	}


	private void scale(double factor, Point2D origin) {
		double delta = factor;
		double scale = affine.getMxx() * factor;
		// clamp at min and max
		if (scale > getMaxScale()) delta = getMaxScale() / affine.getMxx();
		if (scale < getMinScale()) delta = getMinScale() / affine.getMxx();
		affine.prependScale(delta, delta, origin);
		clampAtBound(factor >= 1);
	}

	private void translate(double x, double y) {
		affine.prependTranslation(x, y);
		clampAtBound(true);
	}

	@Override
	protected final double computePrefWidth(double height) {
		return getInsets().getLeft() + getTargetWidth() + getInsets().getRight();
	}

	@Override
	protected final double computePrefHeight(double width) {
		return getInsets().getTop() + getTargetHeight() + getInsets().getBottom();
	}


	private void clampAtBound(boolean zoomPositive) {
		System.out.println(targetPointAtViewportCentre());
		double targetWidth = getTargetWidth();
		double targetHeight = getTargetHeight();

		double scaledWidth = affine.getMxx() * targetWidth;
		double scaledHeight = affine.getMyy() * targetHeight;

		double width = viewportWidth.get();
		double height = viewportHeight.get();

		double maxX = width - scaledWidth;
		double maxY = height - scaledHeight;


		if (affine.getTx() < maxX) affine.setTx(maxX);
		if (affine.getTy() < maxY) affine.setTy(maxY);
		if (affine.getTy() > 0) affine.setTy(0);
		if (affine.getTx() > 0) affine.setTx(0);
		if (width >= scaledWidth)
			affine.setTx((width - affine.getMxx() * targetWidth) / 2);
		if (height >= scaledHeight)
			affine.setTy((height - affine.getMyy() * targetHeight) / 2);

		switch (fitMode.get()) {
			case COVER:
				if (width < scaledWidth && height < scaledHeight)
					return;
				double coverScale = Math.max(width / targetWidth, height / targetHeight);
				affine.setMxx(coverScale);
				affine.setMyy(coverScale);
				//TODO need to centre the image back to origin
//					affine.setTy((height - coverScale * targetHeight) / 2);
//				if (height >= scaledHeight)
//					affine.setTx((width - coverScale * targetWidth) / 2);
//				break;
			case FIT:
				double fitScale = Math.min(width / targetWidth, height / targetHeight);
				if (zoomPositive ||
						    affine.getMxx() > fitScale ||
						    affine.getMyy() > fitScale) return;
				affine.setTx((width - fitScale * targetWidth) / 2);
				affine.setTy((height - fitScale * targetHeight) / 2);

				affine.setMxx(fitScale);
				affine.setMyy(fitScale);
				break;
			case CENTER:
				break;
		}
	}


	private final Timeline timeline = new Timeline();
	private void animateValue(double from,
	                          double to,
	                          Duration duration,
	                          DoubleConsumer consumer,
	                          EventHandler<ActionEvent> l) {
		timeline.stop();
		timeline.getKeyFrames().clear();
		KeyValue keyValue = new KeyValue(new WritableValue<Double>() {

			@Override
			public Double getValue() {
				return from;
			}

			@Override
			public void setValue(Double value) {
				consumer.accept(value);
			}

		}, to, Interpolator.EASE_BOTH);
		timeline.getKeyFrames().add(new KeyFrame(duration, keyValue));
		timeline.setOnFinished(l);
		timeline.play();
	}




	public Transformable getTarget() { return target.get(); }
	public ObjectProperty<Transformable> targetProperty() { return target; }
	public void setTarget(Transformable target) { this.target.set(target); }


	public Node getContent() {
		return content.get();
	}
	public ObjectProperty<Node> contentProperty() {
		return content;
	}
	public void setContent(Node content) {
		this.content.set(content);
	}

	public boolean isVBarEnabled() { return vBar.isManaged(); }
	public BooleanProperty vBarEnabledProperty() { return vBar.managedProperty(); }
	public boolean isHBarEnabled() { return hBar.isManaged(); }
	public void setHBarEnabled(boolean enable) { hBar.managedProperty().set(enable); }
	public BooleanProperty hBarEnabledProperty() { return hBar.managedProperty(); }



	public boolean isGestureEnabled() { return gestureEnabled.get(); }
	public BooleanProperty gestureEnabledProperty() { return gestureEnabled; }
	public double getMinScale() { return minScale.get(); }
	public DoubleProperty minScaleProperty() { return minScale; }
	public double getMaxScale() { return maxScale.get(); }
	public DoubleProperty maxScaleProperty() { return maxScale; }
	public double getCurrentScale() { return affine.getMxx(); }
	public DoubleProperty currentScaleProperty() { return affine.mxxProperty(); }
	public double getScrollZoomFactor() { return scrollZoomFactor.get(); }
	public SimpleDoubleProperty scrollZoomFactorProperty() { return scrollZoomFactor; }
	public ScrollMode getScrollMode() { return scrollMode.get(); }
	public ObjectProperty<ScrollMode> scrollModeProperty() { return scrollMode; }
	public FitMode getFitMode() { return fitMode.get(); }
	public ObjectProperty<FitMode> fitModeProperty() { return fitMode; }


	private static Point2D fromGesture(GestureEvent event) {
		return new Point2D(event.getX(), event.getY());
	}

}