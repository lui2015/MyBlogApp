package xyz.luliming.app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebBackForwardList;
import android.webkit.WebHistoryItem;
import android.widget.ArrayAdapter;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import xyz.luliming.app.databinding.ActivityMainBinding;
import xyz.luliming.app.databinding.ActivitySplashBinding;
import android.graphics.BitmapFactory;

public class MainActivity extends AppCompatActivity {

    private static final String TARGET_URL = "https://www.luliming.xyz/";
    private static final String KEY_URL = "current_url";

    private ActivityMainBinding binding;
    private ActivitySplashBinding splashBinding;
    private boolean isErrorOccurred = false;
    private boolean isPageLoaded = false;
    
    private Bitmap currentFavicon = null;
    private Bitmap customIcon = null;
    private ImageView dialogIconView = null;
    
    private boolean isMenuOpen = false;
    private float dX, dY;
    private float lastX, lastY;
    private static final int CLICK_ACTION_THRESHOLD = 10;

    private final ActivityResultLauncher<String> pickIconLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        customIcon = BitmapFactory.decodeStream(inputStream);
                        if (dialogIconView != null && customIcon != null) {
                            dialogIconView.setImageBitmap(customIcon);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
    );

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
        setupShortcutButton();
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

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String url = null;

        if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                url = data.toString();
            }
        }

        if (url == null) {
            url = intent.getStringExtra(KEY_URL);
        }

        if (url != null && !url.isEmpty()) {
            binding.webView.loadUrl(url);
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
                        showShortcutButton();
                    }
                });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupShortcutButton() {
        binding.btnAddShortcut.setOnTouchListener((v, event) -> {
            View container = binding.fabMenuContainer;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dX = container.getX() - event.getRawX();
                    dY = container.getY() - event.getRawY();
                    lastX = event.getRawX();
                    lastY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float newX = event.getRawX() + dX;
                    float newY = event.getRawY() + dY;

                    View parent = (View) container.getParent();
                    newX = Math.max(0, Math.min(newX, parent.getWidth() - container.getWidth()));
                    newY = Math.max(0, Math.min(newY, parent.getHeight() - container.getHeight()));

                    container.setX(newX);
                    container.setY(newY);
                    return true;

                case MotionEvent.ACTION_UP:
                    float deltaX = event.getRawX() - lastX;
                    float deltaY = event.getRawY() - lastY;
                    if (Math.abs(deltaX) < CLICK_ACTION_THRESHOLD && Math.abs(deltaY) < CLICK_ACTION_THRESHOLD) {
                        v.performClick();
                    }
                    return true;

                default:
                    return false;
            }
        });

        binding.btnAddShortcut.setOnClickListener(v -> toggleMenu());
        
        binding.fabHome.setOnClickListener(v -> {
            binding.webView.loadUrl(TARGET_URL);
            toggleMenu();
        });

        binding.fabHistory.setOnClickListener(v -> {
            showHistoryDialog();
            toggleMenu();
        });

        binding.fabShortcut.setOnClickListener(v -> {
            createShortcut();
            toggleMenu();
        });

        binding.fabGoTo.setOnClickListener(v -> {
            showGoToDialog();
            toggleMenu();
        });
    }

    private void showGoToDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_go_to, null);
        EditText etUrl = dialogView.findViewById(R.id.etUrl);

        new AlertDialog.Builder(this, R.style.Theme_LuLiMing_Dialog)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String url = etUrl.getText().toString().trim();
                    if (!url.isEmpty()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        binding.webView.loadUrl(url);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleMenu() {
        if (binding.fabMenuContainer.getVisibility() != View.VISIBLE) return;
        isMenuOpen = !isMenuOpen;
        
        float rotation = isMenuOpen ? 45f : 0f;
        binding.btnAddShortcut.animate()
                .rotation(rotation)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        if (isMenuOpen) {
            // 扇形放射位置 (dp) - 增加到 4 个按钮
            expandButton(binding.fabHome, 0f, -100f, 0);
            expandButton(binding.fabShortcut, -45f, -90f, 40);
            expandButton(binding.fabGoTo, -90f, -45f, 80);
            expandButton(binding.fabHistory, -100f, 0f, 120);
        } else {
            collapseButton(binding.fabHome);
            collapseButton(binding.fabShortcut);
            collapseButton(binding.fabGoTo);
            collapseButton(binding.fabHistory);
        }
    }

    private void expandButton(View view, float transX, float transY, int delay) {
        view.setVisibility(View.VISIBLE);
        view.setAlpha(0f);
        view.setScaleX(0f);
        view.setScaleY(0f);
        
        view.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .translationX(dpToPx(transX))
                .translationY(dpToPx(transY))
                .alpha(1f)
                .setDuration(350)
                .setStartDelay(delay)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.5f))
                .withEndAction(() -> view.animate().scaleX(1f).scaleY(1f).setDuration(100).start())
                .start();
    }

    private void collapseButton(View view) {
        view.animate()
                .scaleX(0f)
                .scaleY(0f)
                .translationX(0f)
                .translationY(0f)
                .alpha(0f)
                .setDuration(300)
                .setStartDelay(0)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> view.setVisibility(View.GONE))
                .start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void showHistoryDialog() {
        WebBackForwardList historyList = binding.webView.copyBackForwardList();
        int size = historyList.getSize();
        if (size <= 1) {
            Toast.makeText(this, R.string.history_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] displayTitles = new String[size];
        for (int i = 0; i < size; i++) {
            WebHistoryItem item = historyList.getItemAtIndex(size - 1 - i);
            String title = item.getTitle();
            displayTitles[i] = (title != null && !title.isEmpty()) ? title : item.getUrl();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_history, displayTitles);

        new AlertDialog.Builder(this, R.style.Theme_LuLiMing_Dialog)
                .setTitle(R.string.history_title)
                .setAdapter(adapter, (dialog, which) -> {
                    int targetIndexInOriginal = (size - 1) - which;
                    int currentIndex = historyList.getCurrentIndex();
                    binding.webView.goBackOrForward(targetIndexInOriginal - currentIndex);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showShortcutButton() {
        if (binding.fabMenuContainer.getVisibility() == View.VISIBLE && binding.fabMenuContainer.getAlpha() > 0.5f) return;
        binding.fabMenuContainer.setVisibility(View.VISIBLE);
        
        if (binding.fabMenuContainer.getTranslationY() != 0) {
            binding.fabMenuContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        } else {
            binding.fabMenuContainer.animate().alpha(1f).setDuration(400).start();
        }
    }

    private void hideShortcutButton() {
        if (binding.fabMenuContainer.getVisibility() == View.GONE || binding.fabMenuContainer.getAlpha() < 0.5f) return;
        if (isMenuOpen) toggleMenu();
        
        binding.fabMenuContainer.animate()
                .alpha(0f)
                .setDuration(400)
                .withEndAction(() -> binding.fabMenuContainer.setVisibility(View.GONE))
                .start();
    }

    private void createShortcut() {
        String currentUrl = binding.webView.getUrl();
        if (currentUrl == null) return;

        String defaultTitle = binding.webView.getTitle();
        if (defaultTitle == null || defaultTitle.isEmpty()) {
            defaultTitle = getString(R.string.app_name);
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_shortcut, null);
        EditText etName = dialogView.findViewById(R.id.etShortcutName);
        dialogIconView = dialogView.findViewById(R.id.ivShortcutIcon);
        View btnChange = dialogView.findViewById(R.id.btnChangeIcon);

        etName.setText(defaultTitle);
        customIcon = currentFavicon; // 默认使用网页图标
        if (customIcon != null) {
            dialogIconView.setImageBitmap(customIcon);
        }

        btnChange.setOnClickListener(v -> pickIconLauncher.launch("image/*"));

        new AlertDialog.Builder(this, R.style.Theme_LuLiMing_Dialog)
                .setView(dialogView)
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    String customName = etName.getText().toString().trim();
                    if (customName.isEmpty()) customName = getString(R.string.app_name);
                    performCreateShortcut(currentUrl, customName, customIcon);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void performCreateShortcut(String url, String title, Bitmap iconBitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ShortcutManager shortcutManager = getSystemService(ShortcutManager.class);
            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported()) {
                Intent shortcutIntent = new Intent(this, MainActivity.class);
                shortcutIntent.setAction(Intent.ACTION_VIEW);
                shortcutIntent.putExtra(KEY_URL, url);
                shortcutIntent.setData(Uri.parse(url));
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                Icon icon;
                if (iconBitmap != null) {
                    icon = Icon.createWithBitmap(iconBitmap);
                } else {
                    icon = Icon.createWithResource(this, R.mipmap.ic_launcher);
                }

                ShortcutInfo pinShortcutInfo = new ShortcutInfo.Builder(this, "shortcut-" + url.hashCode())
                        .setShortLabel(title)
                        .setIcon(icon)
                        .setIntent(shortcutIntent)
                        .build();

                shortcutManager.requestPinShortcut(pinShortcutInfo, null);
                Toast.makeText(this, R.string.shortcut_added, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show();
            }
        } else {
            // Legacy way for Android < 8.0
            Intent shortcutIntent = new Intent(this, MainActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(KEY_URL, url);
            shortcutIntent.setData(Uri.parse(url));

            Intent addIntent = new Intent();
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
            addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, title);
            if (iconBitmap != null) {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, iconBitmap);
            } else {
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                        Intent.ShortcutIconResource.fromContext(this, R.mipmap.ic_launcher));
            }
            addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
            sendBroadcast(addIntent);
            Toast.makeText(this, R.string.shortcut_added, Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = binding.webView.getSettings();
        
        // 增加滑动显示/隐藏逻辑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            binding.webView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                if (scrollY > oldScrollY + 20) {
                    hideShortcutButton();
                } else if (scrollY < oldScrollY - 20) {
                    showShortcutButton();
                }
            });
        }

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
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                currentFavicon = icon;
            }

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
