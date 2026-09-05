package io.github.shumtugle.hark;

import android.content.Context;
import android.graphics.*;
import android.graphics.Shader;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.util.List;
import java.util.Locale;

public final class Cover {

    private static final String[] PREFERRED = {
        "cover", "folder", "front", "album", "artwork", "albumart", "обложка" };
    private static final String[] PIC_EXT = { ".jpg", ".jpeg", ".png", ".webp" };

    private static final java.util.HashMap<String, Bitmap> CACHE = new java.util.HashMap<>();
    private static final java.util.HashSet<String> EMPTY = new java.util.HashSet<>();

    private Cover() {}

    public static Bitmap get(Context c, Uri tree, String docId, String folderId, int px) {
        return get(c, tree, docId, folderId, px, false);
    }

    public static synchronized Bitmap get(Context c, Uri tree, String docId, String folderId,
                                          int px, boolean faded) {
        if (docId == null) return null;
        String key = docId + "|" + folderId + "|" + px + (faded ? "|f" : "");
        if (EMPTY.contains(key)) return null;
        Bitmap have = CACHE.get(key);
        if (have != null && !have.isRecycled()) return have;

        Bitmap b = load(c, tree, docId, folderId, px);
        if (b != null && faded) b = fade(b);
        if (b == null) EMPTY.add(key);
        else {

            if (CACHE.size() >= 6) CACHE.clear();
            CACHE.put(key, b);
        }
        return b;
    }

    public static synchronized void forget() {
        CACHE.clear();
        EMPTY.clear();
    }

    private static Bitmap load(Context c, Uri tree, String docId, String folderId, int px) {
        byte[] art = embedded(c, tree, docId);
        if (art != null) {
            Bitmap raw = BitmapFactory.decodeByteArray(art, 0, art.length);
            if (raw != null) { Probe.log("cover from tags"); return square(raw, px); }
        }
        Bitmap side = beside(c, tree, folderId, px);
        if (side != null) { Probe.log("cover from folder"); return side; }
        return null;
    }

    private static byte[] embedded(Context c, Uri tree, String docId) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            Uri u = Folder.fileUri(tree, docId);
            if (u == null) return null;
            r.setDataSource(c, u);
            return r.getEmbeddedPicture();
        } catch (Exception e) {
            return null;
        } finally {
            try { r.release(); } catch (Exception ignored) {}
        }
    }

    private static Bitmap beside(Context c, Uri tree, String folderId, int px) {
        if (tree == null || folderId == null) return null;
        try {
            List<Folder.Item> items = Folder.listRaw(c, tree, folderId);
            Folder.Item best = null;
            for (Folder.Item it : items) {
                if (it.dir || it.name == null) continue;
                String n = it.name.toLowerCase(Locale.US);
                boolean pic = false;
                for (String e : PIC_EXT) if (n.endsWith(e)) { pic = true; break; }
                if (!pic) continue;

                for (String p : PREFERRED) {
                    if (n.startsWith(p)) { best = it; break; }
                }
                if (best == it) break;
                if (best == null) best = it;
            }
            if (best == null) return null;
            Uri u = DocumentsContract.buildDocumentUriUsingTree(tree, best.docId);
            java.io.InputStream in = c.getContentResolver().openInputStream(u);
            if (in == null) return null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                int scale = 1;
                while (o.outWidth / (scale * 2) >= px) scale *= 2;
                java.io.InputStream in2 = c.getContentResolver().openInputStream(u);
                if (in2 == null) return null;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = scale;
                Bitmap raw = BitmapFactory.decodeStream(in2, null, o2);
                in2.close();
                return raw == null ? null : square(raw, px);
            } catch (Exception e) {
                try { in.close(); } catch (Exception ignored) {}
                return null;
            }
        } catch (Exception e) {
            Probe.log("cover lookup failed: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static Bitmap fade(Bitmap src) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawBitmap(src, 0, 0, null);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

            p.setShader(new LinearGradient(0, 0, w, 0,
                    new int[]{0xFF000000, 0xCC000000, 0x26000000},
                    new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            cv.drawRect(0, 0, w, h, p);
            return out;
        } catch (Exception e) {
            return src;
        }
    }

    private static Bitmap square(Bitmap src, int px) {
        try {
            int w = src.getWidth(), h = src.getHeight();
            int side = Math.min(w, h);
            Bitmap cut = Bitmap.createBitmap(src, (w - side) / 2, (h - side) / 2, side, side);
            Bitmap scaled = Bitmap.createScaledBitmap(cut, px, px, true);

            Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            RectF r = new RectF(0, 0, px, px);
            float rad = px * 0.18f;
            cv.drawRoundRect(r, rad, rad, p);
            p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            cv.drawBitmap(scaled, 0, 0, p);
            return out;
        } catch (Exception e) {
            return null;
        }
    }
}
