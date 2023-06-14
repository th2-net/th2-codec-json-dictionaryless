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
import com.exactpro.th2.common.grpc.Direction as ProtoDirection
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.grpc.AnyMessage as ProtoAnyMessage
import com.exactpro.th2.common.grpc.RawMessage as ProtoRawMessage
import com.exactpro.th2.common.message.getList
import com.exactpro.th2.common.message.getMessage
import com.exactpro.th2.common.message.getString
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.*
import com.google.protobuf.ByteString
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DecodeTest {

    private val settings = JsonPipelineCodecSettings(true, true)
    private val codec = JsonPipelineCodec(settings)

    @Test
    fun testProtoDecodeJsonRequest() {
        val message = ProtoRawMessage.newBuilder().apply {
            parentEventIdBuilder.id = eventId
            metadataBuilder.protocol = PROTOCOL
            metadataBuilder.idBuilder.direction = ProtoDirection.SECOND
            body = ByteString.copyFrom(jsonString.toByteArray())
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(ProtoAnyMessage.newBuilder().setRawMessage(message)).build()

        val decodedMessage = codec.decode(messageGroup).getMessages(0).message

        assertEquals("value", decodedMessage.getString("stringField"))
        assertEquals("number(123)", decodedMessage.getString("intField"))
        assertEquals("number(123.1)", decodedMessage.getString("decimalField"))

        val objectTest = decodedMessage.getMessage("object")
        assertNotNull(objectTest)
        assertEquals("objectFieldValue", objectTest.getString("objectField"))

        val list = decodedMessage.getList("primitiveList")
        assertNotNull(list)
        assertEquals("number(1)", list[0].simpleValue)

        val objectList = decodedMessage.getList("objectList")
        assertNotNull(objectList)
        val listObject = objectList[1].messageValue
        assertNotNull(listObject)
        assertEquals("boolean(true)", listObject.getString("anotherObjectField"))
        assertEquals(decodedMessage.parentEventId.id, eventId)
    }

    @Test
    fun testTransportDecodeJsonRequest() {

        val message = RawMessage(
            MessageId("session_1", Direction.OUTGOING, 1, Instant.now()),
            EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = PROTOCOL,
            body = Unpooled.wrappedBuffer(jsonString.toByteArray())
        )

        val group = MessageGroup(listOf(message))

        val decodedMessage = codec.decode(group).messages[0] as ParsedMessage
        val body = decodedMessage.body

        assertEquals("value", body["stringField"])

        assertEquals("number(123)", body["intField"])
        assertEquals("number(123.1)", body["decimalField"])

        val objectTest = body["object"] as Map<String, Any>
        assertNotNull(objectTest)
        assertEquals("objectFieldValue", objectTest["objectField"])

        val list = body["primitiveList"] as List<Any>
        assertNotNull(list)
        assertEquals(4, list.size)
        assertEquals("number(1)", list[0])

        val objectList = body["objectList"] as List<Any>
        assertNotNull(objectList)
        assertEquals(2, objectList.size)

        val listObject = objectList[1] as Map<String, Any>
        assertEquals("boolean(true)", listObject["anotherObjectField"])

        objectList.forEach {
            val obj = it as Map<String, Any>
            assertNotNull(obj)
            assertEquals(1, obj.size)
        }

        assertEquals(eventId, decodedMessage.eventId?.id)
    }

    companion object {
        private const val eventId = "123"
        private val jsonString = """
            {
               "stringField": "value",
               "intField": 123,
               "decimalField": 123.100000000000000000,
               "object": {
                  "objectField": "objectFieldValue"
               },
               "primitiveList": [ 1, 2, 3, 4 ],
               "objectList": [ { "objectField": 123 }, { "anotherObjectField": true } ]
            }
        """.trimIndent()
    }
}