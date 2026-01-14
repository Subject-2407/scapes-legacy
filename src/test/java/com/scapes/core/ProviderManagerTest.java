package com.scapes.core;

import com.scapes.model.WallpaperImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kelas pengujian untuk {@link ProviderManager}.
 * <p>Bertujuan untuk memverifikasi fungsionalitas inti dalam mengelola berbagai sumber wallpaper,
 * termasuk proses registrasi, seleksi provider aktif, dan pengiriman parameter pencarian 
 * yang tepat ke sub-sistem provider.</p>
 */
class ProviderManagerTest {
    
    private ProviderManager providerManager;
    private MockWallpaperProvider mockProvider1;
    private MockWallpaperProvider mockProvider2;

    /**
     * Menyiapkan lingkungan pengujian sebelum setiap metode test dijalankan.
     * <p>Menginisialisasi instance {@code ProviderManager} baru dan menyiapkan objek 
     * mock provider untuk mensimulasikan layanan seperti Pexels dan Unsplash.</p>
     */
    @BeforeEach
    void setUp() {
        providerManager = new ProviderManager();
        mockProvider1 = new MockWallpaperProvider("Pexels");
        mockProvider2 = new MockWallpaperProvider("Unsplash");
    }

    /**
     * Menguji kemampuan sistem untuk mendaftarkan satu provider tunggal.
     * Memastikan nama provider tersimpan dengan benar dalam daftar yang tersedia.
     */
    @Test
    @DisplayName("Harus berhasil mendaftarkan provider")
    void testDaftarkanProvider() {
        providerManager.registerProvider(mockProvider1);
        
        List<String> providers = providerManager.getAvailableProviders();
        assertEquals(1, providers.size());
        assertTrue(providers.contains("Pexels"));
    }

    /**
     * Menguji pendaftaran beberapa provider sekaligus.
     * Memastikan semua provider yang didaftarkan masuk ke dalam koleksi sistem tanpa ada yang hilang.
     */
    @Test
    @DisplayName("Harus berhasil mendaftarkan beberapa provider")
    void testDaftarkanBeberapaProvider() {
        providerManager.registerProvider(mockProvider1);
        providerManager.registerProvider(mockProvider2);
        
        List<String> providers = providerManager.getAvailableProviders();
        assertEquals(2, providers.size());
        assertTrue(providers.contains("Pexels"));
        assertTrue(providers.contains("Unsplash"));
    }

    /**
     * Memverifikasi perilaku default sistem saat pendaftaran pertama kali.
     * Provider yang pertama kali didaftarkan harus menjadi provider aktif otomatis.
     */
    @Test
    @DisplayName("Provider pertama yang didaftarkan harus aktif secara default")
    void testProviderPertamaAktif() throws ExecutionException, InterruptedException {
        providerManager.registerProvider(mockProvider1);
        providerManager.registerProvider(mockProvider2);
        
        // Lakukan pencarian dan verifikasi provider mana yang dipanggil
        CompletableFuture<List<WallpaperImage>> result = providerManager.search("nature", 1, 1920, 1080);
        List<WallpaperImage> images = result.get();
        
        // Provider pertama (mockProvider1) harus yang dipanggil
        assertTrue(mockProvider1.apakahSearchDipanggil());
        assertFalse(mockProvider2.apakahSearchDipanggil());
    }

    /**
     * Menguji fitur pergantian provider aktif secara eksplisit.
     * Memastikan bahwa saat provider diganti, permintaan pencarian diarahkan ke provider baru tersebut.
     */
    @Test
    @DisplayName("Harus bisa mengganti provider aktif")
    void testGantiProviderAktif() throws ExecutionException, InterruptedException {
        providerManager.registerProvider(mockProvider1);
        providerManager.registerProvider(mockProvider2);
        
        // Ganti provider aktif ke Unsplash
        providerManager.setActiveProvider("Unsplash");
        
        CompletableFuture<List<WallpaperImage>> result = providerManager.search("mountains", 1, 1920, 1080);
        result.get();
        
        // Sekarang mockProvider2 (Unsplash) yang harus dipanggil
        assertFalse(mockProvider1.apakahSearchDipanggil());
        assertTrue(mockProvider2.apakahSearchDipanggil());
    }

    /**
     * Menguji ketahanan sistem terhadap input provider yang tidak valid.
     * Jika pengguna memilih nama provider yang tidak terdaftar, sistem harus tetap menggunakan
     * provider yang saat ini aktif (fallback).
     */
    @Test
    @DisplayName("Harus menangani provider yang tidak ada dengan baik")
    void testGantiProviderTidakAda() throws ExecutionException, InterruptedException {
        providerManager.registerProvider(mockProvider1);
        
        // Coba set provider yang tidak terdaftar
        providerManager.setActiveProvider("ProviderTidakAda");
        
        // Seharusnya tetap menggunakan provider pertama (mockProvider1)
        CompletableFuture<List<WallpaperImage>> result = providerManager.search("test", 1, 1920, 1080);
        result.get();
        
        assertTrue(mockProvider1.apakahSearchDipanggil());
    }

