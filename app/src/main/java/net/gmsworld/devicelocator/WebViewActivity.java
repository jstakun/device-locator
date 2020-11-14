package net.gmsworld.devicelocator;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.apache.commons.lang3.StringUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class WebViewActivity extends AppCompatActivity {

    private WebView webView = null;
    private String url = null, title = null;
    private static final String WEBVIEW_STATE_PRESENT = "webview_state_present";
    private static final String TAG = WebViewActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            url = extras.getString("url");
            title = extras.getString("title");
        }

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        setContentView(R.layout.activity_web_view);

        final Toolbar toolbar = findViewById(R.id.smsToolbar);
        setSupportActionBar(toolbar);

        webView = findViewById(R.id.webview);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setAllowFileAccess(true);
        //settings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);

        try {
            HelperInternal.setCacheSettings(getApplicationContext(), settings);
        } catch (VerifyError e) {
        }

        webView.setWebViewClient(new MyWebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                Log.d(TAG, "Loading progress " + progress + "...");
                //Make the bar disappear after URL is loaded, and changes string to Loading...
                WebViewActivity.this.setTitle(R.string.please_wait);
                setProgressBarIndeterminateVisibility(true);
                setProgressBarVisibility(true);
                WebViewActivity.this.setProgress(progress * 100); //Make the bar disappear after URL is loaded

                // Return the app name after finish loading
                if (progress == 100) {
                    if (title != null) {
                        WebViewActivity.this.setTitle(title);
                    } else {
                        WebViewActivity.this.setTitle(R.string.app_name);
                    }
                    setProgressBarIndeterminateVisibility(false);
                    setProgressBarVisibility(false);
                }
            }
        });

        Bundle bundle = new Bundle();
        if (StringUtils.isNotEmpty(url)) {
            bundle.putString("url", url);
        }
        FirebaseAnalytics.getInstance(this).logEvent("webview_activity", bundle);

        if (savedInstanceState == null && url != null) {
            webView.loadUrl(url);
        } else if ((savedInstanceState != null && !savedInstanceState.getBoolean(WEBVIEW_STATE_PRESENT, false)) && url != null) {
            webView.loadUrl(url);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (url != null) {
            state.putBoolean(WEBVIEW_STATE_PRESENT, true);
            webView.saveState(state);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private class MyWebViewClient extends WebViewClient {

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Log.d(TAG, "Loading url: " + url);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                view.loadUrl(url);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Log.d(TAG, "Loaded " + url + ".");
            findViewById(R.id.loadingWebView).setVisibility(View.GONE);
            findViewById(R.id.layoutWebView).setVisibility(View.VISIBLE);
        }
    }

    private static class HelperInternal {
        private static void setCacheSettings(Context context, WebSettings settings) {
            settings.setAppCacheMaxSize(1024 * 1024); // 1MB
            settings.setAppCachePath(context.getCacheDir().getAbsolutePath());
            settings.setAppCacheEnabled(true);
        }
    }
}