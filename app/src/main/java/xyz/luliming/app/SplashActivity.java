package xyz.luliming.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.DecelerateInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import xyz.luliming.app.databinding.ActivitySplashBinding;

public class SplashActivity extends AppCompatActivity {

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        playAnimation();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }, 1800);
    }

    private void playAnimation() {
        // Cyan text animation
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f);
        fadeIn.setDuration(400);

        // Glitch (Magenta) text animation
        ObjectAnimator glitchAnim = ObjectAnimator.ofFloat(binding.tvAppNameGlitch, "alpha", 0f, 0.5f, 0f, 0.3f, 0f);
        glitchAnim.setDuration(1000);
        glitchAnim.setStartDelay(400);
        glitchAnim.setRepeatCount(2);

        ObjectAnimator sloganFade = ObjectAnimator.ofFloat(binding.tvSlogan, "alpha", 0f, 1f);
        sloganFade.setDuration(600);
        sloganFade.setStartDelay(600);

        // Random X translation for glitch effect
        ObjectAnimator glitchX = ObjectAnimator.ofFloat(binding.tvAppNameGlitch, "translationX", -5f, 5f, -2f, 2f, 0f);
        glitchX.setDuration(200);
        glitchX.setStartDelay(500);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(fadeIn, glitchAnim, sloganFade, glitchX);
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.start();
    }
}
