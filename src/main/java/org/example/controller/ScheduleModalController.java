package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ScheduleModalController implements Initializable {

    @FXML private VBox scheduleBox;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        String uid = SessionManager.getInstance().getUserId();
        if (uid != null) Thread.ofVirtual().start(() -> loadSchedule(uid));
    }

    private void loadSchedule(String userId) {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("upcoming_tests")
                    .select("title,test_date,description,subject:subjects(name,color)")
                    .eq("user_id", userId)
                    .order("test_date", true)
                    .execute();

            List<javafx.scene.Node> nodes = new ArrayList<>();
            DateTimeFormatter display = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

            if (rows.isArray() && rows.size() > 0) {
                String[] colors = {"#0d7ff2","#059669","#d97706","#e11d48","#7c3aed"};
                int idx = 0;
                for (JsonNode row : rows) {
                    String title    = row.path("title").asText("Test");
                    String dateStr  = row.path("test_date").asText("").substring(0, 10);
                    String desc     = row.path("description").asText("");
                    String subName  = row.path("subject").path("name").asText("General");
                    LocalDate date  = LocalDate.parse(dateStr);
                    String fg       = colors[idx % colors.length];
                    boolean past    = date.isBefore(LocalDate.now());

                    HBox card = new HBox(16);
                    card.setAlignment(Pos.CENTER_LEFT);
                    card.setStyle("-fx-background-color:#ffffff; -fx-background-radius:12; " +
                            "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:12; " +
                            "-fx-padding:16 18;" + (past ? "-fx-opacity:0.55;" : ""));

                    // Left accent bar
                    VBox accent = new VBox();
                    accent.setStyle("-fx-background-color:" + fg + "; -fx-background-radius:4; " +
                            "-fx-min-width:4; -fx-pref-width:4; -fx-min-height:48;");

                    // Info
                    VBox info = new VBox(4);
                    HBox.setHgrow(info, Priority.ALWAYS);

                    Label titleLbl = new Label(title);
                    titleLbl.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");

                    Label dateLbl = new Label(date.format(display));
                    dateLbl.setStyle("-fx-font-size:12px; -fx-text-fill:" + fg + "; -fx-font-weight:600;");

                    Label subLbl = new Label(subName + (desc.isBlank() ? "" : "  •  " + desc));
                    subLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8;");

                    info.getChildren().addAll(titleLbl, dateLbl, subLbl);

                    Label badgeLbl = new Label(past ? "PAST" : daysUntil(date));
                    badgeLbl.setStyle("-fx-background-color:" + (past ? "#f1f5f9" : "#e8f3fe") + "; " +
                            "-fx-background-radius:50; -fx-text-fill:" + (past ? "#94a3b8" : fg) + "; " +
                            "-fx-font-size:10px; -fx-font-weight:bold; -fx-padding:4 10;");

                    card.getChildren().addAll(accent, info, badgeLbl);
                    nodes.add(card);
                    idx++;
                }
            } else {
                Label empty = new Label("No tests scheduled yet. Go back and book one!");
                empty.setStyle("-fx-font-size:13px; -fx-text-fill:#94a3b8;");
                nodes.add(empty);
            }

            Platform.runLater(() -> scheduleBox.getChildren().setAll(nodes));

        } catch (Exception e) {
            System.err.println("[ScheduleModal] " + e.getMessage());
            Platform.runLater(() -> {
                Label err = new Label("Could not load schedule: " + e.getMessage());
                err.setStyle("-fx-font-size:12px; -fx-text-fill:#ef4444;");
                scheduleBox.getChildren().setAll(err);
            });
        }
    }

    private String daysUntil(LocalDate date) {
        long days = date.toEpochDay() - LocalDate.now().toEpochDay();
        if (days == 0) return "TODAY";
        if (days == 1) return "TOMORROW";
        return "IN " + days + " DAYS";
    }

    @FXML private void onClose() {
        ((Stage) scheduleBox.getScene().getWindow()).close();
    }
}