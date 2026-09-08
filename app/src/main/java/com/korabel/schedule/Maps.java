package com.korabel.schedule;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Поэтажные планы корпусов.
 *
 * У корпусов А, Б и У есть официальные планы (smtu.ru/ru/page/201, файлы лежат
 * на isu.smtu.ru): один PDF на корпус, страница N — этаж N. Файл скачивается
 * один раз в приватное хранилище и дальше открывается офлайн; рисует его
 * системный {@link PdfRenderer}, так что никаких библиотек не нужно.
 *
 * Этаж угадывается по первой цифре номера аудитории, но его можно листать
 * стрелками: номер не всегда совпадает с этажом, а посмотреть соседний этаж
 * обычно и надо.
 *
 * У корпусов Г и М планов не опубликовано — показывается адрес.
 */
final class Maps {

    static final class Building {
        final String letter, address, url;
        final int floors;

        Building(String letter, String address, String url, int floors) {
            this.letter = letter;
            this.address = address;
            this.url = url;
            this.floors = floors;
        }

        boolean hasPlan() {
            return url != null;
        }
    }

    private static final Building[] TABLE = {
        new Building("А", "Лоцманская ул., 3",
            "https://isu.smtu.ru/doc_tree_file/"
                + "9340022def0eb135cfe16946c8e678a262a93545ddb0ddbfa3cfcabeec27ce4a/", 5),
        new Building("Б", "Лоцманская ул., 10-14",
            "https://isu.smtu.ru/doc_tree_file/"
                + "e556fc798afbfcab2900f2966edc78ad69a1d7660e1f00c381cdd8d7a73b4551/", 6),
        new Building("У", "Ленинский пр., 101",
            "https://isu.smtu.ru/doc_tree_file/"
                + "50c1eeb37e952c41b6737c1acccdc1c1b39cea4a604e750df94649707029de15/", 5),
        new Building("Г", "Кронверкский пр., 5", null, 0),
        new Building("М", "Псковская ул., 23", null, 0),
    };

    /** Сторона отрисованной страницы в пикселях: план — это лист А1, мельче нечитаемо. */
    private static final float MAX_SIDE = 2200f;

    private Maps() { }

    /** «167 Корпус У» → корпус У; null, если корпус не назван или незнаком. */
    static Building ofRoom(String room) {
        if (room == null) return null;
        int at = room.indexOf("Корпус");
        if (at < 0) return null;
        for (int i = at + 6; i < room.length(); i++) {
            char c = room.charAt(i);
            if (c == ' ' || c == '.') continue;
            for (Building b : TABLE) if (b.letter.charAt(0) == c) return b;
            return null;
        }
        return null;
    }

    /** Этаж по первой цифре номера: «509 Корпус Г» → 5; «каф.ИЯ …» → -1. */
    static int floorOf(String room) {
        for (int i = 0; i < room.length(); i++) {
            char c = room.charAt(i);
            if (c >= '0' && c <= '9') return c - '0';
            if (c == ' ') break;
        }
        return -1;
    }

    /** Строка для диалога занятия: «Корпус У, 5 этаж». */
    static String label(Building b, String room) {
        int floor = floorOf(room);
        return "Корпус " + b.letter + (floor > 0 ? ", " + floor + " этаж" : "");
    }

    /** Открыть план корпуса на нужном этаже. */
    static void show(final Activity a, final Building b, final String room) {
        if (!b.hasPlan()) {
            new AlertDialog.Builder(a)
                    .setTitle("Корпус " + b.letter)
                    .setMessage("Адрес: " + b.address
                            + "\n\nПоэтажные планы этого корпуса университет не публикует.")
                    .setPositiveButton("Закрыть", null)
                    .show();
            return;
        }

        final File pdf = file(a, b);
        if (pdf.exists() && pdf.length() > 0) {
            open(a, b, pdf, floorOf(room));
            return;
        }

        final AlertDialog wait = new AlertDialog.Builder(a)
                .setTitle("Корпус " + b.letter)
                .setMessage("Скачиваю план корпуса — несколько мегабайт, один раз.\n"
                        + "Дальше он открывается без сети.")
                .setNegativeButton("Отмена", null)
                .create();
        wait.show();
        download(a, b, pdf, error -> {
            if (a.isFinishing()) return;
            wait.dismiss();
            if (error != null) {
                new AlertDialog.Builder(a)
                        .setTitle("План не скачался")
                        .setMessage(error + ".\n\nПланы лежат на isu.smtu.ru — под VPN с "
                                + "зарубежным выходом сайт университета обычно не отвечает.\n\n"
                                + "Адрес корпуса: " + b.address)
                        .setPositiveButton("Закрыть", null)
                        .show();
                return;
            }
            open(a, b, pdf, floorOf(room));
        });
    }

