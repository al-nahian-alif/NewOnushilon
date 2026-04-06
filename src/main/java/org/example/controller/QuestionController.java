package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class QuestionController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane        mainScroll;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Button exitBtn;
    @FXML private Label  questionCountLabel;
    @FXML private Label  statusRightLabel;
    @FXML private HBox   progressBar;
    @FXML private Label  timerLabel;
    @FXML private VBox   countdownBox;
    @FXML private Label  countdownLabel;
    @FXML private HBox   statusBadge;
    @FXML private Label  statusIcon;
    @FXML private Label  statusLabel;

    // ── Question card ────────────────────────────────────────
    @FXML private Label  subjectBadge;
    @FXML private Label  modeBadge;
    @FXML private Label  questionLabel;
    @FXML private VBox   optionsBox;

    // ── Pre-submit: hint card ─────────────────────────────────
    @FXML private HBox   hintCard;
    @FXML private Label  hintLabel;

    // ── Post-submit: AI explanation ───────────────────────────
    @FXML private VBox   aiExplanationCard;
    @FXML private Label  aiExplanationText;
    @FXML private VBox   keyPointsBox;
    @FXML private Label  correctAnswerHighlight;

    // ── Post-submit: notes ────────────────────────────────────
    @FXML private VBox     notesCard;
    @FXML private TextArea notesArea;
    @FXML private Label    notesSavedLabel;

    // ── Footer ───────────────────────────────────────────────
    @FXML private Button leftActionBtn;
    @FXML private Label  correctCountLabel;
    @FXML private Label  wrongCountLabel;
    @FXML private Label  skippedCountLabel;
    @FXML private Button prevBtn;
    @FXML private Button submitBtn;
    @FXML private Button skipBtn;
    @FXML private Button addMistakeBtn;

    // ════════════════════════════════════════════════════════════
    //  SESSION — static, survives scene change
    // ════════════════════════════════════════════════════════════
    public static class QuestionSession {
        public final List<String> questionIds;
        public final String chapterId, chapterName, subjectName, mode;
        public QuestionSession(List<String> ids, String cId,
                               String cName, String sName, String mode) {
            this.questionIds = ids; this.chapterId = cId;
            this.chapterName = cName; this.subjectName = sName; this.mode = mode;
        }
    }
    private static QuestionSession pendingSession;
    public static void setSession(QuestionSession s) { pendingSession = s; }

    // ════════════════════════════════════════════════════════════
    //  DATA MODEL
    // ════════════════════════════════════════════════════════════
    private record Question(
            String id, String body, List<String> options,
            String correctAnswer, String difficulty,
            String explanation, int xpReward
    ) {}

    // ── Runtime state ─────────────────────────────────────────
    private List<Question> questions    = new ArrayList<>();
    private int    currentIndex         = 0;
    private String selectedAnswer;
    private boolean answered;

    private int correctCount = 0, wrongCount = 0, skippedCount = 0;

    private final Map<Integer, String>  userAnswers   = new HashMap<>();
    private final Map<Integer, Boolean> answerResults = new HashMap<>();
    private final Set<Integer>          savedLater    = new HashSet<>();
    private final Map<Integer, String>  savedNotes    = new HashMap<>();

    // Per-question timing
    private long          questionStartMs;
    private final Map<Integer, Long> questionTimings = new HashMap<>();

    // Session timing
    private Timeline      elapsedTL;
    private int           elapsedSecs = 0;
    private LocalDateTime sessionStart;

    // Mock exam countdown
    private Timeline countdownTL;
    private int      countdownSecs = 0;
    private boolean  mockWarned    = false;

    // Session info
    private String chapterId, chapterName, subjectName, mode;

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (sidebarController != null) sidebarController.setActivePage("practice");

        Platform.runLater(() ->
                mainScroll.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );

        if (pendingSession != null) {
            chapterId   = pendingSession.chapterId;
            chapterName = pendingSession.chapterName;
            subjectName = pendingSession.subjectName;
            mode        = pendingSession.mode;
            List<String> ids = pendingSession.questionIds;
            pendingSession   = null;

            if (!ids.isEmpty()) loadQuestionsById(ids);
            else if (chapterId != null) loadQuestionsByChapter(chapterId);
            else loadFallback();
        } else {
            loadFallback();
        }

        // Show mode badge
        if (mode != null && !mode.equals("PRACTICE")) {
            String modeText = switch (mode) {
                case "MOCK_EXAM"       -> "⏱  Mock Exam";
                case "QUICK_QUIZ"      -> "⚡  Quick Quiz";
                case "DAILY_CHALLENGE" -> "✦  Daily Challenge";
                case "WEAK_FOCUS"      -> "🎯  Weak Focus";
                case "MISTAKE_BANK"    -> "❗  Mistake Bank";
                case "REVIEW"          -> "🔍  Review Mode";
                default                -> mode;
            };
            modeBadge.setText(modeText);
            modeBadge.setVisible(true);
            modeBadge.setManaged(true);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  LOAD QUESTIONS
    // ════════════════════════════════════════════════════════════
    private void loadQuestionsById(List<String> ids) {
        Thread.ofVirtual().start(() -> {
            try {
                List<Question> loaded = new ArrayList<>();
                for (String id : ids) {
                    JsonNode r = SupabaseClient.getInstance()
                            .from("questions")
                            .select("id,body,options,correct_answer,difficulty,explanation,xp_reward," +
                                    "chapter:chapters(title,subject:subjects(name))")
                            .eq("id", id).limit(1).execute();
                    if (r.isArray() && r.size() > 0) {
                        loaded.add(parseQuestion(r.get(0)));
                        inferNames(r.get(0));
                    }
                }
                Platform.runLater(() -> { questions = loaded; startSession(); });
            } catch (Exception e) {
                Platform.runLater(() -> { questions = demo(); startSession(); });
            }
        });
    }

    private void loadQuestionsByChapter(String chapId) {
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode rows = SupabaseClient.getInstance()
                        .from("questions")
                        .select("id,body,options,correct_answer,difficulty,explanation,xp_reward," +
                                "chapter:chapters(title,subject:subjects(name))")
                        .eq("chapter_id", chapId).execute();
                List<Question> loaded = new ArrayList<>();
                if (rows.isArray())
                    for (JsonNode r : rows) { loaded.add(parseQuestion(r)); inferNames(r); }
                Collections.shuffle(loaded);
                Platform.runLater(() -> { questions = loaded; startSession(); });
            } catch (Exception e) {
                Platform.runLater(() -> { questions = demo(); startSession(); });
            }
        });
    }

    private void loadFallback() {
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode rows = SupabaseClient.getInstance()
                        .from("questions")
                        .select("id,body,options,correct_answer,difficulty,explanation,xp_reward," +
                                "chapter:chapters(title,subject:subjects(name))")
                        .limit(5).execute();
                List<Question> loaded = new ArrayList<>();
                if (rows.isArray())
                    for (JsonNode r : rows) { loaded.add(parseQuestion(r)); inferNames(r); }
                if (loaded.isEmpty()) loaded = demo();
                final List<Question> fl = loaded;
                Platform.runLater(() -> { questions = fl; startSession(); });
            } catch (Exception e) {
                Platform.runLater(() -> { questions = demo(); startSession(); });
            }
        });
    }

    private void inferNames(JsonNode r) {
        if (chapterName == null)
            chapterName = r.path("chapter").path("title").asText(null);
        if (subjectName == null)
            subjectName = r.path("chapter").path("subject").path("name").asText(null);
    }

    private Question parseQuestion(JsonNode r) {
        List<String> opts = new ArrayList<>();
        if (r.path("options").isArray())
            for (JsonNode o : r.path("options")) opts.add(o.asText());
        if (opts.isEmpty()) opts = List.of("Option A", "Option B", "Option C", "Option D");
        return new Question(
                r.path("id").asText(""),
                r.path("body").asText("Question"),
                opts,
                r.path("correct_answer").asText(""),
                r.path("difficulty").asText("medium"),
                r.path("explanation").asText(""),
                r.path("xp_reward").asInt(10));
    }

    private List<Question> demo() {
        return new ArrayList<>(List.of(
                new Question("d1",
                        "Which molecule acts as the final electron acceptor in the electron transport chain?",
                        List.of("Glucose", "Oxygen (O₂)", "Carbon dioxide (CO₂)", "Water (H₂O)"),
                        "Oxygen (O₂)", "medium",
                        "Oxygen (O₂) is the final electron acceptor in aerobic respiration, accepting electrons and combining with protons to form water.", 10),
                new Question("d2",
                        "What is Newton's Second Law?",
                        List.of("F = mv", "F = ma", "E = mc²", "p = mv"),
                        "F = ma", "easy",
                        "Newton's Second Law: Force equals mass times acceleration (F = ma).", 10)));
    }

    // ════════════════════════════════════════════════════════════
    //  START SESSION
    // ════════════════════════════════════════════════════════════
    private void startSession() {
        if (questions.isEmpty()) {
            questionLabel.setText("No questions available for this selection.");
            return;
        }
        sessionStart = LocalDateTime.now();
        startElapsedTimer();
        if ("MOCK_EXAM".equals(mode)) startMockCountdown();
        showQuestion(0);
    }

    // ════════════════════════════════════════════════════════════
    //  7.4  TIMERS
    // ════════════════════════════════════════════════════════════

    /** Elapsed stopwatch — always running */
    private void startElapsedTimer() {
        elapsedTL = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSecs++;
            timerLabel.setText(String.format("%02d:%02d:%02d",
                    elapsedSecs / 3600, (elapsedSecs % 3600) / 60, elapsedSecs % 60));
        }));
        elapsedTL.setCycleCount(Timeline.INDEFINITE);
        elapsedTL.play();
    }

    /** Mock exam countdown — counts DOWN, warns at 10 min, auto-submits at 0 */
    private void startMockCountdown() {
        // Default 60 min; QuestionNavigator can override via session
        countdownSecs = 60 * 60;
        countdownBox.setVisible(true);
        countdownBox.setManaged(true);

        countdownTL = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            countdownSecs--;
            int m = countdownSecs / 60;
            int s = countdownSecs % 60;
            countdownLabel.setText(String.format("%02d:%02d", m, s));

            // Warn at 10 minutes
            if (countdownSecs == 600 && !mockWarned) {
                mockWarned = true;
                countdownLabel.setStyle(
                        "-fx-font-size:17px; -fx-font-weight:bold; -fx-text-fill:#ef4444;");
                // Pulse animation
                ScaleTransition pulse = new ScaleTransition(Duration.millis(500), countdownLabel);
                pulse.setFromX(1.0); pulse.setToX(1.15);
                pulse.setFromY(1.0); pulse.setToY(1.15);
                pulse.setCycleCount(6);
                pulse.setAutoReverse(true);
                pulse.play();
                showTimerWarning();
            }

            // Auto-submit at 0
            if (countdownSecs <= 0) {
                countdownTL.stop();
                countdownLabel.setText("00:00");
                Platform.runLater(this::finishSession);
            }
        }));
        countdownTL.setCycleCount(Timeline.INDEFINITE);
        countdownTL.play();
    }

    private void showTimerWarning() {
        Alert warn = new Alert(Alert.AlertType.WARNING);
        warn.setTitle("Time Warning");
        warn.setHeaderText("⚠  10 Minutes Remaining!");
        warn.setContentText("You have 10 minutes left. Finish your remaining questions.");
        warn.show();
    }

    // ════════════════════════════════════════════════════════════
    //  SHOW QUESTION (STATE 1 or 2)
    // ════════════════════════════════════════════════════════════
    private void showQuestion(int idx) {
        currentIndex    = idx;
        selectedAnswer  = userAnswers.get(idx);
        answered        = answerResults.containsKey(idx);
        questionStartMs = System.currentTimeMillis();

        Question q = questions.get(idx);

        // ── Status bar ────────────────────────────────────────
        questionCountLabel.setText("Question " + (idx + 1) + " of " + questions.size());
        if (!answered) {
            statusRightLabel.setText(
                    (subjectName != null ? subjectName : "") +
                            (chapterName != null ? " • " + chapterName : ""));
            setStatusBadge("in_progress");
        } else {
            boolean wasRight = Boolean.TRUE.equals(answerResults.get(idx));
            int total = correctCount + wrongCount;
            int rate  = total > 0 ? correctCount * 100 / total : 0;
            statusRightLabel.setText(rate + "% Correct Rate");
            setStatusBadge(wasRight ? "correct" : "incorrect");
        }

        animateProgressBar(idx);

        // ── Question card ─────────────────────────────────────
        subjectBadge.setText(
                (subjectName != null ? subjectName : "Subject") + "  •  " + cap(q.difficulty()));
        questionLabel.setText(q.body());

        buildOptions(q, answered);

        // ── State switching ───────────────────────────────────
        if (!answered) showPreSubmitState(q);
        else           showPostSubmitState(q, Boolean.TRUE.equals(answerResults.get(idx)));

        // ── Navigation ────────────────────────────────────────
        prevBtn.setDisable(idx == 0);
        boolean isLast = (idx == questions.size() - 1);
        submitBtn.setText(answered
                ? (isLast ? "Finish  🎉" : "Next Question  →")
                : "Submit Answer  ▶");
        submitBtn.setDisable(!answered && selectedAnswer == null);

        correctCountLabel.setText(String.valueOf(correctCount));
        wrongCountLabel.setText(String.valueOf(wrongCount));
        skippedCountLabel.setText(String.valueOf(skippedCount));
    }

    // ── STATE 1: pre-submit ───────────────────────────────────
    private void showPreSubmitState(Question q) {
        hintCard.setVisible(true);  hintCard.setManaged(true);
        hintLabel.setText("Review \"" + (chapterName != null ? chapterName : q.difficulty()) +
                "\" in your study materials for a quick refresh.");
        aiExplanationCard.setVisible(false); aiExplanationCard.setManaged(false);
        notesCard.setVisible(false);         notesCard.setManaged(false);
        leftActionBtn.setText(savedLater.contains(currentIndex) ? "🔖  Saved" : "🔖  Save for later");
        skipBtn.setVisible(true);    skipBtn.setManaged(true);
        addMistakeBtn.setVisible(false); addMistakeBtn.setManaged(false);
    }

    // ── STATE 2: post-submit ──────────────────────────────────
    private void showPostSubmitState(Question q, boolean wasCorrect) {
        hintCard.setVisible(false); hintCard.setManaged(false);

        // AI Explanation
        aiExplanationCard.setVisible(true); aiExplanationCard.setManaged(true);
        populateAiExplanation(q);
        FadeTransition ftAi = new FadeTransition(Duration.millis(350), aiExplanationCard);
        ftAi.setFromValue(0); ftAi.setToValue(1); ftAi.play();

        // Notes
        notesCard.setVisible(true); notesCard.setManaged(true);
        notesArea.setText(savedNotes.getOrDefault(currentIndex, ""));
        notesSavedLabel.setText("");
        notesArea.textProperty().addListener((obs, ov, nv) -> {
            savedNotes.put(currentIndex, nv);
            notesSavedLabel.setText("✓  Notes saved");
        });
        FadeTransition ftNotes = new FadeTransition(Duration.millis(350), notesCard);
        ftNotes.setFromValue(0); ftNotes.setToValue(1); ftNotes.play();

        // Footer
        leftActionBtn.setText("🚩  Report Issue");
        skipBtn.setVisible(false);   skipBtn.setManaged(false);
        addMistakeBtn.setVisible(!wasCorrect);
        addMistakeBtn.setManaged(!wasCorrect);
        if (!wasCorrect) addMistakeBtn.setUserData(q.id());
    }

    private void populateAiExplanation(Question q) {
        aiExplanationText.setText(q.explanation().isBlank()
                ? "The correct answer is \"" + q.correctAnswer() + "\"."
                : q.explanation());
        correctAnswerHighlight.setText(q.correctAnswer());

        // Key bullet points
        keyPointsBox.getChildren().removeIf(n -> !(n instanceof Label lb &&
                lb.getStyle().contains("bold") && lb.getText().equals("Key Points")));

        String[] sentences = q.explanation().split("\\. ");
        int max = Math.min(sentences.length, 3);
        for (int i = 0; i < max; i++) {
            String s = sentences[i].trim();
            if (s.isBlank()) continue;
            HBox bullet = new HBox(8);
            bullet.setAlignment(Pos.TOP_LEFT);
            Label dot = new Label("•");
            dot.setStyle("-fx-font-size:14px; -fx-text-fill:#0d7ff2; -fx-font-weight:bold;");
            dot.setMinWidth(12);
            Label txt = new Label(s + (s.endsWith(".") ? "" : "."));
            txt.setStyle("-fx-font-size:12px; -fx-text-fill:#334155;");
            txt.setWrapText(true);
            HBox.setHgrow(txt, Priority.ALWAYS);
            bullet.getChildren().addAll(dot, txt);
            keyPointsBox.getChildren().add(bullet);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  BUILD OPTIONS
    // ════════════════════════════════════════════════════════════
    private void buildOptions(Question q, boolean isAnswered) {
        optionsBox.getChildren().clear();
        String[] letters = {"A","B","C","D"};
        for (int i = 0; i < q.options().size(); i++) {
            String letter = i < letters.length ? letters[i] : String.valueOf((char)('A'+i));
            optionsBox.getChildren().add(
                    buildOptionRow(letter, q.options().get(i),
                            q.options().get(i).equals(selectedAnswer), isAnswered, q));
        }
    }

    private HBox buildOptionRow(String letter, String text,
                                boolean selected, boolean isAnswered, Question q) {
        boolean isCorrect = text.equals(q.correctAnswer());
        String rowBg, rowBorder, circleBg, circleFg, textColor, textWeight;

        if (isAnswered) {
            if (isCorrect) {
                rowBg="#f0fdf4"; rowBorder="#4ade80"; circleBg="#22c55e"; circleFg="white";
                textColor="#166534"; textWeight="bold";
            } else if (selected) {
                rowBg="#fef2f2"; rowBorder="#fca5a5"; circleBg="#ef4444"; circleFg="white";
                textColor="#991b1b"; textWeight="bold";
            } else {
                rowBg="#f8fafc"; rowBorder="#f1f5f9"; circleBg="#f1f5f9"; circleFg="#94a3b8";
                textColor="#94a3b8"; textWeight="400";
            }
        } else if (selected) {
            rowBg="#eff6ff"; rowBorder="#0d7ff2"; circleBg="#0d7ff2"; circleFg="white";
            textColor="#0f172a"; textWeight="bold";
        } else {
            rowBg="#f8fafc"; rowBorder="#f1f5f9"; circleBg="#f1f5f9"; circleFg="#475569";
            textColor="#334155"; textWeight="500";
        }

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color:" + rowBg + "; -fx-background-radius:10; " +
                "-fx-border-color:" + rowBorder + "; -fx-border-width:2; " +
                "-fx-border-radius:10; -fx-padding:14 18; " +
                "-fx-cursor:" + (isAnswered ? "default" : "hand") + ";");
        if (isAnswered && !isCorrect && !selected) row.setOpacity(0.55);

        StackPane circle = new StackPane();
        circle.setMinSize(32,32); circle.setPrefSize(32,32); circle.setMaxSize(32,32);
        circle.setStyle("-fx-background-color:" + circleBg + "; -fx-background-radius:50;");
        Label lLbl = new Label(letter);
        lLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:" + circleFg + ";");
        circle.getChildren().add(lLbl);

        Label tLbl = new Label(text);
        HBox.setHgrow(tLbl, Priority.ALWAYS);
        tLbl.setStyle("-fx-font-size:14px; -fx-font-weight:" + textWeight + "; " +
                "-fx-text-fill:" + textColor + "; -fx-wrap-text:true;");
        tLbl.setWrapText(true);

        if (isAnswered && isCorrect) {
            Label chk = new Label("✓");
            chk.setStyle("-fx-font-size:16px; -fx-text-fill:#22c55e; -fx-font-weight:bold;");
            row.getChildren().addAll(circle, tLbl, chk);
        } else {
            StackPane radio = new StackPane();
            radio.setMinSize(22,22); radio.setPrefSize(22,22); radio.setMaxSize(22,22);
            String rBorder = selected ? (isAnswered ? "#ef4444" : "#0d7ff2") : "#cbd5e1";
            radio.setStyle("-fx-border-color:" + rBorder + "; -fx-border-width:2; " +
                    "-fx-border-radius:50; -fx-background-radius:50;");
            if (selected) {
                Circle dot = new Circle(5, isAnswered ? Color.web("#ef4444") : Color.web("#0d7ff2"));
                radio.getChildren().add(dot);
            }
            row.getChildren().addAll(circle, tLbl, radio);
        }

        if (!isAnswered) {
            String hoverS = "-fx-background-color:#eff6ff; -fx-background-radius:10; " +
                    "-fx-border-color:#93c5fd; -fx-border-width:2; " +
                    "-fx-border-radius:10; -fx-padding:14 18; -fx-cursor:hand;";
            String normS  = "-fx-background-color:#f8fafc; -fx-background-radius:10; " +
                    "-fx-border-color:#f1f5f9; -fx-border-width:2; " +
                    "-fx-border-radius:10; -fx-padding:14 18; -fx-cursor:hand;";
            if (!selected) {
                row.setOnMouseEntered(e -> row.setStyle(hoverS));
                row.setOnMouseExited(e  -> row.setStyle(normS));
            }
            row.setOnMouseClicked(e -> onSelectOption(text));
        }
        return row;
    }

    // ════════════════════════════════════════════════════════════
    //  STATUS BADGE
    // ════════════════════════════════════════════════════════════
    private void setStatusBadge(String state) {
        switch (state) {
            case "correct" -> {
                statusBadge.setStyle("-fx-background-color:#dcfce7; -fx-background-radius:50; -fx-padding:5 14;");
                statusIcon.setText("✓");
                statusIcon.setStyle("-fx-font-size:13px; -fx-text-fill:#16a34a; -fx-font-weight:bold;");
                statusLabel.setText("Correct");
                statusLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#16a34a;");
            }
            case "incorrect" -> {
                statusBadge.setStyle("-fx-background-color:#fee2e2; -fx-background-radius:50; -fx-padding:5 14;");
                statusIcon.setText("✗");
                statusIcon.setStyle("-fx-font-size:13px; -fx-text-fill:#dc2626; -fx-font-weight:bold;");
                statusLabel.setText("Incorrect");
                statusLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#dc2626;");
            }
            default -> {
                statusBadge.setStyle("-fx-background-color:#dbeafe; -fx-background-radius:50; -fx-padding:5 14;");
                statusIcon.setText("⏱");
                statusIcon.setStyle("-fx-font-size:12px; -fx-text-fill:#2563eb;");
                statusLabel.setText("In Progress");
                statusLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#2563eb;");
            }
        }
    }

    private void animateProgressBar(int idx) {
        double pct = questions.isEmpty() ? 0 : (double)(idx + 1) / questions.size();
        progressBar.getParent().layoutBoundsProperty().addListener((obs, ov, nv) -> {
            if (nv.getWidth() > 0) {
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.ZERO,
                                new KeyValue(progressBar.prefWidthProperty(), progressBar.getPrefWidth())),
                        new KeyFrame(Duration.millis(300),
                                new KeyValue(progressBar.prefWidthProperty(), nv.getWidth() * pct)));
                tl.play();
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  SELECT OPTION
    // ════════════════════════════════════════════════════════════
    private void onSelectOption(String answer) {
        if (answered) return;
        selectedAnswer = answer;
        submitBtn.setDisable(false);
        buildOptions(questions.get(currentIndex), false);
    }

    // ════════════════════════════════════════════════════════════
    //  SUBMIT / NEXT
    // ════════════════════════════════════════════════════════════
    @FXML private void onSubmitOrNext() {
        if (answered) {
            if (currentIndex >= questions.size() - 1) finishSession();
            else showQuestion(currentIndex + 1);
            return;
        }
        if (selectedAnswer == null) return;

        answered = true;
        Question q         = questions.get(currentIndex);
        boolean  correct   = selectedAnswer.equals(q.correctAnswer());
        long     timeTaken = System.currentTimeMillis() - questionStartMs;

        userAnswers.put(currentIndex, selectedAnswer);
        answerResults.put(currentIndex, correct);
        questionTimings.put(currentIndex, timeTaken);
        if (correct) correctCount++; else wrongCount++;

        buildOptions(q, true);
        showPostSubmitState(q, correct);

        int total = correctCount + wrongCount;
        int rate  = total > 0 ? correctCount * 100 / total : 0;
        statusRightLabel.setText(rate + "% Correct Rate");
        setStatusBadge(correct ? "correct" : "incorrect");

        boolean isLast = (currentIndex == questions.size() - 1);
        submitBtn.setText(isLast ? "Finish  🎉" : "Next Question  →");
        skipBtn.setVisible(false); skipBtn.setManaged(false);
        correctCountLabel.setText(String.valueOf(correctCount));
        wrongCountLabel.setText(String.valueOf(wrongCount));

        // DB saves on background
        saveAttemptToDb(q, correct, (int) timeTaken);
    }

    // ════════════════════════════════════════════════════════════
    //  EXIT (7.1)
    // ════════════════════════════════════════════════════════════
    @FXML private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Exit Session");
        confirm.setHeaderText("Exit this session?");
        confirm.setContentText(
                "Your progress so far (" + correctCount + " correct, " +
                        wrongCount + " wrong) will be saved.\n\nAre you sure you want to exit?");

        ButtonType exitNow  = new ButtonType("Exit Now",  ButtonBar.ButtonData.LEFT);
        ButtonType continueBtn = new ButtonType("Continue", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(exitNow, continueBtn);

        confirm.showAndWait().ifPresent(bt -> {
            if (bt == exitNow) {
                stopTimers();
                saveStudySessionPartial();
                navigateBack("/PracticeView.fxml");
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  SKIP
    // ════════════════════════════════════════════════════════════
    @FXML private void onSkip() {
        skippedCount++;
        skippedCountLabel.setText(String.valueOf(skippedCount));
        if (currentIndex < questions.size() - 1) showQuestion(currentIndex + 1);
        else finishSession();
    }

    // ════════════════════════════════════════════════════════════
    //  PREVIOUS
    // ════════════════════════════════════════════════════════════
    @FXML private void onPrevious() {
        if (currentIndex > 0) showQuestion(currentIndex - 1);
    }

    // ════════════════════════════════════════════════════════════
    //  LEFT ACTION (Save for Later / Report Issue)
    // ════════════════════════════════════════════════════════════
    @FXML private void onLeftAction() {
        if (!answered) {
            if (savedLater.contains(currentIndex)) {
                savedLater.remove(currentIndex);
                leftActionBtn.setText("🔖  Save for later");
            } else {
                savedLater.add(currentIndex);
                leftActionBtn.setText("🔖  Saved ✓");
            }
        } else {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setHeaderText("Report Issue");
            a.setContentText("Thanks for your report! Our team will review this question.");
            a.showAndWait();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  ADD TO MISTAKE BANK
    // ════════════════════════════════════════════════════════════
    @FXML private void onAddToMistakeBank() {
        String qId = (String) addMistakeBtn.getUserData();
        if (qId == null) return;
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("mistake_bank").upsert(
                        String.format("{\"user_id\":\"%s\",\"question_id\":\"%s\"}", uid, qId));
                Platform.runLater(() -> {
                    addMistakeBtn.setText("✓  Added");
                    addMistakeBtn.setDisable(true);
                });
            } catch (Exception e) { System.err.println("[MistakeBank] " + e.getMessage()); }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  VIEW STUDY MATERIAL
    // ════════════════════════════════════════════════════════════
    @FXML private void onViewStudyMaterial() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Study Material: " + (chapterName != null ? chapterName : "Chapter"));
        a.setContentText("Full notes viewer coming in the next sprint!");
        a.showAndWait();
    }

    // ════════════════════════════════════════════════════════════
    //  7.2  SAVE ATTEMPT TO DB
    // ════════════════════════════════════════════════════════════
    private void saveAttemptToDb(Question q, boolean correct, int timeTakenMs) {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) return;

        Thread.ofVirtual().start(() -> {
            try {
                // 1. question_attempts
                SupabaseClient.getInstance().from("question_attempts").insert(String.format(
                        "{\"user_id\":\"%s\",\"question_id\":\"%s\"," +
                                "\"is_correct\":%b,\"time_taken_ms\":%d}",
                        uid, q.id(), correct, timeTakenMs));

                // 2. mistake_bank — insert on wrong, remove on correct
                if (!correct) {
                    SupabaseClient.getInstance().from("mistake_bank").upsert(String.format(
                            "{\"user_id\":\"%s\",\"question_id\":\"%s\"}", uid, q.id()));
                } else {
                    SupabaseClient.getInstance().from("mistake_bank")
                            .eq("user_id", uid).eq("question_id", q.id()).delete();
                }

                // 3. daily_goals.completed_questions += 1
                String today = LocalDate.now().toString();
                JsonNode g = SupabaseClient.getInstance().from("daily_goals")
                        .select("completed_questions")
                        .eq("user_id", uid).eq("date", today).limit(1).execute();
                if (g.isArray() && g.size() > 0) {
                    int cur = g.get(0).path("completed_questions").asInt(0);
                    SupabaseClient.getInstance().from("daily_goals")
                            .eq("user_id", uid).eq("date", today)
                            .update("{\"completed_questions\":" + (cur + 1) + "}");
                }

                // 4. user_progress
                if (chapterId != null) {
                    int done = answerResults.size();
                    int pct  = questions.isEmpty() ? 0 : done * 100 / questions.size();
                    SupabaseClient.getInstance().from("user_progress").upsert(String.format(
                            "{\"user_id\":\"%s\",\"chapter_id\":\"%s\"," +
                                    "\"completed_pct\":%d,\"last_accessed_at\":\"%s\"}",
                            uid, chapterId, pct,
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                }

                // 5. XP award on correct
                if (correct) awardXp(uid, q.xpReward());

                // 6. Check achievements
                checkAchievements(uid);

            } catch (Exception e) { System.err.println("[SaveAttempt] " + e.getMessage()); }
        });
    }

    private void awardXp(String uid, int xp) throws Exception {
        JsonNode p = SupabaseClient.getInstance().from("profiles")
                .select("xp").eq("user_id", uid).limit(1).execute();
        if (p.isArray() && p.size() > 0) {
            int newXp = p.get(0).path("xp").asInt(0) + xp;
            int level = newXp < 500 ? 1 : newXp < 1200 ? 2 : newXp < 2500 ? 3
                    : newXp < 5000 ? 4 : newXp < 10000 ? 5 : 6;
            SupabaseClient.getInstance().from("profiles")
                    .eq("user_id", uid)
                    .update(String.format("{\"xp\":%d,\"level\":%d}", newXp, level));
            SessionManager.getInstance().setXp(newXp);
            SessionManager.getInstance().setLevel(level);
        }
    }

    private void checkAchievements(String uid) {
        try {
            // Count total correct attempts
            JsonNode attempts = SupabaseClient.getInstance()
                    .from("question_attempts")
                    .select("is_correct").eq("user_id", uid).execute();
            int totalCorrect = 0;
            if (attempts.isArray())
                for (JsonNode a : attempts)
                    if (a.path("is_correct").asBoolean(false)) totalCorrect++;

            int streak = SessionManager.getInstance().getStreak();

            JsonNode allAch = SupabaseClient.getInstance()
                    .from("achievements").select("id,key,title,xp_reward").execute();
            JsonNode userAch = SupabaseClient.getInstance()
                    .from("user_achievements").select("achievement_id")
                    .eq("user_id", uid).execute();

            Set<String> unlocked = new HashSet<>();
            if (userAch.isArray())
                for (JsonNode ua : userAch)
                    unlocked.add(ua.path("achievement_id").asText());

            if (allAch.isArray())
                for (JsonNode a : allAch) {
                    String aid = a.path("id").asText();
                    String key = a.path("key").asText();
                    if (unlocked.contains(aid)) continue;

                    boolean earn = switch (key) {
                        case "concept_master" -> totalCorrect >= 50;
                        case "fire_15"        -> streak >= 15;
                        case "quiz_titan"     -> totalCorrect >= 100;
                        default               -> false;
                    };

                    if (earn) {
                        SupabaseClient.getInstance().from("user_achievements").upsert(
                                String.format("{\"user_id\":\"%s\",\"achievement_id\":\"%s\"}", uid, aid));
                        int achXp = a.path("xp_reward").asInt(0);
                        if (achXp > 0) awardXp(uid, achXp);
                    }
                }
        } catch (Exception e) { System.err.println("[CheckAch] " + e.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════
    //  7.3  FINISH SESSION
    // ════════════════════════════════════════════════════════════
    private void finishSession() {
        stopTimers();
        String uid = SessionManager.getInstance().getUserId();

        // Save study session
        if (uid != null && sessionStart != null) {
            int totalMins = Math.max(1, elapsedSecs / 60);
            Thread.ofVirtual().start(() -> {
                try {
                    SupabaseClient.getInstance().from("study_sessions").insert(String.format(
                            "{\"user_id\":\"%s\",\"started_at\":\"%s\"," +
                                    "\"ended_at\":\"%s\",\"duration_mins\":%d}",
                            uid,
                            sessionStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            totalMins));

                    // Update streak if daily goal met
                    checkAndUpdateStreak(uid);

                } catch (Exception e) { System.err.println("[StudySession] " + e.getMessage()); }
            });
        }

        int total    = questions.size();
        int accuracy = (correctCount + wrongCount) > 0
                ? correctCount * 100 / (correctCount + wrongCount) : 0;
        int xpEarned = correctCount * 10;

        Platform.runLater(() ->
                showResultsDialog(accuracy, xpEarned, correctCount, wrongCount,
                        skippedCount, total, timerLabel.getText()));
    }

    /** 7.3 — Check if daily goal is met → update streak */
    private void checkAndUpdateStreak(String uid) {
        try {
            String today = LocalDate.now().toString();
            JsonNode goals = SupabaseClient.getInstance()
                    .from("daily_goals")
                    .select("completed_questions,target_questions")
                    .eq("user_id", uid).eq("date", today).limit(1).execute();

            if (goals.isArray() && goals.size() > 0) {
                int done   = goals.get(0).path("completed_questions").asInt(0);
                int target = goals.get(0).path("target_questions").asInt(10);
                if (done >= target) {
                    // Goal met — update streak
                    JsonNode profile = SupabaseClient.getInstance()
                            .from("profiles")
                            .select("streak,streak_last_date")
                            .eq("user_id", uid).limit(1).execute();
                    if (profile.isArray() && profile.size() > 0) {
                        int    streak  = profile.get(0).path("streak").asInt(0);
                        String lastStr = profile.get(0).path("streak_last_date").asText("");
                        LocalDate lastDate = lastStr.isBlank() ? LocalDate.now().minusDays(2)
                                : LocalDate.parse(lastStr.substring(0, 10));
                        if (!lastDate.equals(LocalDate.now())) {
                            int newStreak = lastDate.equals(LocalDate.now().minusDays(1))
                                    ? streak + 1 : 1;
                            SupabaseClient.getInstance().from("profiles")
                                    .eq("user_id", uid)
                                    .update(String.format(
                                            "{\"streak\":%d,\"streak_last_date\":\"%s\"}",
                                            newStreak, LocalDate.now()));
                            SessionManager.getInstance().setStreak(newStreak);
                        }
                    }
                }
            }
        } catch (Exception e) { System.err.println("[Streak check] " + e.getMessage()); }
    }

    private void saveStudySessionPartial() {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null || sessionStart == null) return;
        int totalMins = Math.max(1, elapsedSecs / 60);
        Thread.ofVirtual().start(() -> {
            try {
                SupabaseClient.getInstance().from("study_sessions").insert(String.format(
                        "{\"user_id\":\"%s\",\"started_at\":\"%s\"," +
                                "\"ended_at\":\"%s\",\"duration_mins\":%d}",
                        uid,
                        sessionStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        totalMins));
            } catch (Exception e) { System.err.println("[PartialSession] " + e.getMessage()); }
        });
    }

    private void stopTimers() {
        if (elapsedTL  != null) elapsedTL.stop();
        if (countdownTL != null) countdownTL.stop();
    }

    // ════════════════════════════════════════════════════════════
    //  7.3  RESULTS DIALOG — with per-question breakdown
    // ════════════════════════════════════════════════════════════
    private void showResultsDialog(int accuracy, int xp, int correct,
                                   int wrong, int skipped, int total, String time) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Session Complete!");

        // ── Main content ──────────────────────────────────────
        VBox content = new VBox(18);
        content.setAlignment(Pos.CENTER);
        content.setStyle("-fx-padding:28 32; -fx-background-color:#f5f7f8;");
        content.setPrefWidth(560);

        Label title = new Label("Session Complete! 🎉");
        title.setStyle("-fx-font-size:22px; -fx-font-weight:900; -fx-text-fill:#0f172a;");

        Label scoreLabel = new Label(accuracy + "%");
        scoreLabel.setStyle("-fx-font-size:52px; -fx-font-weight:900; -fx-text-fill:#0d7ff2;");
        Label scoreDesc = new Label(correct + " / " + total + " correct  •  " + time);
        scoreDesc.setStyle("-fx-font-size:13px; -fx-text-fill:#64748b;");

        // Stats row
        HBox stats = new HBox(0);
        stats.setAlignment(Pos.CENTER);
        stats.setStyle("-fx-background-color:#ffffff; -fx-background-radius:12; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:12; " +
                "-fx-padding:16 0;");
        stats.getChildren().addAll(
                statCol("✓", String.valueOf(correct),  "Correct",  "#22c55e"),
                statDiv(),
                statCol("✗", String.valueOf(wrong),    "Wrong",    "#ef4444"),
                statDiv(),
                statCol("⟳", String.valueOf(skipped),  "Skipped",  "#94a3b8"),
                statDiv(),
                statCol("⏱", time,                     "Time",     "#0d7ff2")
        );

        Label xpLabel = new Label("+" + xp + " XP earned!");
        xpLabel.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#0d7ff2; " +
                "-fx-background-color:#e8f3fe; -fx-background-radius:50; -fx-padding:8 22;");

        // ── Per-question breakdown ────────────────────────────
        VBox breakdown = new VBox(6);
        breakdown.setStyle("-fx-background-color:#ffffff; -fx-background-radius:12; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:12; " +
                "-fx-padding:16; -fx-max-height:200;");

        ScrollPane bScroll = new ScrollPane(breakdown);
        bScroll.setFitToWidth(true);
        bScroll.setMaxHeight(180);
        bScroll.setStyle("-fx-background-color:transparent; -fx-background:#ffffff; " +
                "-fx-border-color:transparent; -fx-padding:0;");

        Label bTitle = new Label("Question Breakdown");
        bTitle.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
        breakdown.getChildren().add(bTitle);

        for (int i = 0; i < questions.size(); i++) {
            Question q    = questions.get(i);
            Boolean res   = answerResults.get(i);
            String  uAns  = userAnswers.get(i);
            boolean skip  = (res == null);
            boolean ok    = Boolean.TRUE.equals(res);

            HBox qRow = new HBox(10);
            qRow.setAlignment(Pos.CENTER_LEFT);
            qRow.setStyle("-fx-padding:4 0;");

            Label num = new Label((i + 1) + ".");
            num.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8; -fx-min-width:22;");

            Label icon = new Label(skip ? "⟳" : ok ? "✓" : "✗");
            icon.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:" +
                    (skip ? "#94a3b8" : ok ? "#22c55e" : "#ef4444") + ";");

            String qPreview = q.body().length() > 55
                    ? q.body().substring(0, 52) + "..." : q.body();
            Label qLbl = new Label(qPreview);
            qLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#334155;");
            HBox.setHgrow(qLbl, Priority.ALWAYS);

            // Time taken per question
            long ms = questionTimings.getOrDefault(i, 0L);
            Label timeLbl = new Label(ms > 0 ? (ms / 1000) + "s" : "skipped");
            timeLbl.setStyle("-fx-font-size:10px; -fx-text-fill:#94a3b8; -fx-min-width:44;");

            qRow.getChildren().addAll(num, icon, qLbl, timeLbl);
            breakdown.getChildren().add(qRow);
        }

        content.getChildren().addAll(title, scoreLabel, scoreDesc, stats, xpLabel, bScroll);

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setStyle("-fx-background-color:#f5f7f8;");
        dlg.getDialogPane().getButtonTypes().addAll(
                new ButtonType("Review Mistakes", ButtonBar.ButtonData.LEFT),
                new ButtonType("Back to Practice", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("Dashboard",        ButtonBar.ButtonData.OK_DONE)
        );

        dlg.showAndWait().ifPresent(bt -> {
            if (bt.getText().equals("Review Mistakes")) {
                List<String> wrongIds = new ArrayList<>();
                for (Map.Entry<Integer, Boolean> e : answerResults.entrySet())
                    if (!e.getValue()) wrongIds.add(questions.get(e.getKey()).id());
                if (!wrongIds.isEmpty()) {
                    QuestionNavigator.go(getStage(), wrongIds,
                            chapterId, chapterName, subjectName, "REVIEW");
                } else {
                    navigateBack("/PracticeView.fxml");
                }
            } else if (bt.getText().equals("Back to Practice")) {
                navigateBack("/PracticeView.fxml");
            } else {
                navigateBack("/DashboardView.fxml");
            }
        });
    }

    // ── Result dialog helpers ─────────────────────────────────
    private VBox statCol(String icon, String val, String lbl, String color) {
        VBox c = new VBox(4); HBox.setHgrow(c, Priority.ALWAYS); c.setAlignment(Pos.CENTER);
        Label ic = new Label(icon); ic.setStyle("-fx-font-size:16px; -fx-text-fill:" + color + ";");
        Label vl = new Label(val);  vl.setStyle("-fx-font-size:18px; -fx-font-weight:900; -fx-text-fill:#0f172a;");
        Label lb = new Label(lbl);  lb.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8;");
        c.getChildren().addAll(ic, vl, lb); return c;
    }
    private Region statDiv() {
        Region d = new Region();
        d.setStyle("-fx-border-color:transparent transparent transparent #e2e8f0; -fx-border-width:0 0 0 1;");
        d.setPrefWidth(1); d.setMinHeight(50); return d;
    }

    private void navigateBack(String fxmlPath) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage stage = getStage();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (Exception e) { System.err.println("[NavBack] " + e.getMessage()); }
    }

    private Stage getStage() {
        return (Stage) submitBtn.getScene().getWindow();
    }

    private String cap(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}