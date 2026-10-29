package com.appman.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;

import com.appman.app.MainActivity;
import com.appman.app.R;
import com.appman.app.adb.AdbManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import io.github.muntashirakon.adb.android.AdbMdns;

public class AdbPairingService extends Service {
    public static final String ACTION_PREPARE = "com.appman.app.PREPARE";
    public static final String ACTION_PAIR = "com.appman.app.PAIR";
    public static final String EXTRA_CODE = "code";
    public static final String REMOTE_CODE = "pairing_code";
    private static final int NOTIFICATION_ID = 4201;
    private static final String CHANNEL = "adb_connection";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile int pairingPort = -1;

    @Override public void onCreate() { super.onCreate(); createChannel(); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification("AppMan is ready for wireless debugging"));
        if (intent != null && ACTION_PREPARE.equals(intent.getAction())) executor.execute(this::discoverPairingPort);
        if (intent != null && ACTION_PAIR.equals(intent.getAction())) {
            String inputCode = intent.getStringExtra(EXTRA_CODE);
            if (inputCode == null) { android.os.Bundle results = RemoteInput.getResultsFromIntent(intent); if (results != null) inputCode = results.getString(REMOTE_CODE); }
            final String code = inputCode;
            executor.execute(() -> pair(code));
        }
        return START_STICKY;
    }

    private void discoverPairingPort() {
        update("Looking for Wireless debugging pairing service…");
        AtomicInteger port = new AtomicInteger(-1); CountDownLatch latch = new CountDownLatch(1);
        AdbMdns mdns = new AdbMdns(getApplicationContext(), AdbMdns.SERVICE_TYPE_TLS_PAIRING, (host, p) -> { if (port.compareAndSet(-1, p)) latch.countDown(); });
        try {
            mdns.start();
            boolean found = latch.await(45, TimeUnit.SECONDS);
            pairingPort = port.get();
            if (found) update("Pairing service found. Enter the current 6-digit code.");
            else update("Pairing service not found. Keep Wireless debugging + Pair device with pairing code open.");
        } catch (Throwable t) { update("Discovery error: " + safe(t)); }
        finally { try { mdns.stop(); } catch (Throwable ignored) {} }
    }

    private void pair(String code) {
        if (TextUtils.isEmpty(code) || code.length() != 6) { update("Pairing code must be 6 digits."); return; }
        if (pairingPort < 0) discoverPairingPort();
        try {
            update("Pairing…");
            boolean ok = AdbManager.get(this).pair(pairingPort, code);
            if (!ok) { update("Pairing rejected. Android may have generated a new code; try the current code."); return; }
            update("Paired. Connecting…");
            boolean connected = AdbManager.get(this).autoConnect();
            update(connected ? "ADB connected." : "Paired, but the ADB connection was not discovered yet. Tap connect again with Wireless debugging open.");
        } catch (Throwable t) { update("Pairing failed: " + safe(t)); }
    }

    private String safe(Throwable t) { String m = t.getMessage(); return m == null ? t.getClass().getSimpleName() : m; }

    private void update(String text) { getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification(text)); }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        RemoteInput input = new RemoteInput.Builder(REMOTE_CODE).setLabel("6-digit pairing code").build();
        Intent pair = new Intent(this, AdbPairingService.class).setAction(ACTION_PAIR);
        PendingIntent pairPi = PendingIntent.getService(this, 2, pair, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        NotificationCompat.Action action = new NotificationCompat.Action.Builder(android.R.drawable.ic_menu_send, "Enter code", pairPi).addRemoteInput(input).build();
        return new NotificationCompat.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_appman).setContentTitle("AppMan • ADB").setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(pi).addAction(action).build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "ADB connection", NotificationManager.IMPORTANCE_LOW));
    }

    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
