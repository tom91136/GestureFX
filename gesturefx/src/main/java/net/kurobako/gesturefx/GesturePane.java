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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WritableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.Skin;
import javafx.scene.layout.Region;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;
import javafx.util.Duration;

import static net.kurobako.gesturefx.GesturePane.FitMode.FIT;
import static net.kurobako.gesturefx.GesturePane.FitMode.UNBOUNDED;
import static net.kurobako.gesturefx.GesturePane.ScrollBarPolicy.AS_NEEDED;
import static net.kurobako.gesturefx.GesturePane.ScrollMode.PAN;

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
 * To listen for transformation changes, register event listeners for {@link AffineEvent}, for
 * example:
 * <pre>
 * {@code
 * GesturePane pane = //...
 * pane.addEventHandler(AffineEvent.CHANGED, e ->{ ... });
 * }
 * </pre>
 * See documentation for the {@link AffineEvent} for more information on the methods provided in
 * the event
 */
@SuppressWarnings({"unused", "WeakerAccess"})
@DefaultProperty("content")
public class GesturePane extends Control implements GesturePaneOps {

	private static final BoundingBox ZERO_BOX = new BoundingBox(0, 0, 0, 0);
	private static final String DEFAULT_STYLE_CLASS = "gesture-pane";

	public static final double DEFAULT_MIN_SCALE = 0.5f;
	public static final double DEFAULT_MAX_SCALE = 10f;
	public static final double DEFAULT_ZOOM_FACTOR = 1f;

	final Affine affine = new Affine();
	private boolean inhibitPropEvent = false;
	private boolean clampExternalScale = true;


	// XXX in general, these should be written like the JDK counterpart(lazy instantiation of
	// properties), but, life is too short for these kind of things. If I feel like it, I would
	// rewrite this entire library in Scala (thus, properties can all be `lazy val` and what not)
	// exposed properties
	final BooleanProperty fitWidth = new SimpleBooleanProperty(true);
	final BooleanProperty fitHeight = new SimpleBooleanProperty(true);

