package com.scapes.util;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.util.Duration;

/**
 * Utility class untuk menampilkan notifikasi sementara (Snackbar)
 * pada aplikasi JavaFX.
 *
 * <p>Snackbar digunakan untuk memberikan feedback singkat
 * kepada pengguna, seperti:</p>
 * <ul>
 *   <li>Status download wallpaper</li>
 *   <li>Notifikasi keberhasilan</li>
 *   <li>Notifikasi kegagalan operasi</li>
 * </ul>
 *
 * <p>Snackbar ditampilkan sebagai {@link Popup} yang muncul
 * di sudut kanan bawah window, dengan animasi slide dan fade.</p>
 *
 * <p>Seluruh operasi UI dijalankan melalui
 * {@link Platform#runLater(Runnable)}
 * untuk menjamin thread-safety dengan JavaFX Application Thread.</p>
 *
 * <p>Kelas ini bersifat stateless dan hanya menyediakan
 * method statis.</p>
 *
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class Snackbar {

    /** Lebar default snackbar (pixel) */
    private static final double SNACKBAR_WIDTH = 300;

    /** Tinggi default snackbar (pixel) */
    private static final double SNACKBAR_HEIGHT = 50;

    /** Margin dari sisi bawah window (pixel) */
    private static final double MARGIN_BOTTOM = 20;

    /** Margin dari sisi kanan window (pixel) */
    private static final double MARGIN_RIGHT = 20;

    /** Durasi animasi masuk dan keluar */
    private static final Duration ANIMATION_DURATION =
            Duration.millis(300);

    /** Durasi snackbar ditampilkan sebelum menghilang */
    private static final Duration DISPLAY_DURATION =
            Duration.seconds(3);

    /**
     * Enum tipe Snackbar untuk menentukan warna dan konteks notifikasi.
     */
    public enum Type {

        /** Informasi proses berjalan (misalnya download) */
        INFO("#0c6370", "#ffffff"),

        /** Notifikasi sukses */
        SUCCESS("#28a745", "#ffffff"),

        /** Notifikasi error */
        ERROR("#dc3545", "#ffffff");

        /** Warna background snackbar */
        private final String backgroundColor;

        /** Warna teks snackbar */
        private final String textColor;

        Type(String backgroundColor, String textColor) {
            this.backgroundColor = backgroundColor;
            this.textColor = textColor;
        }

        /**
         * @return warna background snackbar
         */
        public String getBackgroundColor() {
            return backgroundColor;
        }

        /**
         * @return warna teks snackbar
         */
        public String getTextColor() {
            return textColor;
        }
    }

    /**
     * Menampilkan snackbar dengan pesan dan tipe tertentu.
     *
     * <p>Snackbar akan:</p>
     * <ol>
     *   <li>Muncul dari sisi kanan bawah window</li>
     *   <li>Menampilkan pesan selama durasi tertentu</li>
     *   <li>Menghilang secara otomatis dengan animasi</li>
     * </ol>
     *
     * @param scene scene aktif tempat snackbar ditampilkan
     * @param message pesan yang akan ditampilkan
     * @param type tipe snackbar ({@link Type})
     */
    public static void show(Scene scene, String message, Type type) {
        Platform.runLater(() -> {

            // Container utama snackbar
            HBox snackbar = new HBox();
            snackbar.setPrefSize(SNACKBAR_WIDTH, SNACKBAR_HEIGHT);
            snackbar.setMaxSize(SNACKBAR_WIDTH, SNACKBAR_HEIGHT);
            snackbar.setStyle(String.format(
                    "-fx-background-color: %s; " +
                    "-fx-background-radius: 8; " +
                    "-fx-border-radius: 8; " +
                    "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.3), 6, 0, 0, 2);",
                    type.getBackgroundColor()
            ));
            snackbar.setPadding(new Insets(12, 16, 12, 16));
            snackbar.setAlignment(Pos.CENTER_LEFT);

            // Indikator visual (lingkaran kecil)
            Circle indicator = new Circle(6);
            indicator.setFill(Color.web(type.getTextColor()));
            indicator.setOpacity(0.8);
            HBox.setMargin(indicator, new Insets(0, 12, 0, 0));

            // Label pesan
            Label messageLabel = new Label(message);
            messageLabel.setStyle(String.format(
                    "-fx-text-fill: %s; " +
                    "-fx-font-size: 14px; " +
                    "-fx-font-weight: 500; " +
                    "-fx-font-family: 'Segoe UI', sans-serif;",
                    type.getTextColor()
            ));
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(SNACKBAR_WIDTH - 60);

            snackbar.getChildren().addAll(indicator, messageLabel);

            // Popup container
            Popup popup = new Popup();
            popup.getContent().add(snackbar);
            popup.setAutoHide(false);

            // Posisi awal snackbar (kanan bawah)
            popup.setX(scene.getWindow().getX()
                    + scene.getWidth()
                    - SNACKBAR_WIDTH
                    - MARGIN_RIGHT);
            popup.setY(scene.getWindow().getY()
                    + scene.getHeight()
                    - SNACKBAR_HEIGHT
                    - MARGIN_BOTTOM);

            // Update posisi saat window di-resize atau dipindah
            scene.widthProperty().addListener((obs, o, n) -> {
                if (popup.isShowing()) {
                    popup.setX(scene.getWindow().getX()
                            + n.doubleValue()
                            - SNACKBAR_WIDTH
                            - MARGIN_RIGHT);
                }
            });

            scene.heightProperty().addListener((obs, o, n) -> {
                if (popup.isShowing()) {
                    popup.setY(scene.getWindow().getY()
                            + n.doubleValue()
                            - SNACKBAR_HEIGHT
                            - MARGIN_BOTTOM);
                }
            });

            scene.getWindow().xProperty().addListener((obs, o, n) -> {
                if (popup.isShowing()) {
                    popup.setX(n.doubleValue()
                            + scene.getWidth()
                            - SNACKBAR_WIDTH
                            - MARGIN_RIGHT);
                }
            });

            scene.getWindow().yProperty().addListener((obs, o, n) -> {
                if (popup.isShowing()) {
                    popup.setY(n.doubleValue()
                            + scene.getHeight()
                            - SNACKBAR_HEIGHT
                            - MARGIN_BOTTOM);
                }
            });

            popup.show(scene.getWindow());

            // Animasi masuk
            snackbar.setTranslateX(SNACKBAR_WIDTH);
            snackbar.setOpacity(0);

            TranslateTransition slideIn =
                    new TranslateTransition(ANIMATION_DURATION, snackbar);
            slideIn.setToX(0);

            FadeTransition fadeIn =
                    new FadeTransition(ANIMATION_DURATION, snackbar);
            fadeIn.setToValue(1);

            slideIn.play();
            fadeIn.play();

            // Animasi keluar otomatis
            FadeTransition fadeOut =
                    new FadeTransition(ANIMATION_DURATION, snackbar);
            fadeOut.setToValue(0);
            fadeOut.setDelay(DISPLAY_DURATION);

            TranslateTransition slideOut =
                    new TranslateTransition(ANIMATION_DURATION, snackbar);
            slideOut.setToX(SNACKBAR_WIDTH);
            slideOut.setDelay(DISPLAY_DURATION);

            fadeOut.setOnFinished(e -> popup.hide());
            slideOut.setOnFinished(e -> popup.hide());

            fadeOut.play();
            slideOut.play();
        });
    }

    /**
     * Menampilkan snackbar dengan pesan proses download.
     *
     * @param scene scene aktif
     */
    public static void showDownloading(Scene scene) {
        show(scene, "Downloading image...", Type.INFO);
    }

    /**
     * Menampilkan snackbar ketika download berhasil.
     *
     * @param scene scene aktif
     */
    public static void showDownloadSuccess(Scene scene) {
        show(scene, "Image downloaded successfully", Type.SUCCESS);
    }

    /**
     * Menampilkan snackbar ketika wallpaper berhasil diterapkan.
     *
     * @param scene scene aktif
     */
    public static void showWallpaperSetSuccess(Scene scene) {
        show(scene, "Wallpaper set successfully", Type.SUCCESS);
    }

    /**
     * Menampilkan snackbar ketika download gagal.
     *
     * @param scene scene aktif
     */
    public static void showDownloadFailed(Scene scene) {
        show(scene, "Failed to download image", Type.ERROR);
    }

    /**
     * Menampilkan snackbar ketika pengaturan wallpaper gagal.
     *
     * @param scene scene aktif
     */
    public static void showWallpaperSetFailed(Scene scene) {
        show(scene, "Failed to set wallpaper", Type.ERROR);
    }
}