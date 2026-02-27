package com.example.tsrapp.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivitySettingsBinding
import com.example.tsrapp.util.SettingsManager
import com.example.tsrapp.util.TextToSpeechHelper
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private var ttsHelper: TextToSpeechHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener { finish() }

        setupToggles()
        setupRegion()
        setupModel()
        setupThreshold()
        setupTtsTest()
    }

    private fun setupToggles() {
        binding.ttsSwitch.isChecked = SettingsManager.isTtsEnabled(this)
        binding.showBoxesSwitch.isChecked = SettingsManager.isShowBoxes(this)

        binding.ttsSwitch.setOnCheckedChangeListener { _, c -> SettingsManager.setTtsEnabled(this, c) }
        binding.showBoxesSwitch.setOnCheckedChangeListener { _, c -> SettingsManager.setShowBoxes(this, c) }
    }

    private fun setupRegion() {
        val regions = listOf("EU", "US", "ASIA")
        val currentRegion = SettingsManager.getRegion(this)
        updateRegionButtons(currentRegion)

        binding.regionEu.setOnClickListener { selectRegion("EU") }
        binding.regionUs.setOnClickListener { selectRegion("US") }
        binding.regionAsia.setOnClickListener { selectRegion("ASIA") }
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
        binding.regionAsia.setBackgroundResource(if (selected == "ASIA") R.drawable.bg_region_active else R.drawable.bg_region_inactive)

        binding.regionEu.setTextColor(if (selected == "EU") activeColor else inactiveColor)
        binding.regionUs.setTextColor(if (selected == "US") activeColor else inactiveColor)
        binding.regionAsia.setTextColor(if (selected == "ASIA") activeColor else inactiveColor)
    }

    private fun setupModel() {
        val models = listOf("CNN v2.1", "CNN v1.8", "Lite v2.0")
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
        binding.confidenceSlider.value = current * 100f
        binding.confidenceValue.text = getString(R.string.settings_conf_threshold_value, (current * 100).toInt())

        binding.confidenceSlider.addOnChangeListener(Slider.OnChangeListener { _, value, _ ->
            SettingsManager.setConfidenceThreshold(this, value / 100f)
            binding.confidenceValue.text = getString(R.string.settings_conf_threshold_value, value.toInt())
        })
    }

    private fun setupTtsTest() {
        // TTS test is triggered by the voice switch long-press or we can add a separate button if needed.
        // For now, a long-press on the voice row speaks the test phrase.
        binding.ttsSwitch.setOnLongClickListener {
            if (SettingsManager.isTtsEnabled(this)) {
                if (ttsHelper == null) ttsHelper = TextToSpeechHelper(this)
                ttsHelper?.initialize { success ->
                    if (success) ttsHelper?.speak(getString(R.string.settings_tts_test_phrase))
                }
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsHelper?.shutdown()
        ttsHelper = null
    }
}
