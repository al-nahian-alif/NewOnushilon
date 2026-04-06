package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

public class QuestionController implements Initializable {

    // --- 1. UI CONNECTIONS (Must match fx:id in SceneBuilder) ---
    @FXML private Label questionLabel;
    @FXML private RadioButton option1, option2, option3, option4, option5;
    @FXML private Button submitButton;

    // Results & Explanation Section
    @FXML private HBox resultBar;        // The purple bar
    @FXML private VBox explanationContainer; // Holds text explanation
    @FXML private Label explanationLabel;
    @FXML private VBox videoContainer;   // Holds video

    // Pace Stats (In the purple bar)
    @FXML private Label yourPaceLabel;
    @FXML private Label othersPaceLabel;

    // SRS Footer (The floating buttons)
    @FXML private HBox srsBar;

    @FXML private Label subjectLabel;
    @FXML private Label chapterLabel;

    @FXML
    private VBox bottomContentArea;

    // --- 2. LOGIC VARIABLES ---
    private ToggleGroup optionsGroup;
    private QuestionModel.Question currentQuestion;
    private String currentSubject;

    List<RadioButton> radioButtons;
    private int currentCorrectIndex = -1;

    @FXML private Label resultLabel;

    // ---------- implementing the next feature --------------

    // 1. The List of all questions for this session
    private List<QuestionModel.Question> questionList;

    // 2. The current position (starts at 0)
    private int currentQuestionIndex = 0;

    @FXML
    private Button nextButton; // Link this in Scene Builder
    @FXML
    private Label questionCountLabel; // The "Question 1 of 61" label

    @FXML
    private MediaView explanationMediaView;

    private MediaPlayer mediaPlayer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupRadioButtons();

        // Start in "Question Mode" (Hide answers)
        setMode(false);

        radioButtons = List.of(option1, option2, option3, option4, option5);

