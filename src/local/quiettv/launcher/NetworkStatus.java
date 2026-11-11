package local.quiettv.launcher;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** The active link as reported by Android, without a network probe or an SSID lookup. */
enum NetworkStatus {
    WIFI(R.string.network_wifi, UiIcon.Glyph.WIFI),
    ETHERNET(R.string.network_ethernet, UiIcon.Glyph.ETHERNET),
    CONNECTED(R.string.control_connected, UiIcon.Glyph.LINK),
    OFFLINE(R.string.control_disconnected, UiIcon.Glyph.OFFLINE),
    UNKNOWN(R.string.control_network_unknown, UiIcon.Glyph.LINK);

    final int label;
    final UiIcon.Glyph glyph;

    NetworkStatus(int label, UiIcon.Glyph glyph) {
        this.label = label;
        this.glyph = glyph;
    }

    static NetworkStatus read(Context context) {
        try {
            ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
            if (manager == null) return UNKNOWN;
            Network active = manager.getActiveNetwork();
            if (active == null) return OFFLINE;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            if (capabilities == null) return UNKNOWN;
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return ETHERNET;
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return WIFI;
            return CONNECTED;
        } catch (RuntimeException ignored) {
            return UNKNOWN;
        }
    }
}
