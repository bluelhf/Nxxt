package io.github.bluelhf.nxxt;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Properties;


public class Launcher extends Application {
    static Controller controller = null;

    /**
     * Main method to start the entire JavaFX applet
     */
    public void start(Stage stage) throws Exception {

        // Loads the JavaFX scene from ui.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui.fxml"));
        Parent root = loader.load();
        controller = loader.getController();


        Properties properties = new Properties();
        InputStream propertyStream = Nxxt.class.getResourceAsStream("/info.properties");
        try {
            properties.load(propertyStream);
        } catch (Exception e) {
            Nxxt.getLogger().severe("Failed to read properties!");
        }

        Scene scene = new Scene(root);
        controller.initialise(stage);
        stage.setScene(scene);
        stage.getIcons().add(new Image("/logo.png"));

        stage.setTitle("Nxxt Autoclicker " + properties.getProperty("version"));
        controller.postInit();
        stage.show();
    }

    public static void main(String[] args) {
        launch();
        controller.shutdown();
        Platform.exit();
    }
}
