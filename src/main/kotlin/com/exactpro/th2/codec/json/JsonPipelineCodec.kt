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

import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.api.IReportingContext
import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.Direction as ProtoDirection
import com.exactpro.th2.common.grpc.EventID as ProtoEventID
import com.exactpro.th2.common.grpc.MessageGroup as ProtoMessageGroup
import com.exactpro.th2.common.grpc.MessageID as ProtoMessageID
import com.exactpro.th2.common.grpc.RawMessage as ProtoRawMessage
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.plusAssign
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.*
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.ByteString
import io.netty.buffer.Unpooled
import kotlin.text.Charsets.UTF_8

class JsonPipelineCodec(settings: JsonPipelineCodecSettings): IPipelineCodec {
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

            val body = rawMessage.body.toByteArray().toString(UTF_8)

            builder += objectMapper.readValue(body, parsedMapTypeRef).toProtoBuilder().apply {
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

            builder += when(val direction = parsedMessage.direction) {
                ProtoDirection.FIRST, ProtoDirection.SECOND -> objectMapper.writeValueAsString(parsedMessage.toMap())
                else -> error("Unsupported message direction: $direction")
            }.toByteArray(UTF_8).toRawMessage(
                metadata.id,
                metadata.propertiesMap,
                parsedMessage.parentEventId
            )

        }

        return builder.build()
    }

    override fun decode(messageGroup: MessageGroup, context: IReportingContext): MessageGroup = MessageGroup(
        messageGroup.messages.map {
            if (it !is RawMessage || (it.protocol.isNotBlank() && it.protocol != PROTOCOL)) {
                it
            } else {
                val messageType = when (it.id.direction) {
                    Direction.INCOMING -> INCOMING
                    Direction.OUTGOING -> OUTGOING
                    else -> error("Unsupported message direction: ${it.id.direction}")
                }

                val parsedBody = objectMapper.readValue(it.body.toString(UTF_8), parsedMapTypeRef)

                ParsedMessage(it.id, it.eventId, messageType, it.metadata, PROTOCOL, parsedBody)
            }
        }
    )

    override fun encode(messageGroup: MessageGroup, context: IReportingContext): MessageGroup = MessageGroup(
        messageGroup.messages.map {
            if (it !is ParsedMessage || (it.protocol.isNotBlank() && it.protocol != PROTOCOL)) {
                it
            } else {
                require(it.id.direction in VALID_DIRECTIONS) { "Unsupported message direction: ${it.id.direction}" }
                val encodedBody = Unpooled.wrappedBuffer(objectMapper.writeValueAsString(it.body).toByteArray(UTF_8))
                RawMessage(it.id, it.eventId, it.metadata, PROTOCOL, encodedBody)
            }
        }
    )

    companion object {
        private const val INCOMING = "Incoming"
        private const val OUTGOING = "Outgoing"
        private val VALID_DIRECTIONS = arrayOf(Direction.INCOMING, Direction.OUTGOING)
        private val parsedMapTypeRef = object : TypeReference<Map<String, Any>>() {}
        private val parsedArrayTypeRef = object : TypeReference<List<Any>>() {}

        private fun ByteArray.toRawMessage(
            messageId: ProtoMessageID,
            metadataProperties: Map<String, String>,
            eventID: ProtoEventID
        ): ProtoRawMessage = ProtoRawMessage.newBuilder().apply {
            body = ByteString.copyFrom(this@toRawMessage)
            parentEventId = eventID
            metadataBuilder.apply {
                protocol = PROTOCOL
                putAllProperties(metadataProperties)
                id = messageId
            }
        }.build()
    }
}