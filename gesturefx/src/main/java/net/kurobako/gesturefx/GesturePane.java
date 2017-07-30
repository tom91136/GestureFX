package net.kurobako.gesturefx;

import java.util.function.DoubleConsumer;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.WritableValue;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.GestureEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.ZoomEvent;
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
	private final ScrollBar hBar = new ScrollBar();
	private final ScrollBar vBar = new ScrollBar();

	private final BooleanProperty gestureEnabled = new SimpleBooleanProperty(true);
	private final BooleanProperty vBarEnabled = new SimpleBooleanProperty(true);
	private final BooleanProperty hBarEnabled = new SimpleBooleanProperty(true);
	private final ObjectProperty<FitMode> fitMode = new SimpleObjectProperty<>(FIT);
	private final ObjectProperty<ScrollMode> scrollMode = new SimpleObjectProperty<>(PAN);

	private final SimpleDoubleProperty minScale = new SimpleDoubleProperty(0.55);
	private final SimpleDoubleProperty maxScale = new SimpleDoubleProperty(10);

	private final SimpleDoubleProperty scrollZoomFactor = new SimpleDoubleProperty(1);


	private Point2D lastPosition;


	public GesturePane(Node target) {
		this.target = target;
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


		gestureEnabled.addListener(o -> setupGestures());
		setupGestures();

		target.getTransforms().add(affine);

		hBar.setOrientation(Orientation.HORIZONTAL);
		hBar.prefWidthProperty().bind(widthProperty());
		vBar.prefHeightProperty().bind(heightProperty());
		vBar.setOrientation(Orientation.VERTICAL);
		getChildren().addAll(vBar, hBar);





		hBar.visibleProperty().bind(hBar.managedProperty());
		hBar.managedProperty().bind(hBarEnabled);
		vBar.visibleProperty().bind(vBar.managedProperty());
		vBar.managedProperty().bind(vBarEnabled);


		target.layoutBoundsProperty().addListener(o -> {
			vBar.setMin(0);
			hBar.setMin(0);
			vBar.setMax(target.getLayoutBounds().getWidth() * affine.getMxx());
			hBar.setMax(target.getLayoutBounds().getHeight() * affine.getMyy());

			System.out.println(vBar.getMax() +" <> "+ hBar.getMax());

		});




		vBar.setMin(0);
		hBar.setMin(0);


		vBar.maxProperty().bind(affine.mxxProperty().multiply(new DoubleBinding() {
			@Override
			protected double computeValue() { return target.getLayoutBounds().getWidth(); }
		}));

		hBar.maxProperty().bind(affine.myyProperty().multiply(new DoubleBinding() {
			@Override
			protected double computeValue() { return target.getLayoutBounds().getHeight(); }
		}));


		vBar.valueProperty().addListener(o -> {
			affine.setTy(-vBar.getValue());
			System.out.println(vBar.getValue());
		});
		hBar.valueProperty().addListener(o -> affine.setTx(-hBar.getValue()));

//		affine.txProperty().addListener(System.out::println);
//		affine.tyProperty().addListener(System.out::println);

	}


	private void setupGestures() {
		boolean disabled = !gestureEnabled.get();

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


		addEventHandler(ZoomEvent.ZOOM, (ZoomEvent event) -> {
			scale(event.getZoomFactor(), mapPoint(fromGesture(event)));
			event.consume();
		});


		setEventHandler(ScrollEvent.SCROLL, e -> {

			// mouse scroll events only
			if (e.getTouchCount() > 0) return;


			// TODO might be driver and platform specific
			// TODO test on macOS
			// TODO test on Linux
			// TODO test on different Windows versions
			// TODO test on machines with different touchpad vendor

			// pinch to zoom on trackpad
			if (e.isShortcutDown()) {
				// TODO test for different deltaY values, value could be bad
				double zoomFactor = 0.095* getScrollZoomFactor();
				if (e.getDeltaY() < 0) zoomFactor *= -1;
				scale(1 + zoomFactor, mapPoint(fromGesture(e)));
				return;
			}

			switch (scrollMode.get()) {
				case ZOOM:
					double zoomFactor = 0.095 * getScrollZoomFactor();
					if (e.getDeltaY() < 0) zoomFactor *= -1;
					scale(1 + zoomFactor, mapPoint(fromGesture(e)));
					return;
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

	public boolean isVBarEnabled() { return vBarEnabled.get(); }
	public BooleanProperty vBarEnabledProperty() { return vBarEnabled; }
	public boolean isHBarEnabled() { return hBarEnabled.get(); }
	public BooleanProperty hBarEnabledProperty() { return hBarEnabled; }
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

	private static double clamp(double min, double max, double value) {
		return Math.max(min, Math.min(max, value));
	}

	private static Point2D fromGesture(GestureEvent event) {
		return new Point2D(event.getX(), event.getY());
	}

}