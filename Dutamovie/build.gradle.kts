import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 1

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

    description = "DutaMovie adalah plugin untuk menonton film dan serial TV dari Dutamovie21.tv"
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

    iconUrl = "https://scandal.dutamovie21.cc/wp-content/uploads/2023/03/logo-dutamovie21.png"
}