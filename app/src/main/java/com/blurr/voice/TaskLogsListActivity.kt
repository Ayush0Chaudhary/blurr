package com.blurr.voice

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blurr.voice.v2.logging.TaskLog
import com.blurr.voice.v2.logging.TaskLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TaskLogsListActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_logs_list)

        val recyclerView = findViewById<RecyclerView>(R.id.taskLogsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val logs = TaskLogger.getLogs(this)
        recyclerView.adapter = TaskLogsAdapter(logs) { log ->
            val intent = Intent(this, TaskLogDetailsActivity::class.java)
            intent.putExtra("uid", log.uid)
            startActivity(intent)
        }
    }

    class TaskLogsAdapter(
        private val logs: List<TaskLog>,
        private val onClick: (TaskLog) -> Unit
    ) : RecyclerView.Adapter<TaskLogsAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val textTimestamp: TextView = view.findViewById(android.R.id.text1)
            val textSummary: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            // Customize text colors for dark theme
            view.findViewById<TextView>(android.R.id.text1).setTextColor(0xFFFFFFFF.toInt())
            view.findViewById<TextView>(android.R.id.text2).setTextColor(0xFFAAAAAA.toInt())
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val log = logs[position]
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            holder.textTimestamp.text = dateFormat.format(Date(log.timestamp))
            holder.textSummary.text = log.input.take(100) + "..." // Show preview of input
            holder.itemView.setOnClickListener { onClick(log) }
        }

        override fun getItemCount() = logs.size
    }
}
