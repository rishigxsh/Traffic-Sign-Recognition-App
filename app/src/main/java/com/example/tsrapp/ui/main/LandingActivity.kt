package com.example.tsrapp.ui.main

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.databinding.ActivityLandingBinding

class LandingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLandingBinding
    private val runningAnimators = mutableListOf<Animator>()
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prepareIntroState()
        binding.startButton.setOnClickListener { openHome() }
        binding.root.post { startIntroAnimation() }
    }

    private fun prepareIntroState() {
        listOf(
            binding.orbTop,
            binding.orbBottom,
            binding.landingEyebrow,
            binding.landingTitle,
            binding.landingSubtitle,
            binding.startButton,
            binding.bottomHint
        ).forEach { view ->
            view.alpha = 0f
            view.translationY = 48f
        }

        binding.startButton.scaleX = 0.92f
        binding.startButton.scaleY = 0.92f
    }

    private fun startIntroAnimation() {
        startOrbAnimation(binding.orbTop, 0f, 26f, 5200L)
        startOrbAnimation(binding.orbBottom, 0f, -22f, 4700L)

        animateIn(binding.landingEyebrow, startDelay = 80L, duration = 500L)
        animateIn(binding.landingTitle, startDelay = 220L, duration = 720L, overshoot = true)
        animateIn(binding.landingSubtitle, startDelay = 360L, duration = 560L)
        animateIn(binding.startButton, startDelay = 500L, duration = 700L, overshoot = true)
        animateIn(binding.bottomHint, startDelay = 700L, duration = 480L)

        startButtonPulse()
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

    private fun startOrbAnimation(view: View, fromY: Float, toY: Float, duration: Long) {
        val floatAnimator = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, fromY, toY, fromY).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        val fadeAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.35f, 0.9f, 0.5f).apply {
            this.duration = duration
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        runningAnimators += floatAnimator
        runningAnimators += fadeAnimator
    }

    private fun startButtonPulse() {
        val pulse = ObjectAnimator.ofPropertyValuesHolder(
            binding.startButton,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f, 1f)
        ).apply {
            duration = 1700L
            startDelay = 1100L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        runningAnimators += pulse
    }

    private fun openHome() {
        if (hasNavigated) return
        hasNavigated = true

        val exitAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.contentCard, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(binding.contentCard, View.TRANSLATION_Y, 0f, -24f),
                ObjectAnimator.ofFloat(binding.startButton, View.SCALE_X, binding.startButton.scaleX, 1.08f),
                ObjectAnimator.ofFloat(binding.startButton, View.SCALE_Y, binding.startButton.scaleY, 1.08f)
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
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        super.onDestroy()
    }
}
