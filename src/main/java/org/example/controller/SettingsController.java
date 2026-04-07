package org.example.controller;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class SettingsController implements Initializable {

    // ── Sidebar & Layout ──────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane mainScroll;

    // ── Profile Section ───────────────────────────────────────
    @FXML private StackPane avatarCircle;
    @FXML private Label     avatarInitials;
    @FXML private VBox      profileViewMode;
    @FXML private Label     profileNameLabel;
    @FXML private Label     profileEmailLabel;
    @FXML private Label     studentIdLabel;

    @FXML private HBox      profileEditMode;
    @FXML private TextField editNameField;
    @FXML private Button    editProfileBtn;
    @FXML private Label     profileSavedLabel;

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Set sidebar active state
        if (sidebarController != null) {
            sidebarController.setActivePage("settings");
        }

        // Apply background styling to the scroll pane
        Platform.runLater(() -> {
            if (mainScroll != null) {
                mainScroll.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"));
            }
        });

        // Load the profile details
        loadUserProfile();
    }

    // ════════════════════════════════════════════════════════════
    //  PROFILE LOGIC
    // ════════════════════════════════════════════════════════════
    private void loadUserProfile() {
        SessionManager session = SessionManager.getInstance();
        String name = session.getUserName();
        String uid  = session.getUserId();

        profileNameLabel.setText(name != null && !name.isBlank() ? name : "Student");
        avatarInitials.setText(session.getInitials());

        // Setup a mock email or fetch it if you store it in SessionManager
        profileEmailLabel.setText("test@hscprep.com");

        if (uid != null) {
            // Format a clean, short display ID from the UUID
            String shortId = uid.length() > 8 ? uid.substring(0, 8).toUpperCase() : uid;
            studentIdLabel.setText("STUDENT ID: HSC-" + shortId);
        }
    }

    @FXML
    private void onEditProfile() {
        profileViewMode.setVisible(false);
        profileViewMode.setManaged(false);
        profileEditMode.setVisible(true);
        profileEditMode.setManaged(true);
        editProfileBtn.setVisible(false);

        editNameField.setText(profileNameLabel.getText());
        profileSavedLabel.setText(""); // Clear previous save messages
    }

    @FXML
    private void onCancelEdit() {
        profileEditMode.setVisible(false);
        profileEditMode.setManaged(false);
        profileViewMode.setVisible(true);
        profileViewMode.setManaged(true);
        editProfileBtn.setVisible(true);
    }

    @FXML
    private void onSaveName() {
        String newName = editNameField.getText().trim();
        if (newName.isEmpty()) return;

        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) return;

        // 1. Instantly update local UI
        profileNameLabel.setText(newName);
        onCancelEdit();
        profileSavedLabel.setText("Profile updated successfully!");

        // 2. Update Session Manager & Initials
        SessionManager.getInstance().setSession(uid, newName);
        avatarInitials.setText(SessionManager.getInstance().getInitials());

        // 3. Update Sidebar (if it exists)
        if (sidebarController != null) {
            sidebarController.refreshProfileCard();
        }

        // 4. Save to database in the background
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance()
                        .from("profiles")
                        .eq("user_id", uid)
                        .update("{\"name\":\"" + newName + "\"}");
            } catch (Exception e) {
                System.err.println("[Settings] Failed to save name: " + e.getMessage());
                Platform.runLater(() -> profileSavedLabel.setText("Saved locally (DB error)"));
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  DANGER ZONE
    // ════════════════════════════════════════════════════════════
    @FXML
    private void onDeleteAccount() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Account");
        alert.setHeaderText("Are you absolutely sure?");
        alert.setContentText("This action cannot be undone. It will permanently delete your account, XP, and all study history.");

        // Apply some styling to make the dialog look native
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif;");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            System.out.println("Initiating account deletion for user: " + SessionManager.getInstance().getUserId());
            // Add your actual deletion API call and redirect to Login screen here
        }
    }
}