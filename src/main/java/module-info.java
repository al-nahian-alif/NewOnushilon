<<<<<<< HEAD
module org.example {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires com.fasterxml.jackson.databind;
    requires java.net.http;
    requires java.desktop;

    opens org.example to javafx.fxml;
    opens org.example.controller to javafx.fxml;
    opens org.example.supabase to com.fasterxml.jackson.databind;

    exports org.example;
    exports org.example.controller;
    exports org.example.supabase;
=======
module Onushilon {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires javafx.media;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.databind;

    opens org.example to javafx.fxml;
    exports org.example;
>>>>>>> a46c9a1364b495cf075a13545d3400c32f1aa64b
}