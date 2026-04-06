package org.example.controller;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.util.*;

public class ScheduleExamController implements Initializable {

    @FXML private TextField titleField;
    @FXML private ComboBox<String> subjectCombo;
    @FXML private DatePicker datePicker;
    @FXML private TextArea   descField;
    @FXML private Label      errorLabel;
    @FXML private Button     saveBtn;

    private Runnable onSaved;

    private static final Map<String, String> SUBJECT_IDS = new LinkedHashMap<>();
    static {
        SUBJECT_IDS.put("Physics",        "11111111-0000-0000-0000-000000000001");
        SUBJECT_IDS.put("Chemistry",      "11111111-0000-0000-0000-000000000002");
        SUBJECT_IDS.put("Math Advanced",  "11111111-0000-0000-0000-000000000003");
        SUBJECT_IDS.put("Biology",        "11111111-0000-0000-0000-000000000004");
        SUBJECT_IDS.put("General",        null);
    }

    public void setOnSaved(Runnable callback) { this.onSaved = callback; }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        subjectCombo.getItems().addAll(SUBJECT_IDS.keySet());
        subjectCombo.setValue("Physics");
        datePicker.setValue(LocalDate.now().plusDays(7));
    }

    @FXML private void onCancel() {
        ((Stage) titleField.getScene().getWindow()).close();
    }

    @FXML private void onSave() {
        String title   = titleField.getText().trim();
        String subject = subjectCombo.getValue();
        LocalDate date = datePicker.getValue();
        String desc    = descField.getText().trim();

        // Validation
        if (title.isBlank())  { errorLabel.setText("Please enter an exam title."); return; }
        if (date == null)     { errorLabel.setText("Please select a date.");        return; }
        if (date.isBefore(LocalDate.now())) { errorLabel.setText("Date must be in the future."); return; }

        errorLabel.setText("");
        saveBtn.setDisable(true);
        saveBtn.setText("Saving...");

        String uid       = SessionManager.getInstance().getUserId();
        String subjectId = SUBJECT_IDS.get(subject);

        Thread.ofVirtual().start(() -> {
            try {
                String body = String.format(
                        "{\"user_id\":\"%s\",\"title\":\"%s\","
                                + "\"subject_id\":%s,"
                                + "\"test_date\":\"%s\","
                                + "\"description\":\"%s\"}",
                        uid,
                        title.replace("\"", "'"),
                        subjectId != null ? "\"" + subjectId + "\"" : "null",
                        date,
                        desc.replace("\"", "'")
                );

                SupabaseClient.getInstance().from("upcoming_tests").insert(body);

                javafx.application.Platform.runLater(() -> {
                    if (onSaved != null) onSaved.run();
                    ((Stage) titleField.getScene().getWindow()).close();
                });

            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> {
                    errorLabel.setText("Failed to save: " + e.getMessage());
                    saveBtn.setDisable(false);
                    saveBtn.setText("Save Exam");
                });
            }
        });
    }
}