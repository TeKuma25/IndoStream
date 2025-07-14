package com.nodrakorid

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import java.net.URI
import org.jsoup.nodes.Element

class Nodrakorid : MainAPI() {
    companion object {
        // Cache for the updated URL to avoid repeated lookups
        private var updatedMainUrl: String? = null
    }

    // Helper function to fetch the latest URL
    private suspend fun updateUrl() {
        if (updatedMainUrl == null) {
            try {
                val doc = app.get("https://link.nodrakorid.info/").document
                val newUrl = doc.selectFirst("a.button")?.attr("href")?.ifBlank { null }
                if (newUrl != null && newUrl.startsWith("http")) {
                    updatedMainUrl = newUrl
                }
            } catch (e: Exception) {
                println("[$name] Failed to update URL from link.nodrakorid.info: ${e.message}")
            }
        }
        updatedMainUrl?.let { mainUrl = it }
    }

    override var mainUrl = "https://nodrakor22.store"
    override var name = "Nodrakorid"
    override val supportedTypes = setOf(TvType.Movie)

    override val mainPage =
            mainPageOf(
                    "genre/movie/page/%d/" to "Film Terbaru",
                    "genre/drama/page/%d/" to "Drama Korea",
                    "genre/c-drama/page/%d/" to "Drama China",
                    "genre/fantasy/page/%d/" to "Fantasy",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        updateUrl()
        return super.getMainPage(page, request)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        updateUrl()
        val doc = app.get("$mainUrl/?s=$query").document
        // Assuming a selector for search results. This might need to be adjusted.
        return doc.select("div.gmr-box-content article").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        updateUrl()
        val response = super.load(url) ?: throw Exception("Failed to load response")
        return response.apply {
            when (this) {
                is TvSeriesLoadResponse -> {
                    val doc = app.get(url).document
                    this.comingSoon = false
                    this.episodes =
                            when {
                                doc.select("div.vid-episodes a, div.gmr-listseries a")
                                        .isNotEmpty() -> this.episodes
                                doc.select("div#download").isEmpty() -> {
                                    doc.select("div.entry-content p:contains(Episode)")
                                            .distinctBy { it.text() }
                                            .map { eps ->
                                                val endSibling =
                                                        eps.nextElementSiblings()
                                                                .select("p:contains(Episode)")
                                                                .firstOrNull()
                                                                ?: eps.nextElementSiblings()
                                                                        .select(
                                                                                "div.content-moviedata"
                                                                        )
                                                                        .firstOrNull()
                                                val siblings =
                                                        eps.nextElementSiblingsUntil(endSibling)
                                                                .map { ele ->
                                                                    ele.ownText()
                                                                            .filter { it.isDigit() }
                                                                            .toIntOrNull() to
                                                                            ele.select("a").map {
                                                                                it.attr("href") to
                                                                                        it.text()
                                                                            }
                                                                }
                                                                .filter { it.first != null }
                                                Episode(
                                                        siblings.toJson(),
                                                        episode = eps.text().toEpisode()
                                                )
                                            }
                                }
                                else -> {
                                    doc.select("div#download h3.title-download").mapNotNull { eps ->
                                        val siblings =
                                                eps.nextElementSibling()
                                                        ?.select("li")
                                                        ?.map { ele ->
                                                            ele.text()
                                                                    .filter { it.isDigit() }
                                                                    .toIntOrNull() to
                                                                    ele.select("a").map {
                                                                        it.attr("href") to
                                                                                it.text()
                                                                                        .split(" ")
                                                                                        .first()
                                                                    }
                                                        }
                                                        ?.filter { it.first != null }
                                        Episode(
                                                siblings?.toJson() ?: return@mapNotNull null,
                                                episode = eps.text().toEpisode()
                                        )
                                    }
                                }
                            }
                }
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        updateUrl()
        return if (data.startsWith("[")) {
            tryParseJson<ArrayList<LinkData>>(data)?.filter { it.first != 360 }?.map {
                it.second.apmap { link ->
                    loadFixedExtractor(
                            fixEmbed(link.first, link.second),
                            it.first,
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                    )
                }
            }
            true
        } else {
            super.loadLinks(data, isCasting, subtitleCallback, callback)
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2.title-archive")?.text() ?: ""
        val href = this.selectFirst("a")?.attr("href") ?: ""
        val posterUrl = this.selectFirst("img")?.attr("src")

        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    private fun fixEmbed(url: String, server: String): String {
        return when {
            server.contains("streamsb", true) -> {
                val host = getBaseUrl(url)
                url.replace("$host/", "$host/e/")
            }
            server.contains("hxfile", true) -> {
                val host = getBaseUrl(url)
                val id = url.substringAfterLast("/")
                "$host/embed-$id.html"
            }
            else -> url
        }
    }

    private fun String.toEpisode(): Int? {
        return Regex("(?i)Episode\\s?([0-9]+)").find(this)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    private suspend fun loadFixedExtractor(
            url: String,
            quality: Int?,
            referer: String? = null,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        loadExtractor(url, referer, subtitleCallback) { link ->
            callback.invoke(
                    ExtractorLink(
                            link.name,
                            link.name,
                            link.url,
                            link.referer,
                            if (link.type == ExtractorLinkType.M3U8) link.quality
                            else quality ?: Qualities.Unknown.value,
                            link.type,
                            link.headers,
                            link.extractorData
                    )
            )
        }
    }

    private fun Element.nextElementSiblingsUntil(untilElement: Element?): List<Element> {
        val siblings = mutableListOf<Element>()
        var currentElement = this.nextElementSibling()

        while (currentElement != null && currentElement != untilElement) {
            siblings.add(currentElement)
            currentElement = currentElement.nextElementSibling()
        }

        return siblings
    }

    data class LinkData(
            @JsonProperty("first") var first: Int? = null,
            @JsonProperty("second") var second: ArrayList<Second> = arrayListOf()
    )

    data class Second(
            @JsonProperty("first") var first: String,
            @JsonProperty("second") var second: String
    )
}
