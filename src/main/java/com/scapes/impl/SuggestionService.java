package com.scapes.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.lang.reflect.Type;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service untuk menangani fitur suggestion, prediction, dan recommendation
 * pada aplikasi Scapes.
 *
 * <p>SuggestionService berfungsi sebagai penghubung antara aplikasi JavaFX
 * dengan backend API yang menyediakan:</p>
 * <ul>
 *   <li>Logging kata kunci pencarian pengguna</li>
 *   <li>Prediksi kata kunci berdasarkan input parsial (autocomplete)</li>
 *   <li>Rekomendasi wallpaper berdasarkan keyword</li>
 *   <li>Daftar keyword yang sedang trending</li>
 * </ul>
 *
 * <p>Seluruh komunikasi jaringan dilakukan secara asynchronous menggunakan
 * {@link CompletableFuture} agar tidak mengganggu thread UI JavaFX.</p>
 *
 * <p>Identifikasi pengguna dilakukan menggunakan <b>device ID</b> yang
 * diambil dari Public IP address. Jika gagal, sistem akan menggunakan
 * fallback berupa username sistem.</p>
 *
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class SuggestionService {

    private static final Logger logger = LoggerFactory.getLogger(SuggestionService.class);

    /** Endpoint API untuk mencatat log pencarian pengguna */
    private static final String LOG_API_URL =
            "https://lte-lyzer.atmadja.id/app/api/scapes-logger/log_search.php";

    /** Endpoint API untuk mengambil keyword yang sedang trending */
    private static final String TRENDING_API_URL =
            "https://lte-lyzer.atmadja.id/app/api/scapes-logger/get_trending.php";

    /** Endpoint API untuk prediksi keyword (autocomplete) */
    private static final String PREDICT_API_URL =
            "http://103.147.46.234:8000/predict";

    /** Endpoint API untuk rekomendasi keyword */
    private static final String RECOMMEND_API_URL =
            "http://103.147.46.234:8000/recommend";

    /** HTTP client untuk komunikasi dengan REST API */
    private final HttpClient client;

    /** Gson instance untuk serialisasi dan deserialisasi JSON */
    private final Gson gson;

    /**
     * Device ID pengguna.
     *
     * <p>Secara default diisi dengan username sistem, lalu
     * diperbarui secara asynchronous menggunakan Public IP.</p>
     */
    private String deviceId;

    /**
     * Konstruktor SuggestionService.
     *
     * <p>Menginisialisasi {@link HttpClient}, {@link Gson},
     * serta memulai proses pengambilan Public IP sebagai
     * identitas perangkat.</p>
     */
    public SuggestionService() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.gson = new Gson();

        this.deviceId = System.getProperty("user.name", "unknown_user");
        fetchPublicIp();
    }

    /**
     * Mengambil Public IP pengguna secara asynchronous.
     *
     * <p>Public IP digunakan sebagai device identifier
     * untuk kebutuhan logging dan personalisasi suggestion.</p>
     *
     * <p>Jika proses gagal, sistem akan menggunakan nilai
     * fallback dari username sistem.</p>
     */
    private void fetchPublicIp() {
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.ipify.org"))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    this.deviceId = response.body();
                    logger.info("Device ID set to Public IP: {}", this.deviceId);
                }
            } catch (Exception e) {
                logger.error(
                        "Failed to fetch Public IP, using fallback instead for Device ID: {}",
                        this.deviceId, e
                );
            }
        });
    }

    /**
     * Mencatat query pencarian pengguna ke server.
     *
     * <p>Data yang dikirim mencakup:</p>
     * <ul>
     *   <li>Device ID</li>
     *   <li>Query pencarian</li>
     * </ul>
     *
     * <p>Proses ini berjalan secara asynchronous dan tidak
     * mengembalikan hasil ke pemanggil.</p>
     *
     * @param query kata kunci pencarian yang dimasukkan pengguna
     */
    public void logSearch(String query) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = new JsonObject();
                json.addProperty("device_id", deviceId);
                json.addProperty("query", query);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(LOG_API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(json)))
                        .build();

                client.send(request, HttpResponse.BodyHandlers.ofString());
                logger.info("Query log sent: {}", query);

            } catch (Exception e) {
                logger.error("Failed to log user query", e);
            }
        });
    }

    /**
     * Mengambil prediksi keyword berdasarkan input parsial.
     *
     * <p>Biasanya digunakan untuk fitur autocomplete pada search bar.</p>
     *
     * @param partialWord kata parsial yang sedang diketik pengguna
     * @return {@link CompletableFuture} berisi daftar keyword prediksi
     */
    public CompletableFuture<List<String>> getPredictions(String partialWord) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            try {
                String url = PREDICT_API_URL +
                        "?query=" + partialWord.replace(" ", "%20");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                    results = gson.fromJson(response.body(), listType);
                }
            } catch (Exception e) {
                logger.error("Keyword Prediction Service error", e);
            }
            return results;
        });
    }

    /**
     * Mengambil rekomendasi keyword berdasarkan keyword tertentu.
     *
     * <p>Digunakan untuk memberikan saran pencarian lanjutan
     * yang relevan dengan minat pengguna.</p>
     *
     * @param keyword keyword utama yang digunakan sebagai acuan rekomendasi
     * @return {@link CompletableFuture} berisi daftar keyword rekomendasi
     */
    public CompletableFuture<List<String>> getRecommendations(String keyword) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            try {
                String url = RECOMMEND_API_URL +
                        "?keyword=" + keyword.replace(" ", "%20");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonElement element =
                            gson.fromJson(response.body(), JsonElement.class);

                    if (element.isJsonObject()) {
                        JsonObject json = element.getAsJsonObject();
                        if (json.has("recommendations")) {
                            json.getAsJsonArray("recommendations")
                                    .forEach(e -> results.add(e.getAsString()));
                        }
                    } else if (element.isJsonArray()) {
                        element.getAsJsonArray()
                                .forEach(e -> results.add(e.getAsString()));
                    }
                }
            } catch (Exception e) {
                logger.error("Suggestion Service error", e);
            }
            return results;
        });
    }

    /**
     * Mengambil daftar keyword yang sedang trending.
     *
     * <p>Keyword trending biasanya ditampilkan sebagai
     * rekomendasi awal saat aplikasi dibuka.</p>
     *
     * @return {@link CompletableFuture} berisi daftar keyword trending
     */
    public CompletableFuture<List<String>> getTrendingKeywords() {
        return CompletableFuture.supplyAsync(() -> {
            List<String> trending = new ArrayList<>();
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(TRENDING_API_URL))
                        .GET()
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                    trending = gson.fromJson(response.body(), listType);
                }
            } catch (Exception e) {
                logger.error("Trending Service offline", e);
            }
            return trending;
        });
    }
}
