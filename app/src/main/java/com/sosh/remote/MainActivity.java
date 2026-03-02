package com.sosh.remote;

import android.os.Bundle;
import android.os.AsyncTask;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import okhttp3.*;

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
    private OkHttpClient httpClient;

    private static final Map<String, String> KEY_MAP = new HashMap<>();
    static {
        KEY_MAP.put("POWER", "116");
        KEY_MAP.put("MUTE", "113");
        KEY_MAP.put("HOME", "125");
        KEY_MAP.put("GUIDE", "362");
        KEY_MAP.put("VOD", "393");
        KEY_MAP.put("UP", "103");
        KEY_MAP.put("DOWN", "108");
        KEY_MAP.put("LEFT", "105");
        KEY_MAP.put("RIGHT", "106");
        KEY_MAP.put("OK", "352");
        KEY_MAP.put("BACK", "158");
        KEY_MAP.put("MENU", "139");
        KEY_MAP.put("VOL_UP", "115");
        KEY_MAP.put("VOL_DOWN", "114");
        KEY_MAP.put("CH_UP", "402");
        KEY_MAP.put("CH_DOWN", "403");
        KEY_MAP.put("PLAY_PAUSE", "164");
        KEY_MAP.put("STOP", "128");
        KEY_MAP.put("REW", "168");
        KEY_MAP.put("FFW", "159");
        KEY_MAP.put("REC", "167");
        KEY_MAP.put("PREV", "165");
        KEY_MAP.put("NEXT", "163");
        KEY_MAP.put("RED", "398");
        KEY_MAP.put("GREEN", "399");
        KEY_MAP.put("YELLOW", "400");
        KEY_MAP.put("BLUE", "401");
        KEY_MAP.put("TEXT", "388");
        KEY_MAP.put("0", "512"); KEY_MAP.put("1", "513");
        KEY_MAP.put("2", "514"); KEY_MAP.put("3", "515");
        KEY_MAP.put("4", "516"); KEY_MAP.put("5", "517");
        KEY_MAP.put("6", "518"); KEY_MAP.put("7", "519");
        KEY_MAP.put("8", "520"); KEY_MAP.put("9", "521");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        httpClient = new OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build();

        ipInput = findViewById(R.id.ip_input);
        statusText = findViewById(R.id.status_text);
        statusDot = findViewById(R.id.status_dot);
        scanResults = findViewById(R.id.scan_results);
        scanProgress = findViewById(R.id.scan_progress);
        scanProgressText = findViewById(R.id.scan_progress_text);
        scanStatus = findViewById(R.id.scan_status);
        scanPanel = findViewById(R.id.scan_panel);

        setupButtons();
    }

    private void setupButtons() {
        // Config
        findViewById(R.id.btn_connect).setOnClickListener(v -> connect());
        findViewById(R.id.btn_scan).setOnClickListener(v -> startScan());

        // Power row
        setCmd(R.id.btn_power, "POWER");
        setCmd(R.id.btn_mute, "MUTE");

        // Quick access
        setCmd(R.id.btn_home, "HOME");
        setCmd(R.id.btn_guide, "GUIDE");
        setCmd(R.id.btn_vod, "VOD");

        // Nav
        setCmd(R.id.btn_up, "UP");
        setCmd(R.id.btn_down, "DOWN");
        setCmd(R.id.btn_left, "LEFT");
        setCmd(R.id.btn_right, "RIGHT");
        setCmd(R.id.btn_ok, "OK");
        setCmd(R.id.btn_back, "BACK");
        setCmd(R.id.btn_menu_btn, "MENU");

        // Volume / Channel
        setCmd(R.id.btn_vol_up, "VOL_UP");
        setCmd(R.id.btn_vol_down, "VOL_DOWN");
        setCmd(R.id.btn_ch_up, "CH_UP");
        setCmd(R.id.btn_ch_down, "CH_DOWN");

        // Media
        setCmd(R.id.btn_prev, "PREV");
        setCmd(R.id.btn_rew, "REW");
        setCmd(R.id.btn_play, "PLAY_PAUSE");
        setCmd(R.id.btn_ffw, "FFW");
        setCmd(R.id.btn_next, "NEXT");
        setCmd(R.id.btn_stop, "STOP");
        setCmd(R.id.btn_rec, "REC");

        // Colors
        setCmd(R.id.btn_red, "RED");
        setCmd(R.id.btn_green, "GREEN");
        setCmd(R.id.btn_yellow, "YELLOW");
        setCmd(R.id.btn_blue, "BLUE");

        // Numpad
        setCmd(R.id.btn_0, "0");
        setCmd(R.id.btn_1, "1");
        setCmd(R.id.btn_2, "2");
        setCmd(R.id.btn_3, "3");
        setCmd(R.id.btn_4, "4");
        setCmd(R.id.btn_5, "5");
        setCmd(R.id.btn_6, "6");
        setCmd(R.id.btn_7, "7");
        setCmd(R.id.btn_8, "8");
        setCmd(R.id.btn_9, "9");
        setCmd(R.id.btn_text, "TEXT");
    }

    private void setCmd(int btnId, String cmd) {
        View v = findViewById(btnId);
        if (v != null) v.setOnClickListener(view -> sendCommand(cmd));
    }

    private void connect() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) {
            toast("Entrez une adresse IP"); return;
        }
        decoderIP = ip;
        setStatus("connecting", "Connexion en cours...");
        new ConnectTask().execute(ip);
    }

    private void setStatus(String state, String msg) {
        runOnUiThread(() -> {
            statusText.setText(msg);
            if ("connected".equals(state)) {
                statusDot.setBackgroundResource(R.drawable.dot_green);
                connected = true;
            } else if ("error".equals(state)) {
                statusDot.setBackgroundResource(R.drawable.dot_red);
            } else {
                statusDot.setBackgroundResource(R.drawable.dot_orange);
            }
        });
    }

    private void sendCommand(String cmd) {
        if (!connected || decoderIP.isEmpty()) {
            toast("Connectez-vous d'abord au décodeur"); return;
        }
        String keyCode = KEY_MAP.get(cmd);
        if (keyCode == null) return;
        String url = "http://" + decoderIP + "/remoteControl/cmd?operation=01&key=" + keyCode + "&mode=0";
        new SendCommandTask().execute(url, cmd);
    }

    private void startScan() {
        scanPanel.setVisibility(View.VISIBLE);
        scanResults.removeAllViews();
        scanProgress.setProgress(0);
        scanProgress.setMax(254);
        new ScanTask().execute();
    }

    private void toast(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    // --- AsyncTasks ---

    class ConnectTask extends AsyncTask<String, Void, Boolean> {
        @Override
        protected Boolean doInBackground(String... params) {
            try {
                String url = "http://" + params[0] + "/remoteControl/cmd?operation=10";
                Request req = new Request.Builder().url(url).build();
                Response res = httpClient.newCall(req).execute();
                res.close();
                return true;
            } catch (Exception e) {
                // Try basic socket connection
                try {
                    Socket s = new Socket();
                    s.connect(new InetSocketAddress(params[0], 8080), 2000);
                    s.close();
                    return true;
                } catch (Exception e2) {
                    return false;
                }
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                setStatus("connected", "✅ Connecté — " + decoderIP);
                toast("Décodeur connecté !");
            } else {
                setStatus("error", "❌ Impossible de joindre " + decoderIP);
                toast("Connexion échouée — vérifiez l'IP");
            }
        }
    }

    class SendCommandTask extends AsyncTask<String, Void, Boolean> {
        String cmd;
        @Override
        protected Boolean doInBackground(String... params) {
            cmd = params[1];
            try {
                Request req = new Request.Builder().url(params[0]).build();
                Response res = httpClient.newCall(req).execute();
                res.close();
                return true;
            } catch (Exception e) { return false; }
        }

        @Override
        protected void onPostExecute(Boolean ok) {
            if (!ok) toast("Erreur — commande " + cmd + " non envoyée");
        }
    }

    class ScanTask extends AsyncTask<Void, int[], Void> {
        List<String> found = new ArrayList<>();

        @Override
        protected Void doInBackground(Void... v) {
            // Detect local IP to find subnet
            String subnet = "192.168.1";
            try {
                Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                outer:
                while (interfaces.hasMoreElements()) {
                    NetworkInterface ni = interfaces.nextElement();
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress addr = ia.getAddress();
                        String ip = addr.getHostAddress();
                        if (ip != null && ip.startsWith("192.168.") && !ip.equals("127.0.0.1")) {
                            String[] parts = ip.split("\\.");
                            subnet = parts[0] + "." + parts[1] + "." + parts[2];
                            break outer;
                        }
                    }
                }
            } catch (Exception e) {}

            final String base = subnet;
            runOnUiThread(() -> scanStatus.setText("🔍 Scan de " + base + ".1–254…"));

            ExecutorService pool = Executors.newFixedThreadPool(40);
            List<Future<?>> futures = new ArrayList<>();
            final int[] done = {0};

            for (int i = 1; i <= 254; i++) {
                final int host = i;
                futures.add(pool.submit(() -> {
                    String ip = base + "." + host;
                    try {
                        // Try the Sosh decoder endpoint
                        URL url = new URL("http://" + ip + "/remoteControl/cmd?operation=10");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(800);
                        conn.setReadTimeout(800);
                        conn.connect();
                        int code = conn.getResponseCode();
                        conn.disconnect();
                        if (code > 0) {
                            synchronized (found) { found.add(ip); }
                            runOnUiThread(() -> addFoundDevice(ip));
                        }
                    } catch (Exception e) {}
                    synchronized (done) {
                        done[0]++;
                        final int d = done[0];
                        runOnUiThread(() -> {
                            scanProgress.setProgress(d);
                            scanProgressText.setText(d + "/254");
                        });
                    }
                }));
            }

            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception e) {}
            }
            pool.shutdown();
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            if (found.isEmpty()) {
                scanStatus.setText("😕 Aucun décodeur trouvé");
                TextView tip = new TextView(MainActivity.this);
                tip.setText("• Vérifiez que le décodeur est allumé\n• Soyez sur le même WiFi\n• Trouvez l'IP via votre Livebox sur 192.168.1.1");
                tip.setTextColor(0xFF888899);
                tip.setTextSize(12);
                tip.setPadding(8, 8, 8, 8);
                scanResults.addView(tip);
            } else {
                scanStatus.setText("✅ " + found.size() + " décodeur(s) trouvé(s) !");
            }
        }

        private void addFoundDevice(String ip) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(16, 12, 16, 12);
            row.setBackgroundColor(0xFF1C1C28);

            TextView label = new TextView(MainActivity.this);
            label.setText("📺  " + ip + "\nDécodeur Sosh détecté");
            label.setTextColor(0xFFE8531A);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Button btn = new Button(MainActivity.this);
            btn.setText("Utiliser");
            btn.setBackgroundColor(0xFFE8531A);
            btn.setTextColor(0xFFFFFFFF);
            btn.setOnClickListener(v -> {
                ipInput.setText(ip);
                connect();
            });

            row.addView(label);
            row.addView(btn);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 4, 0, 4);
            row.setLayoutParams(params);
            scanResults.addView(row);
        }
    }
}
