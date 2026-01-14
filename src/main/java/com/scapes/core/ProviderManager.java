package com.scapes.core;

import com.scapes.model.WallpaperImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manager untuk mengelola berbagai WallpaperProvider dan routing pencarian gambar.
 * 
 * <p>Kelas ini bertanggung jawab untuk:</p>
 * <ul>
 *   <li>Registrasi multiple wallpaper providers (Pexels, Unsplash, dll)</li>
 *   <li>Switching provider aktif</li>
 *   <li>Routing pencarian ke provider yang dipilih</li>
 *   <li>Menyediakan daftar provider yang tersedia</li>
 * </ul>
 * 
 * <p>Pattern yang digunakan: Strategy Pattern untuk switching provider</p>
 * 
 * @author Scapes
 * @version 1.0
 * @see WallpaperProvider
 */
public class ProviderManager {
    private static final Logger logger = LoggerFactory.getLogger(ProviderManager.class);
    
    /** Map untuk menyimpan semua provider yang terdaftar */
    private final Map<String, WallpaperProvider> providers = new HashMap<>();
    
    /** Provider yang sedang aktif saat ini */
    private WallpaperProvider currentProvider;

    /**
     * Mendaftarkan provider baru ke dalam manager.
     * 
     * <p>Provider pertama yang didaftarkan akan otomatis menjadi provider aktif.</p>
     * 
     * @param provider Instance WallpaperProvider yang akan didaftarkan
     */
    public void registerProvider(WallpaperProvider provider) {
        logger.info("Registering provider: " + provider.getProviderName());
        providers.put(provider.getProviderName(), provider);
        
        // Set sebagai current provider jika belum ada
        if (currentProvider == null) {
            currentProvider = provider;
        }
    }

    /**
     * Mengatur provider aktif berdasarkan nama.
     * 
     * <p>Jika nama provider tidak ditemukan, provider aktif tidak akan berubah.</p>
     * 
     * @param name Nama provider yang akan diaktifkan (contoh: "Pexels", "Unsplash")
     */
    public void setActiveProvider(String name) {
        if (providers.containsKey(name)) {
            this.currentProvider = providers.get(name);
            logger.info("Active provider set to: " + name);
        }
    }

    /**
     * Melakukan pencarian gambar menggunakan provider yang sedang aktif.
     * 
     * <p>Method ini akan mengembalikan CompletableFuture yang failed jika
     * tidak ada provider yang terdaftar.</p>
     * 
     * @param query Kata kunci pencarian
     * @param page Nomor halaman (untuk paginasi)
     * @param minWidth Lebar minimum gambar
     * @param minHeight Tinggi minimum gambar
     * @return CompletableFuture berisi List hasil pencarian
     * @throws RuntimeException jika tidak ada provider yang terdaftar
     */
    public CompletableFuture<List<WallpaperImage>> search(String query, int page, 
                                                          double minWidth, double minHeight) {
        if (currentProvider == null) {
            logger.error("No active provider set!");
            return CompletableFuture.failedFuture(
                new RuntimeException("No provider registered!")
            );
        }
        return currentProvider.searchImages(query, page, minWidth, minHeight);
    }
    
    /**
     * Mendapatkan daftar nama semua provider yang tersedia.
     * 
     * @return List immutable berisi nama-nama provider
     */
    public List<String> getAvailableProviders() {
        return List.copyOf(providers.keySet());
    }
}