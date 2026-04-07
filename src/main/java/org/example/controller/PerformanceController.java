package org.example.controller;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.example.controller.QuestionNavigator;
import org.example.supabase.SessionManager;
import org.example.supabase.SupabaseClient;

import java.net.URL;
import java.time.LocalDate;
import java.util.*;

public class PerformanceController implements Initializable {

    // ── Sidebar ──────────────────────────────────────────────
    @FXML private SidebarController sidebarController;
    @FXML private ScrollPane        mainScroll;

    // ── Period toggle ─────────────────────────────────────────
    @FXML private Button btnDaily, btnWeekly, btnAllTime;

    // ── Stat cards ────────────────────────────────────────────
    @FXML private Label xpLabel, xpChangeBadge;
    @FXML private Label rankLabel, rankChangeBadge;
    @FXML private Label accuracyLabel, accuracyChangeBadge;
    @FXML private Label streakLabel, streakBadge;

    // ── Leaderboard ───────────────────────────────────────────
    @FXML private VBox  leaderboardBox;
    @FXML private Label realtimeDot, realtimeLabel;

    // ── Achievements ──────────────────────────────────────────
    @FXML private Label achievementCountLabel;
    @FXML private HBox  unlockedRow, lockedRow;

    // ── Growth Tip ────────────────────────────────────────────
    @FXML private Label  growthTipLabel;
    @FXML private Button startPracticeBtn;

    // ── Toast ─────────────────────────────────────────────────
    @FXML private StackPane toastPane;
    @FXML private VBox      toastBox;
    @FXML private Label     toastAchievementName;

    // ── State ─────────────────────────────────────────────────
    private String activePeriod = "daily";
    private String growthTipChapterId;

    private static final String PERIOD_ACTIVE =
            "-fx-background-color:#0d7ff2; -fx-background-radius:8; " +
                    "-fx-text-fill:white; -fx-font-size:13px; -fx-font-weight:600; " +
                    "-fx-padding:7 18; -fx-cursor:hand;";
    private static final String PERIOD_INACTIVE =
            "-fx-background-color:transparent; -fx-background-radius:8; " +
                    "-fx-text-fill:#475569; -fx-font-size:13px; -fx-font-weight:600; " +
                    "-fx-padding:7 18; -fx-cursor:hand;";

    // ════════════════════════════════════════════════════════════
    //  INITIALIZE
    // ════════════════════════════════════════════════════════════
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (sidebarController != null) sidebarController.setActivePage("performance");

        Platform.runLater(() ->
                mainScroll.lookupAll(".viewport")
                        .forEach(n -> n.setStyle("-fx-background-color:#f5f7f8;"))
        );

        for (Label l : new Label[]{xpLabel, rankLabel, accuracyLabel, streakLabel})
            l.setText("...");

