package net.kurobako.gesturefx;

import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WritableValue;
import javafx.collections.ObservableList;
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
import javafx.scene.transform.Translate;
import javafx.util.Duration;

/**
 * Pane that transforms children when a gesture is applied
 */
@SuppressWarnings("unused")
public class GesturePane extends Region {

	private final ChangeListener<Bounds> boundChangeListener;
	private Node target;


	private final BooleanProperty gesture = new SimpleBooleanProperty(true);
	private final ScrollBar horizontal;
	private final ScrollBar vertical;

	public FitMode getFitMode() {
		return fitMode.get();
	}

	public ObjectProperty<FitMode> fitModeProperty() {
		return fitMode;
	}

	private final ObjectProperty<FitMode> fitMode =
			new SimpleObjectProperty<>(FitMode.COVER);
	private final ObjectProperty<ScrollMode> scrollMode =
			new SimpleObjectProperty<>(ScrollMode.ZOOM);

	private Affine affine = new Affine();

	private final SimpleDoubleProperty minScale = new SimpleDoubleProperty(0.55);
	private final SimpleDoubleProperty maxScale = new SimpleDoubleProperty(10);
	private final SimpleDoubleProperty currentScale = new SimpleDoubleProperty(1);


	private Point2D lastPosition;


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

	public GesturePane(Node target, ScrollMode mode) {
		getChildren().add(target);
		setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		setMinSize(0, 0);
		this.target = target;
		this.scrollMode.setValue(mode);
		boundChangeListener = this::invalidateMinScale;
		setFocusTraversable(true);


		setDepthTest(DepthTest.DISABLE);
		cacheEnabled(true);
		setCacheHint(CacheHint.SPEED);
		setCacheShape(true);

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

		gesture.addListener(o -> setupGestures());
		setupGestures();


		target.getTransforms().add(affine);


		horizontal = new ScrollBar();
		horizontal.setOrientation(Orientation.HORIZONTAL);
		horizontal.prefWidthProperty().bind(widthProperty());

		vertical = new ScrollBar();
		vertical.prefHeightProperty().bind(heightProperty());
		vertical.setOrientation(Orientation.VERTICAL);

		getChildren().add(horizontal);
		getChildren().add(vertical);


		//TODO finish me
//		vertical.setMin(0);
//		horizontal.setMin(0);
//		vertical.setMax(512);
//		horizontal.setMax(512);

		vertical.valueProperty().bindBidirectional(affine.txProperty());
		horizontal.valueProperty().bindBidirectional(affine.tyProperty());

	}


	private void setupGestures() {

		boolean disabled = !gesture.get();


		setOnMousePressed(disabled ? null : e -> {
			lastPosition = new Point2D(e.getX(), e.getY());
			cacheEnabled(true);
			e.consume();
		});

		setOnMouseDragged(disabled ? null : e -> {
			translate(e.getX() - lastPosition.getX(), e.getY() - lastPosition.getY());
			lastPosition = new Point2D(e.getX(), e.getY());
			e.consume();
		});

		setOnMouseReleased(disabled ? null : e -> {
			cacheEnabled(false);
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
			cacheEnabled(true);
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


	@Override
	protected void layoutChildren() {
		super.layoutChildren();
//		layoutInArea(target, 0, 0 ,getWidth(), getHeight(), 0, HPos.LEFT, VPos.TOP);
		clampAtBound(false);


		layoutInArea(vertical, 0, 0,
				getWidth(),
				getHeight() - horizontal.getHeight(),
				0, HPos.RIGHT, VPos.CENTER);
		layoutInArea(horizontal, 0, 0,
				getWidth() - vertical.getWidth(),
				getHeight(),
				0, HPos.CENTER, VPos.BOTTOM);

//		double oX = -(getWidth() - target.getLayoutBounds().getWidth())/2;
//		double oY = -(getHeight() - target.getLayoutBounds().getHeight())/2;

//		target.setTranslateX(oX);
//		target.setTranslateY(oY);
//		target.setLayoutX(target.getLayoutX() + oX);
//		target.setLayoutY(target.getLayoutY() + oY);


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

//		System.out.println("Bound delta");
//		clampAtBound();
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


//		if (width < scaledWidth && height < scaledHeight) {
		if (affine.getTx() < maxX) affine.setTx(maxX);
		if (affine.getTy() < maxY) affine.setTy(maxY);
		if (affine.getTy() > 0) affine.setTy(0);
		if (affine.getTx() > 0) affine.setTx(0);
//		}


		if (width < scaledWidth && height < scaledHeight) return;
		System.out.println(fitMode);

		switch (fitMode.get()) {
			case COVER:
				if (width > scaledWidth || height > scaledHeight) {
					double scale = Math.max(width / targetWidth, height / targetHeight);
					affine.setTx((width - scale * targetWidth) / 2);
					affine.setTy((height - scale * targetHeight) / 2);
					affine.setMxx(scale);
					affine.setMyy(scale);
					System.out.println("!");
				}
				break;
			case FIT:


				// if actual size < pane
				if (width > scaledWidth || height > scaledHeight) {
					double scale = Math.min(width / targetWidth, height / targetHeight);
//					if(!zoomPositive) {
					affine.setMxx(scale);
					affine.setMyy(scale);

//					}

					affine.setTx((width - affine.getMxx() * targetWidth) / 2);
					affine.setTy((height - affine.getMyy() * targetHeight) / 2);
					System.out.println("Snap");
				}
				break;
			case CENTER:
				affine.setTx((width - affine.getMxx() * targetWidth) / 2);
				affine.setTy((height - affine.getMyy() * targetHeight) / 2);
				break;
		}


//		if (width > scaledWidth) affine.setMxx(width / targetWidth);
//		if (height > scaledHeight) affine.setMyy(height / targetHeight);

	}

	private void scale(double factor, Point2D origin) {
		System.out.println(factor);
		affine.appendScale(factor, factor, origin);
		clampAtBound(factor >= 1);
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