	final ObjectProperty<ScrollBarPolicy> vbarPolicy = new SimpleObjectProperty<>(AS_NEEDED);
	final ObjectProperty<ScrollBarPolicy> hbarPolicy = new SimpleObjectProperty<>(AS_NEEDED);
	final BooleanProperty gestureEnabled = new SimpleBooleanProperty(true);
	final BooleanProperty clipEnabled = new SimpleBooleanProperty(true);
	final BooleanProperty changing = new SimpleBooleanProperty(false);
	final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(PAN);
	final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FIT);
	final BooleanProperty invertScrollTranslate = new SimpleBooleanProperty(false);



	final DoubleProperty scaleX = new SimpleDoubleProperty(1);
	final DoubleProperty scaleY = new SimpleDoubleProperty(1);


	final DoubleProperty scrollZoomFactor = new SimpleDoubleProperty(DEFAULT_ZOOM_FACTOR);

	final BooleanProperty lockScaleX = new SimpleBooleanProperty(false);
	final BooleanProperty lockScaleY = new SimpleBooleanProperty(false);

	final BooleanProperty bindScale = new SimpleBooleanProperty(false);

	private final DoubleProperty minScale = new SimpleDoubleProperty(DEFAULT_MIN_SCALE);
	private final DoubleProperty maxScale = new SimpleDoubleProperty(DEFAULT_MAX_SCALE);

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
		// can't use bindBidirectional here because we need to clamp 
		// scale -> mxx,myy but not the other way around
		affine.mxxProperty().addListener(o -> scaleX.set(affine.getMxx()));
		affine.myyProperty().addListener(o -> scaleY.set(affine.getMyy()));
		scaleX.addListener(e -> {
			if (clampExternalScale) clampAtBound(true);
			affine.setMxx(scaleX.get());
			if (!inhibitPropEvent) fireAffineEvent(AffineEvent.CHANGED);

			boolean bind = bindScale.get();
			if(bind) {
				scaleY.set(scaleX.get());
			}

		});
		scaleY.addListener(e -> {
			if (clampExternalScale) clampAtBound(true);
			affine.setMyy(scaleY.get());
			if (!inhibitPropEvent) fireAffineEvent(AffineEvent.CHANGED);
		});

		getStyleClass().setAll(DEFAULT_STYLE_CLASS);
		setAccessibleRole(AccessibleRole.SCROLL_PANE);
		target.addListener((o, p, n) -> {
			if (n == null) return;
			content.set(null);
			runLaterOrNowIfOnFXThread(() -> {
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
			if (p != null){
				p.layoutBoundsProperty().removeListener(layoutBoundsListener);
				getChildren().remove(p);
			}
			if (n == null) return;
			target.set(null);
			if(n instanceof Parent){
				((Parent) n).layout();
			}
			n.applyCss();
			runLaterOrNowIfOnFXThread(() -> {
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

	// apparently, performance is an issue here, see
	// https://bitbucket.org/controlsfx/controlsfx/pull-requests/519/this-is-the-fix-for-https
	// -javafx/diff
	private String stylesheet;
	@Override
	public String getUserAgentStylesheet() {
		if (stylesheet == null)
			stylesheet = GesturePane.class.getResource("gesturepane.css").toExternalForm();
		return stylesheet;

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
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		atomicallyChange(() -> {
			// target coordinate, so append; origin is top left so we we flip signs
			affine.appendTranslation(-targetAmount.getWidth(), -targetAmount.getHeight());
			clampAtBound(true);
		});
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void centreOn(Point2D pointOnTarget) {
		// move to centre point and apply scale
		Point2D delta = pointOnTarget.subtract(targetPointAtViewportCentre());
		translateBy(new Dimension2D(delta.getX(), delta.getY()));
	}

	@Override
	public void centreOnX(double pointOnTarget) {
		// move to centre point and apply scale
		double delta = pointOnTarget - targetPointAtViewportCentre().getX();
		translateBy(new Dimension2D(delta, 0));
	}

	@Override
	public void centreOnY(double pointOnTarget) {
		// move to centre point and apply scale
		double delta = pointOnTarget - targetPointAtViewportCentre().getY();
		translateBy(new Dimension2D(0, delta));
	}

	@Override
	public void zoomTo(double scaleX,double scaleY, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(scaleX / getCurrentScaleX(),scaleY / getCurrentScaleY(), viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void zoomToX(double scaleX, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(scaleX / getCurrentScaleX(),1, viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void zoomToY(double scaleY, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(1,scaleY / getCurrentScaleY(), viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void zoomBy(double amountX,double amountY, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(
				(amountX + getCurrentScaleX()) / getCurrentScaleX(),
				(amountY + getCurrentScaleY()) / getCurrentScaleY(),
				viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void zoomByX(double amountX, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale((amountX + getCurrentScaleX()) / getCurrentScaleX(), 1, viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	@Override
	public void zoomByY(double amountY, Point2D pivotOnTarget) {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(1, (amountY + getCurrentScaleY()) / getCurrentScaleY(), viewportPointAt(pivotOnTarget));
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}

	/**
	 * Animate changes for all operations supported in {@link GesturePaneOps}.
	 * <br>
	 * Animations does not compose so starting an new animation while one is already running will
	 * result in the first animation being stopped arbitrary for the second animation to start.
	 * <br>
	 * The method returns a type-safe builder that will limit access to only the available builder
	 * methods, for example:
	 * <pre>
	 * {@code
	 * animate(Duration.millis(300))
	 * 	.interpolateWith(Interpolator.EASE_IN)
	 * 	.afterFinished(() -> System.out.println("Done!"))
	 * 	.zoomTo(2f);
	 * }
	 * </pre>
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
				Point2D delta = pointOnTarget.subtract(targetPointAtViewportCentre());
				translateBy(new Dimension2D(delta.getX(), delta.getY()));
			}

			@Override
			public void centreOnX(double pointOnTarget) {
				// move to centre point and apply scale
				double delta = pointOnTarget - targetPointAtViewportCentre().getX();
				translateBy(new Dimension2D(delta, 0));
			}

			@Override
			public void centreOnY(double pointOnTarget) {
				// move to centre point and apply scale
				double delta = pointOnTarget - targetPointAtViewportCentre().getY();
				translateBy(new Dimension2D(0, delta));
			}

			private void markStart() {
				changing.set(true);
				if (beforeStart != null) beforeStart.run();
				fireAffineEvent(AffineEvent.CHANGE_STARTED);
				inhibitPropEvent = true;
				clampExternalScale = false;
			}

			private void markEnd() {
				inhibitPropEvent = false;
				clampExternalScale = true;
				fireAffineEvent(AffineEvent.CHANGE_FINISHED);
				if (afterFinished != null) afterFinished.run();
				changing.set(false);
			}

			@Override
			public void translateBy(Dimension2D targetAmount) {
				// target coordinate so we will be setting tx and ty manually(append) so manually
				// scale the target amount first
				double vx = -targetAmount.getWidth() * getCurrentScaleX();
				double vy = -targetAmount.getHeight() * getCurrentScaleY();
				double tx = affine.getTx(); // fixed point
				double ty = affine.getTy(); // fixed point
				markStart();
				animateValue(duration, interpolator, v -> {
					affine.setTx(tx + vx * v);
					affine.setTy(ty + vy * v);
					clampAtBound(true);
				}, e -> markEnd());
			}
			@Override
			public void zoomTo(double targetScaleX,double targetScaleY,  Point2D pivotOnTarget) {
				double initialScaleX = scaleX.get(); // fixed point
				double initialScaleY = scaleY.get(); // fixed point
				double dsX = clamp(getMinScale(), getMaxScale(), targetScaleX) - initialScaleX; // delta
				double dsY = clamp(getMinScale(), getMaxScale(), targetScaleY) - initialScaleY; // delta
				Point2D pv = viewportPointAt(pivotOnTarget);
				markStart();
				animateValue(duration, interpolator, v -> {
					// so, prependScale with pivot is:
					// prependTranslate->prependScale->prependTranslate
					// 1. prependTranslate
					affine.setTx(affine.getTx() - pv.getX());
					affine.setTy(affine.getTy() - pv.getY());
					// 2. prependScale, but extract the coefficient to scale the translation first
					double dssX = initialScaleX + dsX * v;
					double dssY = initialScaleY + dsY * v;
					double currentScaleX = scaleX.get();
					double currentScaleY = scaleY.get();
					affine.setTx(affine.getTx() * (dssX / currentScaleX));
					affine.setTy(affine.getTy() * (dssY / currentScaleY));
					scaleX.set(dssX);
					scaleY.set(dssY);
					// 3. prependTranslate
					affine.setTx(affine.getTx() + pv.getX());
					affine.setTy(affine.getTy() + pv.getY());
					clampAtBound(false);
					fireAffineEvent(AffineEvent.CHANGED);
				}, e -> markEnd());
			}

			@Override
			public void zoomToX(double scaleX, Point2D pivotOnTarget) {
				zoomTo(scaleX, scaleY.get(), pivotOnTarget);
			}

			@Override
			public void zoomToY(double scaleY, Point2D pivotOnTarget) {
				zoomTo(scaleX.get(), scaleY, pivotOnTarget);
			}

			@Override
			public void zoomBy(double scaleX, double scaleY, Point2D pivotOnTarget) {
				zoomTo(getCurrentScaleX() + scaleX, getCurrentScaleY() + scaleY, pivotOnTarget);
			}

			@Override
			public void zoomByX(double scaleX, Point2D pivotOnTarget) {
				zoomTo(getCurrentScaleX() + scaleX, getCurrentScaleY(), pivotOnTarget);
			}

			@Override
			public void zoomByY(double scaleY, Point2D pivotOnTarget) {
				zoomTo(getCurrentScaleX(), getCurrentScaleY() + scaleY, pivotOnTarget);

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
	 * Resets zoom to minimum scale
	 */
	public final void reset() {
		zoomTo(getMinScale(), targetPointAtViewportCentre());
	}

	/**
	 * Resets zoom to the minimum scale and conditionally centres the image depending on the
	 * current
	 * {@link FitMode}
	 */
	public void cover() {
		fireAffineEvent(AffineEvent.CHANGE_STARTED);
		scale(getMinScale() / getCurrentScaleX(),getMinScale() / getCurrentScaleY(), viewportPointAt(targetPointAtViewportCentre()));
		if (fitMode.get() == FitMode.COVER) {
			double width = getViewportWidth();
			double height = getViewportHeight();
			double scale = getCurrentScaleX();
			affine.setTy((height - scale * getTargetHeight()) / 2);
			if (height >= scale * getTargetHeight())
				affine.setTx((width - scale * getTargetWidth()) / 2);
		}
		fireAffineEvent(AffineEvent.CHANGE_FINISHED);
	}


	final void scale(double factor, Point2D origin) {
		scale(factor, factor, origin);
	}

	final void scale(double factorX, double factorY, Point2D origin) {


		atomicallyChange(() -> {

			double deltaX = factorX;
			double deltaY = factorY;

			if (lockScaleX.get()) deltaX = 1;
			if (lockScaleY.get()) deltaY = 1;
			double scaleX = getCurrentScaleX() * factorX;
			double scaleY = getCurrentScaleY() * factorY;
			// clamp at min and max
			if (scaleX > getMaxScale()) deltaX = getMaxScale() / getCurrentScaleX();
			if (scaleX < getMinScale()) deltaX = getMinScale() / getCurrentScaleX();

			if (scaleY > getMaxScale()) deltaY = getMaxScale() / getCurrentScaleY();
			if (scaleY < getMinScale()) deltaY = getMinScale() / getCurrentScaleY();


			affine.prependScale(deltaX, deltaY, origin);
			clampAtBound(factorX >= 1 || factorY > 1);
		});
	}


	final void translate(double x, double y) {
		atomicallyChange(() -> {
			affine.prependTranslation(x, y);
			clampAtBound(true);
		});
	}

	private static double clamp(double min, double max, double value) {
		return Math.max(min, Math.min(max, value));
	}

	final void clampAtBound(boolean zoomPositive) {
		double scaleX = getCurrentScaleX();
		double scaleY = getCurrentScaleY();
		double targetWidth = getTargetWidth();
		double targetHeight = getTargetHeight();
		double scaledWidth = scaleX * targetWidth;
		double scaledHeight = scaleY * targetHeight;
		double width = getViewportWidth();
		double height = getViewportHeight();

		// clamp translation
		double minX = width - scaledWidth;
		double minY = height - scaledHeight;

		double tx = affine.getTx();
		double ty = affine.getTy();
		double tsX = scaleX;
		double tsY = scaleY;

		if (fitMode.get() != UNBOUNDED) {

			tx = clamp(minX, 0, tx);
			ty = clamp(minY, 0, ty);
			if (width >= scaledWidth) tx = (width - scaleX * targetWidth) / 2;
			if (height >= scaledHeight) ty = (height - scaleY * targetHeight) / 2;
		}

		// clamp scale
		switch (fitMode.get()) {
			case COVER:
				if (width < scaledWidth && height < scaledHeight) break;
				tsX = tsY = targetWidth <= 0 || targetHeight <= 0 ?
						1 :
						Math.max(width / targetWidth, height / targetHeight);
				break;
			case FIT:
				double fitScale = (targetWidth <= 0 || targetHeight <= 0) ?
						0 :
						Math.min(width / targetWidth, height / targetHeight);
				if (zoomPositive || scaleX > fitScale) break;
				tx = (width - fitScale * targetWidth) / 2;
				ty = (height - fitScale * targetHeight) / 2;
				tsX = tsY  = fitScale;
				break;
			default:
				break;
		}

		// to prevent excessive affine events as we don't have access to the atomic change field 
		affine.setTx(tx);
		affine.setTy(ty);
		this.scaleX.set(tsX);
		this.scaleY.set(tsY);

	}

	private final Timeline timeline = new Timeline();
	private void animateValue(Duration duration,
	                          Interpolator interpolator, DoubleConsumer consumer,
	                          EventHandler<ActionEvent> l) {
		timeline.stop();
		timeline.getKeyFrames().clear();
		KeyValue keyValue = new KeyValue(new WritableValue<Double>() {
			@Override public Double getValue() { return 0.0; }
			@Override public void setValue(Double value) {
				consumer.accept(value);
			}
		}, 1.0, interpolator == null ? Interpolator.LINEAR : interpolator);
		timeline.getKeyFrames().add(new KeyFrame(duration, keyValue));
		timeline.setOnFinished(l);
		timeline.play();
	}

	private void atomicallyChange(Runnable e) {
		inhibitPropEvent = true;
		e.run();
		inhibitPropEvent = false;
		fireAffineEvent(AffineEvent.CHANGED);
	}

	Affine lastAffine = null;
	final void fireAffineEvent(EventType<AffineEvent> type) {
		if (type == AffineEvent.CHANGED &&
				lastAffine != null &&
				affine.similarTo(lastAffine, getViewportBound(), 0.001)) return;
		Dimension2D dimension = new Dimension2D(getTargetWidth(), getTargetHeight());
		Affine snapshot = getAffine();
		fireEvent(new AffineEvent(type, snapshot, lastAffine, dimension));
		lastAffine = snapshot;
	}

	/**
	 * @return the current target/content node's width if set; 0 if unset.
	 */
	public double getTargetWidth() {
		if (target.get() != null) return target.get().width();
		else if (content.get() != null) return content.get().getLayoutBounds().getWidth();
		else return 0;
	}
	/**
	 * @return the current target/content node's height if set; 0 if unset.
	 */
	public double getTargetHeight() {
		if (target.get() != null) return target.get().height();
		else if (content.get() != null) return content.get().getLayoutBounds().getHeight();
		else return 0;
	}

	public double getViewportWidth() { return viewport.get().getWidth(); }
	public double getViewportHeight() { return viewport.get().getHeight(); }
	public Bounds getViewportBound() { return viewport.get(); }
	public ReadOnlyObjectProperty<Bounds> viewportBoundProperty() { return viewport; }

	/**
	 * @return the current target if set; null if unset.
	 * <b>NOTE</b>: Targets and content nodes are mutually exclusive, if one is set, the other one
	 * is automatically set to null
	 */
	public Transformable getTarget() { return target.get(); }
	public ObjectProperty<Transformable> targetProperty() { return target; }
	public void setTarget(Transformable target) { this.target.set(target); }

	/**
	 * @return the current content node if set; null if unset
	 * <b>NOTE</b>: Targets and content nodes are mutually exclusive, if one is set, the other one
	 * is automatically set to null
	 */
	public Node getContent() { return content.get(); }
	public ObjectProperty<Node> contentProperty() { return content; }
	public void setContent(Node content) { this.content.set(content); }

	/**
	 * @return which scrollbar policy the vertical scrollbar uses
	 */
	public ScrollBarPolicy getVbarPolicy() { return vbarPolicy.get(); }
	public ObjectProperty<ScrollBarPolicy> vbarPolicyProperty() { return vbarPolicy; }
	public void setVbarPolicy(ScrollBarPolicy policy) { this.vbarPolicy.set(policy); }


	/**
	 * @return which scrollbar policy the horizontal scrollbar uses
	 */
	public ScrollBarPolicy getHbarPolicy() { return hbarPolicy.get(); }
	public ObjectProperty<ScrollBarPolicy> hbarPolicyProperty() { return hbarPolicy; }
	public void setHbarPolicy(ScrollBarPolicy policy) { this.hbarPolicy.set(policy); }

	/**
	 * Sets the same scrollbar policy for both vbar and hbar
	 */
	public void setScrollBarPolicy(ScrollBarPolicy policy) {
		setHbarPolicy(policy);
		setVbarPolicy(policy);
	}

	/**
	 * @return whether there is an on-going animation or gesture
	 */
	public boolean isChanging() { return changing.get(); }
	public ReadOnlyBooleanProperty changingProperty() { return changing; }

	/**
	 * @return whether gesture inputs are enabled; this only affects inputs and does not lock the
	 * affine transform itself
	 */
	public boolean isGestureEnabled() { return gestureEnabled.get(); }
	public BooleanProperty gestureEnabledProperty() { return gestureEnabled; }
	public void setGestureEnabled(boolean enable) { this.gestureEnabled.set(enable); }

	/**
	 * @return whether the content node (if set) will be clipped with {@link Node#setClip(Node)}
	 * using a viewport-sized rectangle
	 */
	public boolean isClipEnabled() { return clipEnabled.get(); }
	public BooleanProperty clipEnabledProperty() { return clipEnabled; }
	public void setClipEnabled(boolean enable) { this.clipEnabled.set(enable); }


	public boolean isFitWidth() { return fitWidth.get(); }
	public BooleanProperty fitWidthProperty() { return fitWidth; }
	public void setFitWidth(boolean fitWidth) { this.fitWidth.set(fitWidth); }

	public boolean isFitHeight() { return fitHeight.get(); }
	public BooleanProperty fitHeightProperty() { return fitHeight; }
	public void setFitHeight(boolean fitHeight) { this.fitHeight.set(fitHeight); }

	/**
	 * @return the current {@link FitMode}
	 */
	public FitMode getFitMode() { return fitMode.get(); }
	public ObjectProperty<FitMode> fitModeProperty() { return fitMode; }
	public void setFitMode(FitMode mode) { this.fitMode.set(mode); }

	/**
	 * @return the current {@link ScrollMode}
	 */
	public ScrollMode getScrollMode() { return scrollMode.get(); }
	public ObjectProperty<ScrollMode> scrollModeProperty() { return scrollMode; }
	public void setScrollMode(ScrollMode mode) { this.scrollMode.set(mode); }


	public boolean isInvertScrollTranslate() { return invertScrollTranslate.get(); }
	public BooleanProperty invertScrollTranslateProperty() { return invertScrollTranslate; }
	public void setInvertScrollTranslate(boolean invertScrollTranslate) { this.invertScrollTranslate.set(invertScrollTranslate); }

	public boolean isLockScaleX() { return lockScaleX.get(); }
	public BooleanProperty lockScaleXProperty() { return lockScaleX; }
	public void setLockScaleX(boolean lockScaleX) { this.lockScaleX.set(lockScaleX); }

	public boolean isLockScaleY() { return lockScaleY.get(); }
	public BooleanProperty lockScaleYProperty() { return lockScaleY; }
	public void setLockScaleY(boolean lockScaleY) { this.lockScaleY.set(lockScaleY); }


	/**
	 * @return the current x axis scale (x == y iff bindScale is true); defaults to 1.0 initially unless the selected {@link FitMode}
	 * disallows this
	 */
	public double getCurrentScale() { return scaleX.get(); }
	public DoubleProperty currentScaleProperty() { return scaleX; }

	public boolean isBindScale() { return bindScale.get(); }
	public BooleanProperty bindScaleProperty() { return bindScale; }
	public void setBindScale(boolean bindScale) { this.bindScale.set(bindScale); }

	/**
	 * @return the current minimum scale; defaults to {@link GesturePane#DEFAULT_MIN_SCALE}
	 */
	public double getMinScale() { return minScale.get(); }
	public DoubleProperty minScaleProperty() { return minScale; }
	public void setMinScale(double scale) { this.minScale.set(scale); }

	/**
	 * @return the current maximum scale; defaults to {@link GesturePane#DEFAULT_MAX_SCALE}
	 */
	public double getMaxScale() { return maxScale.get(); }
	public DoubleProperty maxScaleProperty() { return maxScale; }
	public void setMaxScale(double scale) { this.maxScale.set(scale); }

	/**
	 * @return the current x axis scale; defaults to 1.0 initially unless the selected {@link FitMode}
	 * disallows this
	 */
	public double getCurrentScaleX() { return scaleX.get(); }
	public DoubleProperty currentScaleXProperty() { return scaleX; }


	/**
	 * @return the current y axis scale; defaults to 1.0 initially unless the selected {@link FitMode}
	 * disallows this
	 */
	public double getCurrentScaleY() { return scaleY.get(); }
	public DoubleProperty currentScaleYProperty() { return scaleY; }



	/**
	 * @return the current x axis offset in <b>target coordinates</b>, see
	 * {@link GesturePane#getTargetViewport()}
	 */
	public double getCurrentX() { return affine.getTx() / getCurrentScaleX(); }
	public DoubleBinding currentXProperty() {
		return affine.txProperty().divide(scaleX);
	}

	/**
	 * @return the current y axis offset in <b>target coordinates</b>, see
	 * {@link GesturePane#getTargetViewport()}
	 */
	public double getCurrentY() { return affine.getTy() / getCurrentScaleY(); }
	public DoubleBinding currentYProperty() {
		return affine.tyProperty().divide(scaleY);
	}

	/**
	 * @return the scroll zoom factor controlling the constant factor of how much each scroll
	 * event affects the transformation; defaults to 1
	 */
	public double getScrollZoomFactor() { return scrollZoomFactor.get(); }
	public DoubleProperty scrollZoomFactorProperty() { return scrollZoomFactor; }
	public void setScrollZoomFactor(double factor) { this.scrollZoomFactor.set(factor); }

	/**
	 * @return the viewport bounding box in <b>target coordinates</b>
	 */
	public Bounds getTargetViewport() { return targetRect.get(); }
	public ObjectProperty<Bounds> targetViewportProperty() { return targetRect; }

	/**
	 * @return a <b>copy</b> of the current affine transformation
	 */
	public Affine getAffine() { return new Affine(affine); }

	@Override
	public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
		if (attribute == AccessibleAttribute.CONTENTS) return getContent();
		return super.queryAccessibleAttribute(attribute, parameters);
	}

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
		 * Node will not be scaled but constrained to the center of the viewport
		 */
		CENTER,
		/**
		 * Node will not be scaled nor constrained; this also disables both vertical and
		 * horizontal scrollbar if enabled
		 */
		UNBOUNDED
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

	public enum ScrollBarPolicy {
		/**
		 * No scrollbar
		 */
		NEVER,
		/**
		 * Always enable scrollbar
		 */
		ALWAYS,
		/**
		 * Show scrollbar when viewport is panning
		 */
		AS_NEEDED;
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
		 * @return the target height, must not be negative
		 */
		double height();
		/**
		 * Sets the transformation for the target, will only happen once
		 *
		 * @param affine the transformation; never null
		 */
		default void setTransform(Affine affine) {}
	}

}