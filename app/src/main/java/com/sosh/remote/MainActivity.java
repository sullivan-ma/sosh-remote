package com.sosh.remote;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.*;
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
        int[][] btns = {
            {R.id.btn_power,0},{R.id.btn_mute,1},{R.id.btn_home,2},
            {R.id.btn_guide,3},{R.id.btn_vod,4},{R.id.btn_up,5},
            {R.id.btn_down,6},{R.id.btn_left,7},{R.id.btn_right,8},
            {R.id.btn_ok,9},{R.id.btn_back,10},{R.id.btn_menu_btn,11},
            {R.id.btn_vol_up,12},{R.id.btn_vol_down,13},{R.id.btn_ch_up,14},
            {R.id.btn_ch_down,15},{R.id.btn_prev,16},{R.id.btn_rew,17},
            {R.id.btn_play,18},{R.id.btn_ffw,19},{R.id.btn_next,20},
            {R.id.btn_stop,21},{R.id.btn_rec,22},{R.id.btn_red,23},
            {R.id.btn_green,24},{R.id.btn_yellow,25},{R.id.btn_blue,26},
            {R.id.btn_0,27},{R.id.btn_1,28},{R.id.btn_2,29},
            {R.id.btn_3,30},{R.id.btn_4,31},{R.id.btn_5,32},
            {R.id.btn_6,33},{R.id.btn_7,34},{R.id.btn_8,35},
            {R.id.btn_9,36},{R.id.btn_text,37}
        };
        String[] cmds = {
            "POWER","MUTE","HOME","GUIDE","VOD","UP","DOWN","LEFT","RIGHT",
            "OK","BACK","MENU","VOL_UP","VOL_DOWN","CH_UP","CH_DOWN",
            "PREV","REW","PLAY_PAUSE","FFW","NEXT","STOP","REC",
            "RED","GREEN","YELLOW","BLUE",
            "0","1","2","3","4","5","6","7","8","9","TEXT"
        };
        for (int[] pair : btns) {
            View v = findViewById(pair[0]);
            if (v != null) {
                String cmd = cmds[pair[1]];
                v.setOnClickListener(view -> sendCommand(cmd));
            }
        }
    }

    // â”€â”€ Connexion â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void connect() {
        String ip = ipInput.getText().toString().trim();
        if (ip.isEmpty()) { toast("Entrez une adresse IP"); return; }
        decoderIP = ip;
        connected = false;
        setStatus("connecting", "Connexion en coursâ€¦");
        executor.execute(() -> {
            boolean ok = testConnection(ip);
            mainHandler.post(() -> {
                if (ok) {
                    connected = true;
                    setStatus("connected", "âœ… ConnectÃ© â€” " + decoderIP);
                    toast("DÃ©codeur connectÃ© !");
                } else {
                    setStatus("error", "âŒ Impossible de joindre " + ip);
                    toast("Connexion Ã©chouÃ©e â€” vÃ©rifiez l'IP");
                }
            });
        });
    }

    private boolean testConnection(String ip) {
        try {
            HttpURLConnection c = (HttpURLConnection)
                new URL("http://" + ip + ":8080/remoteControl/cmd?operation=10").openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.connect();
            int code = c.getResponseCode();
            c.disconnect();
            return code > 0;
        } catch (Exception e) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(ip, 8080), 2000);
                s.close();
                return true;
            } catch (Exception e2) { return false; }
        }
    }

    // â”€â”€ Envoi commande (thread rÃ©seau obligatoire sur Android) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void sendCommand(String cmd) {
        if (!connected || decoderIP.isEmpty()) {
            toast("Connectez-vous d'abord au dÃ©codeur");
            return;
        }
        String keyCode = KEY_MAP.get(cmd);
        if (keyCode == null) return;
        String url = "http://" + decoder Ip 
            + ":8080/remoteControl/cmd?operation=01&key=" + keyCode + "&mode=0";
        executor.execute(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(2000);
                c.setReadTimeout(2000);
                c.setRequestMethod("GET");
                c.connect();
                c.getResponseCode();
                c.disconnect();
            } catch (Exception e) {
                mainHandler.post(() -> toast("âŒ Erreur : " + e.getMessage()));
            }
        });
    }

    // â”€â”€ Scanner rÃ©seau â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void startScan() {
        scanPanel.setVisibility(View.VISIBLE);
        scanResults.removeAllViews();
        scanProgress.setProgress(0);
        scanProgressText.setText("0/254");
        scanStatus.setText("DÃ©tection du rÃ©seauâ€¦");

        executor.execute(() -> {
            String subnet = detectSubnet();
            mainHandler.post(() ->
                scanStatus.setText("ðŸ” Scan de " + subnet + ".1â€“254â€¦"));

            List<String> found = Collections.synchronizedList(new ArrayList<>());
            int[] done = {0};
            ExecutorService pool = Executors.newFixedThreadPool(50);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 1; i <= 254; i++) {
                final int host = i;
                futures.add(pool.submit(() -> {
                    String ip = subnet + "." + host;
                    String name = probeDecoder(ip);
                    synchronized (done) { done[0]++; }
                    final int d = done[0];
                    if (name != null) {
                        found.add(ip);
                        mainHandler.post(() -> addFoundDevice(ip, name));
                    }
                    mainHandler.post(() -> {
                        scanProgress.setProgress(d);
                        scanProgressText.setText(d + "/254");
                    });
                }));
            }
            for (Future<?> f : futures) {
                try { f.get(); } catch (Exception ignored) {}
            }
            pool.shutdown();

            mainHandler.post(() -> {
                if (found.isEmpty()) {
                    scanStatus.setText("ðŸ˜• Aucun dÃ©codeur trouvÃ©");
                    TextView tip = new TextView(this);
                    tip.setText("â€¢ VÃ©rifiez que le dÃ©codeur est allumÃ©\n"
                            + "â€¢ MÃªme rÃ©seau WiFi requis\n"
                            + "â€¢ IP visible sur votre Livebox : 192.168.1.1");
                    tip.setTextColor(0xFF888899);
                    tip.setTextSize(12);
                    tip.setPadding(8, 8, 8, 8);
                    scanResults.addView(tip);
                } else {
                    scanStatus.setText("âœ… " + found.size() + " dÃ©codeur(s) trouvÃ©(s) !");
                }
            });
        });
    }

    /** Retourne le nom de l'appareil si c'est un dÃ©codeur, null sinon */
    private String probeDecoder(String ip) {
        try {
            HttpURLConnection c = (HttpURLConnection)
                new URL("http://" + ip + ":8080/remoteControl/cmd?operation=10").openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            c.connect();
            int code = c.getResponseCode();
            c.disconnect();
            if (code > 0) return resolveDeviceName(ip);
        } catch (Exception ignored) {}
        return null;
    }

    /** Essaie de trouver un nom lisible pour l'IP */
    private String resolveDeviceName(String ip) {
        // 1. RÃ©solution DNS inverse
        try {
            String hostname = InetAddress.getByName(ip).getCanonicalHostName();
            if (!hostname.equals(ip)) {
                return "ðŸ“º " + hostname;
            }
        } catch (Exception ignored) {}

        // 2. Lecture du titre de la page d'accueil
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://" + ip + "/").openConnection();
            c.setConnectTimeout(600);
            c.setReadTimeout(600);
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line; int n = 0;
            while ((line = br.readLine()) != null && n++ < 30) sb.append(line);
            br.close(); c.disconnect();
            String html = sb.toString();
            if (html.contains("<title>")) {
                int s = html.indexOf("<title>") + 7;
                int e = html.indexOf("</title>", s);
                if (e > s) return "ðŸ“º " + html.substring(s, e).trim();
            }
        } catch (Exception ignored) {}

        // 3. Nom par dÃ©faut
        return "ðŸ“º DÃ©codeur Sosh";
    }

    private void addFoundDevice(String ip, String name) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(16, 14, 16, 14);
        row.setBackgroundColor(0xFF1C1C28);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(0xFFE8531A);
        nameView.setTextSize(14);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView ipView = new TextView(this);
        ipView.setText(ip);
        ipView.setTextColor(0xFF888899);
        ipView.setTextSize(12);

        info.addView(nameView);
        info.addView(ipView);

        Button btn = new Button(this);
        btn.setText("Utiliser");
        btn.setBackgroundColor(0xFFE8531A);
        btn.setTextColor(0xFFFFFFFF);
        btn.setOnClickListener(v -> { ipInput.setText(ip); connect(); });

        row.addView(info);
        row.addView(btn);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 4, 0, 4);
        row.setLayoutParams(p);
        scanResults.addView(row);
    }

    private String detectSubnet() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    String ip = ia.getAddress().getHostAddress();
                    if (ip == null) continue;
                    if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                        String[] p = ip.split("\\.");
                        if (p.length == 4) return p[0]+"."+p[1]+"."+p[2];
                    }
                }
            }
        } catch (Exception ignored) {}
        return "192.168.1";
    }

    private void setStatus(String state, String msg) {
        statusText.setText(msg);
        switch (state) {
            case "connected": statusDot.setBackgroundResource(R.drawable.dot_green); break;
            case "error":     statusDot.setBackgroundResource(R.drawable.dot_red);   break;
            default:          statusDot.setBackgroundResource(R.drawable.dot_orange);
        }
    }

    private void toast(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
