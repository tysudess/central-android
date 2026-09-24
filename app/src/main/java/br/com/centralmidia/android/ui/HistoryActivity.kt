package br.com.centralmidia.android.ui

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.centralmidia.android.core.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = CentralDb(this)
        val root = Ui.page(this, "Histórico", "Atividades realizadas neste aparelho")
        val rv = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HistoryActivity)
        }
        var rows = db.history()
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
        val adapter = SimpleTextAdapter(
            rows.map { row ->
                "${fmt.format(Date(row[1].toLong()))} • ${row[2]}\n${row[3]}\n${row[4]}"
            },
            onClick = { i ->
                if (rows[i][5].isNotBlank()) openUrl(rows[i][5])
            },
        )
        rv.adapter = adapter
        root.addView(Ui.button(this, "Limpar histórico") {
            db.clearHistory()
            rows = db.history()
            adapter.submit(emptyList())
        }, Ui.lp())
        root.addView(
            rv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.heightPixels * 2 / 3,
            ),
        )
    }
}
