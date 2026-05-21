package com.fluxpass;

import com.fluxpass.ui.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class FluxPassApp extends Application {

    private static javafx.scene.image.Image appIcon;

    public static javafx.scene.image.Image getAppIcon() {
        if (appIcon == null) {
            appIcon = createAppIcon();
        }
        return appIcon;
    }

    private static javafx.scene.image.Image createAppIcon() {
        BufferedImage awtImage = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = awtImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(33, 150, 243));
        g.fillRoundRect(4, 4, 56, 56, 14, 14);
        g.setColor(new Color(255, 255, 255));
        g.fillOval(22, 12, 20, 20);
        g.fillRect(28, 24, 8, 24);
        g.fillRect(36, 32, 10, 4);
        g.fillRect(36, 40, 8, 4);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(awtImage, "png", out);
            return new javafx.scene.image.Image(new ByteArrayInputStream(out.toByteArray()));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void start(Stage stage) {
        try {
            MainWindow mainWindow = new MainWindow(stage);
            mainWindow.show();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to start: " + e.getMessage(), ButtonType.OK);
            alert.setHeaderText("Startup Error");
            alert.showAndWait();
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
