package net.kurobako.gesturefx;

import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.DepthTest;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Affine;
import javafx.util.Duration;

import static net.kurobako.gesturefx.GesturePane.FitMode.*;
import static net.kurobako.gesturefx.GesturePane.ScrollMode.*;

/**
 * Pane that transforms children when a gesture is applied
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class GesturePane extends Region {


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


	public enum ScrollMode {
		ZOOM, PAN
	}

	private Node target;


	private final Affine affine = new Affine();
	private final ScrollBar horizontal = new ScrollBar();
	private final ScrollBar vertical = new ScrollBar();

	private final BooleanProperty gesture = new SimpleBooleanProperty(true);
	private final BooleanProperty verticalScrollBarEnabled = new SimpleBooleanProperty(true);
	private final BooleanProperty horizontalScrollBarEnabled = new SimpleBooleanProperty(true);
	private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(COVER);
	private final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(ZOOM);

	private final SimpleDoubleProperty minScale = new SimpleDoubleProperty(0.55);
	private final SimpleDoubleProperty maxScale = new SimpleDoubleProperty(10);
	private final SimpleDoubleProperty currentScale = new SimpleDoubleProperty(1);


	private Point2D lastPosition;


	public GesturePane(Node target, ScrollMode mode) {
		this.target = target;
		this.scrollMode.setValue(mode);
		getChildren().add(target);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setMinSize(0, 0);
		setFocusTraversable(true);
		cache(false);

		// clip stuff that goes out of bound
		Rectangle rectangle = new Rectangle();
		rectangle.heightProperty().bind(heightProperty());
		rectangle.widthProperty().bind(widthProperty());
		setClip(rectangle);


		gesture.addListener(o -> setupGestures());
		setupGestures();

		target.getTransforms().add(affine);

		horizontal.setOrientation(Orientation.HORIZONTAL);
		horizontal.prefWidthProperty().bind(widthProperty());
		vertical.prefHeightProperty().bind(heightProperty());
		vertical.setOrientation(Orientation.VERTICAL);
		getChildren().addAll(vertical, horizontal);


		//TODO finish me
//		vertical.setMin(0);
//		horizontal.setMin(0);
//		vertical.setMax(512);
//		horizontal.setMax(512);

		horizontal.managedProperty().bind(horizontalScrollBarEnabled);
		vertical.managedProperty().bind(verticalScrollBarEnabled);

		vertical.valueProperty().bindBidirectional(affine.txProperty());
		horizontal.valueProperty().bindBidirectional(affine.tyProperty());

	}


	private void setupGestures() {
		boolean disabled = !gesture.get();

		setOnMousePressed(disabled ? null : e -> {
			lastPosition = new Point2D(e.getX(), e.getY());
			cache(true);
			e.consume();
		});

		setOnMouseDragged(disabled ? null : e -> {
			translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
			lastPosition = new Point2D(e.getX(), e.getY());
			e.consume();
		});

		setOnMouseReleased(disabled ? null : e -> {
			cache(false);
		});

//		setOnMouseClicked(e -> {
//			if (e.getClickCount() == 2) {
//				currentScale.set(clamp(minScale.get(), maxScale.get(), currentScale.get() + 1));
//				scale(currentScale.get(), new Point2D(e.getX(), e.getY()));
//				e.consume();
//			}
//		});

		setOnZoom(disabled ? null : e -> {
			scale(e.getZoomFactor(), mapPoint(fromGesture(e)));
		});

		setOnScrollStarted(disabled ? null : e -> {
			cache(true);
		});
		setOnScroll(disabled ? null : e -> {
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
		setOnScrollFinished(disabled ? null : e -> {
//			cacheEnabled(false);
		});

		setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				System.out.println("ESC");
				affine.setToIdentity();
				e.consume();
			}
		});

	}

	private void cache(boolean enable) {
		setCacheHint(enable ? CacheHint.SPEED : CacheHint.QUALITY);
	}


	@Override
	protected void layoutChildren() {
		super.layoutChildren();
		clampAtBound(false);

		if (vertical.isManaged())
			layoutInArea(vertical, 0, 0,
			             getWidth(),
			             getHeight() - horizontal.getHeight(),
			             0, HPos.RIGHT, VPos.CENTER);
		if (horizontal.isManaged())
			layoutInArea(horizontal, 0, 0,
			             getWidth() - vertical.getWidth(),
			             getHeight(),
			             0, HPos.CENTER, VPos.BOTTOM);
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

	public Point2D centrePoint() {
		return target.parentToLocal(new Point2D(getWidth() / 2, getHeight() / 2));
	}

	public void translateTo(Point2D point2D) {


		Point2D centrePoint = centrePoint();
		// move to centre point and apply scale
		Point2D newPoint = centrePoint.subtract(point2D);
		affine.setTx(affine.getTx() + newPoint.getX() * affine.getMxx());
		affine.setTy(affine.getTy() + newPoint.getY() * affine.getMxx());
	}

	public void translateTo(Point2D point2D, Duration duration, Runnable callback) {
		// get current centre point
		Point2D centrePoint = centrePoint();
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

	public void zoomTo(double zoom, boolean animate) {
		if (zoom < 1) throw new IllegalArgumentException("Zoom range must >= 1");
		Point2D centrePoint = centrePoint();
		affine.setMxx(minScale.multiply(zoom).get());
		affine.setMyy(minScale.multiply(zoom).get());
	}


	// TODO set to FitMode with proper size
	public void reset() {
		zoomTo(1);
	}

	private Point2D mapPoint(Point2D point) {
		return target.parentToLocal(point);
	}


	private void scale(double factor, Point2D origin) {
		affine.appendScale(factor, factor, origin);
		clampAtBound(factor >= 1);
	}

	private void translate(double x, double y) {
		affine.prependTranslation(x, y);
		clampAtBound(true);
	}


	private void clampAtBound(boolean zoomPositive) {
		double targetWidth = target.getLayoutBounds().getWidth();
		double targetHeight = target.getLayoutBounds().getHeight();

		double scaledWidth = affine.getMxx() * targetWidth;
		double scaledHeight = affine.getMyy() * targetHeight;

		double width = getWidth();
		double height = getHeight();

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

	//@formatter:off
	public boolean isVerticalScrollBarEnabled() { return verticalScrollBarEnabled.get(); }
	public BooleanProperty verticalScrollBarEnabledProperty() { return verticalScrollBarEnabled; }

	public boolean isHorizontalScrollBarEnabled() { return horizontalScrollBarEnabled.get(); }
	public BooleanProperty horizontalScrollBarEnabledProperty() { return horizontalScrollBarEnabled; }
	//@formatter:on

	public boolean isGesture() { return gesture.get(); }
	public BooleanProperty gestureProperty() { return gesture; }

	public double getMinScale() { return minScale.get(); }
	public DoubleProperty minScaleProperty() { return minScale; }

	public double getMaxScale() { return maxScale.get(); }
	public DoubleProperty maxScaleProperty() { return maxScale; }

	public double getCurrentScale() { return currentScale.get(); }
	public ReadOnlyDoubleProperty currentScaleProperty() { return currentScale; }

	public ScrollMode getScrollMode() { return scrollMode.get(); }
	public ObjectProperty<ScrollMode> scrollModeProperty() { return scrollMode; }

	public FitMode getFitMode() { return fitMode.get(); }
	public ObjectProperty<FitMode> fitModeProperty() { return fitMode; }


	private static double clamp(double min, double max, double value) {
		return Math.max(min, Math.min(max, value));
	}

	private static Point2D fromGesture(GestureEvent event) {
		return new Point2D(event.getX(), event.getY());
	}

}