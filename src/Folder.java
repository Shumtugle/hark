package io.github.shumtugle.hark;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class Folder {

    public static class Item {
        public String docId, name, mime;
        public boolean dir;
        public long size;
        public long modified;
        public long duration = -1;
    }

    private static final String[] MP4_EXT = {".m4a", ".m4b", ".mp4", ".aac", ".m4p"};

    private static final String[] MP4_MIME = {"audio/mp4", "audio/x-m4a", "audio/aac",
                                              "audio/mp4a-latm", "video/mp4"};

    private static final String[] SND_EXT = {".mp3", ".ogg", ".oga", ".opus", ".flac",
                                             ".wav", ".wma", ".mka", ".mkv", ".webm",
                                             ".3gp", ".amr", ".ape", ".aiff", ".aif"};

    public static boolean isMp4(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        for (String e : MP4_EXT) if (n.endsWith(e)) return true;
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        for (String e : MP4_MIME) if (m.equals(e)) return true;
        return false;
    }

    public static boolean isSound(String name, String mime) {
        if (isMp4(name, mime)) return true;
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        for (String e : SND_EXT) if (n.endsWith(e)) return true;
        String m = mime == null ? "" : mime.toLowerCase(Locale.US);
        return m.startsWith("audio/");
    }

    public static final int SORT_NAME = 0, SORT_PLAYED = 1, SORT_DURATION = 2, SORT_MODIFIED = 3;

    public static String sortLabel(int mode) {
        switch (mode) {
            case SORT_PLAYED:   return I18n.t(I18n.S_PLAYED);
            case SORT_DURATION: return I18n.t(I18n.S_DURATION);
            case SORT_MODIFIED: return I18n.t(I18n.S_MODIFIED);
            default:            return I18n.t(I18n.S_NAME);
        }
    }

    public static void sort(List<Item> items, final int mode, final boolean desc, final Store store) {
        Collections.sort(items, new java.util.Comparator<Item>() {
            @Override public int compare(Item a, Item b) {

                boolean ma = isMp4(a.name, a.mime), mb = isMp4(b.name, b.mime);
                if (ma != mb) return ma ? -1 : 1;
                int r;
                switch (mode) {
                    case SORT_PLAYED: {
                        long pa = store == null ? 0 : store.lastPlayed(a.docId);
                        long pb = store == null ? 0 : store.lastPlayed(b.docId);
                        if (pa == 0 && pb == 0) return natural(a.name, b.name);
                        if (pa == 0) return 1;
                        if (pb == 0) return -1;
                        r = Long.compare(pb, pa);
                        break;
                    }
                    case SORT_DURATION: {
                        long da = a.duration > 0 ? a.duration : (store == null ? 0 : store.duration(a.docId));
                        long db = b.duration > 0 ? b.duration : (store == null ? 0 : store.duration(b.docId));
                        if (da == 0 && db == 0) return natural(a.name, b.name);
                        if (da == 0) return 1;
                        if (db == 0) return -1;
                        r = Long.compare(da, db);
                        break;
                    }
                    case SORT_MODIFIED: {
                        if (a.modified == 0 && b.modified == 0) return natural(a.name, b.name);
                        if (a.modified == 0) return 1;
                        if (b.modified == 0) return -1;
                        r = Long.compare(b.modified, a.modified);
                        break;
                    }
                    default:
                        r = natural(a.name, b.name);
                }
                return desc ? -r : r;
            }
        });
    }

    public static List<Item> listRaw(Context ctx, Uri tree, String parentDocId) {
        List<Item> out = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(kids, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
            if (c == null) return out;
            while (c.moveToNext()) {
                Item it = new Item();
                it.docId = c.getString(0);
                it.name  = c.getString(1);
                it.mime  = c.getString(2);
                it.dir   = DocumentsContract.Document.MIME_TYPE_DIR.equals(it.mime);
                out.add(it);
            }
        } catch (Exception ignored) {
        } finally { if (c != null) try { c.close(); } catch (Exception ignored) {} }
        return out;
    }

    public static List<Item> list(Context ctx, Uri tree, String parentDocId, boolean filterMp4,
                                  int sortMode, boolean sortDesc, Store store) {
        List<Item> dirs = new ArrayList<>(), files = new ArrayList<>();
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId);
        ContentResolver cr = ctx.getContentResolver();
        Cursor c = null;
        try {
            c = cr.query(kids, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED}, null, null, null);
            if (c == null) return dirs;
            while (c.moveToNext()) {
                Item it = new Item();
                it.docId = c.getString(0);
                it.name  = c.getString(1);
                it.mime  = c.getString(2);
                it.size  = c.isNull(3) ? 0 : c.getLong(3);
                it.modified = c.isNull(4) ? 0 : c.getLong(4);
                it.dir   = DocumentsContract.Document.MIME_TYPE_DIR.equals(it.mime);
                if (it.name == null) continue;
                if (it.dir) { dirs.add(it); continue; }
                if (filterMp4 ? isMp4(it.name, it.mime) : isSound(it.name, it.mime)) files.add(it);
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
        }
        sort(dirs, SORT_NAME, false, null);
        sort(files, sortMode, sortDesc, store);
        dirs.addAll(files);
        return dirs;
    }

    public static int natural(String a, String b) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i), cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int si = i, sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) i++;
                while (j < b.length() && Character.isDigit(b.charAt(j))) j++;
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) return na.length() - nb.length();
                int cmp = na.compareTo(nb);
                if (cmp != 0) return cmp;
            } else {
                int cmp = Character.toLowerCase(ca) - Character.toLowerCase(cb);
                if (cmp != 0) return cmp;
                i++; j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    public static Uri fileUri(Uri tree, String docId) {
        if (docId != null && (docId.startsWith("content://") || docId.startsWith("file://")))
            return Uri.parse(docId);
        if (tree == null) return null;
        return DocumentsContract.buildDocumentUriUsingTree(tree, docId);
    }

    public static String rootId(Uri tree) {
        return DocumentsContract.getTreeDocumentId(tree);
    }

    public static String nameOf(Context ctx, Uri tree, String docId) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    DocumentsContract.buildDocumentUriUsingTree(tree, docId),
                    new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},
                    null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
        }

        int i = docId.lastIndexOf('/');
        if (i >= 0 && i + 1 < docId.length()) return docId.substring(i + 1);
        i = docId.lastIndexOf(':');
        if (i >= 0 && i + 1 < docId.length()) return docId.substring(i + 1);
        return docId;
    }
}
