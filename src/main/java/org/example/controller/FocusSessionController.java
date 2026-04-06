package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FocusSessionController implements Initializable {

    @FXML private ComboBox<String> subjectCombo;
    @FXML private Button           btn25, btn45, btn60;
    @FXML private Label            timerLabel;
    @FXML private Label            timerStatusLabel;
    @FXML private Button           startBtn;
    @FXML private Button           endBtn;
    @FXML private Label            savedLabel;

    private int           totalSeconds   = 25 * 60;
    private int           remainingSeconds;
    private Timeline      timer;
    private boolean       running        = false;
    private LocalDateTime sessionStart;
    private Runnable      onSessionEnd;

    // Subject ID map (must match seed data)
    private static final Map<String, String> SUBJECT_IDS = new LinkedHashMap<>();
    static {
        SUBJECT_IDS.put("Physics",   "11111111-0000-0000-0000-000000000001");
        SUBJECT_IDS.put("Chemistry", "11111111-0000-0000-0000-000000000002");
        SUBJECT_IDS.put("Math Advanced", "11111111-0000-0000-0000-000000000003");
        SUBJECT_IDS.put("Biology",   "11111111-0000-0000-0000-000000000004");
    }

    public void setOnSessionEnd(Runnable callback) { this.onSessionEnd = callback; }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        subjectCombo.getItems().addAll(SUBJECT_IDS.keySet());
        subjectCombo.setValue("Physics");
        remainingSeconds = totalSeconds;
        updateTimerLabel();
    }

    // ── Duration buttons ──────────────────────────────────────
    @FXML private void onDuration25() { setDuration(25); highlightDuration(btn25); }
    @FXML private void onDuration45() { setDuration(45); highlightDuration(btn45); }
    @FXML private void onDuration60() { setDuration(60); highlightDuration(btn60); }

    private void setDuration(int mins) {
        if (running) return;
        totalSeconds     = mins * 60;
        remainingSeconds = totalSeconds;
        updateTimerLabel();
    }

    private void highlightDuration(Button active) {
        String on  = "-fx-background-color:#0d7ff2; -fx-background-radius:8; " +
                "-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; " +
                "-fx-padding:7 18; -fx-cursor:hand;";
        String off = "-fx-background-color:#f1f5f9; -fx-background-radius:8; " +
                "-fx-text-fill:#475569; -fx-font-size:12px; -fx-font-weight:bold; " +
                "-fx-padding:7 18; -fx-cursor:hand;";
        btn25.setStyle(off); btn45.setStyle(off); btn60.setStyle(off);
        active.setStyle(on);
    }

    // ── Start / Pause ─────────────────────────────────────────
    @FXML private void onStart() {
        if (!running) {
            startSession();
        } else {
            pauseSession();
        }
    }

    private void startSession() {
        if (sessionStart == null) sessionStart = LocalDateTime.now();
        running = true;
        startBtn.setText("⏸  Pause");
        endBtn.setDisable(false);
        timerStatusLabel.setText("Session in progress...");

        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateTimerLabel();
            if (remainingSeconds <= 0) {
                timer.stop();
                running = false;
                timerStatusLabel.setText("Session complete! 🎉");
                startBtn.setText("▶  Start");
                saveSession(true);
            }
        }));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void pauseSession() {
        running = false;
        timer.stop();
        startBtn.setText("▶  Resume");
        timerStatusLabel.setText("Paused — click Resume to continue.");
    }

    // ── End Session ───────────────────────────────────────────
    @FXML private void onEnd() {
        if (timer != null) timer.stop();
        running = false;
        startBtn.setText("▶  Start");
        endBtn.setDisable(true);
        timerStatusLabel.setText("Session ended.");
        saveSession(false);
    }

    // ── Save to Supabase ──────────────────────────────────────
    private void saveSession(boolean completed) {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null || sessionStart == null) {
            closeModal();
            return;
        }

        String subject     = subjectCombo.getValue();
        String subjectId   = SUBJECT_IDS.getOrDefault(subject, null);
        int    elapsedMins = (totalSeconds - remainingSeconds) / 60;
        if (elapsedMins < 1) elapsedMins = 1;

        LocalDateTime endTime = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        String startStr = sessionStart.format(fmt);
        String endStr   = endTime.format(fmt);

        final int mins = elapsedMins;

        Thread.ofVirtual().start(() -> {
            try {
                // INSERT study session
                String sessionBody = String.format(
                        "{\"user_id\":\"%s\",\"subject_id\":%s," +
                                "\"started_at\":\"%s\",\"ended_at\":\"%s\",\"duration_mins\":%d}",
                        uid,
                        subjectId != null ? "\"" + subjectId + "\"" : "null",
                        startStr, endStr, mins);
                SupabaseClient.getInstance().from("study_sessions").insert(sessionBody);

                // UPDATE daily_goals.completed_hours
                String today = java.time.LocalDate.now().toString();
                JsonNode goals = SupabaseClient.getInstance()
                        .from("daily_goals")
                        .select("completed_hours")
                        .eq("user_id", uid)
                        .eq("date", today)
                        .limit(1)
                        .execute();

                if (goals.isArray() && goals.size() > 0) {
                    double current = goals.get(0).path("completed_hours").asDouble(0);
                    double updated = current + (mins / 60.0);
                    String updateBody = String.format("{\"completed_hours\":%.2f}", updated);
                    SupabaseClient.getInstance()
                            .from("daily_goals")
                            .eq("user_id", uid)
                            .eq("date", today)
                            .update(updateBody);
                }

                Platform.runLater(() -> {
                    savedLabel.setText("✓  " + mins + " min session saved! Great work.");
                    if (onSessionEnd != null) onSessionEnd.run();
                    // Auto-close after 1.5 seconds
                    new Timeline(new KeyFrame(Duration.millis(1500), ev -> closeModal())).play();
                });

            } catch (Exception e) {
                System.err.println("[FocusSession] Save error: " + e.getMessage());
                Platform.runLater(() -> {
                    savedLabel.setText("Session done! (offline — not saved)");
                    new Timeline(new KeyFrame(Duration.millis(1500), ev -> closeModal())).play();
                });
            }
        });
    }

    private void updateTimerLabel() {
        int m = remainingSeconds / 60;
        int s = remainingSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d", m, s));

        // Colour red when < 60 seconds
        if (remainingSeconds < 60 && running)
            timerLabel.setStyle("-fx-font-size:52px; -fx-font-weight:900; -fx-text-fill:#ef4444;");
        else
            timerLabel.setStyle("-fx-font-size:52px; -fx-font-weight:900; -fx-text-fill:#0f172a;");
    }

    private void closeModal() {
        Stage stage = (Stage) timerLabel.getScene().getWindow();
        stage.close();
    }
}