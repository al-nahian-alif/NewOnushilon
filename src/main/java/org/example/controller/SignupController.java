package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.supabase.SupabaseClient;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class SignupController implements Initializable {

    @FXML private TextField         nameField;
    @FXML private TextField         emailField;
    @FXML private PasswordField     passwordField;
    @FXML private Button            signupBtn;
    @FXML private Label             errorLabel;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label             loadingLabel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Pressing Enter on password field triggers sign up
        passwordField.setOnAction(e -> onSignUp());
        setLoading(false);
    }

    @FXML
    private void onSignUp() {
        String name  = nameField.getText().trim();
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();

        // Basic validation
        if (name.isBlank()) {
            showError("Please enter your full name.");
            return;
        }
        if (email.isBlank() || !email.contains("@")) {
            showError("Please enter a valid email address.");
            return;
        }
        if (pass.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        setLoading(true);
        errorLabel.setText("");

        // Run auth on background thread
        Thread.ofVirtual().start(() -> {
            try {
                // 1. Register the user in Supabase Auth (Updated to use 3 parameters)
                JsonNode res = SupabaseClient.getInstance().signUp(email, pass, name);

                if (res != null && res.has("user") && !res.get("user").isNull()) {
                    String userId = res.path("user").path("id").asText();

                    // 2. Initialize the user's profile in the database
                    // (Even though name is in auth meta-data, you likely still want the public profile row)
                    String profilePayload = String.format(
                            "{\"user_id\":\"%s\", \"name\":\"%s\", \"track\":\"HSC Science\", \"xp\":0, \"streak\":0, \"level\":1}",
                            userId, name.replace("\"", "\\\"")
                    );

                    try {
                        SupabaseClient.getInstance().from("profiles").insert(profilePayload);
                    } catch (Exception e) {
                        System.err.println("Warning: Could not create profile row: " + e.getMessage());
                    }

                    // 3. Navigate back to login with a success message
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Account Created");
                        alert.setHeaderText("Welcome to HSC Prep Pro, " + name + "!");
                        alert.setContentText("Your account has been successfully created. Please sign in to continue.");
                        alert.showAndWait();
                        navigateToLogin();
                    });

                } else {
                    // Auth failed (e.g., user already exists)
                    String msg = res != null && res.has("msg")
                            ? res.get("msg").asText()
                            : "Failed to create account. Email might already be in use.";
                    Platform.runLater(() -> {
                        setLoading(false);
                        showError(msg);
                    });
                }

            } catch (Exception ex) {
                Platform.runLater(() -> {
                    setLoading(false);
                    showError("Connection failed. Check your internet and try again.\n" + ex.getMessage());
                });
            }
        });
    }

    @FXML
    private void onBackToLogin() {
        navigateToLogin();
    }

    // ── Helpers ───────────────────────────────────────────────
    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#ef4444;"); // Red for errors
    }

    private void setLoading(boolean loading) {
        signupBtn.setDisable(loading);
        loadingSpinner.setVisible(loading);
        loadingLabel.setVisible(loading);
        signupBtn.setText(loading ? "Creating account..." : "Create Account");
    }

    private void navigateToLogin() {
        try {
            URL fxml = getClass().getResource("/LoginView.fxml");
            if (fxml == null) {
                showError("Login.fxml not found.");
                return;
            }
            Parent root = FXMLLoader.load(fxml);
            Stage stage = (Stage) signupBtn.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getScene().getWidth(), stage.getScene().getHeight()));
        } catch (IOException e) {
            showError("Failed to load login page: " + e.getMessage());
        }
    }
}