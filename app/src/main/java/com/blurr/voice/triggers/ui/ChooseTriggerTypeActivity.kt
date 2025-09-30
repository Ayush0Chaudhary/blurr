package com.blurr.voice.triggers.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.blurr.voice.R
import com.blurr.voice.triggers.TriggerType

class ChooseTriggerTypeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_trigger_type)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        findViewById<CardView>(R.id.scheduledTimeCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.SCHEDULED_TIME)
        }

        findViewById<CardView>(R.id.notificationCard).setOnClickListener {
            launchCreateTriggerActivity(TriggerType.NOTIFICATION)
        }
    }

    private fun launchCreateTriggerActivity(triggerType: TriggerType) {
        val intent = Intent(this, CreateTriggerActivity::class.java).apply {
            putExtra("EXTRA_TRIGGER_TYPE", triggerType)
        }
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
