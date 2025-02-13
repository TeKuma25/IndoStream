// use an integer for version numbers
version = 2

cloudstream {

    // All of these properties are optional, you can safely remove them
    description = "DutaMovie"
    language = "id"
    authors = listOf("Hexated", "TeKuma25")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Movie",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=scandal.dutamovie21.tv&sz=256"
}