package net.kurobako.gesturefx;

import javafx.beans.property.ReadOnlyDoubleProperty;

/**
 * A simple abstraction over some affine transformation. Some properties are intentionally
 * left out as those are not used in {@link GesturePane}
 */
@SuppressWarnings("unused")
public interface NamedAffine {
	/**
	 * @return the scale on the X axis
	 */
	double scaleX();
	ReadOnlyDoubleProperty scaleXProperty();
	/**
	 * @return the scale on the Y axis
	 */
	double scaleY();
	ReadOnlyDoubleProperty scaleYProperty();
	/**
	 * @return the translation on the X axis
	 */
	double translateX();
	ReadOnlyDoubleProperty translateXProperty();
	/**
	 * @return the translation on the Y axis
	 */
	double translateY();
	ReadOnlyDoubleProperty translateYProperty();


}
