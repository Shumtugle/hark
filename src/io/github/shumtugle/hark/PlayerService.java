package io.github.shumtugle.hark;

import android.app.*;
import android.content.*;
import android.media.*;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.*;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Playback lives here and outlives the activity. The position is written every few seconds and
 * on every pause.
 */
public class PlayerService extends Service
        implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    public static final String ACT_PLAY   = "io.github.shumtugle.hark.PLAY";
    public static final String ACT_PAUSE  = "io.github.shumtugle.hark.PAUSE";
    public static final String ACT_TOGGLE = "io.github.shumtugle.hark.TOGGLE";
    public static final String ACT_BACK   = "io.github.shumtugle.hark.BACK";
    public static final String ACT_FWD    = "io.github.shumtugle.hark.FWD";
    public static final String ACT_NEXT   = "io.github.shumtugle.hark.NEXT";
    public static final String ACT_PREV   = "io.github.shumtugle.hark.PREV";
    public static final String ACT_STOP   = "io.github.shumtugle.hark.STOP";
    public static final String ACT_FORGET = "io.github.shumtugle.hark.FORGET";
    /** Delivered by an alarm to the widget receiver, which asks a live service to check. */
    public static final String ACT_IDLE   = "io.github.shumtugle.hark.IDLE";

    public static final long BACK_MS = 15_000L;
    public static final long FWD_MS  = 30_000L;

    private static final String CHANNEL = "hark.playback";
    private static final int    NOTE_ID = 41;
    private static final long   SAVE_EVERY = 5_000L;

    public interface Watcher { void onPlayerChanged(); }

    private static PlayerService live;
    public static PlayerService get() { return live; }

    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder { public PlayerService svc() { return PlayerService.this; } }

    private MediaPlayer mp;
    private MediaSession session;
    private AudioManager am;
    private Object focusRequest;                 // an audio focus request on API 26+
    private Store store;
    private Watcher watcher;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Uri tree;
    private String folderId, folderName;
    private final List<Folder.Item> queue = new ArrayList<>();
    private int index = -1;

    private boolean prepared = false;
    private boolean wantPlay = false;
    private boolean pausedByFocus = false;
    private boolean noisyRegistered = false;
    private boolean foreground = false;
    private boolean resumingFromPause = false;

    private long sleepAtMs = 0;                  // 0 = no timer
    private boolean sleepAtEnd = false;
    private float volume = 1f;

    // ---------- lifecycle ----------

    @Override public void onCreate() {
        super.onCreate();
        live = this;
        store = new Store(this);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        makeChannel();
        session = new MediaSession(this, "Hark");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay()  { play(); }
            @Override public void onPause() { pause(true); }
            @Override public void onStop()  { pause(true); }
            @Override public void onSkipToNext()     { next(); }
            @Override public void onSkipToPrevious() { prev(); }
            @Override public void onSeekTo(long pos) { seekTo(pos); }
            @Override public boolean onMediaButtonEvent(Intent i) {
                KeyEvent k = (KeyEvent) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (k != null && k.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (k.getKeyCode()) {
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        case KeyEvent.KEYCODE_HEADSETHOOK: toggle(); return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:  play();   return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE: pause(true); return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:  next();   return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS: prev(); return true;
                    }
                }
                return super.onMediaButtonEvent(i);
            }
        });
        session.setActive(true);
        ui.postDelayed(ticker, SAVE_EVERY);
    }

    @Override public int onStartCommand(Intent i, int flags, int startId) {
        // We may have been raised from the home screen: the system allows five seconds to
        // enter the foreground, so the notification goes up at once, before we know what to
        // play. A plain start from our own screen carries no action and needs no
        // notification: otherwise merely opening the app left a nameless one behind for good.
        if (i != null && i.getAction() != null) ensureForeground();

        // A restart by the system after the process was killed: nobody asked for anything,
        // and an idle background service has no business coming back.
        if (i == null && !isPlaying()) { stopSelf(); return START_NOT_STICKY; }

        if (i != null && i.getAction() != null) {
            String act = i.getAction();
            boolean needs = ACT_TOGGLE.equals(act) || ACT_PLAY.equals(act)
                    || ACT_BACK.equals(act) || ACT_FWD.equals(act)
                    || ACT_NEXT.equals(act) || ACT_PREV.equals(act);
            if (needs && queue.isEmpty()) {
                restoreLast(act);
                return START_STICKY;
            }
            switch (i.getAction()) {
                case ACT_TOGGLE: toggle(); break;
                case ACT_PLAY:   play();   break;
                case ACT_PAUSE:  pause(true); break;
                case ACT_BACK:   nudge(-BACK_MS); break;
                case ACT_FWD:    nudge(+FWD_MS);  break;
                case ACT_NEXT:   next(); break;
                case ACT_PREV:   prev(); break;
                case ACT_STOP:   stopEverything(); break;
                case ACT_FORGET: stopEverything(); break;
            }
        }
        return START_STICKY;
    }

    @Override public IBinder onBind(Intent i) { return binder; }

    /** A placeholder notification, so as not to be killed for silence in the first seconds. */
    private void ensureForeground() {
        if (foreground) return;
        try {
            Notification.Builder b = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_note)
                    .setContentTitle("Hark")
                    .setShowWhen(false);
            startForeground(NOTE_ID, b.build());
            foreground = true;
        } catch (Exception e) {
            Probe.log("foreground refused: " + e.getClass().getSimpleName());
        }
    }

    /**
     * The service may have been killed while paused. Then the queue is empty while someone
     * presses a widget button: raise the last file from storage.
     */
    private void restoreLast(final String action) {
        final String root = store.root();
        final String docId = store.lastDoc();
        final String folder = store.lastFolder();
        if (root == null || docId == null) {
            shutDown("nothing to restore");
            return;
        }
        Probe.log("rising after death: " + store.lastName());
        final Uri t = Uri.parse(root);
        final String fid = (folder == null) ? Folder.rootId(t) : folder;
        new Thread(new Runnable() { @Override public void run() {
            final List<Folder.Item> items = Folder.list(PlayerService.this, t, fid,
                    store.filterMp4(), store.sortMode(), store.sortDesc(), store);
            ui.post(new Runnable() { @Override public void run() {
                setQueue(t, fid, store.lastFolderName(), items);
                boolean start = !ACT_PAUSE.equals(action);
                open(docId, start);
                if (ACT_BACK.equals(action)) nudge(-BACK_MS);
                else if (ACT_FWD.equals(action)) nudge(+FWD_MS);
            }});
        }}).start();
    }

    /** Swiped away from the recent apps while paused: nothing is left to keep alive. */
    @Override public void onTaskRemoved(Intent root) {
        if (!isPlaying()) shutDown("task removed while paused");
        super.onTaskRemoved(root);
    }

    @Override public void onDestroy() {
        cancelIdle();
        saveNow();
        ui.removeCallbacks(ticker);
        unregisterNoisy();
        dropFocus();
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        if (session != null) { session.setActive(false); session.release(); session = null; }
        live = null;
        Widget.push(this);      // the widget now draws the last state from storage
        super.onDestroy();
    }

    public void watch(Watcher w) {
        watcher = w;
        // Our screen went away with nothing playing: start the idle countdown from here, or a
        // file opened and never started would keep the notification alive.
        if (w == null && !isPlaying() && idleSince == 0L) scheduleIdle();
    }
    private void changed() {
        Widget.push(this);
        if (watcher == null) return;
        ui.post(new Runnable() {
            @Override public void run() { if (watcher != null) watcher.onPlayerChanged(); }
        });
    }

    // ---------- queue ----------

    public void setQueue(Uri tree, String folderId, String folderName, List<Folder.Item> items) {
        this.tree = tree; this.folderId = folderId; this.folderName = folderName;
        queue.clear();
        for (Folder.Item it : items) if (!it.dir) queue.add(it);
    }

    public Folder.Item current() {
        return (index >= 0 && index < queue.size()) ? queue.get(index) : null;
    }

    public String folderName() { return folderName; }
    public String folderId()   { return folderId; }
    public boolean hasQueue()  { return !queue.isEmpty(); }

    /** Loads a file. start=false only prepares it and seeks to the saved position. */
    public void open(String docId, boolean start) {
        int at = -1;
        for (int i = 0; i < queue.size(); i++) if (queue.get(i).docId.equals(docId)) { at = i; break; }
        if (at < 0) return;
        openAt(at, start);
    }

    private void openAt(int at, boolean start) {
        if (at < 0 || at >= queue.size()) return;
        saveNow();
        index = at;
        Folder.Item it = queue.get(at);
        prepared = false;
        wantPlay = start;

        if (mp != null) { try { mp.reset(); } catch (Exception e) { mp.release(); mp = null; } }
        if (mp == null) mp = new MediaPlayer();
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        mp.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        try {
            mp.setDataSource(this, Folder.fileUri(tree, it.docId));
            final Folder.Item fit = it;
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override public void onPrepared(MediaPlayer p) {
                    prepared = true;
                    fit.duration = p.getDuration();
                    long at2 = store.resumeAt(fit.docId);
                    if (at2 > 0 && at2 < fit.duration) rawSeek(at2);
                    applySpeedSafely();
                    if (wantPlay) play(); else { pushNote(); changed(); }
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            Probe.log("setDataSource failed: " + e.getClass().getSimpleName() + " " + e.getMessage());
            prepared = false;
        }
        Cover.forget();
        if (tree != null) store.lastTree(tree.toString());
        store.last(it.docId, it.name, folderId, folderName);
        store.touch(it.docId, it.name, folderId, folderName);
        changed();
    }

    // ---------- controls ----------

    public void toggle() { if (isPlaying()) pause(true); else play(); }

    public void play() {
        if (mp == null || !prepared) { wantPlay = true; return; }
        if (!takeFocus()) { Probe.log("audio focus refused"); return; }
        registerNoisy();
        cancelIdle();
        if (session != null && !session.isActive()) session.setActive(true);

        // Made for speech: a pause cuts a thought mid-sentence, and resuming from the exact
        // same spot is useless. A small rewind brings back the end of the phrase, and the
        // sense with it.
        if (resumingFromPause) {
            resumingFromPause = false;
            long back = store.resumeBack();
            if (back > 0) {
                long p = position();
                if (p > back) rawSeek(p - back);
            }
        }
        boolean kick = needsKick();
        try {
            mp.setVolume(volume, volume);
            if (kick) setSpeedRaw(KICK_SPEED);
            mp.start();
            if (kick) {
                final int token = ++kickToken;
                ui.postDelayed(new Runnable() {
                    @Override public void run() {
                        // Same start, same file: otherwise someone else has already set the
                        // speed, and there is no reason to interfere.
                        if (token == kickToken) applySpeedSafely();
                    }
                }, KICK_MS);
                Probe.log("aac start at " + KICK_SPEED + "x, speed restored in " + KICK_MS + " ms");
            } else {
                applySpeedSafely();
            }
        } catch (Exception e) { Probe.log("start failed: " + e.getMessage()); return; }
        wantPlay = false;
        pausedByFocus = false;
        pushNote();
        changed();
    }

    /** byUser=true: the pause is final and will not resume by itself. */
    public void pause(boolean byUser) {
        kickToken++;
        if (mp != null && prepared) {
            try { if (mp.isPlaying()) { mp.pause(); resumingFromPause = true; } }
            catch (Exception ignored) {}
        }
        wantPlay = false;
        if (byUser) { pausedByFocus = false; dropFocus(); }
        unregisterNoisy();
        saveNow();
        pushNote();
        scheduleIdle();
        changed();
    }

    public void nudge(long delta) {
        if (mp == null || !prepared) return;
        long p = position() + delta;
        long d = duration();
        if (p < 0) p = 0;
        if (d > 0 && p > d - 500) p = Math.max(0, d - 500);
        rawSeek(p);
        saveNow();
        pushNote();
        changed();
    }

    public void seekTo(long ms) {
        if (mp == null || !prepared) return;
        long d = duration();
        if (ms < 0) ms = 0;
        if (d > 0 && ms > d) ms = d;
        rawSeek(ms);
        saveNow();
        pushNote();
        changed();
    }

    private void rawSeek(long ms) {
        try {
            if (Build.VERSION.SDK_INT >= 26)
                mp.seekTo(ms, MediaPlayer.SEEK_CLOSEST);
            else
                mp.seekTo((int) ms);
        } catch (Exception e) {
            try { mp.seekTo((int) ms); } catch (Exception ignored) {}
        }
    }

    public void next() { if (index + 1 < queue.size()) openAt(index + 1, true); else pause(true); }
    public void prev() { if (index > 0) openAt(index - 1, true); else seekTo(0); }

    public void speed(float v) {
        kickToken++;
        store.speed(v);
        applySpeedSafely();
        changed();
    }

    /**
     * On some devices setting playback params on a paused player starts the sound by itself.
     * So: remember the state, set, restore.
     */
    /*
     * Silent long AAC. On some phones the system player hands long AAC to the audio processor,
     * which plays silence: the clock runs, no sound comes. Starting at any speed other than
     * 1.0 turns that offload off. So AAC at speed 1 starts at 0.99x and gets its own speed
     * back a second later. It is done on every start, not once per file: nobody knows whether
     * the system gives the file back to the processor after a pause.
     *
     * Mp3 is left alone: for it the offload is honest battery saving over a long listen. Any
     * speed other than 1 is left alone too: it turns the offload off by itself.
     */
    private static final float KICK_SPEED = 0.99f;
    private static final long  KICK_MS = 1000L;
    private int kickToken = 0;

    private boolean needsKick() {
        Folder.Item it = current();
        if (it == null) return false;
        if (Math.abs(store.speed() - 1.0f) > 0.01f) return false;
        return Folder.isMp4(it.name, it.mime);
    }

    /** Speed without restoring the pause: called right before start(). */
    private void setSpeedRaw(float v) {
        try {
            PlaybackParams pp = mp.getPlaybackParams();
            pp.setSpeed(v);
            mp.setPlaybackParams(pp);
        } catch (Exception e) {
            Probe.log("speed " + v + " refused: " + e.getClass().getSimpleName());
        }
    }

    private void applySpeedSafely() {
        if (mp == null || !prepared) return;
        float v = store.speed();
        boolean was = false;
        try { was = mp.isPlaying(); } catch (Exception ignored) {}
        try {
            PlaybackParams pp = mp.getPlaybackParams();
            pp.setSpeed(v);
            mp.setPlaybackParams(pp);
        } catch (Exception e) {
            Probe.log("speed " + v + " refused: " + e.getClass().getSimpleName());
            return;
        }
        try {
            boolean now = mp.isPlaying();
            if (!was && now) { mp.pause(); Probe.log("speed started playback by itself, paused again"); }
        } catch (Exception ignored) {}
    }

    public boolean isPlaying() {
        try { return mp != null && prepared && mp.isPlaying(); } catch (Exception e) { return false; }
    }

    public long position() {
        try { return (mp != null && prepared) ? mp.getCurrentPosition() : 0L; } catch (Exception e) { return 0L; }
    }

    public long duration() {
        try { return (mp != null && prepared) ? mp.getDuration() : 0L; } catch (Exception e) { return 0L; }
    }

    private void stopEverything() { shutDown("closed by hand"); }

    // ---------- closing ----------

    /*
     * A paused player used to stay in the foreground forever: the notification and the lock
     * screen card lived for days, holding on to a file nobody was listening to. Now a pause
     * arms an alarm, and when it fires with nothing playing the service saves the position,
     * drops its session and notification, and stops. The widget keeps working: it draws the
     * last state from storage and wakes the player on a tap.
     *
     * An alarm rather than a handler: handler time stands still while the phone sleeps, so a
     * thirty-minute delay could take days to arrive.
     */
    private long idleSince = 0L;

    private PendingIntent idleIntent() {
        Intent i = new Intent(this, Widget.class).setAction(ACT_IDLE);
        return PendingIntent.getBroadcast(this, 93, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleIdle() {
        long ms = store.idleClose();
        cancelIdle();
        if (ms <= 0) return;
        idleSince = System.currentTimeMillis();
        armIdle(ms);
    }

    private void armIdle(long ms) {
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + ms, idleIntent());
        } catch (Exception e) {
            Probe.log("idle alarm refused: " + e.getClass().getSimpleName());
        }
    }

    private void cancelIdle() {
        idleSince = 0L;
        try {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(idleIntent());
        } catch (Exception ignored) {}
    }

    /** The alarm went off: close if still idle, otherwise wait for the rest of the delay. */
    public void idleCheck() {
        long ms = store.idleClose();
        if (ms <= 0 || isPlaying() || idleSince == 0L) { cancelIdle(); return; }
        if (watcher != null) { armIdle(ms); return; }   // our screen is open: not idle
        long left = idleSince + ms - System.currentTimeMillis();
        if (left > 60_000L) { armIdle(left); return; }  // inexact alarms may fire early
        shutDown("idle for " + (ms / 60_000L) + " min");
    }

    /** Closes the player completely: position saved, session and notification gone. */
    public void shutDown(String why) {
        Probe.log("closing: " + why);
        cancelIdle();
        if (isPlaying()) pause(true);
        saveNow();
        cancelIdle();
        kickToken++;
        unregisterNoisy();
        dropFocus();
        foreground = false;
        try { stopForeground(STOP_FOREGROUND_REMOVE); } catch (Exception ignored) {}
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTE_ID);
        } catch (Exception ignored) {}
        // The session goes quiet now, not at destruction: our screen may still be bound, and the
        // lock screen card lives exactly as long as the session is active.
        if (session != null) { try { session.setActive(false); } catch (Exception ignored) {} }
        if (mp != null) { try { mp.release(); } catch (Exception ignored) {} mp = null; }
        prepared = false;
        queue.clear();
        index = -1;
        changed();
        stopSelf();
    }

    // ---------- end of file ----------

    @Override public void onCompletion(MediaPlayer p) {
        Folder.Item it = current();
        if (it != null) {
            // The duration is taken from whichever source knows it: the player itself does not
            // always report it after the end.
            long d = duration();
            if (d <= 0) d = it.duration;
            if (d <= 0) d = store.duration(it.docId);
            if (d <= 0) try { d = p.getDuration(); } catch (Exception ignored) {}
            store.finish(it.docId, d);
            Probe.log("finished: " + it.name);
        }
        if (sleepAtEnd) { sleepAtEnd = false; pause(true); return; }
        next();
    }

    @Override public boolean onError(MediaPlayer p, int what, int extra) {
        Probe.log("MediaPlayer error what=" + what + " extra=" + extra);
        prepared = false;
        // do not freeze: if the file is broken or gone, move to the next one
        if (wantPlay && index + 1 < queue.size()) {
            Probe.log("skipping the file, moving to the next");
            ui.postDelayed(new Runnable() {
                @Override public void run() { next(); }
            }, 300);
        } else changed();
        return true;
    }

    // ---------- audio focus ----------

    private final AudioManager.OnAudioFocusChangeListener focusListener =
            new AudioManager.OnAudioFocusChangeListener() {
        @Override public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (isPlaying()) { pausedByFocus = true; pause(false); }
                Probe.log("focus lost briefly");
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                pausedByFocus = false;
                pause(true);
                Probe.log("focus lost for good");
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                volume = 0.2f;
                if (mp != null) try { mp.setVolume(volume, volume); } catch (Exception ignored) {}
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                volume = 1f;
                if (mp != null) try { mp.setVolume(volume, volume); } catch (Exception ignored) {}
                if (pausedByFocus) { pausedByFocus = false; play(); Probe.log("focus returned, resuming"); }
                break;
        }
        }
    };

    private boolean takeFocus() {
        if (am == null) return true;
        int r;
        if (Build.VERSION.SDK_INT >= 26) {
            if (focusRequest == null) {
                focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setOnAudioFocusChangeListener(focusListener)
                        .setWillPauseWhenDucked(false)
                        .build();
            }
            r = am.requestAudioFocus((AudioFocusRequest) focusRequest);
        } else {
            r = am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
        return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void dropFocus() {
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (focusRequest != null) am.abandonAudioFocusRequest((AudioFocusRequest) focusRequest);
            } else am.abandonAudioFocus(focusListener);
        } catch (Exception ignored) {}
    }

    // ---------- headphones ----------

    private final BroadcastReceiver noisy = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) {
                Probe.log("headphones pulled, pausing");
                pause(true);      // resuming is manual only
            }
        }
    };

    private void registerNoisy() {
        if (noisyRegistered) return;
        registerReceiver(noisy, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
        noisyRegistered = true;
    }

    private void unregisterNoisy() {
        if (!noisyRegistered) return;
        try { unregisterReceiver(noisy); } catch (Exception ignored) {}
        noisyRegistered = false;
    }

    // ---------- sleep timer ----------

    public void sleepIn(long ms) {
        sleepAtEnd = false;
        sleepAtMs = (ms <= 0) ? 0 : System.currentTimeMillis() + ms;
        changed();
    }

    public void sleepAtEndOfFile() { sleepAtMs = 0; sleepAtEnd = true; changed(); }
    public void sleepCancel()      { sleepAtMs = 0; sleepAtEnd = false; volume = 1f;
                                     if (mp != null) try { mp.setVolume(1f,1f); } catch (Exception ignored) {}
                                     changed(); }
    public long sleepLeft()        { return sleepAtMs == 0 ? 0 : Math.max(0, sleepAtMs - System.currentTimeMillis()); }
    public boolean sleepTilEnd()   { return sleepAtEnd; }

    // ---------- ticker ----------

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            if (isPlaying()) saveNow();
            if (sleepAtMs > 0) {
                long left = sleepAtMs - System.currentTimeMillis();
                if (left <= 0) {
                    sleepAtMs = 0; volume = 1f;
                    pause(true);
                    if (mp != null) try { mp.setVolume(1f, 1f); } catch (Exception ignored) {}
                } else if (left <= 20_000L) {
                    float v = Math.max(0.02f, left / 20_000f);
                    volume = v;
                    if (mp != null) try { mp.setVolume(v, v); } catch (Exception ignored) {}
                }
            }
            ui.postDelayed(this, SAVE_EVERY);
        }
    };

    public void saveNow() {
        Folder.Item it = current();
        if (it == null || !prepared) return;
        long p = position(), d = duration();
        if (d > 0) store.save(it.docId, p, d);
        Widget.push(this);
    }

    // ---------- notification ----------

    private void makeChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Воспроизведение",
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        ch.setSound(null, null);
        ch.enableVibration(false);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent pi(String action) {
        Intent i = new Intent(this, PlayerService.class).setAction(action);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, action.hashCode(), i, f);
    }

    private void pushNote() {
        Folder.Item it = current();
        if (it == null) return;

        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int f = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) f |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent tap = PendingIntent.getActivity(this, 7, open, f);

        boolean playing = isPlaying();

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) b = new Notification.Builder(this, CHANNEL);
        else { b = new Notification.Builder(this); b.setPriority(Notification.PRIORITY_LOW); }

        b.setSmallIcon(R.drawable.ic_note)
         .setContentTitle(Ui.bare(it.name))
         .setContentText(folderName == null ? "Hark" : folderName)
         .setContentIntent(tap)
         .setOngoing(playing)
         .setShowWhen(false)
         .setVisibility(Notification.VISIBILITY_PUBLIC);

        b.addAction(R.drawable.ic_note, "−15", pi(ACT_BACK));
        b.addAction(R.drawable.ic_note, playing ? "\u275A\u275A" : "\u25B6", pi(ACT_TOGGLE));
        b.addAction(R.drawable.ic_note, "+30", pi(ACT_FWD));
        // The fourth action shows in the expanded notification: a way out from anywhere.
        b.addAction(R.drawable.ic_note, "\u2715  " + I18n.t(I18n.CLOSE), pi(ACT_STOP));
        if (!playing) b.setDeleteIntent(pi(ACT_STOP));

        Notification.MediaStyle st = new Notification.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2);
        if (session != null) st.setMediaSession(session.getSessionToken());
        b.setStyle(st);

        // state for the lock screen and watches
        if (session != null) {
            PlaybackState.Builder ps = new PlaybackState.Builder()
                    .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                            | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO
                            | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                    .setState(playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                            position(), playing ? store.speed() : 0f);
            session.setPlaybackState(ps.build());
            MediaMetadata md = new MediaMetadata.Builder()
                    .putString(MediaMetadata.METADATA_KEY_TITLE, Ui.bare(it.name))
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, folderName == null ? "" : folderName)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration())
                    .build();
            session.setMetadata(md);
        }

        Notification n = b.build();
        // Stay in the foreground while loaded, paused included. Otherwise the system kills the
        // service, the widget goes on showing its picture, and its buttons hit nothing.
        startForeground(NOTE_ID, n);
        foreground = true;
    }
}
