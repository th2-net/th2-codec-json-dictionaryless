/*
 * Copyright 2022-2025 Exactpro (Exactpro Systems Limited)
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
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.exactpro.th2.common.value.toValue
import com.google.protobuf.ByteString
import com.google.protobuf.UnsafeByteOperations
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import com.exactpro.th2.common.grpc.AnyMessage as ProtoAnyMessage
import com.exactpro.th2.common.grpc.Message as ProtoParsedMessage
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.grpc.RawMessage as ProtoRawMessage

class DecodeTest {

    private val settings = JsonPipelineCodecSettings(
        encodeTypeInfo = true,
        decodeTypeInfo = true,
        rootArrayField = ROOT_ARRAY_FIELD
    )
    private val codec = JsonPipelineCodec(settings)

    @Test
    fun `test proto decode json object`() {
        val message = ProtoRawMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
            }
            body = ByteString.copyFrom(JSON_OBJECT_STRING.toByteArray())
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(
            ProtoAnyMessage.newBuilder().setRawMessage(message)
        ).build()

        val group = codec.decode(messageGroup, ReportingContext())
        assertEquals(1, group.messagesCount)
        val decodedMessage = group.getMessages(0).message

        assertEquals(decodedMessage.fieldsMap, wrapProto(JSON_OBJECT).messageValue.fieldsMap)
        assertEquals(PROTO_EVENT_ID, decodedMessage.parentEventId)
    }

    @Test
    fun `test proto decode json array`() {
        val message = ProtoRawMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
            }
            body = ByteString.copyFrom("[$JSON_OBJECT_STRING]".toByteArray())
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(
            ProtoAnyMessage.newBuilder().setRawMessage(message)
        ).build()

        val group = codec.decode(messageGroup, ReportingContext())
        assertEquals(1, group.messagesCount)
        val decodedMessage = group.getMessages(0).message

        assertEquals(decodedMessage.fieldsMap, wrapProto(mapOf(ROOT_ARRAY_FIELD to JSON_OBJECT)).messageValue.fieldsMap)
        assertEquals(PROTO_EVENT_ID, decodedMessage.parentEventId)
    }

    @Test
    fun `test proto decode protocol`() {
        val messageA = ProtoParsedMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
                messageType = "type_1"
            }
            addField("fieldA", "valueA".toValue())
        }.build()
        val messageB = ProtoParsedMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = ""
                messageType = "type_1"
            }
            addField("fieldB", "valueB".toValue())
        }.build()
        val messageC = ProtoParsedMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = "test-protocol"
                messageType = "type_1"
            }
            addField("fieldC", "fieldC".toValue())
        }.build()
        val messageD = ProtoRawMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
            }
            metadataBuilder.protocol = PROTOCOL
            body = UnsafeByteOperations.unsafeWrap("""{"fieldD":"valueD"}""".toByteArray())
        }.build()
        val messageE = ProtoRawMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = ""
            }
            body = UnsafeByteOperations.unsafeWrap("""{"fieldE":"valueE"}""".toByteArray())
        }.build()
        val messageF = ProtoRawMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = "test-protocol"
            }
            body = UnsafeByteOperations.unsafeWrap("""{"fieldF":"fieldF"}""".toByteArray())
        }.build()

        val group = ProtoMessageGroup.newBuilder()
            .addMessages(ProtoAnyMessage.newBuilder().setMessage(messageA))
            .addMessages(ProtoAnyMessage.newBuilder().setMessage(messageB))
            .addMessages(ProtoAnyMessage.newBuilder().setMessage(messageC))
            .addMessages(ProtoAnyMessage.newBuilder().setRawMessage(messageD))
            .addMessages(ProtoAnyMessage.newBuilder().setRawMessage(messageE))
            .addMessages(ProtoAnyMessage.newBuilder().setRawMessage(messageF))
            .build()

        val encoded = codec.decode(group, ReportingContext())
        assertEquals(6, encoded.messagesCount)
        assertSame(messageA, encoded.getMessages(0).message)
        assertSame(messageB, encoded.getMessages(1).message)
        assertSame(messageC, encoded.getMessages(2).message)
        assertEquals(mapOf("fieldD" to "valueD".toValue()), encoded.getMessages(3).message.fieldsMap)
        assertEquals(PROTOCOL, encoded.getMessages(3).message.metadata.protocol)
        assertEquals(mapOf("fieldE" to "valueE".toValue()), encoded.getMessages(4).message.fieldsMap)
        assertEquals(PROTOCOL, encoded.getMessages(4).message.metadata.protocol)
        assertSame(messageF, encoded.getMessages(5).rawMessage)
    }

    @Test
    fun `test transport decode json object`() {
        val message = RawMessage(
            TRANSPORT_MESSAGE_ID,
            TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            body = Unpooled.wrappedBuffer(JSON_OBJECT_STRING.toByteArray())
        )

        val decodedMessage = codec.decode(MessageGroup(listOf(message)), ReportingContext())
            .messages.single() as ParsedMessage
        val body = decodedMessage.body

        assertEquals(body, wrapTransport(JSON_OBJECT))
        assertEquals(TRANSPORT_EVENT_ID, decodedMessage.eventId)
    }

    @Test
    fun `test transport decode json array`() {
        val message = RawMessage(
            TRANSPORT_MESSAGE_ID,
            TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            body = Unpooled.wrappedBuffer("[$JSON_OBJECT_STRING]".toByteArray())
        )

        val decodedMessage = codec.decode(MessageGroup(listOf(message)), ReportingContext())
            .messages.single() as ParsedMessage
        val body = decodedMessage.body

        assertEquals(body, wrapTransport(mapOf(ROOT_ARRAY_FIELD to JSON_OBJECT)))
        assertEquals(TRANSPORT_EVENT_ID, decodedMessage.eventId)
    }

    @Test
    fun `test transport protocol decode`() {
        val messageA = ParsedMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            type = "type_1",
            body = mapOf("fieldA" to "valueA")
        )
        val messageB = ParsedMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = "",
            type = "type_1",
            body = mapOf("fieldB" to "valueB")
        )
        val messageC = ParsedMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = "test-protocol",
            type = "type_1",
            body = mapOf("fieldC" to "valueC")
        )
        val messageD = RawMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            body = Unpooled.wrappedBuffer("""{"fieldD":"valueD"}""".toByteArray())
        )
        val messageE = RawMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = "",
            body = Unpooled.wrappedBuffer("""{"fieldE":"valueE"}""".toByteArray())
        )
        val messageF = RawMessage(
            eventId = TRANSPORT_EVENT_ID,
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

    companion object {
        private val JSON_OBJECT = mapOf(
            "stringField" to "value",
            "intField" to 123,
            "decimalField" to 123.100000000000000000,
            "object" to mapOf(
                "objectField" to "objectFieldValue",
            ),
            "primitiveList" to listOf(1, 2, 3, 4),
            "objectList" to listOf(
                mapOf("objectField" to 123),
                mapOf("anotherObjectField" to true),
            )
        )
        private val JSON_OBJECT_STRING = """
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