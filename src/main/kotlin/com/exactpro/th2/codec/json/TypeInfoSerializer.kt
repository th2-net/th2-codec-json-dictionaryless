/*
 * Copyright 2022-2022 Exactpro (Exactpro Systems Limited)
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

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.String
import kotlin.Throws


class TypeInfoSerializer constructor() : StdSerializer<String>(String::class.java) {
    @Throws(IOException::class)
    override fun serialize(value: String, gen: JsonGenerator, provider: SerializerProvider) {
        if(!(provider.getAttribute(ENCODE_TYPE_INFO_PROPERTY) as Boolean)) { gen.writeString(value); return }

        if(value.startsWith(NUMBER_PREFIX) && value.endsWith(TYPE_SUFFIX)) {
            value.removePrefix(NUMBER_PREFIX).removeSuffix(TYPE_SUFFIX).let { value ->
                if (value.any { it == '.' || it == ','})
                    gen.writeNumber(BigDecimal(value))
                else gen.writeNumber(BigInteger(value))
            }
            return
        }

        if(value.startsWith(BOOLEAN_PREFIX) && value.endsWith(TYPE_SUFFIX)) {
            gen.writeBoolean(value.removePrefix(BOOLEAN_PREFIX).removeSuffix(TYPE_SUFFIX).let { it.toBoolean() })
            return
        }

        gen.writeString(value)
    }

    companion object {
        private const val NUMBER_PREFIX = "number("
        private const val BOOLEAN_PREFIX = "boolean("
        private const val TYPE_SUFFIX = ")"
    }
}
