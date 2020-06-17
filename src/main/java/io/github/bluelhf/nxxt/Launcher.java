package io.github.bluelhf.nxxt;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Properties;


public class Launcher extends Application {
    static Controller controller = null;

    public void start(Stage stage) throws Exception {
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
        controller.initialise();
        stage.setScene(scene);
        stage.setResizable(false);

        stage.setTitle("Nxxt Autoclicker " + properties.getProperty("version"));
        stage.show();
    }

    public static void main(String[] args) {
        launch();
        Platform.exit();
        controller.shutdown();
    }
}
