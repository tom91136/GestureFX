package net.kurobako.gesturefx;

import java.util.Objects;
import java.util.Optional;
import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.DefaultProperty;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WritableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
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
public class GesturePane extends Control implements GesturePaneOps {

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
	final ObjectProperty<Bounds> targetRect = new SimpleObjectProperty<>(ZERO_BOX);

	// internal properties
	final DoubleProperty targetWidth = new SimpleDoubleProperty();
	final DoubleProperty targetHeight = new SimpleDoubleProperty();
	final ObjectProperty<Bounds> viewport = new SimpleObjectProperty<>(ZERO_BOX);

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
			runLaterOrNowIfOnFXThread(() -> {
				// TODO what if n is null?
				getChildren().removeIf(x -> !(x instanceof ScrollBar));
				n.setTransform(affine);
				targetWidth.set(n.width());
				targetHeight.set(n.height());
			});
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
			runLaterOrNowIfOnFXThread(() -> {
				// TODO what if n is null?
				getChildren().add(0, n);
				n.getTransforms().add(affine);
				n.layoutBoundsProperty().addListener(layoutBoundsListener);
				targetWidth.set(n.getLayoutBounds().getWidth());
				targetHeight.set(n.getLayoutBounds().getHeight());
			});
		});
	}

	private static void runLaterOrNowIfOnFXThread(Runnable r) {
		if (Platform.isFxApplicationThread()) r.run();
		else Platform.runLater(r);
	}

	@Override
	protected Skin<?> createDefaultSkin() { return new GesturePaneSkin(this); }


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
	 * @return a point on the target that corresponds to the viewport point or empty if the point
	 * is not within the the bound returned by {@link #getViewportBound()}
	 */
	public Optional<Point2D> targetPointAt(Point2D viewportPoint) {
		if (!getViewportBound().contains(viewportPoint)) return Optional.empty();
		try {
			return Optional.of(affine.inverseTransform(viewportPoint));
		} catch (NonInvertibleTransformException e) {
			// TODO does this ever happen with just translate and scale?
			return Optional.empty();
		}
	}

	/**
	 * Computes the point on the viewport at the given target point.
	 *
	 * @param targetPoint a point on the target
	 * @return a point on the viewport that corresponds to the target point
	 */
	public Point2D viewportPointAt(Point2D targetPoint) {
		return affine.transform(targetPoint);
	}

	@Override
	public void translateBy(Dimension2D targetAmount) {
		// target coordinate, so append; origin is top left so we we flip signs
		affine.appendTranslation(-targetAmount.getWidth(), -targetAmount.getHeight());
		clampAtBound(true);

	}

	@Override
	public void centreOn(Point2D pointOnTarget) {
		// move to centre point and apply scale
		Point2D delta = pointOnTarget.subtract(targetPointAtViewportCentre());
		translateBy(new Dimension2D(delta.getX(), delta.getY()));
	}

	@Override
	public void zoomTo(double scale, Point2D pivotOnTarget) {
		scale(scale / affine.getMxx(), viewportPointAt(pivotOnTarget));
	}

	@Override
	public void zoomBy(double amount, Point2D pivotOnTarget) {
		scale((amount + affine.getMxx()) / affine.getMxx(), viewportPointAt(pivotOnTarget));
	}

	/**
	 * Animate changes for all operations supported in {@link GesturePaneOps}.
	 * <p>
	 * Animations does not compose so starting an new animation while one is already running will
	 * result in the first animation being stopped arbitrary for the second animation to start.
	 * <p>
	 * The method returns
	 * a type-safe builder
	 * that will limit access to only the available builder methods, for example:
	 * <p>
	 * <pre><code>
	 * animate(Duration.millis(300))
	 * 	.interpolateWith(Interpolator.EASE_IN)
	 * 	.afterFinished(() -> System.out.println("Done!"))
	 * 	.zoomTo(2f);
	 * </code></pre>
	 * </p>
	 *
	 * @param duration the duration of the animation; must not be null
	 * @return a type-safe builder that will allow various options to be set
	 */
	public AnimationInterpolatorBuilder animate(Duration duration) {
		Objects.requireNonNull(duration);
		// keep the builder to one object instantiation
		return new AnimationInterpolatorBuilder() {

			private Runnable afterFinished;
			private Runnable beforeStart;
			private Interpolator interpolator;

			@Override
			public AnimationStartBuilder interpolateWith(Interpolator interpolator) {
				this.interpolator = Objects.requireNonNull(interpolator);
				return this;
			}
			@Override
			public AnimationEndBuilder beforeStart(Runnable action) {
				this.beforeStart = Objects.requireNonNull(action);
				return this;
			}
			@Override
			public GesturePaneOps afterFinished(Runnable action) {
				this.afterFinished = Objects.requireNonNull(action);
				return this;
			}
			@Override
			public void centreOn(Point2D pointOnTarget) {
				// find the delta between current centre and the point and then animate the delta
				Point2D delta = targetPointAtViewportCentre().subtract(pointOnTarget);
				translateBy(new Dimension2D(delta.getX(), delta.getY()));
			}
			@Override
			public void translateBy(Dimension2D targetAmount) {
				// target coordinate so we will be setting tx and ty manually(append) so manually
				// scale the target amount first
				double vx = targetAmount.getWidth() * affine.getMxx();
				double vy = targetAmount.getHeight() * affine.getMyy();
				double tx = affine.getTx(); // fixed point
				double ty = affine.getTy(); // fixed point
				animateValue(0d, 1d, duration, interpolator, v -> {
					affine.setTx(tx + vx * v);
					affine.setTy(ty + vy * v);
					clampAtBound(true);
				}, afterFinished != null ? e -> afterFinished.run() : null);
			}
			@Override
			public void zoomTo(double scale, Point2D pivotOnTarget) {
				double mxx = affine.getMxx(); // fixed point
				double myy = affine.getMyy(); // fixed point
				double dmx = scale - mxx; // delta
				double dmy = scale - myy; // delta
				Point2D pv = viewportPointAt(pivotOnTarget);
				System.out.println(pv);
				if (beforeStart != null) beforeStart.run();
				animateValue(0d, 1d, duration, interpolator, v -> {
					// so, prependScale with pivot is:
					// prependTranslate->prependScale->prependTranslate
					// 1. prependTranslate
					affine.setTx(affine.getTx() - pv.getX());
					affine.setTy(affine.getTy() - pv.getY());
					// 2. prependScale, but extract the coefficient to scale the translation first
					double txx = mxx + dmx * v;
					double tyy = myy + dmy * v;
					affine.setTx(affine.getTx() * (txx / affine.getMxx()));
					affine.setTy(affine.getTy() * (tyy / affine.getMyy()));
					affine.setMxx(txx);
					affine.setMyy(tyy);
					// 3. prependTranslate
					affine.setTx(affine.getTx() + pv.getX());
					affine.setTy(affine.getTy() + pv.getY());
					clampAtBound(true);
				}, afterFinished != null ? e -> afterFinished.run() : null);
			}
			@Override
			public void zoomBy(double scale, Point2D pivotOnTarget) {
				zoomTo(getCurrentScale() + scale, pivotOnTarget);
			}
		};
	}

	public interface AnimationInterpolatorBuilder extends AnimationStartBuilder {
		/**
		 * @param interpolator the interpolator to use for the animation
		 * @return the next group of configurable options in a type-safe builder
		 */
		AnimationStartBuilder interpolateWith(Interpolator interpolator);
	}

	public interface AnimationStartBuilder extends AnimationEndBuilder {
		/**
		 * @param action the action to execute <b>before</b> the animation starts
		 * @return the next group of configurable options in a type-safe builder
		 */
		AnimationEndBuilder beforeStart(Runnable action);
	}

	public interface AnimationEndBuilder extends GesturePaneOps {
		/**
		 * @param action the action to execute <b>after</b> the animation finishes
		 * @return the next group of configurable options in a type-safe builder
		 */
		GesturePaneOps afterFinished(Runnable action);
	}

	/**
	 * Resets scale to {@code 1.0} and conditionally centres the image depending on the current
	 * {@link FitMode}
	 */
	public final void reset() { zoomTo(1, targetPointAtViewportCentre()); }

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
	}

	private final Timeline timeline = new Timeline();
	private void animateValue(double from,
	                          double to,
	                          Duration duration,
	                          Interpolator interpolator, DoubleConsumer consumer,
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
		}, to, interpolator == null ? Interpolator.LINEAR : interpolator);
		timeline.getKeyFrames().add(new KeyFrame(duration, keyValue));
		timeline.setOnFinished(l);
		timeline.play();
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

	public double getViewportWidth() { return viewport.get().getWidth(); }
	public double getViewportHeight() { return viewport.get().getHeight(); }
	public Bounds getViewportBound() { return viewport.get(); }
	public ReadOnlyObjectProperty<Bounds> viewportBoundProperty() { return viewport; }

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

	public void setScrollBarEnabled(boolean enabled) {
		setHBarEnabled(enabled);
		setVBarEnabled(enabled);
	}

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

	public double getCurrentX() { return affine.getTx() / affine.getMxx(); }
	public DoubleBinding currentXProperty() {
		return affine.txProperty().divide(affine.mxxProperty());
	}
	public double getCurrentY() { return affine.getTy() / affine.getMyy(); }
	public DoubleBinding currentYProperty() {
		return affine.tyProperty().divide(affine.myyProperty());
	}

	public double getScrollZoomFactor() { return scrollZoomFactor.get(); }
	public DoubleProperty scrollZoomFactorProperty() { return scrollZoomFactor; }
	public void setScrollZoomFactor(double factor) { this.scrollZoomFactor.set(factor); }

	public Bounds getTargetViewport() { return targetRect.get(); }
	public ObjectProperty<Bounds> targetViewportProperty() { return targetRect; }

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