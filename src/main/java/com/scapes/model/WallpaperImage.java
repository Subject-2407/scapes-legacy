package com.scapes.model;

/**
 * Model data untuk merepresentasikan sebuah gambar wallpaper.
 * 
 * <p>Kelas ini menyimpan informasi lengkap tentang gambar wallpaper termasuk
 * URL gambar, thumbnail, deskripsi, dan dimensi. Semua field bersifat immutable
 * untuk menjaga konsistensi data.</p>
 * 
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class WallpaperImage {
    /** ID unik untuk gambar wallpaper */
    private final String id;
    
    /** URL lengkap ke gambar resolusi penuh */
    private final String imageUrl;
    
    /** URL ke thumbnail gambar (preview kecil) */
    private final String thumbnailUrl;
    
    /** Deskripsi atau alt text untuk gambar */
    private final String description;
    
    /** Nama sumber/provider gambar (contoh: "Pexels", "Unsplash") */
    private final String sourceName;
    
    /** Lebar gambar dalam pixel */
    private final double width;
    
    /** Tinggi gambar dalam pixel */
    private final double height;

    /**
     * Konstruktor untuk membuat instance WallpaperImage.
     * 
     * @param id ID unik gambar dari provider
     * @param imageUrl URL gambar resolusi penuh
     * @param thumbnailUrl URL thumbnail gambar
     * @param description Deskripsi gambar (akan diganti "No Description" jika null)
     * @param sourceName Nama sumber/provider
     * @param width Lebar gambar dalam pixel
     * @param height Tinggi gambar dalam pixel
     */
    public WallpaperImage(String id, String imageUrl, String thumbnailUrl, 
                         String description, String sourceName, double width, double height) {
        this.id = id;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.description = description != null ? description : "No Description";
        this.sourceName = sourceName;
        this.width = width;
        this.height = height;
    }

    /**
     * Mendapatkan ID unik gambar.
     * @return ID gambar
     */
    public String getId() { return id; }
    
    /**
     * Mendapatkan URL gambar resolusi penuh.
     * @return URL gambar lengkap
     */
    public String getImageUrl() { return imageUrl; }
    
    /**
     * Mendapatkan URL thumbnail gambar.
     * @return URL thumbnail
     */
    public String getThumbnailUrl() { return thumbnailUrl; }
    
    /**
     * Mendapatkan deskripsi gambar.
     * @return Deskripsi gambar atau "No Description" jika tidak ada
     */
    public String getDescription() { return description; }
    
    /**
     * Mendapatkan nama sumber/provider gambar.
     * @return Nama provider (contoh: "Pexels", "Unsplash")
     */
    public String getSourceName() { return sourceName; }
    
    /**
     * Mendapatkan lebar gambar dalam pixel.
     * @return Lebar gambar
     */
    public double getWidth() { return width; }
    
    /**
     * Mendapatkan tinggi gambar dalam pixel.
     * @return Tinggi gambar
     */
    public double getHeight() { return height; }
}