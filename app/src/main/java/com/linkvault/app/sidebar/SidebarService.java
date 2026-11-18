package com.linkvault.app.sidebar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.linkvault.app.R;
import com.linkvault.app.ai.SidebarSettings;
import com.linkvault.app.ui.MainActivity;

/**
 * Foreground service hosting the AI Sidebar overlay (spec §2, §7).
 *
 * Why a service: the overlay must survive the user leaving LinkVault to
 * rewrite text in OTHER apps. A specialUse FGS (subtyped as "user-initiated
 * in-app writing assistant") keeps Android 14+ happy; the notification is
 * ongoing with LOW importance (no per-result notifications anywhere).
 *
 * Start policy (Android 12+ background-FGS restrictions honoured):
 * - Settings toggle → ContextCompat.startForegroundService (user in app).
 * - MainActivity.onCreate → guarded start only (app in foreground).
 * - No boot receiver (spec: unrequested features not added).
 * START_STICKY: system may redeliver after a low-memory kill; onCreate /
 * onStartCommand re-check the enable toggle + overlay permission before
 * re-attaching, so a disabled sidebar never comes back by itself.
 */
public class SidebarService extends Service {

    public static final String ACTION_STOP = "com.linkvault.app.sidebar.STOP";
    public static final String ACTION_EXPAND = "com.linkvault.app.sidebar.EXPAND";

    private static final int NOTIF_ID = 4701;
    private static final String CHANNEL_ID = "sidebar_ai";

    @Nullable
    private SidebarOverlay overlay;

    /** Safe start from foreground callers (toggle / MainActivity). */
    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(
                    context, new Intent(context, SidebarService.class));
        } catch (RuntimeException ignored) {
            // FGS start refused (background state) — retried on next open.
        }
    }

    public static void expand(Context context) {
        try {
            Intent intent = new Intent(context, SidebarService.class);
            intent.setAction(ACTION_EXPAND);
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException ignored) {
        }
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, SidebarService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        startAsForeground();
        if (!ensureOverlay()) {
            stopSelf();
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // First call from onCreate already attached; later calls (redelivery,
        // repeated start()) must not double-attach.
        if (overlay == null && !ensureOverlay()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_EXPAND.equals(intent.getAction()) && overlay != null) {
            overlay.expand();
        }
        return START_STICKY;
    }

    /** Attaches the overlay when enabled AND overlay permission granted. */
    private boolean ensureOverlay() {
        SidebarSettings settings = new SidebarSettings(this);
        if (!settings.isEnabled()) return false;
        SidebarOverlay candidate = overlay != null ? overlay : new SidebarOverlay(this);
        if (!candidate.attach()) return false;
        overlay = candidate;
        return true;
    }

    @Override
    public void onDestroy() {
        if (overlay != null) {
            overlay.destroy();
            overlay = null;
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Sidebar", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    private void startAsForeground() {
        Intent open = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_sidebar)
                .setContentTitle("LinkVault Sidebar")
                .setContentText("Writing assistant active — edge pill ready.")
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }
}
