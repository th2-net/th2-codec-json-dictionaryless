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
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.addFields
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.exactpro.th2.common.value.toValue
import com.google.protobuf.UnsafeByteOperations
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.text.Charsets.UTF_8
import com.exactpro.th2.common.grpc.AnyMessage as ProtoAnyMessage
import com.exactpro.th2.common.grpc.Message as ProtoMessage
import com.exactpro.th2.common.grpc.Message as ProtoParsedMessage
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.grpc.RawMessage as ProtoRawMessage

class EncodeTest {
    private val codecSettings = JsonPipelineCodecSettings(
        encodeTypeInfo = true,
        decodeTypeInfo = true,
        rootArrayField = ROOT_ARRAY_FIELD
    )
    private val codec = JsonPipelineCodec(codecSettings)

    @Test
    fun `test proto encode json object`() {
        val message = ProtoMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
            }
            addFields(wrapProto(JSON_OBJECT).messageValue.fieldsMap)
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(
            ProtoAnyMessage.newBuilder().setMessage(message)
        ).build()
        val group = codec.encode(messageGroup, ReportingContext())
        assertEquals(1, group.messagesCount)
        val result = group.getMessages(0).rawMessage.body.toString(UTF_8)
        assertEquals(MAPPER.readTree(JSON_OBJECT_STRING), MAPPER.readTree(result))
    }

    @Test
    fun `test proto encode json array`() {
        val message = ProtoMessage.newBuilder().apply {
            parentEventId = PROTO_EVENT_ID
            metadataBuilder.apply {
                id = PROTO_MESSAGE_ID
                protocol = PROTOCOL
            }
            addFields(wrapProto(mapOf(ROOT_ARRAY_FIELD to JSON_OBJECT)).messageValue.fieldsMap)
        }
        val messageGroup = ProtoMessageGroup.newBuilder().addMessages(
            ProtoAnyMessage.newBuilder().setMessage(message)
        ).build()
        val group = codec.encode(messageGroup, ReportingContext())
        assertEquals(1, group.messagesCount)
        val result = group.getMessages(0).rawMessage.body.toString(UTF_8)
        assertEquals(MAPPER.readTree("[$JSON_OBJECT_STRING]"), MAPPER.readTree(result))
    }

    @Test
    fun `test proot encode protocol`() {
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

        val encoded = codec.encode(group, ReportingContext())
        assertEquals(6, encoded.messagesCount)
        assertEquals("""{"fieldA":"valueA"}""", encoded.getMessages(0).rawMessage.body.toString(UTF_8))
        assertEquals(PROTOCOL, encoded.getMessages(0).rawMessage.metadata.protocol)
        assertEquals("""{"fieldB":"valueB"}""", encoded.getMessages(1).rawMessage.body.toString(UTF_8))
        assertEquals(PROTOCOL, encoded.getMessages(1).rawMessage.metadata.protocol)
        assertSame(messageC, encoded.getMessages(2).message)
        assertSame(messageD, encoded.getMessages(3).rawMessage)
        assertSame(messageE, encoded.getMessages(4).rawMessage)
        assertSame(messageF, encoded.getMessages(5).rawMessage)
    }

    @Test
    fun `test transport encode json object`() {
        @Suppress("UNCHECKED_CAST") val message = ParsedMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            type = "type_1",
            body = wrapTransport(JSON_OBJECT) as Map<String, Any?>
        )

        val group = MessageGroup(listOf(message))

        val result = (codec.encode(group, ReportingContext()).messages.single() as RawMessage).body.toString(UTF_8)
        assertEquals(MAPPER.readTree(JSON_OBJECT_STRING), MAPPER.readTree(result))
    }

    @Test
    fun `test transport encode json array`() {
        @Suppress("UNCHECKED_CAST") val message = ParsedMessage(
            eventId = TRANSPORT_EVENT_ID,
            protocol = PROTOCOL,
            type = "type_1",
            body = wrapTransport(mapOf(ROOT_ARRAY_FIELD to JSON_OBJECT)) as Map<String, Any?>
        )

        val group = MessageGroup(listOf(message))

        val result = (codec.encode(group, ReportingContext()).messages.single() as RawMessage).body.toString(UTF_8)
        assertEquals(MAPPER.readTree("[$JSON_OBJECT_STRING]"), MAPPER.readTree(result))
    }

    @Test
    fun `test transport protocol encode`() {
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

        val encoded = codec.encode(group, ReportingContext())
        assertEquals(6, encoded.messages.size)
        assertEquals("""{"fieldA":"valueA"}""", (encoded.messages[0] as RawMessage).body.toString(UTF_8))
        assertEquals(PROTOCOL, encoded.messages[0].protocol)
        assertEquals("""{"fieldB":"valueB"}""", (encoded.messages[1] as RawMessage).body.toString(UTF_8))
        assertEquals(PROTOCOL, encoded.messages[1].protocol)
        assertSame(messageC, encoded.messages[2])
        assertSame(messageD, encoded.messages[3])
        assertSame(messageE, encoded.messages[4])
        assertSame(messageF, encoded.messages[5])
    }

    companion object {
        private val JSON_OBJECT = mapOf(
            "numberField" to 123,
            "listOfObjects" to listOf(
                mapOf("objectField" to "value"),
                mapOf("numberField" to 123)
            ),
            "booleanValue" to true,
            "listOfPrimitives" to listOf(1.1, 2.2),
            "stringField" to "stringValue",
            "object" to mapOf("objectField" to "objectValue")
        )
        private val JSON_OBJECT_STRING = MAPPER.writeValueAsString(JSON_OBJECT)
    }
}