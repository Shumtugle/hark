package io.github.shumtugle.hark;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.lang.reflect.Method;

public final class Audio {

    public static class Out {
        public boolean wired;
        public boolean bluetooth;
        public int battery = -1;
        public String name;

        public boolean any() { return wired || bluetooth; }
    }

    private Audio() {}

    public static Out current(Context c) {
        Out o = new Out();
        try {
            AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return o;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                switch (d.getType()) {
                    case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
                    case AudioDeviceInfo.TYPE_WIRED_HEADSET:
                    case AudioDeviceInfo.TYPE_USB_HEADSET:
                        o.wired = true;
                        break;
                    case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
                    case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
                        o.bluetooth = true;
                        if (o.name == null && d.getProductName() != null)
                            o.name = d.getProductName().toString();
                        break;
                }
            }
        } catch (Exception e) {
            Probe.log("audio devices unreadable: " + e.getClass().getSimpleName());
        }
        if (o.bluetooth) battery(c, o);
        return o;
    }

    private static void battery(Context c, Out o) {
        try {
            BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
            if (a == null || !a.isEnabled()) return;
            if (a.getProfileConnectionState(BluetoothProfile.A2DP)
                    != BluetoothProfile.STATE_CONNECTED) return;

            Method isConnected = BluetoothDevice.class.getMethod("isConnected");
            for (BluetoothDevice d : a.getBondedDevices()) {
                Object on = isConnected.invoke(d);
                if (!(on instanceof Boolean) || !((Boolean) on)) continue;
                if (o.name == null) o.name = safeName(d);

                int v = viaLevel(d);
                if (v < 0) v = viaMetadata(d);
                if (v >= 0 && v <= 100) { o.battery = v; return; }
            }
            Probe.log("bluetooth battery: device connected, level not exposed");
        } catch (SecurityException e) {
            Probe.log("bluetooth battery needs permission");
        } catch (Throwable t) {
            Probe.log("bluetooth battery unavailable: " + t.getClass().getSimpleName());
        }
    }

    private static int viaLevel(BluetoothDevice d) {
        try {
            Method m = BluetoothDevice.class.getMethod("getBatteryLevel");
            Object v = m.invoke(d);
            return (v instanceof Integer) ? (Integer) v : -1;
        } catch (Throwable t) { return -1; }
    }

    private static int viaMetadata(BluetoothDevice d) {
        try {
            Method m = BluetoothDevice.class.getMethod("getMetadata", int.class);
            Object v = m.invoke(d, 18);
            if (v instanceof byte[]) {
                String s = new String((byte[]) v, "UTF-8").trim();
                if (!s.isEmpty()) return Integer.parseInt(s);
            }
        } catch (Throwable t) { }
        return -1;
    }

    private static String safeName(BluetoothDevice d) {
        try { return d.getName(); } catch (Throwable t) { return null; }
    }

    public static String label(Out o) {
        if (o == null || !o.any()) return "";
        return o.battery >= 0 ? o.battery + "%" : "";
    }
}
