package com.scapes.controller;

import com.scapes.core.ProviderManager;
import com.scapes.core.SystemHandler;
import com.scapes.impl.SuggestionService;
import com.scapes.model.WallpaperImage;
import com.scapes.util.Snackbar;

import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.util.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller utama untuk antarmuka aplikasi Scapes.
 *
 * <p>Kelas ini menangani hampir seluruh alur UI aplikasi, meliputi:</p>
 * <ul>
 *   <li>Mode Explore (kategori & trending wallpaper)</li>
 *   <li>Mode Search (masonry grid hasil pencarian)</li>
 *   <li>Manajemen tema (dark / light)</li>
 *   <li>Kontrol window custom (minimize, maximize, close)</li>
 *   <li>Download dan set wallpaper</li>
 *   <li>Pagination dan lazy loading</li>
 * </ul>
 *
 * <p>MainController berfungsi sebagai orchestrator antara UI,
 * {@link ProviderManager}, {@link SystemHandler},
 * dan {@link SuggestionService}.</p>
 *
 * <p>Controller ini diinisialisasi melalui FXML
 * dan dependency utama di-inject melalui method {@link #init}.</p>
 *
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class MainController {

    /** Logger utama untuk MainController */
    private static final Logger logger =
            LoggerFactory.getLogger(MainController.class);

    @FXML private TabPane mainTabPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> providerCombo;
    @FXML private HBox topBar;
    @FXML private BorderPane rootPane;
    @FXML private Label welcomeLabel;
    @FXML private Label logoLabel;
    @FXML private Button themeBtn;
    @FXML private Button btnMin, btnMax, btnClose;

    /* ---------------- ONLINE TAB ---------------- */

    /** ScrollPane untuk konten online (explore & search) */
    @FXML private ScrollPane scrollPane;

    /* ---------------- LOCAL TAB ---------------- */

    /** ScrollPane untuk wallpaper lokal */
    @FXML private ScrollPane localScrollPane;

    /** Menyimpan status tema saat ini */
    private boolean isDarkMode = isWindowsDarkMode();

    /** Manager provider wallpaper */
    private ProviderManager providerManager;

    /** Handler sistem untuk download dan set wallpaper */
    private SystemHandler systemHandler;

    /** Scene utama aplikasi */
    private javafx.scene.Scene scene;

    /** Daftar kolom masonry online */
    private List<VBox> masonryColumns = new ArrayList<>();

    /** Mapping tinggi tiap kolom masonry online */
    private Map<VBox, Double> columnHeights = new HashMap<>();

    /** Container masonry lokal */
    private HBox localMasonryContainer;

    /** Daftar kolom masonry lokal */
    private List<VBox> localMasonryColumns = new ArrayList<>();

    /** Mapping tinggi tiap kolom masonry lokal */
    private Map<VBox, Double> localColumnHeights = new HashMap<>();

    /** Mapping kartu ke kolom masonry */
    private Map<VBox, VBox> cardColumnMap = new HashMap<>();

    /** Resolusi layar utama */
    private Rectangle2D screenBounds =
            Screen.getPrimary().getBounds();

    /** Lebar window default (80% layar) */
    private double screenWidth = screenBounds.getWidth() * 0.8;

    /** Tinggi window default (80% layar) */
    private double screenHeight = screenBounds.getHeight() * 0.8;

    /** HTTP client bersama untuk request ringan */
    private static final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

    private double xOffset = 0;
    private double yOffset = 0;

    private Image iconWhite;
    private Image iconBlack;
    private ImageView headerIconView;

    private double storedX, storedY, storedWidth, storedHeight;
    private boolean isMaximized = false;

    /** Service autocomplete & recommendation */
    private SuggestionService suggestionService =
            new SuggestionService();

    private ContextMenu suggestionsPopup;

    private int currentPage = 1;
    private boolean isLoading = false;
    private String currentQuery = "";
    private List<Node> activeSkeletons = new ArrayList<>();

    /** Container utama untuk mode explore */
    private VBox exploreContainer;

    /** Container masonry untuk hasil search */
    private HBox searchMasonry;

    /**
     * Menginisialisasi MainController dengan dependency utama aplikasi.
     *
     * <p>Method ini dipanggil dari {@code MainApp}
     * setelah FXML selesai di-load.</p>
     *
     * <p>Tugas utama method ini:</p>
     * <ul>
     *   <li>Setup provider wallpaper</li>
     *   <li>Inisialisasi mode explore dan search</li>
     *   <li>Menyiapkan masonry grid</li>
     *   <li>Menyiapkan suggestion, pagination, dan window control</li>
     * </ul>
     *
     * @param manager provider manager aktif
     * @param system system handler sesuai OS
     * @param scene scene utama aplikasi
     */
    public void init(ProviderManager manager,
                     SystemHandler system,
                     javafx.scene.Scene scene) {

        this.providerManager = manager;
        this.systemHandler = system;
        this.scene = scene;

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                toggleViewMode(false);
            }
        });

        loadExploreSections().thenRun(() -> {
            Platform.runLater(() -> toggleViewMode(false));
        });

        // Setup provider
        if (manager.getAvailableProviders() != null) {
            providerCombo.getItems()
                    .addAll(manager.getAvailableProviders());
        }
        if (!providerCombo.getItems().isEmpty()) {
            providerCombo.getSelectionModel().select(0);
            manager.setActiveProvider(
                    providerCombo.getItems().get(0)
            );
        }
        providerCombo.setOnAction(
                e -> manager.setActiveProvider(
                        providerCombo.getValue()
                )
        );

        // Setup explore & search container
        exploreContainer = new VBox(20);
        exploreContainer.setPadding(
                new Insets(20, 35, 50, 20)
        );
        exploreContainer.setAlignment(Pos.TOP_LEFT);

        searchMasonry = new HBox(16);
        searchMasonry.setPadding(
                new Insets(20, 35, 20, 20)
        );
        searchMasonry.setAlignment(Pos.TOP_CENTER);

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(true);
            welcomeLabel.setManaged(true);
        }
        scrollPane.setVisible(false);

        localMasonryContainer =
                setupMasonryContainer(localScrollPane);

        rootPane.widthProperty().addListener((obs, o, n) -> {
            ensureMasonryColumns(
                    searchMasonry,
                    masonryColumns,
                    columnHeights
            );
            ensureMasonryColumns(
                    localMasonryContainer,
                    localMasonryColumns,
                    localColumnHeights
            );
        });

        Platform.runLater(() -> {
            ensureMasonryColumns(
                    searchMasonry,
                    masonryColumns,
                    columnHeights
            );
            ensureMasonryColumns(
                    localMasonryContainer,
                    localMasonryColumns,
                    localColumnHeights
            );
        });

        setupSearchSuggestions();
        setupWindowControls();
        setupFocusTraversal();

        scrollPane.vvalueProperty().addListener((obs, oldVal, newVal) -> {
            if (scrollPane.getContent() == searchMasonry) {
                if (newVal.doubleValue() > 0.8
                        && !isLoading
                        && !currentQuery.isEmpty()) {
                    loadNextPage();
                }
            }
        });

        applyCurrentTheme();
    }

    /**
     * Mengganti mode tampilan antara Explore dan Search.
     *
     * @param isSearchMode {@code true} untuk mode pencarian,
     *                     {@code false} untuk mode explore
     */
    private void toggleViewMode(boolean isSearchMode) {
        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false);
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        if (isSearchMode) {
            scrollPane.setContent(searchMasonry);
            scrollPane.setVvalue(0);
        } else {
            scrollPane.setContent(exploreContainer);
            scrollPane.setVvalue(0);
        }
    }

    /**
     * Memuat section explore (trending dan kategori populer)
     * secara asynchronous.
     *
     * @return {@link CompletableFuture} yang selesai
     *         ketika seluruh section dijadwalkan
     */
    private CompletableFuture<Void> loadExploreSections() {
        return CompletableFuture.runAsync(() -> {
            Platform.runLater(
                    () -> exploreContainer.getChildren().clear()
            );

            suggestionService.getTrendingKeywords()
                    .thenAccept(trending -> {

                        String topTrend =
                                trending.isEmpty()
                                ? "Nature"
                                : trending.get(0);

                        buildCategorySection(
                                "🔥 Trending This Week",
                                topTrend,
                                true
                        );

                        runDelayed(
                                () -> buildCategorySection(
                                        "Nature",
                                        "Nature",
                                        false
                                ),
                                200
                        );
                        runDelayed(
                                () -> buildCategorySection(
                                        "Sky",
                                        "Sky",
                                        false
                                ),
                                400
                        );
                        runDelayed(
                                () -> buildCategorySection(
                                        "Automotive",
                                        "Car",
                                        false
                                ),
                                600
                        );
                        runDelayed(
                                () -> buildCategorySection(
                                        "Anime & Japan",
                                        "Anime",
                                        false
                                ),
                                800
                        );
                        runDelayed(
                                () -> buildCategorySection(
                                        "Minimalist",
                                        "Minimalist",
                                        false
                                ),
                                1000
                        );

                        preloadPopularCategories();
                    });
        });
    }
    /**
     * Memuat thumbnail untuk kategori populer di latar belakang (Background Thread).
     * <p>Metode ini mengambil daftar kata kunci populer dan melakukan pencarian awal secara asinkron.
     * Hasilnya (beberapa thumbnail pertama) akan dipicu untuk masuk ke cache sistem agar saat 
     * pengguna membuka tab Explore, gambar muncul secara instan tanpa jeda muat.</p>
     */
    private void preloadPopularCategories() {
        List<String> popularQueries = List.of("Nature", "Sky", "Car", "Anime", "Minimalist", "Mountain", "Ocean", "Forest");
        
        popularQueries.forEach(query -> {
            providerManager.search(query, 1, screenWidth, 400)
                .thenAccept(images -> {
                    if (!images.isEmpty()) {
                        List<String> thumbnailUrls = images.stream()
                            .limit(5)
                            .map(WallpaperImage::getThumbnailUrl)
                            .toList();
                        
                        preloadThumbnailsInParallel(thumbnailUrls);
                    }
                })
                .exceptionally(ex -> {
                    logger.debug("Gagal melakukan preload kategori: {}", query);
                    return null;
                });
        });
    }

    /**
     * Menjalankan tugas Runnable dengan penundaan waktu (delay) tertentu.
     * <p>Metode ini berguna untuk mengatur urutan eksekusi UI atau memberikan jeda 
     * sebelum animasi dimulai, dengan tetap memastikan perubahan visual terjadi di JavaFX Application Thread.</p>
     * * @param r Tugas (Runnable) yang akan dijalankan.
     * @param ms Durasi penundaan dalam satuan milidetik.
     */
    private void runDelayed(Runnable r, int ms) {
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() { Platform.runLater(r); }
        }, ms);
    }

    /**
     * Membangun bagian (section) kategori wallpaper pada tab Explore.
     * <p>Metode ini membuat container visual yang terdiri dari judul, tombol "See All", 
     * dan sebuah ScrollPane horizontal. Data gambar dimuat secara paralel untuk menjaga 
     * responsivitas aplikasi.</p>
     * * @param title Judul kategori yang akan ditampilkan (misal: "Trending Now").
     * @param query Kata kunci pencarian untuk mendapatkan data gambar.
     * @param isTrendingSection Jika true, akan menerapkan gaya visual khusus (Featured Card) 
     * pada item pertama.
     */
    private void buildCategorySection(String title, String query, boolean isTrendingSection) {
        VBox sectionBox = new VBox(10);
        sectionBox.setPadding(new Insets(0, 0, 10, 0));

        HBox header = new HBox(15);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Label lblTitle = new Label(title);
        lblTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + (isDarkMode ? "white" : "#333333") + ";");
        
        Button btnSeeAll = new Button("See All");
        btnSeeAll.setStyle("-fx-background-color: transparent; -fx-text-fill: #0c6370; -fx-font-weight: bold; -fx-cursor: hand;");
        btnSeeAll.setOnAction(e -> {
            searchField.setText(query);
            onSearchAction();
        });

        header.getChildren().addAll(lblTitle, btnSeeAll);

        ScrollPane hScroll = new ScrollPane();
        hScroll.setFitToHeight(true);
        hScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        hScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); 
        hScroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        hScroll.setPannable(true);

        HBox contentBox = new HBox(15);
        contentBox.setPadding(new Insets(10));
        contentBox.setAlignment(isTrendingSection ? Pos.CENTER_LEFT : Pos.TOP_LEFT); 
        contentBox.setFillHeight(false);

        hScroll.setContent(contentBox);

        Label loadingLbl = new Label("Loading...");
        loadingLbl.setStyle("-fx-text-fill: gray;");
        contentBox.getChildren().add(loadingLbl);

        sectionBox.getChildren().addAll(header, hScroll);
        
        Platform.runLater(() -> exploreContainer.getChildren().add(sectionBox));

        providerManager.search(query, 1, screenWidth, 800)
            .thenAccept(images -> {
                Platform.runLater(() -> {
                    contentBox.getChildren().clear();
                    int limit = Math.min(images.size(), 10);
                    List<WallpaperImage> limitedImages = images.subList(0, limit);
                    
                    createHorizontalCardsInParallel(limitedImages, isTrendingSection).thenAccept(cards -> {
                        Platform.runLater(() -> contentBox.getChildren().addAll(cards));
                    });
                });
            });
    }

    /**
     * Membuat kartu wallpaper horizontal untuk penggunaan di dalam ScrollPane.
     * <p>Metode ini menghitung rasio aspek secara dinamis dan melakukan "clamping" 
     * (pembatasan) tinggi agar kartu tetap terlihat estetis (tidak terlalu tinggi atau terlalu lebar).
     * Juga menangani event klik untuk mengunduh dan memasang wallpaper.</p>
     * * @param data Objek data wallpaper.
     * @param isFeatured Jika true, kartu akan berukuran lebih besar dengan efek shadow kuat.
     * @param isTrendingSection Menentukan ukuran standar kartu berdasarkan konteks section.
     * @return VBox yang telah dikonfigurasi sebagai kartu wallpaper.
     */
    private VBox createHorizontalCard(WallpaperImage data, boolean isFeatured, boolean isTrendingSection) {
        VBox card = new VBox(0);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(Insets.EMPTY);
        
        double targetWidth = isFeatured ? 400 : (isTrendingSection ? 330 : 220);
        double targetHeight = 150; 
        
        if (data.getWidth() > 0 && data.getHeight() > 0) {
            targetHeight = (data.getHeight() / data.getWidth()) * targetWidth;
            
            // Logika Clamping: Batasi tinggi antara 0.6x hingga 1.4x lebar target
            double maxHeight = targetWidth * 1.4; 
            double minHeight = targetWidth * 0.6;
            if (targetHeight > maxHeight) targetHeight = maxHeight;
            if (targetHeight < minHeight) targetHeight = minHeight;
        }

        ImageView imageView = new ImageView();
        imageView.setFitWidth(targetWidth);
        imageView.setFitHeight(targetHeight);
        imageView.setPreserveRatio(false);
        imageView.setSmooth(true);

        Rectangle clip = new Rectangle(targetWidth, targetHeight);
        double radius = isFeatured ? 24 : (isTrendingSection ? 20 : 16);
        clip.setArcWidth(radius);
        clip.setArcHeight(radius);
        imageView.setClip(clip);

        loadImageAsync(data.getThumbnailUrl(), imageView);

        Label descLabel = new Label(data.getDescription());
        descLabel.setMaxWidth(targetWidth); 
        descLabel.setPadding(new Insets(8, 12, 12, 12)); 
        
        String fontSize = isFeatured ? "14px" : (isTrendingSection ? "12px" : "11px");
        descLabel.setStyle("-fx-font-size: " + fontSize + "; -fx-text-fill: " + (isDarkMode ? "#dddddd" : "#333333") + ";");
        if (isFeatured) descLabel.setStyle(descLabel.getStyle() + "-fx-font-weight: bold;");

        // Hover Effect
        ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
        card.setOnMouseEntered(e -> {
            st.setToX(1.02); st.setToY(1.02); st.playFromStart();
            card.setStyle("-fx-cursor: hand;");
        });
        card.setOnMouseExited(e -> {
            st.setToX(1.0); st.setToY(1.0); st.playFromStart();
        });

        // Click Logic
        card.setOnMouseClicked(e -> {
             card.setOpacity(0.5);
             Snackbar.showDownloading(scene);
             systemHandler.downloadImage(data.getImageUrl(), data.getId() + ".jpg")
                 .thenAccept(dl -> {
                     if(dl != null) {
                         Snackbar.showDownloadSuccess(scene);
                         if (systemHandler.setWallpaper(dl)) Snackbar.showWallpaperSetSuccess(scene);
                         else Snackbar.showWallpaperSetFailed(scene);
                         Platform.runLater(() -> card.setOpacity(1.0));
                     }
                 })
                 .exceptionally(ex -> {
                     logger.error("Gagal mengunduh wallpaper", ex);
                     Snackbar.showDownloadFailed(scene);
                     Platform.runLater(() -> card.setOpacity(1.0));
                     return null;
                 });
        });

        card.getChildren().addAll(imageView, descLabel);
        styleCardVBox(card); 

        if (isFeatured) {
            card.setEffect(new DropShadow(20, Color.web("#0078d744")));
        }
        
        return card;
    }

    /**
     * Menginisialisasi sistem saran pencarian (Search Suggestions).
     * <p>Metode ini mengatur ContextMenu yang akan muncul saat pengguna mengetik di kolom pencarian.
     * Saran diambil dari dua sumber secara asinkron: prediksi kata kunci (autocomplete) 
     * dan rekomendasi berbasis konteks.</p>
     */
    private void setupSearchSuggestions() {
        suggestionsPopup = new ContextMenu();
        // ... (styling logic)
        
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().length() < 2) {
                suggestionsPopup.hide();
                return;
            }
            
            CompletableFuture<List<String>> predictTask = suggestionService.getPredictions(newVal);
            CompletableFuture<List<String>> recommendTask = suggestionService.getRecommendations(newVal);

            CompletableFuture.allOf(predictTask, recommendTask).thenRun(() -> {
                try {
                    List<String> predictions = predictTask.get();
                    List<String> recommendations = recommendTask.get();

                    Platform.runLater(() -> {
                        if (predictions.isEmpty() && recommendations.isEmpty()) {
                            suggestionsPopup.hide();
                        } else {
                            populateCombinedSuggestions(predictions, recommendations);
                            
                            double currentWidth = searchField.getWidth();
                            suggestionsPopup.setMinWidth(currentWidth);
                            suggestionsPopup.setPrefWidth(currentWidth);
                            suggestionsPopup.setMaxWidth(currentWidth);

                            if (!suggestionsPopup.isShowing()) {
                                suggestionsPopup.show(searchField, javafx.geometry.Side.BOTTOM, 0, 0);
                            }
                        }
                    });
                } catch (Exception e) { /* ignored */ }
            });
        });

        // Pastikan popup menyesuaikan lebar saat window di-resize
        searchField.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (suggestionsPopup.isShowing()) {
                suggestionsPopup.setMinWidth(newVal.doubleValue());
                suggestionsPopup.setPrefWidth(newVal.doubleValue());
                suggestionsPopup.setMaxWidth(newVal.doubleValue());
            }
        });
    }

    /**
     * Menggabungkan dan menampilkan saran kata kunci serta rekomendasi pada popup pencarian.
     * <p>Metode ini membersihkan item lama, menambahkan hasil prediksi, dan jika ada rekomendasi tambahan 
     * yang unik (tidak ada di daftar prediksi), akan ditambahkan di bawah pemisah (separator).</p>
     * * @param predictions List string berisi prediksi kata kunci (autocomplete).
     * @param recommendations List string berisi rekomendasi pencarian terkait.
     */
    private void populateCombinedSuggestions(List<String> predictions, List<String> recommendations) {
        suggestionsPopup.getItems().clear();

        // 1. Tambahkan hasil prediksi kata kunci
        for (String text : predictions) {
            addItemToPopup(text, false);
        }

        // 2. Tambahkan rekomendasi (hanya yang unik/belum ada di prediksi)
        if (!recommendations.isEmpty()) {
            recommendations.removeAll(predictions);

            if (!recommendations.isEmpty()) {
                suggestionsPopup.getItems().add(new SeparatorMenuItem());
                for (String text : recommendations) {
                    addItemToPopup(text, true);
                }
            }
        }
    }

    /**
     * Membuat dan menambahkan item MenuItem ke dalam popup saran pencarian.
     * * @param text Teks saran yang akan ditampilkan.
     * @param isRecommendation Menentukan apakah item ini kategori rekomendasi (mempengaruhi style font miring).
     */
    private void addItemToPopup(String text, boolean isRecommendation) {
        MenuItem item = new MenuItem(text);
        
        String textColor = isDarkMode ? "white" : "black";
        String style = "-fx-text-fill: " + textColor + "; -fx-padding: 8 12;";
        
        if (isRecommendation) {
            style += "-fx-font-style: italic; -fx-opacity: 0.9;";
        }

        item.setStyle(style);
        item.setOnAction(e -> {
            searchField.setText(text);
            suggestionsPopup.hide();
            onSearchAction();
        });
        
        suggestionsPopup.getItems().add(item);
    }

    /**
     * Memuat halaman data berikutnya (Pagination) saat pengguna mencapai akhir scroll.
     * <p>Metode ini mengimplementasikan "Infinite Scroll" dengan cara:</p>
     * <ol>
     * <li>Menampilkan skeleton loading sebagai indikator proses.</li>
     * <li>Meminta data halaman baru dari {@link ProviderManager}.</li>
     * <li>Menghapus skeleton dan menyisipkan kartu baru ke dalam Masonry grid yang ada.</li>
     * </ol>
     */
    private void loadNextPage() {
        isLoading = true;
        currentPage++;
        logger.info("Memuat halaman (dari pagination): " + currentPage);

        showSkeletons();

        providerManager.search(currentQuery, currentPage, screenWidth, screenHeight)
            .thenAccept(images -> {
                Platform.runLater(() -> {
                    removeSkeletons();
                    
                    if (!images.isEmpty()) {
                        for (WallpaperImage img : images) {
                            VBox card = createMasonryCard(img);
                            addCardToMasonry(card, masonryColumns, columnHeights);
                        }
                    }
                    isLoading = false;
                });
            })
            .exceptionally(ex -> {
                Platform.runLater(() -> {
                    removeSkeletons();
                    isLoading = false;
                });
                return null;
            });
    }

    /**
     * Mengatur kontrol jendela kustom (Drag & Resize logic).
     * <p>Karena aplikasi menggunakan StageStyle.TRANSPARENT, logika dragging manual diperlukan.
     * Metode ini juga menangani transisi dari kondisi Maximize kembali ke Normal saat jendela diseret.</p>
     */
    private void setupWindowControls() {
        Platform.runLater(() -> {
            if (rootPane.getScene() != null) {
                // Inisialisasi referensi stage jika diperlukan
            }
        });

        topBar.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        topBar.setOnMouseDragged(event -> {
            javafx.stage.Stage stage = (javafx.stage.Stage) topBar.getScene().getWindow();

            if (isMaximized) {
                // Logika: Jika sedang maximize lalu diseret, kembalikan ukuran jendela 
                // secara proporsional berdasarkan posisi kursor mouse.
                isMaximized = false;
                if (btnMax != null) btnMax.setText("⬜");
                
                double currentMouseX = event.getScreenX();
                double ratio = (currentMouseX - stage.getX()) / stage.getWidth();
                
                stage.setWidth(storedWidth);
                stage.setHeight(storedHeight);
                
                stage.setX(currentMouseX - (storedWidth * ratio));
                stage.setY(event.getScreenY() - yOffset);
                
            } else {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    /**
     * Event handler saat tab "Local" dipilih.
     * Memastikan data koleksi gambar dari storage lokal diperbarui.
     */
    @FXML
    public void onLocalTabSelected() {
        if (mainTabPane.getSelectionModel().getSelectedIndex() == 1) {
            loadLocalImages();
        }
    }

    /**
     * Memindai direktori lokal dan merender ulang koleksi gambar yang sudah diunduh.
     * Proses ini dijalankan secara asinkron agar tidak memblokir thread UI utama.
     */
    @FXML
    public void loadLocalImages() {
        if (localMasonryContainer == null) return;

        // Reset state grid lokal
        for (VBox col : localMasonryColumns) col.getChildren().clear();
        localColumnHeights.replaceAll((k, v) -> 0.0);
        
        logger.info("Memuat gambar lokal...");

        CompletableFuture.runAsync(() -> {
            List<WallpaperImage> localFiles = systemHandler.getDownloadedImages();
            Platform.runLater(() -> {
                if (!localFiles.isEmpty()) {
                    for (WallpaperImage img : localFiles) {
                        VBox card = createLocalCard(img);
                        addCardToMasonry(card, localMasonryColumns, localColumnHeights);
                    }
                }
            });
        });
    }

    /**
     * Menginisialisasi container ScrollPane agar mendukung tampilan Masonry.
     * * @param targetScroll ScrollPane yang akan dikonfigurasi.
     * @return HBox container utama yang akan menampung kolom-kolom VBox.
     */
    private HBox setupMasonryContainer(ScrollPane targetScroll) {
        if (targetScroll == null) return null;
        targetScroll.setFitToWidth(true);
        targetScroll.setPannable(true);
        targetScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        HBox container = new HBox(16);
        container.setPadding(new Insets(20, 35, 20, 20));
        container.setAlignment(Pos.TOP_CENTER);
        targetScroll.setContent(container);
        return container;
    }

    /**
     * Memastikan jumlah kolom dalam grid Masonry sesuai dengan lebar jendela aplikasi.
     * <p>Metode ini secara dinamis menambah atau mengurangi kolom saat jendela di-resize,
     * lalu mendistribusikan ulang kartu yang sudah ada agar layout tetap rapi.</p>
     * * @param container HBox induk.
     * @param columns List kolom VBox saat ini.
     * @param heights Map untuk melacak tinggi total setiap kolom.
     */
    private void ensureMasonryColumns(HBox container, List<VBox> columns, Map<VBox, Double> heights) {
        if (container == null) return;
        
        double w = rootPane.getWidth();
        // Kalkulasi jumlah kolom: minimal 1, lebar per kolom target ~260px
        int cols = Math.max(1, (int) (w / 260));
        if (cols == columns.size() && !columns.isEmpty()) return;

        // Backup kartu yang ada sebelum grid di-reset
        List<VBox> existingCards = new ArrayList<>();
        for (VBox col : columns) {
            for (Node node : col.getChildren()) {
                if (node instanceof VBox) existingCards.add((VBox) node);
            }
        }

        container.getChildren().clear();
        columns.clear();
        heights.clear();

        for (int i = 0; i < cols; i++) {
            VBox col = new VBox(12);
            col.setFillWidth(true);
            columns.add(col);
            heights.put(col, 0.0);
            container.getChildren().add(col);
        }
        
        // Distribusi ulang kartu ke kolom baru
        for (VBox card : existingCards) {
            addCardToMasonry(card, columns, heights);
        }
    }

    /**
     * Memasukkan kartu (VBox) ke dalam kolom grid yang memiliki tinggi total paling pendek.
     * <p>Algoritma ini memastikan distribusi gambar merata secara vertikal, 
     * mirip dengan layout Pinterest.</p>
     * * @param card Node kartu yang akan dimasukkan.
     * @param columns Daftar kolom yang tersedia.
     * @param heights Map data tinggi kolom untuk kalkulasi penempatan.
     */
    private void addCardToMasonry(VBox card, List<VBox> columns, Map<VBox, Double> heights) {
        if (columns.isEmpty()) return;
        
        // Cari kolom terpendek
        VBox targetCol = columns.get(0);
        double min = heights.getOrDefault(targetCol, 0.0);
        
        for (VBox c : columns) {
            double h = heights.getOrDefault(c, 0.0);
            if (h < min) { 
                min = h; 
                targetCol = c; 
            }
        }
        
        targetCol.getChildren().add(card);
        
        // Hitung estimasi tinggi untuk update map (height)
        double est = card.prefHeight(-1);
        if (est <= 0) est = 300; // Fallback jika belum di-render
        
        heights.put(targetCol, heights.getOrDefault(targetCol, 0.0) + est + 12);
        cardColumnMap.put(card, targetCol);
    }

    /**
     * Mengeksekusi logika pencarian wallpaper secara online.
     * <p>Alur kerja metode ini meliputi:</p>
     * <ul>
     * <li>Menutup popup saran pencarian jika masih terbuka.</li>
     * <li>Memvalidasi query dan mencatatnya ke dalam riwayat saran.</li>
     * <li>Mereset status pagination ke halaman pertama dan membersihkan grid Masonry.</li>
     * <li>Menampilkan kartu skeleton sebagai placeholder visual.</li>
     * <li>Memanggil {@link ProviderManager} untuk mengambil data gambar secara asinkron.</li>
     * <li>Merender kartu gambar secara paralel setelah data diterima.</li>
     * </ul>
     */
    @FXML
    public void onSearchAction() {
        if (suggestionsPopup != null && suggestionsPopup.isShowing()) {
            suggestionsPopup.hide();
        }

        String query = searchField.getText();
        if (query.isEmpty() || providerManager == null) return;

        // Logika pengosongan query untuk kembali ke mode awal
        if (query.isEmpty()) {
            toggleViewMode(false);
            return;
        }

        suggestionService.logSearch(query);
        mainTabPane.getSelectionModel().select(0);
        toggleViewMode(true);

        if (welcomeLabel != null) {
            welcomeLabel.setVisible(false);
            welcomeLabel.setManaged(false);
        }
        scrollPane.setVisible(true);

        currentQuery = query;
        currentPage = 1;
        isLoading = true;

        // Membersihkan kolom Masonry online untuk hasil pencarian baru
        for (VBox col : masonryColumns) col.getChildren().clear();
        columnHeights.replaceAll((k, v) -> 0.0);

        showSkeletons();

        providerManager.search(query, currentPage, screenWidth, screenHeight)
            .thenAccept(images -> {
                Platform.runLater(() -> {
                    removeSkeletons();
                    if (!images.isEmpty()) {
                        // Membuat kartu secara paralel untuk meningkatkan performa UI
                        createCardsInParallel(images).thenAccept(cards -> {
                            Platform.runLater(() -> {
                                for (VBox card : cards) {
                                    addCardToMasonry(card, masonryColumns, columnHeights);
                                }
                            });
                        });
                    }
                    isLoading = false;
                });
            })
            .exceptionally(ex -> { 
                Platform.runLater(() -> {
                    removeSkeletons();
                    isLoading = false;
                });
                logger.error("Pencarian gagal: ", ex); 
                return null; 
            });
    }

    /**
     * Menampilkan placeholder (skeleton cards) pada grid Masonry.
     * <p>Digunakan untuk memberikan indikasi visual kepada pengguna bahwa data sedang 
     * dimuat, sehingga aplikasi terasa lebih cepat dan responsif.</p>
     */
    private void showSkeletons() {
        for (int i = 0; i < 10; i++) {
            VBox skeleton = createSkeletonCard();
            addCardToMasonry(skeleton, masonryColumns, columnHeights);
            activeSkeletons.add(skeleton);
        }
    }

    /**
     * Menghapus semua kartu skeleton yang aktif dari tampilan.
     * <p>Metode ini juga melakukan koreksi pada variabel pelacak tinggi kolom 
     * ({@code columnHeights}) agar kalkulasi posisi kartu asli yang masuk 
     * tetap akurat sesuai algoritma Masonry.</p>
     */
    private void removeSkeletons() {
        for (Node node : activeSkeletons) {
            if (node.getParent() instanceof VBox) {
                VBox parentCol = (VBox) node.getParent();
                parentCol.getChildren().remove(node);
                
                if (columnHeights.containsKey(parentCol)) {
                    double currentH = columnHeights.get(parentCol);
                    // Koreksi tinggi: kurangi estimasi tinggi skeleton (200px) + margin (12px)
                    columnHeights.put(parentCol, Math.max(0, currentH - 212)); 
                }
            }
        }
        activeSkeletons.clear();
    }

    /**
     * Membuat komponen visual kartu skeleton tunggal.
     * <p>Kartu ini terdiri dari kotak placeholder dengan tinggi acak (untuk mensimulasikan 
     * tampilan Masonry asli) dan baris teks dummy. Dilengkapi dengan animasi fade-in-out 
     * (breathing effect) menggunakan {@link javafx.animation.FadeTransition}.</p>
     * * @return VBox yang telah dikonfigurasi sebagai kartu skeleton.
     */
    private VBox createSkeletonCard() {
        VBox card = new VBox(8);
        card.setPadding(Insets.EMPTY);
        
        double colWidth = getCurrentColWidth();
        
        // Kotak Abu-abu: Tinggi acak antara 150 - 300px agar layout terlihat dinamis
        double randomHeight = 150 + Math.random() * 150; 
        
        Region box = new Region();
        box.setPrefSize(colWidth, randomHeight);
        
        String color = isDarkMode ? "#333333" : "#e0e0e0";
        box.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 12;");

        // Baris Teks Dummy: Lebar 60% dari kolom
        Region textBar = new Region();
        textBar.setPrefSize(colWidth * 0.6, 12);
        textBar.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 4;");
        
        // Animasi Denyut (Breathing Effect) untuk menandakan aktivitas loading
        javafx.animation.FadeTransition fade = new javafx.animation.FadeTransition(Duration.millis(800), card);
        fade.setFromValue(0.5);
        fade.setToValue(1.0);
        fade.setCycleCount(javafx.animation.Animation.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();

        card.getChildren().addAll(box, textBar);
        return card;
    }

    /**
     * Menginisialisasi ikon aplikasi pada bar bagian atas (Top Bar).
     * <p>Metode ini memastikan ImageView hanya dibuat satu kali (singleton pattern di tingkat UI) 
     * untuk efisiensi memori, mengatur margin, dan menyisipkannya sebagai elemen pertama di TopBar.</p>
     * * @param imgWhite Objek {@link Image} versi putih/terang untuk penggunaan di tema gelap.
     * @param imgBlack Objek {@link Image} versi hitam/gelap untuk penggunaan di tema terang.
     */
    public void initAppIcons(Image imgWhite, Image imgBlack) {
        this.iconWhite = imgWhite;
        this.iconBlack = imgBlack;

        // 1. Inisialisasi ImageView dan properti visualnya
        headerIconView = new ImageView();
        headerIconView.setFitHeight(40);
        headerIconView.setPreserveRatio(true);
        headerIconView.setSmooth(true);
        HBox.setMargin(headerIconView, new Insets(0, 10, 0, 0));

        // 2. Masukkan ke struktur TopBar
        if (topBar != null) {
            // Validasi: Cegah duplikasi ikon jika metode init dipanggil berulang kali
            if (!topBar.getChildren().isEmpty() && topBar.getChildren().get(0) instanceof ImageView) {
                topBar.getChildren().remove(0);
            }
            topBar.getChildren().add(0, headerIconView);
        }

        // 3. Tentukan ikon yang aktif berdasarkan preferensi tema saat ini
        updateHeaderIcon();
    }

    /**
     * Memperbarui gambar pada header sesuai dengan status mode gelap/terang.
     * <p>Metode ini dipanggil saat inisialisasi awal maupun saat pengguna mengubah tema aplikasi.</p>
     */
    private void updateHeaderIcon() {
        if (headerIconView == null) return;
        
        if (isDarkMode) {
            if (iconWhite != null) headerIconView.setImage(iconWhite);
        } else {
            if (iconBlack != null) headerIconView.setImage(iconBlack);
        }
    }

    /**
     * Menghitung lebar dinamis untuk kolom berdasarkan lebar jendela saat ini.
     * <p>Rumus ini mempertimbangkan padding container (80px) dan jarak antar kolom (gutter 16px) 
     * untuk memastikan kartu mengisi ruang secara proporsional.</p>
     * * @return Nilai double yang merepresentasikan lebar kolom dalam piksel.
     */
    private double getCurrentColWidth() {
        double w = rootPane.getWidth();
        int cols = Math.max(1, (int) (w / 260));
        return (w - 80 - (cols * 16)) / cols;
    }

    /**
     * Helper untuk membuat kartu wallpaper hasil pencarian online.
     * @param data Objek data wallpaper dari provider online.
     * @return Komponen VBox kartu.
     */
    private VBox createMasonryCard(WallpaperImage data) {
        return createBaseCard(data, false);
    }

    /**
     * Helper untuk membuat kartu wallpaper dari koleksi lokal (downloaded).
     * @param data Objek data wallpaper dari penyimpanan lokal.
     * @return Komponen VBox kartu.
     */
    private VBox createLocalCard(WallpaperImage data) {
        return createBaseCard(data, true);
    }

    /**
     * Metode dasar (Unified) untuk mengonstruksi komponen kartu wallpaper.
     * <p>Metode ini mengintegrasikan beberapa logika kompleks:</p>
     * <ul>
     * <li><b>Aspek Rasio:</b> Menghitung tinggi target secara dinamis agar gambar tidak terdistorsi (stretch).</li>
     * <li><b>Clipping:</b> Menerapkan {@link Rectangle} dengan sudut membulat (arc) pada gambar.</li>
     * <li><b>Async Loading:</b> Membedakan loader antara {@code loadLocalImageAsync} dan {@code loadImageAsync} (internet).</li>
     * <li><b>Event Handling:</b> Mengelola proses download asinkron dan pengaturan wallpaper sistem (System Wallpaper) saat diklik.</li>
     * </ul>
     * * @param data Objek {@link WallpaperImage} yang berisi metadata gambar (URL, dimensi, deskripsi).
     * @param isLocal Flag penentu apakah data berasal dari sistem file lokal atau internet.
     * @return {@link VBox} yang telah diformat lengkap dengan efek hover dan aksi klik.
     */
    private VBox createBaseCard(WallpaperImage data, boolean isLocal) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(0));

        double colWidth = getCurrentColWidth();

        // Kalkulasi tinggi berdasarkan rasio asli gambar (jika tersedia)
        double targetHeight = 200; 
        if (data.getWidth() > 0 && data.getHeight() > 0) {
            targetHeight = (data.getHeight() / data.getWidth()) * colWidth;
        }

        ImageView imageView = new ImageView();
        imageView.setFitWidth(colWidth);
        imageView.setFitHeight(targetHeight);
        imageView.setPreserveRatio(false); 
        imageView.setSmooth(true);

        // Efek Visual: Rounded Corners
        Rectangle clip = new Rectangle(colWidth, targetHeight);
        clip.setArcWidth(24); clip.setArcHeight(24);
        imageView.setClip(clip);

        // Strategi Pemuatan Gambar
        if (isLocal) {
            loadLocalImageAsync(data.getThumbnailUrl(), imageView, colWidth);
        } else {
            loadImageAsync(data.getThumbnailUrl(), imageView);
        }

        Label descLabel = new Label(isLocal ? data.getId() : data.getDescription());
        descLabel.setMaxWidth(colWidth - 10);
        descLabel.setPadding(new Insets(0, 10, 10, 10));

        setupHoverEffect(card);

        // Penanganan Interaksi Klik
        card.setOnMouseClicked(e -> {
            card.setOpacity(0.7);
            CompletableFuture.runAsync(() -> {
                try {
                    if (isLocal) {
                        // Logika pemasangan wallpaper dari file yang sudah ada di disk
                        logger.info("Setting local wallpaper: " + data.getId());
                        File file = new File(URI.create(data.getImageUrl()));
                        if (file.exists()) {
                            boolean setSuccess = systemHandler.setWallpaper(file);
                            if (setSuccess) Snackbar.showWallpaperSetSuccess(scene);
                            else Snackbar.showWallpaperSetFailed(scene);
                            Platform.runLater(() -> card.setOpacity(1.0));
                        }
                    } else {
                        // Logika unduh file sementara kemudian dipasang sebagai wallpaper
                        logger.info("Downloading wallpaper: " + data.getId());
                        Snackbar.showDownloading(scene);
                        systemHandler.downloadImage(data.getImageUrl(), data.getId() + ".jpg")
                            .thenAccept(downloaded -> {
                                if (downloaded != null) {
                                    Snackbar.showDownloadSuccess(scene);
                                    if (systemHandler.setWallpaper(downloaded)) {
                                        Snackbar.showWallpaperSetSuccess(scene);
                                    } else {
                                        Snackbar.showWallpaperSetFailed(scene);
                                    }
                                    Platform.runLater(() -> card.setOpacity(1.0));
                                }
                            })
                            .exceptionally(ex -> {
                                logger.error("Gagal mengunduh wallpaper", ex);
                                Snackbar.showDownloadFailed(scene);
                                Platform.runLater(() -> card.setOpacity(1.0));
                                return null;
                            });
                    }
                } catch (Exception ex) {
                    logger.error("Terjadi kesalahan pada aksi kartu", ex);
                    Platform.runLater(() -> card.setOpacity(1.0));
                }
            });
        });

        card.getChildren().addAll(imageView, descLabel);
        styleCardVBox(card); // Menerapkan CSS styling akhir
        return card;
    }

    /**
     * Memuat gambar thumbnail dari internet secara asinkron.
     * <p>Menggunakan {@link java.net.http.HttpClient} untuk mengambil aliran data (InputStream) 
     * tanpa memblokir thread UI. Setelah berhasil, gambar akan dipasang ke ImageView melalui 
     * {@link Platform#runLater(Runnable)}.</p>
     * * @param url    URL gambar yang akan dimuat.
     * @param target ImageView tujuan tempat gambar akan ditampilkan.
     */
    private void loadImageAsync(String url, ImageView target) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
                
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    Image image = new Image(response.body());
                    Platform.runLater(() -> target.setImage(image));
                }
            } catch (Exception e) { 
                logger.error("Gagal memuat thumbnail online: ", e); 
            }
        });
    }

    /**
     * Memuat gambar thumbnail dari penyimpanan lokal secara asinkron.
     * <p>Optimasi dilakukan dengan menentukan {@code reqWidth} agar JavaFX tidak memuat 
     * resolusi penuh ke memori jika hanya ditampilkan dalam ukuran kecil.</p>
     * * @param url      Path file lokal atau URL resource lokal.
     * @param target   ImageView tujuan.
     * @param reqWidth Lebar target untuk proses scaling saat pemuatan (load-time scaling).
     */
    private void loadLocalImageAsync(String url, ImageView target, double reqWidth) {
        CompletableFuture.runAsync(() -> {
            try {
                double widthToLoad = (reqWidth > 0) ? reqWidth : 300;
                // Mengaktifkan smooth scaling dan preserve ratio saat loading
                Image image = new Image(url, widthToLoad, 0, true, false);
                if (image.isError()) return;
                Platform.runLater(() -> target.setImage(image));
            } catch (Exception e) { 
                logger.error("Gagal memuat thumbnail lokal: ", e); 
            }
        });
    }

    /**
     * Menambahkan efek animasi scaling saat kursor mouse berada di atas kartu (hover).
     * <p>Memberikan umpan balik visual berupa perbesaran halus (2%) dan perubahan 
     * bentuk kursor menjadi tangan (hand cursor).</p>
     * * @param card Node VBox yang akan diberikan efek hover.
     */
    private void setupHoverEffect(VBox card) {
        ScaleTransition stEnter = new ScaleTransition(Duration.millis(150), card);
        stEnter.setToX(1.02); stEnter.setToY(1.02);
        
        ScaleTransition stExit = new ScaleTransition(Duration.millis(150), card);
        stExit.setToX(1.0); stExit.setToY(1.0);

        card.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            stExit.stop(); 
            stEnter.playFromStart();
            card.setStyle("-fx-cursor: hand;");
        });
        
        card.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            stEnter.stop(); 
            stExit.playFromStart();
        });
    }

    /**
     * Menerapkan gaya visual (CSS dan Efek) pada kartu wallpaper.
     * <p>Mengatur warna latar belakang, radius sudut (rounded corners), shadow, 
     * dan warna teks label berdasarkan status mode gelap (Dark Mode).</p>
     * * @param card VBox kartu yang akan di-styling.
     */
    private void styleCardVBox(VBox card) {
        String cardBg = isDarkMode ? "#181818" : "#ffffff";
        String textColor = isDarkMode ? "#dddddd" : "#333333";
        String shadowCol = isDarkMode ? "rgba(0,0,0,0.5)" : "rgba(0,0,0,0.15)";

        card.setBackground(new Background(new BackgroundFill(
            Color.web(cardBg), new CornerRadii(12), Insets.EMPTY)));
        card.setEffect(new DropShadow(10, 4, 4, Color.web(shadowCol)));

        for (Node n : card.getChildren()) {
            if (n instanceof Label) {
                ((Label) n).setStyle("-fx-font-size: 12px; -fx-text-fill: " + textColor + ";");
            }
        }
    }

    /**
     * Membuat sekumpulan kartu Masonry secara paralel untuk meningkatkan responsivitas UI.
     * <p>Metode ini sangat penting saat menangani hasil pencarian dalam jumlah besar agar 
     * proses instansiasi objek VBox tidak menyebabkan UI 'freeze'.</p>
     * * @param images Daftar objek {@link WallpaperImage}.
     * @return {@link CompletableFuture} yang berisi daftar VBox yang telah dibuat.
     */
    private CompletableFuture<List<VBox>> createCardsInParallel(List<WallpaperImage> images) {
        if (images == null || images.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        // Membagi tugas pembuatan setiap kartu ke thread pool
        List<CompletableFuture<VBox>> cardFutures = images.stream()
            .map(img -> CompletableFuture.supplyAsync(() -> createMasonryCard(img)))
            .toList();
        
        // Menunggu semua proses pembuatan kartu selesai (join)
        return CompletableFuture.allOf(cardFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> cardFutures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    /**
     * Membuat kartu horizontal untuk bagian kategori/trending secara paralel.
     * <p>Mirip dengan {@link #createCardsInParallel}, namun ditujukan untuk layout 
     * horizontal dengan logika khusus untuk item unggulan (featured).</p>
     * * @param images            Daftar gambar.
     * @param isTrendingSection Flag jika ini adalah bagian trending.
     * @return CompletableFuture berisi daftar kartu.
     */
    private CompletableFuture<List<VBox>> createHorizontalCardsInParallel(List<WallpaperImage> images, boolean isTrendingSection) {
        if (images == null || images.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }
        
        List<CompletableFuture<VBox>> cardFutures = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            final int index = i;
            WallpaperImage img = images.get(i);
            boolean isFeatured = isTrendingSection && (index == 0);
            cardFutures.add(CompletableFuture.supplyAsync(() -> createHorizontalCard(img, isFeatured, isTrendingSection)));
        }
        
        return CompletableFuture.allOf(cardFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> cardFutures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    /**
     * Melakukan pemuatan awal (preloading) thumbnail secara paralel ke dalam cache internal.
     * <p>Teknik ini mengunduh aliran data gambar tanpa menampilkannya langsung, sehingga 
     * saat kartu benar-benar muncul di layar (scroll), gambar sudah tersedia di cache sistem/aplikasi.</p>
     * * @param urls Daftar URL thumbnail yang akan di-preload.
     */
    private void preloadThumbnailsInParallel(List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        
        List<CompletableFuture<Void>> preloadTasks = urls.stream()
            .map(url -> CompletableFuture.runAsync(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", "Mozilla/5.0")
                        .GET()
                        .build();
                    
                    HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                    if (response.statusCode() == 200) {
                        // Mengonsumsi stream sepenuhnya untuk memastikan data tersimpan di cache
                        try (InputStream stream = response.body()) {
                            byte[] buffer = new byte[4096];
                            while (stream.read(buffer) != -1) { /* Just consuming */ }
                        }
                        logger.trace("Berhasil preload thumbnail: {}", url);
                    }
                } catch (Exception e) {
                    logger.trace("Gagal preload thumbnail: {}", url);
                }
            }))
            .toList();
        
        // Eksekusi secara 'fire and forget' (tidak memblokir eksekusi utama)
        CompletableFuture.allOf(preloadTasks.toArray(new CompletableFuture[0]))
            .thenRun(() -> logger.debug("Preloading thumbnail selesai untuk {} gambar", urls.size()));
    }

    /**
     * Menutup aplikasi secara permanen.
     * <p>Memanggil {@link Platform#exit()} untuk menghentikan thread JavaFX dan 
     * {@link System#exit(int)} untuk memastikan proses JVM benar-benar berakhir.</p>
     */
    @FXML 
    private void closeApp() { 
        Platform.exit(); 
        System.exit(0); 
    }

    /**
     * Meminimalkan jendela aplikasi ke taskbar (Iconified).
     */
    @FXML 
    private void minimizeApp() { 
        ((javafx.stage.Stage) rootPane.getScene().getWindow()).setIconified(true); 
    }

    /**
     * Menangani logika Maximize dan Restore jendela aplikasi secara kustom.
     * <p>Karena menggunakan jendela tanpa dekorasi (undecorated stage), metode ini 
     * mensimulasikan perilaku sistem dengan:</p>
     * <ul>
     * <li>Mendeteksi layar (monitor) tempat jendela berada saat ini menggunakan {@link Screen}.</li>
     * <li>Menggunakan {@code visualBounds} agar saat dimaksimalkan, jendela tidak menutupi Taskbar.</li>
     * <li>Menyimpan posisi dan ukuran asli sebelum dimaksimalkan untuk proses Restore.</li>
     * <li>Mengubah ikon tombol antara "⬜" (Maximize) dan "❐" (Restore).</li>
     * </ul>
     */
    @FXML
    private void maximizeApp() {
        javafx.stage.Stage stage = (javafx.stage.Stage) rootPane.getScene().getWindow();
        
        // Menentukan layar aktif agar dukungan multi-monitor berjalan baik
        List<Screen> screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        Rectangle2D bounds = screen.getVisualBounds();

        if (isMaximized) {
            // Logika RESTORE: Mengembalikan ke koordinat dan ukuran yang disimpan sebelumnya
            stage.setX(storedX);
            stage.setY(storedY);
            stage.setWidth(storedWidth);
            stage.setHeight(storedHeight);
            
            if (btnMax != null) btnMax.setText("⬜"); 
            isMaximized = false;
        } else {
            // Logika MAXIMIZE: Simpan state saat ini sebelum mengubah ukuran
            storedX = stage.getX();
            storedY = stage.getY();
            storedWidth = stage.getWidth();
            storedHeight = stage.getHeight();

            // Mengisi layar sesuai batas visual (Taskbar safe)
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            if (btnMax != null) btnMax.setText("❐"); 
            isMaximized = true;
        }
    }

    /**
     * Mengganti tema aplikasi secara dinamis antara Mode Gelap (Dark) dan Mode Terang (Light).
     * <p>Metode ini memicu pembaruan gaya pada seluruh elemen root dan melakukan 
     * iterasi ke semua komponen kartu yang sudah di-render untuk memastikan 
     * konsistensi visual tanpa perlu memuat ulang data.</p>
     */
    @FXML 
    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        applyCurrentTheme(); // Mengupdate stylesheet utama dan ikon header
        updateExistingCards(); // Mengupdate gaya kartu yang sudah tampil di layar
    }

    /**
     * Melakukan sinkronisasi tema pada seluruh kartu wallpaper yang ada di berbagai container.
     * <p>Metode ini melakukan iterasi mendalam (deep iteration) pada:</p>
     * <ul>
     * <li>Kolom Masonry hasil pencarian online.</li>
     * <li>Kolom Masonry koleksi lokal.</li>
     * <li>Kontainer Explore yang memiliki struktur kompleks (VBox > ScrollPane > HBox).</li>
     * </ul>
     * <p>Khusus untuk <i>Featured Card</i>, metode ini menerapkan kembali efek shadow 
     * berwarna khusus agar tetap terlihat menonjol setelah gaya dasarnya diperbarui.</p>
     */
    private void updateExistingCards() {
        // Pembaruan kartu pada grid hasil pencarian online
        for (VBox col : masonryColumns) {
            for (Node n : col.getChildren()) {
                if (n instanceof VBox) styleCardVBox((VBox) n);
            }
        }
        
        // Pembaruan kartu pada koleksi lokal
        for (VBox col : localMasonryColumns) {
            for (Node n : col.getChildren()) {
                if (n instanceof VBox) styleCardVBox((VBox) n);
            }
        }

        // Pembaruan pada bagian Explore (Horizontal Layout)
        if (exploreContainer != null) {
            for (Node section : exploreContainer.getChildren()) {
                if (section instanceof VBox) {
                    VBox sectionBox = (VBox) section;
                    // Mengakses konten di dalam ScrollPane
                    if (sectionBox.getChildren().size() > 1 && sectionBox.getChildren().get(1) instanceof ScrollPane) {
                        ScrollPane hScroll = (ScrollPane) sectionBox.getChildren().get(1);
                        if (hScroll.getContent() instanceof HBox) {
                            HBox contentBox = (HBox) hScroll.getContent();
                            for (Node card : contentBox.getChildren()) {
                                if (card instanceof VBox) {
                                    VBox vCard = (VBox) card;
                                    styleCardVBox(vCard);
                                    
                                    // Logika khusus Featured Card: Jika lebar gambar > 300px, berikan shadow biru aksen
                                    if (!vCard.getChildren().isEmpty() && vCard.getChildren().get(0) instanceof ImageView) {
                                        ImageView img = (ImageView) vCard.getChildren().get(0);
                                        if (img.getFitWidth() > 300) {
                                            vCard.setEffect(new DropShadow(20, Color.web("#0078d744")));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Menerapkan gaya visual tema (Mode Gelap atau Terang) ke seluruh elemen antarmuka pengguna.
     * <p>Metode ini berfungsi sebagai pusat kendali gaya dinamis yang melakukan hal-hal berikut:</p>
     * <ul>
     * <li><b>Definisi Palet:</b> Menentukan variabel warna untuk latar belakang, teks, aksen, dan elemen 
     * kontrol berdasarkan status {@code isDarkMode}.</li>
     * <li><b>Integrasi CSS Kustom:</b> Mendefinisikan variabel CSS (seperti {@code -theme-tab-text}) 
     * pada level root untuk digunakan oleh elemen yang sulit dijangkau melalui metode Java standar.</li>
     * <li><b>Styling Window:</b> Mengatur radius sudut jendela (12px) dan border aplikasi agar tetap 
     * estetik pada mode <i>undecorated stage</i>.</li>
     * <li><b>Iterasi Komponen:</b> Melakukan pembaruan gaya pada elemen-elemen spesifik seperti 
     * <i>Search Field</i>, tombol kontrol (Min/Max/Close), <i>ComboBox</i>, hingga container <i>Explore</i>.</li>
     * </ul>
     * <p>Penting: Metode ini secara eksplisit melakukan {@code rootPane.setBackground(Background.EMPTY)} 
     * sebelum menyuntikkan gaya melalui String CSS untuk menghindari konflik rendering antara objek 
     * JavaFX Paint dan String CSS.</p>
     * * 
     */
    private void applyCurrentTheme() {
        // --- 1. PENENTUAN PALETTE WARNA ---
        String bgRoot = isDarkMode ? "#0e0e0eff" : "#f4f4f4"; 
        String bgBar  = isDarkMode ? "#121212" : "#ffffff"; 
        String bgInput= isDarkMode ? "#1e1e1e" : "#eeeeee"; 
        String textCol= isDarkMode ? "white"   : "black";
        String subText= isDarkMode ? "#aaaaaa" : "#555555";
        String accent = "#0c6370"; 

        // Warna khusus untuk elemen fungsional
        String scrollColor = isDarkMode ? "#333333" : "#C0C0C0";
        String tabText     = isDarkMode ? "#828282ff" : "#777777"; 
        String tabTextSel  = isDarkMode ? "white"   : "black"; 
        String tabHoverBg  = isDarkMode ? "rgba(255, 255, 255, 0.1)" : "rgba(0, 0, 0, 0.05)";

        // --- 2. ROOT & SCROLLPANE STYLING ---
        // Set background awal (Paint)
        rootPane.setBackground(new Background(new BackgroundFill(Color.web(bgRoot), CornerRadii.EMPTY, Insets.EMPTY)));
        
        // Membersihkan style ScrollPane agar transparan terhadap background root
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        // --- 3. TOP BAR & LOGO ---
        topBar.setBackground(new Background(new BackgroundFill(Color.web(bgBar), CornerRadii.EMPTY, Insets.EMPTY)));
        topBar.setPadding(new Insets(14));
        topBar.setEffect(new DropShadow(5, Color.rgb(0,0,0, isDarkMode ? 0.5 : 0.1)));

        if(logoLabel != null) logoLabel.setTextFill(Color.web(textCol));

        // --- 4. SEARCH FIELD & TOGGLE THEME BUTTON ---
        searchField.setBackground(new Background(new BackgroundFill(Color.web(bgInput), new CornerRadii(8), Insets.EMPTY)));
        searchField.setPadding(new Insets(10));
        searchField.setStyle("-fx-text-fill: " + textCol + "; -fx-prompt-text-fill: " + subText + "; -fx-font-size: 14px;");

        themeBtn.setText(isDarkMode ? "☀" : "🌙");
        themeBtn.setBackground(new Background(new BackgroundFill(Color.web(bgInput), new CornerRadii(8), Insets.EMPTY)));
        themeBtn.setTextFill(Color.web(textCol));
        themeBtn.setPadding(new Insets(8, 12, 8, 12));
        themeBtn.setStyle("-fx-font-size: 16px; -fx-cursor: hand;");

        // --- 5. LABELS & BUTTONS ---
        if (welcomeLabel != null) {
            welcomeLabel.setTextFill(Color.web(textCol));
            welcomeLabel.setStyle("-fx-font-size: 64px; -fx-font-family: 'Segoe UI Light', 'Arial'; -fx-opacity: 0.9;");
        }

        // Styling tombol pencarian/umum di Top Bar menggunakan Stream API
        topBar.getChildren().stream()
                .filter(n -> n instanceof Button && n != themeBtn && n != btnMin && n != btnMax && n != btnClose)
                .forEach(n -> {
                    Button b = (Button) n;
                    b.setBackground(new Background(new BackgroundFill(Color.web(accent), new CornerRadii(8), Insets.EMPTY)));
                    b.setTextFill(Color.WHITE);
                    b.setPadding(new Insets(9, 20, 9, 20));
                    b.setStyle("-fx-font-weight: bold; -fx-cursor: hand;");
                });

        // --- 6. COMBOBOX & WINDOW CONTROLS ---
        styleComboBox(bgInput, textCol, subText);

        String winBtnColor = isDarkMode ? "white" : "black";
        String hoverColor = isDarkMode ? "#333333" : "#e0e0e0";
        String baseStyle = "-fx-background-color: transparent; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px; -fx-font-weight: bold;";
        
        // Logika Hover untuk Window Buttons (Minimize, Maximize, Close)
        if (btnMin != null) {
            btnMin.setStyle(baseStyle);
            btnMin.setOnMouseEntered(e -> btnMin.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px;"));
            btnMin.setOnMouseExited(e -> btnMin.setStyle(baseStyle));
        }
        if (btnMax != null) {
            btnMax.setStyle(baseStyle);
            btnMax.setOnMouseEntered(e -> btnMax.setStyle("-fx-background-color: " + hoverColor + "; -fx-text-fill: " + winBtnColor + "; -fx-font-size: 14px;"));
            btnMax.setOnMouseExited(e -> btnMax.setStyle(baseStyle));
        }
        if (btnClose != null) {
            btnClose.setStyle(baseStyle);
            btnClose.setOnMouseEntered(e -> btnClose.setStyle("-fx-background-color: #e81123; -fx-text-fill: white; -fx-font-size: 14px;"));
            btnClose.setOnMouseExited(e -> btnClose.setStyle(baseStyle));
        }

        updateHeaderIcon();

        // --- 7. FINAL ROOT STYLING (Radius, Border, & CSS Variables) ---
        String borderColor = isDarkMode ? "#333333" : "#cccccc";
        String rootStyle = 
            "-fx-background-color: " + bgRoot + ";" + 
            "-fx-background-radius: 12;" + 
            "-fx-border-color: " + borderColor + ";" + 
            "-fx-border-width: 1;" +
            "-fx-border-radius: 12;" + 
            "-fx-thumb-color: " + scrollColor + ";" +
            "-theme-tab-text: " + tabText + ";" +
            "-theme-tab-text-selected: " + tabTextSel + ";" +
            "-theme-tab-hover-bg: " + tabHoverBg + ";";

        rootPane.setBackground(Background.EMPTY); // Reset untuk menerapkan rootStyle dengan aman
        rootPane.setStyle(rootStyle);

        // Styling khusus untuk Top Bar agar sinkron dengan radius Root
        topBar.setStyle("-fx-background-color: " + bgBar + "; -fx-background-radius: 12 12 0 0;");

        // --- 8. POPUP & EXPLORE SECTIONS ---
        if (suggestionsPopup != null) {
            suggestionsPopup.setStyle(
                "-fx-background-color: " + (isDarkMode ? "#1e1e1e" : "#ffffff") + ";" +
                "-fx-text-fill: " + (isDarkMode ? "white" : "black") + ";" +
                "-fx-background-radius: 0 0 8 8;" +
                "-fx-border-color: " + (isDarkMode ? "#333333" : "#cccccc") + ";" +
                "-fx-border-width: 0 1 1 1;" +
                "-fx-border-radius: 0 0 8 8;" +
                "-fx-width: 500px;"
            );
        }

        if (exploreContainer != null) {
            String titleColor = isDarkMode ? "white" : "#333333";
            for (Node section : exploreContainer.getChildren()) {
                if (section instanceof VBox) {
                    VBox box = (VBox) section;
                    if (!box.getChildren().isEmpty() && box.getChildren().get(0) instanceof HBox) {
                        HBox header = (HBox) box.getChildren().get(0);
                        if (!header.getChildren().isEmpty() && header.getChildren().get(0) instanceof Label) {
                            ((Label) header.getChildren().get(0)).setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + titleColor + ";");
                        }
                    }
                }
            }
        }
    }

    /**
     * Mengatur gaya visual kustom untuk komponen ComboBox (pemilih provider).
     * <p>Karena ComboBox merupakan kontrol kompleks yang terdiri dari beberapa bagian internal, 
     * metode ini melakukan styling pada beberapa level:</p>
     * <ul>
     * <li><b>Main Button:</b> Mengatur latar belakang, radius sudut, dan border utama.</li>
     * <li><b>Arrow & Arrow Button:</b> Menggunakan {@link Node#lookup(String)} untuk mencari 
     * sub-elemen kustom (".arrow" dan ".arrow-button") guna mengubah warna ikon panah.</li>
     * <li><b>Cell Factory:</b> Mendefinisikan ulang cara setiap item (ListCell) di dalam dropdown di-render, 
     * termasuk logika hover dan seleksi dengan warna aksen kustom (#0c6370).</li>
     * <li><b>Popup Shadow:</b> Menggunakan event {@code setOnShowing} untuk mengakses ListView internal 
     * dari dropdown dan memberikan efek {@link DropShadow} (melalui CSS {@code -fx-effect}).</li>
     * </ul>
     * <p>Penggunaan {@link Platform#runLater(Runnable)} diperlukan saat mencari sub-node (lookup) 
     * karena node-node tersebut mungkin belum di-instansiasi oleh skin JavaFX saat inisialisasi awal.</p>
     * * @param bgInput Warna latar belakang input yang sesuai dengan tema aktif.
     * @param textCol Warna teks utama.
     * @param subText Warna teks sekunder/prompt.
     */
    private void styleComboBox(String bgInput, String textCol, String subText) {
        // --- SETUP WARNA DASAR ---
        String borderColor = isDarkMode ? "#333333" : "#cccccc"; 
        String arrowColor  = isDarkMode ? "#aaaaaa" : "#333333"; 
        
        // Shadow Popup: Menggunakan format string CSS DropShadow
        String shadowEffect = isDarkMode 
            ? "dropshadow(three-pass-box, rgba(0,0,0,0.6), 8, 0, 0, 4)" 
            : "dropshadow(three-pass-box, rgba(0,0,0,0.1), 8, 0, 0, 2)";

        // --- 1. STYLING KONTROL UTAMA ---
        providerCombo.setStyle(
            "-fx-background-color: " + bgInput + ";" +
            "-fx-background-radius: 8;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 8;" +
            "-fx-border-width: 1px;" +
            "-fx-text-fill: " + textCol + ";" + 
            "-fx-font-size: 14px;" +
            "-fx-font-family: 'Segoe UI';" +
            "-fx-padding: 2 6 2 10;"
        );

        // --- 2. STYLING SUB-ELEMEN (PANAH) ---
        Platform.runLater(() -> {
            Node arrow = providerCombo.lookup(".arrow");
            Node arrowBtn = providerCombo.lookup(".arrow-button");
            if (arrow != null) arrow.setStyle("-fx-background-color: " + arrowColor + ";");
            if (arrowBtn != null) arrowBtn.setStyle("-fx-background-insets: 0; -fx-padding: 8; -fx-background-color: transparent;");
        });

        // --- 3. STYLING LIST CELLS (ISI DROPDOWN) ---
        providerCombo.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                
                setText(null);
                setGraphic(null);
                
                if (empty || item == null) {
                    setStyle("-fx-background-color: transparent;");
                } else {
                    setText(item);
                    String baseFont = "-fx-font-family: 'Segoe UI'; -fx-font-size: 14px; -fx-padding: 8 12 8 12; ";
                    String currentBg;
                    String currentTextFill;

                    // Logika pewarnaan saat sel dipilih atau diarahkan kursor
                    if (isSelected() || isHover()) {
                        currentBg = "#0c6370"; // Warna aksen biru tua
                        currentTextFill = "white";
                    } else {
                        currentBg = bgInput;
                        currentTextFill = textCol; 
                    }

                    setStyle(baseFont + "-fx-background-color: " + currentBg + "; -fx-text-fill: " + currentTextFill + ";");

                    // Listener interaksi mouse manual untuk mempercepat respon visual
                    setOnMouseEntered(e -> setStyle(baseFont + "-fx-background-color: #0c6370; -fx-text-fill: white;"));
                    setOnMouseExited(e -> {
                        String bg = isSelected() ? "#0c6370" : bgInput;
                        String tf = isSelected() ? "white" : textCol;
                        setStyle(baseFont + "-fx-background-color: " + bg + "; -fx-text-fill: " + tf + ";");
                    });
                }
            }
        });

        // --- 4. BUTTON CELL (AREA TEKS TERPILIH) ---
        providerCombo.setButtonCell(new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (!empty && item != null) {
                    setText(item);
                    setStyle("-fx-text-fill: " + textCol + "; -fx-font-size: 14px; -fx-font-family: 'Segoe UI'; -fx-background-color: transparent;");
                } else { setText(null); }
            }
        });

        // --- 5. POPUP CONTAINER STYLING ---
        // Memastikan ListView (popup) memiliki border-radius dan shadow yang konsisten
        providerCombo.setOnShowing(event -> {
            Node listView = providerCombo.lookup(".list-view");
            if (listView != null) {
                listView.setStyle(
                    "-fx-background-color: " + bgInput + ";" +
                    "-fx-background-radius: 8;" +
                    "-fx-border-color: " + borderColor + ";" +
                    "-fx-border-radius: 8;" +
                    "-fx-border-width: 1px;" +
                    "-fx-effect: " + shadowEffect + ";"
                );
            }
        });
        
        // Listener tambahan jika Skin berubah untuk menjamin style tetap diterapkan
        providerCombo.skinProperty().addListener((obs, old, skin) -> {
            if(skin != null) {
                Node listView = providerCombo.lookup(".list-view");
                if (listView != null) {
                    listView.setStyle("-fx-background-color: " + bgInput + "; -fx-background-radius: 8; -fx-effect: " + shadowEffect + ";");
                }
            }
        });
    }

    /**
     * Mendeteksi apakah sistem operasi Windows sedang menggunakan Mode Gelap (Dark Mode).
     * <p>Metode ini bekerja dengan melakukan query ke Windows Registry melalui {@link ProcessBuilder}. 
     * Kunci registri yang diperiksa adalah {@code AppsUseLightTheme}. Jika nilainya {@code 0x0}, 
     * berarti sistem sedang dalam mode gelap.</p>
     * * <ul>
     * <li><b>Kompatibilitas:</b> Jika OS bukan Windows, metode ini secara default mengembalikan {@code true}.</li>
     * <li><b>Keamanan:</b> Jika terjadi kesalahan saat akses registry (izin akses, dll), 
     * aplikasi akan default ke mode gelap demi kenyamanan visual.</li>
     * </ul>
     * * @return {@code true} jika sistem dalam mode gelap atau jika bukan sistem operasi Windows.
     */
    private boolean isWindowsDarkMode() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (!os.contains("win")) {
                return true; 
            }

            // Command untuk memeriksa status tema melalui Registry Windows
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "query", 
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", 
                "/v", "AppsUseLightTheme"
            );
            
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            
            // "0x0" menunjukkan Light Theme bernilai false (berarti Dark Mode aktif)
            return output.contains("0x0");

        } catch (Exception e) {
            return true; 
        }
    }

    /**
     * Mengatur alur navigasi fokus (Focus Traversal) antar elemen antarmuka.
     * <p>Metode ini sangat penting untuk pengalaman pengguna (UX) yang bersih, dengan tujuan:</p>
     * * <ul>
     * <li><b>Mencegah Fokus Liar:</b> Mematikan {@code focusTraversable} pada tombol kontrol window, 
     * tab pane, dan scroll pane agar pengguna tidak sengaja memberikan fokus pada elemen tersebut 
     * saat menekan tombol Tab.</li>
     * <li><b>Input-Centric:</b> Memastikan hanya {@code searchField} yang dapat menerima fokus utama.</li>
     * <li><b>Auto-Focus:</b> Mengarahkan fokus kursor ke kotak pencarian secara otomatis saat aplikasi 
     * pertama kali dimuat menggunakan {@link Platform#runLater(Runnable)}.</li>
     * </ul>
     */
    private void setupFocusTraversal() {
        // Nonaktifkan fokus untuk elemen-elemen di Top Bar
        for (Node node : topBar.getChildren()) {
            node.setFocusTraversable(false);
        }

        // Nonaktifkan fokus untuk container utama
        mainTabPane.setFocusTraversable(false);
        scrollPane.setFocusTraversable(false);
        if (localScrollPane != null) localScrollPane.setFocusTraversable(false);

        // Nonaktifkan fokus untuk tombol aksi agar tidak mengganggu navigasi keyboard
        if (btnMin != null) btnMin.setFocusTraversable(false);
        if (btnMax != null) btnMax.setFocusTraversable(false);
        if (btnClose != null) btnClose.setFocusTraversable(false);
        if (themeBtn != null) themeBtn.setFocusTraversable(false);
        if (providerCombo != null) providerCombo.setFocusTraversable(false);

        // Aktifkan fokus hanya pada input pencarian
        searchField.setFocusTraversable(true);
        
        // Memaksa fokus ke search field setelah scene siap
        Platform.runLater(() -> searchField.requestFocus());
    }
}