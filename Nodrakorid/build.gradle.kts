import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 33

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        buildConfigField("String", "ZSHOW_API", "\"${properties.getProperty("ZSHOW_API")}\"")
    }
}

cloudstream {
    language = "id"
    // All of these properties are optional, you can safely remove them

    description = "Nodrakorid is a plugin that provides streaming links for Korean dramas and movies."
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

    iconUrl = "https://tv.nodrakor22.sbs/wp-content/uploads/2025/01/22-2.png"
}