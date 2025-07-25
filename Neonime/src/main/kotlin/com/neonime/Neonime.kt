package com.neonime

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element
import java.net.URI

class Neonime : MainAPI() {
    override var mainUrl = "https://otakupoi.org/neonime/"
    private var baseUrl = mainUrl
    override var name = "Neonime"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA", true) || t.contains("Special", true)) TvType.OVA
            else if (t.contains("Movie", true)) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Ended"  -> ShowStatus.Completed
                "OnGoing" -> ShowStatus.Ongoing
                "Ongoing" -> ShowStatus.Ongoing
                "In Production" -> ShowStatus.Ongoing
                "Returning Series" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/episode/page/" to "Episode Terbaru",
        "$mainUrl/tvshows/page/" to "Anime Terbaru",
        "$mainUrl/movies/page/" to "Movie",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val req = app.get(request.data + page)
        baseUrl = getBaseUrl(req.url)
        val home = req.document.select("tbody tr,div.item").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperAnimeLink(uri: String): String {
        return when {
            uri.contains("/episode") -> {
                val title = uri.substringAfter("$baseUrl/episode/").let { tt ->
                    val fixTitle = Regex("(.*)-\\d{1,2}x\\d+").find(tt)?.groupValues?.getOrNull(1).toString()
                    when {
                        !tt.contains("-season") && !tt.contains(Regex("-1x\\d+")) && !tt.contains("one-piece") -> "$fixTitle-season-${Regex("-(\\d{1,2})x\\d+").find(tt)?.groupValues?.getOrNull(1).toString()}"
                        tt.contains("-special") -> fixTitle.replace(Regex("-x\\d+"), "")
                        !fixTitle.contains("-subtitle-indonesia") -> "$fixTitle-subtitle-indonesia"
                        else -> fixTitle
                    }
                }

//                title = when {
//                    title.contains("youkoso-jitsuryoku") && !title.contains("-season") -> title.replace("-e-", "-e-tv-")
//                    else -> title
//                }

                "$baseUrl/tvshows/$title"
            }
            else -> uri
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse? {
        val title = this.selectFirst("td.bb a")?.ownText() ?: this.selectFirst("h2")?.text() ?: return null
        val href = getProperAnimeLink(fixUrl(this.select("a").attr("href")))
        val posterUrl = fixUrl(this.select("img").attr("data-src"))
        val epNum = this.selectFirst("td.bb span")?.text()?.let { eps ->
            Regex("Episode\\s?(\\d+)").find(eps)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$baseUrl/?s=$query").document
        return document.select("div.item.episode-home").mapNotNull {
            val title = it.selectFirst("div.judul-anime > span")!!.text()
            val poster = it.select("img").attr("data-src").trim()
            val episodes = it.selectFirst("div.fixyear > h2.text-center")!!
                .text().replace(Regex("\\D"), "").trim().toIntOrNull()
            val tvType = getType(it.selectFirst("span.calidad2.episode")?.text().toString())
            val href = getProperAnimeLink(fixUrl(it.selectFirst("a")!!.attr("href")))

            newAnimeSearchResponse(title, href, tvType) {
                this.posterUrl = poster
                addSub(episodes)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        if (url.contains("movie") || url.contains("live-action")) {
            val mTitle = document.selectFirst(".sbox > .data > h1[itemprop = name]")?.text().toString().replace("Subtitle Indonesia", "").trim()
            val mPoster = document.selectFirst(".sbox > .imagen > .fix > img[itemprop = image]")?.attr("data-src")
            val mTrailer = document.selectFirst("div.youtube_id iframe")?.attr("data-wpfc-original-src")?.substringAfterLast("html#")?.let{ "https://www.youtube.com/embed/$it"}
            val year = document.selectFirst("a[href*=release-year]")!!.text().toIntOrNull()
            val tracker = APIHolder.getTracker(listOf(mTitle),TrackerType.getTypes(TvType.Movie),year,true)
            return newMovieLoadResponse(name = mTitle, url = url, type = TvType.Movie, dataUrl = url) {
                posterUrl = tracker?.image ?: mPoster
                backgroundPosterUrl = tracker?.cover
                this.year = year
                plot = document.select("div[itemprop = description]").text().trim()
                rating = document.select("span[itemprop = ratingValue]").text().toIntOrNull()
                tags = document.select("p.meta_dd > a").map { it.text() }
                addTrailer(mTrailer)
                addMalId(tracker?.malId)
                addAniListId(tracker?.aniId?.toIntOrNull())
            }
        }
        else {
            val title = document.select("h1[itemprop = name]").text().replace("Subtitle Indonesia", "").trim()
            val poster = document.selectFirst(".imagen > img")?.attr("data-src")
            val trailer = document.selectFirst("div.youtube_id_tv iframe")?.attr("data-wpfc-original-src")?.substringAfterLast("html#")?.let{ "https://www.youtube.com/embed/$it"}
            val year = document.select("#info a[href*=\"-year/\"]").text().toIntOrNull()
            val episodes = document.select("ul.episodios > li").mapNotNull {
                val link = fixUrl(it.selectFirst(".episodiotitle > a")!!.attr("href"))
                val name = it.selectFirst(".episodiotitle > a")?.ownText().toString()
                val episode = Regex("(\\d+[.,]?\\d*)").find(name)?.groupValues?.getOrNull(0)?.toIntOrNull()
                Episode(link, episode = episode)
            }.reversed()
            val tracker = APIHolder.getTracker(listOf(title),TrackerType.getTypes(TvType.Anime),year,true)
            return newAnimeLoadResponse(title, url, TvType.Anime) {
                engName = title
                posterUrl = tracker?.image ?: poster
                backgroundPosterUrl = tracker?.cover
                this.year = year
                addEpisodes(DubStatus.Subbed, episodes)
                showStatus = getStatus(document.select("div.metadatac > span").last()!!.text().trim())
                plot = document.select("div[itemprop = description] > p").text().trim()
                tags = document.select("#info a[href*=\"-genre/\"]").map { it.text() }
                addTrailer(trailer)
                addMalId(tracker?.malId)
                addAniListId(tracker?.aniId?.toIntOrNull())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val source = if(data.contains("movie") || data.contains("live-action")) {
            app.get(data).document.select("#player2-1 > div[id*=div]").mapNotNull {
                fixUrl(it.select("iframe").attr("data-src"))
            }
        } else {
            app.get(data).document.select(".player2 > .embed2 > div[id*=player]").mapNotNull {
                fixUrl(it.select("iframe").attr("data-src"))
            }
        }

        source.apmap {
            loadExtractor(it, data, subtitleCallback, callback)
        }

        return true
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

}