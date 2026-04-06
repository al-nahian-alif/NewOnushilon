package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Consumer;

public class MockExamController implements Initializable {

    @FXML private ComboBox<String> subjectCombo;
    @FXML private Button btn1hr, btn2hr, btn3hr;
    @FXML private Label  infoLabel, errorLabel;

    private int selectedDuration = 60; // minutes
    private List<PracticeController.Subject> subjects;

    // Callback: (subjectId, subjectName, durationMins)
    @FunctionalInterface
    public interface ExamStartCallback {
        void start(String subjectId, String subjectName, int durationMins);
    }
    private ExamStartCallback onStart;

    // Subject record alias
    record Subject(String id, String name) {}
    private final java.util.Map<String, String> subjectIdMap = new java.util.LinkedHashMap<>();

    public void setSubjects(List<PracticeController.Subject> list) {
        this.subjects = list;
        subjectCombo.getItems().add("All Subjects");
        for (var s : list) {
            subjectCombo.getItems().add(s.icon() + "  " + s.name());
            subjectIdMap.put(s.icon() + "  " + s.name(), s.id());
        }
        subjectCombo.setValue("All Subjects");
    }

    public void setOnStart(ExamStartCallback cb) { this.onStart = cb; }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        subjectCombo.valueProperty().addListener((obs, ov, nv) -> updateInfo());
        updateInfo();
    }

    @FXML private void onDur1() { selectedDuration = 60;  updateDurButtons(btn1hr); }
    @FXML private void onDur2() { selectedDuration = 120; updateDurButtons(btn2hr); }
    @FXML private void onDur3() { selectedDuration = 180; updateDurButtons(btn3hr); }

    private void updateDurButtons(Button active) {
        String on  = "-fx-background-color:#0d7ff2; -fx-background-radius:10; " +
                "-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:bold; " +
                "-fx-padding:10 0; -fx-cursor:hand;";
        String off = "-fx-background-color:#f1f5f9; -fx-background-radius:10; " +
                "-fx-text-fill:#475569; -fx-font-size:13px; -fx-font-weight:bold; " +
                "-fx-padding:10 0; -fx-cursor:hand;";
        btn1hr.setStyle(off); btn2hr.setStyle(off); btn3hr.setStyle(off);
        active.setStyle(on);
        updateInfo();
    }

    private void updateInfo() {
        String sub  = subjectCombo.getValue() != null ? subjectCombo.getValue() : "All Subjects";
        String hrs  = selectedDuration == 60 ? "1 hour"
                : selectedDuration == 120 ? "2 hours" : "3 hours";
        infoLabel.setText(hrs + " exam  •  Questions from: " + sub + ".");
    }

    @FXML private void onStart() {
        String selected   = subjectCombo.getValue();
        String subjectId  = subjectIdMap.getOrDefault(selected, null);
        String subjectName = selected != null && selected.contains("  ")
                ? selected.substring(selected.indexOf("  ") + 2) : "All Subjects";

        close();
        if (onStart != null)
            onStart.start(subjectId, subjectName, selectedDuration);
    }

    @FXML private void onCancel() { close(); }

    private void close() {
        ((Stage) subjectCombo.getScene().getWindow()).close();
    }
}