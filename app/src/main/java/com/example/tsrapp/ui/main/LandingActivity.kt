package com.example.tsrapp.ui.main

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.databinding.ActivityLandingBinding
import kotlin.random.Random

class LandingActivity : AppCompatActivity() {

    companion object {
        private const val FALLBACK_SPLASH_DELAY_MS = 2800L
        private const val SPLASH_SIGNS_FOLDER = "splash_signs"
        private const val MAX_VISIBLE_SIGNS = 16
        private const val SIGN_START_DELAY_MS = 600L
        private const val SIGN_INTERVAL_MS = 120L
        private const val SIGN_POP_DURATION_MS = 540L
        private const val SIGN_HOLD_AFTER_POP_MS = 900L
        private const val TAG = "LandingActivity"
    }

    private lateinit var binding: ActivityLandingBinding
    private val runningAnimators = mutableListOf<Animator>()
    private val handler = Handler(Looper.getMainLooper())
    private val signAssetNames = mutableListOf<String>()
    private val signRunnables = mutableListOf<Runnable>()
    private val activeSignViews = mutableListOf<View>()
    private val random = Random(12345)
    private var hasNavigated = false
    private val navigateRunnable = Runnable { openHome() }

    private data class SignPlacement(
        val x: Float,
        val y: Float,
        val sizePx: Int,
        val rotation: Float
    )



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLandingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSplashSigns()
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

