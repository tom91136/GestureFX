package net.kurobako.gesturefx;

import javafx.event.Event;
import javafx.event.EventTarget;
import javafx.event.EventType;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.transform.Affine;

@SuppressWarnings("WeakerAccess")
public final class AffineEvent extends Event {

	public static final EventType<AffineEvent> CHANGE_STARTED =
			new EventType<>(Event.ANY, "CHANGE_STARTED");
	public static final EventType<AffineEvent> CHANGED =
			new EventType<>(Event.ANY, "CHANGED");
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
	public AffineEvent(Object source, EventTarget target,
	                   EventType<? extends Event> eventType,
	                   Affine affine,
	                   Dimension2D targetDimension) {
		super(source, target, eventType);
		this.affine = affine;
		this.targetDimension = targetDimension;
	}
	public Affine getAffine() { return affine; }

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

}
