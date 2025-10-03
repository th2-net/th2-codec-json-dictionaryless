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

import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.api.IReportingContext
import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageGroup
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.ParsedMessage
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.RawMessage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.ByteString
import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufInputStream
import io.netty.buffer.ByteBufOutputStream
import io.netty.buffer.Unpooled
import java.io.OutputStream
import com.exactpro.th2.common.grpc.Direction as ProtoDirection
import com.exactpro.th2.common.grpc.EventID as ProtoEventID
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.grpc.MessageID as ProtoMessageID
import com.exactpro.th2.common.grpc.RawMessage as ProtoRawMessage
import com.exactpro.th2.common.grpc.Value as ProtoValue

class JsonPipelineCodec(settings: JsonPipelineCodecSettings): IPipelineCodec {

    private val rootArrayField = settings.rootArrayField

    private val objectMapper = ObjectMapper().apply {
        if (settings.encodeTypeInfo || settings.decodeTypeInfo) {
            val module = SimpleModule()
            if (settings.encodeTypeInfo) module.addSerializer(String::class.java, TypeInfoSerializer())
            if (settings.decodeTypeInfo) {
                val listType = typeFactory.constructCollectionType(List::class.java, Object::class.java)
                val mapType = typeFactory.constructMapType(Map::class.java, String::class.java, Object::class.java)
                module.addDeserializer(Any::class.java, TypeInfoDeserializer(listType, mapType))
            }
            registerModule(module)
        }
    }

    override fun decode(messageGroup: ProtoMessageGroup, context: IReportingContext): ProtoMessageGroup {
        val builder = ProtoMessageGroup.newBuilder()

        for (message in messageGroup.messagesList) {
            if (!message.hasRawMessage()) {
                builder.addMessages(message)
                continue
            }

            val rawMessage = message.rawMessage
            val metadata = rawMessage.metadata

            if (metadata.run { protocol.isNotBlank() && protocol != PROTOCOL }) {
                builder.addMessages(message)
                continue
            }

            val direction = metadata.id.direction

            require(direction == ProtoDirection.FIRST || direction == ProtoDirection.SECOND) { "Unsupported message direction: $direction" }

            builder += rawMessage.body.parse().toProtoBuilder().apply {
                if (rawMessage.hasParentEventId()) parentEventId = rawMessage.parentEventId

                metadataBuilder.apply {
                    putAllProperties(metadata.propertiesMap)
                    messageType = if (direction == ProtoDirection.FIRST) INCOMING else OUTGOING
                    protocol = PROTOCOL
                    id = metadata.id
                }
            }
        }

        return builder.build()
    }

    override fun encode(messageGroup: ProtoMessageGroup, context: IReportingContext): ProtoMessageGroup {
        val builder = ProtoMessageGroup.newBuilder()

        for (message in messageGroup.messagesList) {
            if (!message.hasMessage()) {
                builder.addMessages(message)
                continue
            }

            val parsedMessage = message.message
            val metadata = parsedMessage.metadata

            if (metadata.run { protocol.isNotBlank() && protocol != PROTOCOL }) {
                builder.addMessages(message)
                continue
            }

            builder += parsedMessage.fieldsMap.serialize().toRawMessage(
                metadata.id,
                metadata.propertiesMap,
                parsedMessage.parentEventId
            )

        }

        return builder.build()
    }

    override fun decode(messageGroup: MessageGroup, context: IReportingContext): MessageGroup = MessageGroup(
        messageGroup.messages.map { message ->
            if (message !is RawMessage || (message.protocol.isNotBlank() && message.protocol != PROTOCOL)) {
                message
            } else {
                @Suppress("REDUNDANT_ELSE_IN_WHEN") val messageType = when (message.id.direction) {
                    Direction.INCOMING -> INCOMING
                    Direction.OUTGOING -> OUTGOING
                    else -> error("Unsupported message direction: ${message.id.direction}")
                }

                ParsedMessage(
                    message.id,
                    message.eventId,
                    messageType,
                    message.metadata,
                    PROTOCOL,
                    message.body.parse()
                )
            }
        }
    )