        String uid = SessionManager.getInstance().getUserId();
        if (uid != null)
            Thread.ofVirtual().start(() -> loadAllParallel(uid, "daily"));
    }

    // ════════════════════════════════════════════════════════════
    //  PARALLEL LOAD
    //  FIX: restored leaderboard_cache for accurate realtime xp & global rank
    // ════════════════════════════════════════════════════════════
    private void loadAllParallel(String uid, String period) {
        try {
            String periodSince = switch (period) {
                case "weekly"  -> LocalDate.now().minusDays(7).toString()  + "T00:00:00";
                case "alltime" -> "2020-01-01T00:00:00";
                default        -> LocalDate.now().toString() + "T00:00:00";
            };
            String prevSince = switch (period) {
                case "weekly"  -> LocalDate.now().minusDays(14).toString() + "T00:00:00";
                case "alltime" -> "2010-01-01T00:00:00";
                default        -> LocalDate.now().minusDays(1).toString()  + "T00:00:00";
            };

            var client  = SupabaseClient.getInstance();
            var results = client.fetchAll(Map.of(
                    // Profile: Streak fallback & Level
                    "profile",      client.from("profiles")
                            .select("xp,streak,level")
                            .eq("user_id", uid).limit(1),
                    // Leaderboard: top 5 from cache, joining profiles for the name
                    "leaderTop",    client.from("leaderboard_cache")
                            .select("user_id,xp,rank,profiles(name)")
                            .order("rank", true).limit(5),
                    // Current User's realtime cache entry
                    "myCache",      client.from("leaderboard_cache")
                            .select("xp,rank")
                            .eq("user_id", uid).limit(1),
                    // This period's attempts
                    "attempts",     client.from("question_attempts")
                            .select("is_correct,attempted_at,question:questions(chapter_id)")
                            .eq("user_id", uid).gte("attempted_at", periodSince),
                    // Previous period's attempts (for change badge)
                    "prevAttempts", client.from("question_attempts")
                            .select("is_correct")
                            .eq("user_id", uid)
                            .gte("attempted_at", prevSince)
                            .lte("attempted_at", periodSince),
                    // All achievement definitions
                    "achievements", client.from("achievements")
                            .select("id,key,title,description,icon,xp_reward"),
                    // User's unlocked achievements
                    "userAchieve",  client.from("user_achievements")
                            .select("achievement_id")
                            .eq("user_id", uid)
            ));

            int myXp = 0;
            JsonNode profile = results.get("profile");
            if (profile != null && profile.isArray() && profile.size() > 0) {
                myXp = profile.get(0).path("xp").asInt(0);
            }

            int globalRank = 0;
            JsonNode myCache = results.get("myCache");
            if (myCache != null && myCache.isArray() && myCache.size() > 0) {
                myXp = myCache.get(0).path("xp").asInt(myXp); // Prioritize cache XP
                globalRank = myCache.get(0).path("rank").asInt(0);
            } else {
                globalRank = calculateRank(uid, myXp);
            }

            processStats(results.get("profile"), globalRank,
                    results.get("attempts"), results.get("prevAttempts"), myXp);
            processLeaderboard(uid, results.get("leaderTop"));
            processAchievements(uid, results.get("achievements"), results.get("userAchieve"));
            processGrowthTip(uid, results.get("attempts"));
            checkNewAchievements(uid, results.get("attempts"),
                    results.get("userAchieve"), results.get("achievements"));

            Platform.runLater(() -> startRealtimePolling(uid));

        } catch (Exception e) {
            System.err.println("[Performance] Load error: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  RANK CALCULATION (Fallback)
    // ════════════════════════════════════════════════════════════
    private int calculateRank(String uid, int myXp) {
        try {
            JsonNode higher = SupabaseClient.getInstance()
                    .from("profiles")
                    .select("user_id")
                    .gte("xp", String.valueOf(myXp + 1))
                    .execute();
            return (higher.isArray() ? higher.size() : 0) + 1;
        } catch (Exception e) {
            System.err.println("[Rank Fallback] " + e.getMessage());
            return 0; // Return 0 to display "Unranked"
        }
    }

    // ════════════════════════════════════════════════════════════
    //  6.1  STAT CARDS
    // ════════════════════════════════════════════════════════════
    private void processStats(JsonNode profile, int globalRank,
                              JsonNode attempts, JsonNode prevAttempts, int currentXp) {
        int streak = 0;
        if (profile != null && profile.isArray() && profile.size() > 0) {
            streak = profile.get(0).path("streak").asInt(0);
        }

        int[] thisAcc  = calcAccuracy(attempts);
        int[] prevAcc  = calcAccuracy(prevAttempts);
        int   accChange = thisAcc[0] - prevAcc[0];
        int   xpGain   = (attempts != null && attempts.isArray()) ? attempts.size() * 10 : 0;

        final int fxp = currentXp, frank = globalRank, facc = thisAcc[0],
                fstreak = streak, fxpGain = xpGain, faccChange = accChange;

        Platform.runLater(() -> {
            // XP
            xpLabel.setText(String.format("%,d", fxp));
            setChangeBadge(xpChangeBadge, fxpGain, true);

            // Rank
            rankLabel.setText(frank > 0 ? "#" + frank : "Unranked");
            rankChangeBadge.setText(frank > 0 ? "Rank #" + frank : "—");
            rankChangeBadge.setStyle(
                    "-fx-background-color:#f0fdf4; -fx-background-radius:50; " +
                            "-fx-text-fill:#22c55e; -fx-font-size:11px; " +
                            "-fx-font-weight:bold; -fx-padding:3 8;");

            // Accuracy
            accuracyLabel.setText(facc + "%");
            setChangeBadge(accuracyChangeBadge, faccChange, false);

            // Streak
            streakLabel.setText(fstreak + " Days");
            streakBadge.setText(fstreak > 0 ? "🔥 Active" : "Inactive");
            streakBadge.setStyle(
                    "-fx-background-color:" + (fstreak > 0 ? "#f0fdf4" : "#f8fafc") +
                            "; -fx-background-radius:50; -fx-text-fill:" +
                            (fstreak > 0 ? "#22c55e" : "#94a3b8") +
                            "; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:3 8;");
        });
    }

    private int[] calcAccuracy(JsonNode attempts) {
        if (attempts == null || !attempts.isArray() || attempts.isEmpty())
            return new int[]{0, 0, 0};
        int correct = 0, total = 0;
        for (JsonNode a : attempts) {
            total++;
            if (a.path("is_correct").asBoolean(false)) correct++;
        }
        return new int[]{total > 0 ? correct * 100 / total : 0, correct, total};
    }

    private void setChangeBadge(Label badge, int change, boolean isXp) {
        String text, bg, fg;
        if (change > 0)      { text = isXp ? "+" + change + " XP" : "+" + change + "%"; bg = "#f0fdf4"; fg = "#22c55e"; }
        else if (change < 0) { text = change + (isXp ? " XP" : "%");                     bg = "#fef2f2"; fg = "#ef4444"; }
        else                 { text = "—";                                                 bg = "#f8fafc"; fg = "#94a3b8"; }
        badge.setText(text);
        badge.setStyle("-fx-background-color:" + bg + "; -fx-background-radius:50; " +
                "-fx-text-fill:" + fg + "; -fx-font-size:11px; " +
                "-fx-font-weight:bold; -fx-padding:3 8;");
    }

    // ════════════════════════════════════════════════════════════
    //  6.2  LEADERBOARD
    // ════════════════════════════════════════════════════════════
    private void processLeaderboard(String uid, JsonNode topRows) {
        List<javafx.scene.Node> nodes = new ArrayList<>();
        String myName = SessionManager.getInstance().getUserName();

        if (topRows != null && topRows.isArray()) {
            String[] rankColors = {"#f59e0b","#94a3b8","#cd7c3f","#0d7ff2","#64748b"};
            int idx = 0;

            for (JsonNode entry : topRows) {
                String entryUid = entry.path("user_id").asText("");

                String name = "";
                if (entry.has("profiles") && !entry.path("profiles").isNull()) {
                    name = entry.path("profiles").path("name").asText("");
                } else if (entry.has("name")) {
                    name = entry.path("name").asText(""); // Fallback if pulled from profiles table directly
                }

                if (name.isBlank()) name = "Student #" + (idx + 1);

                int     xp      = entry.path("xp").asInt(0);
                int     rank    = entry.path("rank").asInt(idx + 1);
                boolean isMe    = entryUid.equals(uid);

                nodes.add(buildLeaderboardRow(
                        String.valueOf(rank),
                        isMe ? (myName != null ? myName : "You") + " (You)" : name,
                        xp, rankColors[Math.min(idx, rankColors.length - 1)], isMe));
                idx++;
            }
        }

        if (nodes.isEmpty()) {
            Label empty = new Label("No leaderboard data yet. Start practicing to appear here!");
            empty.setStyle("-fx-font-size:12px; -fx-text-fill:#94a3b8; -fx-padding:16 24;");
            nodes.add(empty);
        }

        Platform.runLater(() -> leaderboardBox.getChildren().setAll(nodes));
    }

    private HBox buildLeaderboardRow(String rank, String name, int xp,
                                     String rankColor, boolean isMe) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        String normStyle =
                "-fx-padding:14 24; -fx-cursor:hand; " +
                        "-fx-background-color:" + (isMe ? "#f0f7ff" : "transparent") + "; " +
                        (isMe ? "-fx-border-color:transparent transparent transparent #0d7ff2; " +
                                "-fx-border-width:0 0 0 4; " : "") +
                        "-fx-border-color:transparent transparent #f8fafc transparent; " +
                        "-fx-border-width:0 0 1 0;";
        row.setStyle(normStyle);

        if (!isMe) {
            row.setOnMouseEntered(e -> row.setStyle(
                    "-fx-padding:14 24; -fx-cursor:hand; -fx-background-color:#f8fafc; " +
                            "-fx-border-color:transparent transparent #f1f5f9 transparent; " +
                            "-fx-border-width:0 0 1 0;"));
            row.setOnMouseExited(e -> row.setStyle(normStyle));
        }

        Label rankLbl = new Label(rank);
        rankLbl.setStyle("-fx-font-size:15px; -fx-font-weight:900; " +
                "-fx-text-fill:" + rankColor + "; -fx-min-width:28;");

        StackPane avatar = new StackPane();
        avatar.setStyle("-fx-background-color:" + (isMe ? "#bfdbfe" : "#e2e8f0") +
                "; -fx-background-radius:50; " +
                "-fx-min-width:42; -fx-min-height:42; " +
                "-fx-pref-width:42; -fx-pref-height:42;");
        avatar.setAlignment(Pos.CENTER);
        String rawName  = name.replace(" (You)", "").trim();
        String initials = rawName.length() >= 2
                ? rawName.substring(0, 2).toUpperCase()
                : rawName.toUpperCase();
        if (isMe) initials = SessionManager.getInstance().getInitials();
        Label initLbl = new Label(initials);
        initLbl.setStyle("-fx-font-size:13px; -fx-font-weight:bold; -fx-text-fill:" +
                (isMe ? "#0d7ff2" : "#475569") + ";");
        avatar.getChildren().add(initLbl);

        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size:13px; -fx-font-weight:" +
                (isMe ? "bold" : "500") + "; -fx-text-fill:#0f172a;");
        info.getChildren().add(nameLbl);

        VBox xpBox = new VBox(2);
        xpBox.setAlignment(Pos.CENTER_RIGHT);
        Label xpLbl = new Label(String.format("%,d", xp) + " XP");
        xpLbl.setStyle("-fx-font-size:13px; -fx-font-weight:900; -fx-text-fill:#0d7ff2;");
        Label tagLbl = new Label(isMe ? "YOU" : rank.equals("1") ? "TOP STUDENT" : "#" + rank);
        tagLbl.setStyle("-fx-font-size:9px; -fx-font-weight:bold; -fx-text-fill:" +
                (isMe ? "#0d7ff2" : "#22c55e") + ";");
        xpBox.getChildren().addAll(xpLbl, tagLbl);

        row.getChildren().addAll(rankLbl, avatar, info, xpBox);
        return row;
    }

    // ════════════════════════════════════════════════════════════
    //  6.3  ACHIEVEMENTS
    // ════════════════════════════════════════════════════════════
    private void processAchievements(String uid, JsonNode allAchievements,
                                     JsonNode userAchievements) {
        if (allAchievements == null || !allAchievements.isArray()) return;

        Set<String> unlockedIds = new HashSet<>();
        if (userAchievements != null && userAchievements.isArray())
            for (JsonNode ua : userAchievements)
                unlockedIds.add(ua.path("achievement_id").asText());

        int total = 0, unlocked = 0;
        List<JsonNode> unlockedList = new ArrayList<>();
        List<JsonNode> lockedList   = new ArrayList<>();

        for (JsonNode a : allAchievements) {
            total++;
            if (unlockedIds.contains(a.path("id").asText())) {
                unlocked++;
                unlockedList.add(a);
            } else {
                lockedList.add(a);
            }
        }

        final int fu = unlocked, ft = total;
        Platform.runLater(() -> {
            achievementCountLabel.setText(fu + " / " + ft);
            buildBadgeRow(unlockedRow, unlockedList.subList(0, Math.min(3, unlockedList.size())), false);
            buildBadgeRow(lockedRow,   lockedList  .subList(0, Math.min(3, lockedList.size())),   true);
        });
    }

    private void buildBadgeRow(HBox row, List<JsonNode> achievements, boolean locked) {
        row.getChildren().clear();
        for (JsonNode a : achievements) {
            String icon  = a.path("icon").asText("🏆");
            String title = a.path("title").asText("Badge");
            String desc  = a.path("description").asText("");
            int    xpRew = a.path("xp_reward").asInt(0);

            VBox badge = new VBox(6);
            HBox.setHgrow(badge, Priority.ALWAYS);
            badge.setAlignment(Pos.CENTER);
            if (locked) badge.setOpacity(0.40);

            StackPane circle = new StackPane();
            circle.setPrefSize(56, 56); circle.setMinSize(56, 56); circle.setMaxSize(56, 56);
            circle.setStyle(
                    "-fx-background-color:" + (locked ? "#f1f5f9" : getBadgeBg(icon)) +
                            "; -fx-background-radius:50; " +
                            "-fx-border-color:" + (locked ? "#e2e8f0" : getBadgeBorder(icon)) +
                            "; -fx-border-width:2; -fx-border-radius:50; -fx-cursor:hand;");
            Label iconLbl = new Label(icon);
            iconLbl.setStyle("-fx-font-size:22px;");
            circle.getChildren().add(iconLbl);

            Label titleLbl = new Label(title.toUpperCase());
            titleLbl.setStyle("-fx-font-size:9px; -fx-font-weight:900; " +
                    "-fx-text-fill:#0f172a; -fx-text-alignment:center;");
            titleLbl.setWrapText(true);
            titleLbl.setMaxWidth(70);
            titleLbl.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

            badge.getChildren().addAll(circle, titleLbl);

            Tooltip tip = new Tooltip(desc + (xpRew > 0 ? "\n+" + xpRew + " XP" : ""));
            tip.setStyle("-fx-font-size:12px;");
            Tooltip.install(circle, tip);

            row.getChildren().add(badge);
        }
    }

    private String getBadgeBg(String icon) {
        return switch (icon) {
            case "📜","⭐" -> "#fff7ed";
            case "🔥"      -> "#eff6ff";
            case "🧠"      -> "#f0fdf4";
            case "🏆"      -> "#fefce8";
            case "🎓","💎" -> "#f5f3ff";
            case "⚡"      -> "#fef9c3";
            default        -> "#f8fafc";
        };
    }

    private String getBadgeBorder(String icon) {
        return switch (icon) {
            case "📜","⭐" -> "#fed7aa";
            case "🔥"      -> "#bfdbfe";
            case "🧠"      -> "#bbf7d0";
            case "🏆"      -> "#fde68a";
            case "🎓","💎" -> "#ddd6fe";
            case "⚡"      -> "#fef08a";
            default        -> "#e2e8f0";
        };
    }

    // ════════════════════════════════════════════════════════════
    //  ACHIEVEMENT UNLOCK CHECK
    // ════════════════════════════════════════════════════════════
    private void checkNewAchievements(String uid, JsonNode attempts,
                                      JsonNode userAchievements, JsonNode allAchievements) {
        if (attempts == null || allAchievements == null) return;

        Set<String> alreadyUnlocked = new HashSet<>();
        if (userAchievements != null && userAchievements.isArray())
            for (JsonNode ua : userAchievements)
                alreadyUnlocked.add(ua.path("achievement_id").asText());

        int correctTotal = 0;
        if (attempts.isArray())
            for (JsonNode a : attempts)
                if (a.path("is_correct").asBoolean(false)) correctTotal++;

        int streak = SessionManager.getInstance().getStreak();

        for (JsonNode a : allAchievements) {
            String aid = a.path("id").asText();
            String key = a.path("key").asText();
            if (alreadyUnlocked.contains(aid)) continue;

            boolean shouldUnlock = switch (key) {
                case "concept_master" -> correctTotal >= 50;
                case "fire_15"        -> streak >= 15;
                case "quiz_titan"     -> correctTotal >= 100;
                case "top_10_global"  -> false;
                case "perfect_exam"   -> false;
                case "study_mentor"   -> false;
                default               -> false;
            };

            if (!shouldUnlock) continue;

            String achId    = aid;
            String achTitle = a.path("title").asText("Achievement");
            int    achXp    = a.path("xp_reward").asInt(0);

            Thread.ofVirtual().start(() -> {
                try {
                    SupabaseClient.getInstance().from("user_achievements").upsert(
                            String.format("{\"user_id\":\"%s\",\"achievement_id\":\"%s\"}",
                                    uid, achId));
                    if (achXp > 0) {
                        JsonNode p = SupabaseClient.getInstance()
                                .from("profiles").select("xp")
                                .eq("user_id", uid).limit(1).execute();
                        if (p.isArray() && p.size() > 0) {
                            int newXp = p.get(0).path("xp").asInt(0) + achXp;
                            SupabaseClient.getInstance().from("profiles")
                                    .eq("user_id", uid)
                                    .update("{\"xp\":" + newXp + "}");
                        }
                    }
                    Platform.runLater(() -> showAchievementToast(achTitle));
                } catch (Exception e) {
                    System.err.println("[Achievement unlock] " + e.getMessage());
                }
            });
        }
    }

    private void showAchievementToast(String title) {
        toastAchievementName.setText(title);
        toastPane.setVisible(true);
        toastPane.setManaged(true);
        toastBox.setOpacity(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toastBox);
        fadeIn.setToValue(1.0);
        fadeIn.setOnFinished(e -> {
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(pe -> {
                FadeTransition fadeOut = new FadeTransition(Duration.millis(400), toastBox);
                fadeOut.setToValue(0);
                fadeOut.setOnFinished(fe -> {
                    toastPane.setVisible(false);
                    toastPane.setManaged(false);
                });
                fadeOut.play();
            });
            pause.play();
        });
        fadeIn.play();
    }

    // ════════════════════════════════════════════════════════════
    //  6.4  GROWTH TIP
    // ════════════════════════════════════════════════════════════
    private void processGrowthTip(String uid, JsonNode attempts) {
        try {
            if (attempts == null || !attempts.isArray() || attempts.isEmpty()) {
                Platform.runLater(() -> {
                    growthTipLabel.setText(
                            "Start practicing to get personalized growth tips!");
                    startPracticeBtn.setVisible(false);
                });
                return;
            }

            Map<String, int[]> chapterAcc = new HashMap<>();
            int totalCorrect = 0, totalAttempts = 0;

            for (JsonNode a : attempts) {
                String  chapId = a.path("question").path("chapter_id").asText("");
                boolean ok     = a.path("is_correct").asBoolean(false);
                if (!chapId.isBlank()) {
                    chapterAcc.computeIfAbsent(chapId, k -> new int[2]);
                    chapterAcc.get(chapId)[1]++;
                    if (ok) chapterAcc.get(chapId)[0]++;
                }
                totalAttempts++;
                if (ok) totalCorrect++;
            }

            double avgAcc = totalAttempts > 0
                    ? (double) totalCorrect / totalAttempts * 100 : 0;

            String weakestChapterId = null;
            double weakestAcc       = 101;
            for (var entry : chapterAcc.entrySet()) {
                int[] acc = entry.getValue();
                if (acc[1] < 2) continue;
                double chapAcc = (double) acc[0] / acc[1] * 100;
                if (chapAcc < weakestAcc) {
                    weakestAcc       = chapAcc;
                    weakestChapterId = entry.getKey();
                }
            }

            if (weakestChapterId == null) {
                Platform.runLater(() -> {
                    growthTipLabel.setText(
                            "Great work! Keep practicing to maintain performance across all chapters.");
                    startPracticeBtn.setVisible(false);
                });
                return;
            }

            final String wcId = weakestChapterId;
            final double wAcc = weakestAcc;
            final double avg  = avgAcc;

            JsonNode chapter = SupabaseClient.getInstance()
                    .from("chapters")
                    .select("id,title,subject:subjects(name)")
                    .eq("id", wcId).limit(1).execute();

            String chapName = "this chapter";
            String subjName = "Subject";
            if (chapter.isArray() && chapter.size() > 0) {
                chapName = chapter.get(0).path("title").asText("this chapter");
                subjName = chapter.get(0).path("subject").path("name").asText("Subject");
            }

            int    gap    = (int)(avg - wAcc);
            String fcName = chapName, fSName = subjName;
            int    fGap   = gap;

            growthTipChapterId = wcId;

            Platform.runLater(() -> {
                growthTipLabel.setText(String.format(
                        "Your %s chapter \"%s\" accuracy is %d%% below your average. " +
                                "Dedicate 20 minutes here to boost your rank this week.",
                        fSName, fcName, Math.max(fGap, 0)));
                startPracticeBtn.setVisible(true);
            });

        } catch (Exception e) {
            System.err.println("[GrowthTip] " + e.getMessage());
            Platform.runLater(() ->
                    growthTipLabel.setText("Keep practicing to unlock personalized growth tips!"));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  REALTIME POLLING
    //  FIX: Polls leaderboard_cache and updates User's stats
    // ════════════════════════════════════════════════════════════
    private void startRealtimePolling(String uid) {
        realtimeDot.setStyle("-fx-font-size:10px; -fx-text-fill:#22c55e;");
        realtimeLabel.setText("Live — updates every 30s");

        Timeline poll = new Timeline(new KeyFrame(Duration.seconds(30), e ->
                Thread.ofVirtual().start(() -> refreshLeaderboard(uid))
        ));
        poll.setCycleCount(Timeline.INDEFINITE);
        poll.play();
    }

    private void refreshLeaderboard(String uid) {
        try {
            // Fetch updated top 5 from cache
            JsonNode top = SupabaseClient.getInstance()
                    .from("leaderboard_cache")
                    .select("user_id,xp,rank,profiles(name)")
                    .order("rank", true).limit(5).execute();
            processLeaderboard(uid, top);

            // Fetch realtime XP/Rank for current user to update Stat Cards
            JsonNode myCache = SupabaseClient.getInstance()
                    .from("leaderboard_cache")
                    .select("xp,rank")
                    .eq("user_id", uid).limit(1).execute();

            if (myCache != null && myCache.isArray() && myCache.size() > 0) {
                int currentXp = myCache.get(0).path("xp").asInt(0);
                int currentRank = myCache.get(0).path("rank").asInt(0);

                Platform.runLater(() -> {
                    xpLabel.setText(String.format("%,d", currentXp));
                    if (currentRank > 0) {
                        rankLabel.setText("#" + currentRank);
                        rankChangeBadge.setText("Rank #" + currentRank);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("[Realtime poll] " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════
    //  PERIOD TOGGLE
    // ════════════════════════════════════════════════════════════
    @FXML private void onDaily()   { setPeriod("daily");   }
    @FXML private void onWeekly()  { setPeriod("weekly");  }
    @FXML private void onAllTime() { setPeriod("alltime"); }

    private void setPeriod(String period) {
        activePeriod = period;
        btnDaily  .setStyle(period.equals("daily")   ? PERIOD_ACTIVE : PERIOD_INACTIVE);
        btnWeekly .setStyle(period.equals("weekly")  ? PERIOD_ACTIVE : PERIOD_INACTIVE);
        btnAllTime.setStyle(period.equals("alltime") ? PERIOD_ACTIVE : PERIOD_INACTIVE);

        for (Label l : new Label[]{xpLabel, rankLabel, accuracyLabel, streakLabel})
            l.setText("...");

        String uid = SessionManager.getInstance().getUserId();
        if (uid != null) Thread.ofVirtual().start(() -> loadAllParallel(uid, period));
    }

    // ════════════════════════════════════════════════════════════
    //  ACTIONS
    // ════════════════════════════════════════════════════════════
    @FXML private void onViewAll() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("Full Leaderboard");
        a.setContentText("Full paginated leaderboard coming in next sprint!");
        a.showAndWait();
    }

    @FXML private void onViewAllBadges() {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setHeaderText("All Badges");
        a.setContentText("Full achievements gallery coming in next sprint!");
        a.showAndWait();
    }

    @FXML private void onStartPractice() {
        if (growthTipChapterId == null) return;
        Stage stage = (Stage) mainScroll.getScene().getWindow();

        Thread.ofVirtual().start(() -> {
            try {
                JsonNode c = SupabaseClient.getInstance()
                        .from("chapters")
                        .select("title,subject:subjects(name)")
                        .eq("id", growthTipChapterId).limit(1).execute();
                String chapName = "Chapter";
                String subjName = "Subject";
                if (c.isArray() && c.size() > 0) {
                    chapName = c.get(0).path("title").asText("Chapter");
                    subjName = c.get(0).path("subject").path("name").asText("Subject");
                }
                final String fc = chapName, fs = subjName;
                Platform.runLater(() ->
                        QuestionNavigator.go(stage, growthTipChapterId, fc, fs, "REVIEW"));
            } catch (Exception e) {
                System.err.println("[StartPractice] " + e.getMessage());
            }
        });
    }
}