package net.kurobako.gesturefx;

import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.transform.Affine;

/**
 * An event that would be fired when the target within a {@link GesturePane} is transformed in
 * anyway
 */
@SuppressWarnings("WeakerAccess")
public final class AffineEvent extends Event {

	private static final long serialVersionUID = 1437008082899813504L;

	/**
	 * Fired when the transformation has just started
	 */
	public static final EventType<AffineEvent> CHANGE_STARTED =
			new EventType<>(Event.ANY, "CHANGE_STARTED");
	/**
	 * Fired when the transformation in progress produced a change(not necessarily visible)
	 */
	public static final EventType<AffineEvent> CHANGED =
			new EventType<>(Event.ANY, "CHANGED");
	/**
	 * Fired when the transformation has finished
	 */
	public static final EventType<AffineEvent> CHANGE_FINISHED =
			new EventType<>(Event.ANY, "CHANGE_FINISHED");


	private final Affine affine;
	private final Dimension2D targetDimension;


	public AffineEvent(EventType<? extends Event> eventType,
	                   Affine affine,
	                   Dimension2D targetDimension) {
		super(eventType);
		this.affine = affine;
		this.targetDimension = targetDimension;
	}

	public Dimension2D getTargetDimension() { return targetDimension; }
	public Point2D getTargetCentre() { return centreOf(targetDimension); }

	public Dimension2D getTransformedDimension() {
		return new Dimension2D(targetDimension.getWidth() * affine.getMxx(),
				                      targetDimension.getHeight() * affine.getMyy());
	}
	public Point2D getTransformedCentre() { return centreOf(getTransformedDimension()); }

	private static Point2D centreOf(Dimension2D d) {
		return new Point2D(d.getWidth() / 2d, d.getHeight() / 2d);
	}

	/**
	 * @return a copy of the current affine transformation
	 */
	public Affine getAffine() { return new Affine(affine); }
	/**
	 * @return the current translation(non delta) on the X axis
	 */
	public double translateX() { return affine.getTx(); }
	/**
	 * @return the current translation(non delta) on the Y axis
	 */
	public double translateY() { return affine.getTy(); }
	/**
	 * @return the current scale(non delta) on the X axis
	 */
	public double scaleX() { return affine.getMxx(); }
	/**
	 * @return the current scale(non delta) on the Y axis
	 */
	public double scaleY() { return affine.getMyy(); }

}
