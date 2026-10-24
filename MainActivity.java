package br.com.videobaixador;

import android.app.Activity;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import android.graphics.Typeface;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

public class MainActivity extends Activity {
    private EditText urlInput;
    private Button downloadButton;
    private TextView statusText;
    private LinearLayout downloadsList;
    private File downloadsDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Python.isStarted()) Python.start(new AndroidPlatform(this));

        downloadsDir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Videos");
        if (!downloadsDir.exists()) downloadsDir.mkdirs();

        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(24), dp(20), dp(20));
        scroll.addView(page);

        TextView title = new TextView(this);
        title.setText("Vídeo Baixador");
        title.setTextSize(27);
        title.setTypeface(null, Typeface.BOLD);
        page.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Instagram Reels e TikTok • links públicos");
        subtitle.setTextSize(14);
        subtitle.setPadding(0, dp(5), 0, dp(22));
        page.addView(subtitle);

        TextView label = new TextView(this);
        label.setText("Link do vídeo");
        label.setTextSize(16);
        page.addView(label);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint("Cole o link aqui");
        urlInput.setInputType( android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        page.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(54)));

        downloadButton = new Button(this);
        downloadButton.setText("Baixar vídeo");
        page.addView(downloadButton, new LinearLayout.LayoutParams(-1, dp(52)));

        statusText = new TextView(this);
        statusText.setText("Pronto para baixar.");
        statusText.setTextSize(14);
        statusText.setPadding(0, dp(10), 0, dp(18));
        page.addView(statusText);

        TextView downloadsTitle = new TextView(this);
        downloadsTitle.setText("Meus downloads");
        downloadsTitle.setTextSize(20);
        downloadsTitle.setTypeface(null, Typeface.BOLD);
        downloadsTitle.setPadding(0, dp(4), 0, dp(10));
        page.addView(downloadsTitle);

        downloadsList = new LinearLayout(this);
        downloadsList.setOrientation(LinearLayout.VERTICAL);
        page.addView(downloadsList);

        setContentView(scroll);
        downloadButton.setOnClickListener(v -> startDownload());
        refreshDownloads();
    }

    private void startDownload() {
        String url = urlInput.getText().toString().trim();
        if (url.isEmpty()) {
            urlInput.setError("Cole um link primeiro");
            return;
        }
        Uri uri = Uri.parse(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (!(host.contains("instagram.com") || host.contains("tiktok.com"))) {
            urlInput.setError("Use um link do Instagram ou TikTok");
            return;
        }
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE))
            .hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        downloadButton.setEnabled(false);
        statusText.setText("Baixando… mantenha o app aberto.");
        executor.execute(() -> {
            try {
                String result = Python.getInstance().getModule("downloader")
                    .callAttr("download", url, downloadsDir.getAbsolutePath()).toString();
                mainHandler.post(() -> {
                    statusText.setText(result);
                    downloadButton.setEnabled(true);
                    refreshDownloads();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    statusText.setText("Falha no download: " + e.getMessage());
                    downloadButton.setEnabled(true);
                });
            }
        });
    }

    private void refreshDownloads() {
        downloadsList.removeAllViews();
        File[] files = downloadsDir.listFiles((dir, name) ->
            name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".mkv")
            || name.toLowerCase().endsWith(".webm") || name.toLowerCase().endsWith(".mov"));
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("Seus vídeos baixados aparecerão aqui.");
            empty.setTextSize(14);
            downloadsList.addView(empty);
            return;
        }
        java.util.Arrays.sort(files, (a,b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File f : files) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12), dp(12), dp(12), dp(12));
            row.setBackgroundColor(0xFFF1F1F1);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.setMargins(0, 0, 0, dp(8));
            downloadsList.addView(row, rp);

            TextView name = new TextView(this);
            name.setText(f.getName());
            name.setTextSize(14);
            name.setTypeface(null, Typeface.BOLD);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxLines(2);
            row.addView(name);

            TextView size = new TextView(this);
            size.setText(String.format(java.util.Locale.getDefault(), "%.1f MB", f.length() / 1048576.0));
            size.setTextSize(12);
            row.addView(size);

            Button open = new Button(this);
            open.setText("Abrir vídeo");
            open.setOnClickListener(v -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.fromFile(f), "video/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try { startActivity(intent); }
                catch (Exception e) { Toast.makeText(this, "Não encontrei um app para abrir o vídeo.", Toast.LENGTH_LONG).show(); }
            });
            row.addView(open);
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
