package net.kurobako.gesturefx;


import net.kurobako.gesturefx.GesturePane.ScrollMode;

import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.testfx.api.FxAssert;
import org.testfx.api.FxRobot;
import org.testfx.api.FxToolkit;
import org.testfx.matcher.base.NodeMatchers;

import javafx.geometry.VerticalDirection;
import javafx.scene.image.ImageView;
import javafx.scene.input.ZoomEvent;

import static org.assertj.core.api.Assertions.assertThat;


public class GesturePaneTest {

	private static final String ID = "target";
	private GesturePane pane;
	private ImageView imageView;


	@Before
	public void setup() throws Exception {
		FxToolkit.registerPrimaryStage();
		FxToolkit.setupSceneRoot(() -> {
			imageView = new ImageView(getClass().getResource("/lena_512.jpg")
					                          .toExternalForm());
			pane = new GesturePane(imageView);
			pane.setId(ID);
			return pane;
		});
		FxToolkit.showStage();
	}


	//	@Override
//	public void start(Stage stage) throws Exception {
//		imageView = new ImageView(getClass().getResource("/lena_compressed.jpg")
//				                          .toExternalForm());
//		pane = new GesturePane(imageView);
//		pane.setId(ID);
//		Scene scene = new Scene(pane);
//		stage.setScene(scene);
//		stage.show();
//	}

	@Test
	public void testInitialisation() throws Exception {
		FxAssert.verifyThat("#" + ID, NodeMatchers.isNotNull());
	}


	@Test
	public void testScale() throws Exception {
		pane.zoomTarget(2, false);
		assertThat(pane.getCurrentScale()).isEqualTo(2d);
	}

	@Test
	public void testSaleByTouch() throws Exception {
		double factor = 4.2;
		pane.fireEvent(new ZoomEvent(ZoomEvent.ZOOM, 0, 0, 0, 0, false, false, false, false,
				                            false, false, factor, factor, null));
		assertThat(pane.getCurrentScale()).isEqualTo(factor);
	}

	@Test
	public void testScaleByScroll() throws Exception {
		pane.scrollModeProperty().set(ScrollMode.ZOOM);
		FxRobot robot = new FxRobot();
		assertThat(pane.getCurrentScale()).isEqualTo(1d);
		robot.moveTo(pane);
		robot.scroll(5, VerticalDirection.UP);
		double expected = Math.pow(1 + GesturePane.DEFAULT_SCROLL_FACTOR, 5);
		assertThat(pane.getCurrentScale())
				.isCloseTo(expected, Offset.offset(0.0001));
		assertThat(imageView.getTransforms()).hasOnlyOneElementSatisfying(t -> {
			assertThat(t.getMxx()).isCloseTo(t.getMyy(), Offset.offset(0.00000001));
			assertThat(t.getMxx()).isCloseTo(expected, Offset.offset(0.0001));
		});
	}


	@Test
	public void testTranslateX() throws Exception {
		pane.zoomTarget(2, false);
	}

	@Test
	public void testTranslateY() throws Exception {

	}

	@Test
	public void testHorizontalClamp() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testVerticalClamp() throws Exception {
		throw new RuntimeException();
	}

	@Test
	public void testZoomClamp() throws Exception {
		throw new RuntimeException();
	}


}