package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField         emailField;
    @FXML private PasswordField     passwordField;
    @FXML private Button            loginBtn;
    @FXML private Label             errorLabel;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label             loadingLabel;
    @FXML private VBox              devBanner;

    private String devEmail;
    private String devPassword;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Load dev credentials from config
        try (InputStream in = getClass().getResourceAsStream("/config.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                devEmail    = props.getProperty("DEV_EMAIL",    "test@hscprep.com");
                devPassword = props.getProperty("DEV_PASSWORD", "TestPass123!");
            }
        } catch (Exception e) {
            devEmail    = "test@hscprep.com";
            devPassword = "TestPass123!";
        }

        // Enter key on password → trigger login
        passwordField.setOnAction(e -> onLogin());

        // Hide spinner initially
        setLoading(false);
    }

    // ── Fill dummy credentials ────────────────────────────────
    @FXML private void onFillDummy() {
        emailField.setText(devEmail);
        passwordField.setText(devPassword);
        errorLabel.setText("");
    }

    // ── Login ─────────────────────────────────────────────────
    @FXML private void onLogin() {
        String email = emailField.getText().trim();
        String pass  = passwordField.getText();

        // Basic validation
        if (email.isBlank()) {
            showError("Please enter your email address.");
            return;
        }
        if (pass.isBlank()) {
            showError("Please enter your password.");
            return;
        }

        setLoading(true);
        errorLabel.setText("");

        // Run auth on background thread (never block FX thread)
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode res = SupabaseClient.getInstance().signIn(email, pass);

                if (res.has("access_token")) {
                    String token   = res.get("access_token").asText();
                    String refresh = res.has("refresh_token") ? res.get("refresh_token").asText() : "";
                    String userId  = res.path("user").path("id").asText();

                    // Fetch profile from DB
                    JsonNode profiles = SupabaseClient.getInstance()
                            .from("profiles")
                            .select("name,year,track,xp,streak,level")
                            .eq("user_id", userId)
                            .limit(1)
                            .execute();

                    String name  = "Student";
                    String track = "HSC Science";
                    int xp       = 0;
                    int streak   = 0;
                    int level    = 1;

                    if (profiles.isArray() && profiles.size() > 0) {
                        JsonNode p = profiles.get(0);
                        name   = p.path("name")   .asText("Student");
                        track  = p.path("track")  .asText("HSC Science");
                        xp     = p.path("xp")     .asInt(0);
                        streak = p.path("streak") .asInt(0);
                        level  = p.path("level")  .asInt(1);
                    }

                    // Store session
                    final String fName = name, fTrack = track;
                    final int    fXp   = xp, fStreak = streak, fLevel = level;

                    SessionManager.getInstance().setSession(
                            userId, token, refresh, fName, fTrack, fXp, fStreak, fLevel
                    );

                    SessionManager.getInstance().setUserEmail(email); // email = the TextField value

                    // Navigate to Dashboard on FX thread
                    Platform.runLater(this::navigateToDashboard);

                } else {
                    // Auth failed
                    String msg = res.has("error_description")
                            ? res.get("error_description").asText()
                            : res.has("msg")
                            ? res.get("msg").asText()
                            : "Invalid email or password.";
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

    // ── Sign Up ───────────────────────────────────────────────
// ── Sign Up ───────────────────────────────────────────────
    @FXML
    private void onSignUp() {
        try {
            // Make sure the name matches exactly what you named the new FXML file
            URL fxml = getClass().getResource("/Signup.fxml");
            if (fxml == null) {
                showError("Signup.fxml not found. Check your resources folder.");
                return;
            }
            Parent root = FXMLLoader.load(fxml);
            Stage stage = (Stage) loginBtn.getScene().getWindow();

            // Keep the same window size when switching scenes
            Scene scene = new Scene(root, stage.getScene().getWidth(), stage.getScene().getHeight());
            stage.setScene(scene);

        } catch (Exception e) {
            showError("Failed to load sign up page: " + e.getMessage());
            e.printStackTrace(); // This will print the exact error to your console if it fails
        }
    }

    // ── Helpers ───────────────────────────────────────────────
    private void showError(String msg) {
        errorLabel.setText(msg);
    }

    private void setLoading(boolean loading) {
        loginBtn.setDisable(loading);
        loadingSpinner.setVisible(loading);
        loadingLabel.setVisible(loading);
        loginBtn.setText(loading ? "Signing in..." : "Sign In");
    }

    private void navigateToDashboard() {
        try {
            URL fxml = getClass().getResource("/DashboardView.fxml");
            if (fxml == null) {
                showError("DashboardView.fxml not found.");
                setLoading(false);
                return;
            }
            Parent root = FXMLLoader.load(fxml);
            Stage stage = (Stage) loginBtn.getScene().getWindow();
            Scene scene = new Scene(root, 1200, 820);
            stage.setScene(scene);
            stage.setTitle("HSC Prep Pro — Dashboard");
        } catch (IOException e) {
            setLoading(false);
            showError("Failed to load dashboard: " + e.getMessage());
        }
    }
}