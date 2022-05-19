package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.api.IPipelineCodec
import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage.KindCase.RAW_MESSAGE
import com.exactpro.th2.common.grpc.Direction.FIRST
import com.exactpro.th2.common.grpc.Direction.SECOND
import com.exactpro.th2.common.grpc.EventID
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.MessageID
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.message.plusAssign
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.google.protobuf.ByteString
import kotlin.text.Charsets.UTF_8

class JsonPipelineCodec(private val settings: JsonPipelineCodecSettings): IPipelineCodec {

    init {
        if(settings.decodeTypeInfo) {
            injectCustomDeserializer()
        }
        if(settings.encodeTypeInfo) {
            injectCustomSerializer()
        }
    }

    override fun decode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList

        if (messages.isEmpty()) {
            return messageGroup
        }

        require(messages.size == 1) { "Message group must contain only 1 message" }
        require(messages[0].kindCase == RAW_MESSAGE) { "Message must be a raw message" }

        val message = messages[0].rawMessage
        val body = message.body.toByteArray().toString(UTF_8)
        val builder = MessageGroup.newBuilder()
        val metadata = message.metadata
        when (val direction = message.metadata.id.direction) {
            FIRST, SECOND -> {
                builder += MAPPER.readValue(body, Map::class.java).toProtoBuilder().apply {
                    metadataBuilder.apply {
                        if(message.hasParentEventId()) parentEventIdBuilder.mergeFrom(message.parentEventId)
                        putAllProperties(metadata.propertiesMap)
                        messageType = when(direction) {
                            FIRST -> RESPONSE
                            SECOND -> REQUEST
                            else -> { error("Unsupported message direction: $direction") }
                        }
                        protocol = PROTOCOL
                        idBuilder.mergeFrom(metadata.id)
                    }
                }.build()
            }
            else -> error("Unsupported message direction: $direction")
        }

        return builder.build()
    }

    override fun encode(messageGroup: MessageGroup): MessageGroup {
        val messages = messageGroup.messagesList

        if (messages.isEmpty()) {
            return messageGroup
        }

        require(messages.size == 1) { "Message group must have one value." }

        val message = messages[0].message

        require(message.metadata.protocol == PROTOCOL) { "First message in group must have $PROTOCOL type." }

        val builder = MessageGroup.newBuilder()
        val metadata = message.metadata

        builder += when(val direction = message.direction) {
            FIRST, SECOND -> {
                MAPPER.writeValueAsString(message.toMap())
            }
            else -> error("Unsupported message direction: $direction")
        }.toByteArray(UTF_8).toRawMessage(
            metadata.id,
            metadata.propertiesMap,
            message.parentEventId
        )

        return builder.build()
    }

    companion object {
        const val REQUEST = "Request"
        const val RESPONSE = "Response"
        val MAPPER = ObjectMapper()

        fun injectCustomSerializer() {
            val module = SimpleModule()
            module.addSerializer(String::class.java, TypeInfoSerializer())
            MAPPER.registerModule(module)
        }

        fun injectCustomDeserializer() {
            val module = SimpleModule()
            module.addDeserializer(Any::class.java, TypeInfoDeserializer())
            MAPPER.registerModule(module)
        }

        private fun ByteArray.toRawMessage(
            messageId: MessageID,
            metadataProperties: Map<String, String>,
            eventID: EventID
        ): RawMessage = RawMessage.newBuilder().apply {
            body = ByteString.copyFrom(this@toRawMessage)
            parentEventIdBuilder.mergeFrom(eventID)
            metadataBuilder.apply {
                putAllProperties(metadataProperties)
                idBuilder.mergeFrom(messageId)
            }
            this
        }.build()
    }
}