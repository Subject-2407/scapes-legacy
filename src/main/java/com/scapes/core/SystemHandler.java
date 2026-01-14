package com.scapes.core;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.scapes.model.WallpaperImage;

/**
 * Interface untuk handling operasi sistem operasi terkait wallpaper.
 * 
 * <p>Interface ini mendefinisikan kontrak untuk operasi-operasi sistem seperti:</p>
 * <ul>
 *   <li>Download gambar dari internet</li>
 *   <li>Set wallpaper di OS</li>
 *   <li>Mengambil daftar gambar yang sudah didownload</li>
 * </ul>
 * 
 * <p>Setiap OS (Windows, Linux, Mac) harus mengimplementasikan interface ini
 * dengan cara yang sesuai untuk sistem operasi tersebut.</p>
 * 
 * @author Scapes
 * @version 1.0
 * @see WindowsSystemHandler
 */
public interface SystemHandler {
    /**
     * Download gambar dari URL ke local storage secara asynchronous.
     * 
     * @param url URL gambar yang akan didownload
     * @param filename Nama file untuk menyimpan gambar
     * @return CompletableFuture berisi File hasil download, atau null jika gagal
     */
    CompletableFuture<File> downloadImage(String url, String filename);
    
    /**
     * Set gambar sebagai wallpaper desktop.
     * 
     * <p>Implementasi method ini bergantung pada sistem operasi yang digunakan.</p>
     * 
     * @param imageFile File gambar yang akan dijadikan wallpaper
     * @return true jika berhasil, false jika gagal
     */
    boolean setWallpaper(File imageFile);

    /**
     * Mendapatkan daftar semua gambar yang sudah didownload ke local storage.
     * 
     * @return List berisi WallpaperImage dari koleksi lokal
     */
    List<WallpaperImage> getDownloadedImages();
}