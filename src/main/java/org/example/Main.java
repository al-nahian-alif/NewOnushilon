package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        // Starting with the Login view.
        // Note: If you want to jump straight to the dashboard for testing, change this to "/dashboard.fxml"
        URL fxml = getClass().getResource("/LoginView.fxml");
        System.out.println("Loading: " + fxml);
        Parent root = FXMLLoader.load(fxml);

        Scene scene = new Scene(root, 1000, 660);

        // Safely loading the CSS from your GitHub branch.
        // The null check prevents the app from crashing if styles.css is missing or moved.
        URL cssUrl = this.getClass().getResource("/styles.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        stage.setScene(scene);
        stage.setTitle("Onushilon");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.show();

        // Keeping your local UI tweak for the scroll panes
        Platform.runLater(() ->
                root.lookupAll(".scroll-pane > .viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}