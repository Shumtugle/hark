package io.github.shumtugle.hark;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import android.widget.HorizontalScrollView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements PlayerService.Watcher {

    private static final int REQ_TREE = 11, REQ_NOTE = 12;
    private static final int ID_HEAD = 0x48A1, ID_LIST = 0x48A2, ID_PANEL = 0x48A3;
    private static final String AUTHORITY = "com.android.externalstorage.documents";
    private static final String TARGET_DOC = "primary:Music";

    private Store store;
    private Uri tree;
    private Rows rows;
    private ListView list;

    private final List<String> pathIds = new ArrayList<>();
    private final List<String> pathNames = new ArrayList<>();
    private List<Folder.Item> current = new ArrayList<>();

    private boolean filterMp4 = true;
    private boolean full = false;
    private Folder.Item external = null;
    private boolean pendingExternal = false;
    private String pendingTarget = null;

    private ImageView btnUp, gear;
    private TextView star, batt;
    private ImageView phones;
    private TextView emptyView;
    private int hiddenByFilter;
    private TextView  tvFolder, chip;

    private LinearLayout panel;
    private TextView pTitle, pTime, pSpeed, pSleep, scrubHint;
    private ImageView pArt;
    private String artFor;
    private ImageView pBack, pPlay, pFwd;
    private Bar seek;

    private FrameLayout rootFrame;
    private LinearLayout fullView;
    private TextView fTime, fLeft, fTitle, fSpeed, fSleep;
    private Bar fSeek;
    private ImageView fPlay, fBack, fFwd;

    private final Handler h = new Handler(Looper.getMainLooper());
    private boolean bound = false;
    private int ticks;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Probe.install(getApplicationContext());
        store = new Store(this);
        I18n.apply(this, store.lang());
        filterMp4 = store.rememberFilter() && store.filterMp4();
        buildUi();

        java.util.List<String> ask = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                   != android.content.pm.PackageManager.PERMISSION_GRANTED)
            ask.add("android.permission.POST_NOTIFICATIONS");

        if (Build.VERSION.SDK_INT >= 31
                && checkSelfPermission("android.permission.BLUETOOTH_CONNECT")
                   != android.content.pm.PackageManager.PERMISSION_GRANTED)
            ask.add("android.permission.BLUETOOTH_CONNECT");
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), REQ_NOTE);

        if (handleIncoming(getIntent())) return;

        final String crash = Probe.lastCrash(this);
        if (crash != null) {
            Probe.log("recovered from a crash \u2014 stack kept below");
            Probe.log(crash);
            Probe.forgetCrash(this);
            h.postDelayed(new Runnable() { @Override public void run() {
                Toast.makeText(MainActivity.this, I18n.t(I18n.CRASHED), Toast.LENGTH_LONG).show();
            }}, 800);
        }

        String saved = store.root();
        if (saved == null) { pickFolder(); return; }
        tree = Uri.parse(saved);
        if (!stillPermitted(tree)) {
            Probe.log("permission to the root is gone");
            tree = null;
            pickFolder();
            return;
        }
        restorePath();
        refreshList();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        handleIncoming(i);
    }

    private boolean handleIncoming(Intent i) {
        if (i == null || i.getAction() == null) return false;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        else if (Intent.ACTION_SEND.equals(i.getAction()))
            u = (Uri) i.getParcelableExtra(Intent.EXTRA_STREAM);
        if (u == null) return false;

        try {
            getContentResolver().takePersistableUriPermission(u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Probe.log("external file: persistent permission granted");
        } catch (Exception e) {
            Probe.log("external file: permission for this session only");
        }

        if (tree == null) {
            String saved = store.root();
            if (saved != null) tree = Uri.parse(saved);
        }
        if (tree != null && adoptIntoTree(u)) return true;

        Folder.Item it = new Folder.Item();
        it.docId = u.toString();
        it.name  = queryName(u);
        it.mime  = getContentResolver().getType(u);
        it.dir   = false;

        external = it;
        btnUp.setVisibility(View.VISIBLE);
        list.setVisibility(View.VISIBLE);
        List<Folder.Item> one = new ArrayList<>();
        one.add(it);
        current = one;
        rows.set(one, null);
        tvFolder.setText(I18n.t(I18n.OUTSIDE));

        PlayerService s = PlayerService.get();
        if (s != null) { s.setQueue(null, "внешний", I18n.t(I18n.OUTSIDE), one); s.open(it.docId, true); }
        else pendingExternal = true;
        Probe.log("external file accepted: " + it.name + "  MIME=" + it.mime);
        return true;
    }

    private boolean adoptIntoTree(Uri u) {
        String rid = Folder.rootId(tree);
        if (rid == null) return false;

        String docId = null;
        try { docId = DocumentsContract.getDocumentId(u); }
        catch (Exception e) { Probe.log("document id unreadable: " + e.getClass().getSimpleName()); }

        if (docId == null || !docId.startsWith(rid)) {

            String want = queryName(u);
            long size = querySize(u);
            String found = findInTree(rid, want, size, 0);
            if (found == null) {
                Probe.log("not found in the tree: " + want);
                return false;
            }
            Probe.log("matched by name: " + found);
            docId = found;
        }
        int cut = docId.lastIndexOf('/');
        final String parent = (cut > rid.length() - 1) ? docId.substring(0, cut) : rid;
        final String target = docId;

        String nm = queryName(u);
        if (!Folder.isMp4(nm, getContentResolver().getType(u))) {
            filterMp4 = false;
            paintChip();
        }

        pathIds.clear(); pathNames.clear();
        pathIds.add(rid);
        pathNames.add(Folder.nameOf(this, tree, rid));
        if (!parent.equals(rid)) {
            pathIds.add(parent);
            pathNames.add(Folder.nameOf(this, tree, parent));
        }
        store.folder(parent);
        external = null;
        Probe.log("file adopted into the tree: " + target);

        final String pname = folderName();
        final boolean f = filterMp4;
        new Thread(new Runnable() { @Override public void run() {
            final List<Folder.Item> items = Folder.list(MainActivity.this, tree, parent, f,
                    store.sortMode(), store.sortDesc(), store);
            runOnUiThread(new Runnable() { @Override public void run() {
                current = items;
                rows.set(items, null);
                tvFolder.setText(pname);
                btnUp.setVisibility(pathIds.size() > 1 ? View.VISIBLE : View.GONE);
                list.setVisibility(View.VISIBLE);
                PlayerService s = PlayerService.get();
                if (s != null) { s.setQueue(tree, parent, pname, items); s.open(target, true); }
                else { pendingTarget = target; }
                rows.playing(target);
                refreshPanel();
            }});
        }}).start();
        return true;
    }

    private long querySize(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (i >= 0 && !c.isNull(i)) return c.getLong(i);
            }
        } catch (Exception ignored) {
        } finally { if (c != null) try { c.close(); } catch (Exception ignored) {} }
        return 0;
    }

    private String findInTree(String parent, String name, long size, int depth) {
        if (name == null || depth > 3) return null;
        List<Folder.Item> items = Folder.list(this, tree, parent, false,
                Folder.SORT_NAME, false, store);
        List<String> dirs = new ArrayList<>();
        for (Folder.Item it : items) {
            if (it.dir) { dirs.add(it.docId); continue; }
            if (name.equals(it.name)) return it.docId;
            if (size > 0 && it.size == size) return it.docId;
        }
        for (String d : dirs) {
            String r = findInTree(d, name, size, depth + 1);
            if (r != null) return r;
        }
        return null;
    }

    private String queryName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, null, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String n = c.getString(idx);
                    if (n != null) return n;
                }
            }
        } catch (Exception ignored) {
        } finally { if (c != null) try { c.close(); } catch (Exception ignored) {} }
        String p = u.getLastPathSegment();
        return p == null ? "файл" : p;
    }

    @Override protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, PlayerService.class);
        startService(i);
        bindService(i, conn, Context.BIND_AUTO_CREATE);
    }

    @Override protected void onResume() {
        super.onResume();
        syncPlaying();
        paintPhones();
        refreshPanel();
        h.post(tick);
    }

    private void paintPhones() {
        if (phones == null) return;
        Audio.Out o = Audio.current(this);
        if (!o.any()) { phones.setVisibility(View.GONE); batt.setVisibility(View.GONE); return; }
        phones.setVisibility(View.VISIBLE);
        phones.setImageDrawable(new Icon(Icon.HEADSET, o.bluetooth ? Ui.ACCENT : Ui.TEXT_2));
        String l = Audio.label(o);
        if (l.isEmpty()) {

            batt.setVisibility(View.GONE);
            phones.setVisibility(View.GONE);
        }
        else {
            batt.setText(l);
            batt.setTextColor(o.battery <= 15 ? Ui.ALARM : Ui.TEXT_2);
            batt.setVisibility(View.VISIBLE);
        }
    }

    private void syncPlaying() {
        PlayerService s = PlayerService.get();
        if (s == null || rows == null) return;
        Folder.Item cur = s.current();
        rows.playing(cur == null ? null : cur.docId);
    }

    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }

    @Override protected void onStop() {
        super.onStop();
        if (bound) { try { unbindService(conn); } catch (Exception ignored) {} bound = false; }
        PlayerService s = PlayerService.get();
        if (s != null) s.watch(null);
    }

    @Override protected void onDestroy() {
        if (rows != null) rows.shutdown();
        super.onDestroy();
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            bound = true;
            PlayerService s = ((PlayerService.LocalBinder) b).svc();
            s.watch(MainActivity.this);
            if (pendingTarget != null) {
                String t = pendingTarget; pendingTarget = null;
                s.setQueue(tree, folderId(), folderName(), current);
                s.open(t, true);
            } else if (pendingExternal && external != null) {
                pendingExternal = false;
                s.setQueue(null, "внешний", I18n.t(I18n.OUTSIDE), current);
                s.open(external.docId, true);
            } else if (!s.hasQueue() && tree != null) {
                s.setQueue(tree, folderId(), folderName(), current);
            }
            syncPlaying();
            refreshPanel();
        }
        @Override public void onServiceDisconnected(ComponentName n) { bound = false; }
    };

    @Override public void onPlayerChanged() { refreshPanel(); refreshList(); }

    public void pickFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                Uri hint = DocumentsContract.buildDocumentUri(AUTHORITY, TARGET_DOC);
                i.putExtra(DocumentsContract.EXTRA_INITIAL_URI, hint);
            } catch (Exception e) { Probe.log("EXTRA_INITIAL_URI refused: " + e.getMessage()); }
        }
        try { startActivityForResult(i, REQ_TREE); }
        catch (Exception e) { toast(I18n.t(I18n.NO_FOLDER)); }
    }

    @Override public void onRequestPermissionsResult(int req, String[] p, int[] r) {
        super.onRequestPermissionsResult(req, p, r);
        for (int i = 0; i < p.length && i < r.length; i++)
            Probe.log("permission " + p[i].substring(p[i].lastIndexOf('.') + 1)
                    + (r[i] == android.content.pm.PackageManager.PERMISSION_GRANTED
                       ? " granted" : " refused"));
        paintPhones();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE || res != RESULT_OK || data == null || data.getData() == null) return;
        Uri picked = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(picked,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) { Probe.log("takePersistableUriPermission failed: " + e.getMessage()); }

        tree = picked;
        store.root(picked.toString());
        pathIds.clear(); pathNames.clear();
        String rid = Folder.rootId(tree);
        pathIds.add(rid);
        pathNames.add(Folder.nameOf(this, tree, rid));
        store.folder(rid);
        Probe.log("root accepted: " + picked);
        refreshList();
    }

    private boolean stillPermitted(Uri t) {
        try {
            for (UriPermission p : getContentResolver().getPersistedUriPermissions())
                if (p.getUri().equals(t) && p.isReadPermission()) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void restorePath() {
        String rid = Folder.rootId(tree);
        pathIds.clear(); pathNames.clear();
        pathIds.add(rid);
        pathNames.add(Folder.nameOf(this, tree, rid));
        String last = store.folder();
        if (last != null && !last.equals(rid)) {
            pathIds.add(last);
            pathNames.add(Folder.nameOf(this, tree, last));
        }
    }

    public void goRoot() {
        while (pathIds.size() > 1) { pathIds.remove(pathIds.size() - 1); pathNames.remove(pathNames.size() - 1); }
        store.folder(folderId());
        refreshList();
    }

    private String folderId()   { return pathIds.isEmpty() ? null : pathIds.get(pathIds.size() - 1); }
    private String folderName() { return pathNames.isEmpty() ? "" : pathNames.get(pathNames.size() - 1); }

    public void setSort(int mode) {
        if (store.sortMode() == mode) store.sortDesc(!store.sortDesc());
        else { store.sortMode(mode); store.sortDesc(false); }
        refreshList();
    }

    public boolean markUsable(Store.Mark m) {
        if (m.tree == null) return true;
        try { return stillPermitted(Uri.parse(m.tree)); } catch (Exception e) { return false; }
    }

    public String treeUri() { return tree == null ? null : tree.toString(); }

    public String folderIdPublic()   { return folderId(); }
    public String folderNamePublic() { return folderName(); }
    public void   refreshChips()     { buildChips(); }

    public void openMark(Store.Mark m) {
        if (tree == null || m == null || m.docId == null) {
            Probe.log("bookmark empty or no tree");
            return;
        }
        Probe.log("jumping to bookmark: " + m.name + "  id=" + m.docId
                + "  дерево=" + (m.tree == null ? "то же" : m.tree));

        if (m.tree != null && !m.tree.equals(tree.toString())) {
            Uri other = Uri.parse(m.tree);
            if (!stillPermitted(other)) {
                Probe.log("access to the bookmark's tree is lost");
                toast(I18n.t(I18n.NO_FOLDER));
                return;
            }
            tree = other;
            store.root(m.tree);
            Probe.log("tree switched to " + m.tree);
        }
        external = null;
        String rid = Folder.rootId(tree);
        pathIds.clear(); pathNames.clear();
        pathIds.add(rid);
        pathNames.add(Folder.nameOf(this, tree, rid));
        if (!m.docId.equals(rid)) { pathIds.add(m.docId); pathNames.add(m.name); }
        store.folder(m.docId);

        final String fid = m.docId;
        final String fname = m.name;
        final boolean f = filterMp4;
        new Thread(new Runnable() { @Override public void run() {
            final List<Folder.Item> items = Folder.list(MainActivity.this, tree, fid, f,
                    store.sortMode(), store.sortDesc(), store);
            Probe.log("bookmark returned items: " + items.size());
            runOnUiThread(new Runnable() { @Override public void run() {
                current = items;
                rows.set(items, buildCont(items, fid));
                tvFolder.setText(fname);
                buildChips();
                btnUp.setVisibility(pathIds.size() > 1 ? View.VISIBLE : View.GONE);
                list.setVisibility(View.VISIBLE);
                list.setSelection(0);
                PlayerService s = PlayerService.get();
                if (s != null && fid.equals(s.folderId())) s.setQueue(tree, fid, fname, items);
            }});
        }}).start();
    }

    public Uri tree()   { return tree; }
    public Store store(){ return store; }

    public void refreshList() {
        if (external != null) { rows.notifyDataSetChanged(); return; }
        if (tree == null || folderId() == null) return;
        final String fid = folderId();
        final boolean f = filterMp4;
        new Thread(new Runnable() { @Override public void run() {
            final List<Folder.Item> items = Folder.list(MainActivity.this, tree, fid, f,
                    store.sortMode(), store.sortDesc(), store);

            int hidden = 0;
            if (f) {
                int all = 0, shown = 0;
                for (Folder.Item x : Folder.list(MainActivity.this, tree, fid, false,
                        Folder.SORT_NAME, false, store)) if (!x.dir) all++;
                for (Folder.Item x : items) if (!x.dir) shown++;
                hidden = all - shown;
            }
            final int hid = hidden;
            final Rows.Cont cont = buildCont(items, fid);
            runOnUiThread(new Runnable() { @Override public void run() {
                current = items;
                rows.set(items, cont);
                PlayerService s = PlayerService.get();
                if (s != null) {
                    if (fid.equals(s.folderId())) s.setQueue(tree, fid, folderName(), items);
                    Folder.Item cur = s.current();
                    rows.playing(cur == null ? null : cur.docId);
                }
                hiddenByFilter = hid;
                tvFolder.setText(folderName());
                buildChips();
                paintEmpty();
                btnUp.setVisibility(pathIds.size() > 1 ? View.VISIBLE : View.GONE);
                list.setVisibility(View.VISIBLE);
            }});
        }}).start();
    }

    private void paintEmpty() {
        if (emptyView == null || rows == null) return;
        if (rows.getCount() > 0) { emptyView.setVisibility(View.GONE); return; }
        emptyView.setVisibility(View.VISIBLE);
        if (hiddenByFilter > 0) {
            emptyView.setText(I18n.t(I18n.HIDDEN_1) + " " + hiddenByFilter + " "
                    + I18n.t(I18n.HIDDEN_2) + "\n\n" + I18n.t(I18n.HIDDEN_3));
            emptyView.setTextColor(Ui.ACCENT);
        } else {
            emptyView.setText(I18n.t(I18n.EMPTY_FOLDER));
            emptyView.setTextColor(Ui.TEXT_OFF);
        }
    }

    private Rows.Cont buildCont(List<Folder.Item> items, String fid) {
        String doc = store.lastDoc();
        if (doc == null) return null;

        String lt = store.lastTree();
        if (lt != null && tree != null && !lt.equals(tree.toString())) return null;

        if (!exists(doc)) {
            if (doc.startsWith("content://") || doc.startsWith("file://"))
                Probe.log("external file no longer reachable, position kept");
            else { store.save(doc, 0, 0); Probe.log("the Continue file is gone"); }
            return null;
        }
        long pos = store.position(doc);
        if (pos <= Store.EDGE_HEAD) return null;
        long dur = store.duration(doc);
        if (dur > 0 && pos >= dur - Store.EDGE_TAIL) return null;

        PlayerService s = PlayerService.get();
        if (s != null && s.isPlaying()) {
            Folder.Item c = s.current();
            if (c != null && c.docId.equals(doc)) return null;
        }
        Rows.Cont k = new Rows.Cont();
        k.docId = doc;
        k.name = store.lastName() == null ? doc : store.lastName();
        k.folderId = store.lastFolder();
        k.folderName = store.lastFolderName() == null ? "" : store.lastFolderName();
        k.position = pos;
        k.duration = dur;
        k.elsewhere = k.folderId != null && !k.folderId.equals(fid);
        return k;
    }

    private boolean exists(String docId) {
        if (tree == null) return false;
        Cursor c = null;
        try {
            c = getContentResolver().query(Folder.fileUri(tree, docId),
                    new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null);
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally { if (c != null) try { c.close(); } catch (Exception ignored) {} }
    }

    private void onRowTap(int i) {
        Object o = rows.getItem(i);
        if (o instanceof Rows.Cont) {
            Rows.Cont k = (Rows.Cont) o;
            if (k.elsewhere && k.folderId != null) {
                enter(k.folderId, k.folderName, new Runnable() { @Override public void run() { startDoc(k.docId); } });
            } else startDoc(k.docId);
            return;
        }
        Folder.Item it = (Folder.Item) o;
        if (it.dir) enter(it.docId, it.name, null);
        else startDoc(it.docId);
    }

    private void enter(String docId, String name, final Runnable then) {
        pathIds.add(docId);
        pathNames.add(name);
        store.folder(docId);
        final String fid = docId;
        final boolean f = filterMp4;
        new Thread(new Runnable() { @Override public void run() {
            final List<Folder.Item> items = Folder.list(MainActivity.this, tree, fid, f,
                    store.sortMode(), store.sortDesc(), store);
            runOnUiThread(new Runnable() { @Override public void run() {
                current = items;
                rows.set(items, buildCont(items, fid));
                hiddenByFilter = 0;
                tvFolder.setText(name);
                buildChips();
                paintEmpty();
                btnUp.setVisibility(View.VISIBLE);
                PlayerService s = PlayerService.get();
                if (s != null) s.setQueue(tree, fid, name, items);
                if (then != null) then.run();
            }});
        }}).start();
    }

    private void startDoc(String docId) {
        PlayerService s = PlayerService.get();
        if (s == null) { toast(I18n.t(I18n.NO_PLAYER)); return; }
        if (!exists(docId)) {
            toast(I18n.t(I18n.GONE));
            Probe.log("tried to open a vanished file");
            store.save(docId, 0, 0);
            refreshList();
            return;
        }
        s.setQueue(tree, folderId(), folderName(), current);
        s.open(docId, true);
        rows.playing(docId);
        refreshPanel();
    }

    public void playFromStart(Folder.Item it) {
        store.save(it.docId, 0, store.duration(it.docId));
        startDoc(it.docId);
    }

    public void jumpToRecent(Store.Recent r) {
        if (r.folderId != null && !r.folderId.isEmpty() && !r.folderId.equals(folderId()))
            enter(r.folderId, r.folderName, new Runnable() { @Override public void run() { startDoc(r.docId); } });
        else startDoc(r.docId);
    }

    public String probeReport() {
        StringBuilder L = new StringBuilder();

        L.append(Probe.head("hark " + About.VERSION)).append('\n');
        L.append(Probe.line("android", new Probe.Fn() { public String get() {
            return Build.VERSION.RELEASE + "   api " + Build.VERSION.SDK_INT; }})).append('\n');
        L.append(Probe.line("device", new Probe.Fn() { public String get() {
            return Build.MANUFACTURER + " " + Build.MODEL; }})).append('\n');
        L.append(Probe.line("language", new Probe.Fn() { public String get() {
            return I18n.code() + (I18n.rtl() ? "   right to left" : ""); }})).append('\n');
        L.append('\n');

        L.append(Probe.line("tree", new Probe.Fn() { public String get() {
            return tree == null ? "none" : tree.toString(); }})).append('\n');
        L.append(Probe.line("permission", new Probe.Fn() { public String get() {
            return (tree != null && stillPermitted(tree)) ? "alive" : "lost"; }})).append('\n');
        L.append(Probe.line("permissions held", new Probe.Fn() { public String get() {
            return String.valueOf(getContentResolver().getPersistedUriPermissions().size()); }})).append('\n');
        L.append(Probe.line("folder", new Probe.Fn() { public String get() {
            return folderName() + "   id " + folderId(); }})).append('\n');
        L.append(Probe.line("depth", new Probe.Fn() { public String get() {
            return String.valueOf(pathIds.size()); }})).append('\n');
        L.append(Probe.line("bookmarks", new Probe.Fn() { public String get() {
            int alive = 0;
            java.util.List<Store.Mark> ms = store.marks();
            for (Store.Mark m : ms) if (markUsable(m)) alive++;
            return ms.size() + " kept, " + alive + " reachable"; }})).append('\n');
        L.append('\n');

        L.append(Probe.line("filter", new Probe.Fn() { public String get() {
            return (filterMp4 ? "mp4 family" : "everything audible")
                    + (store.rememberFilter() ? "   remembered" : "   resets on launch"); }})).append('\n');
        L.append(Probe.line("sort", new Probe.Fn() { public String get() {
            return Folder.sortLabel(store.sortMode()) + (store.sortDesc() ? "   reversed" : ""); }})).append('\n');
        L.append(Probe.line("list", new Probe.Fn() { public String get() {
            return "rows " + (rows == null ? -1 : rows.getCount())
                    + "   height " + (list == null ? -1 : list.getHeight())
                    + (external != null ? "   external file" : ""); }})).append('\n');
        L.append('\n');

        L.append(Probe.line("service", new Probe.Fn() { public String get() {
            return PlayerService.get() == null ? "not running" : "alive"; }})).append('\n');
        L.append(Probe.line("playing", new Probe.Fn() { public String get() {
            PlayerService s = PlayerService.get();
            if (s == null) return "\u2014";
            Folder.Item c = s.current();
            return (c == null ? "nothing loaded" : c.name); }})).append('\n');
        L.append(Probe.line("position", new Probe.Fn() { public String get() {
            PlayerService s = PlayerService.get();
            if (s == null) return "\u2014";
            return Ui.time(s.position()) + " of " + Ui.time(s.duration())
                    + (s.isPlaying() ? "   running" : "   paused"); }})).append('\n');
        L.append(Probe.line("speed", new Probe.Fn() { public String get() {
            return store.speed() + "\u00D7"; }})).append('\n');
        L.append(Probe.line("sleep timer", new Probe.Fn() { public String get() {
            PlayerService s = PlayerService.get();
            if (s == null) return "\u2014";
            if (s.sleepTilEnd()) return "until the file ends";
            long left = s.sleepLeft();
            return left > 0 ? Ui.time(left) + " left" : "off"; }})).append('\n');
        L.append(Probe.line("widget backdrop", new Probe.Fn() { public String get() {
            return (store.widgetShade() * 25) + " %"; }})).append('\n');
        L.append('\n');

        L.append(Probe.head("mime as the firmware reports it")).append('\n');
        int n = 0;
        for (Folder.Item it : current) {
            if (it.dir) continue;
            L.append(it.name).append("\n    \u2192  ")
             .append(it.mime == null ? "null" : it.mime)
             .append(it.duration > 0 ? "   " + Ui.time(it.duration) : "")
             .append('\n');
            if (++n >= 30) { L.append("\u2026\n"); break; }
        }
        if (n == 0) L.append("no files here\n");
        L.append('\n');

        L.append(Probe.tail());
        return L.toString();
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public void refreshPanel() {
        PlayerService s = PlayerService.get();
        Folder.Item it = (s == null) ? null : s.current();
        boolean playing = s != null && s.isPlaying();

        loadArt(it);

        if (it == null) {
            pTitle.setText(I18n.t(I18n.NOTHING));
            pTitle.setTextColor(Ui.TEXT_OFF);
            pTime.setText("");
            seek.set(0f);
            pPlay.setImageDrawable(new Icon(Icon.PLAY, Ui.TEXT_OFF));
            pSpeed.setTextColor(Ui.TEXT_OFF);
            pSleep.setTextColor(Ui.TEXT_OFF);
            return;
        }
        pTitle.setText(Ui.bare(it.name));
        pTitle.setTextColor(Ui.TEXT);
        long p = s.position(), d = s.duration();
        pTime.setText(Ui.time(p));
        seek.set(d > 0 ? (float) p / d : 0f);
        pPlay.setImageDrawable(new Icon(playing ? Icon.PAUSE : Icon.PLAY, Ui.ACCENT));

        float psp = store.speed();
        pSpeed.setText((psp == 1.0f ? "1.0" : String.valueOf(psp)) + "\u00D7");
        pSpeed.setTextColor(psp == 1.0f ? Ui.TEXT_2 : Ui.ACCENT);
        long pleft = s.sleepLeft();
        if (s.sleepTilEnd())  { pSleep.setText(I18n.t(I18n.TILL_END_SHORT)); pSleep.setTextColor(Ui.ACCENT); }
        else if (pleft > 0)   { pSleep.setText(Ui.time(pleft)); pSleep.setTextColor(Ui.ACCENT); }
        else                  { pSleep.setText(I18n.t(I18n.SLEEP).toLowerCase()); pSleep.setTextColor(Ui.TEXT_2); }

        if (full) {
            fTitle.setText(Ui.bare(it.name));
            fTime.setText(Ui.time(p));
            fLeft.setText(d > 0 ? I18n.t(I18n.LEFT) + " " + Ui.time(d - p) : "");
            fSeek.set(d > 0 ? (float) p / d : 0f);
            fPlay.setImageDrawable(new Icon(playing ? Icon.PAUSE : Icon.PLAY, Ui.ACCENT));
            float sp = store.speed();
            fSpeed.setText((sp == 1.0f ? "1.0" : String.valueOf(sp)) + "×");
            fSpeed.setTextColor(sp == 1.0f ? Ui.TEXT_2 : Ui.ACCENT);
            long left = s.sleepLeft();
            if (s.sleepTilEnd()) { fSleep.setText(I18n.t(I18n.TILL_END_SHORT)); fSleep.setTextColor(Ui.ACCENT); }
            else if (left > 0)   { fSleep.setText(Ui.time(left)); fSleep.setTextColor(Ui.ACCENT); }
            else                 { fSleep.setText(I18n.t(I18n.SLEEP).toLowerCase()); fSleep.setTextColor(Ui.TEXT_2); }
        }
    }

    private void loadArt(final Folder.Item it) {
        if (it == null) { pArt.setVisibility(View.GONE); artFor = null; return; }
        if (it.docId.equals(artFor)) return;
        artFor = it.docId;
        final PlayerService s = PlayerService.get();
        final String fid = (s == null) ? folderId() : s.folderId();
        final Uri t = tree;
        final int px = Ui.dp(this, 62);
        new Thread(new Runnable() { @Override public void run() {
            final android.graphics.Bitmap b = Cover.get(MainActivity.this, t, it.docId, fid, px);
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!it.docId.equals(artFor)) return;
                if (b == null) { pArt.setVisibility(View.GONE); }
                else { pArt.setImageBitmap(b); pArt.setVisibility(View.VISIBLE); }
            }});
        }}).start();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            PlayerService s = PlayerService.get();
            if (s != null && s.isPlaying()) refreshPanel();
            if (++ticks % 20 == 0) paintPhones();
            h.postDelayed(this, 500);
        }
    };

    private void buildUi() {
        getWindow().setStatusBarColor(Ui.SURFACE);
        getWindow().setNavigationBarColor(Ui.SURFACE);

        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Ui.BG);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        col.addView(buildHeader());

        list = new ListView(this);
        list.setBackgroundColor(Ui.BG);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        list.setCacheColorHint(0);
        list.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        rows = new Rows(this, store);
        list.setAdapter(rows);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int i, long id) { onRowTap(i); }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int i, long id) {
                Object o = rows.getItem(i);
                if (o instanceof Folder.Item && !((Folder.Item) o).dir) {
                    Sheets.file(MainActivity.this, (Folder.Item) o);
                    return true;
                }
                return false;
            }
        });
        col.addView(list);
        col.addView(buildPanel());
        rootFrame.addView(col);

        emptyView = new TextView(this);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(15f);
        emptyView.setTextColor(Ui.TEXT_2);
        emptyView.setPadding(Ui.dp(this, 36), 0, Ui.dp(this, 36), Ui.dp(this, 80));
        emptyView.setVisibility(View.GONE);
        emptyView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (hiddenByFilter > 0) {
                    filterMp4 = false;
                    store.filterMp4(false);
                    paintChip();
                    refreshList();
                }
            }
        });
        rootFrame.addView(emptyView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scrubHint = new TextView(this);
        scrubHint.setTextSize(38f);
        scrubHint.setTypeface(Typeface.MONOSPACE);
        scrubHint.setTextColor(Ui.TEXT);
        scrubHint.setGravity(Gravity.CENTER);
        scrubHint.setBackgroundColor(0xE6171512);
        scrubHint.setVisibility(View.GONE);
        rootFrame.addView(scrubHint, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 96), Gravity.CENTER));

        buildFull();
        rootFrame.addView(fullView);
        fullView.setVisibility(View.GONE);

        setContentView(rootFrame);
    }

    private View buildHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Ui.SURFACE);
        head.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6));
        head.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60)));

        btnUp = new ImageView(this);
        btnUp.setImageDrawable(new Icon(Icon.UP, Ui.TEXT_2));
        int bs = Ui.dp(this, 30);
        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(bs, bs);
        up.rightMargin = Ui.dp(this, 4);
        btnUp.setLayoutParams(up);
        btnUp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { up(); }
        });
        btnUp.setVisibility(View.GONE);
        head.addView(btnUp);

        tvFolder = new TextView(this);
        tvFolder.setTextSize(20f);
        tvFolder.setTypeface(Typeface.DEFAULT_BOLD);
        tvFolder.setTextColor(Ui.TEXT);
        tvFolder.setSingleLine(true);
        tvFolder.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvFolder.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tvFolder);

        chip = pill("MP4", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                filterMp4 = !filterMp4;
                store.filterMp4(filterMp4);
                paintChip();
                refreshList();
            }
        });
        chip.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Sheets.probe(MainActivity.this); return true; }
        });
        head.addView(chip);
        paintChip();

        phones = new ImageView(this);
        phones.setImageDrawable(new Icon(Icon.HEADSET, Ui.ACCENT));
        int hs = Ui.dp(this, 24);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(hs, hs);
        hp.rightMargin = Ui.dp(this, 3);
        phones.setLayoutParams(hp);
        phones.setVisibility(View.GONE);
        head.addView(phones);

        batt = new TextView(this);
        batt.setTextSize(11f);
        batt.setTypeface(Typeface.DEFAULT_BOLD);
        batt.setTextColor(Ui.TEXT_2);
        batt.setPadding(0, 0, Ui.dp(this, 8), 0);
        batt.setVisibility(View.GONE);
        head.addView(batt);

        star = pill("\u2605", false, new View.OnClickListener() {
            @Override public void onClick(View v) { Sheets.marks(MainActivity.this); }
        });
        head.addView(star);

        gear = new ImageView(this);
        gear.setImageDrawable(new Icon(Icon.LOGO, Ui.ACCENT));
        int gs = Ui.dp(this, 42);
        gear.setLayoutParams(new LinearLayout.LayoutParams(gs, gs));
        gear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { About.menu(MainActivity.this); }
        });
        head.addView(gear);

        return head;
    }

    private void buildChips() {
        if (star == null) return;
        boolean lit = folderId() != null && store.marked(folderId());
        star.setTextColor(lit ? Ui.ACCENT : Ui.TEXT_2);
        star.setBackground(pillBg(lit));
    }

    private TextView pill(String text, boolean lit, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setTextColor(lit ? Ui.ACCENT : Ui.TEXT_2);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        t.setBackground(pillBg(lit));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Ui.dp(this, 8);
        t.setLayoutParams(lp);
        t.setOnClickListener(l);
        return t;
    }

    private android.graphics.drawable.GradientDrawable pillBg(boolean lit) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(Ui.dp(this, 14));
        g.setStroke(Ui.dp(this, 1), lit ? Ui.ACCENT : Ui.LINE);
        g.setColor(0x00000000);
        return g;
    }

    private void paintChip() {
        chip.setText(filterMp4 ? "MP4" : I18n.t(I18n.ALL));
        chip.setTextColor(filterMp4 ? Ui.ACCENT : Ui.TEXT_2);
        chip.setBackground(pillBg(filterMp4));
    }

    private View buildPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.SURFACE);
        box.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        seek = new Bar(this);
        seek.knob(true);
        seek.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));
        box.addView(seek);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 10));
        panel.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout topLine = new LinearLayout(this);
        topLine.setOrientation(LinearLayout.HORIZONTAL);
        topLine.setGravity(Gravity.CENTER_VERTICAL);
        topLine.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pTitle = new TextView(this);
        pTitle.setTextSize(14f);
        pTitle.setSingleLine(true);
        pTitle.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        pTitle.setTextColor(Ui.TEXT_OFF);
        pTitle.setText(I18n.t(I18n.NOTHING));
        pTitle.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        topLine.addView(pTitle);

        pTime = new TextView(this);
        pTime.setTextSize(13f);
        pTime.setTypeface(Typeface.MONOSPACE);
        pTime.setTextColor(Ui.TEXT_2);
        pTime.setPadding(Ui.dp(this, 10), 0, 0, 0);
        pTime.setLongClickable(true);
        pTime.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { Sheets.jump(MainActivity.this); return true; }
        });
        topLine.addView(pTime);
        panel.addView(topLine);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER_VERTICAL);
        ctrl.setPadding(0, Ui.dp(this, 6), 0, 0);
        ctrl.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        pSpeed = chipText("1.0\u00D7", new View.OnClickListener() {
            @Override public void onClick(View v) { Sheets.speed(MainActivity.this); }
        });
        ctrl.addView(pSpeed);

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.HORIZONTAL);
        mid.setGravity(Gravity.CENTER);
        mid.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int cs = Ui.dp(this, 44);
        pBack = iconBtn(Icon.BACK, "15", cs, new View.OnClickListener() {
            @Override public void onClick(View v) { svc(PlayerService.ACT_BACK); } });
        pPlay = iconBtn(Icon.PLAY, null, Ui.dp(this, 52), new View.OnClickListener() {
            @Override public void onClick(View v) { svc(PlayerService.ACT_TOGGLE); } });
        pFwd  = iconBtn(Icon.FWD,  "30", cs, new View.OnClickListener() {
            @Override public void onClick(View v) { svc(PlayerService.ACT_FWD); } });
        mid.addView(pBack); mid.addView(pPlay); mid.addView(pFwd);
        ctrl.addView(mid);

        pSleep = chipText(I18n.t(I18n.SLEEP).toLowerCase(), new View.OnClickListener() {
            @Override public void onClick(View v) { Sheets.sleep(MainActivity.this); }
        });
        ctrl.addView(pSleep);

        panel.addView(ctrl);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER_VERTICAL);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wrap.addView(panel);

        pArt = new ImageView(this);
        int as = Ui.dp(this, 62);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(as, as);
        ap.rightMargin = Ui.dp(this, 14);
        pArt.setLayoutParams(ap);
        pArt.setVisibility(View.GONE);
        pArt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFull(true); }
        });
        wrap.addView(pArt);

        box.addView(wrap);

        panel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showFull(true); }
        });
        attachScrub(panel, seek, false);
        return box;
    }

    private ImageView iconBtn(int kind, String label, int size, View.OnClickListener l) {
        ImageView v = new ImageView(this);
        v.setImageDrawable(new Icon(kind, Ui.TEXT, label));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.rightMargin = Ui.dp(this, 8);
        v.setLayoutParams(lp);
        v.setOnClickListener(l);
        v.setBackground(ripple());
        return v;
    }

    private android.graphics.drawable.Drawable ripple() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(0);
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Ui.PRESSED), null, g);
    }

    private void svc(String action) {
        PlayerService s = PlayerService.get();
        if (s == null) return;
        switch (action) {
            case PlayerService.ACT_TOGGLE: s.toggle(); break;
            case PlayerService.ACT_BACK:   s.nudge(-PlayerService.BACK_MS); break;
            case PlayerService.ACT_FWD:    s.nudge(+PlayerService.FWD_MS);  break;
        }
        refreshPanel();
    }

    private static float scrubFrac(float x, int width) {
        float w = Math.max(1, width);
        float inset = w / 6f;
        float f = (x - inset) / Math.max(1f, w - inset * 2f);
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }

    private void attachScrub(View zone, final Bar bar, final boolean big) {
        zone.setOnTouchListener(new View.OnTouchListener() {
            boolean dragging = false;
            float startX;
            @Override public boolean onTouch(View v, MotionEvent e) {
                PlayerService s = PlayerService.get();
                if (s == null || s.duration() <= 0) return false;
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getX(); dragging = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!dragging && Math.abs(e.getX() - startX) > Ui.dp(MainActivity.this, 8))
                            dragging = true;
                        if (dragging) {
                            float f = scrubFrac(e.getX(), v.getWidth());
                            bar.set(f);
                            long t = (long) (f * s.duration());
                            scrubHint.setText(Ui.time(t));
                            scrubHint.setVisibility(View.VISIBLE);
                            if (big) { fTime.setText(Ui.time(t)); fLeft.setText(I18n.t(I18n.LEFT) + " " + Ui.time(s.duration() - t)); }
                            else pTime.setText(Ui.time(t));
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        scrubHint.setVisibility(View.GONE);
                        float f = scrubFrac(e.getX(), v.getWidth());
                        if (dragging) {
                            s.seekTo((long) (f * s.duration()));
                            refreshPanel();
                        } else if (big) {

                            s.seekTo((long) (f * s.duration()));
                            refreshPanel();
                        } else v.performClick();
                        dragging = false;
                        return true;
                }
                return false;
            }
        });
    }

    private void buildFull() {
        fullView = new LinearLayout(this);
        fullView.setOrientation(LinearLayout.VERTICAL);
        fullView.setBackgroundColor(Ui.BG);
        fullView.setGravity(Gravity.CENTER);
        fullView.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        fullView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fTitle = new TextView(this);
        fTitle.setTextSize(18f);
        fTitle.setTextColor(Ui.TEXT);
        fTitle.setGravity(Gravity.CENTER);
        fTitle.setPadding(0, 0, 0, Ui.dp(this, 28));
        fullView.addView(fTitle);

        fTime = new TextView(this);
        fTime.setTextSize(44f);
        fTime.setTypeface(Typeface.MONOSPACE);
        fTime.setTextColor(Ui.TEXT);
        fTime.setGravity(Gravity.CENTER);
        fTime.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { Sheets.jump(MainActivity.this); return true; } });
        fullView.addView(fTime);

        fLeft = new TextView(this);
        fLeft.setTextSize(13f);
        fLeft.setTextColor(Ui.TEXT_2);
        fLeft.setGravity(Gravity.CENTER);
        fLeft.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 24));
        fullView.addView(fLeft);

        LinearLayout scrub = new LinearLayout(this);
        scrub.setOrientation(LinearLayout.VERTICAL);
        scrub.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        fSeek = new Bar(this);
        fSeek.knob(true);
        fSeek.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 5)));
        scrub.addView(fSeek);
        fullView.addView(scrub);
        attachScrub(scrub, fSeek, true);

        LinearLayout ctrl = new LinearLayout(this);
        ctrl.setOrientation(LinearLayout.HORIZONTAL);
        ctrl.setGravity(Gravity.CENTER);
        ctrl.setPadding(0, Ui.dp(this, 24), 0, 0);

        fSpeed = chipText("1.0×", new View.OnClickListener() { @Override public void onClick(View v) { Sheets.speed(MainActivity.this); } });
        ctrl.addView(fSpeed);

        fBack = iconBtn(Icon.BACK, "15", Ui.dp(this, 52), new View.OnClickListener() { @Override public void onClick(View v) { svc(PlayerService.ACT_BACK); } });
        fPlay = iconBtn(Icon.PLAY, null, Ui.dp(this, 64), new View.OnClickListener() { @Override public void onClick(View v) { svc(PlayerService.ACT_TOGGLE); } });
        fFwd  = iconBtn(Icon.FWD,  "30", Ui.dp(this, 52), new View.OnClickListener() { @Override public void onClick(View v) { svc(PlayerService.ACT_FWD); } });
        ctrl.addView(fBack); ctrl.addView(fPlay); ctrl.addView(fFwd);

        fSleep = chipText(I18n.t(I18n.SLEEP).toLowerCase(), new View.OnClickListener() { @Override public void onClick(View v) { Sheets.sleep(MainActivity.this); } });
        ctrl.addView(fSleep);

        fullView.addView(ctrl);

        TextView down = new TextView(this);
        down.setText("▾");
        down.setTextSize(20f);
        down.setTextColor(Ui.TEXT_OFF);
        down.setGravity(Gravity.CENTER);
        down.setPadding(0, Ui.dp(this, 28), 0, 0);
        down.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showFull(false); } });
        fullView.addView(down);
    }

    private TextView chipText(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(13f);
        t.setTextColor(Ui.TEXT_2);
        t.setGravity(Gravity.CENTER);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        t.setMinWidth(Ui.dp(this, 56));
        t.setOnClickListener(l);
        return t;
    }

    private void showFull(boolean on) {
        full = on;
        fullView.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) refreshPanel();
    }

    private void leaveExternal() {
        external = null;
        if (tree == null) {
            String saved = store.root();
            if (saved == null) { pickFolder(); return; }
            tree = Uri.parse(saved);
        }
        if (pathIds.isEmpty()) restorePath();
        refreshList();
    }

    private void up() {
        if (external != null) { leaveExternal(); return; }
        if (pathIds.size() <= 1) return;
        pathIds.remove(pathIds.size() - 1);
        pathNames.remove(pathNames.size() - 1);
        store.folder(folderId());
        refreshList();
    }

    @Override public void onBackPressed() {
        if (full) { showFull(false); return; }
        if (external != null) { leaveExternal(); return; }
        if (pathIds.size() > 1) { up(); return; }
        moveTaskToBack(true);
    }
}
