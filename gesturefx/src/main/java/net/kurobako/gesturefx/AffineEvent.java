package net.kurobako.gesturefx;

import java.util.Objects;
import java.util.Optional;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.transform.Affine;

/**
 * An event that would be fired when the target within a {@link GesturePane} is transformed in
 * anyway
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class AffineEvent extends Event {

	private static final long serialVersionUID = 1437008082899813504L;


	public static final EventType<AffineEvent> ANY =
			new EventType<>(Event.ANY, "AFFINE");

	/**
	 * Fired when the transformation has just started
	 */
	public static final EventType<AffineEvent> CHANGE_STARTED =
			new EventType<>(AffineEvent.ANY, "AFFINE_CHANGE_STARTED");
	/**
	 * Fired when the transformation in progress produced a change(not necessarily visible)
	 */
	public static final EventType<AffineEvent> CHANGED =
			new EventType<>(AffineEvent.ANY, "AFFINE_CHANGED");
	/**
	 * Fired when the transformation has finished
	 */
	public static final EventType<AffineEvent> CHANGE_FINISHED =
			new EventType<>(AffineEvent.ANY, "AFFINE_CHANGE_FINISHED");


	private final Affine affine;
	private final Affine previous;
	private final Dimension2D targetDimension;


	public AffineEvent(EventType<? extends Event> eventType,
	                   Affine affine,
	                   Affine previous,
	                   Dimension2D targetDimension) {
		super(eventType);
		this.affine = affine;
		this.previous = previous;
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
	 * @return a copy of the previous affine transformation if exists
	 */
	public Optional<Affine> previous() { return Optional.ofNullable(previous).map(Affine::new); }

	/**
	 * @return a copy of the current affine transformation(named) if exists
	 */
	public Optional<NamedAffine> namedPrevious() {
		return previous().map
				(NamedAffineTransform::new);
	}

	/**
	 * @return a copy of the current affine transformation
	 */
	public Affine current() { return new Affine(affine); }

	/**
	 * @return a copy of the current affine transformation(named)
	 */
	public NamedAffine namedCurrent() { return new NamedAffineTransform(current()); }


	/**
	 * @return a copy of the current affine transformation subtract the previous transformation.
	 * This is the same as direct matrix subtraction: Mat(previous)-Mat(current)
	 */
	public Affine difference() {
		Affine c = this.affine;
		Affine p = this.previous;
		if (p == null) return current();
		return new Affine(
				c.getMxx() - p.getMxx(), c.getMxy() - p.getMxy(), c.getMxz() - p.getMxz(),
				c.getTx() - p.getTx(),
				c.getMyx() - p.getMyx(), c.getMyy() - p.getMyy(), c.getMyz() - p.getMyz(),
				c.getTy() - p.getTy(),
				c.getMzx() - p.getMzx(), c.getMzy() - p.getMzy(), c.getMzz() - p.getMzz(),
				c.getTz() - p.getTz());
	}

	/**
	 * @return a copy of the difference affine transformation(named), see {@link #difference()}
	 */
	public NamedAffine namedDifference() { return new NamedAffineTransform(difference());}


	static class NamedAffineTransform implements NamedAffine {
		private final Affine af;
		NamedAffineTransform(Affine affine) {this.af = Objects.requireNonNull(affine);}
		@Override public double scaleX() { return af.getMxx(); }
		@Override public ReadOnlyDoubleProperty scaleXProperty() { return af.mxxProperty(); }
		@Override public double scaleY() { return af.getMyy(); }
		@Override public ReadOnlyDoubleProperty scaleYProperty() { return af.myyProperty(); }
		@Override public double translateX() { return af.getTx(); }
		@Override public ReadOnlyDoubleProperty translateXProperty() { return af.txProperty(); }
		@Override public double translateY() { return af.getTy(); }
		@Override public ReadOnlyDoubleProperty translateYProperty() { return af.tyProperty(); }
	}

}
