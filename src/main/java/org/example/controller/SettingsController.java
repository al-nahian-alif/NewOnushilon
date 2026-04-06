package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;

public class SettingsController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane        mainScroll;

    // ── 8.1 Profile ──────────────────────────────────────────
    @FXML private StackPane avatarCircle;
    @FXML private Label     avatarInitials;
    @FXML private VBox      profileViewMode;
    @FXML private HBox      profileEditMode;
    @FXML private Label     profileNameLabel;
    @FXML private Label     profileEmailLabel;
    @FXML private Label     studentIdLabel;
    @FXML private TextField editNameField;
    @FXML private Button    editProfileBtn;
    @FXML private Label     profileSavedLabel;

    // ── 8.3 Study Preferences ────────────────────────────────
    @FXML private DatePicker       examDatePicker;
    @FXML private ComboBox<String> dailyGoalCombo;
    @FXML private HBox             subjectPillsBox;
    @FXML private Label            prefsSavedLabel;

    // ── 8.4 Notifications ────────────────────────────────────
    @FXML private CheckBox emailToggle;
    @FXML private CheckBox pushToggle;
    @FXML private CheckBox remindersToggle;
    @FXML private CheckBox weeklyToggle;

    // ── 8.5 Appearance ───────────────────────────────────────
    @FXML private VBox   themeLight;
    @FXML private VBox   themeDark;
    @FXML private VBox   themeSystem;
    @FXML private Slider fontSizeSlider;
    @FXML private Label  fontSizeLabel;

    // ── Internal state ────────────────────────────────────────
    private String            activeTheme = "light";
    private final Set<String> activePills = new LinkedHashSet<>();
    private final ObjectMapper mapper      = new ObjectMapper();

    private static final List<String> ALL_SUBJECTS =
            List.of("Physics", "Chemistry", "Mathematics", "Biology", "English");

    private static final String THEME_ON =
            "-fx-background-color:#ffffff; -fx-background-radius:12; " +
                    "-fx-border-color:#0d7ff2; -fx-border-width:2; -fx-border-radius:12; " +
                    "-fx-padding:14 0; -fx-cursor:hand; -fx-min-width:90;";
    private static final String THEME_OFF =
            "-fx-background-color:#f8fafc; -fx-background-radius:12; " +
                    "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:12; " +
                    "-fx-padding:14 0; -fx-cursor:hand; -fx-min-width:90;";

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (sidebarController != null) sidebarController.setActivePage("settings");

        Platform.runLater(() ->
                mainScroll.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );

        // Populate daily goal dropdown
        for (int i = 1; i <= 8; i++)
            dailyGoalCombo.getItems().add(i + (i == 1 ? " Hour" : " Hours"));
        dailyGoalCombo.setValue("4 Hours");

        // FIX 1 — Font slider: update label AND apply to scene live while dragging
        fontSizeSlider.valueProperty().addListener((obs, ov, nv) -> {
            int sz = nv.intValue();
            fontSizeLabel.setText(sz + "px" + (sz == 14 ? " (Default)" : ""));
            if (mainScroll.getScene() != null)
                mainScroll.getScene().getRoot().setStyle("-fx-font-size:" + sz + "px;");
        });

        buildSubjectPills();

        String uid = SessionManager.getInstance().getUserId();
        if (uid != null) Thread.ofVirtual().start(() -> loadAllData(uid));
    }

    // ════════════════════════════════════════════════════════════
    //  LOAD ALL DATA
    // ════════════════════════════════════════════════════════════
    private void loadAllData(String uid) {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("profiles")
                    .select("name,exam_date,target_hours,notification_prefs,subject_priority")
                    .eq("user_id", uid)
                    .limit(1)
                    .execute();

            String   name        = SessionManager.getInstance().getUserName();
            String   examDate    = "";
            double   targetHours = 4.0;
            JsonNode notifPrefs  = null;
            JsonNode subPrio     = null;

            if (rows.isArray() && rows.size() > 0) {
                JsonNode p = rows.get(0);
                name        = p.path("name").asText(name);
                examDate    = p.path("exam_date").asText("");
                targetHours = p.path("target_hours").asDouble(4.0);
                if (!p.path("notification_prefs").isMissingNode() &&
                        !p.path("notification_prefs").isNull())
                    notifPrefs = p.path("notification_prefs");
                if (!p.path("subject_priority").isMissingNode() &&
                        !p.path("subject_priority").isNull())
                    subPrio = p.path("subject_priority");
            }

            String   email     = SessionManager.getInstance().getUserEmail();
            String   studentId = SessionManager.getInstance().getStudentId();
            String   initials  = getInitials(name);
            String   fName     = name;
            double   fHours    = targetHours;
            String   fExamDate = examDate;
            JsonNode fNotif    = notifPrefs;
            JsonNode fSubPrio  = subPrio;

            Platform.runLater(() -> {
                avatarInitials.setText(initials);
                profileNameLabel.setText(fName);
                profileEmailLabel.setText(email != null ? email : "");
                studentIdLabel.setText("STUDENT ID: " + studentId);

                if (!fExamDate.isBlank()) {
                    try { examDatePicker.setValue(LocalDate.parse(fExamDate)); }
                    catch (Exception ignored) {}
                }

                int    hrs     = (int) fHours;
                String goalStr = hrs + (hrs == 1 ? " Hour" : " Hours");
                if (dailyGoalCombo.getItems().contains(goalStr))
                    dailyGoalCombo.setValue(goalStr);

                if (fNotif != null) {
                    emailToggle    .setSelected(fNotif.path("email")    .asBoolean(true));
                    pushToggle     .setSelected(fNotif.path("push")     .asBoolean(true));
                    remindersToggle.setSelected(fNotif.path("reminders").asBoolean(false));
                    weeklyToggle   .setSelected(fNotif.path("weekly")   .asBoolean(true));
                } else {
                    emailToggle .setSelected(true);
                    pushToggle  .setSelected(true);
                    weeklyToggle.setSelected(true);
                }

                if (fSubPrio != null && fSubPrio.isArray()) {
                    activePills.clear();
                    for (JsonNode s : fSubPrio) activePills.add(s.asText());
                    buildSubjectPills();
                }
            });

        } catch (Exception e) {
            System.err.println("[Settings loadAllData] " + e.getMessage());
            Platform.runLater(() -> {
                String n = SessionManager.getInstance().getUserName();
                if (n == null) n = "Student";
                avatarInitials.setText(getInitials(n));
                profileNameLabel.setText(n);
                studentIdLabel.setText("STUDENT ID: " +
                        SessionManager.getInstance().getStudentId());
            });
        }
    }

    // ════════════════════════════════════════════════════════════
    //  8.1  EDIT PROFILE
    // ════════════════════════════════════════════════════════════
    @FXML private void onEditProfile() {
        profileViewMode.setVisible(false);
        profileViewMode.setManaged(false);
        profileEditMode.setVisible(true);
        profileEditMode.setManaged(true);
        editProfileBtn.setVisible(false);
        editNameField.setText(profileNameLabel.getText());
        editNameField.requestFocus();
        editNameField.selectAll();
        profileSavedLabel.setText("");
    }

    @FXML private void onCancelEdit() {
        profileEditMode.setVisible(false);
        profileEditMode.setManaged(false);
        profileViewMode.setVisible(true);
        profileViewMode.setManaged(true);
        editProfileBtn.setVisible(true);
    }

    @FXML private void onSaveName() {
        String newName = editNameField.getText().trim();
        if (newName.isBlank()) {
            profileSavedLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#ef4444;");
            profileSavedLabel.setText("Name cannot be empty.");
            return;
        }
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                String body = String.format("{\"name\":\"%s\"}",
                        newName.replace("\\", "\\\\").replace("\"", "\\\""));
                SupabaseClient.getInstance()
                        .from("profiles").eq("user_id", uid).update(body);
                SessionManager.getInstance().setUserName(newName);
                Platform.runLater(() -> {
                    profileNameLabel.setText(newName);
                    avatarInitials.setText(getInitials(newName));
                    if (sidebarController != null)
                        sidebarController.refreshProfileCard();
                    onCancelEdit();
                    profileSavedLabel.setStyle(
                            "-fx-font-size:12px; -fx-text-fill:#22c55e;");
                    showSaved(profileSavedLabel, "✓  Name updated successfully.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    profileSavedLabel.setStyle(
                            "-fx-font-size:12px; -fx-text-fill:#ef4444;");
                    profileSavedLabel.setText("Error: " + e.getMessage());
                });
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  8.2  ACCOUNT & SUBSCRIPTION
    // ════════════════════════════════════════════════════════════
    @FXML private void onViewInvoices() {
        showInfo("View Invoices", "Invoice portal coming soon in a future update!");
    }

    @FXML private void onManageSubscription() {
        showInfo("Manage Subscription", "Subscription portal coming soon in a future update!");
    }

    // ════════════════════════════════════════════════════════════
    //  8.3  STUDY PREFERENCES
    // ════════════════════════════════════════════════════════════
    @FXML private void onExamDateChanged() {
        LocalDate date = examDatePicker.getValue();
        if (date == null) return;
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("profiles").eq("user_id", uid)
                        .update(String.format("{\"exam_date\":\"%s\"}", date));
                Platform.runLater(() -> showSaved(prefsSavedLabel, "✓  Exam date saved."));
            } catch (Exception e) { System.err.println("[ExamDate] " + e.getMessage()); }
        });
    }

    @FXML private void onDailyGoalChanged() {
        String val = dailyGoalCombo.getValue();
        if (val == null || val.isBlank()) return;
        int hours;
        try { hours = Integer.parseInt(val.split(" ")[0]); }
        catch (NumberFormatException ex) { return; }
        String uid = SessionManager.getInstance().getUserId();
        final int h = hours;
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("profiles").eq("user_id", uid)
                        .update(String.format("{\"target_hours\":%.1f}", (double) h));
                String today = LocalDate.now().toString();
                JsonNode goals = SupabaseClient.getInstance().from("daily_goals")
                        .select("id").eq("user_id", uid).eq("date", today).limit(1).execute();
                if (goals.isArray() && goals.size() > 0)
                    SupabaseClient.getInstance().from("daily_goals")
                            .eq("user_id", uid).eq("date", today)
                            .update(String.format("{\"target_hours\":%.1f}", (double) h));
                Platform.runLater(() -> showSaved(prefsSavedLabel, "✓  Daily goal updated."));
            } catch (Exception e) { System.err.println("[DailyGoal] " + e.getMessage()); }
        });
    }

    private void buildSubjectPills() {
        subjectPillsBox.getChildren().clear();
        for (String subj : ALL_SUBJECTS) {
            boolean on = activePills.contains(subj);
            Button pill = new Button(subj + (on ? "  ×" : "  +"));
            pill.setStyle(on
                    ? "-fx-background-color:#0d7ff2; -fx-background-radius:50; " +
                    "-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:600; " +
                    "-fx-padding:7 16; -fx-cursor:hand;"
                    : "-fx-background-color:#f1f5f9; -fx-background-radius:50; " +
                    "-fx-border-color:#e2e8f0; -fx-border-width:1; " +
                    "-fx-text-fill:#334155; -fx-font-size:12px; -fx-font-weight:600; " +
                    "-fx-padding:7 16; -fx-cursor:hand;");
            pill.setOnAction(e -> {
                if (activePills.contains(subj)) activePills.remove(subj);
                else                            activePills.add(subj);
                buildSubjectPills();
                saveSubjectPriority();
            });
            subjectPillsBox.getChildren().add(pill);
        }
    }

    private void saveSubjectPriority() {
        String uid = SessionManager.getInstance().getUserId();
        StringBuilder sb = new StringBuilder("[");
        Iterator<String> it = activePills.iterator();
        while (it.hasNext()) {
            sb.append("\"").append(it.next()).append("\"");
            if (it.hasNext()) sb.append(",");
        }
        sb.append("]");
        String jsonArray = sb.toString();
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("profiles").eq("user_id", uid)
                        .update(String.format("{\"subject_priority\":%s}", jsonArray));
                Platform.runLater(() ->
                        showSaved(prefsSavedLabel, "✓  Subject priority saved."));
            } catch (Exception e) { System.err.println("[SubjectPriority] " + e.getMessage()); }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  8.4  NOTIFICATIONS
    // ════════════════════════════════════════════════════════════
    @FXML private void onNotifChanged() {
        String uid  = SessionManager.getInstance().getUserId();
        String body = String.format(
                "{\"notification_prefs\":{\"email\":%b,\"push\":%b," +
                        "\"reminders\":%b,\"weekly\":%b}}",
                emailToggle.isSelected(), pushToggle.isSelected(),
                remindersToggle.isSelected(), weeklyToggle.isSelected());
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("profiles")
                        .eq("user_id", uid).update(body);
            } catch (Exception e) { System.err.println("[Notif save] " + e.getMessage()); }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  8.5  APPEARANCE
    // ════════════════════════════════════════════════════════════
    @FXML private void onThemeLight()  { applyTheme("light");  }
    @FXML private void onThemeDark()   { applyTheme("dark");   }
    @FXML private void onThemeSystem() { applyTheme("system"); }

    private void applyTheme(String theme) {
        activeTheme = theme;
        themeLight .setStyle(theme.equals("light")  ? THEME_ON : THEME_OFF);
        themeDark  .setStyle(theme.equals("dark")   ? THEME_ON : THEME_OFF);
        themeSystem.setStyle(theme.equals("system") ? THEME_ON : THEME_OFF);
        setThemeLabelColor(themeLight,  theme.equals("light"));
        setThemeLabelColor(themeDark,   theme.equals("dark"));
        setThemeLabelColor(themeSystem, theme.equals("system"));
        SessionManager.getInstance().setTheme(theme);
    }

    private void setThemeLabelColor(VBox box, boolean active) {
        if (box.getChildren().size() >= 2 &&
                box.getChildren().get(1) instanceof Label lbl)
            lbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:"
                    + (active ? "#0f172a" : "#64748b") + ";");
    }

    // FIX 1 — onFontSizeChanged kept for FXML compatibility (live update
    // already handled by the listener in initialize())
    @FXML private void onFontSizeChanged() {
        int size = (int) fontSizeSlider.getValue();
        fontSizeLabel.setText(size + "px" + (size == 14 ? " (Default)" : ""));
        if (mainScroll.getScene() != null)
            mainScroll.getScene().getRoot().setStyle("-fx-font-size:" + size + "px;");
    }

    // ════════════════════════════════════════════════════════════
    //  8.6  SECURITY & PRIVACY
    // ════════════════════════════════════════════════════════════
    @FXML private void onChangePassword() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Change Password");

        VBox content = new VBox(14);
        content.setStyle("-fx-padding:28; -fx-background-color:#f5f7f8;");
        content.setPrefWidth(400);

        Label title    = new Label("Change Your Password");
        title.setStyle("-fx-font-size:17px; -fx-font-weight:900; -fx-text-fill:#0f172a;");
        Label subtitle = new Label("Your new password must be at least 8 characters.");
        subtitle.setStyle("-fx-font-size:12px; -fx-text-fill:#64748b;");

        PasswordField currentPw = styledPassword("Current password");
        PasswordField newPw     = styledPassword("New password (min 8 characters)");
        PasswordField confirmPw = styledPassword("Confirm new password");
        Label errLabel = new Label("");
        errLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#ef4444;");
        errLabel.setWrapText(true);

        content.getChildren().addAll(
                title, subtitle,
                fieldLabel("Current Password"), currentPw,
                fieldLabel("New Password"),     newPw,
                fieldLabel("Confirm Password"), confirmPw,
                errLabel);

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setStyle("-fx-background-color:#f5f7f8;");

        ButtonType saveBtn = new ButtonType("Update Password", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        // FIX 3 — style buttons after dialog is shown (not in Platform.runLater)
        dlg.setOnShown(ev -> {
            Button okBtn = (Button) dlg.getDialogPane().lookupButton(saveBtn);
            if (okBtn != null)
                okBtn.setStyle("-fx-background-color:#0d7ff2; -fx-text-fill:white; " +
                        "-fx-font-weight:bold; -fx-background-radius:8; " +
                        "-fx-padding:8 18;");
            Button cancelBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (cancelBtn != null)
                cancelBtn.setStyle("-fx-background-color:#f1f5f9; -fx-text-fill:#334155; " +
                        "-fx-font-weight:600; -fx-background-radius:8; " +
                        "-fx-padding:8 16;");
        });

        dlg.showAndWait().ifPresent(bt -> {
            if (bt != saveBtn) return;

            String current = currentPw.getText();
            String n       = newPw.getText();
            String conf    = confirmPw.getText();

            if (current.isBlank()) { showError("Current password is required."); return; }
            if (n.length() < 8)    { showError("New password must be at least 8 characters."); return; }
            if (!n.equals(conf))   { showError("Passwords do not match."); return; }

            // FIX 2 — Supabase Auth uses PATCH not PUT
            Thread.ofVirtual().start(() -> {
                try {
                    String token = SessionManager.getInstance().getAccessToken();
                    String url   = getSupabaseUrl() + "/auth/v1/user";
                    String body  = String.format("{\"password\":\"%s\"}",
                            n.replace("\\", "\\\\").replace("\"", "\\\""));

                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(url))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + token)
                            .header("apikey", getAnonKey())
                            // FIX 2 — PATCH (not PUT)
                            .method("PATCH",
                                    java.net.http.HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    java.net.http.HttpClient client =
                            java.net.http.HttpClient.newHttpClient();
                    java.net.http.HttpResponse<String> resp =
                            client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

                    if (resp.statusCode() == 200) {
                        Platform.runLater(() -> showInfo("Password Updated",
                                "Your password has been changed successfully.\n" +
                                        "Use your new password next time you log in."));
                    } else {
                        System.err.println("[Password] Response: " + resp.body());
                        Platform.runLater(() -> showError(
                                "Could not update password (status " + resp.statusCode() +
                                        ").\nMake sure your current password is correct."));
                    }
                } catch (Exception e) {
                    System.err.println("[Password update] " + e.getMessage());
                    Platform.runLater(() ->
                            showError("Failed to update password: " + e.getMessage()));
                }
            });
        });
    }

    @FXML private void onTwoFactor() {
        showInfo("Two-Factor Authentication",
                "2FA setup is coming in a future update!\n" +
                        "This will support authenticator apps and SMS verification.");
    }

    @FXML private void onPrivacyPolicy() {
        try { Desktop.getDesktop().browse(new URI("https://www.anthropic.com/privacy")); }
        catch (Exception e) { System.err.println("[Privacy] " + e.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════
    //  8.7  DANGER ZONE — DELETE ACCOUNT
    // ════════════════════════════════════════════════════════════
    @FXML private void onDeleteAccount() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Delete Account");

        VBox content = new VBox(14);
        content.setStyle("-fx-padding:28; -fx-background-color:#fff5f5;");
        content.setPrefWidth(440);

        Label heading = new Label("⚠  Are you absolutely sure?");
        heading.setStyle("-fx-font-size:17px; -fx-font-weight:900; -fx-text-fill:#dc2626;");

        Label warning = new Label(
                "Deleting your account is permanent and irreversible.\n" +
                        "All of the following will be permanently lost:\n\n" +
                        "• All question attempts and accuracy history\n" +
                        "• Study sessions and progress data\n" +
                        "• Achievements and XP\n" +
                        "• Upcoming tests and scheduled sessions\n\n" +
                        "There is no way to recover this data.");
        warning.setStyle("-fx-font-size:13px; -fx-text-fill:#475569;");
        warning.setWrapText(true);

        Label instruction = new Label("Type  DELETE  to confirm:");
        instruction.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#334155;");

        TextField confirmField = new TextField();
        confirmField.setPromptText("Type DELETE here");
        confirmField.setStyle(
                "-fx-background-color:#ffffff; -fx-border-color:#fca5a5; " +
                        "-fx-border-width:2; -fx-border-radius:8; -fx-background-radius:8; " +
                        "-fx-font-size:14px; -fx-padding:10 14;");

        // Live border feedback: turns red when text isn't "DELETE"
        confirmField.textProperty().addListener((obs, ov, nv) -> {
            if ("DELETE".equals(nv)) {
                confirmField.setStyle(
                        "-fx-background-color:#ffffff; -fx-border-color:#22c55e; " +
                                "-fx-border-width:2; -fx-border-radius:8; -fx-background-radius:8; " +
                                "-fx-font-size:14px; -fx-padding:10 14;");
            } else {
                confirmField.setStyle(
                        "-fx-background-color:#ffffff; -fx-border-color:#fca5a5; " +
                                "-fx-border-width:2; -fx-border-radius:8; -fx-background-radius:8; " +
                                "-fx-font-size:14px; -fx-padding:10 14;");
            }
        });

        content.getChildren().addAll(heading, warning, instruction, confirmField);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setStyle("-fx-background-color:#fff5f5;");

        ButtonType deleteBtn = new ButtonType("Delete Forever", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(deleteBtn, ButtonType.CANCEL);

        // FIX 3 — style buttons in setOnShown (guaranteed to find them)
        dlg.setOnShown(ev -> {
            Button delBtnNode = (Button) dlg.getDialogPane().lookupButton(deleteBtn);
            if (delBtnNode != null)
                delBtnNode.setStyle(
                        "-fx-background-color:#dc2626; -fx-text-fill:white; " +
                                "-fx-font-weight:bold; -fx-background-radius:8; -fx-padding:8 18;");
            Button cancelNode = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (cancelNode != null)
                cancelNode.setStyle(
                        "-fx-background-color:#f1f5f9; -fx-text-fill:#334155; " +
                                "-fx-font-weight:600; -fx-background-radius:8; -fx-padding:8 16;");
        });

        dlg.showAndWait().ifPresent(bt -> {
            if (bt != deleteBtn) return;

            if (!"DELETE".equals(confirmField.getText())) {
                showError("You must type DELETE (in capitals) to confirm deletion.");
                return;
            }

            Thread.ofVirtual().start(() -> {
                try { SupabaseClient.getInstance().signOut(); }
                catch (Exception e) { System.err.println("[Delete signOut] " + e.getMessage()); }
                SessionManager.getInstance().clear();
                Platform.runLater(() -> {
                    try {
                        Parent root = FXMLLoader.load(
                                getClass().getResource("/LoginView.fxml"));
                        Stage stage = (Stage) mainScroll.getScene().getWindow();
                        stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
                    } catch (Exception ex) {
                        System.err.println("[Delete nav] " + ex.getMessage());
                    }
                });
            });
        });
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════
    private void showSaved(Label label, String msg) {
        label.setOpacity(1.0);
        label.setStyle("-fx-font-size:12px; -fx-text-fill:#22c55e;");
        label.setText(msg);
        FadeTransition ft = new FadeTransition(Duration.seconds(2.5), label);
        ft.setDelay(Duration.seconds(1.5));
        ft.setFromValue(1.0);
        ft.setToValue(0.0);
        ft.setOnFinished(e -> label.setText(""));
        ft.play();
    }

    private void showInfo(String header, String body) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(header); a.setHeaderText(header); a.setContentText(body);
        a.showAndWait();
    }

    private void showError(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error"); a.setHeaderText(msg);
        a.showAndWait();
    }

    private Label fieldLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-font-weight:600; -fx-text-fill:#334155;");
        return l;
    }

    private PasswordField styledPassword(String prompt) {
        PasswordField pf = new PasswordField();
        pf.setPromptText(prompt);
        pf.setStyle("-fx-background-color:#ffffff; -fx-border-color:#e2e8f0; " +
                "-fx-border-width:1; -fx-border-radius:8; -fx-background-radius:8; " +
                "-fx-font-size:13px; -fx-padding:10 14;");
        return pf;
    }

    private String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[parts.length-1].charAt(0)).toUpperCase();
        return name.substring(0, Math.min(2, name.length())).toUpperCase();
    }

    private String getSupabaseUrl() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.load(getClass().getResourceAsStream("/config.properties"));
            return p.getProperty("SUPABASE_URL", "");
        } catch (Exception e) { return ""; }
    }

    private String getAnonKey() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.load(getClass().getResourceAsStream("/config.properties"));
            return p.getProperty("SUPABASE_ANON_KEY", "");
        } catch (Exception e) { return ""; }
    }
}