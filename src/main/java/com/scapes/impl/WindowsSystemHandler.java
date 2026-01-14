package com.scapes.impl;

import com.scapes.core.SystemHandler;
import com.scapes.model.WallpaperImage;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.win32.W32APIOptions;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;

/**
 * Implementasi {@link SystemHandler} khusus untuk sistem operasi Windows.
 *
 * <p>Kelas ini bertanggung jawab untuk menangani seluruh operasi
 * yang bersifat platform-dependent pada Windows, antara lain:</p>
 * <ul>
 *   <li>Mengunduh wallpaper ke direktori lokal</li>
 *   <li>Mengatur wallpaper desktop menggunakan Windows API</li>
 *   <li>Mengambil daftar wallpaper yang telah diunduh</li>
 * </ul>
 *
 * <p>Integrasi dengan sistem operasi dilakukan menggunakan
 * <b>Java Native Access (JNA)</b> untuk memanggil fungsi native
 * Windows <code>SystemParametersInfo</code>.</p>
 *
 * <p>Seluruh proses I/O berat seperti download dilakukan secara
 * asynchronous menggunakan {@link CompletableFuture}
 * agar tidak memblokir thread UI JavaFX.</p>
 *
 * <p>Struktur direktori default penyimpanan wallpaper:</p>
 * <pre>
 * C:\Users\{username}\Pictures\Scapes
 * </pre>
 *
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class WindowsSystemHandler implements SystemHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(WindowsSystemHandler.class);

    /** Direktori default untuk menyimpan wallpaper hasil unduhan */
    private static final Path downloadPath =
            Path.of(System.getProperty("user.home"), "Pictures", "Scapes");

    /**
     * Binding Windows API User32 menggunakan JNA.
     *
     * <p>Interface ini digunakan untuk memanggil fungsi native
     * <code>SystemParametersInfo</code> yang bertugas mengatur
     * wallpaper desktop pada Windows.</p>
     */
    public interface User32 extends Library {

        /** Instance singleton User32 */
        User32 INSTANCE =
                Native.load("user32", User32.class, W32APIOptions.UNICODE_OPTIONS);

        /** Action code untuk mengatur wallpaper desktop */
        int SPI_SETDESKWALLPAPER = 0x0014;

        /** Menulis perubahan ke file konfigurasi sistem */
        int SPIF_UPDATEINIFILE = 0x01;

        /** Memberi notifikasi ke seluruh sistem tentang perubahan */
        int SPIF_SENDWININICHANGE = 0x02;

        /**
         * Fungsi native Windows untuk mengatur parameter sistem.
         *
         * @param uiAction jenis aksi sistem
         * @param uiParam parameter tambahan
         * @param pvParam parameter utama (path wallpaper)
         * @param fWinIni flag update sistem
         * @return {@code true} jika operasi berhasil
         */
        boolean SystemParametersInfo(
                int uiAction,
                int uiParam,
                String pvParam,
                int fWinIni
        );
    }

    /**
     * Mengunduh gambar wallpaper dari URL dan menyimpannya ke direktori lokal.
     *
     * <p>Jika file dengan nama yang sama sudah ada dan valid,
     * proses download akan dilewati (cached).</p>
     *
     * <p>Nama file akan disanitasi untuk menghindari karakter
     * yang tidak valid pada sistem file Windows.</p>
     *
     * @param url URL gambar wallpaper
     * @param filename nama file tujuan
     * @return {@link CompletableFuture} yang berisi {@link File}
     *         hasil download, atau {@code null} jika gagal
     */
    @Override
    public CompletableFuture<File> downloadImage(String url, String filename) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String safeFilename =
                        filename.replaceAll("[^a-zA-Z0-9.-]", "_");
                Path destination = downloadPath.resolve(safeFilename);
                File file = destination.toFile();

                if (file.exists() && file.length() > 0) {
                    logger.info(
                        "Requested image is already cached, skipped downloading: {}",
                        safeFilename
                    );
                    return file;
                }

                logger.info("Downloading image from: {}", url);

                if (!Files.exists(downloadPath)) {
                    Files.createDirectories(downloadPath);
                }

                HttpClient client = HttpClient.newHttpClient();
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(new URI(url))
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
                        )
                        .GET()
                        .build();

                try (InputStream stream =
                             client.send(
                                 req,
                                 HttpResponse.BodyHandlers.ofInputStream()
                             ).body()) {
                    Files.copy(
                        stream,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                    );
                }

                // Beri jeda agar filesystem mengenali file baru
                Thread.sleep(100);

                return destination.toFile();

            } catch (Exception e) {
                logger.error("Failed to download image from: {}", url, e);
                return null;
            }
        });
    }

    /**
     * Mengatur wallpaper desktop Windows menggunakan Windows API.
     *
     * <p>Method ini memanggil fungsi native
     * <code>SystemParametersInfo</code> melalui JNA.</p>
     *
     * @param imageFile file wallpaper yang akan diterapkan
     * @return {@code true} jika wallpaper berhasil diatur
     */
    @Override
    public boolean setWallpaper(File imageFile) {
        logger.info(
            "Setting wallpaper to file: {}",
            imageFile != null ? imageFile.getAbsolutePath() : "null"
        );

        if (imageFile == null || !imageFile.exists()) {
            return false;
        }

        String absolutePath =
                imageFile.getAbsolutePath().replace("/", "\\");

        boolean result =
                User32.INSTANCE.SystemParametersInfo(
                    User32.SPI_SETDESKWALLPAPER,
                    0,
                    absolutePath,
                    User32.SPIF_UPDATEINIFILE |
                    User32.SPIF_SENDWININICHANGE
                );

        if (!result) {
            logger.error(
                "Failed to set wallpaper using SystemParametersInfo."
            );
        } else {
            logger.info("Wallpaper set successfully.");
        }

        return result;
    }

    /**
     * Mengambil daftar wallpaper yang telah diunduh secara lokal.
     *
     * <p>Method ini akan melakukan scanning pada direktori
     * download dan membaca metadata gambar seperti:</p>
     * <ul>
     *   <li>Nama file</li>
     *   <li>URI file</li>
     *   <li>Resolusi gambar</li>
     * </ul>
     *
     * <p>Resolusi gambar diambil menggunakan
     * {@link ImageIO} tanpa memuat seluruh gambar ke memori.</p>
     *
     * @return daftar {@link WallpaperImage} lokal
     */
    @Override
    public List<WallpaperImage> getDownloadedImages() {
        List<WallpaperImage> localImages = new ArrayList<>();

        try {
            if (Files.exists(downloadPath)) {
                try (Stream<Path> paths =
                             Files.walk(downloadPath)) {

                    paths.filter(Files::isRegularFile)
                         .filter(p ->
                             p.toString().endsWith(".jpg") ||
                             p.toString().endsWith(".png")
                         )
                         .forEach(path -> {
                             File file = path.toFile();

                             double w = 1920;
                             double h = 1080;

                             String fileName = file.getName();
                             String fileUri = file.toURI().toString();

                             try (ImageInputStream in =
                                          ImageIO.createImageInputStream(file)) {

                                 Iterator<ImageReader> readers =
                                         ImageIO.getImageReaders(in);

                                 if (readers.hasNext()) {
                                     ImageReader reader = readers.next();
                                     try {
                                         reader.setInput(in);
                                         w = reader.getWidth(0);
                                         h = reader.getHeight(0);
                                     } finally {
                                         reader.dispose();
                                     }
                                 }
                             } catch (Exception ignored) {}

                             localImages.add(
                                 new WallpaperImage(
                                     fileName,
                                     fileUri,
                                     fileUri,
                                     "Downloaded Image",
                                     "Local",
                                     w,
                                     h
                                 )
                             );
                         });
                }
            }
        } catch (Exception e) {
            logger.error(
                "Failed to scan downloaded images from {}",
                downloadPath, e
            );
        }

        return localImages;
    }
}