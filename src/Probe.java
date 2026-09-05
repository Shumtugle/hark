package io.github.shumtugle.hark;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class Probe {

    public interface Fn { String get(); }

    private static final int MAX = 400;
    private static final List<String> ERRS = new ArrayList<>();
    private static final SimpleDateFormat CLOCK = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final int PAD = 20;

    private Probe() {}

    private static final String CRASH_FILE = "last-crash.txt";

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

    public static synchronized String tail() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- log \u00B7 newest at the bottom \u00B7 ")
          .append(ERRS.size()).append(" ---\n");
        if (ERRS.isEmpty()) sb.append("empty. That is good news.\n");
        else for (String e : ERRS) sb.append(e).append('\n');
        return sb.toString();
    }
}
