package com.pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import java.net.URI
import org.jsoup.nodes.Element

class Pusatfilm : MainAPI() {

    override var mainUrl = "http://2.56.10.87/"

    override var name = "Pusatfilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "film-terbaru/page/%d/" to "Film Terbaru",
                    "trending/page/%d/" to "Film Trending",
                    "genre/action/page/%d/" to "Film Action",
                    "series-terbaru/page/%d/" to "Series Terbaru",
                    "drama-korea/page/%d/" to "Drama Korea",
                    "west-series/page/%d/" to "West Series",
                    "drama-china/page/%d/" to "Drama China",
            )
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val data = request.data.format(page)
        val document = app.get("$mainUrl/$data").document
        val home = document.select("article.item").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.entry-title > a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("a")!!.attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr()).fixImageQuality()
        val quality =
            this.select("div.gmr-qual, div.gmr-quality-item > a").text().trim().replace("-", "")
        return if (quality.isEmpty()) {
            val episode =
                Regex("Episode\\s?([0-9]+)")
                    .find(title)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()
                    ?: this.select("div.gmr-numbeps > span").text().toIntOrNull()
            newAnimeSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                addSub(episode)
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                addQuality(quality)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document =
            app.get("${mainUrl}?s=$query&post_type[]=post&post_type[]=tv", timeout = 50L)
                .document
        val results = document.select("article.item").mapNotNull { it.toSearchResult() }
        return results
    }

    private fun Element.toRecommendResult(): SearchResponse? {
        val title = this.selectFirst("a > span.idmuvi-rp-title")?.text()?.trim() ?: return null
        val href = this.selectFirst("a")!!.attr("href")
        val posterUrl = fixUrlNull(this.selectFirst("a > img")?.getImageAttr().fixImageQuality())
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun load(url: String): LoadResponse {
        return super.load(url).apply {
            when (this) {
                is TvSeriesLoadResponse -> {
                    val document = app.get(url).document
                    this.episodes =
                        document.select("div.vid-episodes a, div.gmr-listseries a")
                            .map { eps ->
                                val href = fixUrl(eps.attr("href"))
                                val name = eps.attr("title")
                                val episode =
                                    "Episode\\s*(\\d+)"
                                        .toRegex()
                                        .find(name)
                                        ?.groupValues
                                        ?.get(1)
                                val season =
                                    "Season\\s*(\\d+)"
                                        .toRegex()
                                        .find(name)
                                        ?.groupValues
                                        ?.get(1)
                                Episode(
                                    href,
                                    name,
                                    season = season?.toIntOrNull(),
                                    episode = episode?.toIntOrNull(),
                                )
                            }
                            .filter { it.episode != null }
                }
            }
        }!!
    }

    private fun Element.getImageAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            this.hasAttr("data-lazy-src") -> this.attr("abs:data-lazy-src")
            this.hasAttr("srcset") -> this.attr("abs:srcset").substringBefore(" ")
            else -> this.attr("abs:src")
        }
    }

    private fun Element?.getIframeAttr(): String? {
        return this?.attr("data-litespeed-src").takeIf { it?.isNotEmpty() == true }
            ?: this?.attr("src")
    }

    private fun String?.fixImageQuality(): String? {
        if (this == null) return null
        val regex = Regex("(-\\d*x\\d*)").find(this)?.groupValues?.get(0) ?: return this
        return this.replace(regex, "")
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

}
