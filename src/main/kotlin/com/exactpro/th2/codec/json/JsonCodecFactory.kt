/*
 * Copyright 2022-2023 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.api.IPipelineCodecContext
import com.exactpro.th2.codec.api.IPipelineCodecFactory
import com.exactpro.th2.codec.api.IPipelineCodecSettings
import com.google.auto.service.AutoService

@AutoService(IPipelineCodecFactory::class)
class JsonCodecFactory: IPipelineCodecFactory {
    override val protocols: Set<String> get() = PROTOCOLS

    override val settingsClass: Class<out IPipelineCodecSettings>
        get() = JsonPipelineCodecSettings::class.java

    override fun init(pipelineCodecContext: IPipelineCodecContext) = Unit

    override fun create(settings: IPipelineCodecSettings?) = JsonPipelineCodec(settings as JsonPipelineCodecSettings)

    companion object {
        const val PROTOCOL = "json"
        private val PROTOCOLS = setOf(PROTOCOL)
    }
}