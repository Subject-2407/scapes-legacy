module com.scapes {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive javafx.graphics;

    requires java.net.http;
    requires com.google.gson;
    requires com.sun.jna;
    requires com.sun.jna.platform;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires java.desktop;

    // --- OPENS (Untuk Reflection seperti FXML & GSON) ---
    opens com.scapes to javafx.fxml, com.google.gson;
    opens com.scapes.model to com.google.gson;
    opens com.scapes.controller to javafx.fxml;
    // Tambahkan opens untuk core jika ada pemakaian reflection (opsional)
    opens com.scapes.core to com.google.gson; 

    exports com.scapes;
    exports com.scapes.core; 
    exports com.scapes.model;  
    exports com.scapes.controller;
    exports com.scapes.impl;
    exports com.scapes.util; 
}