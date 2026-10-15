package com.byteclub.meetyourfriend.share

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WhatsAppLauncher {
    fun buildUrl(e164: String, message: String): String {
        val digits = e164.filter(Char::isDigit)
        val encoded = URLEncoder.encode(message, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "https://wa.me/$digits?text=$encoded"
    }

    fun launch(context: Context, e164: String, message: String) {
        val url = buildUrl(e164, message)
        for (packageName in listOf("com.whatsapp", "com.whatsapp.w4b")) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next supported WhatsApp package.
            }
        }
        val sms = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$e164"))
            .putExtra("sms_body", message)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(sms)
    }
}
