package com.appman.app.adb;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import io.github.muntashirakon.adb.AbsAdbConnectionManager;
import io.github.muntashirakon.adb.AdbPairingRequiredException;
import io.github.muntashirakon.adb.AdbStream;
import io.github.muntashirakon.adb.LocalServices;
import io.github.muntashirakon.adb.android.AndroidUtils;

public final class AdbManager {
    private static volatile AdbManager instance;
    private final Context context;
    private final AtomicReference<AbsAdbConnectionManager> manager = new AtomicReference<>();
    private volatile AdbStream shell;

    private AdbManager(Context context) { this.context = context.getApplicationContext(); }
    public static AdbManager get(Context c) { if (instance == null) synchronized (AdbManager.class) { if (instance == null) instance = new AdbManager(c); } return instance; }

    private AbsAdbConnectionManager manager() throws Exception {
        AbsAdbConnectionManager m = manager.get();
        if (m != null) return m;
        synchronized (manager) {
            m = manager.get();
            if (m == null) { m = new PersistentAdbConnectionManager(context); manager.set(m); }
        }
        return m;
    }

    public boolean pair(int pairingPort, String code) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) throw new UnsupportedOperationException("Wireless debugging pairing requires Android 11 or newer.");
        return manager().pair(AndroidUtils.getHostIpAddress(context), pairingPort, code);
    }

    public boolean autoConnect() throws Exception { return manager().autoConnect(context, 7000); }

    public boolean isConnected() {
        try { return manager().isConnected(); } catch (Throwable ignored) { return false; }
    }

    public void disconnect() { try { shell = null; manager().disconnect(); } catch (Throwable ignored) {} }

    public String exec(String command) throws Exception {
        if (command == null || command.trim().isEmpty()) return "";

        try {
            return execOnce(command);
        } catch (Throwable first) {

            // The ADB transport may have gone stale while the app was
            // in the background. Reconnect the transport and try opening
            // the command stream again.
            if (!isTransportFailure(first)) {
                throw first;
            }

            reconnect();

            return execOnce(command);
        }
    }



    private String execOnce(String command) throws Exception {
        AbsAdbConnectionManager m = manager();

        AdbStream s = m.openStream("shell:" + command);

        StringBuilder out = new StringBuilder();

        try (InputStream in = s.openInputStream()) {
            byte[] buf = new byte[8192];
            int n;

            while ((n = in.read(buf)) != -1) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }

            return out.toString();

        } catch (IOException e) {

            /*
             * The command may already have completed successfully even
             * though the underlying ADB transport closed while we were
             * reading its final output.
             *
             * Do NOT run the command a second time in this situation.
             * Commands such as uninstall/clear-data must never be
             * blindly duplicated.
             */
            if (isStreamClosed(e)) {
                try {
                    reconnect();
                } catch (Throwable ignored) {
                }

                return out.toString();
            }

            throw e;

        } finally {
            try {
                s.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean isTransportFailure(Throwable t) {
        Throwable current = t;

        while (current != null) {
            String message = current.getMessage();

            if (message != null) {
                String lower = message.toLowerCase(java.util.Locale.ROOT);

                if (lower.contains("stream closed")
                        || lower.contains("not connected to adb")
                        || lower.contains("connection closed")
                        || lower.contains("broken pipe")
                        || lower.contains("socket closed")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isStreamClosed(Throwable t) {
        Throwable current = t;

        while (current != null) {
            String message = current.getMessage();

            if (message != null &&
                    message.toLowerCase(java.util.Locale.ROOT).contains("stream closed")) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private void reconnect() throws Exception {
        AbsAdbConnectionManager m = manager();

        try {
            m.disconnect();
        } catch (Throwable ignored) {
        }

        shell = null;

        boolean connected = m.autoConnect(context, 7000);

        if (!connected && !m.isConnected()) {
            throw new IOException("Unable to reconnect to ADB");
        }
    }



    public AdbStream interactiveShell() throws Exception {
        if (shell == null || shell.isClosed()) shell = manager().openStream(LocalServices.SHELL);
        return shell;
    }

    private static final class PersistentAdbConnectionManager extends AbsAdbConnectionManager {
        private final Context context; private final PrivateKey privateKey; private final Certificate certificate;
        PersistentAdbConnectionManager(Context c) throws Exception {
            context = c;
            setApi(Build.VERSION.SDK_INT);
//          if (Security.getProvider("BC") == null) Security.addProvider(new BouncyCastleProvider());
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME);
            Security.insertProviderAt(new BouncyCastleProvider(), 1);
            KeyPair pair = loadKeyPair(); privateKey = pair.getPrivate(); certificate = loadOrCreateCertificate(pair);
        }
        @Override protected PrivateKey getPrivateKey() { return privateKey; }
        @Override protected Certificate getCertificate() { return certificate; }
        @NonNull @Override protected String getDeviceName() { return "AppMan"; }

        private KeyPair loadKeyPair() throws Exception {
            File key = new File(context.getFilesDir(), "adb_rsa.pk8");
            if (key.exists()) {
                byte[] b = readAll(key); return new KeyPair(KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.X509EncodedKeySpec(loadPublic())), KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(b)));
            }
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); KeyPair kp = gen.generateKeyPair();
            write(new File(context.getFilesDir(), "adb_rsa.pk8"), kp.getPrivate().getEncoded());
            write(new File(context.getFilesDir(), "adb_rsa.pub"), kp.getPublic().getEncoded()); return kp;
        }
        private byte[] loadPublic() throws IOException { return readAll(new File(context.getFilesDir(), "adb_rsa.pub")); }
        private Certificate loadOrCreateCertificate(KeyPair kp) throws Exception {
            File f = new File(context.getFilesDir(), "adb_cert.der");
            if (f.exists()) try (InputStream in = new FileInputStream(f)) { return CertificateFactory.getInstance("X.509").generateCertificate(in); }
            long now = System.currentTimeMillis();
            X500Name name = new X500Name("CN=AppMan ADB");
            JcaX509v3CertificateBuilder b = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now), new Date(now - 86400000L), new Date(now + 3650L * 86400000L), name, kp.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(kp.getPrivate());
            X509CertificateHolder holder = b.build(signer);
            Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder); write(f, cert.getEncoded()); return cert;
        }
        private static byte[] readAll(File f) throws IOException { try (InputStream in = new FileInputStream(f)) { java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n=in.read(b))!=-1) o.write(b,0,n); return o.toByteArray(); } }
        private static void write(File f, byte[] b) throws IOException { try (OutputStream out = new FileOutputStream(f)) { out.write(b); } }
    }
}
