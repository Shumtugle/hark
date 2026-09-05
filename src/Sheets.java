package io.github.shumtugle.hark;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.List;

public final class Sheets {

    private Sheets() {}

    private static AlertDialog.Builder dark(Context c) {
        return new AlertDialog.Builder(c, android.R.style.Theme_Material_Dialog);
    }

    private static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(Ui.SURFACE);
        int p = Ui.dp(c, 8);
        l.setPadding(0, p, 0, p);
        return l;
    }

    private static TextView row(Context c, String text, int color) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(16f);
        t.setTextColor(color);
        t.setPadding(Ui.dp(c, 24), Ui.dp(c, 14), Ui.dp(c, 24), Ui.dp(c, 14));
        return t;
    }

    public static void speed(final MainActivity a) {
        final float[] vals = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        final Store st = a.store();
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(col).create();
        float cur = st.speed();
        for (final float v : vals) {
            boolean on = Math.abs(v - cur) < 0.01f;
            TextView t = row(a, (v == 1.0f ? "1.0" : String.valueOf(v)) + "×",
                    on ? Ui.ACCENT : Ui.TEXT);
            t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                PlayerService s = PlayerService.get();
                if (s != null) s.speed(v); else st.speed(v);
                a.refreshPanel();
                d.dismiss();}
        });
            col.addView(t);
        }
        d.show();
    }

    public static void sleep(final MainActivity a) {
        final PlayerService s = PlayerService.get();
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(col).create();

        if (s != null && (s.sleepLeft() > 0 || s.sleepTilEnd())) {
            TextView off = row(a, I18n.t(I18n.SLEEP_OFF), Ui.ALARM);
            off.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { s.sleepCancel(); a.refreshPanel(); d.dismiss(); }
        });
            col.addView(off);
        }
        int[] mins = {15, 30, 60};
        for (final int m : mins) {
            TextView t = row(a, m + " " + I18n.t(I18n.MINUTES), Ui.TEXT);
            t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
                if (s != null) s.sleepIn(m * 60_000L);
                a.refreshPanel(); d.dismiss();}
        });
            col.addView(t);
        }
        TextView end = row(a, I18n.t(I18n.TILL_END), Ui.TEXT);
        end.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
            if (s != null) s.sleepAtEndOfFile();
            a.refreshPanel(); d.dismiss();}
        });
        col.addView(end);
        d.show();
    }

    public static void jump(final MainActivity a) {
        final PlayerService s = PlayerService.get();
        if (s == null) return;
        long cur = s.position();

        LinearLayout col = column(a);
        col.setPadding(Ui.dp(a, 24), Ui.dp(a, 20), Ui.dp(a, 24), Ui.dp(a, 8));

        TextView cap = new TextView(a);
        cap.setText(I18n.t(I18n.JUMP_TO));
        cap.setTextColor(Ui.TEXT_2);
        cap.setTextSize(13f);
        col.addView(cap);

        LinearLayout line = new LinearLayout(a);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(0, Ui.dp(a, 12), 0, 0);

        final EditText[] f = new EditText[3];
        long sec = cur / 1000;
        long[] init = {sec / 3600, (sec % 3600) / 60, sec % 60};
        String[] hint = {I18n.t(I18n.H), I18n.t(I18n.M), I18n.t(I18n.S)};
        for (int i = 0; i < 3; i++) {
            f[i] = new EditText(a);
            f[i].setInputType(InputType.TYPE_CLASS_NUMBER);
            f[i].setText(String.valueOf(init[i]));
            f[i].setTextColor(Ui.TEXT);
            f[i].setTextSize(22f);
            f[i].setTypeface(Typeface.MONOSPACE);
            f[i].setGravity(Gravity.CENTER);
            f[i].setHint(hint[i]);
            f[i].setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(f[i]);
            if (i < 2) {
                TextView c = new TextView(a);
                c.setText(":");
                c.setTextColor(Ui.TEXT_2);
                c.setTextSize(22f);
                line.addView(c);
            }
        }
        col.addView(line);

        dark(a).setView(col)
                .setPositiveButton(I18n.t(I18n.JUMP), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dl, int w) {
                        long h = num(f[0]), m = num(f[1]), ss = num(f[2]);
                        s.seekTo((h * 3600 + m * 60 + ss) * 1000L);
                        a.refreshPanel();
                    }
                })
                .setNegativeButton(I18n.t(I18n.CANCEL), null)
                .show();
    }

    private static long num(EditText e) {
        try { return Long.parseLong(e.getText().toString().trim()); }
        catch (Exception x) { return 0; }
    }

    public static void file(final MainActivity a, final Folder.Item it) {
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(col).create();

        TextView head = row(a, Ui.bare(it.name), Ui.TEXT_2);
        head.setTextSize(13f);
        col.addView(head);

        TextView share = row(a, I18n.t(I18n.SHARE), Ui.TEXT);
        share.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); share(a, it); }
        });
        col.addView(share);

        TextView start = row(a, I18n.t(I18n.FROM_START), Ui.TEXT);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.playFromStart(it); }
        });
        col.addView(start);

        TextView forget = row(a, I18n.t(I18n.FORGET), Ui.TEXT);
        forget.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) {
            a.store().save(it.docId, 0, a.store().duration(it.docId));
            a.refreshList();
            d.dismiss();}
        });
        col.addView(forget);

        TextView about = row(a, I18n.t(I18n.DETAILS), Ui.TEXT_2);
        about.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); about(a, it); }
        });
        col.addView(about);

        d.show();
    }

    public static void share(MainActivity a, Folder.Item it) {
        Uri tree = a.tree();
        if (tree == null) return;
        try {
            Uri u = Folder.fileUri(tree, it.docId);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(it.mime == null ? "audio/*" : it.mime);
            i.putExtra(Intent.EXTRA_STREAM, u);
            i.putExtra(Intent.EXTRA_SUBJECT, it.name);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, I18n.t(I18n.SEND)));
        } catch (Exception e) {
            Probe.log("share failed: " + e.getMessage());
            Toast.makeText(a, I18n.t(I18n.SEND_FAIL), Toast.LENGTH_SHORT).show();
        }
    }

    public static void about(MainActivity a, Folder.Item it) {
        long dur = it.duration > 0 ? it.duration : a.store().duration(it.docId);
        String s = I18n.t(I18n.F_NAME) + "\n" + it.name
                + "\n\n" + I18n.t(I18n.F_MIME) + "\n" + (it.mime == null ? I18n.t(I18n.NOT_SENT) : it.mime)
                + "\n\n" + I18n.t(I18n.F_SIZE) + "\n" + (it.size > 0 ? (it.size / 1024 / 1024) + " " + I18n.t(I18n.MB) : I18n.t(I18n.UNKNOWN))
                + "\n\n" + I18n.t(I18n.F_DUR) + "\n" + (dur > 0 ? Ui.time(dur) : I18n.t(I18n.NOT_READ))
                + "\n\n" + I18n.t(I18n.F_POS) + "\n" + Ui.time(a.store().position(it.docId))
                + "\n\ndocumentId\n" + it.docId;
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(13f);
        t.setPadding(Ui.dp(a, 24), Ui.dp(a, 20), Ui.dp(a, 24), Ui.dp(a, 20));
        ScrollView sc = new ScrollView(a);
        sc.setBackgroundColor(Ui.SURFACE);
        sc.addView(t);
        dark(a).setView(sc).setPositiveButton(I18n.t(I18n.CLOSE), null).show();
    }

    public static void history(final MainActivity a) {
        List<Store.Recent> h = a.store().history();
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(wrap(a, col)).create();

        if (h.isEmpty()) col.addView(row(a, I18n.t(I18n.HIST_EMPTY), Ui.TEXT_2));

        for (final Store.Recent r : h) {
            LinearLayout item = new LinearLayout(a);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(Ui.dp(a, 24), Ui.dp(a, 10), Ui.dp(a, 24), Ui.dp(a, 10));

            TextView t1 = new TextView(a);
            t1.setText(Ui.bare(r.name));
            t1.setTextColor(Ui.TEXT);
            t1.setTextSize(15f);
            t1.setSingleLine(true);
            t1.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            item.addView(t1);

            TextView t2 = new TextView(a);
            String pos = Ui.time(r.position) + (r.duration > 0 ? " / " + Ui.time(r.duration) : "");
            t2.setText((r.folderName.isEmpty() ? "" : r.folderName + "  ·  ") + pos);
            t2.setTextColor(Ui.TEXT_2);
            t2.setTextSize(12f);
            item.addView(t2);

            item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.jumpToRecent(r); }
        });
            col.addView(item);
        }
        d.show();
    }

    private static ScrollView wrap(Context c, View v) {
        ScrollView s = new ScrollView(c);
        s.setBackgroundColor(Ui.SURFACE);
        s.addView(v);
        return s;
    }

    public static void marks(final MainActivity a) {
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(wrap(a, col)).create();

        final String fid = a.folderIdPublic();
        final String fname = a.folderNamePublic();

        if (fid != null) {
            boolean on = a.store().marked(fid);
            TextView add = row(a, (on ? "\u2605  " : "\u2606  ")
                    + (on ? fname : I18n.t(I18n.MARK_ADD)), on ? Ui.ACCENT : Ui.TEXT);
            add.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) {
                    if (a.store().marked(fid)) {
                        a.store().unmark(fid);
                        Probe.log("bookmark removed: " + fname);
                    } else {
                        a.store().mark(a.treeUri(), fid, fname);
                        Probe.log("bookmark added: " + fname + "   id " + fid);
                    }
                    d.dismiss();
                    a.refreshChips();
                }
            });
            col.addView(add);

            View sep = new View(a);
            sep.setBackgroundColor(Ui.LINE);
            sep.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(a, 1)));
            col.addView(sep);
        }

        java.util.List<Store.Mark> list = a.store().marks();
        if (list.isEmpty()) {
            col.addView(row(a, I18n.t(I18n.MARK_NONE), Ui.TEXT_2));
        } else {
            for (final Store.Mark m : list) {
                boolean alive = a.markUsable(m);
                TextView t = row(a, m.name + (alive ? "" : "   \u2014"),
                        !alive ? Ui.ALARM : (m.docId.equals(fid) ? Ui.ACCENT : Ui.TEXT));
                t.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View x) { d.dismiss(); a.openMark(m); }
                });
                t.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override public boolean onLongClick(View x) {
                        a.store().unmark(m.docId);
                        d.dismiss();
                        a.refreshChips();
                        Toast.makeText(a, m.name, Toast.LENGTH_SHORT).show();
                        return true;
                    }
                });
                col.addView(t);
            }
            TextView hint = row(a, I18n.t(I18n.MARK_DEL), Ui.TEXT_OFF);
            hint.setTextSize(12f);
            col.addView(hint);
        }
        d.show();
    }

    public static void sort(final MainActivity a) {
        final int[] modes = {Folder.SORT_NAME, Folder.SORT_PLAYED,
                             Folder.SORT_DURATION, Folder.SORT_MODIFIED};
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(col).create();
        int cur = a.store().sortMode();
        boolean desc = a.store().sortDesc();

        TextView cap = row(a, I18n.t(I18n.SORT_HINT), Ui.TEXT_2);
        cap.setTextSize(12f);
        col.addView(cap);

        for (final int m : modes) {
            boolean on = (m == cur);
            TextView t = row(a, Folder.sortLabel(m) + (on ? (desc ? "   \u2191" : "   \u2193") : ""),
                    on ? Ui.ACCENT : Ui.TEXT);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View x) { a.setSort(m); d.dismiss(); }
            });
            col.addView(t);
        }
        d.show();
    }

    public static void probe(final MainActivity a) {
        final String full = a.probeReport();

        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.SURFACE);

        final EditText find = new EditText(a);
        find.setHint("filter");
        find.setTextSize(13f);
        find.setSingleLine(true);
        find.setTextColor(Ui.TEXT);
        find.setHintTextColor(Ui.TEXT_OFF);
        find.setPadding(Ui.dp(a, 16), Ui.dp(a, 10), Ui.dp(a, 16), Ui.dp(a, 6));
        col.addView(find);

        final TextView t = new TextView(a);
        t.setText(full);
        t.setTextColor(Ui.TEXT);
        t.setTextSize(11f);
        t.setTypeface(Typeface.MONOSPACE);
        t.setPadding(Ui.dp(a, 14), Ui.dp(a, 8), Ui.dp(a, 14), Ui.dp(a, 16));
        t.setTextIsSelectable(true);

        ScrollView sc = new ScrollView(a);
        sc.setBackgroundColor(Ui.SURFACE);
        sc.addView(t);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        sc.setLayoutParams(sp);
        col.addView(sc);

        find.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a1, int b, int c1) {}
            @Override public void onTextChanged(CharSequence c, int a1, int b, int c1) {}
            @Override public void afterTextChanged(android.text.Editable e) {
                String q = e.toString().trim().toLowerCase(java.util.Locale.US);
                if (q.isEmpty()) { t.setText(full); return; }
                StringBuilder out = new StringBuilder();
                for (String ln : full.split("\n"))
                    if (ln.toLowerCase(java.util.Locale.US).contains(q)) out.append(ln).append('\n');
                t.setText(out.length() > 0 ? out.toString() : "nothing matched");
            }
        });

        dark(a).setView(col)
                .setPositiveButton(I18n.t(I18n.CLOSE), null)
                .setNeutralButton(I18n.t(I18n.COPY), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        try {
                            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                                    a.getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("hark", full));
                            Toast.makeText(a, I18n.t(I18n.COPIED), Toast.LENGTH_SHORT).show();
                        } catch (Exception ex) {

                            Toast.makeText(a, I18n.t(I18n.SEND_FAIL), Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton(I18n.t(I18n.CLEAR), new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { Probe.clear(); }
                })
                .show();
    }

    public static void folder(final MainActivity a) {
        LinearLayout col = column(a);
        final AlertDialog d = dark(a).setView(col).create();

        TextView pick = row(a, I18n.t(I18n.PICK), Ui.TEXT);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.pickFolder(); }
        });
        col.addView(pick);

        TextView root = row(a, I18n.t(I18n.TO_ROOT), Ui.TEXT);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); a.goRoot(); }
        });
        col.addView(root);

        TextView srt = row(a, I18n.t(I18n.SORT) + "  ·  " + Folder.sortLabel(a.store().sortMode())
                + (a.store().sortDesc() ? "  ↑" : "  ↓"), Ui.TEXT);
        srt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); sort(a); }
        });
        col.addView(srt);

        TextView hist = row(a, I18n.t(I18n.HISTORY), Ui.TEXT);
        hist.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View x) { d.dismiss(); history(a); }
        });
        col.addView(hist);

        d.show();
    }
}
