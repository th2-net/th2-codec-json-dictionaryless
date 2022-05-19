package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.api.IPipelineCodecContext
import com.exactpro.th2.codec.api.IPipelineCodecFactory
import com.exactpro.th2.codec.api.IPipelineCodecSettings

class JsonCodecFactory: IPipelineCodecFactory {
    override val protocol: String = PROTOCOL

    override val settingsClass: Class<out IPipelineCodecSettings>
        get() = JsonPipelineCodecSettings::class.java

    override fun init(pipelineCodecContext: IPipelineCodecContext) = Unit

    override fun create(settings: IPipelineCodecSettings?) = JsonPipelineCodec(settings as JsonPipelineCodecSettings)

    companion object {
        const val PROTOCOL = "json"
    }
}