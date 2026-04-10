package com.example.tsrapp.ui.main

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.databinding.ActivityLandingBinding

class LandingActivity : AppCompatActivity() {

    companion object {
        private const val SPLASH_DELAY_MS = 2800L
    }

    private lateinit var binding: ActivityLandingBinding
    private val runningAnimators = mutableListOf<Animator>()
    private val handler = Handler(Looper.getMainLooper())
    private var hasNavigated = false
    private val navigateRunnable = Runnable { openHome() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareIntroState()
        binding.root.post { startIntroAnimation() }
    }

    private fun prepareIntroState() {
        binding.root.alpha = 0f

        listOf(
            binding.splashContent,
            binding.logoImage,
            binding.landingTitle
        ).forEach { view ->
            view.alpha = 0f
            view.translationY = 64f
            view.scaleX = 0.92f
            view.scaleY = 0.92f
        }
    }

    private fun startIntroAnimation() {
        binding.root.animate()
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        binding.splashContent.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setStartDelay(40L)
            .setDuration(820L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        animateIn(binding.logoImage, startDelay = 140L, duration = 820L, overshoot = true)
        animateIn(binding.landingTitle, startDelay = 320L, duration = 760L, overshoot = true)

        handler.postDelayed(navigateRunnable, SPLASH_DELAY_MS)
    }

    private fun animateIn(
        view: View,
        startDelay: Long,
        duration: Long,
        overshoot: Boolean = false
    ) {
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setStartDelay(startDelay)
            .setDuration(duration)
            .setInterpolator(
                if (overshoot) OvershootInterpolator(0.9f) else AccelerateDecelerateInterpolator()
            )
            .start()
    }

    private fun openHome() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacks(navigateRunnable)

        val exitAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.splashContent, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(binding.splashContent, View.TRANSLATION_Y, 0f, -20f),
                ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_X, binding.logoImage.scaleX, 1.05f),
                ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_Y, binding.logoImage.scaleY, 1.05f)
            )
            duration = 260L
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startActivity(Intent(this@LandingActivity, HomeActivity::class.java))
                    overridePendingTransition(
                        com.example.tsrapp.R.anim.landing_page_enter,
                        com.example.tsrapp.R.anim.landing_page_exit
                    )
                    finish()
                }
            })
        }

        runningAnimators += exitAnim
        exitAnim.start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(navigateRunnable)
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        super.onDestroy()
    }
}
