package com.scapes.util;

import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * Utility helper untuk menambahkan fitur resize pada custom window JavaFX.
 *
 * <p>Kelas ini digunakan ketika aplikasi memakai
 * <b>undecorated / transparent stage</b>,
 * sehingga border bawaan OS untuk resize tidak tersedia.</p>
 *
 * <p>Resize dilakukan dengan mendeteksi posisi mouse
 * di area tepi (border) window dan menyesuaikan
 * ukuran {@link Stage} secara manual.</p>
 *
 * <p>Helper ini mendukung:</p>
 * <ul>
 *   <li>Resize dari semua sisi (atas, bawah, kiri, kanan)</li>
 *   <li>Resize dari sudut (corner)</li>
 *   <li>Perubahan cursor sesuai arah resize</li>
 * </ul>
 *
 * <p>Biasanya dipanggil sekali setelah {@link Scene}
 * diset ke {@link Stage}.</p>
 *
 * <pre>
 * ResizeHelper.addResizeListener(stage);
 * </pre>
 *
 * @author Scapes
 * @version 1.0
 * @since 2024
 */
public class ResizeHelper {

    /**
     * Menambahkan event listener resize ke {@link Stage}.
     *
     * <p>Method ini akan mendaftarkan satu listener
     * yang menangani seluruh event mouse terkait resize:</p>
     * <ul>
     *   <li>{@link MouseEvent#MOUSE_MOVED}</li>
     *   <li>{@link MouseEvent#MOUSE_PRESSED}</li>
     *   <li>{@link MouseEvent#MOUSE_DRAGGED}</li>
     *   <li>{@link MouseEvent#MOUSE_EXITED}</li>
     *   <li>{@link MouseEvent#MOUSE_EXITED_TARGET}</li>
     * </ul>
     *
     * @param stage stage JavaFX yang akan diberi kemampuan resize
     */
    public static void addResizeListener(Stage stage) {
        ResizeListener listener = new ResizeListener(stage);

        stage.getScene().addEventHandler(MouseEvent.MOUSE_MOVED, listener);
        stage.getScene().addEventHandler(MouseEvent.MOUSE_PRESSED, listener);
        stage.getScene().addEventHandler(MouseEvent.MOUSE_DRAGGED, listener);
        stage.getScene().addEventHandler(MouseEvent.MOUSE_EXITED, listener);
        stage.getScene().addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, listener);
    }

    /**
     * Event listener internal yang menangani logika resize window.
     *
     * <p>Kelas ini mendeteksi arah resize berdasarkan
     * posisi mouse terhadap border window,
     * lalu menyesuaikan ukuran dan posisi {@link Stage}.</p>
     *
     * <p>Implementasi ini mengikuti perilaku resize
     * standar Windows (drag dari sisi atau sudut).</p>
     */
    static class ResizeListener implements EventHandler<MouseEvent> {

        /** Stage yang akan di-resize */
        private final Stage stage;

        /** Cursor aktif sesuai arah resize */
        private Cursor cursorEvent = Cursor.DEFAULT;

        /**
         * Lebar area sensitif di tepi window (dalam pixel).
         *
         * <p>Semakin besar nilai ini, semakin mudah
         * pengguna melakukan resize.</p>
         */
        private final int border = 12;

        /** Posisi awal mouse relatif terhadap ukuran stage */
        private double startX = 0;
        private double startY = 0;

        /**
         * Konstruktor ResizeListener.
         *
         * @param stage stage yang akan diberi fitur resize
         */
        public ResizeListener(Stage stage) {
            this.stage = stage;
        }

        /**
         * Menangani event mouse untuk mendeteksi
         * dan melakukan resize window.
         *
         * @param mouseEvent event mouse yang terjadi
         */
        @Override
        public void handle(MouseEvent mouseEvent) {
            EventType<? extends MouseEvent> mouseEventType =
                    mouseEvent.getEventType();

            Scene scene = stage.getScene();

            double mouseEventX = mouseEvent.getSceneX();
            double mouseEventY = mouseEvent.getSceneY();
            double sceneWidth = scene.getWidth();
            double sceneHeight = scene.getHeight();

            // Deteksi arah resize dan ubah cursor
            if (MouseEvent.MOUSE_MOVED.equals(mouseEventType)) {

                if (mouseEventX < border && mouseEventY < border)
                    cursorEvent = Cursor.NW_RESIZE;
                else if (mouseEventX < border && mouseEventY > sceneHeight - border)
                    cursorEvent = Cursor.SW_RESIZE;
                else if (mouseEventX > sceneWidth - border && mouseEventY < border)
                    cursorEvent = Cursor.NE_RESIZE;
                else if (mouseEventX > sceneWidth - border && mouseEventY > sceneHeight - border)
                    cursorEvent = Cursor.SE_RESIZE;
                else if (mouseEventX < border)
                    cursorEvent = Cursor.W_RESIZE;
                else if (mouseEventX > sceneWidth - border)
                    cursorEvent = Cursor.E_RESIZE;
                else if (mouseEventY < border)
                    cursorEvent = Cursor.N_RESIZE;
                else if (mouseEventY > sceneHeight - border)
                    cursorEvent = Cursor.S_RESIZE;
                else
                    cursorEvent = Cursor.DEFAULT;

                scene.setCursor(cursorEvent);

            }
            // Reset cursor saat mouse keluar area scene
            else if (MouseEvent.MOUSE_EXITED.equals(mouseEventType)
                  || MouseEvent.MOUSE_EXITED_TARGET.equals(mouseEventType)) {

                scene.setCursor(Cursor.DEFAULT);

            }
            // Simpan posisi awal saat drag dimulai
            else if (MouseEvent.MOUSE_PRESSED.equals(mouseEventType)) {

                startX = stage.getWidth() - mouseEventX;
                startY = stage.getHeight() - mouseEventY;

            }
            // Proses resize saat mouse di-drag
            else if (MouseEvent.MOUSE_DRAGGED.equals(mouseEventType)) {

                if (!Cursor.DEFAULT.equals(cursorEvent)) {

                    // Resize vertikal
                    if (!Cursor.W_RESIZE.equals(cursorEvent)
                     && !Cursor.E_RESIZE.equals(cursorEvent)) {

                        double minHeight =
                                stage.getMinHeight() > (border * 2)
                                ? stage.getMinHeight()
                                : (border * 2);

                        if (Cursor.NW_RESIZE.equals(cursorEvent)
                         || Cursor.N_RESIZE.equals(cursorEvent)
                         || Cursor.NE_RESIZE.equals(cursorEvent)) {

                            if (stage.getHeight() > minHeight || mouseEventY < 0) {
                                stage.setHeight(
                                        stage.getY()
                                        - mouseEvent.getScreenY()
                                        + stage.getHeight()
                                );
                                stage.setY(mouseEvent.getScreenY());
                            }

                        } else {
                            if (stage.getHeight() > minHeight
                             || mouseEventY + startY - stage.getHeight() > 0) {

                                stage.setHeight(mouseEventY + startY);
                            }
                        }
                    }

                    // Resize horizontal
                    if (!Cursor.N_RESIZE.equals(cursorEvent)
                     && !Cursor.S_RESIZE.equals(cursorEvent)) {

                        double minWidth =
                                stage.getMinWidth() > (border * 2)
                                ? stage.getMinWidth()
                                : (border * 2);

                        if (Cursor.NW_RESIZE.equals(cursorEvent)
                         || Cursor.W_RESIZE.equals(cursorEvent)
                         || Cursor.SW_RESIZE.equals(cursorEvent)) {

                            if (stage.getWidth() > minWidth || mouseEventX < 0) {
                                stage.setWidth(
                                        stage.getX()
                                        - mouseEvent.getScreenX()
                                        + stage.getWidth()
                                );
                                stage.setX(mouseEvent.getScreenX());
                            }

                        } else {
                            if (stage.getWidth() > minWidth
                             || mouseEventX + startX - stage.getWidth() > 0) {

                                stage.setWidth(mouseEventX + startX);
                            }
                        }
                    }
                }
            }
        }
    }
}