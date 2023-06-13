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

import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage as ProtoAnyMessage
import com.exactpro.th2.common.grpc.Direction as ProtoDirection
import com.exactpro.th2.common.grpc.Message as ProtoMessage
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.set
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.EventId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.listValue
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.text.Charsets.UTF_8

class EncodeTest {
    private val codecSettings = JsonPipelineCodecSettings(true, true)
    private val codec = JsonPipelineCodec(codecSettings)

    @Test
    fun testProtoAnyMessageEncodeTest() {
        val message = ProtoMessage.newBuilder().apply {
            direction = ProtoDirection.FIRST
            this["stringField"] = "stringValue"
            this["numberField"] = "number(123)"
            this["booleanValue"] = "boolean(true)"
            this["object"] = ProtoMessage.newBuilder().apply {
                this["objectField"] = "objectValue"
            }
            this["listOfPrimitives"] = listOf("number(1.1)", "number(2.2)")
            this["listOfObjects"] = listValue().apply {
                this.add(ProtoMessage.newBuilder().apply {
                    this["objectField"] = "value"
                })
                this.add(ProtoMessage.newBuilder().apply {
                    this["numberField"] =  "number(123)"
                })
            }
            parentEventIdBuilder.id = eventId
            metadataBuilder.protocol = PROTOCOL
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(ProtoAnyMessage.newBuilder().setMessage(message)).build()
        val result = codec.encode(messageGroup).getMessages(0).rawMessage.body.toString(UTF_8)
        assertEquals(MAPPER.readTree(expectedJsonString), MAPPER.readTree(result))
    }

    @Test
    fun testTransportAnyMessageEncodeTest() {
        val body = mapOf(
            "stringField" to "stringValue",
            "numberField" to 123,
            "booleanValue" to true,
            "object" to mapOf("objectField" to "objectValue"),
            "listOfPrimitives" to listOf(1.1, 2.2),
            "listOfObjects" to listOf(
                mapOf("objectField" to "value"),
                mapOf("numberField" to 123)
            )
        )

        val message = ParsedMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = PROTOCOL,
            type = "type_1",
            body = body
        )

        val group = MessageGroup(listOf(message))

        val result = (codec.encode(group).messages[0] as RawMessage).body.toString(UTF_8)
        assertEquals(MAPPER.readTree(expectedJsonString), MAPPER.readTree(result))
    }

    companion object {
        private val MAPPER = ObjectMapper()
        private const val eventId = "123"
        private val expectedJsonString = """
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
    }
}