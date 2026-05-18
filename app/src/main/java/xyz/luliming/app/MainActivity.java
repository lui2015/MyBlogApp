package xyz.luliming.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import xyz.luliming.app.databinding.ActivityMainBinding;
import xyz.luliming.app.databinding.ActivitySplashBinding;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_URL = "https://www.luliming.xyz/";
    private static final String KEY_URL = "current_url";

    private ActivityMainBinding binding;
    private ActivitySplashBinding splashBinding;
    private boolean isErrorOccurred = false;
    private boolean isPageLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 初始化闪屏叠加层绑定
        splashBinding = ActivitySplashBinding.bind(binding.splashOverlay.getRoot());

        setupWebView();
        setupSwipeRefresh();
        setupRetryButton();
        playSplashAnimation();

        String url = TARGET_URL;
        if (savedInstanceState != null) {
            url = savedInstanceState.getString(KEY_URL, TARGET_URL);
            // 如果是恢复状态，直接隐藏闪屏
            hideSplashOverlay();
        }

        if (isNetworkAvailable()) {
            binding.webView.loadUrl(url);
            checkUpdate(); // 异步检查版本更新
        } else {
            showError(getString(R.string.no_network));
            hideSplashOverlay();
        }
    }

    private void checkUpdate() {
        new Thread(() -> {
            try {
                // 这里替换为您真实的检查更新接口
                // 预期返回 JSON: {"versionCode": 2, "versionName": "1.0.1", "downloadUrl": "...", "updateLog": "优化体验"}
                URL updateUrl = new URL("https://www.luliming.xyz/app-update.json");
                HttpURLConnection connection = (HttpURLConnection) updateUrl.openConnection();
                connection.setConnectTimeout(5000);
                connection.setRequestMethod("GET");

                if (connection.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    int serverVersionCode = json.getInt("versionCode");
                    String versionName = json.getString("versionName");
                    String downloadUrl = json.getString("downloadUrl");
                    String updateLog = json.optString("updateLog", "新版本发布");

                    // 获取当前版本号
                    int currentVersionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

                    if (serverVersionCode > currentVersionCode) {
                        runOnUiThread(() -> showUpdateDialog(versionName, updateLog, downloadUrl));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void showUpdateDialog(String versionName, String updateLog, String downloadUrl) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.update_title) + " v" + versionName)
                .setMessage(getString(R.string.update_msg, updateLog))
                .setPositiveButton(R.string.update_btn_now, (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                    startActivity(intent);
                })
                .setNegativeButton(R.string.update_btn_later, null)
                .setCancelable(false)
                .show();
    }

    private void playSplashAnimation() {
        // 复用之前的动画逻辑
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(splashBinding.tvAppName, "alpha", 0f, 1f);
        fadeIn.setDuration(400);

        ObjectAnimator glitchAnim = ObjectAnimator.ofFloat(splashBinding.tvAppNameGlitch, "alpha", 0f, 0.5f, 0f, 0.3f, 0f);
        glitchAnim.setDuration(1000);
        glitchAnim.setStartDelay(400);
        glitchAnim.setRepeatCount(ObjectAnimator.INFINITE); // 循环播放直到网页加载完成

        ObjectAnimator sloganFade = ObjectAnimator.ofFloat(splashBinding.tvSlogan, "alpha", 0f, 1f);
        sloganFade.setDuration(600);
        sloganFade.setStartDelay(600);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fadeIn, glitchAnim, sloganFade);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }

    private void hideSplashOverlay() {
        if (binding.splashOverlay.getRoot().getVisibility() == View.GONE) return;

        binding.splashOverlay.getRoot().animate()
                .alpha(0f)
                .setDuration(600)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        binding.splashOverlay.getRoot().setVisibility(View.GONE);
                    }
                });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = binding.webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        
        // 进一步增强：允许混合内容加载，解决部分资源加载失败问题
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true); // 允许访问文件，部分 H5 插件需要
        settings.setAllowContentAccess(true);
        
        // 进一步增强：使用更通用的现代 Chrome User-Agent
        String customUA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36";
        settings.setUserAgentString(customUA);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true);

        binding.webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // 站内链接在 WebView 内打开
                if (url.contains("luliming.xyz")) {
                    return false;
                }
                // 外部链接用浏览器打开
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    // Ignore
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                isErrorOccurred = false;
                binding.progressBar.setVisibility(View.VISIBLE);
                binding.errorLayout.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                binding.progressBar.setVisibility(View.GONE);
                binding.swipeRefresh.setRefreshing(false);
                
                // 网页加载完成后隐藏闪屏
                if (!isErrorOccurred) {
                    hideSplashOverlay();
                }
            }

            @Override
            public void onReceivedSslError(WebView view, android.webkit.SslErrorHandler handler, android.net.http.SslError error) {
                // 忽略证书错误（主要针对部分旧设备或特定网络环境）
                handler.proceed();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    isErrorOccurred = true;
                    String debugInfo = "\nURL: " + request.getUrl() + 
                                     "\nError: " + error.getErrorCode() + 
                                     "\nDesc: " + error.getDescription();
                    showError(getString(R.string.load_error) + debugInfo);
                    hideSplashOverlay();
                }
            }
        });

        binding.webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                binding.progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    binding.progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary);
        binding.swipeRefresh.setOnRefreshListener(() -> {
            if (isNetworkAvailable()) {
                binding.errorLayout.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                binding.webView.reload();
            } else {
                binding.swipeRefresh.setRefreshing(false);
                showError(getString(R.string.no_network));
            }
        });
    }

    private void setupRetryButton() {
        binding.btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                binding.errorLayout.setVisibility(View.GONE);
                binding.webView.setVisibility(View.VISIBLE);
                binding.webView.reload();
            } else {
                showError(getString(R.string.no_network));
            }
        });
    }

    private void showError(String message) {
        binding.webView.setVisibility(View.GONE);
        binding.progressBar.setVisibility(View.GONE);
        binding.errorLayout.setVisibility(View.VISIBLE);
        binding.tvErrorMsg.setText(message);
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_URL, binding.webView.getUrl());
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.webView.onResume();
    }

    @Override
    protected void onPause() {
        binding.webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (binding != null && binding.webView != null) {
            binding.webView.stopLoading();
            binding.webView.loadUrl("about:blank");
            binding.webView.clearHistory();
            binding.webView.removeAllViews();
            binding.webView.destroy();
        }
        super.onDestroy();
    }
}
