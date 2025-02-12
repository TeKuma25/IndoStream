package com.Pusatfilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvSeriesLoadResponse

class Pusatfilm : MainAPI() {

    override var mainUrl = "http://pf21.net"

    override var name = "Pusatfilm"
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
}
