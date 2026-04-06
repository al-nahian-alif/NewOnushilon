package org.example.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

/**
 * Shared helper to navigate any page → QuestionView with a configured session.
 * Usage:
 *   QuestionNavigator.go(stage, chapterId, chapterName, subjectName, "PRACTICE");
 *   QuestionNavigator.go(stage, questionIds, chapterId, chapterName, subjectName, "REVIEW");
 */
public class QuestionNavigator {

    /** Navigate with a specific list of question IDs (e.g. for REVIEW / MISTAKE_BANK mode). */
    public static void go(Stage stage, List<String> questionIds,
                          String chapterId, String chapterName,
                          String subjectName, String mode) {
        QuestionController.setSession(
                new QuestionController.QuestionSession(
                        questionIds, chapterId, chapterName, subjectName, mode));
        navigate(stage);
    }

    /** Navigate with chapter ID — questions will be fetched by chapter. */
    public static void go(Stage stage, String chapterId,
                          String chapterName, String subjectName, String mode) {
        QuestionController.setSession(
                new QuestionController.QuestionSession(
                        List.of(), chapterId, chapterName, subjectName, mode));
        navigate(stage);
    }

    private static void navigate(Stage stage) {
        try {
            Parent root = FXMLLoader.load(
                    QuestionNavigator.class.getResource("/QuestionView.fxml"));
            stage.setScene(new Scene(root, stage.getWidth(), stage.getHeight()));
        } catch (Exception e) {
            System.err.println("[QuestionNavigator] " + e.getMessage());
            e.printStackTrace();
        }
    }
}