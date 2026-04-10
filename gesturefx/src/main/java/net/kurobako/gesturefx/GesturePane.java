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
 * A pane that applies pan and zoom transformations to its content in response to mouse,
 * trackpad, and touch gestures.
 * <p>
 * Two content models are supported:
 * <ul>
 *   <li><b>Node content</b> - set via {@link #setContent(Node)}; the node is added as a child
 *       of the pane and transformed via a JavaFX {@link Affine} transform.</li>
 *   <li><b>Transformable target</b> - set via {@link #setTarget(Transformable)}; the pane
 *       delegates transformation to a custom {@link Transformable} implementation, suitable for
 *       canvas or OpenGL surfaces that cannot be wrapped in a scene graph node.</li>
 * </ul>
 * Content and target are mutually exclusive: setting one clears the other.
 * <p>
 * The pane operates in two coordinate spaces:
 * <ul>
 *   <li><em>Viewport coordinates</em> - the pixel space of the pane itself.</li>
 *   <li><em>Target coordinates</em> - the untransformed coordinate space of the content.</li>
 * </ul>
 * Methods that accept or return points are documented with which space they use.
 * <p>
 * To listen for transformation changes, register a handler for {@link AffineEvent}:
 * <pre>{@code
 * GesturePane pane = ...;
 * pane.addEventHandler(AffineEvent.CHANGED, e -> { ... });
 * }</pre>
 * <p>
 * The pane's preferred size defaults to the target's dimensions. Use
 * {@link Region#setPrefSize(double, double)} or the {@code fitWidth}/{@code fitHeight}
 * properties to override this.
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
	 * Creates a new {@link GesturePane} backed by the given {@link Transformable}.
	 *
	 * @param target the transformable to apply transforms to; must not be null
	 */
	@SuppressWarnings("this-escape")
	public GesturePane(Transformable target) {
		this();
		setTarget(target);
	}

	/**
	 * Creates a new {@link GesturePane} with the given node as its content.
	 * The node is added as a child of the pane and transformed directly.
	 *
	 * @param target the node to apply transforms to; must not be null
	 */
	@SuppressWarnings("this-escape")
	public GesturePane(Node target) {
		this();
		setContent(target);
	}

	/**
	 * Creates a new {@link GesturePane} with no content.
	 * Content or a target can be set later via {@link #setContent(Node)} or
	 * {@link #setTarget(Transformable)}.
	 */
	@SuppressWarnings("this-escape")
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
			runLaterOrNowIfOnFXThread(() -> {
				content.set(null);
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
				runLaterOrNowIfOnFXThread(() -> {
					p.layoutBoundsProperty().removeListener(layoutBoundsListener);
					getChildren().remove(p);
				});
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

	/**
	 * Returns the centre point of the viewport in viewport coordinates.
	 *
	 * @return a new point at {@code (viewportWidth / 2, viewportHeight / 2)}
	 */
	public Point2D viewportCentre() {
		return new Point2D(getViewportWidth() / 2, getViewportHeight() / 2);
	}

	/**
	 * Returns the point on the target that is currently visible at the centre of the viewport.
	 *
	 * @return the target-coordinate point aligned with the viewport centre
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
	 * Returns the target-coordinate point corresponding to the given viewport point.
	 *
	 * @param viewportPoint a point in viewport coordinates
	 * @return the corresponding target-coordinate point, or empty if {@code viewportPoint}
	 *         lies outside the bounds returned by {@link #getViewportBound()}
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
	 * Returns the viewport-coordinate point corresponding to the given target point.
	 *
	 * @param targetPoint a point in target coordinates
	 * @return the corresponding point in viewport coordinates
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

	/**
	 * First step in the animation builder chain returned by {@link #animate(Duration)}.
	 * Allows setting the interpolator before configuring callbacks and the operation.
	 */
	public interface AnimationInterpolatorBuilder extends AnimationStartBuilder {
		/**
		 * Sets the interpolator for the animation. If not called, {@link Interpolator#LINEAR}
		 * is used.
		 *
		 * @param interpolator the interpolator; must not be null
		 * @return the next builder step
		 */
		AnimationStartBuilder interpolateWith(Interpolator interpolator);
	}

	/**
	 * Second step in the animation builder chain. Allows registering a pre-start callback.
	 */
	public interface AnimationStartBuilder extends AnimationEndBuilder {
		/**
		 * Registers an action to run on the FX thread immediately before the animation begins.
		 *
		 * @param action the callback; must not be null
		 * @return the next builder step
		 */
		AnimationEndBuilder beforeStart(Runnable action);
	}

	/**
	 * Third step in the animation builder chain. Allows registering a post-finish callback,
	 * then exposes the full {@link GesturePaneOps} API to specify the animated operation.
	 */
	public interface AnimationEndBuilder extends GesturePaneOps {
		/**
		 * Registers an action to run on the FX thread after the animation completes.
		 *
		 * @param action the callback; must not be null
		 * @return the ops interface used to specify the operation to animate
		 */
		GesturePaneOps afterFinished(Runnable action);
	}

	/**
	 * Resets the zoom to {@link #getMinScale()}, pivoting at the current viewport centre.
	 */
	public final void reset() {
		zoomTo(getMinScale(), targetPointAtViewportCentre());
	}

	/**
	 * Resets the scale to {@link #getMinScale()} and re-centres the content according to the
	 * current {@link FitMode}.
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

			double deltaX = lockScaleX.get() ? 1 : factorX;
			double deltaY = lockScaleY.get() ? 1 : factorY;

			double scaleX = getCurrentScaleX() * deltaX;
			double scaleY = getCurrentScaleY() * deltaY;
			// clamp at min and max
			if (scaleX > getMaxScale()) deltaX = getMaxScale() / getCurrentScaleX();
			if (scaleX < getMinScale()) deltaX = getMinScale() / getCurrentScaleX();

			if (scaleY > getMaxScale()) deltaY = getMaxScale() / getCurrentScaleY();
			if (scaleY < getMinScale()) deltaY = getMinScale() / getCurrentScaleY();

			affine.prependScale(deltaX, deltaY, origin);

			clampAtBound(factorX >= 1 || factorY >= 1);
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
		boolean validTarget = targetWidth > 0 && targetHeight > 0;
		double coverScale = validTarget ? Math.max(width / targetWidth, height / targetHeight) : 1;
		double fitScale   = validTarget ? Math.min(width / targetWidth, height / targetHeight) : 0;
		switch (fitMode.get()) {
			case COVER:
				if (width >= scaledWidth || height >= scaledHeight)
					tsX = tsY = coverScale;
				break;
			case COVER_FILL:
				tsX = tsY = coverScale;
				break;
			case FIT:
				if (!zoomPositive && scaleX <= fitScale) {
					tx = (width - fitScale * targetWidth) / 2;
					ty = (height - fitScale * targetHeight) / 2;
					tsX = tsY = fitScale;
				}
				break;
			case FIT_FILL:
				tx = (width - fitScale * targetWidth) / 2;
				ty = (height - fitScale * targetHeight) / 2;
				tsX = tsY = fitScale;
				break;
			default:
				break;
		}

		// If the scale changed (e.g. COVER/COVER_FILL on viewport resize), the translation was
		// clamped against the old scale above and must be re-clamped against the new scale.
		if (tsX != scaleX || tsY != scaleY) {
			double newScaledWidth  = tsX * targetWidth;
			double newScaledHeight = tsY * targetHeight;
			tx = clamp(width - newScaledWidth, 0, tx);
			ty = clamp(height - newScaledHeight, 0, ty);
			if (width  >= newScaledWidth)  tx = (width  - newScaledWidth)  / 2;
			if (height >= newScaledHeight) ty = (height - newScaledHeight) / 2;
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
		clampExternalScale = false;
		try {
			e.run();
		} finally {
			clampExternalScale = true;
			inhibitPropEvent = false;
		}
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

	/** Returns the current viewport width in pixels, excluding scrollbar insets. */
	public double getViewportWidth() { return viewport.get().getWidth(); }
	/** Returns the current viewport height in pixels, excluding scrollbar insets. */
	public double getViewportHeight() { return viewport.get().getHeight(); }
	/** Returns the current viewport bounds in viewport coordinates. */
	public Bounds getViewportBound() { return viewport.get(); }
	/** Read-only property for the viewport bounds. */
	public ReadOnlyObjectProperty<Bounds> viewportBoundProperty() { return viewport; }

	/**
	 * Returns the current {@link Transformable} target, or {@code null} if none is set.
	 * Target and content are mutually exclusive; setting one clears the other.
	 */
	public Transformable getTarget() { return target.get(); }
	public ObjectProperty<Transformable> targetProperty() { return target; }
	public void setTarget(Transformable target) { this.target.set(target); }

	/**
	 * Returns the current content node, or {@code null} if none is set.
	 * Content and target are mutually exclusive; setting one clears the other.
	 */
	public Node getContent() { return content.get(); }
	public ObjectProperty<Node> contentProperty() { return content; }
	public void setContent(Node content) { this.content.set(content); }

	/**
	 * Returns the {@link ScrollBarPolicy} applied to the vertical scrollbar.
	 */
	public ScrollBarPolicy getVbarPolicy() { return vbarPolicy.get(); }
	public ObjectProperty<ScrollBarPolicy> vbarPolicyProperty() { return vbarPolicy; }
	public void setVbarPolicy(ScrollBarPolicy policy) { this.vbarPolicy.set(policy); }

	/**
	 * Returns the {@link ScrollBarPolicy} applied to the horizontal scrollbar.
	 */
	public ScrollBarPolicy getHbarPolicy() { return hbarPolicy.get(); }
	public ObjectProperty<ScrollBarPolicy> hbarPolicyProperty() { return hbarPolicy; }
	public void setHbarPolicy(ScrollBarPolicy policy) { this.hbarPolicy.set(policy); }

	/**
	 * Convenience method to set the same {@link ScrollBarPolicy} on both scrollbars at once.
	 *
	 * @param policy the policy to apply to both the horizontal and vertical scrollbars
	 */
	public void setScrollBarPolicy(ScrollBarPolicy policy) {
		setHbarPolicy(policy);
		setVbarPolicy(policy);
	}

	/**
	 * Returns {@code true} if a gesture or programmatic animation is currently in progress.
	 */
	public boolean isChanging() { return changing.get(); }
	public ReadOnlyBooleanProperty changingProperty() { return changing; }

	/**
	 * Returns whether gesture inputs (mouse drag, scroll, touch) are enabled.
	 * Disabling this does not lock the affine transform; programmatic changes still work.
	 */
	public boolean isGestureEnabled() { return gestureEnabled.get(); }
	public BooleanProperty gestureEnabledProperty() { return gestureEnabled; }
	public void setGestureEnabled(boolean enable) { this.gestureEnabled.set(enable); }

	/**
	 * Returns whether the content node is clipped to the viewport bounds.
	 * When enabled, a viewport-sized rectangle clip is applied via {@link Node#setClip(Node)}.
	 */
	public boolean isClipEnabled() { return clipEnabled.get(); }
	public BooleanProperty clipEnabledProperty() { return clipEnabled; }
	public void setClipEnabled(boolean enable) { this.clipEnabled.set(enable); }

	/**
	 * Returns whether the pane's preferred width tracks the target's width.
	 * When {@code true}, the measured preferred width equals the target width.
	 */
	public boolean isFitWidth() { return fitWidth.get(); }
	public BooleanProperty fitWidthProperty() { return fitWidth; }
	public void setFitWidth(boolean fitWidth) { this.fitWidth.set(fitWidth); }

	/**
	 * Returns whether the pane's preferred height tracks the target's height.
	 * When {@code true}, the measured preferred height equals the target height.
	 */
	public boolean isFitHeight() { return fitHeight.get(); }
	public BooleanProperty fitHeightProperty() { return fitHeight; }
	public void setFitHeight(boolean fitHeight) { this.fitHeight.set(fitHeight); }

	/**
	 * Returns the current {@link FitMode}, which controls how content is scaled relative to
	 * the viewport. Defaults to {@link FitMode#FIT}.
	 */
	public FitMode getFitMode() { return fitMode.get(); }
	public ObjectProperty<FitMode> fitModeProperty() { return fitMode; }
	public void setFitMode(FitMode mode) { this.fitMode.set(mode); }

	/**
	 * Returns the current {@link ScrollMode}, which controls how scroll events are interpreted.
	 * Defaults to {@link ScrollMode#PAN}.
	 */
	public ScrollMode getScrollMode() { return scrollMode.get(); }
	public ObjectProperty<ScrollMode> scrollModeProperty() { return scrollMode; }
	public void setScrollMode(ScrollMode mode) { this.scrollMode.set(mode); }

	/**
	 * Returns whether scroll translation direction is inverted.
	 * When {@code true}, scrolling up moves content down (natural/trackpad-style scrolling).
	 */
	public boolean isInvertScrollTranslate() { return invertScrollTranslate.get(); }
	public BooleanProperty invertScrollTranslateProperty() { return invertScrollTranslate; }
	public void setInvertScrollTranslate(boolean invertScrollTranslate) { this.invertScrollTranslate.set(invertScrollTranslate); }

	/**
	 * Returns whether scaling on the x-axis is locked.
	 * When locked, zoom gestures and programmatic zoom have no effect on the x-axis scale.
	 */
	public boolean isLockScaleX() { return lockScaleX.get(); }
	public BooleanProperty lockScaleXProperty() { return lockScaleX; }
	public void setLockScaleX(boolean lockScaleX) { this.lockScaleX.set(lockScaleX); }

	/**
	 * Returns whether scaling on the y-axis is locked.
	 * When locked, zoom gestures and programmatic zoom have no effect on the y-axis scale.
	 */
	public boolean isLockScaleY() { return lockScaleY.get(); }
	public BooleanProperty lockScaleYProperty() { return lockScaleY; }
	public void setLockScaleY(boolean lockScaleY) { this.lockScaleY.set(lockScaleY); }

	/**
	 * Returns the current x-axis scale. When {@link #isBindScale()} is {@code true} the x and
	 * y scales are kept equal, so this is equivalent to {@link #getCurrentScaleX()}.
	 */
	public double getCurrentScale() { return scaleX.get(); }
	public DoubleProperty currentScaleProperty() { return scaleX; }

	/**
	 * Returns whether the x and y scales are bound together.
	 * When {@code true}, any change to one axis scale is immediately applied to the other.
	 */
	public boolean isBindScale() { return bindScale.get(); }
	public BooleanProperty bindScaleProperty() { return bindScale; }
	public void setBindScale(boolean bindScale) { this.bindScale.set(bindScale); }

	/**
	 * Returns the minimum allowed scale. Defaults to {@link #DEFAULT_MIN_SCALE}.
	 * The active {@link FitMode} may enforce a higher effective minimum.
	 */
	public double getMinScale() { return minScale.get(); }
	public DoubleProperty minScaleProperty() { return minScale; }
	public void setMinScale(double scale) { this.minScale.set(scale); }

	/**
	 * Returns the maximum allowed scale. Defaults to {@link #DEFAULT_MAX_SCALE}.
	 */
	public double getMaxScale() { return maxScale.get(); }
	public DoubleProperty maxScaleProperty() { return maxScale; }
	public void setMaxScale(double scale) { this.maxScale.set(scale); }

	/**
	 * Returns the current x-axis scale factor. The initial value is 1.0 unless the active
	 * {@link FitMode} computes a different scale on first layout.
	 */
	public double getCurrentScaleX() { return scaleX.get(); }
	public DoubleProperty currentScaleXProperty() { return scaleX; }

	/**
	 * Returns the current y-axis scale factor. The initial value is 1.0 unless the active
	 * {@link FitMode} computes a different scale on first layout.
	 */
	public double getCurrentScaleY() { return scaleY.get(); }
	public DoubleProperty currentScaleYProperty() { return scaleY; }



	/**
	 * Returns the current x-axis pan offset in target coordinates.
	 *
	 * @see #getTargetViewport()
	 */
	public double getCurrentX() { return affine.getTx() / getCurrentScaleX(); }
	public DoubleBinding currentXProperty() {
		return affine.txProperty().divide(scaleX);
	}

	/**
	 * Returns the current y-axis pan offset in target coordinates.
	 *
	 * @see #getTargetViewport()
	 */
	public double getCurrentY() { return affine.getTy() / getCurrentScaleY(); }
	public DoubleBinding currentYProperty() {
		return affine.tyProperty().divide(scaleY);
	}

	/**
	 * Returns the scroll zoom factor: a multiplier applied to each scroll zoom event.
	 * Higher values produce larger zoom steps per scroll tick. Defaults to
	 * {@value #DEFAULT_ZOOM_FACTOR}.
	 */
	public double getScrollZoomFactor() { return scrollZoomFactor.get(); }
	public DoubleProperty scrollZoomFactorProperty() { return scrollZoomFactor; }
	public void setScrollZoomFactor(double factor) { this.scrollZoomFactor.set(factor); }

	/**
	 * Returns the currently visible area of the target as a bounding box in target coordinates.
	 * This is the inverse-transformed viewport: the region of the target currently on screen.
	 */
	public Bounds getTargetViewport() { return targetRect.get(); }
	public ObjectProperty<Bounds> targetViewportProperty() { return targetRect; }

	/**
	 * Returns a snapshot copy of the current affine transformation.
	 * Modifying the returned object has no effect on the pane.
	 */
	public Affine getAffine() { return new Affine(affine); }

	@Override
	public Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
		if (attribute == AccessibleAttribute.CONTENTS) return getContent();
		return super.queryAccessibleAttribute(attribute, parameters);
	}

	/**
	 * Controls how the content is scaled relative to the viewport.
	 * <p>
	 * Modes ending in {@code _FILL} track the viewport size in both directions - if the viewport
	 * shrinks, the content shrinks with it. The plain variants only enforce a minimum scale:
	 * the user can zoom in freely, and the scale is not adjusted when the viewport shrinks.
	 */
	public enum FitMode {
		/**
		 * Content is scaled to the smallest size that completely covers the viewport
		 * (analogous to CSS {@code object-fit: cover}).
		 * The user may zoom in further, but the scale will not drop below the cover scale.
		 * If the viewport grows such that the current scale no longer covers, the scale is
		 * increased to compensate; shrinking the viewport does not reduce the scale.
		 */
		COVER,
		/**
		 * Content is always scaled to exactly cover the viewport, tracking resize in both
		 * directions. The scale is recomputed whenever the viewport changes size, so the
		 * content always fills the pane with no letterboxing. User zoom gestures are ignored.
		 */
		COVER_FILL,
		/**
		 * Content is scaled to the largest size that fits entirely within the viewport
		 * (analogous to CSS {@code object-fit: contain}).
		 * The user may zoom in further, but the scale will not drop below the fit scale.
		 * If the viewport grows, the scale is increased so the content continues to fit;
		 * shrinking the viewport does not reduce the scale.
		 */
		FIT,
		/**
		 * Content is always scaled to exactly fit within the viewport, tracking resize in both
		 * directions. The scale is recomputed whenever the viewport changes size, so the content
		 * always fills the pane edge-to-edge with no overflow. User zoom gestures are ignored.
		 * Analogous to CSS {@code object-fit: contain}.
		 */
		FIT_FILL,
		/**
		 * Content is not scaled. It is translated so that it remains centred within the
		 * viewport, but the user cannot pan it outside the viewport bounds.
		 */
		CENTER,
		/**
		 * Content is neither scaled nor constrained. The user can pan and zoom freely,
		 * including outside the viewport bounds. Scrollbars are also disabled in this mode.
		 */
		UNBOUNDED
	}

	/**
	 * Controls how scroll wheel (or trackpad scroll) events are interpreted.
	 */
	public enum ScrollMode {
		/**
		 * Scroll events zoom the content. The zoom pivot is the cursor position.
		 */
		ZOOM,
		/**
		 * Scroll events pan the content. Horizontal and vertical scroll axes are both supported.
		 */
		PAN
	}

	/**
	 * Controls when the scrollbars are shown.
	 */
	public enum ScrollBarPolicy {
		/**
		 * Scrollbars are never shown.
		 */
		NEVER,
		/**
		 * Scrollbars are always shown.
		 */
		ALWAYS,
		/**
		 * Scrollbars are shown only while the content is being panned, then fade out.
		 */
		AS_NEEDED;
	}

	/**
	 * Abstraction for content that is not a JavaFX {@link Node} but still needs to be
	 * panned and zoomed by a {@link GesturePane}.
	 * <p>
	 * Implement this interface to allow the pane to transform a custom surface such as a
	 * {@link javafx.scene.canvas.Canvas} or an OpenGL viewport. The pane calls
	 * {@link #setTransform(Affine)} once on attachment to hand over the live affine object;
	 * the implementation is responsible for applying it on every render pass.
	 */
	public interface Transformable {
		/**
		 * Returns the intrinsic width of the target in target coordinates. Must not be negative.
		 */
		double width();

		/**
		 * Returns the intrinsic height of the target in target coordinates. Must not be negative.
		 */
		double height();

		/**
		 * Called once by the pane to hand over the live {@link Affine} transform object.
		 * The implementation should apply this transform on every render pass.
		 *
		 * @param affine the transform to apply; never null
		 */
		default void setTransform(Affine affine) {}
	}

}