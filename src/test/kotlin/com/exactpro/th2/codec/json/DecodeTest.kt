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

import com.exactpro.th2.codec.api.impl.ReportingContext
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
import kotlin.test.assertSame

class DecodeTest {

    private val settings = JsonPipelineCodecSettings(encodeTypeInfo = true, decodeTypeInfo = true)
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

        val decodedMessage = codec.decode(messageGroup, ReportingContext()).getMessages(0).message

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
    fun testTransportAnyProtocolDecodeTest() {

        val messageA = ParsedMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = PROTOCOL,
            type = "type_1",
            body = mapOf("fieldA" to "valueA")
        )
        val messageB = ParsedMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = "",
            type = "type_1",
            body = mapOf("fieldB" to "valueB")
        )
        val messageC = ParsedMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = "test-protocol",
            type = "type_1",
            body = mapOf("fieldC" to "valueC")
        )
        val messageD = RawMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = PROTOCOL,
            body = Unpooled.wrappedBuffer("""{"fieldD":"valueD"}""".toByteArray())
        )
        val messageE = RawMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = "",
            body = Unpooled.wrappedBuffer("""{"fieldE":"valueE"}""".toByteArray())
        )
        val messageF = RawMessage(
            eventId = EventId(eventId, "book_1", "scope_1", Instant.now()),
            protocol = "test-protocol",
            body = Unpooled.wrappedBuffer("""{"fieldF":"valueF"}""".toByteArray())
        )

        val group = MessageGroup(listOf(messageA, messageB, messageC, messageD, messageE, messageF))

        val encoded = codec.decode(group, ReportingContext())
        assertEquals(6, encoded.messages.size)
        assertSame(messageA, encoded.messages[0])
        assertSame(messageB, encoded.messages[1])
        assertSame(messageC, encoded.messages[2])
        assertEquals(mapOf("fieldD" to "valueD"), encoded.messages[3].body)
        assertEquals(PROTOCOL, encoded.messages[3].protocol)
        assertEquals(mapOf("fieldE" to "valueE"), encoded.messages[4].body)
        assertEquals(PROTOCOL, encoded.messages[4].protocol)
        assertSame(messageF, encoded.messages[5])
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

        val decodedMessage = codec.decode(group, ReportingContext()).messages[0] as ParsedMessage
        val body = decodedMessage.body

        assertEquals("value", body["stringField"])

        assertEquals("number(123)", body["intField"])
        assertEquals("number(123.1)", body["decimalField"])

        val objectTest = body["object"] as Map<*, *>
        assertNotNull(objectTest)
        assertEquals("objectFieldValue", objectTest["objectField"])

        val list = body["primitiveList"] as List<*>
        assertNotNull(list)
        assertEquals(4, list.size)
        assertEquals("number(1)", list[0])

        val objectList = body["objectList"] as List<*>
        assertNotNull(objectList)
        assertEquals(2, objectList.size)

        val listObject = objectList[1] as Map<*, *>
        assertEquals("boolean(true)", listObject["anotherObjectField"])

        objectList.forEach {
            val obj = it as Map<*, *>
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