    private static File file(Context ctx, Building b) {
        return new File(ctx.getFilesDir(), "map_" + b.letter + ".pdf");
    }

    private interface Done {
        void done(String error);
    }

    /** Однократная закачка во временный файл с проверкой, что это вправду PDF. */
    private static void download(final Context ctx, final Building b, final File target,
                                 final Done cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            String error = null;
            File tmp = new File(app.getFilesDir(), "map_" + b.letter + ".part");
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(b.url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(12000);
                c.setReadTimeout(60000);
                if (c.getResponseCode() != HttpURLConnection.HTTP_OK)
                    throw new IOException("сайт ответил " + c.getResponseCode());
                InputStream in = c.getInputStream();
                FileOutputStream out = new FileOutputStream(tmp);
                byte[] buf = new byte[32 * 1024];
                long total = 0;
                try {
                    for (int n; (n = in.read(buf)) > 0; ) {
                        out.write(buf, 0, n);
                        total += n;
                    }
                } finally {
                    out.close();
                    in.close();
                }
                if (total < 1024 || !isPdf(tmp)) throw new IOException("вместо плана пришло не PDF");
                target.delete();
                if (!tmp.renameTo(target)) throw new IOException("не удалось сохранить файл");
            } catch (Exception e) {
                tmp.delete();
                error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            } finally {
                if (c != null) c.disconnect();
            }
            final String result = error;
            new Handler(Looper.getMainLooper()).post(() -> cb.done(result));
        }, "smtu-maps").start();
    }

    private static boolean isPdf(File f) {
        try (InputStream in = new java.io.FileInputStream(f)) {
            byte[] head = new byte[5];
            return in.read(head) == 5 && head[0] == '%' && head[1] == 'P'
                    && head[2] == 'D' && head[3] == 'F';
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------- просмотр

    /** Диалог с планом и переключателем этажей. */
    private static void open(final Activity a, final Building b, final File pdf, int floor) {
        final int[] page = {Math.max(1, Math.min(b.floors, floor > 0 ? floor : 1))};

        LinearLayout box = Ui.column(a);
        LinearLayout bar = Ui.row(a);
        bar.setGravity(Gravity.CENTER);
        final Ui ui = new Ui(a);

        TextView prev = ui.iconButton(a, "‹", 24);
        TextView next = ui.iconButton(a, "›", 24);
        final TextView caption = new TextView(a);
        caption.setGravity(Gravity.CENTER);
        caption.setTextSize(15);
        caption.setTypeface(Typeface.DEFAULT_BOLD);
        caption.setTextColor(ui.text);

        bar.addView(prev, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 40)));
        bar.addView(caption, Ui.lp(0, -2, 1f));
        bar.addView(next, Ui.lp(Ui.dp(a, 44), Ui.dp(a, 40)));
        box.addView(bar, Ui.lp(-1, -2));

        TextView hint = new TextView(a);
        hint.setText("щипком — увеличить, двойным тапом — вписать");
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setTextColor(ui.muted);
        hint.setPadding(0, 0, 0, Ui.dp(a, 6));
        box.addView(hint, Ui.lp(-1, -2));

        final ZoomView view = new ZoomView(a);
        view.setBackgroundColor(0xFF303338);
        int height = (int) (a.getResources().getDisplayMetrics().heightPixels * 0.62f);
        box.addView(view, Ui.lp(-1, height));

        final AlertDialog dialog = new AlertDialog.Builder(a)
                .setTitle("Корпус " + b.letter)
                .setView(box)
                .setNegativeButton("Закрыть", null)
                .create();
        dialog.show();

        final Runnable draw = () -> {
            caption.setText(page[0] + " этаж");
            prev.setEnabled(page[0] > 1);
            next.setEnabled(page[0] < b.floors);
            prev.setAlpha(page[0] > 1 ? 1f : 0.3f);
            next.setAlpha(page[0] < b.floors ? 1f : 0.3f);
            render(a, pdf, page[0] - 1, bitmap -> {
                if (a.isFinishing() || !dialog.isShowing()) return;
                if (bitmap == null) {
                    dialog.dismiss();
                    pdf.delete();          // битая закачка: в следующий раз скачаем заново
                    Toast.makeText(a, "План повреждён — откройте ещё раз",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                view.setPage(bitmap);
            });
        };
        prev.setOnClickListener(v -> {
            if (page[0] > 1) {
                page[0]--;
                draw.run();
            }
        });
        next.setOnClickListener(v -> {
            if (page[0] < b.floors) {
                page[0]++;
                draw.run();
            }
        });
        draw.run();
    }

    private interface Rendered {
        void done(Bitmap bitmap);
    }

    /** Отрисовать страницу в фоне — на слабом телефоне это заметные полсекунды. */
    private static void render(final Context ctx, final File pdf, final int page,
                               final Rendered cb) {
        new Thread(() -> {
            Bitmap bitmap = null;
            try (ParcelFileDescriptor fd =
                         ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                 PdfRenderer renderer = new PdfRenderer(fd)) {
                int index = Math.max(0, Math.min(renderer.getPageCount() - 1, page));
                PdfRenderer.Page p = renderer.openPage(index);
                float k = Math.min(4f, MAX_SIDE / (float) Math.max(p.getWidth(), p.getHeight()));
                // только ARGB_8888: другие форматы PdfRenderer отвергает
                bitmap = Bitmap.createBitmap(Math.round(p.getWidth() * k),
                        Math.round(p.getHeight() * k), Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                p.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
            } catch (Exception | OutOfMemoryError e) {
                android.util.Log.w("smtu-maps", "render failed", e);
                bitmap = null;
            }
            final Bitmap result = bitmap;
            new Handler(Looper.getMainLooper()).post(() -> cb.done(result));
        }, "smtu-maps-render").start();
    }

    /** Картинка с щипковым зумом и перетаскиванием. */
    static final class ZoomView extends View {
        private Bitmap page;
        private float scale = 1f, fit = 1f, tx, ty;
        private final ScaleGestureDetector pinch;
        private final GestureDetector drag;

        ZoomView(Context c) {
            super(c);
            pinch = new ScaleGestureDetector(c,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override public boolean onScale(ScaleGestureDetector d) {
                            float wanted = Math.max(fit, Math.min(scale * d.getScaleFactor(),
                                    fit * 8f));
                            float k = wanted / scale;
                            tx = d.getFocusX() - (d.getFocusX() - tx) * k;
                            ty = d.getFocusY() - (d.getFocusY() - ty) * k;
                            scale = wanted;
                            clamp();
                            invalidate();
                            return true;
                        }
                    });
            drag = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent e) {
                    return true;
                }

                @Override public boolean onDoubleTap(MotionEvent e) {
                    float wanted = scale > fit * 1.2f ? fit : fit * 3f;
                    float k = wanted / scale;
                    tx = e.getX() - (e.getX() - tx) * k;
                    ty = e.getY() - (e.getY() - ty) * k;
                    scale = wanted;
                    clamp();
                    invalidate();
                    return true;
                }

                @Override public boolean onScroll(MotionEvent a, MotionEvent e, float dx, float dy) {
                    tx -= dx;
                    ty -= dy;
                    clamp();
                    invalidate();
                    return true;
                }
            });
        }

        void setPage(Bitmap b) {
            page = b;
            fitToView();
            invalidate();
        }

        /**
         * Планы — широкие листы А1: если вписать целиком, читать нечего.
         * Поэтому минимальный масштаб — «страница целиком», а открывается
         * страница по высоте, чтобы номера аудиторий сразу были видны.
         */
        private void fitToView() {
            if (page == null || getWidth() == 0) return;
            fit = Math.min(getWidth() / (float) page.getWidth(),
                    getHeight() / (float) page.getHeight());
            scale = Math.max(fit, Math.min(getHeight() / (float) page.getHeight(), fit * 6f));
            fitCenterIfSmall();
            clamp();
        }

        private void fitCenterIfSmall() {
            if (page == null) return;
            tx = (getWidth() - page.getWidth() * scale) / 2f;
            ty = (getHeight() - page.getHeight() * scale) / 2f;
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            fitToView();
        }

        @Override protected void onDraw(Canvas c) {
            if (page == null) return;
            c.translate(tx, ty);
            c.scale(scale, scale);
            c.drawBitmap(page, 0, 0, null);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            pinch.onTouchEvent(e);
            if (!pinch.isInProgress()) drag.onTouchEvent(e);
            // не отдавать жест листанию расписания под диалогом
            if (e.getAction() == MotionEvent.ACTION_DOWN && getParent() != null)
                getParent().requestDisallowInterceptTouchEvent(true);
            return true;
        }

        @Override public boolean performClick() {
            return super.performClick();
        }

        private void clamp() {
            if (page == null) return;
            float w = page.getWidth() * scale, h = page.getHeight() * scale;
            tx = w <= getWidth() ? (getWidth() - w) / 2f
                    : Math.max(getWidth() - w, Math.min(0, tx));
            ty = h <= getHeight() ? (getHeight() - h) / 2f
                    : Math.max(getHeight() - h, Math.min(0, ty));
        }
    }
}
