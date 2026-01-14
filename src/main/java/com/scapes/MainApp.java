package com.scapes;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.scapes.controller.MainController;
import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.impl.PexelsProvider;
import com.scapes.impl.UnsplashProvider;
import com.scapes.impl.WindowsSystemHandler;
import com.scapes.util.ResizeHelper;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;

/**
 * Kelas utama aplikasi Scapes - Wallpaper Downloader dan Setter.
 * 
 * <p>Aplikasi ini menyediakan antarmuka untuk mencari, mengunduh, dan mengatur
 * wallpaper dari berbagai sumber online seperti Pexels dan Unsplash.</p>
 * 
 * <p>Fitur utama:</p>
 * <ul>
 *   <li>Pencarian wallpaper dari multiple provider (Pexels, Unsplash)</li>
 *   <li>Download dan set wallpaper secara otomatis</li>
 *   <li>Support untuk sistem operasi Windows</li>
 *   <li>Antarmuka yang responsive dan modern</li>
 * </ul>
 * 
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    
    /** Judul aplikasi yang ditampilkan di title bar */
    private static final String APP_TITLE = "Scapes";
    
    /** Path ke ikon aplikasi default */
    private static final String APP_ICON = "/com/scapes/icon/scapes.png";
    
    /** Path ke ikon aplikasi untuk tema dark mode */
    private static final String APP_ICON_DARK = "/com/scapes/icon/scapes_light.png";
    
    /** Path ke ikon aplikasi untuk tema light mode */
    private static final String APP_ICON_LIGHT = "/com/scapes/icon/scapes_dark.png";
    
    /** Path ke file FXML untuk tampilan utama */
    private static final String MAIN_VIEW_FXML = "/com/scapes/view/main_view.fxml";
    
    /** Path ke file CSS untuk styling aplikasi */
    private static final String APP_STYLE = "/com/scapes/view/style.css";

    /**
     * Method utama yang dipanggil saat aplikasi JavaFX dimulai.
     * 
     * <p>Method ini bertanggung jawab untuk:</p>
     * <ol>
     *   <li>Mendeteksi sistem operasi yang digunakan</li>
     *   <li>Menginisialisasi SystemHandler yang sesuai</li>
     *   <li>Memuat tampilan FXML dan stylesheet</li>
     *   <li>Mengatur wallpaper provider (Pexels, Unsplash)</li>
     *   <li>Menginisialisasi controller dengan dependencies</li>
     *   <li>Menampilkan window aplikasi</li>
     * </ol>
     * 
     * @param stage Stage utama JavaFX yang disediakan oleh framework
     * @throws Exception jika terjadi error saat loading FXML atau inisialisasi komponen
     */
    @Override
    public void start(Stage stage) throws Exception {
        logger.info("Starting Scapes...");

        // Pusatkan stage di tengah layar
        stage.centerOnScreen();

        // Setup system handler berdasarkan OS
        logger.info("Detecting operating system...");
        SystemHandler system = null;
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (osName.contains("win")) {
            // Inisialisasi handler untuk Windows
            system = new WindowsSystemHandler();
        } else {
            // OS tidak didukung - tampilkan error dan keluar
            Exception ex = new UnsupportedOperationException("Only Windows OS is supported currently.");
            Alert alert = new Alert(AlertType.ERROR);
            alert.setTitle(APP_TITLE);
            alert.setHeaderText("Startup Error");
            alert.setContentText("Unsupported OS detected: " + osName + "\n" + ex.getMessage());
            alert.showAndWait();
            Platform.exit();
            logger.error("Unsupported OS: {}", osName);
            return;
        }

        // Load tampilan utama dari FXML
        logger.info("Loading main view...");
        FXMLLoader loader = new FXMLLoader(getClass().getResource(MAIN_VIEW_FXML));
        String css = this.getClass().getResource(APP_STYLE).toExternalForm();
        Scene scene = new Scene(loader.load());
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        
        // Set ikon aplikasi (favicon)
        Image appIcon = new Image(getClass().getResourceAsStream(APP_ICON));
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }

        // Apply stylesheet dan set transparent window
        scene.getStylesheets().add(css);
        stage.initStyle(javafx.stage.StageStyle.TRANSPARENT);
        
        // Setup dan registrasi wallpaper providers
        logger.info("Setting up wallpaper providers...");
        ProviderManager manager = new ProviderManager();
        manager.registerProvider(new UnsplashProvider()); // Provider 1: Unsplash
        manager.registerProvider(new PexelsProvider());   // Provider 2: Pexels

        // Inject dependencies ke controller
        logger.info("Initializing main controller...");
        MainController controller = loader.getController();
        controller.init(manager, system, scene); 

        // Load ikon aplikasi untuk top bar (tema light dan dark)
        try {
            Image imgWhite = null;
            Image imgBlack = null;

            // Load ikon untuk light theme (ikon hitam)
            InputStream streamWhite = getClass().getResourceAsStream(APP_ICON_LIGHT);
            if (streamWhite != null) imgWhite = new Image(streamWhite);
 
            // Load ikon untuk dark theme (ikon putih)
            InputStream streamBlack = getClass().getResourceAsStream(APP_ICON_DARK);
            if (streamBlack != null) imgBlack = new Image(streamBlack);

            // Inisialisasi ikon di controller jika berhasil di-load
            if (imgWhite != null && imgBlack != null) {
                controller.initAppIcons(imgWhite, imgBlack);
            } else {
                logger.error("Failed loading app icons.");
            }

        } catch (Exception e) {
            logger.error("Error loading app icons: ", e);
        }

        // Tampilkan stage dengan konfigurasi akhir
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(900);   // Lebar minimum window
        stage.setMinHeight(600);  // Tinggi minimum window
        
        // Tambahkan listener untuk resize window
        ResizeHelper.addResizeListener(stage);
        stage.show();

        logger.info("Scapes started successfully.");
    }

    /**
     * Entry point aplikasi Java.
     * 
     * <p>Method ini dipanggil pertama kali saat aplikasi dijalankan
     * dan akan memicu lifecycle JavaFX Application.</p>
     * 
     * @param args argumen command line (tidak digunakan saat ini)
     */
    public static void main(String[] args) {
        logger.info("Launching application...");
        launch();
    }
}