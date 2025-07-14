package com.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.mainPageOf

class Ngefilm : MainAPI() {

    override var mainUrl = "https://new14.ngefilm.site"

    override var name = "Ngefilm"
    override val hasMainPage = true
    override var lang = "id"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override val mainPage =
            mainPageOf(
                    "/page/%d/?s&search=advanced&post_type=movie&index&orderby&genre&movieyear&country&quality=" to
                            "Movies Terbaru",
                    "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=&quality=" to
                            "Series Terbaru",
                    "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=drakor&movieyear=&country=&quality=" to
                            "Series Korea",
                    "/page/%d/?s=&search=advanced&post_type=tv&index=&orderby=&genre=&movieyear=&country=indonesia&quality=" to
                            "Series Indonesia",
            )
}
