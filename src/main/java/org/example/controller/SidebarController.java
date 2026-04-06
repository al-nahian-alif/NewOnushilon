package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.util.List;
import java.util.ResourceBundle;

/**
 * SidebarController — reusable across all pages via <fx:include>.
 *
 * Call from host controller:
 *   sidebarController.setActivePage("dashboard");
 *
 * Valid keys: "dashboard" | "subjects" | "practice" | "performance" | "settings"
 */
public class SidebarController implements Initializable {

    // ── Nav buttons ───────────────────────────────────────────
    @FXML private Button btnDashboard;
    @FXML private Button btnSubjects;
    @FXML private Button btnPractice;
    @FXML private Button btnPerformance;
    @FXML private Button btnSettings;

    // ── Daily goal ────────────────────────────────────────────
    @FXML private HBox  dailyGoalBar;
    @FXML private Label dailyGoalLabel;

    // ── Bottom profile card (8.8) ─────────────────────────────
    @FXML private StackPane sidebarAvatarPane;
    @FXML private Label     sidebarInitials;
    @FXML private Label     sidebarName;

    // ── Styles ────────────────────────────────────────────────
    private static final String ACTIVE =
            "-fx-background-color:#e8f3fe; -fx-background-radius:8; " +
                    "-fx-text-fill:#0d7ff2; -fx-font-size:13px; -fx-font-weight:bold; " +
                    "-fx-padding:9 14; -fx-alignment:CENTER_LEFT; -fx-cursor:hand;";
    private static final String INACTIVE =
            "-fx-background-color:transparent; -fx-background-radius:8; " +
                    "-fx-text-fill:#475569; -fx-font-size:13px; -fx-font-weight:500; " +
                    "-fx-padding:9 14; -fx-alignment:CENTER_LEFT; -fx-cursor:hand;";

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 8.8 — populate bottom profile card from SessionManager (already in memory)
        refreshProfileCard();

        // Load daily goal from DB
        String uid = SessionManager.getInstance().getUserId();
        if (uid != null)
            Thread.ofVirtual().start(() -> loadDailyGoal(uid));
    }

    // ════════════════════════════════════════════════════════════
    //  8.8  BOTTOM PROFILE CARD
    // ════════════════════════════════════════════════════════════

    /** Populate initials + name from SessionManager (no extra DB call needed). */
    public void refreshProfileCard() {
        String name     = SessionManager.getInstance().getUserName();
        String initials = SessionManager.getInstance().getInitials();
        sidebarName.setText(name != null && !name.isBlank() ? name : "Student");
        sidebarInitials.setText(initials);
    }

    // ── Profile card hover effect ────────────────────────────
    @FXML private void onProfileCardHover(javafx.scene.input.MouseEvent e) {
        sidebarAvatarPane.setStyle(
                "-fx-background-color:#bfdbfe; -fx-background-radius:50; " +
                        "-fx-min-width:40; -fx-min-height:40; -fx-pref-width:40; -fx-pref-height:40;");
    }
    @FXML private void onProfileCardExit(javafx.scene.input.MouseEvent e) {
        sidebarAvatarPane.setStyle(
                "-fx-background-color:#dbeafe; -fx-background-radius:50; " +
                        "-fx-min-width:40; -fx-min-height:40; -fx-pref-width:40; -fx-pref-height:40;");
    }

    // ════════════════════════════════════════════════════════════
    //  DAILY GOAL BAR
    // ════════════════════════════════════════════════════════════
    private void loadDailyGoal(String uid) {
        try {
            String today = LocalDate.now().toString();
            JsonNode rows = SupabaseClient.getInstance()
                    .from("daily_goals")
                    .select("completed_questions,target_questions")
                    .eq("user_id", uid)
                    .eq("date", today)
                    .limit(1)
                    .execute();

            int    done   = 0, target = 10;
            double ratio  = 0;
            String text   = "No goal set for today";

            if (rows.isArray() && rows.size() > 0) {
                done   = rows.get(0).path("completed_questions").asInt(0);
                target = rows.get(0).path("target_questions").asInt(10);
                ratio  = target > 0 ? Math.min((double) done / target, 1.0) : 0;
                text   = done + "/" + target + " questions today";
            }

            final double r  = ratio;
            final String t  = text;
            Platform.runLater(() -> {
                dailyGoalLabel.setText(t);
                // Animate bar once parent width is known
                dailyGoalBar.getParent().layoutBoundsProperty()
                        .addListener((obs, ov, nv) -> {
                            double pw = nv.getWidth();
                            if (pw > 0 && dailyGoalBar.getPrefWidth() < 1) {
                                Timeline tl = new Timeline(
                                        new KeyFrame(Duration.ZERO,
                                                new KeyValue(dailyGoalBar.prefWidthProperty(), 0)),
                                        new KeyFrame(Duration.millis(600),
                                                new KeyValue(dailyGoalBar.prefWidthProperty(), pw * r))
                                );
                                tl.play();
                            }
                        });
            });

        } catch (Exception e) {
            System.err.println("[Sidebar goal] " + e.getMessage());
            Platform.runLater(() -> dailyGoalLabel.setText("Could not load goal"));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  ACTIVE PAGE HIGHLIGHT
    // ════════════════════════════════════════════════════════════
    public void setActivePage(String page) {
        List<Button> all = List.of(
                btnDashboard, btnSubjects, btnPractice, btnPerformance, btnSettings);
        all.forEach(b -> b.setStyle(INACTIVE));

        switch (page) {
            case "dashboard"   -> btnDashboard  .setStyle(ACTIVE);
            case "subjects"    -> btnSubjects   .setStyle(ACTIVE);
            case "practice"    -> btnPractice   .setStyle(ACTIVE);
            case "performance",
                 "tests"       -> btnPerformance.setStyle(ACTIVE);
            case "settings",
                 "profile"     -> btnSettings   .setStyle(ACTIVE);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  NAV ACTIONS
    // ════════════════════════════════════════════════════════════
    @FXML private void onDashboard()   { go("/DashboardView.fxml");   }
    @FXML private void onSubjects()    { go("/SubjectsView.fxml");    }
    @FXML private void onPractice()    { go("/PracticeView.fxml");    }
    @FXML private void onPerformance() { go("/PerformanceView.fxml"); }
    @FXML private void onSettings()    { go("/SettingsView.fxml");    }

    private void go(String fxmlPath) {
        try {
            URL url = getClass().getResource(fxmlPath);
            if (url == null) { System.err.println("[Sidebar] Not found: " + fxmlPath); return; }
            Parent root = FXMLLoader.load(url);
            Stage  stage = (Stage) btnDashboard.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            System.err.println("[Sidebar nav] " + e.getMessage());
            e.printStackTrace();
        }
    }
}