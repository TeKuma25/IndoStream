package com.useetv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import org.jsoup.nodes.Element

class UseeTv : MainAPI() {
    override var mainUrl = "https://www.indihometv.com"
    override var name = "Useetv"
    override var lang = "id"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage =
            mainPageOf(
                    "$mainUrl/home/content/kids-3" to "Kids",
                    "$mainUrl/home/content/sport-4" to "Sports",
                    "$mainUrl/vodpremium/category/sport" to "VOD Sports",
                    "$mainUrl/vodpremium/category/yang-bisa-bikin-happy-minggu-ini" to
                            "VOD Yang bisa bikin happy minggu ini",
                    "$mainUrl/vodpremium/category/fun-planet" to "VOD Fun Planet",
                    "$mainUrl/vodpremium/category/film-gratis-untuk-kamu" to
                            "VOD Film Gratis Untuk Kamu",
                    "$mainUrl/home/content/tv-series-12" to "TV Series",
                    "$mainUrl/home/content/documentary-1" to "Documentary",
                    "$mainUrl/home/content/news-9" to "News",
                    "$mainUrl/home/content/lifestyle-5" to "Lifestyle",
                    "$mainUrl/home/content/knowledge-43" to "Knowledge",
                    "$mainUrl/home/content/movies-44" to "Movies",
                    "$mainUrl/home/content/entertainment-45" to "Entertainment",
                    "$mainUrl/home/content/music-46" to "Music",
            )

    companion object {
        private const val mainLink = "https://www.indihometv.com"
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/tv/live").document

        val home =
                listOf(
                                Pair("col-xs-4", "Semua"),
                                Pair("local", "Local"),
                                Pair("sport", "Sports"),
                                Pair("news", "News"),
                                Pair("movies", "Movies"),
                                Pair("music", "Music"),
                                Pair("kids", "Kids"),
                                Pair("knowledge", "Knowledge"),
                                Pair("lifestyle", "Lifestyle"),
                                Pair("entertainment", "Entertainment"),
                                Pair("religi", "Religion"),
                        )
                        .map { (soap, name) ->
                            val home =
                                    document.select("div#channelContainer div.col-channel")
                                            .mapNotNull { it }
                                            .filter { it.attr("class").contains(soap, true) }
                                            .mapNotNull { it.toSearchResult() }
                            HomePageList(name, home, true)
                        }
                        .filter { it.list.isNotEmpty() }
        return HomePageResponse(home)
    }

    private fun Element.toSearchResult(): LiveSearchResponse? {
        return LiveSearchResponse(
                this.selectFirst("a")?.attr("data-name") ?: return null,
                fixUrl(this.selectFirst("a")!!.attr("href")),
                this@UseeTv.name,
                TvType.Live,
                fixUrlNull(this.select("img").attr("data-src")),
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return app.get("$mainUrl/search?keyword=$query")
                .document
                .select("div#channelContainer div.col-channel")
                .mapNotNull { it }
                .filter { it.select("a").attr("data-name").contains(query, true) }
                .mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val content = document.selectFirst("div.d-flex.video-schedule-time p")?.text()?.split("•")

        // Perbaikan: Gunakan Regex object dengan benar
        val scriptContent =
                document.select("script")
                        .find { scriptElement ->
                            val pattern = Regex("var v\\d+ = 'http", RegexOption.IGNORE_CASE)
                            scriptElement.data().contains(pattern)
                        }
                        ?.data()

        val m3u8Url =
                scriptContent
                        ?.let {
                            """var v\d+ = '([^']+)';""".toRegex().find(it)?.groupValues?.get(1)
                        }
                        ?.takeIf { it.contains(".m3u8") }

        requireNotNull(m3u8Url) { "Gagal mengekstrak URL M3U8" }

        return LiveStreamLoadResponse(
                content?.firstOrNull()?.trim() ?: "Live Stream",
                url,
                this.name,
                m3u8Url,
                fixUrlNull(document.selectFirst("div.row.video-schedule img")?.attr("src")),
                plot = document.selectFirst("title")?.text()
        )
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        // Gunakan domain streaming sebagai referrer
        M3u8Helper.generateM3u8(this.name, data, "https://streaming.indihometv.com")
                .forEach(callback)

        return true
    }
}
