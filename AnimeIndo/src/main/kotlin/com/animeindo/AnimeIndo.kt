package com.animeindo

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.httpsify
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class AnimeIndo : MainAPI() {
    override var mainUrl = "https://animeindo.xyz"
    override var name = "AnimeIndo"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)

    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA", true) || t.contains("Special")) TvType.OVA
            else if (t.contains("Movie", true)) TvType.AnimeMovie else TvType.Anime
        }

        fun getStatus(t: String): ShowStatus {
            return when (t) {
                "Finished Airing" -> ShowStatus.Completed
                "Currently Airing" -> ShowStatus.Ongoing
                else -> ShowStatus.Completed
            }
        }
    }

    override val mainPage =
            mainPageOf(
                    "" to "Latest Release",
                    "genres/action" to "Action",
                    "genres/adult-cast" to "Adult Cast",
                    "genres/adventure" to "Adventure",
                    "genres/Award-Winning" to "Award Winning",
                    "genres/comedy" to "Comedy",
                    "genres/drama" to "Drama",
                    "genres/ecchi" to "Ecchi",
                    "genres/isekai" to "Isekai",
                    "genres/fantasy" to "Fantasy",
                    "genres/harem" to "Harem",
                    "genres/historical" to "Historical",
                    "genres/martial-arts" to "Martial Arts",
                    "genres/military" to "Military",
                    "genres/music" to "Music",
                    "genres/mystery" to "Mystery",
                    "genres/parody" to "Parody",
                    "genres/psychological" to "Psychological",
                    "genres/romance" to "Romance",
                    "genres/school" to "School",
                    "genres/sci-fi" to "Sci-Fi",
                    "genres/sports" to "Sports",
                    "latest-release" to "Episode Terbaru",
                    "ongoing" to "Anime Ongoing",
                    "populer" to "Anime Populer",
                    "donghua-terbaru" to "Donghua Terbaru",
            )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page").document
        val home = document.select("article.bs").mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperAnimeLink(uri: String): String {
        return if (uri.contains("/anime/")) {
            uri
        } else {
            var title = uri.substringAfter("$mainUrl/")
            title =
                    when {
                        (title.contains("-episode")) && !(title.contains("-movie")) ->
                                title.substringBefore("-episode")
                        (title.contains("-movie")) -> title.substringBefore("-movie")
                        else -> title
                    }

            "$mainUrl/anime/$title"
        }
    }

    private fun Element.toSearchResult(): AnimeSearchResponse {
        val title = this.selectFirst("div.tt > h2")?.text()?.trim() ?: ""
        val href = getProperAnimeLink(this.selectFirst("a.tip")!!.attr("href"))
        val posterUrl = fixUrl(this.selectFirst("div.limit > img")?.attr("src") ?: "")
        val epNum =
                this.selectFirst("span.epx")
                        ?.ownText()
                        ?.replace(Regex("\\D"), "")
                        ?.trim()
                        ?.toIntOrNull()
        return newAnimeSearchResponse(title, href, TvType.Anime) {
            this.posterUrl = posterUrl
            addSub(epNum)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val anime = mutableListOf<SearchResponse>()
        (1..2).forEach { page ->
            val document = app.get("$mainUrl/?s=$query").document
            val media =
                    document.select("article.bs").mapNotNull {
                        val title = it.selectFirst("div.tt")!!.ownText().trim()
                        val href = it.selectFirst("a")!!.attr("href")
                        val posterUrl = it.selectFirst("div.limit > img")!!.attr("src")
                        val type = getType(it.select("div.type").text().trim())
                        newAnimeSearchResponse(title, href, type) { this.posterUrl = posterUrl }
                    }
            if (media.isNotEmpty()) anime.addAll(media)
        }
        return anime
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title =
                document.selectFirst("h1.entry-title")
                        ?.text()
                        ?.replace("Subtitle Indonesia", "")
                        ?.trim()
                        ?: return null
        val poster = document.selectFirst("div.thumb > img[itemprop=image]")?.attr("src")
        val tags = document.select("div.genxed > a").map { it.text() }
        val type =
                getType(
                        document.selectFirst("div.info-content > div.spe > span:contains(Type:)")
                                ?.ownText()
                                ?.trim()
                                ?.lowercase()
                                ?: "tv"
                )
        val year =
                document.selectFirst("div.info-content > div.spe > span:contains(Released:)")
                        ?.ownText()
                        ?.let {
                            Regex("\\d,\\s(\\d*)").find(it)?.groupValues?.get(1)?.toIntOrNull()
                        }
        val status =
                getStatus(
                        document.selectFirst("div.info-content > div.spe > span:nth-child(1)")!!
                                .ownText()
                                .trim()
                )
        val description = document.select("div[itemprop=description] > p").text()

        val trailer = document.selectFirst("div.player-embed iframe")?.attr("src")
        val episodes =
            document.select("div.eplister ul li")
                .mapNotNull { element ->
                    val headerAnchor = element.selectFirst("a") ?: return@mapNotNull null
                    val episodeTitleText = headerAnchor.text().trim()
                    val link = fixUrl(headerAnchor.attr("href"))
                    val episodeNumber = episodeTitleText.replace("Episode", "", ignoreCase = true).trim().toIntOrNull()
                    val runTimeString = element.selectFirst("span.duration")?.text()
                    val runTimeInSeconds = runTimeString?.let { rt ->
                        rt.filter { it.isDigit() }.toLongOrNull()?.times(60)
                    }
                    // Assuming newEpisode has a signature like:
                    // fun newEpisode(data: String, name: String?, episode: Int?, ..., runtime: Long?): Episode
                    newEpisode(link) {
                        this.data = link
                        this.name = episodeTitleText
                        this.episode = episodeNumber
                        //this.runtime = runTimeInSeconds
                        // other parameters...
                    }
                }
                .reversed()

        val recommendations =
                document.select("div.relat div.animposx").mapNotNull { it.toSearchResult() }

        val tracker = APIHolder.getTracker(listOf(title), TrackerType.getTypes(type), year, true)

        return newAnimeLoadResponse(title, url, type) {
            engName = title
            posterUrl = tracker?.image ?: poster
            backgroundPosterUrl = tracker?.cover
            this.year = year
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            plot = description
            this.tags = tags
            this.recommendations = recommendations
            addTrailer(trailer)
            addMalId(tracker?.malId)
            addAniListId(tracker?.aniId?.toIntOrNull())
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {

        val document = app.get(data).document
        document.select("div.mobius > select.mirror > option")
                .mapNotNull {
                    fixUrl(Jsoup.parse(base64Decode(it.attr("value"))).select("iframe").attr("src"))
                }
                .apmap {
                    if (it.startsWith(mainUrl)) {
                        app.get(it, referer = "$mainUrl/").document.select("iframe").attr("src")
                    } else {
                        it
                    }
                }
                .apmap { loadExtractor(httpsify(it), data, subtitleCallback, callback) }

        return true
    }
}
