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
import androidx.appcompat.app.AppCompatActivity;

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
        } else {
            showError(getString(R.string.no_network));
            hideSplashOverlay();
        }
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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager.getInstance().setAcceptCookie(true);

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
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    isErrorOccurred = true;
                    showError(getString(R.string.load_error));
                    hideSplashOverlay(); // 出错也要隐藏闪屏，否则看不见错误提示
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