        explanationMediaView.setOnMouseClicked(event -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.play();
                }
            }
        });
    }

    private void setMode(boolean showResults) {
        setVisible(bottomContentArea, showResults);

        setVisible(srsBar, showResults);
    }

    /**
     * Helper to safely set visible + managed state without null crashes.
     */
    private void setVisible(javafx.scene.Node node, boolean isVisible) {
        if (node != null) {
            node.setVisible(isVisible);
            node.setManaged(isVisible); // managed=false means it takes 0 space
        }
    }

    private void setupRadioButtons() {
        optionsGroup = new ToggleGroup();
        option1.setToggleGroup(optionsGroup);
        option2.setToggleGroup(optionsGroup);
        option3.setToggleGroup(optionsGroup);
        option4.setToggleGroup(optionsGroup);
        option5.setToggleGroup(optionsGroup);
    }

    private void loadDataFromFile(String subject) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            String fileName = "";

            switch (subject.toLowerCase()) {
                case "physics":
                    fileName = "/data/physics.json";
                    break;
                case "math":
                    fileName = "/data/math.json";
                    break;
                case "chemistry":
                    fileName = "/data/chemistry.json";
                    break;
                default:
                    System.out.println("Unknown subject: " + subject);
                    return;
            }

            InputStream is = getClass().getResourceAsStream(fileName);

            if (is == null) {
                questionLabel.setText("Error: File not found for " + subject);
                return;
            }

            QuestionModel database = mapper.readValue(is, QuestionModel.class);

            if (subjectLabel != null) {
                subjectLabel.setText(subject);
            }

            if (database.chapters != null && !database.chapters.isEmpty()) {
                QuestionModel.Chapter firstChapter = database.chapters.get(0);

                // SAVE THE LIST!
                this.questionList = firstChapter.questions;
                this.currentQuestionIndex = 0; // Reset to start

                if (!questionList.isEmpty()) {
                    displayQuestion(questionList.get(0));
                    updateProgressLabel(); // Helper to show "Question 1 of X"
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            questionLabel.setText("Error loading data: " + e.getMessage());
        }
    }

    private void updateProgressLabel() {
        int current = currentQuestionIndex + 1;
        int total = questionList.size();
        questionCountLabel.setText("Question " + current + " of " + total);
    }

    private void displayQuestion(QuestionModel.Question q) {
        nextButton.setDisable(true);

        this.currentQuestion = q;

        questionLabel.setText(q.content.questionText);

        // Setup Options
        List<String> opts = q.content.options;

        for (int i = 0; i < radioButtons.size(); i++) {
            RadioButton btn = radioButtons.get(i);
            if (i < opts.size()) {
                btn.setText(opts.get(i));
                btn.setVisible(true);
                btn.setDisable(false);
                btn.setSelected(false);
            } else {
                btn.setVisible(false);
            }
        }

        // Pre-load the data (but keep hidden)
        if (othersPaceLabel != null) othersPaceLabel.setText(q.meta.avgTimeSec + " sec");

        if (q.explanation != null && q.explanation.text != null) {
            // This replaces literal "\n" characters with ACTUAL line breaks
            String formattedText = q.explanation.text.replace("\\n", "\n");

            explanationLabel.setText(formattedText);
        }

        if (explanationLabel != null) explanationLabel.setText(q.explanation.text);
//        loadYoutubeVideo();
        loadLocalVideo("test_video.mp4");
    }

    @FXML
    protected void onSubmitClick() {
//        playVideo();
        nextButton.setDisable(false);

        // Lock inputs
        option1.setDisable(true);
        option2.setDisable(true);
        option3.setDisable(true);
        option4.setDisable(true);
        option5.setDisable(true);

        // 1. Find which button is selected
        int selectedIndex = -1;
        for (int i = 0; i < radioButtons.size(); i++) {
            if (radioButtons.get(i).isSelected()) {
                selectedIndex = i;
                break;
            }
        }

        // 2. Handle "No Selection" case
        if (selectedIndex == -1) {
            // Optional: Show an alert saying "Please select an answer"
            System.out.println("No answer selected!");
            return;
        }

        // 3. Compare with Correct Answer
        // 'currentQuestion' is the object you loaded from JSON
        int correctIndex = currentQuestion.content.correctOptionIndex;

        boolean isCorrect = (selectedIndex == correctIndex);

        // 4. Update the UI
        updateResultUI(isCorrect);

        // 5. Reveal the Explanations (This is your existing function!)
        setMode(true);

        // 6. Disable Submit Button so they can't click it again
        submitButton.setDisable(true);

        // --- EMOJI LOGIC ---

        // Case A: User is Correct
        if (isCorrect) {
            RadioButton selectedButton = radioButtons.get(selectedIndex);
            selectedButton.setText(selectedButton.getText() + " ✅");
            selectedButton.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;"); // Optional: Make text Green
        }
        // Case B: User is Wrong
        else {
            // Mark the WRONG selection with Cross
            RadioButton selectedButton = radioButtons.get(selectedIndex);
            selectedButton.setText(selectedButton.getText() + " ❌");
            selectedButton.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); // Optional: Make text Red

            // Mark the CORRECT answer with Tick
            RadioButton correctButton = radioButtons.get(correctIndex);
            correctButton.setText(correctButton.getText() + " ✅");
            correctButton.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;"); // Optional: Make text Green
        }
    }

    @FXML
    private void handleNextQuestionAction(ActionEvent event) {
        // 1. Check if there are more questions
        if (currentQuestionIndex < questionList.size() - 1) {

            // 2. Move to next index
            currentQuestionIndex++;

            // 3. Reset the UI (Clear colors, emojis, selection)
            resetUI();

            // 4. Load the new question
            displayQuestion(questionList.get(currentQuestionIndex));

            // 5. Update "Question X of Y" label
            updateProgressLabel();

        } else {
            // --- END OF QUIZ ---
            // You can show an alert or navigate to a score screen here
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Quiz Finished");
            alert.setHeaderText("Great job!");
            alert.setContentText("You have completed all questions in this chapter.");
            alert.showAndWait();
        }

        // Optional: Change button text to "Finish" on the last question
        if (currentQuestionIndex == questionList.size() - 1) {
            nextButton.setText("Finish Practice");
        } else {
            nextButton.setText("Next Question");
        }
    }

    private void resetUI() {
        // 1. Hide the purple bar and explanations
        setMode(false);
        stopVideo();

        // 2. Re-enable the Submit button
        submitButton.setDisable(false);

        // 3. Reset the RadioButtons (Crucial!)
        ToggleGroup group = option1.getToggleGroup();
        group.selectToggle(null); // Deselect everything

        for (RadioButton rb : radioButtons) {
            rb.setSelected(false);
            rb.setStyle("-fx-text-fill: black;"); // Reset text color to black
            // Note: The text itself (with emojis) gets overwritten
            // automatically when displayQuestion() sets the new text.
        }

        // 4. Reset Result Bar Color (Optional cleanup)
        resultBar.setStyle("");
        resultLabel.setText("");
    }

    private void updateResultUI(boolean isCorrect) {
        if (isCorrect) {
            // CORRECT STATE
            resultLabel.setText("Correct");
            // Set background to Green
            resultBar.setStyle("-fx-background-color: #2ecc71;");
        } else {
            // INCORRECT STATE
            resultLabel.setText("Incorrect");
            // Set background to Red
            resultBar.setStyle("-fx-background-color: #e74c3c;");
        }
    }

    public void startPracticeSession(String subject) {
        this.currentSubject = subject;
        System.out.println("Starting practice for: " + subject); // Debug check

        // 1. Reset the UI to "Question Mode" (Hide results)
        setMode(false);

        // 2. Load the actual questions for this subject
        loadDataFromFile(subject);
    }

    private void loadLocalVideo(String videoFileName) {
        if (explanationMediaView == null) return;

        try {
            // 1. Get the file path from resources
            // Note: Use a leading slash "/" to find files in the resources root
            String path = getClass().getResource("/videos/" + videoFileName).toExternalForm();

            // 2. Create Media and Player
            Media media = new Media(path);

            // If a previous player exists, stop and dispose of it to free memory!
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }

            mediaPlayer = new MediaPlayer(media);

            // 3. Attach to the View
            explanationMediaView.setMediaPlayer(mediaPlayer);

            // 4. (Optional) Auto-play when loaded?
            // mediaPlayer.setAutoPlay(true);

        } catch (Exception e) {
            System.out.println("Error loading video: " + e.getMessage());
        }
    }

    // Call this from your "Submit" button action
    public void playVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    // Call this in "resetUI" to stop audio when moving to the next question
    public void stopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }
}

//    private void loadYoutubeVideo() {
//        if (videoWebView == null) return;
//
//        WebEngine engine = videoWebView.getEngine();
//
//        // 1. Extract just the Video ID (e.g., from "XOuc-oVSOnw")
//        // If you have the full URL, you'd parse it.
//        // Since you are hardcoding for now, let's just use the ID:
//        String videoId = "XOuc-oVSOnw";
//
//        // 2. Create a custom HTML String
//        // We use "youtube-nocookie.com" because it's lighter and has fewer restrictions
//        String embedHtml = """
//        <html>
//        <body style='margin:0;padding:0;background:#000000;'>
//            <iframe width="560" height="315" src="https://www.youtube.com/embed/5qaL-fUnVBY?si=VlgjsRvkEhG2h2Y0" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
//        </body>
//        </html>
//        """;
//
//        // 3. Load the HTML String instead of the URL
//        System.out.println("Loading Video Content...");
//        engine.loadContent(embedHtml);
//    }