    /**
     * Menguji kasus batas di mana tidak ada provider yang terdaftar sama sekali.
     * Sistem harus melemparkan exception karena tidak ada mesin pencari yang bisa menangani permintaan.
     */
    @Test
    @DisplayName("Harus gagal ketika tidak ada provider yang terdaftar")
    void testPencarianTanpaProvider() {
        // Coba search tanpa mendaftarkan provider apapun
        CompletableFuture<List<WallpaperImage>> result = providerManager.search("test", 1, 1920, 1080);
        
        // Harus throw exception
        assertThrows(ExecutionException.class, result::get);
    }

    /**
     * Memverifikasi akurasi transmisi data dari Manager ke Provider.
     * Menguji apakah parameter seperti query, halaman, dan dimensi gambar diteruskan tanpa modifikasi yang salah.
     */
    @Test
    @DisplayName("Harus melewatkan parameter yang benar ke provider")
    void testParameterPencarian() throws ExecutionException, InterruptedException {
        providerManager.registerProvider(mockProvider1);
        
        // Parameter pencarian
        String query = "sunset";
        int halaman = 2;
        double lebar = 2560;
        double tinggi = 1440;
        
        providerManager.search(query, halaman, lebar, tinggi).get();
        
        // Verifikasi bahwa parameter yang benar diteruskan ke provider
        assertEquals(query, mockProvider1.getQueryTerakhir());
        assertEquals(halaman, mockProvider1.getHalamanTerakhir());
        assertEquals(lebar, mockProvider1.getLebarMinTerakhir());
        assertEquals(tinggi, mockProvider1.getTinggiMinTerakhir());
    }

    /**
     * Memastikan method pengambilan daftar provider tidak mengembalikan null 
     * meskipun sistem dalam keadaan kosong.
     */
    @Test
    @DisplayName("Harus mengembalikan list kosong dari getAvailableProviders ketika tidak ada yang terdaftar")
    void testGetProviderTersediaKosong() {
        List<String> providers = providerManager.getAvailableProviders();
        assertNotNull(providers);
        assertTrue(providers.isEmpty());
    }

    /**
     * Implementasi tiruan (Mock) dari {@link WallpaperProvider} untuk keperluan isolasi pengujian.
     * <p>Kelas ini bertindak sebagai mata-mata (spy) yang mencatat semua interaksi dari 
     * {@code ProviderManager} untuk divalidasi oleh assertion JUnit.</p>
     */
    private static class MockWallpaperProvider implements WallpaperProvider {
        private final String nama;
        private boolean searchDipanggil = false;
        private String queryTerakhir;
        private int halamanTerakhir;
        private double lebarMinTerakhir;
        private double tinggiMinTerakhir;

        public MockWallpaperProvider(String nama) {
            this.nama = nama;
        }

        @Override
        public String getProviderName() {
            return nama;
        }

        /**
         * Mensimulasikan proses pencarian gambar secara asinkron.
         * Mencatat status pemanggilan dan parameter yang diterima untuk keperluan verifikasi pengujian.
         */
        @Override
        public CompletableFuture<List<WallpaperImage>> searchImages(String query, int page, 
                                                                     double minWidth, double minHeight) {
            this.searchDipanggil = true;
            this.queryTerakhir = query;
            this.halamanTerakhir = page;
            this.lebarMinTerakhir = minWidth;
            this.tinggiMinTerakhir = minHeight;

            List<WallpaperImage> gambarMock = new ArrayList<>();
            gambarMock.add(buatGambarMock(query + "_1"));
            gambarMock.add(buatGambarMock(query + "_2"));
            
            return CompletableFuture.completedFuture(gambarMock);
        }

        /**
         * Helper internal untuk membungkus data dummy ke dalam objek {@link WallpaperImage}.
         * * @param id Identitas unik simulasi untuk gambar.
         * @return Objek gambar tiruan dengan data statis.
         */
        private WallpaperImage buatGambarMock(String id) {
            return new WallpaperImage(
                id,
                "https://example.com/" + id + ".jpg",
                "https://example.com/thumb_" + id + ".jpg",
                "Deskripsi gambar mock untuk " + id,
                nama,
                1920,
                1080
            );
        }

        // --- Metode Getter untuk keperluan Assertion (Verifikasi Hasil Test) ---

        public boolean apakahSearchDipanggil() {
            return searchDipanggil;
        }

        public String getQueryTerakhir() {
            return queryTerakhir;
        }

        public int getHalamanTerakhir() {
            return halamanTerakhir;
        }

        public double getLebarMinTerakhir() {
            return lebarMinTerakhir;
        }

        public double getTinggiMinTerakhir() {
            return tinggiMinTerakhir;
        }
    }
}