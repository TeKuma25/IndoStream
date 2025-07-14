package com.gomov

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.helper.AesHelper.cryptoAESHandler
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getQualityFromName

class Watchx : Chillx() {
    override val name = "Watchx"
    override val mainUrl = "https://watchx.top"
}

class Boosterx : Chillx() {
    override val name = "Boosterx"
    override val mainUrl = "https://boosterx.stream"
    override val requiresReferer = true
}

open class Chillx : ExtractorApi() {
    override val name = "Chillx"
    override val mainUrl = "https://chillx.top"
    override val requiresReferer = true

    companion object {
        private var key: String? = null

        suspend fun fetchKey(): String {
            return if (key != null) {
                key!!
            } else {
                val fetch =
                        app.get(
                                        "https://raw.githubusercontent.com/rushi-chavan/multi-keys/keys/keys.json"
                                )
                                .parsedSafe<Keys>()
                                ?.key
                                ?.get(0)
                                ?: throw ErrorLoadingException("Unable to get key")
                key = fetch
                key!!
            }
        }
    }

    @Suppress("NAME_SHADOWING")
    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val master =
                Regex("""JScript[\w+]?\s*=\s*'([^']+)""")
                        .find(
                                app.get(
                                                url,
                                                referer = url,
                                        )
                                        .text
                        )
                        ?.groupValues
                        ?.get(1)
        val key = fetchKey()
        val decrypt =
                cryptoAESHandler(master ?: "", key.toByteArray(), false)?.replace("\\", "")
                        ?: throw ErrorLoadingException("failed to decrypt")
        val source = Regex(""""?file"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitles = Regex("""subtitle"?:\s*"([^"]+)""").find(decrypt)?.groupValues?.get(1)
        val subtitlePattern = """\[(.*?)](https?://[^\s,]+)""".toRegex()
        val matches = subtitlePattern.findAll(subtitles ?: "")
        val languageUrlPairs =
                matches
                        .map { matchResult ->
                            val (language, url) = matchResult.destructured
                            decodeUnicodeEscape(language) to url
                        }
                        .toList()

        languageUrlPairs.forEach { (name, file) ->
            subtitleCallback.invoke(SubtitleFile(name, file))
        }
        // required
        val headers =
                mapOf(
                        "Accept" to "*/*",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Origin" to mainUrl,
                )

        generateM3u8(name, source ?: return, "$mainUrl/", headers = headers)
                .forEach(callback)
    }

    private fun decodeUnicodeEscape(input: String): String {
        val regex = Regex("u([0-9a-fA-F]{4})")
        return regex.replace(input) { it.groupValues[1].toInt(16).toChar().toString() }
    }

    data class Keys(@JsonProperty("chillx") val key: List<String>)
}

class Dhtpre : JWPlayer() {
    override val name = "Dhtpre"
    override val mainUrl = "https://dhtpre.com"
}

open class JWPlayer : ExtractorApi() {
    override val name = "JWPlayer"
    override val mainUrl = "https://www.jwplayer.com"
    override val requiresReferer = false

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val sources = mutableListOf<ExtractorLink>()
        with(app.get(url).document) {
            val data =
                    this.select("script").mapNotNull { script ->
                        if (script.data().contains("sources: [")) {
                            script.data()
                                    .substringAfter("sources: [")
                                    .substringBefore("],")
                                    .replace("'", "\"")
                        } else if (script.data().contains("otakudesu('")) {
                            script.data().substringAfter("otakudesu('").substringBefore("');")
                        } else {
                            null
                        }
                    }

            tryParseJson<List<ResponseSource>>("$data")?.map {
                sources.add(
                        ExtractorLink(
                                name,
                                name,
                                it.file,
                                referer = url,
                                quality =
                                        getQualityFromName(
                                                Regex("(\\d{3,4}p)")
                                                        .find(it.file)
                                                        ?.groupValues
                                                        ?.get(1)
                                        )
                        )
                )
            }
        }
        return sources
    }

    private data class ResponseSource(
            @JsonProperty("file") val file: String,
            @JsonProperty("type") val type: String?,
            @JsonProperty("label") val label: String?
    )
}

class Filelions : VidHidePro() {
    override var mainUrl = "https://filelions.site"
}

open class VidHidePro : ExtractorApi() {
    override val name = "VidHidePro"
    override val mainUrl = "https://vidhidepro.com"
    override val requiresReferer = true

    override suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val headers =
                mapOf(
                        "Accept" to "*/*",
                        "Connection" to "keep-alive",
                        "Sec-Fetch-Dest" to "empty",
                        "Sec-Fetch-Mode" to "cors",
                        "Sec-Fetch-Site" to "cross-site",
                        "Origin" to "$mainUrl/",
                        "User-Agent" to USER_AGENT,
                )

        val response = app.get(getEmbedUrl(url), referer = referer)
        val script =
                if (!getPacked(response.text).isNullOrEmpty()) {
                    getAndUnpack(response.text)
                } else {
                    response.document.selectFirst("script:containsData(sources:)")?.data()
                }
        val m3u8 =
                Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        generateM3u8(name, m3u8 ?: return, mainUrl, headers = headers).forEach(callback)
    }

    private fun getEmbedUrl(url: String): String {
        return when {
            url.contains("/d/") -> url.replace("/d/", "/v/")
            url.contains("/download/") -> url.replace("/download/", "/v/")
            url.contains("/file/") -> url.replace("/file/", "/v/")
            else -> url.replace("/f/", "/v/")
        }
    }
}
