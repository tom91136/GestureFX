package net.kurobako.gesturefx;

import java.util.Optional;
import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.DefaultProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WritableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.util.Duration;

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

	private static final BoundingBox ZERO_BOX = new BoundingBox(0, 0, 0, 0);

	final Affine affine = new Affine();

	// XXX in general, these should be written like the JDK counterpart(lazy instantiation of
	// properties), but, life is too short for these kind of things. If I feel like it, I would
	// rewrite this entire library in Scala (thus, properties can all be `lazy val` and what not)
	// exposed properties
	final BooleanProperty fitWidth = new SimpleBooleanProperty(true);
	final BooleanProperty fitHeight = new SimpleBooleanProperty(true);

	final BooleanProperty vBarEnabled = new SimpleBooleanProperty(true);
	final BooleanProperty hBarEnabled = new SimpleBooleanProperty(true);
	final BooleanProperty gestureEnabled = new SimpleBooleanProperty(true);
	final BooleanProperty clipEnabled = new SimpleBooleanProperty(true);
	final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(PAN);
	private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FIT);
	private final DoubleProperty minScale = new SimpleDoubleProperty(0.5);
	private final DoubleProperty maxScale = new SimpleDoubleProperty(10);
	private final DoubleProperty scrollZoomFactor = new SimpleDoubleProperty(1);

	final ObjectProperty<Transformable> target = new SimpleObjectProperty<>();
	final ObjectProperty<Node> content = new SimpleObjectProperty<>();
	final ObjectProperty<Bounds> targetViewport = new SimpleObjectProperty<>(ZERO_BOX);

	// internal properties
	final DoubleProperty targetWidth = new SimpleDoubleProperty();
	final DoubleProperty targetHeight = new SimpleDoubleProperty();
	final ObjectProperty<Bounds> bounds = new SimpleObjectProperty<>(ZERO_BOX);

	/**
	 * Create a new {@link GesturePane} with the specified {@link Transformable}
	 *
	 * @param target the transformable to apply the transforms to
	 */
	public GesturePane(Transformable target) {
		this();
		setTarget(target);
	}

	/**
	 * Creates a new {@link GesturePane} with the specified node as children(i.e the node gets
	 * added to the pane)
	 *
	 * @param target the node to apply transforms to
	 */
	public GesturePane(Node target) {
		this();
		setContent(target);
	}

	public GesturePane() {
		super();
		target.addListener((o, p, n) -> {
			if (n == null) return;
			content.set(null);
			getChildren().removeIf(x -> !(x instanceof ScrollBar));
			n.setTransform(affine);
			targetWidth.set(n.width());
			targetHeight.set(n.height());
		});

		final ChangeListener<Bounds> layoutBoundsListener = (o, p, n) -> {
			targetWidth.set(n.getWidth());
			targetHeight.set(n.getHeight());
		};
		content.addListener((o, p, n) -> {
			if (p != null)
				p.layoutBoundsProperty().removeListener(layoutBoundsListener);
			if (n == null) return;
			target.set(null);
			getChildren().add(0, n);
			n.getTransforms().add(affine);
			n.layoutBoundsProperty().addListener(layoutBoundsListener);
			targetWidth.set(n.getLayoutBounds().getWidth());
			targetHeight.set(n.getLayoutBounds().getHeight());
		});
	}

	@Override
	protected Skin<?> createDefaultSkin() { return new GesturePaneSkin(this); }

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

	public double getViewportWidth() { return bounds.get().getWidth(); }
	public double getViewportHeight() { return bounds.get().getHeight(); }

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
		return new Point2D(getViewportWidth() / 2, getViewportHeight() / 2);
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

	void scale(double factor, Point2D origin) {
		double delta = factor;
		double scale = affine.getMxx() * factor;
		// clamp at min and max
		if (scale > getMaxScale()) delta = getMaxScale() / affine.getMxx();
		if (scale < getMinScale()) delta = getMinScale() / affine.getMxx();
		affine.prependScale(delta, delta, origin);
		clampAtBound(factor >= 1);
	}

	void translate(double x, double y) {
		affine.prependTranslation(x, y);
		clampAtBound(true);
	}

	void clampAtBound(boolean zoomPositive) {
		double targetWidth = getTargetWidth();
		double targetHeight = getTargetHeight();
		double scaledWidth = affine.getMxx() * targetWidth;
		double scaledHeight = affine.getMyy() * targetHeight;
		double width = getViewportWidth();
		double height = getViewportHeight();


		// clamp translation
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

		// clamp scale
		switch (fitMode.get()) {
			case COVER:
				if (width < scaledWidth && height < scaledHeight)
					return;

				double coverScale = (targetWidth <= 0 || targetHeight <= 0) ?
						                    1 :
						                    Math.max(width / targetWidth, height / targetHeight);
				affine.setMxx(coverScale);
				affine.setMyy(coverScale);
				//TODO need to centre the image back to origin
//					affine.setTy((height - coverScale * targetHeight) / 2);
//				if (height >= scaledHeight)
//					affine.setTx((width - coverScale * targetWidth) / 2);
				break;
			case FIT:
				double fitScale = (targetWidth <= 0 || targetHeight <= 0) ?
						                  0 :
						                  Math.min(width / targetWidth, height / targetHeight);
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
		System.out.println(affine);
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

	public Node getContent() { return content.get(); }
	public ObjectProperty<Node> contentProperty() { return content; }
	public void setContent(Node content) { this.content.set(content); }

	public boolean isVBarEnabled() { return vBarEnabled.get();}
	public BooleanProperty vBarEnabledProperty() { return vBarEnabled; }
	public void setVBarEnabled(boolean enable) { this.vBarEnabled.set(enable); }

	public boolean isHBarEnabled() { return hBarEnabled.get(); }
	public BooleanProperty hBarEnabledProperty() { return hBarEnabled; }
	public void setHBarEnabled(boolean enable) { this.hBarEnabled.set(enable); }

	public boolean isGestureEnabled() { return gestureEnabled.get(); }
	public BooleanProperty gestureEnabledProperty() { return gestureEnabled; }
	public void setGestureEnabled(boolean enable) { this.gestureEnabled.set(enable); }

	public boolean isClipEnabled() { return clipEnabled.get(); }
	public BooleanProperty clipEnabledProperty() { return clipEnabled; }
	public void setClipEnabled(boolean enable) { this.clipEnabled.set(enable); }

	public boolean isFitWidth() { return fitWidth.get(); }
	public BooleanProperty fitWidthProperty() { return fitWidth; }
	public void setFitWidth(boolean fitWidth) { this.fitWidth.set(fitWidth); }

	public boolean isFitHeight() { return fitHeight.get(); }
	public BooleanProperty fitHeightProperty() { return fitHeight; }
	public void setFitHeight(boolean fitHeight) { this.fitHeight.set(fitHeight); }

	public FitMode getFitMode() { return fitMode.get(); }
	public ObjectProperty<FitMode> fitModeProperty() { return fitMode; }
	public void setFitMode(FitMode mode) { this.fitMode.set(mode); }

	public ScrollMode getScrollMode() { return scrollMode.get(); }
	public ObjectProperty<ScrollMode> scrollModeProperty() { return scrollMode; }
	public void setScrollMode(ScrollMode mode) { this.scrollMode.set(mode); }

	public double getMinScale() { return minScale.get(); }
	public DoubleProperty minScaleProperty() { return minScale; }
	public void setMinScale(double scale) { this.minScale.set(scale); }

	public double getMaxScale() { return maxScale.get(); }
	public DoubleProperty maxScaleProperty() { return maxScale; }
	public void setMaxScale(double scale) { this.maxScale.set(scale); }

	public double getCurrentScale() { return affine.getMxx(); }
	public DoubleProperty currentScaleProperty() { return affine.mxxProperty(); }
	public double getCurrentX() { return affine.getTx(); }
	public DoubleProperty currentXProperty() { return affine.txProperty(); }
	public double getCurrentY() { return affine.getTy(); }
	public DoubleProperty currentYProperty() { return affine.tyProperty(); }

	public double getScrollZoomFactor() { return scrollZoomFactor.get(); }
	public DoubleProperty scrollZoomFactorProperty() { return scrollZoomFactor; }
	public void setScrollZoomFactor(double factor) { this.scrollZoomFactor.set(factor); }

	public Bounds getTargetViewport() { return targetViewport.get(); }
	public ObjectProperty<Bounds> targetViewportProperty() { return targetViewport; }

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

}