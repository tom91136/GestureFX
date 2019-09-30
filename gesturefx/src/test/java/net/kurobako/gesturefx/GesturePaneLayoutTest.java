package net.kurobako.gesturefx;


import net.kurobako.gesturefx.GesturePaneTests.TestTarget;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.testfx.api.FxToolkit;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import static net.kurobako.gesturefx.GesturePaneTests.HANDLER;
import static net.kurobako.gesturefx.GesturePaneTests.basicTestCases;
import static org.assertj.core.api.Assertions.assertThat;

// TODO this needs to be run on OpenJFX to be meaningful
@RunWith(Parameterized.class) public class GesturePaneLayoutTest {

	@Parameters(name = "{index}:{0}")
	public static Collection<TestTarget> data() {return basicTestCases(); }

	@Parameter public TestTarget target;

	@BeforeClass public static void setupClass() { GesturePaneTests.setupProperties(); }

	private StackPane root;
	private Stage stage;

	@Before public void setup() throws Exception {
		if (Platform.isFxApplicationThread()) throw new AssertionError("Invalid test state");
		Thread.setDefaultUncaughtExceptionHandler(HANDLER);
		stage = FxToolkit.registerPrimaryStage();
		FxToolkit.setupSceneRoot(() -> {
			if (!Platform.isFxApplicationThread()) throw new AssertionError("Invalid test state");
			Thread.currentThread().setUncaughtExceptionHandler(HANDLER);
			root = new StackPane();
			return root;
		});
		FxToolkit.setupStage(stage -> {
			stage.sizeToScene();
			stage.setAlwaysOnTop(true);
		});
		FxToolkit.showStage();
	}

	@After public void tearDown() throws Exception { FxToolkit.cleanupStages(); }

	@Test public void testLayoutPassCount() throws Exception {
		GesturePane pane = target.createPane();
		AtomicInteger layoutCount = new AtomicInteger(0);
		FxToolkit.setupFixture(() -> {
			root.getChildren().add(new StackPane() {
				{
					getChildren().add(pane);
				}

				@Override protected void layoutChildren() {
					super.layoutChildren();
					layoutCount.incrementAndGet();
				}
			});
			stage.setWidth(200);
			stage.setHeight(200);


		});
		Thread.sleep(500);

		FxToolkit.setupFixture(() -> {
			stage.setWidth(350);
			stage.setHeight(350);
		});
		Thread.sleep(1500);
		assertThat(layoutCount).hasValueLessThanOrEqualTo(10);
	}


}