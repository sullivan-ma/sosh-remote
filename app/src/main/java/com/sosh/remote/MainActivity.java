package com.sosh.remote;

import java.io.IOException;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {

    private EditText ipInput;
    private TextView statusText;
    private View statusDot;
    private LinearLayout scanResults;
    private ProgressBar scanProgress;
    private TextView scanProgressText;
    private TextView scanStatus;
    private ScrollView scanPanel;

    private String decoderIP = "";
    private boolean connected = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private static final Map<String, String> KEY_MAP = new HashMap<>();
    static {
        KEY_MAP.put("POWER","116"); KEY_MAP.put("MUTE","113");
        KEY_MAP.put("HOME","125");  KEY_MAP.put("GUIDE","362");
        KEY_MAP.put("VOD","393");   KEY_MAP.put("UP","103");
        KEY_MAP.put("DOWN","108");  KEY_MAP.put("LEFT","105");
        KEY_MAP.put("RIGHT","106"); KEY_MAP.put("OK","352");
        KEY_MAP.put("BACK","158");  KEY_MAP.put("MENU","139");
        KEY_MAP.put("VOL_UP","115"); KEY_MAP.put("VOL_DOWN","114");
        KEY_MAP.put("CH_UP","402"); KEY_MAP.put("CH_DOWN","403");
        KEY_MAP.put("PLAY_PAUSE","164"); KEY_MAP.put("STOP","128");
        KEY_MAP.put("REW","168");   KEY_MAP.put("FFW","159");
        KEY_MAP.put("REC","167");   KEY_MAP.put("PREV","165");
        KEY_MAP.put("NEXT","163");  KEY_MAP.put("RED","398");
        KEY_MAP.put("GREEN","399"); KEY_MAP.put("YELLOW","400");
        KEY_MAP.put("BLUE","401");  KEY_MAP.put("TEXT","388");
        KEY_MAP.put("0","512"); KEY_MAP.put("1","513");
        KEY_MAP.put("2","514"); KEY_MAP.put("3","515");
        KEY_MAP.put("4","516"); KEY_MAP.put("5","517");
        KEY_MAP.put("6","518"); KEY_MAP.put("7","519");
        KEY_MAP.put("8","520"); KEY_MAP.put("9","521");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput          = findViewById(R.id.ip_input);
        statusText       = findViewById(R.id.status_text);
        statusDot        = findViewById(R.id.status_dot);
        scanResults      = findViewById(R.id.scan_results);
        scanProgress     = findViewById(R.id.scan_progress);
        scanProgressText = findViewById(R.id.scan_progress_text);
        scanStatus       = findViewById(R.id.scan_status);
        scanPanel        = findViewById(R.id.scan_panel);

        setupButtons();
    }

    private void setupButtons() {
        findViewById(R.id.btn_connect).setOnClickListener(v -> connect());
        findViewById(R.id.btn_scan).setOnClickListener(v -> startScan());
    }

    private void connect() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            toast("Entrez une adresse IP");
            return;
        }

        decoderIP = ip;
        connected = false;
        setStatus("connecting", "Connexion en cours…");

        executor.execute(() -> {
            boolean ok = testConnection(ip);
            mainHandler.post(() -> {
                if (ok) {
                    connected = true;
                    setStatus("connected", "✅ Connecté — " + decoderIP);
                } else {
                    setStatus("error", "❌ Connexion impossible");
                }
            });
        });
    }

    private boolean testConnection(String ip) {
        try {
            HttpURLConnection c = (HttpURLConnection)
                    new URL("http://" + ip + ":8080/remoteControl/cmd?operation=10")
                            .openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private void sendCommand(String cmd) {
        if (!connected || decoderIP.isEmpty()) {
            toast("Connectez-vous d'abord au décodeur");
            return;
        }

        String keyCode = KEY_MAP.get(cmd);
        if (keyCode == null) return;

        String url = "http://" + decoderIP +
                ":8080/remoteControl/cmd?operation=01&key=" + keyCode + "&mode=0";

        executor.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(2000);
                c.setReadTimeout(2000);
                int code = c.getResponseCode();
                c.disconnect();
                if (code < 200 || code >= 300) {
                    throw new IOException("HTTP " + code);
                }
            } catch (Exception e) {
                mainHandler.post(() ->
                        toast("Erreur réseau : " + e.getMessage()));
            }
        });
    }

    private void startScan() {
        scanPanel.setVisibility(View.VISIBLE);
        scanResults.removeAllViews();
        scanProgress.setProgress(0);
        scanProgressText.setText("0/254");

        executor.execute(() -> {
            String subnet = detectSubnet();
            ExecutorService pool = Executors.newFixedThreadPool(50);
            int[] done = {0};

            try {
                for (int i = 1; i <= 254; i++) {
                    final int host = i;
                    pool.execute(() -> {
                        String ip = subnet + "." + host;
                        if (probeDecoder(ip)) {
                            mainHandler.post(() -> addFoundDevice(ip));
                        }
                        int d;
                        synchronized (done) {
                            done[0]++;
                            d = done[0];
                        }
                        mainHandler.post(() -> {
                            scanProgress.setProgress(d);
                            scanProgressText.setText(d + "/254");
                        });
                    });
                }
            } finally {
                pool.shutdown();
            }
        });
    }

    private boolean probeDecoder(String ip) {
        try {
            HttpURLConnection c = (HttpURLConnection)
                    new URL("http://" + ip + ":8080/remoteControl/cmd?operation=10")
                            .openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            int code = c.getResponseCode();
            c.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private void addFoundDevice(String ip) {
        TextView tv = new TextView(this);
        tv.setText("📺 Décodeur trouvé : " + ip);
        tv.setPadding(8, 8, 8, 8);
        scanResults.addView(tv);
    }

    private String detectSubnet() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    String ip = ia.getAddress().getHostAddress();
                    if (ip != null && ip.startsWith("192.168.")) {
                        String[] p = ip.split("\\.");
                        return p[0] + "." + p[1] + "." + p[2];
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1";
    }

    private void setStatus(String state, String msg) {
        statusText.setText(msg);
        if ("connected".equals(state)) {
            statusDot.setBackgroundResource(R.drawable.dot_green);
        } else if ("error".equals(state)) {
            statusDot.setBackgroundResource(R.drawable.dot_red);
        } else {
            statusDot.setBackgroundResource(R.drawable.dot_orange);
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
