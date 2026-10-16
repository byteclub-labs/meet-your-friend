package com.byteclub.meetyourfriend.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.byteclub.meetyourfriend.data.Connect
import java.io.File
import java.time.Instant

object CsvExporter {
    private val columns = listOf(
        "id", "name", "organization", "role", "mobile", "linkedinUrl", "metAt",
        "whatsappSentAt", "briefConfident", "notes",
    )

    fun toCsv(connects: List<Connect>): String = buildString {
        appendLine(columns.joinToString(","))
        connects.forEach { connect ->
            val values = listOf(
                connect.id.toString(),
                connect.name,
                connect.organization,
                connect.role,
                connect.mobileE164,
                connect.linkedinUrl.orEmpty(),
                connect.metAt.toString(),
                connect.whatsappSentAt?.toString().orEmpty(),
                connect.briefConfident?.toString().orEmpty(),
                connect.notes.orEmpty(),
            )
            appendLine(values.joinToString(",") { escape(it) })
        }
    }

    fun writeAndShare(context: Context, connects: List<Connect>) {
        val file = File(context.cacheDir, "meet-your-friend-connects.csv")
        file.writeText(toCsv(connects))
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private fun escape(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
