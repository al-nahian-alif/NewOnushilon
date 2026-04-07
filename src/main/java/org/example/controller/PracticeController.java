package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.controller.SidebarController;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class PracticeController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane        mainScroll;

    // ── Header ───────────────────────────────────────────────
    @FXML private TextField searchField;
    @FXML private VBox      searchDropdown;
    @FXML private Label     avatarLabel;

    // ── Quick stats bar ───────────────────────────────────────
    @FXML private Label statQuestionsLabel;
    @FXML private Label statStreakLabel;
    @FXML private Label statXpLabel;
    @FXML private Label statAccuracyLabel;

    // ── Recommended session ───────────────────────────────────
    @FXML private VBox   recommendedCard;
    @FXML private Label  recommendedTitle;
    @FXML private Label  recommendedSubtitle;
    @FXML private Button recommendedBtn;

    // ── Quick start ───────────────────────────────────────────
    @FXML private Button dailyChallengeBtn;
    @FXML private Label  dailyChallengeSubLabel;
    @FXML private Label  challengeStatusLabel;
    @FXML private Label  weakAreaSubLabel;

    // ── Recent sessions ───────────────────────────────────────
    @FXML private VBox recentSessionsBox;

    // ── Study tools ──────────────────────────────────────────
    @FXML private Label flashcardSubLabel;
    @FXML private Label mistakeBankLabel;
    @FXML private HBox  mistakeBar;

    // ── Leaderboard ──────────────────────────────────────────
    @FXML private VBox  leaderboardBox;
    @FXML private Label leaderboardLiveLabel;

    // ── State ─────────────────────────────────────────────────
    private String recommendedChapterId;
    private String recommendedChapterName;
    private String recommendedSubjectName;

    // Subject record
    public record Subject(String id, String name, String color, String icon) {}
    private final List<Subject> subjects = new ArrayList<>();

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

        avatarLabel.setText(SessionManager.getInstance().getInitials());
        setupSearch();

        String uid = SessionManager.getInstance().getUserId();
        if (uid != null) Thread.ofVirtual().start(() -> loadAll(uid));
    }

    // ════════════════════════════════════════════════════════════
    //  LOAD ALL (Independent & Safe)
    // ════════════════════════════════════════════════════════════
    private void loadAll(String uid) {
        String today = LocalDate.now().toString();
        var client = SupabaseClient.getInstance();

        JsonNode profile = null;
        try { profile = client.from("profiles").select("xp,streak,level").eq("user_id", uid).limit(1).execute(); }
        catch (Exception e) { System.err.println("Profile fetch err: " + e.getMessage()); }

        JsonNode goal = null;
        try { goal = client.from("daily_goals").select("completed_questions,target_questions").eq("user_id", uid).eq("date", today).limit(1).execute(); }
        catch (Exception e) { System.err.println("Goal fetch err: " + e.getMessage()); }

        JsonNode attempts = null;
        try { attempts = client.from("question_attempts").select("is_correct,attempted_at,question:questions(chapter_id)").eq("user_id", uid).gte("attempted_at", today + "T00:00:00").execute(); }
        catch (Exception e) { System.err.println("Attempts fetch err: " + e.getMessage()); }

        JsonNode mistakes = null;
        try { mistakes = client.from("mistake_bank").select("question_id").eq("user_id", uid).execute(); }
        catch (Exception e) { System.err.println("Mistakes fetch err: " + e.getMessage()); }

        JsonNode sessions = null;
        try { sessions = client.from("study_sessions").select("duration_mins,started_at,subject:subjects(name,color)").eq("user_id", uid).order("started_at", false).limit(5).execute(); }
        catch (Exception e) { System.err.println("Sessions fetch err: " + e.getMessage()); }

        JsonNode leaderTop = null;
        try { leaderTop = client.from("profiles").select("user_id,name,xp").order("xp", false).limit(5).execute(); }
        catch (Exception e) { System.err.println("Leaderboard fetch err: " + e.getMessage()); }

        JsonNode allAttempts = null;
        try { allAttempts = client.from("question_attempts").select("is_correct").eq("user_id", uid).gte("attempted_at", "2020-01-01T00:00:00").execute(); }
        catch (Exception e) { System.err.println("AllAttempts fetch err: " + e.getMessage()); }

        // Process data even if some queries failed
        processStats(uid, profile, goal, attempts, allAttempts);
        processRecommended(uid, attempts, mistakes);
        processDailyChallengeStatus(goal);
        processWeakAreaStatus(mistakes);
        processRecentSessions(sessions);
        processStudyTools(mistakes, allAttempts);
        processLeaderboard(uid, leaderTop);
        loadSubjectsForMockExam();

        // Start leaderboard polling
        Platform.runLater(() -> startLeaderboardPoll(uid));
    }

    // ════════════════════════════════════════════════════════════
    //  QUICK STATS BAR
    // ════════════════════════════════════════════════════════════
    private void processStats(String uid, JsonNode profile, JsonNode goal,
                              JsonNode todayAttempts, JsonNode allAttempts) {
        int xp     = 0, streak = 0;
        int qToday = 0, qTarget = 10;
        int correct = 0, total = 0;

        if (profile != null && profile.isArray() && profile.size() > 0) {
            xp     = profile.get(0).path("xp").asInt(0);
            streak = profile.get(0).path("streak").asInt(0);
        }
        if (goal != null && goal.isArray() && goal.size() > 0) {
            qToday  = goal.get(0).path("completed_questions").asInt(0);
            qTarget = goal.get(0).path("target_questions").asInt(10);
        }
        if (allAttempts != null && allAttempts.isArray()) {
            for (JsonNode a : allAttempts) {
                total++;
                if (a.path("is_correct").asBoolean(false)) correct++;
            }
        }
        int accPct = total > 0 ? correct * 100 / total : 0;

        final int fq = qToday, ft = qTarget, fs = streak, fx = xp, fa = accPct;
        Platform.runLater(() -> {
            statQuestionsLabel.setText(fq + "/" + ft + " questions today");
            statStreakLabel.setText(fs + " day streak");
            statXpLabel.setText(String.format("%,d", fx) + " XP total");
            statAccuracyLabel.setText(fa + "% avg accuracy");
        });
    }

    // ════════════════════════════════════════════════════════════
    //  RECOMMENDED SESSION
    // ════════════════════════════════════════════════════════════
    private void processRecommended(String uid, JsonNode todayAttempts, JsonNode mistakes) {
        try {
            // Find weakest chapter from mistake bank
            Map<String, Integer> chapMistakes = new HashMap<>();
            if (mistakes != null && mistakes.isArray()) {
                // Get chapter IDs for mistake questions
                JsonNode qs = SupabaseClient.getInstance()
                        .from("mistake_bank")
                        .select("question:questions(chapter_id)")
                        .eq("user_id", uid)
                        .limit(50).execute();
                if (qs.isArray())
                    for (JsonNode q : qs) {
                        String cid = q.path("question").path("chapter_id").asText("");
                        if (!cid.isBlank())
                            chapMistakes.merge(cid, 1, Integer::sum);
                    }
            }

            String bestChapterId = null;
            int    mostMistakes  = 0;
            for (var e : chapMistakes.entrySet()) {
                if (e.getValue() > mostMistakes) {
                    mostMistakes  = e.getValue();
                    bestChapterId = e.getKey();
                }
            }

            if (bestChapterId != null) {
                JsonNode c = SupabaseClient.getInstance()
                        .from("chapters")
                        .select("id,title,subject:subjects(name)")
                        .eq("id", bestChapterId).limit(1).execute();

                if (c.isArray() && c.size() > 0) {
                    String chapName = c.get(0).path("title").asText("Chapter");
                    String subjName = c.get(0).path("subject").path("name").asText("Subject");
                    recommendedChapterId   = bestChapterId;
                    recommendedChapterName = chapName;
                    recommendedSubjectName = subjName;

                    final int fm = mostMistakes;
                    final String fc = chapName, fs = subjName;
                    Platform.runLater(() -> {
                        recommendedTitle.setText("Review: " + fc);
                        recommendedSubtitle.setText(
                                fs + "  •  " + fm + " mistake" + (fm != 1 ? "s" : "") +
                                        " recorded  •  Completing this will boost your accuracy score.");
                    });
                    return;
                }
            }

            // Fallback: Daily Challenge recommendation
            Platform.runLater(() -> {
                recommendedTitle.setText("Start Your Daily Challenge");
                recommendedSubtitle.setText(
                        "10 randomised questions across all subjects tailored to your level.");
                recommendedChapterId = null;
            });

        } catch (Exception e) {
            System.err.println("[Recommended] " + e.getMessage());
            Platform.runLater(() -> {
                recommendedTitle.setText("Start Your Daily Challenge");
                recommendedSubtitle.setText(
                        "10 questions tailored to your weak areas.");
            });
        }
    }

    @FXML private void onRecommended() {
        if (recommendedChapterId != null) {
            QuestionNavigator.go(getStage(), recommendedChapterId,
                    recommendedChapterName, recommendedSubjectName, "REVIEW");
        } else {
            onDailyChallenge();
        }
    }

    @FXML private void onSkipRecommended() {
        // Fade out and replace with "See you tomorrow" message
        FadeTransition ft = new FadeTransition(Duration.millis(300), recommendedCard);
        ft.setToValue(0);
        ft.setOnFinished(e -> {
            recommendedCard.setVisible(false);
            recommendedCard.setManaged(false);
        });
        ft.play();
    }

    // ════════════════════════════════════════════════════════════
    //  DAILY CHALLENGE STATUS
    // ════════════════════════════════════════════════════════════
    private void processDailyChallengeStatus(JsonNode goal) {
        int done = 0, target = 10;
        if (goal != null && goal.isArray() && goal.size() > 0) {
            done   = goal.get(0).path("completed_questions").asInt(0);
            target = goal.get(0).path("target_questions").asInt(10);
        }
        boolean complete = done >= target;
        final int fd = done, ft = target;
        Platform.runLater(() -> {
            if (complete) {
                challengeStatusLabel.setText("✓ Completed today! " + fd + "/" + ft);
                dailyChallengeBtn.setText("Play Again  ▶");
                dailyChallengeSubLabel.setText(fd + "/" + ft + " questions done today");
            } else {
                challengeStatusLabel.setText("");
                dailyChallengeSubLabel.setText((ft - fd) + " questions left today");
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  WEAK AREA STATUS
    // ════════════════════════════════════════════════════════════
    private void processWeakAreaStatus(JsonNode mistakes) {
        int count = (mistakes != null && mistakes.isArray()) ? mistakes.size() : 0;
        Platform.runLater(() ->
                weakAreaSubLabel.setText(
                        count > 0
                                ? count + " questions in your mistake bank"
                                : "No mistakes yet — keep practicing!"));
    }

    // ════════════════════════════════════════════════════════════
    //  RECENT SESSIONS
    // ════════════════════════════════════════════════════════════
    private void processRecentSessions(JsonNode sessions) {
        List<javafx.scene.Node> rows = new ArrayList<>();

        if (sessions != null && sessions.isArray() && sessions.size() > 0) {
            for (JsonNode s : sessions) {
                int    mins    = s.path("duration_mins").asInt(0);
                String atStr   = s.path("started_at").asText("");

                // Safe JSON parsing for null subjects in the DB
                String subjName = "General";
                String subjColor = "#0d7ff2";
                JsonNode subjNode = s.path("subject");
                if (subjNode != null && !subjNode.isMissingNode() && !subjNode.isNull()) {
                    String n = subjNode.path("name").asText("");
                    String c = subjNode.path("color").asText("");
                    if (!n.isBlank()) subjName = n;
                    if (!c.isBlank()) subjColor = c;
                }

                String dateLabel = "";
                try {
                    if (atStr.length() >= 16) {
                        // Extracts e.g., "2026-03-20  15:42"
                        dateLabel = atStr.substring(0, 10) + "  " + atStr.substring(11, 16);
                    } else {
                        dateLabel = atStr;
                    }
                } catch (Exception ignored) {}

                HBox row = buildSessionRow(subjName, subjColor, mins, dateLabel);
                rows.add(row);
            }
        } else {
            Label empty = new Label("No sessions yet. Start your first one above!");
            empty.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
            rows.add(empty);
        }

        Platform.runLater(() -> recentSessionsBox.getChildren().setAll(rows));
    }

    private HBox buildSessionRow(String subject, String color,
                                 int durationMins, String date) {
        String norm = "-fx-background-color:#ffffff; -fx-background-radius:12; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:12; " +
                "-fx-padding:14 18; -fx-cursor:hand; " +
                "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.03),4,0,0,1);";
        String hover = "-fx-background-color:#f8fafc; -fx-background-radius:12; " +
                "-fx-border-color:#0d7ff2; -fx-border-width:1; -fx-border-radius:12; " +
                "-fx-padding:14 18; -fx-cursor:hand;";

        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(norm);
        row.setOnMouseEntered(e -> row.setStyle(hover));
        row.setOnMouseExited(e  -> row.setStyle(norm));

        // Color dot
        StackPane dot = new StackPane();
        dot.setStyle("-fx-background-color:" + color + "; -fx-background-radius:50; " +
                "-fx-min-width:10; -fx-min-height:10; " +
                "-fx-pref-width:10; -fx-pref-height:10;");

        // Subject name
        Label subjLbl = new Label(subject);
        subjLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
        HBox.setHgrow(subjLbl, Priority.ALWAYS);

        // Duration badge
        Label durLbl = new Label(durationMins + " min");
        durLbl.setStyle("-fx-background-color:#e8f3fe; -fx-background-radius:50; " +
                "-fx-text-fill:#0d7ff2; -fx-font-size:11px; " +
                "-fx-font-weight:bold; -fx-padding:3 10;");

        // Date
        Label dateLbl = new Label(date);
        dateLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8; -fx-min-width:130;");

        row.getChildren().addAll(dot, subjLbl, durLbl, dateLbl);
        return row;
    }

    @FXML private void onViewAllSessions() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("All Study Sessions");
        a.setContentText("Full session history coming in next sprint!");
        a.showAndWait();
    }

    // ════════════════════════════════════════════════════════════
    //  STUDY TOOLS
    // ════════════════════════════════════════════════════════════
    private void processStudyTools(JsonNode mistakes, JsonNode allAttempts) {
        int mistakeCount = (mistakes != null && mistakes.isArray()) ? mistakes.size() : 0;
        int totalQ       = (allAttempts != null && allAttempts.isArray()) ? allAttempts.size() : 100;
        double ratio     = totalQ > 0 ? Math.min((double) mistakeCount / Math.max(totalQ, 1), 1.0) : 0;

        Platform.runLater(() -> {
            mistakeBankLabel.setText(mistakeCount + " question" +
                    (mistakeCount != 1 ? "s" : "") + " to review");

            mistakeBar.getParent().layoutBoundsProperty().addListener((obs, ov, nv) -> {
                double pw = nv.getWidth();
                if (pw > 0 && mistakeBar.getPrefWidth() < 1) {
                    Timeline tl = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(mistakeBar.prefWidthProperty(), 0)),
                            new KeyFrame(Duration.millis(600),
                                    new KeyValue(mistakeBar.prefWidthProperty(), pw * ratio))
                    );
                    tl.play();
                }
            });
        });

        // Flashcard count
        try {
            JsonNode decks = SupabaseClient.getInstance()
                    .from("flashcard_decks").select("id,card_count").execute();
            int cards = 0, deckCount = 0;
            if (decks.isArray()) {
                deckCount = decks.size();
                for (JsonNode d : decks) cards += d.path("card_count").asInt(0);
            }
            final int fc = cards, fd = deckCount;
            Platform.runLater(() -> flashcardSubLabel.setText(
                    fd > 0 ? fc + " cards across " + fd + " decks" : "Pre-made decks across all subjects"));
        } catch (Exception e) {
            System.err.println("[Flashcards] " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  SEARCH (5.1)
    // ════════════════════════════════════════════════════════════
    private void setupSearch() {
        searchField.textProperty().addListener((obs, ov, nv) -> {
            if (nv.trim().length() < 2) {
                searchDropdown.setVisible(false);
                searchDropdown.setManaged(false);
                searchDropdown.getChildren().clear();
                return;
            }
            Thread.ofVirtual().start(() -> runSearch(nv.trim()));
        });
        searchField.focusedProperty().addListener((obs, ov, focused) -> {
            if (!focused) Platform.runLater(() -> {
                searchDropdown.setVisible(false);
                searchDropdown.setManaged(false);
            });
        });
    }

    private void runSearch(String query) {
        try {
            List<javafx.scene.Node> results = new ArrayList<>();

            JsonNode chapters = SupabaseClient.getInstance()
                    .from("chapters")
                    .select("id,title,subject:subjects(name)")
                    .ilike("title", "%" + query + "%")
                    .limit(4).execute();
            if (chapters.isArray())
                for (JsonNode c : chapters)
                    results.add(buildSearchResult("📚",
                            c.path("title").asText(),
                            c.path("subject").path("name").asText("Subject"),
                            () -> QuestionNavigator.go(getStage(),
                                    c.path("id").asText(),
                                    c.path("title").asText(),
                                    c.path("subject").path("name").asText(), "PRACTICE")));

            JsonNode questions = SupabaseClient.getInstance()
                    .from("questions")
                    .select("id,body,chapter:chapters(id,title,subject:subjects(name))")
                    .ilike("body", "%" + query + "%")
                    .limit(3).execute();
            if (questions.isArray())
                for (JsonNode q : questions) {
                    String qBody = q.path("body").asText();
                    String preview = qBody.length() > 60 ? qBody.substring(0, 57) + "..." : qBody;
                    String cId    = q.path("chapter").path("id").asText();
                    String cTitle = q.path("chapter").path("title").asText("Chapter");
                    String sName  = q.path("chapter").path("subject").path("name").asText("Subject");
                    results.add(buildSearchResult("❓", preview, cTitle,
                            () -> QuestionNavigator.go(getStage(),
                                    List.of(q.path("id").asText()), cId, cTitle, sName, "PRACTICE")));
                }

            Platform.runLater(() -> {
                searchDropdown.getChildren().setAll(results);
                boolean show = !results.isEmpty();
                searchDropdown.setVisible(show);
                searchDropdown.setManaged(show);
            });
        } catch (Exception e) {
            System.err.println("[Search] " + e.getMessage());
        }
    }

    private javafx.scene.Node buildSearchResult(String icon, String title,
                                                String sub, Runnable onClick) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:10 14; -fx-cursor:hand; -fx-background-color:transparent;");
        Label ic = new Label(icon); ic.setStyle("-fx-font-size:14px;");
        VBox info = new VBox(2); HBox.setHgrow(info, Priority.ALWAYS);
        Label t = new Label(title);
        t.setStyle("-fx-font-size:13px; -fx-font-weight:600; -fx-text-fill:#0f172a;");
        Label s = new Label(sub);
        s.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        info.getChildren().addAll(t, s);
        row.getChildren().addAll(ic, info);
        row.setOnMouseEntered(e ->
                row.setStyle("-fx-padding:10 14; -fx-cursor:hand; -fx-background-color:#f1f5f9;"));
        row.setOnMouseExited(e ->
                row.setStyle("-fx-padding:10 14; -fx-cursor:hand; -fx-background-color:transparent;"));
        row.setOnMouseClicked(e -> {
            searchDropdown.setVisible(false);
            searchDropdown.setManaged(false);
            searchField.clear();
            onClick.run();
        });
        return row;
    }

    // ════════════════════════════════════════════════════════════
    //  LEADERBOARD
    // ════════════════════════════════════════════════════════════
    private void processLeaderboard(String uid, JsonNode topRows) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        String myName = SessionManager.getInstance().getUserName();

        if (topRows != null && topRows.isArray()) {
            String[] rankColors = {"#f59e0b","#94a3b8","#cd7c3f","#0d7ff2","#64748b"};
            int idx = 0;
            for (JsonNode entry : topRows) {
                String entryUid = entry.path("user_id").asText("");
                String name     = entry.path("name").asText("");
                if (name.isBlank()) name = "Student #" + (idx + 1);
                int    xp       = entry.path("xp").asInt(0);
                boolean isMe    = entryUid.equals(uid);

                HBox row = new HBox(10);
                row.setAlignment(Pos.CENTER_LEFT);
                if (isMe) row.setStyle(
                        "-fx-background-color:#e8f3fe; -fx-background-radius:8; -fx-padding:8 10;");

                Label rankLbl = new Label(String.valueOf(idx + 1));
                rankLbl.setStyle("-fx-font-size:13px; -fx-font-weight:900; " +
                        "-fx-text-fill:" + rankColors[Math.min(idx, 4)] + "; " +
                        "-fx-min-width:18;");

                StackPane av = new StackPane();
                av.setStyle("-fx-background-color:" + (isMe ? "#bfdbfe" : "#e2e8f0") +
                        "; -fx-background-radius:50; " +
                        "-fx-min-width:32; -fx-min-height:32; " +
                        "-fx-pref-width:32; -fx-pref-height:32;");
                av.setAlignment(Pos.CENTER);
                String ini = isMe ? SessionManager.getInstance().getInitials()
                        : (name.length() >= 2 ? name.substring(0,2).toUpperCase()
                        : name.toUpperCase());
                Label initLbl = new Label(ini.substring(0, Math.min(2, ini.length())));
                initLbl.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:" +
                        (isMe ? "#0d7ff2" : "#475569") + ";");
                av.getChildren().add(initLbl);

                Label nameLbl = new Label(isMe ? (myName != null ? myName : "You")+" (You)" : name);
                nameLbl.setStyle("-fx-font-size:12px; -fx-font-weight:" +
                        (isMe ? "bold" : "500") + "; -fx-text-fill:#0f172a;");
                HBox.setHgrow(nameLbl, Priority.ALWAYS);

                Label xpLbl = new Label(String.format("%,d", xp) + " XP");
                xpLbl.setStyle("-fx-font-size:12px; -fx-font-weight:900; -fx-text-fill:#0d7ff2;");

                row.getChildren().addAll(rankLbl, av, nameLbl, xpLbl);
                nodes.add(row);
                idx++;
            }
        }

        if (nodes.isEmpty()) {
            Label empty = new Label("No data yet.");
            empty.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
            nodes.add(empty);
        }
        Platform.runLater(() -> leaderboardBox.getChildren().setAll(nodes));
    }

    private void startLeaderboardPoll(String uid) {
        leaderboardLiveLabel.setStyle(
                "-fx-font-size:11px; -fx-text-fill:#22c55e; -fx-font-weight:bold;");
        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(30), e ->
                Thread.ofVirtual().start(() -> {
                    try {
                        JsonNode top = SupabaseClient.getInstance()
                                .from("profiles").select("user_id,name,xp")
                                .order("xp", false).limit(5).execute();
                        processLeaderboard(uid, top);
                    } catch (Exception ex) {
                        System.err.println("[LB poll] " + ex.getMessage());
                    }
                })
        ));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
    }

    // ════════════════════════════════════════════════════════════
    //  QUICK START ACTIONS
    // ════════════════════════════════════════════════════════════
    @FXML private void onDailyChallenge() {
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                List<String> ids = new ArrayList<>();
                JsonNode mistakes = SupabaseClient.getInstance()
                        .from("mistake_bank").select("question_id")
                        .eq("user_id", uid).limit(5).execute();
                if (mistakes.isArray())
                    for (JsonNode m : mistakes) ids.add(m.path("question_id").asText());
                if (ids.size() < 10) {
                    JsonNode extra = SupabaseClient.getInstance()
                            .from("questions").select("id").limit(10).execute();
                    if (extra.isArray())
                        for (JsonNode q : extra) {
                            String qid = q.path("id").asText();
                            if (!ids.contains(qid)) ids.add(qid);
                            if (ids.size() >= 10) break;
                        }
                }
                Collections.shuffle(ids);
                List<String> final10 = ids.subList(0, Math.min(10, ids.size()));
                Platform.runLater(() -> QuestionNavigator.go(getStage(), final10,
                        null, "Daily Challenge", "All Subjects", "DAILY_CHALLENGE"));
            } catch (Exception e) { System.err.println("[Daily] " + e.getMessage()); }
        });
    }

    @FXML private void onWeakAreaFocus() {
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode mistakes = SupabaseClient.getInstance()
                        .from("mistake_bank").select("question_id")
                        .eq("user_id", uid).limit(10).execute();
                List<String> ids = new ArrayList<>();
                if (mistakes.isArray())
                    for (JsonNode m : mistakes) ids.add(m.path("question_id").asText());
                if (ids.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setHeaderText("Mistake Bank Empty");
                        a.setContentText("No mistakes yet! Answer some questions first.");
                        a.showAndWait();
                    });
                    return;
                }
                Collections.shuffle(ids);
                Platform.runLater(() -> QuestionNavigator.go(getStage(), ids,
                        null, "Weak Area Focus", "All Subjects", "WEAK_FOCUS"));
            } catch (Exception e) { System.err.println("[WeakFocus] " + e.getMessage()); }
        });
    }

    @FXML private void onMockExam() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/MockExamModal.fxml"));
            javafx.scene.Parent root = loader.load();
            MockExamController ctrl = loader.getController();
            ctrl.setSubjects(subjects);
            ctrl.setOnStart((subjectId, subjectName, durationMins) ->
                    Thread.ofVirtual().start(() -> {
                        try {
                            JsonNode qs = SupabaseClient.getInstance()
                                    .from("questions")
                                    .select("id,chapter:chapters(subject_id)")
                                    .execute();
                            List<String> ids = new ArrayList<>();
                            if (qs.isArray())
                                for (JsonNode q : qs) {
                                    String sid = q.path("chapter").path("subject_id").asText("");
                                    if (subjectId == null || sid.equals(subjectId))
                                        ids.add(q.path("id").asText());
                                }
                            Collections.shuffle(ids);
                            Platform.runLater(() -> QuestionNavigator.go(getStage(), ids,
                                    null, "Mock Exam", subjectName, "MOCK_EXAM"));
                        } catch (Exception e) { System.err.println("[Mock] " + e.getMessage()); }
                    }));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.initOwner(getStage());
            modal.setTitle("Configure Mock Exam");
            modal.setScene(new javafx.scene.Scene(root, 480, 380));
            modal.setResizable(false);
            modal.show();
        } catch (Exception e) { System.err.println("[MockModal] " + e.getMessage()); }
    }

    // ════════════════════════════════════════════════════════════
    //  STUDY TOOLS ACTIONS
    // ════════════════════════════════════════════════════════════
    @FXML private void onFlashcardDecks() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Flashcard Decks");
        a.setContentText("Flashcard viewer coming in next sprint!");
        a.showAndWait();
    }

    @FXML private void onPastPapers() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Past Papers Archive");
        a.setContentText("PDF downloads coming in next sprint!");
        a.showAndWait();
    }

    @FXML private void onMistakeBankClick() {
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            try {
                JsonNode mistakes = SupabaseClient.getInstance()
                        .from("mistake_bank").select("question_id")
                        .eq("user_id", uid).execute();
                List<String> ids = new ArrayList<>();
                if (mistakes.isArray())
                    for (JsonNode m : mistakes) ids.add(m.path("question_id").asText());
                if (ids.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert a = new Alert(Alert.AlertType.INFORMATION);
                        a.setHeaderText("Mistake Bank Empty");
                        a.setContentText("No mistakes saved yet. Keep practicing!");
                        a.showAndWait();
                    });
                    return;
                }
                Collections.shuffle(ids);
                Platform.runLater(() -> QuestionNavigator.go(getStage(), ids,
                        null, "Mistake Bank", "All Subjects", "MISTAKE_BANK"));
            } catch (Exception e) { System.err.println("[MistakeBank] " + e.getMessage()); }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  HELPERS
    // ════════════════════════════════════════════════════════════
    private void loadSubjectsForMockExam() {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("subjects").select("id,name,color,icon")
                    .order("name", true).execute();
            if (rows.isArray())
                for (JsonNode r : rows)
                    subjects.add(new Subject(
                            r.path("id").asText(),
                            r.path("name").asText(),
                            r.path("color").asText("#0d7ff2"),
                            r.path("icon").asText("📚")));
        } catch (Exception e) { System.err.println("[Subjects] " + e.getMessage()); }
    }

    private Stage getStage() {
        return (Stage) mainScroll.getScene().getWindow();
    }
}
