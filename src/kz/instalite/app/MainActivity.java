package kz.instalite.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

/**
 * InstaLite — облегчённый Instagram без Reels и рекомендаций.
 * Это браузер (WebView) для официального сайта instagram.com: вход и все данные идут напрямую
 * через сайт Instagram, приложение ничего не собирает и никуда не отправляет.
 */
public class MainActivity extends Activity {

    private static final String BASE = "https://www.instagram.com";
    private static final String HOME = BASE + "/?variant=following";
    private static final int REQ_FILE = 1001;
    private static final int ACCENT = 0xFF6F4CFF;

    private FrameLayout root;
    private WebView web;
    private ProgressBar progress;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private ValueCallback<Uri[]> fileCallback;
    private String injectJs = "";
    private boolean night;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int bg = night ? 0xFF000000 : 0xFFFFFFFF;
        if (!night && Build.VERSION.SDK_INT >= 26) {
            // светлые значки в статус-баре и навигации на белом фоне
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | 0x00000010 /* LIGHT_NAVIGATION_BAR */);
        }

        root = new FrameLayout(this);
        root.setBackgroundColor(bg);

        web = new WebView(this);
        web.setBackgroundColor(bg);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setIndeterminate(false);
        progress.setProgressTintList(ColorStateList.valueOf(ACCENT));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3), Gravity.TOP);
        root.addView(progress, lp);

        setContentView(root);

        injectJs = readAsset("inject.js");
        setupWebView();

        if (savedInstanceState != null && web.restoreState(savedInstanceState) != null) {
            return;
        }
        web.loadUrl(urlFromIntent(getIntent()));
    }

    private void setupWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setSupportMultipleWindows(false);

        // Представляемся обычным мобильным Chrome, а не встроенным WebView
        String ua = s.getUserAgentString();
        ua = ua.replace("; wv)", ")").replaceAll("Version/\\d+(\\.\\d+)* ", "");
        s.setUserAgentString(ua);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
    }

    // ---------------- Навигация ----------------

    /** Ссылки, пришедшие извне (из мессенджеров и т.п.). */
    private String urlFromIntent(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null || data.getHost() == null || !isInstagramHost(data.getHost().toLowerCase())) {
            return HOME;
        }
        String path = data.getPath() == null ? "/" : data.getPath();
        if (path.equals("/") || path.isEmpty()) return HOME;
        // Ссылка на конкретный рилс вида /reels/ID/ → открываем как одиночный пост /reel/ID/
        if (path.startsWith("/reels/")) {
            String[] parts = path.split("/");
            if (parts.length >= 3 && !parts[2].isEmpty() && !parts[2].equals("audio")) {
                return BASE + "/reel/" + parts[2] + "/";
            }
            return HOME;
        }
        return data.toString();
    }

    private static boolean isInstagramHost(String host) {
        return host.equals("instagram.com") || host.endsWith(".instagram.com");
    }

    private static boolean isInternalHost(String host) {
        return isInstagramHost(host)
                || host.equals("facebook.com") || host.endsWith(".facebook.com") // вход через Facebook
                || host.endsWith(".cdninstagram.com") || host.endsWith(".fbcdn.net");
    }

    private boolean handleUrl(WebView view, String url) {
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https")) {
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (host.equals("l.instagram.com")) {
                // внешняя ссылка из поста/профиля — открываем в обычном браузере
                String target = u.getQueryParameter("u");
                openExternal(target != null ? target : url);
                return true;
            }
            if (isInternalHost(host)) {
                String path = u.getPath() == null ? "" : u.getPath();
                if (isInstagramHost(host) && (path.equals("/reels") || path.startsWith("/reels/"))) {
                    view.loadUrl(HOME); // вкладка Reels отключена
                    return true;
                }
                return false; // грузим в приложении
            }
            openExternal(url);
            return true;
        }
        if (scheme.equals("mailto") || scheme.equals("tel") || scheme.equals("sms")) {
            openExternal(url);
        }
        // intent://, instagram:// и прочие попытки открыть официальное приложение — игнорируем
        return true;
    }

    private void openExternal(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_SHORT).show();
        }
    }

    private void inject(WebView view) {
        if (injectJs.length() > 0) view.evaluateJavascript(injectJs, null);
    }

    private class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            inject(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            inject(view);
            progress.setVisibility(View.GONE);
            CookieManager.getInstance().flush();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            String retry = request.getUrl().toString().replace("\"", "%22");
            String fg = night ? "#f5f5f5" : "#111";
            String bg = night ? "#000" : "#fff";
            String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>body{font-family:sans-serif;background:" + bg + ";color:" + fg + ";"
                    + "display:flex;flex-direction:column;align-items:center;justify-content:center;"
                    + "height:90vh;margin:0;text-align:center;padding:0 24px}"
                    + "a{margin-top:20px;padding:12px 28px;border-radius:10px;background:#6F4CFF;"
                    + "color:#fff;text-decoration:none;font-weight:600}</style></head><body>"
                    + "<div style='font-size:20px;font-weight:600'>Нет подключения</div>"
                    + "<div style='margin-top:8px;opacity:.7'>Проверьте интернет и попробуйте снова</div>"
                    + "<a href=\"" + retry + "\">Повторить</a></body></html>";
            view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
        }
    }

    private class Chrome extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                         FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;

            Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            ArrayList<String> mimes = new ArrayList<String>();
            String[] accept = params.getAcceptTypes();
            if (accept != null) {
                for (String a : accept) {
                    if (a == null) continue;
                    for (String part : a.split(",")) {
                        String m = part.trim();
                        if (m.contains("/")) mimes.add(m);
                    }
                }
            }
            if (!mimes.isEmpty()) pick.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
            if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            try {
                startActivityForResult(Intent.createChooser(pick, "Выберите файл"), REQ_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                return false;
            }
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            web.setVisibility(View.GONE);
            root.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    private void hideCustomView() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        web.setVisibility(View.VISIBLE);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_FILE) {
            super.onActivityResult(requestCode, resultCode, data);
            return;
        }
        if (fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    // ---------------- Жизненный цикл ----------------

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getData() != null) web.loadUrl(urlFromIntent(intent));
    }

    @Override
    public void onBackPressed() {
        if (customView != null) {
            hideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            root.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    // ---------------- Утилиты ----------------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String readAsset(String name) {
        InputStream in = null;
        try {
            in = getAssets().open(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
        }
    }
}
