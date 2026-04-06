package org.example;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HomeController {

    @FXML
    private void handlePhysicsAction(ActionEvent event) {
        launchPractice(event,"Physics");
    }

    @FXML
    private void handleMathAction(ActionEvent event) {
        launchPractice(event, "Math");
    }

    @FXML
    private void handleChemistryAction(ActionEvent event) {
        launchPractice(event,"Chemistry");
    }

    // The shared logic to switch screens
    private void launchPractice(ActionEvent event, String subject) {
        try {
            // 1. Load the FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("QuestionView.fxml"));
            Parent root = loader.load();

            // 2. Get the Controller and pass the data
            QuestionController controller = loader.getController();
            controller.startPracticeSession(subject); // <--- This passes the data!

            // 3. Switch the Scene
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}