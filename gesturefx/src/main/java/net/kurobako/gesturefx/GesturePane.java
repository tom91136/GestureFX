package net.kurobako.gesturefx;

import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.DepthTest;
import javafx.scene.Node;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.util.Duration;

/**
 * Pane that transforms children when a gesture is applied
 */
@SuppressWarnings("unused")
public class GesturePane extends Region {

	private final ChangeListener<Bounds> boundChangeListener;
	private Node target;
	private final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(ScrollMode
			                                                                                 .ZOOM);

	private Affine affine = new Affine();

	private final SimpleDoubleProperty minScale = new SimpleDoubleProperty(0.51);
	private final SimpleDoubleProperty maxScale = new SimpleDoubleProperty(4.5);
	private final SimpleDoubleProperty currentScale = new SimpleDoubleProperty(1);

	// private double minScale = 0.51;
	// private double maxScale = 4.5;
	// private double currentScale = 1f;

	private Point2D lastPosition;

	public enum ScrollMode {
		ZOOM, PAN
	}

	public GesturePane(Node target, ScrollMode mode) {
		getChildren().add(target);
		this.target = target;
		this.scrollMode.setValue(mode);
		boundChangeListener = this::invalidateMinScale;
		setFocusTraversable(true);
		setOnMousePressed(e -> {
			lastPosition = new Point2D(e.getX(), e.getY());
			cacheEnabled(true);
			e.consume();
		});

		setOnMouseDragged(e -> {
			translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
			lastPosition = new Point2D(e.getX(), e.getY());
			e.consume();
		});

		setOnMouseReleased(e -> {
			cacheEnabled(false);
		});

//		setOnMouseClicked(e -> {
//			if (e.getClickCount() == 2) {
//				currentScale.set(clamp(minScale.get(), maxScale.get(), currentScale.get() + 1));
//				scale(currentScale.get(), new Point2D(e.getX(), e.getY()));
//				e.consume();
//			}
//		});

		setOnZoom(e -> {
			scale(e.getZoomFactor(), mapPoint(fromGesture(e)));
		});

		setOnScrollStarted(e -> {
			cacheEnabled(true);
		});
		setOnScroll(e -> {
			switch (scrollMode.get()) {
				case ZOOM:
					double zoomFactor = minScale.divide(maxScale).get();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					double oldScale = currentScale.get();
					currentScale.set(currentScale.add(zoomFactor).get());
					// no delta
					if (currentScale.isEqualTo(oldScale, 0.01).get()) return;
					double delta = 1 + (currentScale.subtract(oldScale).get());
					double scale = affine.getMxx() * delta;
					// out of bound
					if ((scale <= minScale.get() && zoomFactor < 0) ||
							    (scale >= maxScale.get() && zoomFactor > 0)) {
						return;
					}
					scale(delta, mapPoint(fromGesture(e)));
					break;
				case PAN:
					translate(e.getDeltaX(), e.getDeltaY());
					break;
			}
			e.consume();
		});
		setOnScrollFinished(e -> {
			cacheEnabled(false);
		});

		setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				System.out.println("ESC");
				affine.setToIdentity();
				e.consume();
			}
		});

		setDepthTest(DepthTest.ENABLE);
		cacheEnabled(false);

		// clip stuff that goes out of bound
		Rectangle rectangle = new Rectangle();
		rectangle.heightProperty().bind(heightProperty());
		rectangle.widthProperty().bind(widthProperty());
		setClip(rectangle);

		parentProperty().addListener((o, p, n) -> {
			if (p != null) p.layoutBoundsProperty().removeListener(boundChangeListener);
			if (n != null) {
				n.layoutBoundsProperty().addListener(boundChangeListener);
			}
		});

		target.getTransforms().add(affine);
	}

	@Override
	protected void layoutChildren() {
		super.layoutChildren();
		layoutInArea(target, 0, 0, getWidth(), getHeight(), 0, HPos.LEFT, VPos.TOP);
	}

	public void zoomTo(double scale) {
		double mxx = affine.getMxx();
		double s = minScale.multiply(scale).get();
		double step = mxx + (s - mxx);
		affine.setMxx(step);
		affine.setMyy(step);
	}

	public void zoomTo(double scale, Duration duration, Runnable callback) {
		double mxx = affine.getMxx();
		double s = minScale.multiply(scale).get();
		double delta = s - mxx;
		animateValue(0, 1, duration, v -> {
			double step = mxx + (delta * v);
			affine.setMxx(step);
			affine.setMyy(step);
		}, callback);

	}

	public void translateTo(Point2D point2D) {


		Point2D centrePoint = target.parentToLocal(new Point2D(getWidth() / 2, getHeight() / 2));
		// move to centre point and apply scale
		Point2D newPoint = centrePoint.subtract(point2D);
		affine.setTx(affine.getTx() + newPoint.getX() * affine.getMxx());
		affine.setTy(affine.getTy() + newPoint.getY() * affine.getMxx());
	}

	public void translateTo(Point2D point2D, Duration duration, Runnable callback) {
		// get current centre point
		Point2D centrePoint = target.parentToLocal(new Point2D(getWidth() / 2, getHeight() / 2));
		// move to centre point and apply scale
		Point2D newPoint = centrePoint.subtract(point2D);
		double ttx = newPoint.getX() * affine.getMxx();
		double tty = newPoint.getY() * affine.getMxx();
		double tx = affine.getTx();
		double ty = affine.getTy();
		animateValue(0, 1, duration, v -> {
			affine.setTx(tx + ttx * v);
			affine.setTy(ty + tty * v);
		}, callback);
	}

	public Point2D centrePoint() {
		return target.parentToLocal(new Point2D(getWidth() / 2, getHeight() / 2));
	}

	public void zoomTo(double zoom, boolean animate) {
		if (zoom < 1) throw new IllegalArgumentException("Zoom range must >= 1");
		Point2D centrePoint = target.parentToLocal(new Point2D(getWidth() / 2, getHeight() / 2));
		affine.setMxx(minScale.multiply(zoom).get());
		affine.setMyy(minScale.multiply(zoom).get());
	}

	private void invalidateMinScale(ObservableValue<? extends Bounds> observable, Bounds oldValue,
	                                Bounds newValue) {
//		Bounds bounds = target.getLayoutBounds();
//		minScale.set(Math.min(
//				newValue.getWidth() / bounds.getWidth(),
//				newValue.getHeight() / bounds.getHeight()));
//		if (currentScale.lessThan(minScale).get()) {
//			affine.setMxx(minScale.get());
//			affine.setMyy(minScale.get());
//		}
	}

	public void cover() {
		zoomTo(1, false);
	}

	private Bounds targetBound() {
		return target.getLayoutBounds();
	}

	private Point2D mapPoint(Point2D point) {
		return target.parentToLocal(point);
	}

	private static double clamp(double min, double max, double value) {
		return Math.max(min, Math.min(max, value));
	}

	private static Point2D fromGesture(GestureEvent event) {
		return new Point2D(event.getX(), event.getY());
	}

	private void translate(double x, double y) {
		affine.prependTranslation(x, y);
//		affine.getTx()


		clampAtBound();

	}


	private void clampAtBound() {
		double scaledWidth = affine.getMxx() * target.getLayoutBounds().getWidth();
		double scaledHeight = affine.getMyy() * target.getLayoutBounds().getHeight();
		double maxX = getWidth() - scaledWidth;
		double maxY = getHeight() - scaledHeight;
		if (affine.getTx() < maxX) affine.setTx(maxX);
		if (affine.getTy() < maxY) affine.setTy(maxY);
		if (affine.getTy() > 0) affine.setTy(0);
		if (affine.getTx() > 0) affine.setTx(0);


		if (getWidth() > scaledWidth || getHeight() > scaledHeight) {
			// TODO clamp to min scale
		}

		System.out.println("Aff ->" + affine.toString());
	}

	private void scale(double factor, Point2D origin) {
		affine.appendScale(factor, factor, origin);
		clampAtBound();
	}

	private void cacheEnabled(boolean enable) {
		setCacheHint(enable ? CacheHint.SPEED : CacheHint.QUALITY);
	}

	Timeline timeline = new Timeline();

	private void animateValue(double from,
	                          double to,
	                          Duration duration,
	                          DoubleConsumer consumer,
	                          Runnable callback) {
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
		if (callback != null) timeline.setOnFinished(e -> callback.run());
		timeline.play();

	}

	public ScrollMode getScrollMode() {
		return scrollMode.get();
	}

	public ObjectProperty<ScrollMode> scrollModeProperty() {
		return scrollMode;
	}

}