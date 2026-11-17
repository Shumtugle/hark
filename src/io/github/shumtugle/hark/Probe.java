package io.github.shumtugle.hark;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The inquest. The app writes down here what actually happened to it.
 *
 * Two things matter in it.
 *
 * First: every line of the report is computed inside its own guard. If its source fails, one
 * line fails, not the whole report, and those are exactly the moments the report is needed.
 *
 * Second: the log goes at the bottom, newest last. The reader reaches the bottom and finds the
 * latest event where they stopped, instead of scrolling back.
 *
 * The report is in English: it travels into conversations read by other eyes.
 */
public final class Probe {

    /** The source of one line. A separate interface, because there are no lambdas here. */
    public interface Fn { String get(); }

    private static final int MAX = 400;
    private static final List<String> ERRS = new ArrayList<>();
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final int PAD = 20;

    private Probe() {}

    private static final String CRASH_FILE = "last-crash.txt";

    /**
     * The log lives in memory and dies with the process. The stack of the last crash is the
     * one thing that must survive death, or the cause can only be guessed.
     */
    public static void install(final android.content.Context ctx) {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    java.io.StringWriter w = new java.io.StringWriter();
                    w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                            .format(new Date()) + "\n");
                    w.write("thread " + t.getName() + "\n\n");
                    e.printStackTrace(new java.io.PrintWriter(w));
                    w.write("\n--- log at the moment of death ---\n");
                    w.write(tail());
                    java.io.FileOutputStream f = ctx.openFileOutput(CRASH_FILE,
                            android.content.Context.MODE_PRIVATE);
                    f.write(w.toString().getBytes("UTF-8"));
                    f.close();
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /** The stack of the previous crash, if there was one. */
    public static String lastCrash(android.content.Context ctx) {
        try {
            java.io.FileInputStream f = ctx.openFileInput(CRASH_FILE);
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = f.read(buf)) > 0) o.write(buf, 0, n);
            f.close();
            return o.toString("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static void forgetCrash(android.content.Context ctx) {
        try { ctx.deleteFile(CRASH_FILE); } catch (Exception ignored) {}
    }

    public static synchronized void log(String s) {
        ERRS.add(CLOCK.format(new Date()) + "  " + s);
        while (ERRS.size() > MAX) ERRS.remove(0);
    }

    public static synchronized int count() { return ERRS.size(); }

    public static synchronized void clear() { ERRS.clear(); }

    /**
     * A name padded with spaces, then the value. An exception inside the source does not break
     * the report: the line honestly says it failed.
     */
    public static String line(String name, Fn f) {
        String v;
        try {
            v = f.get();
            if (v == null) v = "null";
        } catch (Throwable t) {
            v = "failed \u2014 " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : ": " + t.getMessage());
        }
        StringBuilder sb = new StringBuilder(name);
        while (sb.length() < PAD) sb.append(' ');
        return sb.append("  ").append(v).toString();
    }

    public static String head(String title) { return "--- " + title + " ---"; }

    /** The tail of the report: the log itself. */
    public static synchronized String tail() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- log \u00B7 newest at the bottom \u00B7 ")
          .append(ERRS.size()).append(" ---\n");
        if (ERRS.isEmpty()) sb.append("empty. That is good news.\n");
        else for (String e : ERRS) sb.append(e).append('\n');
        return sb.toString();
    }
}
