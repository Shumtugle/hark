package io.github.shumtugle.hark;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Store {

    private static final String POS  = "positions";
    private static final String CFG  = "config";
    private static final int    HISTORY_MAX = 20;

    public static final long EDGE_HEAD = 15_000L;

    public static final long EDGE_TAIL = 20_000L;

    public static final long REWIND    = 5_000L;

    private final SharedPreferences pos, cfg;

    public Store(Context c) {
        pos = c.getSharedPreferences(POS, Context.MODE_PRIVATE);
        cfg = c.getSharedPreferences(CFG, Context.MODE_PRIVATE);
    }

    public String root()            { return cfg.getString("root_tree", null); }
    public void   root(String uri)  { cfg.edit().putString("root_tree", uri).apply(); }

    public String folder()          { return cfg.getString("last_folder", null); }
    public void   folder(String id) { cfg.edit().putString("last_folder", id).apply(); }

    public float speed()            { return cfg.getFloat("speed", 1.0f); }
    public void  speed(float v)     { cfg.edit().putFloat("speed", v).apply(); }

    public int  sortMode()          { return cfg.getInt("sort_mode", 0); }
    public void sortMode(int v)     { cfg.edit().putInt("sort_mode", v).apply(); }
    public boolean sortDesc()       { return cfg.getBoolean("sort_desc", false); }
    public void    sortDesc(boolean v) { cfg.edit().putBoolean("sort_desc", v).apply(); }

    public boolean widgetFrame()      { return cfg.getBoolean("widget_frame", true); }
    public void    widgetFrame(boolean v) { cfg.edit().putBoolean("widget_frame", v).apply(); }

    public int  widgetShade()       { return cfg.getInt("widget_shade", 2); }
    public void widgetShade(int v)  { cfg.edit().putInt("widget_shade", v).apply(); }

    public long resumeBack()        { return cfg.getLong("resume_back", 3500L); }
    public void resumeBack(long v)  { cfg.edit().putLong("resume_back", v).apply(); }

    public boolean rememberFilter()      { return cfg.getBoolean("remember_filter", true); }
    public void    rememberFilter(boolean v) { cfg.edit().putBoolean("remember_filter", v).apply(); }

    public boolean filterMp4()      { return cfg.getBoolean("filter_mp4", false); }
    public void    filterMp4(boolean v) { cfg.edit().putBoolean("filter_mp4", v).apply(); }

    public static class Mark { public String tree, docId, name; }

    public java.util.List<Mark> marks() {
        java.util.List<Mark> out = new java.util.ArrayList<>();
        String raw = cfg.getString("marks", "");
        if (raw.isEmpty()) return out;
        for (String part : raw.split("\u0002")) {
            String[] f = part.split("\u0001", -1);
            Mark m = new Mark();
            if (f.length >= 3) { m.tree = f[0]; m.docId = f[1]; m.name = f[2]; }
            else if (f.length == 2) { m.tree = null; m.docId = f[0]; m.name = f[1]; }
            else continue;
            if (m.docId == null || m.docId.isEmpty()) continue;
            out.add(m);
        }
        return out;
    }

    private void marks(java.util.List<Mark> list) {
        StringBuilder sb = new StringBuilder();
        for (Mark m : list) {
            if (sb.length() > 0) sb.append('\u0002');
            sb.append(m.tree == null ? "" : m.tree).append('\u0001')
              .append(m.docId).append('\u0001').append(m.name);
        }
        cfg.edit().putString("marks", sb.toString()).apply();
    }

    public boolean marked(String docId) {
        for (Mark m : marks()) if (m.docId.equals(docId)) return true;
        return false;
    }

    public void mark(String tree, String docId, String name) {
        java.util.List<Mark> l = marks();
        for (Mark m : l) if (m.docId.equals(docId)) return;
        Mark m = new Mark();
        m.tree = tree; m.docId = docId; m.name = name;
        l.add(m);
        while (l.size() > 20) l.remove(0);
        marks(l);
    }

    public void unmark(String docId) {
        java.util.List<Mark> l = marks();
        for (int i = 0; i < l.size(); i++)
            if (l.get(i).docId.equals(docId)) { l.remove(i); break; }
        marks(l);
    }

    public String lang()            { return cfg.getString("lang", "auto"); }
    public void   lang(String v)    { cfg.edit().putString("lang", v).apply(); }

    public boolean probe()          { return cfg.getBoolean("probe", true); }
    public void    probe(boolean v) { cfg.edit().putBoolean("probe", v).apply(); }

    public String  lastDoc()        { return cfg.getString("last_doc", null); }
    public String  lastName()       { return cfg.getString("last_name", null); }
    public String  lastFolder()     { return cfg.getString("last_doc_folder", null); }
    public String  lastFolderName() { return cfg.getString("last_doc_folder_name", null); }

    public String lastTree()        { return cfg.getString("last_doc_tree", null); }
    public void   lastTree(String v){ cfg.edit().putString("last_doc_tree", v).apply(); }

    public void last(String docId, String name, String folderId, String folderName) {
        cfg.edit()
           .putString("last_doc", docId)
           .putString("last_name", name)
           .putString("last_doc_folder", folderId)
           .putString("last_doc_folder_name", folderName)
           .commit();
    }

    public long lastPlayed(String docId) { return pos.getLong("t_" + docId, 0L); }

    public long position(String docId) { return pos.getLong("p_" + docId, 0L); }
    public long duration(String docId) { return pos.getLong("d_" + docId, 0L); }
    public boolean done(String docId)  { return pos.getBoolean("f_" + docId, false); }

    public long resumeAt(String docId) {
        long p = position(docId);
        long d = duration(docId);
        if (p <= EDGE_HEAD) return 0L;
        if (d > 0 && p >= d - EDGE_TAIL) return 0L;
        return Math.max(0L, p - REWIND);
    }

    public void save(String docId, long p, long d) {
        SharedPreferences.Editor e = pos.edit();
        e.putLong("p_" + docId, p);
        if (d > 0) e.putLong("d_" + docId, d);
        e.putLong("t_" + docId, System.currentTimeMillis());
        if (d > 0 && p >= d - EDGE_TAIL) e.putBoolean("f_" + docId, true);
        else if (p > EDGE_HEAD)          e.putBoolean("f_" + docId, false);
        e.commit();
        trim();
    }

    public void touch(String docId, String name, String folderId, String folderName) {
        pos.edit().putLong("t_" + docId, System.currentTimeMillis())
                  .putString("n_" + docId, name)
                  .putString("fn_" + docId, folderName == null ? "" : folderName)
                  .putString("fi_" + docId, folderId == null ? "" : folderId)
                  .apply();
    }

    public static class Recent {
        public String docId, name, folderId, folderName;
        public long at, position, duration;
    }

    public List<Recent> history() {
        List<Recent> out = new ArrayList<>();
        Map<String, ?> all = pos.getAll();
        for (Map.Entry<String, ?> e : all.entrySet()) {
            if (!e.getKey().startsWith("t_")) continue;
            String id = e.getKey().substring(2);
            Recent r = new Recent();
            r.docId = id;
            r.at = pos.getLong("t_" + id, 0L);
            r.name = pos.getString("n_" + id, id);
            r.folderId = pos.getString("fi_" + id, "");
            r.folderName = pos.getString("fn_" + id, "");
            r.position = position(id);
            r.duration = duration(id);
            out.add(r);
        }
        Collections.sort(out, new java.util.Comparator<Recent>() {
            @Override public int compare(Recent a, Recent b) { return Long.compare(b.at, a.at); }
        });
        while (out.size() > HISTORY_MAX) out.remove(out.size() - 1);
        return out;
    }

    private void trim() {
        Map<String, ?> all = pos.getAll();
        int stamps = 0;
        for (String k : all.keySet()) if (k.startsWith("t_")) stamps++;
        if (stamps <= HISTORY_MAX * 3) return;

        List<Recent> h = history();
        java.util.HashSet<String> keep = new java.util.HashSet<>();
        for (Recent r : h) keep.add(r.docId);

        SharedPreferences.Editor e = pos.edit();
        for (String k : all.keySet()) {
            int i = k.indexOf('_');
            if (i < 0) continue;
            String id = k.substring(i + 1);
            if (!keep.contains(id)) e.remove(k);
        }
        e.apply();
    }
}
