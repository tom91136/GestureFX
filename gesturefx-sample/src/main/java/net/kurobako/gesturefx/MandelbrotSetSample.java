package net.kurobako.gesturefx;

import net.kurobako.gesturefx.GesturePane.FitMode;
import net.kurobako.gesturefx.GesturePane.Transformable;
import net.kurobako.gesturefx.SamplerController.Sample;

import org.apache.commons.math3.complex.Complex;

import java.util.function.BiFunction;
import java.util.stream.IntStream;

import javafx.geometry.Dimension2D;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.transform.Affine;

public class MandelbrotSetSample implements Sample {


	private double[][] drawF(int size,
	                         int iter,
	                         double poiX,
	                         double poiY,
	                         double zoom) {
		Configuration configuration = new Configuration(size, iter);
		double[][] matrix = IntStream.rangeClosed(0, size).parallel().mapToObj(x -> {
			return IntStream.rangeClosed(0, size).mapToDouble(y -> {
				return computeDiscrete(configuration, x, y, poiX, poiY,zoom);
			}).toArray();
		}).toArray(double[][]::new);
		return matrix;
	}
	private void render(PixelWriter writer, double[][] matrix) {
		for (int x = 0; x < matrix.length; x++) {
			for (int y = 0; y < matrix[x].length; y++) {
				writer.setColor(x, y, Color.gray(matrix[x][y]));
			}
		}
	}




	double[][] that = null;
	Affine last;

	@Override
	public Node mkRoot() {

		Canvas canvas = new Canvas(500, 500);
		canvas.setMouseTransparent(true);
		final GraphicsContext gc = canvas.getGraphicsContext2D();
		PixelWriter writer = gc.getPixelWriter();
		gc.setFill(Color.WHEAT);
		gc.setFont(Font.getDefault());
		gc.fillText("hello   world!", 15, 50);

		that = drawF((int) canvas.getWidth(), 200, 0,0,1);

		GesturePane pane = new GesturePane(new Transformable() {
			@Override
			public double width() {
				return canvas.getWidth();
			}
			@Override
			public double height() {
				return canvas.getHeight();
			}
			@Override
			public void setTransform(Affine affine) {
//				gc.setTransform(affine);
			}
		});
		pane.fitModeProperty().set(FitMode.CENTER);


		pane.addEventHandler(AffineEvent.CHANGE_STARTED, e -> {
			Dimension2D td = new Dimension2D(500, 500);
//
//double cx = scale(e.getAffine().getTx(), -canvas.getHeight() * e.getAffine().getMxx(), 0, -1, 1);
//double cy = scale(e.getAffine().getTy(), -canvas.getHeight()* e.getAffine().getMxx(), 0, -1, 1);
//
//			// -1 <-> 1
////			double tx = e.getAffine().getTx() ;/// td.getWidth()/e.getAffine().getMxx();
////			double ty = e.getAffine().getTy() ;/// td.getHeight()/e.getAffine().getMxx();
//			that = drawF((int) canvas.getWidth(), 320, cx, cy, e.getAffine().getMxx());
//			System.out.println(cx + " , " + cy + " z=" + e.getAffine().getMxx());
//			WritableImage image = new WritableImage(501, 501);
//			render(image.getPixelWriter(), that);
//			gc.drawImage(image, 0, 0);
//			last = e.getAffine();

		});
		pane.addEventHandler(AffineEvent.CHANGED, e -> {
//			gc.setTransform(e.getAffine());


			System.out.println(e.getAffine());
			double cx = scale(e.getAffine().getTx(), -canvas.getHeight() * e.getAffine().getMxx(), 0, -1, 1);
			double cy = scale(e.getAffine().getTy(), -canvas.getHeight()* e.getAffine().getMxx(), 0, -1, 1);

			double dx = e.getAffine().getTx() - last.getTx();
			double dy = e.getAffine().getTy() - last.getTy();
			double dz = e.getAffine().getMxx() - last.getMxx();


			e.getAffine().setMxx(e.getAffine().getMxx() );
			e.getAffine().setMyy(e.getAffine().getMyy());
//			gc.translate(dx, dy);
//			gc.scale(dz, dz);
			gc.setTransform(e.getAffine());
//			System.out.println(cx + " , " + cy + " z=" + e.getAffine().getMxx());
//			that = drawF((int) canvas.getWidth(), 100, -cx, -cy, e.getAffine().getMxx());
			WritableImage image = new WritableImage(501, 501);
			if(that == null) return;
			render(image.getPixelWriter(), that);
			gc.setFill(Color.RED);
			gc.setStroke(Color.GREEN);
			gc.setLineWidth(10);
			gc.strokeRect(0, 0, image.getWidth(), image.getHeight());
			gc.drawImage(image, 0, 0);
//			gc.setTransform(e.getAffine());
//			gc.setFill(Color.RED);

gc.setTransform(new Affine());

		});
		pane.addEventHandler(AffineEvent.CHANGE_FINISHED, e -> {
gc.setTransform(new Affine());
			double cx = 1-scale(e.getAffine().getTx(), -canvas.getHeight() * e.getAffine().getMxx(), 0, -1, 1);
			double cy = 1-scale(e.getAffine().getTy(), -canvas.getHeight() * e.getAffine().getMxx(), 0, -1, 1);

			// -1 <-> 1
//			double tx = e.getAffine().getTx() ;/// td.getWidth()/e.getAffine().getMxx();
//			double ty = e.getAffine().getTy() ;/// td.getHeight()/e.getAffine().getMxx();

			that = drawF((int) canvas.getWidth(), 320, cx, cy, e.getAffine().getMxx());
			System.out.println(cx + " , " + cy + " z=" + e.getAffine().getMxx());
			WritableImage image = new WritableImage(501, 501);
			render(image.getPixelWriter(), that);
			gc.drawImage(image, 0, 0);
			last = e.getAffine();

		});


		return new StackPane(canvas, pane);
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

		public Configuration(int size, int maxIteration) {
			this.size = size;
			this.maxIteration = maxIteration;
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

	static double computeDiscrete(Configuration config, int x, int y, double poix, double poiy, double zoom) {
		double scale = 1d / zoom;
		double scaledX = scale(x, 0, config.size, poix - scale, poix + scale);
		double scaledY = scale(y, 0, config.size, poiy - scale, poiy + scale);
		double colour = colourize(config.maxIteration, iterateFrac(MandelbrotSetSample::mandelbrot,
				Complex.valueOf(scaledX, scaledY), Complex.ZERO, 0, config.maxIteration));
		return (colour);
	}

}
