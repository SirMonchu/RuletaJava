module com.example.ruleta {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.bootstrapfx.core;

    opens com.example.ruleta to javafx.fxml;
    exports com.example.ruleta;
}