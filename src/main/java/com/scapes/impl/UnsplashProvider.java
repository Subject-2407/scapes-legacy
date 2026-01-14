package com.scapes.impl;

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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scapes.core.WallpaperProvider;
import com.scapes.model.WallpaperImage;

/**
 * Implementasi WallpaperProvider untuk layanan Unsplash.
 * 
 * <p>Provider ini mengintegrasikan aplikasi dengan Unsplash API untuk mengakses
 * koleksi foto berkualitas tinggi dari fotografer profesional di seluruh dunia.</p>
 * 
 * <p>Fitur utama:</p>
 * <ul>
 *   <li>Pencarian gambar dengan berbagai kategori</li>
 *   <li>Filter dimensi minimum untuk kualitas wallpaper</li>
 *   <li>Orientasi landscape otomatis</li>
 *   <li>Paginasi hasil pencarian</li>
 * </ul>
 * 
 * @author Scapes
 * @version 1.0
 * @see WallpaperProvider
 * @see WallpaperImage
 */
public class UnsplashProvider implements WallpaperProvider {
    private static final Logger logger = LoggerFactory.getLogger(UnsplashProvider.class);
    
    /** API Key untuk akses Unsplash API */
    private static final String API_KEY = "8pMXKEp0lhIA8HZhywGmJe2ogcma9J4VTFmq7vTrc2k";
    
    /** HTTP Client untuk melakukan request ke API */
    private final HttpClient client = HttpClient.newHttpClient();
    
    /** Gson parser untuk parsing JSON response */
    private final Gson gson = new Gson();

    /**
     * Mendapatkan nama provider.
     * @return String "Unsplash"
     */
    @Override
    public String getProviderName() { 
        return "Unsplash"; 
    }

    /**
     * Mencari gambar wallpaper di Unsplash berdasarkan query.
     * 
     * <p>Method ini melakukan pencarian asynchronous ke Unsplash API.
     * Hasil pencarian difilter berdasarkan dimensi minimum untuk memastikan
     * kualitas gambar sesuai untuk wallpaper desktop.</p>
     * 
     * @param query Kata kunci pencarian (contoh: "mountains", "ocean")
     * @param page Nomor halaman untuk paginasi (mulai dari 1)
     * @param minWidth Lebar minimum gambar dalam pixel
     * @param minHeight Tinggi minimum gambar dalam pixel
     * @return CompletableFuture berisi List gambar yang ditemukan
     */
    @Override
    public CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, 
                                                                 double minWidth, double minHeight) {
        logger.info("Searching Unsplash for query: " + query);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

        return CompletableFuture.supplyAsync(() -> {
            List<WallpaperImage> results = new ArrayList<>();
            try {
                logger.info("Sending request to Unsplash API...");
                
                // Build URL dengan parameter pencarian
                String url = "https://api.unsplash.com/search/photos?query=" + encodedQuery 
                           + "&client_id=" + API_KEY 
                           + "&per_page=30&orientation=landscape&page=" + page;
                
                // Buat dan kirim HTTP request
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                var resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                // Validasi response
                if (resp.statusCode() != 200) {
                    logger.error("Unsplash API request failed with status: " + resp.statusCode());
                    return results;
                }
                
                // Parse JSON response
                JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
                logger.info("Parsing Unsplash API response...");
                
                json.getAsJsonArray("results").forEach(elem -> {
                    JsonObject item = elem.getAsJsonObject();

                    long imgW = item.get("width").getAsLong();
                    long imgH = item.get("height").getAsLong();

                    // Skip gambar yang tidak memenuhi dimensi minimum
                    if (imgW < minWidth || imgH < minHeight) {
                        return; 
                    }

                    // Ekstrak data gambar
                    String id = item.get("id").getAsString();
                    String desc = item.has("description") && !item.get("description").isJsonNull() 
                                ? item.get("description").getAsString() 
                                : "Untitled";
                    
                    JsonObject urls = item.getAsJsonObject("urls");
                    String full = urls.get("regular").getAsString();
                    String thumb = urls.get("small").getAsString();

                    // Tambahkan ke hasil pencarian
                    results.add(new WallpaperImage(id, full, thumb, desc, "Unsplash", imgW, imgH));
                });
            } catch (Exception e) {
                logger.error("Failed to search Unsplash for query: " + query, e); 
            }
            return results;
        });
    }
}