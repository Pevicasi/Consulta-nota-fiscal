package br.com.pevicasi.nfefacil;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintManager;
import android.webkit.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public class MainActivity extends AppearanceActivity {
    static final String ORIGIN = "https://appassets.androidplatform.net";
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] pendingExport;
    private String pendingNativeKey;
    private GeolocationPermissions.Callback locationCallback;
    private String locationOrigin;
    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (fileCallback != null) { fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.getResultCode(), result.getData())); fileCallback = null; }
        });
    private final ActivityResultLauncher<String> exportPicker = registerForActivityResult(
        new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            if (uri != null && pendingExport != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new IOException("Destino indisponível");
                    out.write(pendingExport); toast("CSV salvo.");
                } catch (IOException e) { toast("Não foi possível salvar o CSV."); }
            }
            pendingExport = null;
        });
    private final ActivityResultLauncher<Intent> scanner = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String key = result.getData().getStringExtra("key");
                acceptScannerKey(key);
            }
        });
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
        new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (locationCallback != null) {
                boolean allowed = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                locationCallback.invoke(locationOrigin, allowed, false); locationCallback = null;
            }
        });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets;
        });
        addAppearanceControls(root);
        LinearLayout nav = new LinearLayout(this);
        button(nav, "Notas", () -> web.loadUrl(ORIGIN + "/assets/site/index.html"));
        button(nav, "Preços", () -> showOwnedDialog(new android.app.AlertDialog.Builder(this).setTitle("Consulta de preços")
            .setItems(new String[]{"Versão 17 (atual)", "Versão 16", "Versão 15"}, (d, which) ->
                web.loadUrl(ORIGIN + "/assets/site/consulta_precos_v" + (17 - which) + ".html")).create()));
        button(nav, "Ler chave", () -> scanner.launch(new Intent(this, ScannerActivity.class)));
        root.addView(nav);
        web = new WebView(this); root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        WebSettings settings = web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        WebViewAssetLoader assets = new WebViewAssetLoader.Builder().addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (ORIGIN.equals(request.getUrl().getScheme() + "://" + request.getUrl().getAuthority())) return assets.shouldInterceptRequest(request.getUrl());
                // The Android reader uses bundled ML Kit, never the site's remote OCR/barcode scripts.
                String host = request.getUrl().getHost();
                if ("unpkg.com".equals(host) || "cdn.jsdelivr.net".equals(host))
                    return new WebResourceResponse("text/javascript", "UTF-8", new ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (ORIGIN.equals(uri.getScheme() + "://" + uri.getAuthority()) && uri.getPath() != null && uri.getPath().startsWith("/assets/site/")) return false;
                if (request.isForMainFrame()) openExternal(uri);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith(ORIGIN + "/assets/site/")) {
                    try (InputStream in = getAssets().open("android-bridge.js")) {
                        view.evaluateJavascript(readUtf8(in), ignored -> {
                            if (url.endsWith("/site/index.html") && pendingNativeKey != null) {
                                deliverPendingKey();
                            }
                        });
                    } catch (IOException e) { toast("Não foi possível iniciar os controles Android."); }
                }
                onAppearanceChanged();
            }
        });
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            showOwnedDialog(new android.app.AlertDialog.Builder(this).setMessage("Atualize o Android System WebView para usar o leitor e a exportação.").setPositiveButton("OK", null).create());
        } else {
            WebViewCompat.addWebMessageListener(web, "NativeApp", java.util.Collections.singleton(ORIGIN), (view, message, sourceOrigin, isMainFrame, reply) -> {
                if (!isMainFrame) return;
                String data = message.getData();
                if (data == null || data.length() > 16_000_000) return;
                try { handleMessage(new JSONObject(data)); } catch (Exception e) { toast("Não foi possível concluir a operação."); }
            });
        }
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try { filePicker.launch(intent); } catch (ActivityNotFoundException e) { callback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
            @Override public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (!ORIGIN.equals(origin)) { callback.invoke(origin, false, false); return; }
                if (locationCallback != null) locationCallback.invoke(locationOrigin, false, false);
                locationCallback = callback; locationOrigin = origin;
                locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { if (web.canGoBack()) web.goBack(); else finish(); }
        });
        if (state != null) pendingNativeKey = state.getString("pendingNativeKey");
        if (state == null || web.restoreState(state) == null) web.loadUrl(ORIGIN + "/assets/site/index.html");
    }
    @Override protected void onAppearanceChanged() {
        if (web == null) return;
        web.setBackgroundColor(android.graphics.Color.parseColor(isDark()?"#111827":"#F4F7FB"));
        String url = web.getUrl();
        if (url == null || !url.matches("https://appassets\\.androidplatform\\.net/assets/site/(index|consulta_precos_v(15|16|17))\\.html")) return;
        try (InputStream in = getAssets().open("android-appearance.js")) {
            web.evaluateJavascript(readUtf8(in)+";window.applyNativeAppearance?.({dark:"+isDark()+",fontPercent:"+fontPercent()+"});", null);
        } catch (IOException error) { toast("Não foi possível aplicar a aparência."); }
    }
    void acceptScannerKey(String key) {
        if (!NfeKey.isValid(key)) return;
        String notesUrl = ORIGIN + "/assets/site/index.html";
        pendingNativeKey = key;
        if (notesUrl.equals(web.getUrl())) web.evaluateJavascript("typeof window.acceptNativeKey === 'function'", ready -> {
            if ("true".equals(ready)) deliverPendingKey();
        });
        else web.loadUrl(notesUrl);
    }
    private void deliverPendingKey() {
        if (pendingNativeKey == null || !(ORIGIN + "/assets/site/index.html").equals(web.getUrl())) return;
        String key = pendingNativeKey; pendingNativeKey = null;
        web.evaluateJavascript("window.acceptNativeKey?.(" + JSONObject.quote(key) + ")", null);
    }
    private static String readUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
    private void handleMessage(JSONObject msg) throws Exception {
        switch (msg.getString("type")) {
            case "scan": scanner.launch(new Intent(this, ScannerActivity.class)); break;
            case "copy":
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Chave NF-e", msg.optString("value"))); toast("Código copiado."); break;
            case "paste":
                ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                if (clipboard.hasPrimaryClip() && clipboard.getPrimaryClip() != null) {
                    String text = String.valueOf(clipboard.getPrimaryClip().getItemAt(0).coerceToText(this));
                    web.evaluateJavascript("window.acceptNativeClipboard?.(" + JSONObject.quote(text) + ")", null);
                } else toast("A área de transferência está vazia.");
                break;
            case "print": ((PrintManager)getSystemService(PRINT_SERVICE)).print("NF-e Fácil", web.createPrintDocumentAdapter("Nota fiscal"), null); break;
            case "export":
                if (pendingExport != null) { toast("Conclua a exportação anterior."); break; }
                JSONObject file = new JSONObject(msg.getString("value")); String value = file.getString("data");
                if (!value.startsWith("data:") || !value.contains(";base64,")) return;
                pendingExport = android.util.Base64.decode(value.substring(value.indexOf(',') + 1), android.util.Base64.DEFAULT);
                String name = file.getString("name").replaceAll("[^a-zA-Z0-9._-]", "_");
                exportPicker.launch(name.endsWith(".csv") ? name : "nota.csv"); break;
            case "error": toast(msg.optString("value")); break;
            default: break;
        }
    }
    private void button(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextSize(14); button.setOnClickListener(v -> action.run());
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void openExternal(Uri uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme()) && !"geo".equals(uri.getScheme())) return;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (ActivityNotFoundException e) { toast("Nenhum aplicativo pode abrir este link."); }
    }
    private void toast(String message) { showNotice(message); }
    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); web.saveState(out); out.putString("pendingNativeKey", pendingNativeKey); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() { if (fileCallback != null) fileCallback.onReceiveValue(null); web.destroy(); super.onDestroy(); }
}
