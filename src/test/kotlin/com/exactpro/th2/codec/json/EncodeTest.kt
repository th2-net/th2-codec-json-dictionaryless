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

import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.set
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.listValue
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.text.Charsets.UTF_8


class EncodeTest {
    @Test
    fun testAnyMessageEncodeTest() {
        val expectedJsonString = """
            {
              "numberField": 123,
              "listOfObjects": [
                {
                  "objectField": "value"
                },
                {
                  "numberField": 123
                }
              ],
              "booleanValue": true,
              "listOfPrimitives": [
                1.1,
                2.2
              ],
              "stringField": "stringValue",
              "object": {
                "objectField": "objectValue"
              }
            }
        """.trimIndent()
        val eventId = "123"

        val codecSettings = JsonPipelineCodecSettings(true, true)
        val codec = JsonPipelineCodec(codecSettings)

        val message = Message.newBuilder().apply {
            direction = Direction.FIRST
            this["stringField"] = "stringValue"
            this["numberField"] = "number(123)"
            this["booleanValue"] = "boolean(true)"
            this["object"] = Message.newBuilder().apply {
                this["objectField"] = "objectValue"
            }
            this["listOfPrimitives"] = listOf("number(1.1)", "number(2.2)")
            this["listOfObjects"] = listValue().apply {
                this.add(Message.newBuilder().apply {
                    this["objectField"] = "value"
                })
                this.add(Message.newBuilder().apply {
                    this["numberField"] =  "number(123)"
                })
            }
            parentEventIdBuilder.id = eventId
            metadataBuilder.protocol = PROTOCOL
        }
        val messageGroup = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message)).build()
        val result = codec.encode(messageGroup).getMessages(0).rawMessage.body.toByteArray().toString(UTF_8)
        assertEquals(MAPPER.readTree(result), MAPPER.readTree(expectedJsonString))
    }
    companion object {
        private val MAPPER = ObjectMapper()
    }
}