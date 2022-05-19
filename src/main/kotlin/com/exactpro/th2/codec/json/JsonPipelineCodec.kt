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

import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.AnyMessage.KindCase.RAW_MESSAGE
import com.exactpro.th2.common.grpc.Direction.FIRST
import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.messageType
import com.exactpro.th2.common.message.plusAssign
import com.fasterxml.jackson.databind.InjectableValues
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.ObjectReader
import com.fasterxml.jackson.databind.ObjectWriter
import com.fasterxml.jackson.databind.cfg.ContextAttributes
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.ByteString
import kotlin.text.Charsets.UTF_8

class JsonPipelineCodec(private val settings: JsonPipelineCodecSettings): IPipelineCodec {
    private val reader: ObjectReader = getReader(settings.decodeTypeInfo)
    private val writer: ObjectWriter = getWriter(settings.encodeTypeInfo)

    override fun decode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList

        if (messages.isEmpty() || messages.none(AnyMessage::hasRawMessage)) {
            return messageGroup
        }

        val builder = MessageGroup.newBuilder()

        for (message in messages) {
            if (!message.hasRawMessage()) {
                builder.addMessages(message)
                continue
            }

            val rawMessage = message.rawMessage
            val metadata = rawMessage.metadata
            val body = message.rawMessage.body.toByteArray().toString(UTF_8)

            when (val direction = metadata.id.direction) {
                FIRST, SECOND -> {
                    builder += reader.readValue(body, Map::class.java).toProtoBuilder().apply {
                        if (rawMessage.hasParentEventId()) parentEventIdBuilder.mergeFrom(rawMessage.parentEventId)
                        metadataBuilder.apply {
                            putAllProperties(metadata.propertiesMap)
                            messageType = if (direction == FIRST) INCOMING else OUTGOING
                            protocol = PROTOCOL
                            id = metadata.id
                        }
                    }
                }
                else -> error("Unsupported message direction: $direction")
            }
        }

        return builder.build()
    }

    override fun encode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList
        val builder = MessageGroup.newBuilder()

        for (message in messages) {
            if (!message.hasMessage()) {
                builder.addMessages(message)
                continue
            }
            if (message.message.metadata.run { protocol.isNotEmpty() && protocol != PROTOCOL }) {
                builder.addMessages(message)
                continue
            }

            val parsedMessage = message.message
            val metadata = parsedMessage.metadata

            builder += when(val direction = parsedMessage.direction) {
                FIRST, SECOND -> writer.writeValueAsString(parsedMessage.toMap())
                else -> error("Unsupported message direction: $direction")
            }.toByteArray(UTF_8).toRawMessage(
                metadata.id,
                metadata.propertiesMap,
                parsedMessage.parentEventId
            )

        }

        return builder.build()
    }

    companion object {
        private const val INCOMING = "Incoming"
        private const val OUTGOING = "Outgoing"
        private val MAPPER = ObjectMapper().apply {
            val module = SimpleModule()
            module.addSerializer(String::class.java, TypeInfoSerializer())
            module.addDeserializer(Any::class.java, TypeInfoDeserializer())
            this.registerModule(module)
        }

        fun getReader(appendTypeInfo: Boolean): ObjectReader {
            return MAPPER.reader().withAttribute(DECODE_TYPE_INFO_PROPERTY, appendTypeInfo)
        }

        fun getWriter(unpackTypeInfo: Boolean): ObjectWriter {
            return MAPPER.writer().withAttribute(ENCODE_TYPE_INFO_PROPERTY, unpackTypeInfo)
        }

        private fun ByteArray.toRawMessage(
            messageId: MessageID,
            metadataProperties: Map<String, String>,
            eventID: EventID
        ): RawMessage = RawMessage.newBuilder().apply {
            body = ByteString.copyFrom(this@toRawMessage)
            parentEventIdBuilder.mergeFrom(eventID)
            metadataBuilder.apply {
                protocol = PROTOCOL
                putAllProperties(metadataProperties)
                id = messageId
            }
        }.build()
    }
}