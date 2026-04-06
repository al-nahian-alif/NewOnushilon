package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class SubjectsController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane        mainScroll;

    // ── Header ───────────────────────────────────────────────
    @FXML private HBox   tabBar;
    @FXML private Button listViewBtn;
    @FXML private Button gridViewBtn;

    // ── Chapter area ─────────────────────────────────────────
    @FXML private Label             chaptersTitle;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private HBox              tableHeader;
    @FXML private VBox              chaptersContainer;

    // ── Smart review ─────────────────────────────────────────
    @FXML private HBox smartReviewBox;

    // ── Internal state ────────────────────────────────────────
    private String  activeSubjectId   = null;
    private String  activeSubjectName = "Physics";
    private boolean isListView        = true;   // 4.4 — persisted in memory

    // Tab button references keyed by subject id
    private final Map<String, Button> tabButtons = new LinkedHashMap<>();

    // Subject data loaded from DB
    private record Subject(String id, String name, String color, String icon) {}
    private final List<Subject> subjects = new ArrayList<>();

    // Chapter data model
    private record ChapterRow(
            String id, String title, String unit, int pct,
            String improvement, String impColor,
            int mistakes, boolean redBadge,
            String lastAccessed, boolean isLocked
    ) {}

    // ── Style constants ───────────────────────────────────────
    private static final String TAB_ACTIVE =
            "-fx-background-color:transparent; " +
                    "-fx-border-color:transparent transparent #0d7ff2 transparent; " +
                    "-fx-border-width:0 0 2.5 0; -fx-text-fill:#0d7ff2; " +
                    "-fx-font-size:13px; -fx-font-weight:bold; " +
                    "-fx-padding:0 4 14 4; -fx-cursor:hand; -fx-background-radius:0;";
    private static final String TAB_INACTIVE =
            "-fx-background-color:transparent; -fx-border-color:transparent; " +
                    "-fx-text-fill:#64748b; -fx-font-size:13px; -fx-font-weight:bold; " +
                    "-fx-padding:0 4 14 4; -fx-cursor:hand; -fx-background-radius:0;";

    private static final String VIEW_ACTIVE =
            "-fx-background-color:#0d7ff2; -fx-background-radius:6; " +
                    "-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; " +
                    "-fx-padding:6 14; -fx-cursor:hand;";
    private static final String VIEW_INACTIVE =
            "-fx-background-color:transparent; -fx-background-radius:6; " +
                    "-fx-text-fill:#64748b; -fx-font-size:12px; -fx-font-weight:bold; " +
                    "-fx-padding:6 14; -fx-cursor:hand;";

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (sidebarController != null) sidebarController.setActivePage("subjects");

        Platform.runLater(() ->
                mainScroll.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );

        // Load subjects from DB, then populate tabs and first subject
        Thread.ofVirtual().start(this::loadSubjectsAndInit);
    }

    // ════════════════════════════════════════════════════════════
    //  4.1  LOAD SUBJECTS → BUILD TABS
    // ════════════════════════════════════════════════════════════
    private void loadSubjectsAndInit() {
        try {
            JsonNode rows = SupabaseClient.getInstance()
                    .from("subjects")
                    .select("id,name,color,icon")
                    .order("name", true)
                    .execute();

            if (rows.isArray()) {
                for (JsonNode r : rows) {
                    subjects.add(new Subject(
                            r.path("id").asText(),
                            r.path("name").asText(),
                            r.path("color").asText("#0d7ff2"),
                            r.path("icon").asText("📚")
                    ));
                }
            }

            Platform.runLater(() -> {
                buildTabs();
                // Select first subject by default (or remembered one)
                if (!subjects.isEmpty()) {
                    String remembered = SessionManager.getInstance().getUserId() != null
                            ? activeSubjectId : null;
                    Subject first = subjects.stream()
                            .filter(s -> s.id().equals(remembered))
                            .findFirst()
                            .orElse(subjects.get(0));
                    selectSubject(first);
                }
            });

        } catch (Exception e) {
            System.err.println("[Subjects] Load error: " + e.getMessage());
            Platform.runLater(() -> chaptersTitle.setText("Could not load subjects."));
        }
    }

    private void buildTabs() {
        tabBar.getChildren().clear();
        tabButtons.clear();

        for (Subject s : subjects) {
            Button tab = new Button(s.icon() + "  " + s.name());
            tab.setStyle(TAB_INACTIVE);
            HBox.setMargin(tab, new javafx.geometry.Insets(0, 28, 0, 0));
            tab.setOnAction(e -> selectSubject(s));
            tabButtons.put(s.id(), tab);
            tabBar.getChildren().add(tab);
        }
    }

    /** 4.1 — Click a tab: update style + load data */
    private void selectSubject(Subject s) {
        activeSubjectId   = s.id();
        activeSubjectName = s.name();

        // Update tab styles
        tabButtons.forEach((id, btn) ->
                btn.setStyle(id.equals(s.id()) ? TAB_ACTIVE : TAB_INACTIVE));

        chaptersTitle.setText(s.name() + " Chapters");
        setLoading(true);

        // Load in background
        String uid = SessionManager.getInstance().getUserId();
        Thread.ofVirtual().start(() -> {
            loadSmartReview(uid, s.id(), s.name());
            loadChapters(uid, s.id());
        });
    }

    /** Called from DashboardController.navigateToSubjects() */
    public void preselectSubject(String subjectId) {
        subjects.stream()
                .filter(s -> s.id().equals(subjectId))
                .findFirst()
                .ifPresent(this::selectSubject);
    }

    // ════════════════════════════════════════════════════════════
    //  4.2  SMART REVIEW CARDS
    // ════════════════════════════════════════════════════════════
    private void loadSmartReview(String userId, String subjectId, String subjectName) {
        try {
            // ── HIGH IMPACT: chapter with most mistakes ──────────────
            // Fetch mistake_bank for this subject's chapters
            JsonNode mistakes = SupabaseClient.getInstance()
                    .from("mistake_bank")
                    .select("question:questions(chapter:chapters(id,title,subject_id))")
                    .eq("user_id", userId)
                    .execute();

            Map<String, Integer> chapterMistakes = new HashMap<>();
            Map<String, String>  chapterNames    = new HashMap<>();
            if (mistakes.isArray()) {
                for (JsonNode m : mistakes) {
                    JsonNode chapter = m.path("question").path("chapter");
                    String   sid     = chapter.path("subject_id").asText("");
                    String   cid     = chapter.path("id").asText("");
                    String   cname   = chapter.path("title").asText("");
                    if (sid.equals(subjectId) && !cid.isBlank()) {
                        chapterMistakes.merge(cid, 1, Integer::sum);
                        chapterNames.put(cid, cname);
                    }
                }
            }

            String highImpactChapterId   = null;
            String highImpactChapterName = "No chapter yet";
            int    highImpactCount       = 0;
            for (Map.Entry<String, Integer> e : chapterMistakes.entrySet()) {
                if (e.getValue() > highImpactCount) {
                    highImpactCount       = e.getValue();
                    highImpactChapterId   = e.getKey();
                    highImpactChapterName = chapterNames.get(e.getKey());
                }
            }

            // ── WEAK AREA: chapter not accessed in 12+ days ──────────
            String staleChapterId   = null;
            String staleChapterName = "All chapters up to date!";
            int    staleDays        = 0;

            JsonNode progress = SupabaseClient.getInstance()
                    .from("user_progress")
                    .select("chapter_id,last_accessed_at,chapter:chapters(title,subject_id)")
                    .eq("user_id", userId)
                    .execute();

            if (progress.isArray()) {
                for (JsonNode p : progress) {
                    String   sid       = p.path("chapter").path("subject_id").asText("");
                    String   cid       = p.path("chapter_id").asText("");
                    String   cname     = p.path("chapter").path("title").asText("");
                    String   lastStr   = p.path("last_accessed_at").asText("");
                    if (!sid.equals(subjectId) || lastStr.isBlank()) continue;
                    LocalDate last = LocalDate.parse(lastStr.substring(0, 10));
                    int days = (int)(LocalDate.now().toEpochDay() - last.toEpochDay());
                    if (days >= 12 && days > staleDays) {
                        staleDays        = days;
                        staleChapterId   = cid;
                        staleChapterName = cname;
                    }
                }
            }

            final String hiId   = highImpactChapterId;
            final String hiName = highImpactChapterName;
            final int    hiCnt  = highImpactCount;
            final String stId   = staleChapterId;
            final String stName = staleChapterName;
            final int    stDays = staleDays;

            Platform.runLater(() ->
                    buildSmartReviewCards(subjectName, hiId, hiName, hiCnt, stId, stName, stDays)
            );

        } catch (Exception e) {
            System.err.println("[SmartReview] " + e.getMessage());
            Platform.runLater(() -> {
                Label err = new Label("Smart review unavailable.");
                err.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
                smartReviewBox.getChildren().setAll(err);
            });
        }
    }

    private void buildSmartReviewCards(String subjectName,
                                       String hiId, String hiName, int hiCount,
                                       String stId, String stName, int stDays) {
        List<javafx.scene.Node> cards = new ArrayList<>();

        // ── HIGH IMPACT card ──────────────────────────────────
        VBox hiCard = new VBox(10);
        HBox.setHgrow(hiCard, Priority.ALWAYS);
        hiCard.setStyle("-fx-background-color:#ffffff; -fx-background-radius:14; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:14; " +
                "-fx-padding:22; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),8,0,0,2);");

        Label hiBadge = new Label("HIGH IMPACT");
        hiBadge.setStyle("-fx-background-color:#e8f3fe; -fx-background-radius:4; " +
                "-fx-text-fill:#0d7ff2; -fx-font-size:10px; -fx-font-weight:bold; -fx-padding:3 8;");

        Label hiTitle = new Label(hiName);
        hiTitle.setStyle("-fx-font-size:17px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");

        String hiBody = hiCount > 0
                ? "High mistake count detected (" + hiCount + " mistakes). Reviewing this will boost your score."
                : "Great job! No major mistake patterns in " + subjectName + " yet.";
        Label hiDesc = new Label(hiBody);
        hiDesc.setStyle("-fx-font-size:12px; -fx-text-fill:#64748b;");
        hiDesc.setWrapText(true);

        Button hiBtn = new Button("⚡  Start Review");
        hiBtn.setStyle("-fx-background-color:#0d7ff2; -fx-background-radius:8; " +
                "-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; " +
                "-fx-padding:8 18; -fx-cursor:hand;");
        hiBtn.setOnAction(e -> onStartReview(hiId, hiName));
        hiBtn.setDisable(hiId == null);

        hiCard.getChildren().addAll(hiBadge, hiTitle, hiDesc, hiBtn);
        cards.add(hiCard);

        // ── WEAK AREA card ────────────────────────────────────
        VBox stCard = new VBox(10);
        HBox.setHgrow(stCard, Priority.ALWAYS);
        stCard.setStyle("-fx-background-color:#ffffff; -fx-background-radius:14; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:14; " +
                "-fx-padding:22; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),8,0,0,2);");

        Label stBadge = new Label(stDays >= 12 ? "WEAK AREA" : "ON TRACK");
        stBadge.setStyle("-fx-background-color:" + (stDays >= 12 ? "#fef3c7" : "#f0fdf4") + "; " +
                "-fx-background-radius:4; -fx-text-fill:" +
                (stDays >= 12 ? "#d97706" : "#16a34a") + "; " +
                "-fx-font-size:10px; -fx-font-weight:bold; -fx-padding:3 8;");

        Label stTitle = new Label(stName);
        stTitle.setStyle("-fx-font-size:17px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");

        String stBody = stDays >= 12
                ? "You haven't practiced this chapter in " + stDays + " days. Mistake rate may be increasing."
                : "All your " + subjectName + " chapters have been accessed recently. Keep it up!";
        Label stDesc = new Label(stBody);
        stDesc.setStyle("-fx-font-size:12px; -fx-text-fill:#64748b;");
        stDesc.setWrapText(true);

        Button stBtn = new Button("🃏  Flashcards");
        stBtn.setStyle("-fx-background-color:#f1f5f9; -fx-background-radius:8; " +
                "-fx-text-fill:#0f172a; -fx-font-size:12px; -fx-font-weight:bold; " +
                "-fx-padding:8 18; -fx-cursor:hand;");
        stBtn.setOnAction(e -> onFlashcards(stId, stName));
        stBtn.setDisable(stId == null);

        stCard.getChildren().addAll(stBadge, stTitle, stDesc, stBtn);
        cards.add(stCard);

        smartReviewBox.getChildren().setAll(cards);
    }

    // ════════════════════════════════════════════════════════════
    //  4.3  CHAPTERS TABLE / GRID
    // ════════════════════════════════════════════════════════════
    private void loadChapters(String userId, String subjectId) {
        try {
            // Fetch chapters
            JsonNode chapters = SupabaseClient.getInstance()
                    .from("chapters")
                    .select("id,title,unit,order_index,is_locked")
                    .eq("subject_id", subjectId)
                    .order("order_index", true)
                    .execute();

            if (!chapters.isArray() || chapters.isEmpty()) {
                Platform.runLater(() -> {
                    setLoading(false);
                    Label empty = new Label("No chapters found for this subject.");
                    empty.setStyle("-fx-font-size:13px; -fx-text-fill:#94a3b8; -fx-padding:20 24;");
                    chaptersContainer.getChildren().setAll(empty);
                });
                return;
            }

            // Collect chapter IDs
            List<String> chapterIds = new ArrayList<>();
            for (JsonNode c : chapters) chapterIds.add(c.path("id").asText());

            // Fetch user_progress for all chapters at once
            Map<String, Integer>   progressMap  = new HashMap<>();
            Map<String, LocalDate> lastAccessMap = new HashMap<>();

            JsonNode progress = SupabaseClient.getInstance()
                    .from("user_progress")
                    .select("chapter_id,completed_pct,last_accessed_at")
                    .eq("user_id", userId)
                    .execute();

            if (progress.isArray()) {
                for (JsonNode p : progress) {
                    String cid = p.path("chapter_id").asText("");
                    if (chapterIds.contains(cid)) {
                        progressMap.put(cid, p.path("completed_pct").asInt(0));
                        String lastStr = p.path("last_accessed_at").asText("");
                        if (!lastStr.isBlank())
                            lastAccessMap.put(cid, LocalDate.parse(lastStr.substring(0, 10)));
                    }
                }
            }

            // Fetch mistake counts
            Map<String, Integer> mistakeMap = new HashMap<>();
            JsonNode mistakeRows = SupabaseClient.getInstance()
                    .from("mistake_bank")
                    .select("question:questions(chapter_id)")
                    .eq("user_id", userId)
                    .execute();

            if (mistakeRows.isArray()) {
                for (JsonNode m : mistakeRows) {
                    String cid = m.path("question").path("chapter_id").asText("");
                    if (chapterIds.contains(cid))
                        mistakeMap.merge(cid, 1, Integer::sum);
                }
            }

            // Fetch question_attempts for improvement calculation
            // This week vs last week accuracy per chapter
            String weekAgo     = LocalDate.now().minusDays(7).toString()  + "T00:00:00";
            String twoWeeksAgo = LocalDate.now().minusDays(14).toString() + "T00:00:00";

            Map<String, double[]> thisWeekAcc  = new HashMap<>(); // [correct, total]
            Map<String, double[]> lastWeekAcc  = new HashMap<>();

            JsonNode attempts = SupabaseClient.getInstance()
                    .from("question_attempts")
                    .select("is_correct,attempted_at,question:questions(chapter_id)")
                    .eq("user_id", userId)
                    .gte("attempted_at", twoWeeksAgo)
                    .execute();

            if (attempts.isArray()) {
                for (JsonNode a : attempts) {
                    String   cid     = a.path("question").path("chapter_id").asText("");
                    String   atStr   = a.path("attempted_at").asText("").substring(0, 10);
                    boolean  correct = a.path("is_correct").asBoolean(false);
                    if (!chapterIds.contains(cid) || atStr.isBlank()) continue;

                    LocalDate atDate = LocalDate.parse(atStr);
                    if (!atDate.isBefore(LocalDate.parse(weekAgo.substring(0, 10)))) {
                        thisWeekAcc.computeIfAbsent(cid, k -> new double[2]);
                        thisWeekAcc.get(cid)[1]++;
                        if (correct) thisWeekAcc.get(cid)[0]++;
                    } else {
                        lastWeekAcc.computeIfAbsent(cid, k -> new double[2]);
                        lastWeekAcc.get(cid)[1]++;
                        if (correct) lastWeekAcc.get(cid)[0]++;
                    }
                }
            }

            // Build ChapterRow models
            List<ChapterRow> rows = new ArrayList<>();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

            for (JsonNode c : chapters) {
                String  cid      = c.path("id").asText();
                String  title    = c.path("title").asText();
                String  unit     = c.path("unit").asText("");
                boolean locked   = c.path("is_locked").asBoolean(false);
                int     pct      = progressMap.getOrDefault(cid, 0);
                int     mistakes = mistakeMap.getOrDefault(cid, 0);
                String  lastStr  = lastAccessMap.containsKey(cid)
                        ? lastAccessMap.get(cid).format(fmt) : "Not started";

                // Improvement %
                double[] tw  = thisWeekAcc.getOrDefault(cid, new double[]{0, 0});
                double[] lw  = lastWeekAcc.getOrDefault(cid, new double[]{0, 0});
                String   imp;
                String   impColor;
                if (tw[1] == 0) {
                    imp = "→ New"; impColor = "#94a3b8";
                } else {
                    double twAcc = tw[0] / tw[1];
                    double lwAcc = lw[1] > 0 ? lw[0] / lw[1] : twAcc;
                    int    diff  = (int) Math.round((twAcc - lwAcc) * 100);
                    if (diff > 0)      { imp = "↑ +" + diff + "%"; impColor = "#22c55e"; }
                    else if (diff < 0) { imp = "↓ " + diff + "%";  impColor = "#ef4444"; }
                    else               { imp = "→ 0%";              impColor = "#94a3b8"; }
                }

                rows.add(new ChapterRow(cid, title, unit, pct, imp, impColor,
                        mistakes, mistakes > 5, lastStr, locked));
            }

            Platform.runLater(() -> {
                setLoading(false);
                if (isListView) renderListView(rows);
                else            renderGridView(rows);
            });

        } catch (Exception e) {
            System.err.println("[Chapters] " + e.getMessage());
            Platform.runLater(() -> {
                setLoading(false);
                Label err = new Label("Could not load chapters: " + e.getMessage());
                err.setStyle("-fx-font-size:13px; -fx-text-fill:#ef4444; -fx-padding:20 24;");
                chaptersContainer.getChildren().setAll(err);
            });
        }
    }

    // ════════════════════════════════════════════════════════════
    //  4.4  LIST VIEW
    // ════════════════════════════════════════════════════════════
    private void renderListView(List<ChapterRow> rows) {
        tableHeader.setVisible(true);
        tableHeader.setManaged(true);
        chaptersContainer.setStyle(
                "-fx-background-color:#ffffff; -fx-background-radius:0 0 12 12; " +
                        "-fx-border-color:#e2e8f0; -fx-border-width:0 1 1 1; -fx-border-radius:0 0 12 12;");

        List<javafx.scene.Node> nodes = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            ChapterRow r = rows.get(i);
            boolean isLast = (i == rows.size() - 1);
            nodes.add(buildListRow(r, isLast));
        }
        chaptersContainer.getChildren().setAll(nodes);
    }

    private HBox buildListRow(ChapterRow r, boolean isLast) {
        String borderBottom = isLast ? "0" : "1";
        String normStyle = "-fx-padding:16 24; -fx-cursor:" + (r.isLocked() ? "default" : "hand") + "; " +
                "-fx-background-color:transparent; " +
                "-fx-border-color:transparent transparent #f1f5f9 transparent; " +
                "-fx-border-width:0 0 " + borderBottom + " 0;";
        String hoverStyle = "-fx-padding:16 24; -fx-cursor:hand; " +
                "-fx-background-color:#f8fafc; " +
                "-fx-border-color:transparent transparent #f1f5f9 transparent; " +
                "-fx-border-width:0 0 " + borderBottom + " 0;";

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle(normStyle);
        if (!r.isLocked()) {
            row.setOnMouseEntered(e -> row.setStyle(hoverStyle));
            row.setOnMouseExited(e  -> row.setStyle(normStyle));
        }
        if (r.isLocked()) row.setOpacity(0.55);

        // ── Col 0: Chapter name + View Notes ─────────────────
        VBox nameCol = new VBox(4);
        nameCol.setPrefWidth(220);
        Label nameLabel = new Label(r.isLocked() ? "🔒  " + r.title() : r.title());
        nameLabel.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");

        HBox notesRow = new HBox(4);
        notesRow.setAlignment(Pos.CENTER_LEFT);
        Label notesIcon = new Label("📄");
        notesIcon.setStyle("-fx-font-size:10px;");
        Label notesLink = new Label("View Notes");
        notesLink.setStyle("-fx-font-size:11px; -fx-text-fill:#0d7ff2; -fx-cursor:hand;");
        notesLink.setOnMouseClicked(e -> onViewNotes(r.id(), r.title()));
        notesRow.getChildren().addAll(notesIcon, notesLink);
        nameCol.getChildren().addAll(nameLabel, notesRow);

        // ── Col 1: Progress bar + % ───────────────────────────
        HBox progCol = new HBox(10);
        progCol.setPrefWidth(170);
        progCol.setAlignment(Pos.CENTER_LEFT);

        StackPane track = new StackPane();
        track.setPrefWidth(90); track.setPrefHeight(6);
        track.setMinHeight(6);  track.setMaxHeight(6);
        HBox trackBg = new HBox();
        trackBg.setMaxWidth(Double.MAX_VALUE);
        trackBg.setPrefHeight(6);
        trackBg.setStyle("-fx-background-color:#f1f5f9; -fx-background-radius:100;");
        HBox fill = new HBox();
        fill.setPrefHeight(6);
        fill.setStyle("-fx-background-color:#0d7ff2; -fx-background-radius:100;");
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        track.getChildren().addAll(trackBg, fill);

        // Animate progress bar
        animateBar(fill, track, r.pct());

        Label pctLabel = new Label(r.pct() + "%");
        pctLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:#475569;");
        progCol.getChildren().addAll(track, pctLabel);

        // ── Col 2: Improvement ────────────────────────────────
        HBox impCol = new HBox(4);
        impCol.setPrefWidth(130);
        impCol.setAlignment(Pos.CENTER_LEFT);
        Label impLabel = new Label(r.improvement());
        impLabel.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:" + r.impColor() + ";");
        impCol.getChildren().add(impLabel);

        // ── Col 3: Mistakes badge ─────────────────────────────
        StackPane mistakePane = new StackPane();
        mistakePane.setPrefWidth(90);
        mistakePane.setAlignment(Pos.CENTER_LEFT);
        Label mistakeLbl = new Label(String.valueOf(r.mistakes()));
        mistakeLbl.setStyle(
                "-fx-background-color:" + (r.redBadge() ? "#fee2e2" : "#f1f5f9") + "; " +
                        "-fx-background-radius:50; -fx-text-fill:" + (r.redBadge() ? "#dc2626" : "#475569") + "; " +
                        "-fx-font-size:11px; -fx-font-weight:bold; -fx-padding:4 10;");
        mistakePane.getChildren().add(mistakeLbl);

        // ── Col 4: Last accessed ──────────────────────────────
        Label lastLbl = new Label(r.lastAccessed());
        lastLbl.setPrefWidth(130);
        lastLbl.setStyle("-fx-font-size:12px; -fx-text-fill:#64748b;");

        // ── Col 5: Action buttons ─────────────────────────────
        HBox actions = new HBox(8);
        HBox.setHgrow(actions, Priority.ALWAYS);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (!r.isLocked()) {
            Button practiceBtn = new Button("Practice");
            practiceBtn.setStyle("-fx-background-color:#0d7ff2; -fx-background-radius:8; " +
                    "-fx-text-fill:white; -fx-font-size:11px; -fx-font-weight:bold; " +
                    "-fx-padding:6 14; -fx-cursor:hand;");
            practiceBtn.setOnAction(e -> onPracticeChapter(r.id(), r.title()));

            Button cardsBtn = new Button("Cards");
            cardsBtn.setStyle("-fx-background-color:#f1f5f9; -fx-background-radius:8; " +
                    "-fx-text-fill:#334155; -fx-font-size:11px; -fx-font-weight:bold; " +
                    "-fx-padding:6 14; -fx-cursor:hand;");
            cardsBtn.setOnAction(e -> onFlashcards(r.id(), r.title()));
            actions.getChildren().addAll(practiceBtn, cardsBtn);
        } else {
            Label locked = new Label("LOCKED");
            locked.setStyle("-fx-background-color:#f1f5f9; -fx-background-radius:8; " +
                    "-fx-text-fill:#94a3b8; -fx-font-size:11px; -fx-font-weight:bold; " +
                    "-fx-padding:6 14;");
            actions.getChildren().add(locked);
        }

        row.getChildren().addAll(nameCol, progCol, impCol, mistakePane, lastLbl, actions);
        return row;
    }

    // ════════════════════════════════════════════════════════════
    //  4.4  GRID VIEW
    // ════════════════════════════════════════════════════════════
    private void renderGridView(List<ChapterRow> rows) {
        tableHeader.setVisible(false);
        tableHeader.setManaged(false);
        chaptersContainer.setStyle(
                "-fx-background-color:transparent; -fx-border-color:transparent; -fx-padding:0;");

        GridPane grid = new GridPane();
        grid.setHgap(16); grid.setVgap(16);
        grid.getColumnConstraints().addAll(
                colConstraint(), colConstraint()
        );

        for (int i = 0; i < rows.size(); i++) {
            grid.add(buildGridCard(rows.get(i)), i % 2, i / 2);
        }

        chaptersContainer.getChildren().setAll(grid);
    }

    private ColumnConstraints colConstraint() {
        ColumnConstraints cc = new ColumnConstraints();
        cc.setHgrow(Priority.ALWAYS);
        cc.setPercentWidth(50);
        return cc;
    }

    private VBox buildGridCard(ChapterRow r) {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color:#ffffff; -fx-background-radius:14; " +
                "-fx-border-color:#e2e8f0; -fx-border-width:1; -fx-border-radius:14; " +
                "-fx-padding:20; -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.05),6,0,0,2);" +
                (r.isLocked() ? "-fx-opacity:0.55;" : ""));

        // Ring + title
        HBox topRow = new HBox(16);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Mini progress ring
        StackPane ring = new StackPane();
        ring.setPrefSize(56, 56); ring.setMinSize(56, 56); ring.setMaxSize(56, 56);
        Canvas canvas = new Canvas(56, 56);
        drawMiniRing(canvas, r.pct(), Color.web("#0d7ff2"));
        Label ringLabel = new Label(r.pct() + "%");
        ringLabel.setStyle("-fx-font-size:11px; -fx-font-weight:900; -fx-text-fill:#0f172a;");
        ring.getChildren().addAll(canvas, ringLabel);

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(r.isLocked() ? "🔒  " + r.title() : r.title());
        title.setStyle("-fx-font-size:14px; -fx-font-weight:bold; -fx-text-fill:#0f172a;");
        title.setWrapText(true);
        Label unit = new Label(r.unit() + "  •  " + r.mistakes() + " mistakes");
        unit.setStyle("-fx-font-size:11px; -fx-text-fill:#94a3b8;");
        Label lastLabel = new Label(r.lastAccessed());
        lastLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#64748b;");
        info.getChildren().addAll(title, unit, lastLabel);

        topRow.getChildren().addAll(ring, info);

        // Improvement
        Label imp = new Label(r.improvement());
        imp.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:" + r.impColor() + ";");

        // Buttons
        HBox btns = new HBox(8);
        if (!r.isLocked()) {
            Button pr = new Button("Practice");
            pr.setStyle("-fx-background-color:#0d7ff2; -fx-background-radius:8; " +
                    "-fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; " +
                    "-fx-padding:7 0; -fx-cursor:hand;");
            HBox.setHgrow(pr, Priority.ALWAYS); pr.setMaxWidth(Double.MAX_VALUE);
            pr.setOnAction(e -> onPracticeChapter(r.id(), r.title()));

            Button ca = new Button("Cards");
            ca.setStyle("-fx-background-color:#f1f5f9; -fx-background-radius:8; " +
                    "-fx-text-fill:#334155; -fx-font-size:12px; -fx-font-weight:bold; " +
                    "-fx-padding:7 0; -fx-cursor:hand;");
            HBox.setHgrow(ca, Priority.ALWAYS); ca.setMaxWidth(Double.MAX_VALUE);
            ca.setOnAction(e -> onFlashcards(r.id(), r.title()));
            btns.getChildren().addAll(pr, ca);
        } else {
            Label lk = new Label("🔒 Locked");
            lk.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8;");
            btns.getChildren().add(lk);
        }

        card.getChildren().addAll(topRow, imp, btns);
        return card;
    }

    private void drawMiniRing(Canvas canvas, int pct, Color color) {
        double cx = 28, cy = 28, r = 22, sw = 4;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setStroke(Color.web("#f1f5f9")); gc.setLineWidth(sw);
        gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
        if (pct > 0) {
            gc.setStroke(color); gc.setLineCap(StrokeLineCap.ROUND);
            gc.strokeArc(cx - r, cy - r, r * 2, r * 2,
                    90, -(360.0 * pct / 100), ArcType.OPEN);
        }
    }

    // ── Animate progress bar ──────────────────────────────────
    private void animateBar(HBox fill, StackPane track, int pct) {
        track.widthProperty().addListener((obs, ov, nv) -> {
            double pw = nv.doubleValue();
            if (pw > 4 && fill.getPrefWidth() < 1) {
                Timeline tl = new Timeline(
                        new KeyFrame(Duration.ZERO,        new KeyValue(fill.prefWidthProperty(), 0)),
                        new KeyFrame(Duration.millis(600), new KeyValue(fill.prefWidthProperty(), pw * pct / 100.0))
                );
                tl.play();
            }
        });
    }

    // ════════════════════════════════════════════════════════════
    //  VIEW TOGGLE (4.4)
    // ════════════════════════════════════════════════════════════
    @FXML private void onListView() {
        if (isListView) return;
        isListView = true;
        listViewBtn.setStyle(VIEW_ACTIVE);
        gridViewBtn.setStyle(VIEW_INACTIVE);
        tableHeader.setVisible(true);
        tableHeader.setManaged(true);
        // Reload in list mode
        if (activeSubjectId != null) {
            setLoading(true);
            String uid = SessionManager.getInstance().getUserId();
            Thread.ofVirtual().start(() -> loadChapters(uid, activeSubjectId));
        }
    }

    @FXML private void onGridView() {
        if (!isListView) return;
        isListView = false;
        gridViewBtn.setStyle(VIEW_ACTIVE);
        listViewBtn.setStyle(VIEW_INACTIVE);
        tableHeader.setVisible(false);
        tableHeader.setManaged(false);
        if (activeSubjectId != null) {
            setLoading(true);
            String uid = SessionManager.getInstance().getUserId();
            Thread.ofVirtual().start(() -> loadChapters(uid, activeSubjectId));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  ACTION HANDLERS
    // ════════════════════════════════════════════════════════════
    private void onStartReview(String chapterId, String chapterName) {
        if (chapterId == null) return;
        Stage stage = (Stage) chaptersTitle.getScene().getWindow();
        QuestionNavigator.go(stage, chapterId, chapterName, activeSubjectName, "REVIEW");
    }

    private void onFlashcards(String chapterId, String chapterName) {
        // FlashcardView coming in next sprint
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Flashcards");
        a.setContentText("FlashcardView for: " + chapterName + "\nComing in next sprint!");
        a.showAndWait();
    }

    private void onPracticeChapter(String chapterId, String chapterName) {
        Stage stage = (Stage) chaptersTitle.getScene().getWindow();
        QuestionNavigator.go(stage, chapterId, chapterName, activeSubjectName, "PRACTICE");
    }

    private void onViewNotes(String chapterId, String chapterName) {
        showComingSoon("View Notes", "NotesView for: " + chapterName);
    }

    private void showComingSoon(String feature, String detail) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText(feature);
        a.setContentText(detail + "\n\nComing in the Question Engine sprint!");
        a.showAndWait();
    }

    // ── Loading state ─────────────────────────────────────────
    private void setLoading(boolean loading) {
        loadingSpinner.setVisible(loading);
        loadingSpinner.setManaged(loading);
        if (loading) {
            Label lbl = new Label("Loading chapters...");
            lbl.setStyle("-fx-font-size:13px; -fx-text-fill:#94a3b8; -fx-padding:20 24;");
            chaptersContainer.getChildren().setAll(lbl);
        }
    }
}