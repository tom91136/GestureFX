package net.kurobako.gesturefx.sample;

import atlantafx.base.theme.PrimerDark;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class Main {

  public static class JFXApp extends Application {

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage primaryStage) throws Exception {
      Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

      FXMLLoader loader = new FXMLLoader(getClass().getResource("/Sampler.fxml"));
      Parent parent = loader.load();
      try {
        loader.<SamplerController>getController().hostServices = getHostServices(); // seems ugly
      } catch (Throwable e) {
        System.err.println("Unable to access host services:" + e.getMessage());
      }
      primaryStage.setTitle("GesturePane samples");
      primaryStage.setScene(new Scene(parent));
      primaryStage.show();
    }

  }

  public static void main(String[] args) {
    JFXApp.main(args);
  }

}
