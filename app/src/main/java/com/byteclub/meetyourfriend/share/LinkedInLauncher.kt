package com.byteclub.meetyourfriend.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object LinkedInLauncher {
    fun searchUrl(name: String, organization: String): String {
        val keywords = "$name $organization".trim()
        val encoded = URLEncoder.encode(keywords, StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "https://www.linkedin.com/search/results/people/?keywords=$encoded"
    }

    fun open(context: Context, name: String, organization: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl(name, organization)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
