package net.kurobako.gesturefx;

import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.util.Duration;

/**
 * Programmatic pan and zoom operations supported by {@link GesturePane}.
 * <p>
 * This interface is also the terminal step of the animation builder returned by
 * {@link GesturePane#animate(Duration)} - calling any method through that builder applies the
 * operation as an animated transition rather than an instant change.
 * <p>
 * All coordinates passed to these methods use the <em>target coordinate system</em> unless
 * stated otherwise.
 */
public interface GesturePaneOps {

	/**
	 * Translates the view so that the given target point is centred in the viewport.
	 * The effective result is bounded by the active {@link GesturePane.FitMode}.
	 *
	 * @param pointOnTarget the point to centre on, in target coordinates
	 */
	void centreOn(Point2D pointOnTarget);

	/**
	 * Like {@link #centreOn(Point2D)} but only adjusts the x-axis; the y offset is unchanged.
	 *
	 * @param pointOnTarget the x coordinate to centre on, in target coordinates
	 */
	void centreOnX(double pointOnTarget);

	/**
	 * Like {@link #centreOn(Point2D)} but only adjusts the y-axis; the x offset is unchanged.
	 *
	 * @param pointOnTarget the y coordinate to centre on, in target coordinates
	 */
	void centreOnY(double pointOnTarget);

	/**
	 * Translates the view by the given amount in target coordinates.
	 * The effective result is bounded by the active {@link GesturePane.FitMode}.
	 * <p>
	 * Note: {@link Dimension2D#getWidth()} maps to the x-axis and
	 * {@link Dimension2D#getHeight()} maps to the y-axis.
	 *
	 * @param targetAmount the translation delta in target coordinates
	 */
	void translateBy(Dimension2D targetAmount);

	/**
	 * Zooms to the given uniform scale, pivoting at the given target point.
	 * The scale is clamped to the range [{@link GesturePane#getMinScale()},
	 * {@link GesturePane#getMaxScale()}] and further constrained by the active
	 * {@link GesturePane.FitMode}.
	 *
	 * @param scale         the target scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	default void zoomTo(double scale, Point2D pivotOnTarget) {
		zoomTo(scale, scale, pivotOnTarget);
	}

	/**
	 * Zooms to the given per-axis scales, pivoting at the given target point.
	 * Each scale is clamped to the range [{@link GesturePane#getMinScale()},
	 * {@link GesturePane#getMaxScale()}] and further constrained by the active
	 * {@link GesturePane.FitMode}.
	 *
	 * @param scaleX        the target x-axis scale
	 * @param scaleY        the target y-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomTo(double scaleX, double scaleY, Point2D pivotOnTarget);

	/**
	 * Like {@link #zoomTo(double, Point2D)} but only changes the x-axis scale;
	 * the y-axis scale is unchanged.
	 *
	 * @param scaleX        the target x-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomToX(double scaleX, Point2D pivotOnTarget);

	/**
	 * Like {@link #zoomTo(double, Point2D)} but only changes the y-axis scale;
	 * the x-axis scale is unchanged.
	 *
	 * @param scaleY        the target y-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomToY(double scaleY, Point2D pivotOnTarget);

	/**
	 * Changes the scale by the given uniform amount, pivoting at the given target point.
	 * Equivalent to calling {@link #zoomTo(double, Point2D)} with
	 * {@code getCurrentScaleX() + amount}.
	 *
	 * @param amount        the amount to add to the current scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	default void zoomBy(double amount, Point2D pivotOnTarget) {
		zoomBy(amount, amount, pivotOnTarget);
	}

	/**
	 * Changes the per-axis scales by the given amounts, pivoting at the given target point.
	 * Equivalent to calling {@link #zoomTo(double, double, Point2D)} with
	 * {@code getCurrentScaleX() + amountX} and {@code getCurrentScaleY() + amountY}.
	 *
	 * @param amountX       the amount to add to the current x-axis scale
	 * @param amountY       the amount to add to the current y-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomBy(double amountX, double amountY, Point2D pivotOnTarget);

	/**
	 * Like {@link #zoomBy(double, Point2D)} but only changes the x-axis scale;
	 * the y-axis scale is unchanged.
	 *
	 * @param amountX       the amount to add to the current x-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomByX(double amountX, Point2D pivotOnTarget);

	/**
	 * Like {@link #zoomBy(double, Point2D)} but only changes the y-axis scale;
	 * the x-axis scale is unchanged.
	 *
	 * @param amountY       the amount to add to the current y-axis scale
	 * @param pivotOnTarget the zoom pivot in target coordinates
	 */
	void zoomByY(double amountY, Point2D pivotOnTarget);

}
