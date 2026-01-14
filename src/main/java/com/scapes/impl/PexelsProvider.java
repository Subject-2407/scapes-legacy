package com.scapes.impl;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scapes.core.WallpaperProvider;
import com.scapes.model.WallpaperImage;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementasi WallpaperProvider untuk layanan Pexels.
 * 
 * <p>Provider ini menggunakan Pexels API untuk mencari dan mengambil gambar wallpaper
 * berkualitas tinggi. Semua request dilakukan secara asynchronous menggunakan
 * CompletableFuture untuk performa yang lebih baik.</p>
 * 
 * <p>Fitur utama:</p>
 * <ul>
 *   <li>Pencarian gambar dengan keyword</li>
 *   <li>Filter berdasarkan dimensi minimum</li>
 *   <li>Orientasi landscape untuk wallpaper</li>
 *   <li>Paginasi untuk hasil banyak</li>
 * </ul>
 * 
 * @author Scapes
 * @version 1.0
 * @see WallpaperProvider
 * @see WallpaperImage
 */
public class PexelsProvider implements WallpaperProvider {
    private static final Logger logger = LoggerFactory.getLogger(PexelsProvider.class);
    
    /** API Key untuk akses Pexels API */
    private static final String API_KEY = "GmgK9CKW7xIJelmpDPSBaNfYunq0cjFRGfuYBq70ba2uVYFtAe7gv33x";
    
    /** HTTP Client untuk melakukan request ke API */
    private final HttpClient client = HttpClient.newHttpClient();
    
    /** Gson parser untuk parsing JSON response */
    private final Gson gson = new Gson();

    /**
     * Mendapatkan nama provider.
     * @return String "Pexels"
     */
    @Override
    public String getProviderName() { 
        return "Pexels"; 
    }

    /**
     * Mencari gambar wallpaper di Pexels berdasarkan query.
     * 
     * <p>Method ini melakukan pencarian asynchronous ke Pexels API dengan
     * parameter filter untuk memastikan gambar memenuhi kriteria dimensi minimum.</p>
     * 
     * @param query Kata kunci pencarian (contoh: "nature", "sunset")
     * @param page Nomor halaman untuk paginasi (mulai dari 1)
     * @param minWidth Lebar minimum gambar dalam pixel
     * @param minHeight Tinggi minimum gambar dalam pixel
     * @return CompletableFuture berisi List gambar yang ditemukan
     */
    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, 
                                                                 double minWidth, double minHeight) {
        logger.info("Searching Pexels for query: " + query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        
        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                logger.info("Sending request to Pexels API...");
                
                // Build URL dengan parameter pencarian
                String url = "https://api.pexels.com/v1/search?query=" + encodedQuery 
                           + "&per_page=30&orientation=landscape&page=" + page;
                
                // Buat HTTP request dengan header Authorization
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", API_KEY) 
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .GET()
                        .build();
                        
                // Kirim request dan dapatkan response
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                
                // Validasi status code
                if (resp.statusCode() != 200) {
                    logger.error("Pexels API request failed with status: " + resp.statusCode());
                    return results;
                }

                // Parse JSON response
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                logger.info("Parsing Pexels API response...");
                
                if (json.has("photos")) {
                    json.getAsJsonArray("photos").forEach(elem -> {
                        JsonObject item = elem.getAsJsonObject();

                        long imgW = item.get("width").getAsLong();
                        long imgH = item.get("height").getAsLong();

                        // Skip gambar yang terlalu kecil
                        if (imgW < minWidth || imgH < minHeight) {
                            return;
                        }

                        // Ekstrak data gambar
                        String id = item.get("id").getAsString(); 
                        String desc = item.has("alt") && !item.get("alt").isJsonNull() 
                                    ? item.get("alt").getAsString() 
                                    : "Untitled";
                        
                        JsonObject src = item.getAsJsonObject("src");
                        String full = src.get("original").getAsString();
                        String thumb = src.get("medium").getAsString(); 

                        // Tambahkan ke hasil pencarian
                        results.add(new WallpaperImage(id, full, thumb, desc, "Pexels", imgW, imgH));
                    });
                }
            } catch (Exception e) { 
                logger.error("Failed to search Pexels for query: " + query, e);
            }
            return results;
        });
    }
}