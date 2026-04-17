package com.example.tsrapp.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.tsrapp.R
import com.example.tsrapp.databinding.BottomSheetVoiceSettingsBinding
import com.example.tsrapp.util.SettingsManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class VoiceSettingsBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetVoiceSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVoiceSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupModeSelectors()
        setupVolumeSelectors()
        
        binding.closeButton.setOnClickListener { dismiss() }
        
        refreshUI()
    }

    private fun setupModeSelectors() {
        binding.modeMuted.setOnClickListener {
            updateVoiceMode(SettingsManager.VOICE_MUTED)
        }
        binding.modeAlerts.setOnClickListener {
            updateVoiceMode(SettingsManager.VOICE_ALERTS)
        }
        binding.modeUnmuted.setOnClickListener {
            updateVoiceMode(SettingsManager.VOICE_UNMUTED)
        }
    }

    private fun setupVolumeSelectors() {
        binding.volLouderRow.setOnClickListener {
            updateVolumeLevel(SettingsManager.VOL_LOUDER)
        }
        binding.volNormalRow.setOnClickListener {
            updateVolumeLevel(SettingsManager.VOL_NORMAL)
        }
        binding.volSofterRow.setOnClickListener {
            updateVolumeLevel(SettingsManager.VOL_SOFTER)
        }
    }

    private fun updateVoiceMode(mode: String) {
        SettingsManager.setVoiceMode(requireContext(), mode)
        refreshUI()
    }

    private fun updateVolumeLevel(level: String) {
        SettingsManager.setVolumeLevel(requireContext(), level)
        refreshUI()
    }

    private fun refreshUI() {
        val currentMode = SettingsManager.getVoiceMode(requireContext())
        val currentLevel = SettingsManager.getVolumeLevel(requireContext())

        // Update Pill Modes
        updatePillState(currentMode == SettingsManager.VOICE_MUTED, binding.bgMuted, binding.iconMuted, binding.textMuted)
        updatePillState(currentMode == SettingsManager.VOICE_ALERTS, binding.bgAlerts, binding.iconAlerts, binding.textAlerts)
        updatePillState(currentMode == SettingsManager.VOICE_UNMUTED, binding.bgUnmuted, binding.iconUnmuted, binding.textUnmuted)

        // Update Volume Checkmarks
        binding.checkLouder.visibility = if (currentLevel == SettingsManager.VOL_LOUDER) View.VISIBLE else View.GONE
        binding.checkNormal.visibility = if (currentLevel == SettingsManager.VOL_NORMAL) View.VISIBLE else View.GONE
        binding.checkSofter.visibility = if (currentLevel == SettingsManager.VOL_SOFTER) View.VISIBLE else View.GONE
    }

    private fun updatePillState(isSelected: Boolean, bg: View, icon: View, text: android.widget.TextView) {
        if (isSelected) {
            bg.setBackgroundResource(R.drawable.bg_pill_selected)
            icon.alpha = 1.0f
            text.setTextColor(requireContext().getColor(R.color.tsr_text_primary))
            text.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            bg.setBackgroundResource(android.R.color.transparent)
            icon.alpha = 0.4f
            text.setTextColor(requireContext().getColor(android.R.color.darker_gray))
            text.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "VoiceSettingsBottomSheet"
    }
}
