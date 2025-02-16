package com.otakudesu

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class OtakudesuPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list
        // directly.
        registerMainAPI(Otakudesu())
        registerExtractorAPI(Moedesu())
        registerExtractorAPI(DesuBeta())
        registerExtractorAPI(Desudesuhd())
    }
}
