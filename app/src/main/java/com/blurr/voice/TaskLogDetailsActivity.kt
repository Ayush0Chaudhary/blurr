package com.blurr.voice

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.blurr.voice.v2.logging.TaskLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskLogDetailsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_log_details)

        val uid = intent.getStringExtra("uid") ?: return
        val log = TaskLogger.getLog(this, uid) ?: return

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        findViewById<TextView>(R.id.textTimestamp).text = dateFormat.format(Date(log.timestamp))
        findViewById<TextView>(R.id.textInput).text = log.input
        findViewById<TextView>(R.id.textOutput).text = log.output
    }
}