    override fun encode(messageGroup: MessageGroup, context: IReportingContext): MessageGroup = MessageGroup(
        messageGroup.messages.map {
            if (it !is ParsedMessage || (it.protocol.isNotBlank() && it.protocol != PROTOCOL)) {
                it
            } else {
                RawMessage(it.id, it.eventId, it.metadata, PROTOCOL, it.body.serialize())
            }
        }
    )

    private fun ByteString.parse(): Map<String, Any> {
        val first = asSequence()
            .filter { it != SPACE && it != TAB }
            .firstOrNull()
        return when(first) {
            null -> emptyMap()
            OPENING_CURLY_BRACE -> newInput().use {
                objectMapper.readValue(it, parsedMapTypeRef)
            }
            OPENING_SQUARE_BRACE -> {
                if (rootArrayField == null) error("JSON array isn't supported without 'rootArrayField' option")
                newInput().use {
                    mapOf(rootArrayField to objectMapper.readValue(it, parsedArrayTypeRef))
                }
            }
            else -> error("'${first.toInt().toChar()}' first character isn't valid for JSON object or array")
        }
    }

    private fun ByteBuf.parse(): Map<String, Any> {
        val first = (readerIndex() .. writerIndex()).asSequence()
            .map(this::getByte)
            .filter { it != SPACE && it != TAB }
            .firstOrNull()
        return when(first) {
            null -> emptyMap()
            OPENING_CURLY_BRACE -> ByteBufInputStream(this).use {
                objectMapper.readValue(it, parsedMapTypeRef)
            }
            OPENING_SQUARE_BRACE -> {
                if (rootArrayField == null) error("JSON array isn't supported without 'rootArrayField' option")
                ByteBufInputStream(this).use {
                    mapOf(rootArrayField to objectMapper.readValue(it, parsedArrayTypeRef))
                }
            }
            else -> error("'${first.toInt().toChar()}' first character isn't valid for JSON object or array")
        }
    }

    private fun Map<String, Any?>.serialize(): ByteBuf {
        if (isEmpty()) return Unpooled.EMPTY_BUFFER
        val obj: Any = if (rootArrayField == null) {
            this
        } else {
            get(rootArrayField)?.let {
                if (size != 1) error("Message contains $rootArrayField configured root filed and other unexpected $keys")
                if (it !is List<*>) error(
                    "$rootArrayField configured root filed has ${it::class.simpleName} unexpected content type instead of ${List::class.simpleName}"
                )
                it
            } ?: this
        }
        return Unpooled.buffer().apply {
            objectMapper.writeValue(ByteBufOutputStream(this) as OutputStream, obj)
        }
    }

    private fun Map<String, ProtoValue>.serialize(): ByteString {
        if (isEmpty()) return ByteString.EMPTY
        val map = mapValuesTo(HashMap()) { it.value.toObject() }
        val obj: Any = if (rootArrayField == null) {
            map
        } else {
            map[rootArrayField]?.let {
                if (size != 1) error("Message contains $rootArrayField configured root filed and other unexpected $keys")
                if (it !is List<*>) error(
                    "$rootArrayField configured root filed has ${it::class.simpleName} unexpected content type instead of ${List::class.simpleName}"
                )
                it
            } ?: map
        }
        return ByteString.newOutput().apply {
            objectMapper.writeValue(this, obj)
        }.toByteString()
    }

    companion object {
        private val SPACE = ' '.code.toByte()
        private val TAB = '\t'.code.toByte()
        private val OPENING_CURLY_BRACE = '{'.code.toByte()
        private val OPENING_SQUARE_BRACE = '['.code.toByte()

        private const val INCOMING = "Incoming"
        private const val OUTGOING = "Outgoing"
        private val parsedMapTypeRef = object : TypeReference<Map<String, Any>>() {}
        private val parsedArrayTypeRef = object : TypeReference<List<Any>>() {}

        private fun ByteString.toRawMessage(
            messageId: ProtoMessageID,
            metadataProperties: Map<String, String>,
            eventID: ProtoEventID
        ): ProtoRawMessage = ProtoRawMessage.newBuilder().apply {
            body = this@toRawMessage
            parentEventId = eventID
            metadataBuilder.apply {
                protocol = PROTOCOL
                putAllProperties(metadataProperties)
                id = messageId
            }
        }.build()
    }
}