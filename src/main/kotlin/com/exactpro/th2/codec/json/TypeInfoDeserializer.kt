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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonTokenId
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer
import java.io.IOException
import java.math.BigDecimal


@Suppress("serial")
class TypeInfoDeserializer(listType: JavaType, mapType: JavaType) : UntypedObjectDeserializer(listType, mapType) {
    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any = when (p.currentTokenId()) {
        JsonTokenId.ID_TRUE, JsonTokenId.ID_FALSE -> "boolean(" + p.booleanValue.toString() + ")"
        JsonTokenId.ID_NUMBER_INT, JsonTokenId.ID_NUMBER_FLOAT -> {
            val number = p.numberValue.apply {
                if (this is BigDecimal) stripTrailingZeros().toPlainString() else toString()
            }
            "number($number)"
        }
        else -> super.deserialize(p, ctxt)
    }
}