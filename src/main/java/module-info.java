module org.example {
    // JavaFX core components
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;

    // JSON parsing for Supabase
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;

    // Core Java libraries for networking and desktop features
    requires java.net.http;
    requires java.desktop;

    // Allow JavaFX to read and inject into your FXML files
    opens org.example to javafx.fxml;
    opens org.example.controller to javafx.fxml;

    // Allow Jackson to read/write JSON to your database models
    opens org.example.supabase to com.fasterxml.jackson.databind;

    // Make your packages accessible to run
    exports org.example;
    exports org.example.controller;
    exports org.example.supabase;
}