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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.awt.Desktop;
import java.net.URI;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane scrollPane;

    // ── Header ───────────────────────────────────────────────
    @FXML private Label welcomeLabel;
    @FXML private Label streakLabel;

    // ── Subject progress labels ───────────────────────────────
    @FXML private Label physicsProgressLabel;
    @FXML private Label chemistryProgressLabel;
    @FXML private Label mathProgressLabel;
    @FXML private Label biologyProgressLabel;

    // ── Subject last-accessed labels ──────────────────────────
    @FXML private Label physicsLastLabel;
    @FXML private Label chemistryLastLabel;
    @FXML private Label mathLastLabel;
    @FXML private Label biologyLastLabel;

    // ── Subject progress bars ─────────────────────────────────
    @FXML private HBox physicsBar;
    @FXML private HBox chemistryBar;
    @FXML private HBox mathBar;
    @FXML private HBox biologyBar;

    // ── Chart ─────────────────────────────────────────────────
    @FXML private StackPane chartContainer;
    @FXML private ComboBox<String> periodCombo;

    // ── Goal ring ─────────────────────────────────────────────
    @FXML private StackPane goalRingContainer;
    @FXML private Label goalStatusLabel;
    @FXML private Label goalSubLabel;

    // ── Upcoming tests container ──────────────────────────────
    @FXML private VBox upcomingTestsBox;

    // ── Subject IDs (must match supabase_setup.sql seed data) ─
    private static final String ID_PHYSICS   = "11111111-0000-0000-0000-000000000001";
    private static final String ID_CHEMISTRY = "11111111-0000-0000-0000-000000000002";
    private static final String ID_MATH      = "11111111-0000-0000-0000-000000000003";
    private static final String ID_BIOLOGY   = "11111111-0000-0000-0000-000000000004";

    // ── Chart data ────────────────────────────────────────────
    private double[] studyHours = new double[7];
    private double[] accuracy   = new double[7];

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (sidebarController != null) sidebarController.setActivePage("dashboard");

        // Fix ScrollPane viewport background
        Platform.runLater(() ->
                scrollPane.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );

        // Populate period ComboBox
        periodCombo.getItems().addAll("Weekly", "Monthly");
        periodCombo.setValue("Weekly");

        // Draw empty placeholders
        buildGoalRing(0, 5);
        drawChart(0, 0);

        // Kick off all DB loads on background thread
        String uid = SessionManager.getInstance().getUserId();
        if (uid != null) {
            Thread.ofVirtual().start(() -> {
                loadHeader(uid);
                loadSubjectProgress(uid);
                loadDailyGoal(uid);
                loadUpcomingTests(uid);
                loadPerformanceChart(uid, "Weekly");
            });
        } else {
            welcomeLabel.setText("Welcome back!");
            streakLabel.setText("Please log in again.");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  3.1  HEADER — name + streak
    // ════════════════════════════════════════════════════════════
    private void loadHeader(String userId) {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("profiles")
                    .select("name,streak,streak_last_date")
                    .eq("user_id", userId)
                    .limit(1)
                    .execute();

            if (!rows.isArray() || rows.isEmpty()) return;
            JsonNode p = rows.get(0);

            String name        = p.path("name").asText("Student");
            int    streak      = p.path("streak").asInt(0);
            String lastDateStr = p.path("streak_last_date").asText("");

            // Update streak if needed
            int finalStreak = handleStreakUpdate(userId, streak, lastDateStr);

            SessionManager.getInstance().setUserName(name);
            SessionManager.getInstance().setStreak(finalStreak);

            String firstName = name.split("\\s+")[0];
            int    fs        = finalStreak;

            Platform.runLater(() -> {
                welcomeLabel.setText("Welcome back, " + firstName + "!");
                streakLabel.setText(fs > 0
                        ? "You're on a " + fs + "-day study streak! 🔥"
                        : "Ready for today's HSC prep? Start your streak!");
            });

        } catch (Exception e) {
            System.err.println("[Header] " + e.getMessage());
            String name = SessionManager.getInstance().getUserName();
            String fn   = (name != null) ? name.split("\\s+")[0] : "Student";
            Platform.runLater(() -> {
                welcomeLabel.setText("Welcome back, " + fn + "!");
                streakLabel.setText("Ready for today's HSC prep!");
            });
        }
    }

    private int handleStreakUpdate(String userId, int current, String lastDateStr) {
        try {
            LocalDate today     = LocalDate.now();
            LocalDate yesterday = today.minusDays(1);

            if (lastDateStr == null || lastDateStr.isBlank()) {
                patchStreak(userId, 1, today); return 1;
            }
            LocalDate last = LocalDate.parse(lastDateStr.substring(0, 10));
            if (last.equals(today))     return current;
            if (last.equals(yesterday)) { patchStreak(userId, current + 1, today); return current + 1; }
            patchStreak(userId, 1, today); return 1;
        } catch (Exception e) {
            System.err.println("[Streak] " + e.getMessage());
            return current;
        }
    }

    private void patchStreak(String userId, int streak, LocalDate date) throws Exception {
        String body = String.format("{\"streak\":%d,\"streak_last_date\":\"%s\"}", streak, date);
        SupabaseClient.getInstance().from("profiles").eq("user_id", userId).update(body);
    }

    // ════════════════════════════════════════════════════════════
    //  3.2  SUBJECT PROGRESS CARDS
    // ════════════════════════════════════════════════════════════
    private void loadSubjectProgress(String userId) {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("user_progress")
                    .select("completed_pct,last_accessed_at,chapter:chapters(title,subject_id)")
                    .eq("user_id", userId)
                    .execute();

            // Aggregate by subject
            Map<String, List<Integer>> pcts  = new HashMap<>();
            Map<String, String>         last  = new HashMap<>();

            if (rows.isArray()) {
                for (JsonNode row : rows) {
                    int    pct      = row.path("completed_pct").asInt(0);
                    JsonNode chapter = row.path("chapter");
                    String subId    = chapter.path("subject_id").asText("");
                    String title    = chapter.path("title").asText("");
                    if (!subId.isBlank()) {
                        pcts.computeIfAbsent(subId, k -> new ArrayList<>()).add(pct);
                        last.put(subId, title); // last in list = most recently fetched
                    }
                }
            }

            int physPct = avg(pcts.get(ID_PHYSICS));
            int chemPct = avg(pcts.get(ID_CHEMISTRY));
            int mathPct = avg(pcts.get(ID_MATH));
            int bioPct  = avg(pcts.get(ID_BIOLOGY));

            Platform.runLater(() -> {
                applySubjectCard(physicsBar,   physicsProgressLabel,   physicsLastLabel,
                        physPct, last.getOrDefault(ID_PHYSICS,   ""));
                applySubjectCard(chemistryBar, chemistryProgressLabel, chemistryLastLabel,
                        chemPct, last.getOrDefault(ID_CHEMISTRY, ""));
                applySubjectCard(mathBar,      mathProgressLabel,      mathLastLabel,
                        mathPct, last.getOrDefault(ID_MATH,      ""));
                applySubjectCard(biologyBar,   biologyProgressLabel,   biologyLastLabel,
                        bioPct,  last.getOrDefault(ID_BIOLOGY,   ""));
            });

        } catch (Exception e) {
            System.err.println("[SubjectProgress] " + e.getMessage());
            Platform.runLater(() -> {
                for (Label l : new Label[]{physicsProgressLabel, chemistryProgressLabel,
                        mathProgressLabel, biologyProgressLabel})
                    l.setText("No data yet — start practicing!");
            });
        }
    }

    private void applySubjectCard(HBox bar, Label progLabel, Label lastLabel,
                                  int pct, String chapTitle) {
        progLabel.setText(pct + "% of Syllabus completed");
        if (!chapTitle.isBlank()) lastLabel.setText("Last: " + chapTitle);

        // Animate bar once parent width is known
        bar.getParent().layoutBoundsProperty().addListener((obs, ov, nv) -> {
            double pw = nv.getWidth();
            if (pw > 4 && bar.getPrefWidth() < 1) {
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.ZERO,        new KeyValue(bar.prefWidthProperty(), 0)),
                        new KeyFrame(Duration.millis(700), new KeyValue(bar.prefWidthProperty(), pw * pct / 100.0))
                );
                tl.play();
            }
        });
    }

    private int avg(List<Integer> vals) {
        if (vals == null || vals.isEmpty()) return 0;
        return (int) vals.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    // ════════════════════════════════════════════════════════════
    //  3.3  PERFORMANCE CHART
    // ════════════════════════════════════════════════════════════
    private void loadPerformanceChart(String userId, String period) {
        try {
            int     days  = "Monthly".equals(period) ? 30 : 7;
            String  since = LocalDate.now().minusDays(days).toString() + "T00:00:00";

            JsonNode sessions = SupabaseClient.getInstance()
                    .from("study_sessions")
                    .select("duration_mins,started_at")
                    .eq("user_id", userId)
                    .gte("started_at", since)
                    .execute();

            JsonNode attempts = SupabaseClient.getInstance()
                    .from("question_attempts")
                    .select("is_correct,attempted_at")
                    .eq("user_id", userId)
                    .gte("attempted_at", since)
                    .execute();

            double[] hrs   = new double[7];
            double[] corr  = new double[7];
            int[]    total = new int[7];
            LocalDate today = LocalDate.now();

            if (sessions.isArray()) {
                for (JsonNode s : sessions) {
                    String at   = s.path("started_at").asText("").substring(0, 10);
                    int    mins = s.path("duration_mins").asInt(0);
                    long   slot = today.toEpochDay() - LocalDate.parse(at).toEpochDay();
                    if (slot >= 0 && slot < 7) hrs[(int)(6 - slot)] += mins / 60.0;
                }
            }
            if (attempts.isArray()) {
                for (JsonNode a : attempts) {
                    String at      = a.path("attempted_at").asText("").substring(0, 10);
                    boolean ok     = a.path("is_correct").asBoolean(false);
                    long    slot   = today.toEpochDay() - LocalDate.parse(at).toEpochDay();
                    if (slot >= 0 && slot < 7) {
                        if (ok) corr[(int)(6 - slot)]++;
                        total[(int)(6 - slot)]++;
                    }
                }
            }

            // Accuracy scaled to same range as hours
            double maxH = Arrays.stream(hrs).max().orElse(1);
            double[] acc = new double[7];
            for (int i = 0; i < 7; i++)
                acc[i] = (total[i] > 0) ? (corr[i] / total[i]) * Math.max(maxH, 1) : 0;

            studyHours = hrs;
            accuracy   = acc;
            Platform.runLater(this::setupChartResize);

        } catch (Exception e) {
            System.err.println("[Chart] " + e.getMessage());
            Platform.runLater(this::setupChartResize);
        }
    }

    private void setupChartResize() {
        chartContainer.widthProperty().addListener((o, ov, nv) ->
                drawChart(nv.doubleValue(), chartContainer.getHeight()));
        chartContainer.heightProperty().addListener((o, ov, nv) ->
                drawChart(chartContainer.getWidth(), nv.doubleValue()));
        drawChart(chartContainer.getWidth(), chartContainer.getHeight());
    }

    private void drawChart(double w, double h) {
        if (w < 10 || h < 10) return;
        Canvas canvas = new Canvas(w, h);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        String[] days = {"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};
        double padL = 12, padR = 12, padB = 26, padT = 8;
        double chartW = w - padL - padR;
        double chartH = h - padB - padT;
        double groupW = chartW / 7;
        double barW   = groupW * 0.55;

        double maxVal = 1;
        for (double v : studyHours) maxVal = Math.max(maxVal, v);
        for (double v : accuracy)   maxVal = Math.max(maxVal, v);
        maxVal = Math.max(maxVal, 0.1) * 1.15;

        for (int i = 0; i < 7; i++) {
            double gx = padL + i * groupW + (groupW - barW) / 2;

            // Track (background)
            gc.setFill(Color.web("#0d7ff2", 0.12));
            fillRoundRect(gc, gx, padT, barW, chartH, 5);

            // Study hours bar
            double sh = chartH * (studyHours[i] / maxVal);
            if (sh > 0) { gc.setFill(Color.web("#0d7ff2")); fillRoundRect(gc, gx, padT + chartH - sh, barW, sh, 5); }

            // Accuracy overlay
            double ah = chartH * (accuracy[i] / maxVal);
            if (ah > 0) { gc.setFill(Color.web("#0d7ff2", 0.28)); fillRoundRect(gc, gx, padT + chartH - ah, barW, ah, 5); }

            // Day label
            gc.setFill(Color.web("#94a3b8"));
            gc.setFont(Font.font("Arial", 10));
            gc.fillText(days[i], gx + barW / 2 - 9, h - 6);
        }
        chartContainer.getChildren().setAll(canvas);
    }

    private void fillRoundRect(GraphicsContext gc, double x, double y, double w, double h, double r) {
        if (h <= 0 || w <= 0) return;
        gc.beginPath();
        gc.moveTo(x + r, y); gc.lineTo(x + w - r, y);
        gc.arcTo(x + w, y, x + w, y + r, r);
        gc.lineTo(x + w, y + h); gc.lineTo(x, y + h);
        gc.lineTo(x, y + r);
        gc.arcTo(x, y, x + r, y, r);
        gc.closePath(); gc.fill();
    }

    // ════════════════════════════════════════════════════════════
    //  3.4  DAILY GOAL RING
    // ════════════════════════════════════════════════════════════
    private void loadDailyGoal(String userId) {
        try {
            String today = LocalDate.now().toString();

            JsonNode rows = SupabaseClient.getInstance()
                    .from("daily_goals")
                    .select("target_hours,completed_hours,target_questions,completed_questions")
                    .eq("user_id", userId)
                    .eq("date", today)
                    .limit(1)
                    .execute();

            double done = 0, total = 5;
            int qDone = 0, qTotal = 10;

            if (rows.isArray() && rows.size() > 0) {
                JsonNode g = rows.get(0);
                done   = g.path("completed_hours").asDouble(0);
                total  = g.path("target_hours").asDouble(5);
                qDone  = g.path("completed_questions").asInt(0);
                qTotal = g.path("target_questions").asInt(10);
            } else {
                // Create today's goal row
                String body = String.format(
                        "{\"user_id\":\"%s\",\"date\":\"%s\","
                                + "\"target_hours\":5.0,\"completed_hours\":0,"
                                + "\"target_questions\":10,\"completed_questions\":0}", userId, today);
                SupabaseClient.getInstance().from("daily_goals").upsert(body);
            }

            final double fd = done, ft = total;
            final int fqd = qDone, fqt = qTotal;

            Platform.runLater(() -> {
                buildGoalRing(fd, ft);
                double rem = ft - fd;
                if (rem <= 0) {
                    goalStatusLabel.setText("Goal complete! 🎉");
                    goalSubLabel.setText("You've hit your daily target. Amazing work!");
                } else {
                    goalStatusLabel.setText("Almost there!");
                    goalSubLabel.setText(String.format(
                            "%.1f more hour%s to go. %d/%d questions done.",
                            rem, rem == 1.0 ? "" : "s", fqd, fqt));
                }
            });

        } catch (Exception e) {
            System.err.println("[Goal] " + e.getMessage());
            Platform.runLater(() -> {
                goalStatusLabel.setText("Goal");
                goalSubLabel.setText("Could not load today's goal.");
            });
        }
    }

    private void buildGoalRing(double done, double total) {
        final double SIZE = 150, CX = 75, CY = 75, R = 58, SW = 12;
        double frac  = (total > 0) ? Math.min(done / total, 1.0) : 0;
        double sweep = 360.0 * frac;

        Canvas canvas = new Canvas(SIZE, SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Static track
        gc.setStroke(Color.web("#f1f5f9"));
        gc.setLineWidth(SW);
        gc.strokeOval(CX - R, CY - R, R * 2, R * 2);

        // Center labels
        Label val = new Label((int) done + "/" + (int) total);
        val.setStyle("-fx-font-size:24px; -fx-font-weight:900; -fx-text-fill:#0f172a;");
        Label unit = new Label("HOURS");
        unit.setStyle("-fx-font-size:10px; -fx-font-weight:bold; -fx-text-fill:#64748b;");
        VBox center = new VBox(2, val, unit);
        center.setAlignment(Pos.CENTER);

        goalRingContainer.getChildren().setAll(canvas, center);

        // Animated arc
        final double[] cur = {0};
        Timeline tl = new Timeline(new KeyFrame(Duration.millis(16), e -> {
            cur[0] = Math.min(cur[0] + sweep / 50.0, sweep);
            gc.clearRect(0, 0, SIZE, SIZE);
            gc.setStroke(Color.web("#f1f5f9")); gc.setLineWidth(SW);
            gc.strokeOval(CX - R, CY - R, R * 2, R * 2);
            if (cur[0] > 0) {
                gc.setStroke(Color.web("#0d7ff2")); gc.setLineCap(StrokeLineCap.ROUND);
                gc.strokeArc(CX - R, CY - R, R * 2, R * 2, 90, -cur[0], ArcType.OPEN);
            }
        }));
        tl.setCycleCount(50);
        tl.play();
    }

    // ════════════════════════════════════════════════════════════
    //  3.5  UPCOMING TESTS
    // ════════════════════════════════════════════════════════════
    private void loadUpcomingTests(String userId) {
        try {
            String today = LocalDate.now().toString();

            JsonNode rows = SupabaseClient.getInstance()
                    .from("upcoming_tests")
                    .select("id,title,test_date,description,subject:subjects(name,color)")
                    .eq("user_id", userId)
                    .gte("test_date", today)
                    .order("test_date", true)
                    .limit(3)
                    .execute();

            List<javafx.scene.Node> nodes = new ArrayList<>();

            if (rows.isArray() && rows.size() > 0) {
                String[] bgs = {"#e8f3fe","#d1fae5","#fef3c7"};
                String[] fgs = {"#0d7ff2","#059669","#d97706"};
                int idx = 0;
                for (JsonNode row : rows) {
                    String dateStr = row.path("test_date").asText("").substring(0, 10);
                    LocalDate date = LocalDate.parse(dateStr);
                    String month   = date.getMonth().name().substring(0, 3);
                    String day     = String.valueOf(date.getDayOfMonth());
                    String title   = row.path("title").asText("Test");
                    String desc    = row.path("description").asText("");
                    String id      = row.path("id").asText("");

                    nodes.add(buildTestRow(month, day, title, desc,
                            bgs[idx % 3], fgs[idx % 3], id));
                    idx++;
                }
            } else {
                Label empty = new Label("No upcoming tests. Book one below!");
                empty.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
                nodes.add(empty);
            }

            Platform.runLater(() -> upcomingTestsBox.getChildren().setAll(nodes));

        } catch (Exception e) {
            System.err.println("[Tests] " + e.getMessage());
            Platform.runLater(() -> {
                Label err = new Label("Could not load upcoming tests.");
                err.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
                upcomingTestsBox.getChildren().setAll(err);
            });
        }
    }

    private HBox buildTestRow(String month, String day, String title,
                              String desc, String bg, String fg, String testId) {
        // Date box
        VBox dateBox = new VBox(0);
        dateBox.setAlignment(Pos.CENTER);
        dateBox.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:8; " +
                "-fx-min-width:46; -fx-min-height:46; -fx-pref-width:46; -fx-pref-height:46;");
        Label mLbl = new Label(month);
        mLbl.setStyle("-fx-font-size:9px; -fx-font-weight:bold; -fx-text-fill:" + fg + ";");
        Label dLbl = new Label(day);
        dLbl.setStyle("-fx-font-size:17px; -fx-font-weight:900; -fx-text-fill:" + fg + ";");
        dateBox.getChildren().addAll(mLbl, dLbl);

        // Info
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label tLbl = new Label(title);
        tLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
        Label dscLbl = new Label(desc.isBlank() ? "Tap for details" : desc);
        dscLbl.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        info.getChildren().addAll(tLbl, dscLbl);

        Label chevron = new Label("›");
        chevron.setStyle("-fx-font-size:16px; -fx-text-fill:#cbd5e1;");

        HBox row = new HBox(12, dateBox, info, chevron);
        row.setAlignment(Pos.CENTER_LEFT);
        String norm = "-fx-background-color:transparent; -fx-background-radius:8; " +
                "-fx-border-color:#f1f5f9; -fx-border-width:1; -fx-border-radius:8; " +
                "-fx-padding:10 12; -fx-cursor:hand;";
        String hover = "-fx-background-color:#f8fafc; -fx-background-radius:8; " +
                "-fx-border-color:#0d7ff2; -fx-border-width:1; -fx-border-radius:8; " +
                "-fx-padding:10 12; -fx-cursor:hand;";
        row.setStyle(norm);
        row.setOnMouseEntered(e -> row.setStyle(hover));
        row.setOnMouseExited(e  -> row.setStyle(norm));
        row.setOnMouseClicked(e -> showTestDetail(title, desc, month + " " + day));
        return row;
    }

    private void showTestDetail(String title, String desc, String date) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Test Details");
        a.setHeaderText(title);
        a.setContentText("Date: " + date + "\n\n" + (desc.isBlank() ? "No description." : desc));
        a.showAndWait();
    }

    // ════════════════════════════════════════════════════════════
    //  BUTTON ACTIONS
    // ════════════════════════════════════════════════════════════

    /** 3.1.1 — View Schedule */
    @FXML private void onViewSchedule() {
        openModal("/ScheduleModal.fxml", "View Schedule", 560, 500);
    }

    /** 3.1.2 — Start Quick Quiz */
    @FXML private void onStartQuiz() {
        String uid = SessionManager.getInstance().getUserId();
        if (uid == null) return;

        Thread.ofVirtual().start(() -> {
            try {
                // Fetch 10 questions: prioritise weak areas (most mistakes)
                JsonNode mistakes = SupabaseClient.getInstance()
                        .from("mistake_bank")
                        .select("question_id")
                        .eq("user_id", uid)
                        .limit(10)
                        .execute();

                List<String> ids = new ArrayList<>();
                if (mistakes.isArray())
                    for (JsonNode m : mistakes)
                        ids.add(m.path("question_id").asText());

                // If not enough, pad with random questions
                if (ids.size() < 10) {
                    JsonNode extra = SupabaseClient.getInstance()
                            .from("questions")
                            .select("id")
                            .limit(10 - ids.size())
                            .execute();
                    if (extra.isArray())
                        for (JsonNode q : extra) {
                            String qid = q.path("id").asText();
                            if (!ids.contains(qid)) ids.add(qid);
                        }
                }

                final List<String> finalIds = ids;
                Platform.runLater(() -> {
                    Stage stage = (Stage) welcomeLabel.getScene().getWindow();
                    QuestionNavigator.go(stage, finalIds, null,
                            "Quick Quiz", "All Subjects", "QUICK_QUIZ");
                });
            } catch (Exception e) {
                System.err.println("[QuickQuiz] " + e.getMessage());
            }
        });
    }

    /** 3.4 — Start Focus Session */
    @FXML private void onStartFocus() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/FocusSessionModal.fxml"));
            Parent root = loader.load();
            FocusSessionController ctrl = loader.getController();
            // Refresh goal ring after session ends
            ctrl.setOnSessionEnd(() -> {
                String uid = SessionManager.getInstance().getUserId();
                if (uid != null) Thread.ofVirtual().start(() -> loadDailyGoal(uid));
            });
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.initOwner(welcomeLabel.getScene().getWindow());
            modal.setTitle("Focus Session");
            modal.setScene(new Scene(root, 440, 400));
            modal.setResizable(false);
            modal.show();
        } catch (Exception e) {
            System.err.println("[FocusModal] " + e.getMessage());
        }
    }

    /** 3.5 — Book Mock Exam */
    @FXML private void onBookExam() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/ScheduleExamModal.fxml"));
            Parent root = loader.load();
            ScheduleExamController ctrl = loader.getController();
            ctrl.setOnSaved(() -> {
                String uid = SessionManager.getInstance().getUserId();
                if (uid != null) Thread.ofVirtual().start(() -> loadUpcomingTests(uid));
            });
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.initOwner(welcomeLabel.getScene().getWindow());
            modal.setTitle("Schedule Mock Exam");
            modal.setScene(new Scene(root, 480, 390));
            modal.setResizable(false);
            modal.show();
        } catch (Exception e) {
            System.err.println("[BookExam] " + e.getMessage());
        }
    }

    /** 3.6 — Find Tutor */
    @FXML private void onFindTutor() {
        try { Desktop.getDesktop().browse(new URI("https://www.google.com/search?q=HSC+tutor")); }
        catch (Exception e) { System.err.println("[Tutor] " + e.getMessage()); }
    }

    /** 3.6 — Resources */
    @FXML private void onResources() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Formula Sheets & Resources");
        a.setContentText("• HSC Physics Formula Sheet\n• Chemistry Data Sheet\n• Math Reference Sheet\n\n(PDF downloads — next sprint)");
        a.showAndWait();
    }

    /** Chart period toggle */
    @FXML private void onPeriodChanged() {
        String uid = SessionManager.getInstance().getUserId();
        if (uid != null)
            Thread.ofVirtual().start(() -> loadPerformanceChart(uid, periodCombo.getValue()));
    }

    /** Subject continue buttons */
    @FXML private void onContinuePhysics()   { navigateToSubjects(ID_PHYSICS);   }
    @FXML private void onContinueChemistry() { navigateToSubjects(ID_CHEMISTRY); }
    @FXML private void onContinueMath()      { navigateToSubjects(ID_MATH);      }
    @FXML private void onContinueBiology()   { navigateToSubjects(ID_BIOLOGY);   }
    @FXML private void onViewAllSubjects()   { navigateToSubjects(null);         }

    private void navigateToSubjects(String subjectId) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/SubjectsView.fxml"));
            Parent root = loader.load();
            SubjectsController ctrl = loader.getController();
            if (subjectId != null) ctrl.preselectSubject(subjectId);
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            System.err.println("[NavSubjects] " + e.getMessage());
        }
    }

    // ── Generic modal helper ──────────────────────────────────
    private void openModal(String fxmlPath, String title, int w, int h) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxmlPath));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.initOwner(welcomeLabel.getScene().getWindow());
            modal.setTitle(title);
            modal.setScene(new Scene(root, w, h));
            modal.setResizable(false);
            modal.show();
        } catch (Exception e) {
            System.err.println("[Modal " + fxmlPath + "] " + e.getMessage());
        }
    }
}