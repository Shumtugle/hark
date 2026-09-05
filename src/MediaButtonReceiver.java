package io.github.shumtugle.hark;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.KeyEvent;

public class MediaButtonReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_MEDIA_BUTTON.equals(i.getAction())) return;
        KeyEvent k = (KeyEvent) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        if (k == null || k.getAction() != KeyEvent.ACTION_DOWN) return;
        String act;
        switch (k.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:     act = PlayerService.ACT_PLAY;  break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:    act = PlayerService.ACT_PAUSE; break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:     act = PlayerService.ACT_NEXT;  break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS: act = PlayerService.ACT_PREV;  break;
            default:                              act = PlayerService.ACT_TOGGLE;
        }
        Intent s = new Intent(c, PlayerService.class).setAction(act);
        try {
            if (Build.VERSION.SDK_INT >= 26 && PlayerService.get() == null) c.startForegroundService(s);
            else c.startService(s);
        } catch (Exception ignored) {}
    }
}
