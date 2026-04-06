package org.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionModel {
    public String subjectId;
    public String subjectName;
    public List<Chapter> chapters;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Chapter {
        public String chapterId;
        public String chapterName;
        public List<Question> questions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Question {
        public String id;
        public Content content;
        public Explanation explanation;
        public Meta meta;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        public String questionText;
        public List<String> options;
        public int correctOptionIndex;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Explanation {
        public String text;
        public String videoId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        public int avgTimeSec;
    }
}
