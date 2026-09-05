package io.github.shumtugle.hark;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Rows extends BaseAdapter {

    public static final int T_CONT = 0, T_DIR = 1, T_FILE = 2;

    public static class Cont {
        public String docId, name, folderId, folderName;
        public long position, duration;
        public boolean elsewhere;
    }

    private final Context ctx;
    private final List<Object> items = new ArrayList<>();
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Store store;
    private String playingId;

    public Rows(Context c, Store s) { ctx = c; store = s; }

    public void set(List<Folder.Item> list, Cont cont) {
        items.clear();
        if (cont != null) items.add(cont);
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void playing(String docId) {
        if (docId == null ? playingId == null : docId.equals(playingId)) return;
        playingId = docId;
        notifyDataSetChanged();
    }

    @Override public int getCount() { return items.size(); }
    @Override public Object getItem(int i) { return items.get(i); }
    @Override public long getItemId(int i) { return i; }
    @Override public int getViewTypeCount() { return 3; }

    @Override public int getItemViewType(int i) {
        Object o = items.get(i);
        if (o instanceof Cont) return T_CONT;
        return ((Folder.Item) o).dir ? T_DIR : T_FILE;
    }

    @Override public View getView(int i, View reuse, ViewGroup parent) {
        int type = getItemViewType(i);
        Holder h;
        if (reuse == null) { h = build(type); reuse = h.root; reuse.setTag(h); }
        else h = (Holder) reuse.getTag();

        if (type == T_CONT)      bindCont((Cont) items.get(i), h);
        else if (type == T_DIR)  bindDir((Folder.Item) items.get(i), h);
        else                     bindFile((Folder.Item) items.get(i), h);
        return reuse;
    }

    private static class Holder {
        LinearLayout root;
        View mark;
        ImageView icon;
        TextView title, right, sub;
        Bar bar;
    }

    private Holder build(int type) {
        Holder h = new Holder();
        int pad = Ui.dp(ctx, 16);

        h.root = new LinearLayout(ctx);
        h.root.setOrientation(LinearLayout.HORIZONTAL);
        h.root.setGravity(Gravity.CENTER_VERTICAL);
        h.root.setMinimumHeight(Ui.dp(ctx, type == T_CONT ? 72 : 64));

        h.mark = new View(ctx);
        h.mark.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(ctx, 3), ViewGroup.LayoutParams.MATCH_PARENT));
        h.root.addView(h.mark);

        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad - Ui.dp(ctx, 3), Ui.dp(ctx, 10), pad, Ui.dp(ctx, 10));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        body.setLayoutParams(bp);

        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);

        h.icon = new ImageView(ctx);
        int is = Ui.dp(ctx, 20);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(is, is);
        ip.rightMargin = Ui.dp(ctx, 12);
        h.icon.setLayoutParams(ip);
        line.addView(h.icon);

        h.title = new TextView(ctx);
        h.title.setTextSize(16f);
        h.title.setSingleLine(true);
        h.title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        h.title.setTextColor(Ui.TEXT);
        h.title.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        line.addView(h.title);

        h.right = new TextView(ctx);
        h.right.setTextSize(13f);
        h.right.setTypeface(Typeface.MONOSPACE);
        h.right.setTextColor(Ui.TEXT_2);
        h.right.setPadding(Ui.dp(ctx, 10), 0, 0, 0);
        line.addView(h.right);

        body.addView(line);

        h.sub = new TextView(ctx);
        h.sub.setTextSize(12f);
        h.sub.setTextColor(Ui.TEXT_2);
        h.sub.setSingleLine(true);
        h.sub.setVisibility(View.GONE);
        body.addView(h.sub);

        h.bar = new Bar(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 2));
        lp.topMargin = Ui.dp(ctx, 8);
        h.bar.setLayoutParams(lp);
        h.bar.setVisibility(View.GONE);
        body.addView(h.bar);

        h.root.addView(body);
        return h;
    }

    private void bindCont(Cont c, Holder h) {
        h.mark.setBackgroundColor(Ui.ACCENT);
        h.icon.setImageDrawable(new Icon(Icon.PLAY, Ui.ACCENT));
        h.title.setText(Ui.bare(c.name));
        h.title.setTextColor(Ui.TEXT);
        h.right.setText("");
        h.sub.setVisibility(View.VISIBLE);
        String pos = Ui.time(c.position) + (c.duration > 0 ? " " + I18n.t(I18n.OF) + " " + Ui.time(c.duration) : "");
        h.sub.setText(c.elsewhere ? (c.folderName + "  ·  " + pos) : pos);
        h.bar.setVisibility(View.VISIBLE);
        h.bar.set(c.duration > 0 ? (float) c.position / c.duration : 0f);
        h.root.setBackgroundColor(Ui.SURFACE);
    }

    private void bindDir(Folder.Item it, Holder h) {
        h.mark.setBackgroundColor(0);
        h.icon.setImageDrawable(new Icon(Icon.FOLDER, Ui.TEXT_2));
        h.title.setText(it.name);
        h.title.setTextColor(Ui.TEXT);
        h.right.setText("");
        h.sub.setVisibility(View.GONE);
        h.bar.setVisibility(View.GONE);
        h.root.setBackgroundColor(0);
    }

    private void bindFile(Folder.Item it, Holder h) {
        boolean isPlaying = it.docId.equals(playingId);
        boolean done = store.done(it.docId);
        long pos = store.position(it.docId);
        long dur = it.duration > 0 ? it.duration : store.duration(it.docId);

        h.mark.setBackgroundColor(isPlaying ? Ui.ACCENT : 0);
        h.title.setText(Ui.bare(it.name));
        h.sub.setVisibility(View.GONE);

        if (isPlaying) {

            PlayerService ps = PlayerService.get();
            h.icon.setVisibility(View.VISIBLE);
            h.icon.setImageDrawable(new Icon(
                    (ps != null && ps.isPlaying()) ? Icon.PLAY : Icon.PAUSE, Ui.ACCENT));
            h.title.setTextColor(Ui.ACCENT);
            h.title.setTypeface(Typeface.DEFAULT_BOLD);
            h.root.setBackgroundColor(0x14D49A3A);
        } else {
            h.icon.setVisibility(View.GONE);
            h.icon.setImageDrawable(null);
            h.title.setTextColor(done ? Ui.TEXT_OFF : Ui.TEXT);
            h.title.setTypeface(Typeface.DEFAULT);
            h.root.setBackgroundColor(0);
        }

        h.right.setText(dur > 0 ? Ui.time(dur) : "");
        h.right.setTextColor(done && !isPlaying ? Ui.TEXT_OFF : Ui.TEXT_2);

        boolean showBar = pos > Store.EDGE_HEAD && dur > 0 && pos < dur - Store.EDGE_TAIL;
        h.bar.setVisibility(showBar ? View.VISIBLE : View.GONE);
        if (showBar) h.bar.set((float) pos / dur);

        if (it.duration < 0) askDuration(it);
    }

    private void askDuration(final Folder.Item it) {
        it.duration = 0;
        final android.net.Uri tree = ((MainActivity) ctx).tree();
        if (tree == null) return;
        final Rows self = this;
        pool.execute(new Runnable() {
            @Override public void run() {
                long d = 0;
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    r.setDataSource(ctx, Folder.fileUri(tree, it.docId));
                    String s = r.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (s != null) d = Long.parseLong(s);
                } catch (Exception ignored) {
                } finally { try { r.release(); } catch (Exception ignored) {} }
                if (d > 0) {
                    it.duration = d;
                    ((MainActivity) ctx).runOnUiThread(new Runnable() {
                        @Override public void run() { self.notifyDataSetChanged(); }
                    });
                }
            }
        });
    }

    public void shutdown() { pool.shutdownNow(); }
}
