package org.example;

import javafx.application.Application;
<<<<<<< HEAD
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.URL;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        URL fxml = getClass().getResource("/LoginView.fxml");
        System.out.println("Loading: " + fxml);
        Parent root = FXMLLoader.load(fxml);

        Scene scene = new Scene(root, 1000, 660);
        stage.setScene(scene);
        stage.setTitle("HSC Prep Pro");
        stage.setMinWidth(800);
        stage.setMinHeight(560);
        stage.show();

        Platform.runLater(() ->
                root.lookupAll(".scroll-pane > .viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );
    }

    public static void main(String[] args) {
        launch(args);
=======
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource("dashboard.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1200, 800);
        String css = this.getClass().getResource("styles.css").toExternalForm();
        scene.getStylesheets().add(css);
        stage.setTitle("Onushiloni");
        stage.setScene(scene);
        stage.show();
}

public static void main(String[] args) {
        launch();
>>>>>>> a46c9a1364b495cf075a13545d3400c32f1aa64b
    }
}