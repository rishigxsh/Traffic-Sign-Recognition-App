package com.example.tsrapp.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivitySettingsBinding
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.TextToSpeechHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var ttsHelper: TextToSpeechHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Handle edge-to-edge and system bar insets
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        binding.backButton.setOnClickListener { finish() }

        // Start non-essential UI logic after first frame to avoid ANR
        binding.root.post {
            setupToggles()
            setupRegion()
            setupModel()
            setupThreshold()
            // setupTtsTest()
        }
    }

    private fun setupToggles() {
        binding.ttsSwitch.isChecked = SettingsManager.isTtsEnabled(this)
        binding.showBoxesSwitch.isChecked = SettingsManager.isShowBoxes(this)
        
        val currentTheme = SettingsManager.getThemeMode(this)
        binding.nightModeSwitch.isChecked = (currentTheme == SettingsManager.THEME_DARK)

        binding.ttsSwitch.setOnCheckedChangeListener { _, c -> SettingsManager.setTtsEnabled(this, c) }
        binding.showBoxesSwitch.setOnCheckedChangeListener { _, c -> SettingsManager.setShowBoxes(this, c) }
        
        binding.nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val mode = if (isChecked) SettingsManager.THEME_DARK else SettingsManager.THEME_LIGHT
            SettingsManager.setThemeMode(this, mode)
            SettingsManager.applyTheme(mode)
        }
    }

    private fun setupRegion() {
        val currentRegion = SettingsManager.getRegion(this)
        updateRegionButtons(currentRegion)

        binding.regionEu.setOnClickListener { selectRegion("EU") }
        binding.regionUs.setOnClickListener { selectRegion("US") }
    }

    private fun selectRegion(region: String) {
        SettingsManager.setRegion(this, region)
        updateRegionButtons(region)
    }

    private fun updateRegionButtons(selected: String) {
        val activeColor = getColor(R.color.white)
        val inactiveColor = getColor(R.color.tsr_text_secondary)

        binding.regionEu.setBackgroundResource(if (selected == "EU") R.drawable.bg_region_active else R.drawable.bg_region_inactive)
        binding.regionUs.setBackgroundResource(if (selected == "US") R.drawable.bg_region_active else R.drawable.bg_region_inactive)

        binding.regionEu.setTextColor(if (selected == "EU") activeColor else inactiveColor)
        binding.regionUs.setTextColor(if (selected == "US") activeColor else inactiveColor)
    }

    private fun setupModel() {
        var currentModel = "CNN v2.1"
        updateModelButtons(currentModel)

        binding.modelCnnV2.setOnClickListener { currentModel = "CNN v2.1"; updateModelButtons(currentModel) }
        binding.modelCnnV1.setOnClickListener { currentModel = "CNN v1.8"; updateModelButtons(currentModel) }
        binding.modelLite.setOnClickListener { currentModel = "Lite v2.0"; updateModelButtons(currentModel) }
    }

    private fun updateModelButtons(selected: String) {
        val activeColor = getColor(R.color.white)
        val inactiveColor = getColor(R.color.tsr_text_secondary)

        binding.modelCnnV2.setBackgroundResource(if (selected == "CNN v2.1") R.drawable.bg_region_active else R.drawable.bg_region_inactive)
        binding.modelCnnV1.setBackgroundResource(if (selected == "CNN v1.8") R.drawable.bg_region_active else R.drawable.bg_region_inactive)
        binding.modelLite.setBackgroundResource(if (selected == "Lite v2.0") R.drawable.bg_region_active else R.drawable.bg_region_inactive)

        binding.modelCnnV2.setTextColor(if (selected == "CNN v2.1") activeColor else inactiveColor)
        binding.modelCnnV1.setTextColor(if (selected == "CNN v1.8") activeColor else inactiveColor)
        binding.modelLite.setTextColor(if (selected == "Lite v2.0") activeColor else inactiveColor)
    }

    private fun setupThreshold() {
        val current = SettingsManager.getConfidenceThreshold(this)
        binding.confidenceSlider.value = (Math.round(current * 100f / 5f) * 5f).coerceIn(0f, 100f)
        binding.confidenceValue.text = getString(R.string.settings_conf_threshold_value, (current * 100).toInt())

        binding.confidenceSlider.addOnChangeListener { _, value, _ ->
            SettingsManager.setConfidenceThreshold(this, value / 100f)
            binding.confidenceValue.text = getString(R.string.settings_conf_threshold_value, value.toInt())
        }
    }

    /*
    private fun setupTtsTest() {
        binding.testTtsButton.setOnClickListener {
            if (!SettingsManager.isTtsEnabled(this)) {
                Toast.makeText(this, R.string.settings_tts_turn_on_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Lazy initialize ttsHelper only when needed
            if (ttsHelper == null) {
                ttsHelper = TextToSpeechHelper(this)
            }
            
            ttsHelper?.initialize { success ->
                if (success) {
                    ttsHelper?.speak(getString(R.string.settings_tts_test_phrase))
                } else {
                    Toast.makeText(
                        this,
                        "Text-to-speech failed to start. Install Google Text-to-Speech in Play Store, " +
                            "then download a voice in Android Settings.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    */

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper?.shutdown()
        ttsHelper = null
    }
}
