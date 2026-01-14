package com.scapes.core;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.scapes.model.WallpaperImage;

/**
 * Interface untuk provider sumber wallpaper online.
 * 
 * <p>Setiap layanan wallpaper (Pexels, Unsplash, dll) harus mengimplementasikan
 * interface ini untuk dapat diintegrasikan dengan aplikasi.</p>
 * 
 * <p>Interface ini menggunakan pattern Strategy untuk memungkinkan penambahan
 * provider baru tanpa mengubah kode core aplikasi.</p>
 * 
 * @author Scapes
 * @version 1.0
 * @see PexelsProvider
 * @see UnsplashProvider
 */
public interface WallpaperProvider {
    /**
     * Mendapatkan nama provider.
     * 
     * <p>Nama ini akan ditampilkan di UI untuk user memilih sumber gambar.</p>
     * 
     * @return Nama provider (contoh: "Pexels", "Unsplash")
     */
    String getProviderName();
    
    /**
     * Mencari gambar wallpaper berdasarkan query dan filter.
     * 
     * <p>Method ini harus diimplementasikan secara asynchronous menggunakan
     * CompletableFuture agar tidak memblokir UI thread.</p>
     * 
     * @param query Kata kunci pencarian
     * @param page Nomor halaman untuk paginasi (mulai dari 1)
     * @param minWidth Lebar minimum gambar dalam pixel
     * @param minHeight Tinggi minimum gambar dalam pixel
     * @return CompletableFuture berisi List gambar yang ditemukan
     */
    CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, 
                                                         double minWidth, double minHeight);
}