package net.kurobako.gesturefx;

import net.kurobako.gesturefx.SamplerController.Sample;

import org.apache.commons.math3.complex.Complex;

import java.util.function.BiFunction;

import javafx.scene.Node;

public class MandelbrotSetSample implements Sample {


	@Override
	public Node mkRoot() {


		return null;
	}

	static class IterationStep {
		final Complex value;
		final int iteration;

		public IterationStep(Complex value, int iteration) {
			this.value = value;
			this.iteration = iteration;
		}
	}

	static class Tuple<F, S> {
		final F first;
		final S second;

		public Tuple(F first, S second) {
			this.first = first;
			this.second = second;
		}
	}

	static class Configuration {
		final int size;
		final int maxIteration;
		final double zoom;
		final Tuple<Double, Double> poiX;
		final Tuple<Double, Double> poiY;

		public Configuration(int size, int maxIteration, double x, double y, double zoom) {
			this.size = size;
			this.maxIteration = maxIteration;
			this.zoom = zoom;
			double scale = 1d / zoom;
			poiX = new Tuple<>(x - scale, x + scale);
			poiY = new Tuple<>(y - scale, y + scale);
		}
	}

	static Complex mandelbrot(Complex c, Complex z) {
		return z.multiply(z).add(c);
	}

	static IterationStep iterateFrac(BiFunction<Complex, Complex, Complex> frac, Complex c,
	                                 Complex z, int iteration, int maxIteration) {
		if (iteration >= maxIteration)
			return new IterationStep(Complex.ZERO, 0);
		Complex result = frac.apply(c, z);
		if (z.abs() > 2)
			return new IterationStep(result, iteration);
		return iterateFrac(frac, c, result, iteration + 1, maxIteration);
	}

	static double colourizeSmooth(int maxIteration, IterationStep step) {
		return (((double) step.iteration) - Math.log(Math.log(step.value.abs()))) / maxIteration;
	}

	static double colourize(int maxIteration, IterationStep step) {
		return ((double) step.iteration) / maxIteration;
	}

	static double scale(double input, double inputMin, double inputMax, double outputMin,
	                    double outputMax) {
		return ((outputMax - outputMin) * (input - inputMin) / (inputMax - inputMin)) + outputMin;
	}

	static int computeDiscrete(Configuration config, int x, int y) {
		double scaledX = scale(x, 0, config.size, config.poiX.first, config.poiX.second);
		double scaledY = scale(y, 0, config.size, config.poiY.first, config.poiY.second);
		double colour = colourize(config.maxIteration, iterateFrac(MandelbrotSetSample::mandelbrot,
				Complex.valueOf(scaledX, scaledY), Complex.ZERO, 0, config.maxIteration));
		return (int) (colour * 255);
	}

}