        animateIn(binding.logoImage, startDelay = 140L, duration = 820L)
        animateIn(binding.landingTitle, startDelay = 320L, duration = 760L)
        binding.signsLayer.post {
            startSplashSignSequence()
            handler.postDelayed(navigateRunnable, navigationDelayMs())
        }
    }

    private fun loadSplashSigns() {
        val supportedExtensions = setOf("png", "jpg", "jpeg", "webp")
        val files = assets.list(SPLASH_SIGNS_FOLDER)
            ?.filter { fileName ->
                val extension = fileName.substringAfterLast('.', "").lowercase()
                extension in supportedExtensions
            }
            ?.sorted()
            .orEmpty()

        signAssetNames.clear()
        signAssetNames.addAll(files)
    }

    private fun startSplashSignSequence() {
        if (signAssetNames.isEmpty()) return
        if (binding.signsLayer.width == 0 || binding.signsLayer.height == 0) return

        val placements = buildSignPlacements()
        if (placements.isEmpty()) return

        val displayCount = minOf(MAX_VISIBLE_SIGNS, signAssetNames.size, placements.size)
        val selectedAssets = signAssetNames.shuffled(random).take(displayCount)

        // Randomize the pop order so they scatter visually instead of appearing sequentially
        val popSequence = (0 until displayCount).shuffled(random)

        popSequence.forEachIndexed { popStep, index ->
            val assetName = selectedAssets[index]
            val placement = placements[index]
            val signRunnable = Runnable { showAnimatedSign(assetName, placement) }
            signRunnables += signRunnable
            handler.postDelayed(signRunnable, SIGN_START_DELAY_MS + popStep * SIGN_INTERVAL_MS)
        }
    }

    private fun showAnimatedSign(assetName: String, placement: SignPlacement) {
        val bitmap = try {
            decodeSampledBitmap(assetName, placement.sizePx)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode sign: $assetName", e)
            null
        } ?: return

        val layer = binding.signsLayer

        val signView = ImageView(this).apply {
            val lp = FrameLayout.LayoutParams(placement.sizePx, placement.sizePx)
            lp.leftMargin = placement.x.toInt()
            lp.topMargin = placement.y.toInt()
            layoutParams = lp
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            translationY = dpToPx(22).toFloat()
            rotation = placement.rotation
        }

        layer.addView(signView)
        activeSignViews += signView

        val alphaHolder = PropertyValuesHolder.ofKeyframe(
            View.ALPHA,
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.16f, 1f),
            Keyframe.ofFloat(1f, 1f)
        )
        val scaleXHolder = PropertyValuesHolder.ofKeyframe(
            View.SCALE_X,
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.58f, 1.22f),
            Keyframe.ofFloat(0.82f, 0.9f),
            Keyframe.ofFloat(1f, 1f)
        )
        val scaleYHolder = PropertyValuesHolder.ofKeyframe(
            View.SCALE_Y,
            Keyframe.ofFloat(0f, 0f),
            Keyframe.ofFloat(0.58f, 1.22f),
            Keyframe.ofFloat(0.82f, 0.9f),
            Keyframe.ofFloat(1f, 1f)
        )
        val translationYHolder = PropertyValuesHolder.ofKeyframe(
            View.TRANSLATION_Y,
            Keyframe.ofFloat(0f, signView.translationY),
            Keyframe.ofFloat(0.58f, -dpToPx(10).toFloat()),
            Keyframe.ofFloat(1f, 0f)
        )

        val signAnimator = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofPropertyValuesHolder(
                    signView,
                    alphaHolder,
                    scaleXHolder,
                    scaleYHolder,
                    translationYHolder
                )
            )
            duration = SIGN_POP_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runningAnimators.remove(animation)
                }

                override fun onAnimationCancel(animation: Animator) {
                    runningAnimators.remove(animation)
                }
            })
        }

        runningAnimators += signAnimator
        signAnimator.start()
    }

    private fun buildSignPlacements(): List<SignPlacement> {
        val w = binding.signsLayer.width.toFloat()
        val h = binding.signsLayer.height.toFloat()
        if (w == 0f || h == 0f) return emptyList()

        val signSize = dpToPx(50)
        val half = signSize / 2f

        // Fixed positions: (xFraction, yFraction, rotation)
        // Hand-picked to scatter evenly around the screen perimeter,
        // avoiding the center logo + title area
        val positions = listOf(
            Triple(0.30f, 0.17f,   -8f),
            Triple(0.47f, 0.08f,   10f),
            Triple(0.73f, 0.07f,   -5f),
            Triple(0.64f, 0.18f,   12f),
            Triple(0.89f, 0.15f,  -11f),
            Triple(0.03f, 0.77f,   -7f),
            Triple(0.75f, 0.97f,   14f),
            Triple(0.49f, 0.80f,    8f),
            Triple(0.96f, 0.92f,  -13f),
            Triple(0.24f, 0.85f,    6f),
            Triple(0.69f, 0.85f,   11f),
            Triple(0.45f, 0.94f,   -4f),
            Triple(0.92f, 0.76f,    0f),
            Triple(0.04f, 0.18f,    0f),
            Triple(0.12f, 0.08f,    0f),
            Triple(0.10f, 0.95f,    0f)
        )

        val count = minOf(MAX_VISIBLE_SIGNS, signAssetNames.size, positions.size)

        return positions.take(count).map { (xFrac, yFrac, rotation) ->
            SignPlacement(
                x = (w * xFrac - half).coerceIn(0f, w - signSize),
                y = (h * yFrac - half).coerceIn(0f, h - signSize),
                sizePx = signSize,
                rotation = rotation
            )
        }
    }

    private fun navigationDelayMs(): Long {
        val visibleSignCount = minOf(MAX_VISIBLE_SIGNS, signAssetNames.size)
        if (visibleSignCount == 0) return FALLBACK_SPLASH_DELAY_MS
        return SIGN_START_DELAY_MS +
            (visibleSignCount - 1) * SIGN_INTERVAL_MS +
            SIGN_POP_DURATION_MS +
            SIGN_HOLD_AFTER_POP_MS
    }

    private fun decodeSampledBitmap(assetName: String, targetSizePx: Int): Bitmap? {
        // First pass: get image dimensions without loading pixels
        val boundsOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        assets.open("$SPLASH_SIGNS_FOLDER/$assetName").use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }

        val imageWidth = boundsOptions.outWidth
        val imageHeight = boundsOptions.outHeight
        if (imageWidth <= 0 || imageHeight <= 0) return null

        // Calculate downsample factor — target 2x the display size for quality
        var sampleSize = 1
        val targetPixels = targetSizePx * 2
        while (imageWidth / sampleSize > targetPixels && imageHeight / sampleSize > targetPixels) {
            sampleSize *= 2
        }

        // Second pass: decode at the reduced resolution
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        return assets.open("$SPLASH_SIGNS_FOLDER/$assetName").use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun animateIn(
        view: View,
        startDelay: Long,
        duration: Long
    ) {
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setStartDelay(startDelay)
            .setDuration(duration)
            .setInterpolator(OvershootInterpolator(0.9f))
            .start()
    }

    private fun openHome() {
        if (hasNavigated) return
        hasNavigated = true
        handler.removeCallbacks(navigateRunnable)
        signRunnables.forEach(handler::removeCallbacks)
        signRunnables.clear()

        val animators = mutableListOf<Animator>(
            ObjectAnimator.ofFloat(binding.splashContent, View.ALPHA, 1f, 0f),
            ObjectAnimator.ofFloat(binding.splashContent, View.TRANSLATION_Y, 0f, -20f),
            ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_X, binding.logoImage.scaleX, 1.05f),
            ObjectAnimator.ofFloat(binding.logoImage, View.SCALE_Y, binding.logoImage.scaleY, 1.05f)
        )

        val screenCenterY = binding.signsLayer.height / 2f
        val flyDistance = dpToPx(100).toFloat()

        activeSignViews.forEach { signView ->
            val signCenterY = signView.y + (signView.height / 2f)
            val isTopHalf = signCenterY < screenCenterY
            val targetTranslationY = signView.translationY + if (isTopHalf) -flyDistance else flyDistance

            animators += ObjectAnimator.ofFloat(signView, View.TRANSLATION_Y, signView.translationY, targetTranslationY)
            animators += ObjectAnimator.ofFloat(signView, View.ALPHA, signView.alpha, 0f)
        }

        val exitAnim = AnimatorSet().apply {
            playTogether(animators)
            duration = 350L
            interpolator = AccelerateDecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startActivity(Intent(this@LandingActivity, HomeActivity::class.java))
                    @Suppress("DEPRECATION")
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
        signRunnables.forEach(handler::removeCallbacks)
        signRunnables.clear()
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        activeSignViews.forEach(binding.signsLayer::removeView)
        activeSignViews.clear()
        super.onDestroy()
    }
}
