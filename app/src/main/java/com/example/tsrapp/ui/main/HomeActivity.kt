package com.example.tsrapp.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tsrapp.R
import com.example.tsrapp.databinding.ActivityHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener {
            showLiveDetectionNote()
        }
        binding.testModeButton.setOnClickListener {
            startActivity(Intent(this, TestModeActivity::class.java))
        }
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showLiveDetectionNote() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.live_scan_note_title)
            .setMessage(getString(R.string.live_scan_note_message))
            .setPositiveButton(R.string.live_scan_note_button) { _, _ ->
                startActivity(Intent(this, MainActivity::class.java))
            }
            .show()
    }
}
