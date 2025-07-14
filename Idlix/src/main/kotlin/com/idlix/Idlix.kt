package com.idlix

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import java.net.URI
import java.util.Base64
import org.jsoup.nodes.Element

class Idlix : MainAPI() {
    override var mainUrl = "https://tv7.idlix.asia"
    private var directUrl = mainUrl
    override var name = "Idlix Asia"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes =
            setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama)

    override val mainPage =
            mainPageOf(
                    "$mainUrl/" to "Featured",
                    "$mainUrl/trending/page/?get=movies" to "Trending Movies",
                    "$mainUrl/trending/page/?get=tv" to "Trending TV Series",
                    "$mainUrl/movie/page/" to "Film Terbaru",
                    "$mainUrl/genre/action/page/" to "Film Action",
                    "$mainUrl/genre/drama-korea/page/" to "Drama Korea",
                    "$mainUrl/genre/anime/page/" to "Anime",
                    "$mainUrl/tvseries/page/" to "Serial TV",
                    "$mainUrl/season/page/" to "Season Terbaru",
                    "$mainUrl/episode/page/" to "Episode Terbaru",
            )

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data.split("?")
        val nonPaged = request.name == "Featured" && page <= 1
        val req =
                if (nonPaged) {
                    app.get(request.data)
                } else {
                    app.get("${url.first()}$page/?${url.lastOrNull()}")
                }
        mainUrl = getBaseUrl(req.url)
        val document = req.document
        val home =
                (if (nonPaged) {
                            document.select("div.items.featured article")
                        } else {
                            document.select("div.items.full article, div#archive-content article")
                        })
                        .mapNotNull { it.toSearchResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun getProperLink(uri: String): String {
        return when {
            uri.contains("/episode/") -> {
                var title = uri.substringAfter("$mainUrl/episode/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }
            uri.contains("/season/") -> {
                var title = uri.substringAfter("$mainUrl/season/")
                title = Regex("(.+?)-season").find(title)?.groupValues?.get(1).toString()
                "$mainUrl/tvseries/$title"
            }
            else -> {
                uri
            }
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h3 > a")!!.text().replace(Regex("\\(\\d{4}\\)"), "").trim()
        val href = getProperLink(this.selectFirst("h3 > a")!!.attr("href"))
        val posterUrl = this.select("div.poster > img").attr("src")
        val quality = getQualityFromString(this.select("span.quality").text())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val req = app.get("$mainUrl/search/$query")
        mainUrl = getBaseUrl(req.url)
        val document = req.document
        return document.select("div.result-item").map {
            val title =
                    it.selectFirst("div.title > a")!!
                            .text()
                            .replace(Regex("\\(\\d{4}\\)"), "")
                            .trim()
            val href = getProperLink(it.selectFirst("div.title > a")!!.attr("href"))
            val posterUrl = it.selectFirst("img")!!.attr("src")
            newMovieSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        directUrl = getBaseUrl(request.url)
        val document = request.document
        val title =
                document.selectFirst("div.data > h1")
                        ?.text()
                        ?.replace(Regex("\\(\\d{4}\\)"), "")
                        ?.trim()
                        .toString()
        val poster = document.select("div.poster > img").attr("src")
        val tags = document.select("div.sgeneros > a").map { it.text() }

        val year =
                Regex(",\\s?(\\d+)")
                        .find(document.select("span.date").text().trim())
                        ?.groupValues
                        ?.get(1)
                        .toString()
                        .toIntOrNull()
        val tvType =
                if (document.select("ul#section > li:nth-child(1)").text().contains("Episodes"))
                        TvType.TvSeries
                else TvType.Movie
        val description = document.select("div.wp-content > p").text().trim()
        val trailer = document.selectFirst("div.embed iframe")?.attr("src")
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()?.toRatingInt()
        val actors =
                document.select("div.persons > div[itemprop=actor]").map {
                    Actor(
                            it.select("meta[itemprop=name]").attr("content"),
                            it.select("img").attr("src")
                    )
                }

        val recommendations =
                document.select("div.owl-item").map {
                    val recName =
                            it.selectFirst("a")!!
                                    .attr("href")
                                    .removeSuffix("/")
                                    .split("/")
                                    .last()
                    val recHref = it.selectFirst("a")!!.attr("href")
                    val recPosterUrl = it.selectFirst("img")?.attr("src").toString()
                    newTvSeriesSearchResponse(recName, recHref, TvType.TvSeries) {
                        this.posterUrl = recPosterUrl
                    }
                }

        return if (tvType == TvType.TvSeries) {
            val episodes =
                    document.select("ul.episodios > li").map {
                        val href = it.select("a").attr("href")
                        val name = fixTitle(it.select("div.episodiotitle > a").text().trim())
                        val image = it.select("div.imagen > img").attr("src")
                        val episode =
                                it.select("div.numerando")
                                        .text()
                                        .replace(" ", "")
                                        .split("-")
                                        .last()
                                        .toIntOrNull()
                        val season =
                                it.select("div.numerando")
                                        .text()
                                        .replace(" ", "")
                                        .split("-")
                                        .first()
                                        .toIntOrNull()
                        Episode(href, name, season, episode, image)
                    }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        directUrl = getBaseUrl(document.location())

        document.select("ul#playeroptionsul > li")
                .map { Triple(it.attr("data-post"), it.attr("data-nume"), it.attr("data-type")) }
                .apmap { (id, nume, type) ->
                    try {
                        val json =
                                app.post(
                                                url = "$directUrl/wp-admin/admin-ajax.php",
                                                data =
                                                        mapOf(
                                                                "action" to "doo_player_ajax",
                                                                "post" to id,
                                                                "nume" to nume,
                                                                "type" to type
                                                        ),
                                                referer = data,
                                                headers =
                                                        mapOf(
                                                                "Accept" to "*/*",
                                                                "X-Requested-With" to
                                                                        "XMLHttpRequest"
                                                        )
                                        )
                                        .parsedSafe<ResponseHash>()
                                        ?: return@apmap

                        val password = createKey(json.key, json.embedurl)
                        val decrypted = CryptoJsAes.decrypt(json.embedurl, password) ?: return@apmap

                        val embedJson =
                                AppUtils.tryParseJson<Map<String, String>>(decrypted as String?)
                                        ?: return@apmap
                        val hash = embedJson["m"]?.split("/")?.last() ?: return@apmap

                        getUrl(
                                url =
                                        "https://jeniusplay.com/player/index.php?data=$hash&do=getVideo",
                                referer = directUrl,
                                subtitleCallback = subtitleCallback,
                                callback = callback
                        )
                    } catch (e: Exception) {
                        println("Error processing player: ${e.message}")
                    }
                }

        return true
    }

    /** FUNGSI CREATEKEY VERSI FIXED */
    private fun createKey(r: String, m: String): String {
        val rList = r.chunked(4).map { it.substring(2) }
        val reversedM = m.reversed()

        val paddedM = addBase64Padding(reversedM)
        val decodedBytes =
                Base64.getDecoder().decode(paddedM) // Menggunakan Base64.getDecoder().decode
        val decodedM = String(decodedBytes, Charsets.UTF_8)

        return decodedM.split("|").joinToString("") { "\\x${rList.getOrNull(it.toInt()) ?: "00"}" }
    }

    /** FUNGSI BANTUAN UNTUK BASE64 PADDING */
    private fun addBase64Padding(input: String): String {
        return input + "=".repeat((4 - input.length % 4) % 4)
    }

    private suspend fun getUrl(
            url: String,
            referer: String?,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        try {
            // Step 1: Dapatkan link M3U8
            val m3uResponse =
                    app.post(
                                    url = url,
                                    data =
                                            mapOf(
                                                    "hash" to url.split("data=").last(),
                                                    "r" to (referer ?: "")
                                            ),
                                    headers =
                                            mapOf(
                                                    "X-Requested-With" to "XMLHttpRequest",
                                                    "Content-Type" to
                                                            "application/x-www-form-urlencoded; charset=UTF-8"
                                            )
                            )
                            .parsedSafe<ResponseSource>()
                            ?: return

            // Step 2: Generate M3U8
            M3u8Helper.generateM3u8(name, m3uResponse.videoSource, referer ?: directUrl)
                    .forEach(callback)

            // Step 3: Cari subtitle
            val document = app.get(url, referer = referer).document
            document.select("script")
                    .find { script -> script.data().contains("eval(function(p,a,c,k,e,d)") }
                    ?.let { script ->
                        val subData =
                                getAndUnpack(script.data())
                                        .substringAfter("\"tracks\":[")
                                        .substringBefore("],")

                        AppUtils.tryParseJson<List<Tracks>>("[$subData]")?.map { subtitle ->
                            subtitleCallback.invoke(
                                    SubtitleFile(getLanguage(subtitle.label ?: ""), subtitle.file)
                            )
                        }
                    }
        } catch (e: Exception) {
            println("Error in getUrl: ${e.message}")
        }
    }

    /** FUNGSI BANTUAN LANGUAGE */
    private fun getLanguage(str: String): String {
        return when {
            str.contains("indonesia", true) -> "Indonesian"
            str.contains("english", true) -> "English"
            else -> str
        }
    }

    data class ResponseSource(
            @JsonProperty("hls") val hls: Boolean,
            @JsonProperty("videoSource") val videoSource: String,
            @JsonProperty("securedLink") val securedLink: String?,
    )

    data class Tracks(
            @JsonProperty("kind") val kind: String?,
            @JsonProperty("file") val file: String,
            @JsonProperty("label") val label: String?,
    )

    data class ResponseHash(
            @JsonProperty("embedurl") val embedurl: String,
            @JsonProperty("key") val key: String,
    )
}
