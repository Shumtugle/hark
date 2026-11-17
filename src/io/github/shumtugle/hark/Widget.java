package io.github.shumtugle.hark;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.widget.RemoteViews;

/**
 * The home-screen widget. Name, folder, position, five buttons, cover art and headset charge.
 * The backdrop is a layer of its own; its density is set in settings, down to fully
 * transparent.
 */
public class Widget extends AppWidgetProvider {

    public static final String ACT_SPEED = "io.github.shumtugle.hark.W_SPEED";
    public static final String ACT_OPEN  = "io.github.shumtugle.hark.W_OPEN";
    /** A command for the player, wrapped in a broadcast. */
    public static final String ACT_CMD   = "io.github.shumtugle.hark.W_CMD";
    public static final String EXTRA_CMD = "cmd";

    /** Five steps of backdrop density: from glass to solid stone. */
    public static final int[] SHADES = {0, 64, 128, 192, 255};

    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        push(c);
    }

    /** The widget was stretched or shrunk: the cover's side has changed. */
    @Override public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m,
                                                    int id, android.os.Bundle o) {
        super.onAppWidgetOptionsChanged(c, m, id, o);
        Cover.forget();
        push(c);
    }

    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (PlayerService.ACT_IDLE.equals(a)) {
            // Only a live service is asked; a dead one is already as closed as it gets.
            PlayerService s = PlayerService.get();
            if (s != null) s.idleCheck();
            return;
        }

        if (ACT_CMD.equals(a)) {
            String cmd = i.getStringExtra(EXTRA_CMD);
            if (cmd == null) return;
            PlayerService s = PlayerService.get();
            if (s != null) {
                // the service is alive: talk to it directly, no extra starts
                if (PlayerService.ACT_TOGGLE.equals(cmd)) s.toggle();
                else if (PlayerService.ACT_BACK.equals(cmd)) s.nudge(-PlayerService.BACK_MS);
                else if (PlayerService.ACT_FWD.equals(cmd))  s.nudge(+PlayerService.FWD_MS);
                else if (PlayerService.ACT_NEXT.equals(cmd)) s.next();
                else if (PlayerService.ACT_PREV.equals(cmd)) s.prev();
                push(c);
                return;
            }
            // the service is dead: start it, and it will remember the last file by itself
            try {
                Intent go = new Intent(c, PlayerService.class).setAction(cmd);
                c.startForegroundService(go);
            } catch (Exception e) {
                Probe.log("service would not start: " + e.getClass().getSimpleName());
                Intent open = new Intent(c, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(open); } catch (Exception ignored) {}
            }
            return;
        }

        if (ACT_SPEED.equals(a)) {
            Store st = new Store(c);
            float[] steps = {0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
            float cur = st.speed();
            int at = 1;
            for (int k = 0; k < steps.length; k++) if (Math.abs(steps[k] - cur) < 0.01f) at = k;
            float next = steps[(at + 1) % steps.length];
            PlayerService s = PlayerService.get();
            if (s != null) s.speed(next); else st.speed(next);
            push(c);
            return;
        }
        super.onReceive(c, i);
    }

    /** Redraws every instance of the widget. */
    public static void push(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            ComponentName cn = new ComponentName(c, Widget.class);
            int[] ids = m.getAppWidgetIds(cn);
            if (ids == null || ids.length == 0) return;
            // The cover is as tall as the inner zone, so its side depends on the widget's
            // height, and the bitmap is requested at that size.
            int hDp = 0;
            try {
                android.os.Bundle o = m.getAppWidgetOptions(ids[0]);
                if (o != null) hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            } catch (Exception ignored) {}
            RemoteViews v = build(c, hDp);
            m.updateAppWidget(cn, v);
        } catch (Exception e) {
            Probe.log("widget refresh failed: " + e.getClass().getSimpleName());
        }
    }

    private static android.net.Uri treeOf(Store st) {
        String r = st.root();
        return r == null ? null : android.net.Uri.parse(r);
    }


    private static RemoteViews build(Context c, int widgetHeightDp) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.hark_widget);
        Store st = new Store(c);

        // the fill fades, the outline stays: otherwise on a transparent backdrop the elements
        // look scattered over the wallpaper
        int shade = SHADES[Math.max(0, Math.min(SHADES.length - 1, st.widgetShade()))];
        v.setInt(R.id.w_bg, "setImageAlpha", shade);
        v.setInt(R.id.w_frame, "setImageAlpha", 105);   // the frame always holds the shape

        PlayerService s = PlayerService.get();
        Folder.Item it = (s == null) ? null : s.current();
        boolean playing = s != null && s.isPlaying();

        if (it == null) {
            // the service sleeps: draw the last state kept in storage, so the widget does not
            // turn into an empty frame
            String doc = st.lastDoc();
            if (doc != null && st.lastName() != null) {
                long p = st.position(doc), d = st.duration(doc);
                v.setTextViewText(R.id.w_title, Ui.bare(st.lastName()));
                v.setTextViewText(R.id.w_folder, st.lastFolderName() == null ? "" : st.lastFolderName());
                v.setTextViewText(R.id.w_pos, Ui.time(p));
                v.setTextViewText(R.id.w_dur, d > 0 ? Ui.time(d) : "");
                v.setProgressBar(R.id.w_bar, 1000, d > 0 ? (int) (1000L * p / d) : 0, false);
            } else {
                v.setTextViewText(R.id.w_title, I18n.t(I18n.NOTHING));
                v.setTextViewText(R.id.w_folder, "");
                v.setTextViewText(R.id.w_pos, "");
                v.setTextViewText(R.id.w_dur, "");
                v.setProgressBar(R.id.w_bar, 1000, 0, false);
            }
        } else {
            long p = s.position(), d = s.duration();
            v.setTextViewText(R.id.w_title, Ui.bare(it.name));
            v.setTextViewText(R.id.w_folder, s.folderName() == null ? "" : s.folderName());
            v.setTextViewText(R.id.w_pos, Ui.time(p));
            v.setTextViewText(R.id.w_dur, d > 0 ? Ui.time(d) : "");
            v.setProgressBar(R.id.w_bar, 1000, d > 0 ? (int) (1000L * p / d) : 0, false);
        }

        v.setImageViewResource(R.id.w_play, playing ? R.drawable.w_pause : R.drawable.w_play);

        // cover art: from the file's tags first, then from the folder
        try {
            String doc = (it != null) ? it.docId : st.lastDoc();
            String fld = (s != null) ? s.folderId() : st.lastFolder();
            android.net.Uri tr = null;
            if (s != null && it != null) tr = treeOf(st);
            else if (st.root() != null) tr = android.net.Uri.parse(st.root());
            // The cover's side equals the height of the inner zone, 12 dp of padding above and
            // below.
            float dens = c.getResources().getDisplayMetrics().density;
            int sideDp = widgetHeightDp > 40 ? widgetHeightDp - 24 : 96;
            if (sideDp < 56) sideDp = 56;
            if (sideDp > 200) sideDp = 200;

            Bitmap art = (tr == null) ? null
                    : Cover.get(c, tr, doc, fld, Math.round(sideDp * dens));
            if (art != null) {
                v.setImageViewBitmap(R.id.w_art, art);
                v.setViewVisibility(R.id.w_art, android.view.View.VISIBLE);
            } else {
                v.setViewVisibility(R.id.w_art, android.view.View.GONE);
            }
        } catch (Exception e) {
            v.setViewVisibility(R.id.w_art, android.view.View.GONE);
            Probe.log("cover failed: " + e.getClass().getSimpleName());
        }

        // the headset charge sits by the buttons: that is where one looks when deciding
        // whether it will last to the end of the chapter
        try {
            Audio.Out ao = Audio.current(c);
            if (ao.any()) {
                boolean low = ao.battery >= 0 && ao.battery <= 15;
                v.setImageViewResource(R.id.w_phones_icon,
                        ao.bluetooth ? R.drawable.w_bt : R.drawable.w_head);
                // A pill, not an oval. Background set from code silently overrides the one in
                // the layout, so the two must agree.
                v.setInt(R.id.w_phones, "setBackgroundResource",
                        low ? R.drawable.widget_chip_low : R.drawable.widget_chip);
                if (ao.battery >= 0) {
                    v.setTextViewText(R.id.w_phones_pct, ao.battery + "%");
                    v.setTextColor(R.id.w_phones_pct, low ? 0xFFA85D4A : 0xFFD49A3A);
                    v.setViewVisibility(R.id.w_phones_pct, android.view.View.VISIBLE);
                    v.setViewVisibility(R.id.w_phones, android.view.View.VISIBLE);
                } else {
                    // The device reported no charge: no pill at all. An empty frame with an
                    // icon would promise data that is not coming, and waste room.
                    v.setViewVisibility(R.id.w_phones, android.view.View.GONE);
                }
            } else {
                v.setViewVisibility(R.id.w_phones, android.view.View.INVISIBLE);
            }
        } catch (Exception e) {
            v.setViewVisibility(R.id.w_phones, android.view.View.INVISIBLE);
        }


        // buttons. The tap target is the whole pill, not the icon inside: on the move people
        // aim at the frame, because that is what looks like a button.
        v.setOnClickPendingIntent(R.id.w_play_zone, svc(c, PlayerService.ACT_TOGGLE));
        v.setOnClickPendingIntent(R.id.w_play, svc(c, PlayerService.ACT_TOGGLE));
        v.setOnClickPendingIntent(R.id.w_back, svc(c, PlayerService.ACT_BACK));
        v.setOnClickPendingIntent(R.id.w_fwd,  svc(c, PlayerService.ACT_FWD));
        v.setOnClickPendingIntent(R.id.w_prev, svc(c, PlayerService.ACT_PREV));
        v.setOnClickPendingIntent(R.id.w_next, svc(c, PlayerService.ACT_NEXT));

        // a tap on the name or an empty spot opens the app on the current folder
        Intent open = new Intent(c, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 92, open, flags());
        v.setOnClickPendingIntent(R.id.w_title, pi);
        v.setOnClickPendingIntent(R.id.w_folder, pi);
        v.setOnClickPendingIntent(R.id.w_bg, pi);
        v.setOnClickPendingIntent(R.id.w_frame, pi);

        return v;
    }

    private static PendingIntent svc(Context c, String action) {
        Intent i = new Intent(c, Widget.class)
                .setAction(ACT_CMD)
                .putExtra(EXTRA_CMD, action);
        return PendingIntent.getBroadcast(c, action.hashCode(), i, flags());
    }

    private static int flags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    }
}
