package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.message.direction
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.listValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.text.Charsets.UTF_8

class EncodeTest {
    @Test
    fun testAnyMessageEncodeTest() {
        val expectedJsonString = """
            {"numberField":123,"listOfObjects":[{"objectField":"value"},{"numberField":123}],"booleanValue":true,"listOfPrimitives":[1.1,2.2],"stringField":"stringValue","object":{"objectField":"objectValue"}}
        """.trimIndent()
        val eventId = "123"

        val codecSettings = JsonPipelineCodecSettings(true, true)
        val codec = JsonPipelineCodec(codecSettings)

        val message = Message.newBuilder().apply {
            direction = Direction.FIRST
            addField("stringField", "stringValue")
            addField("numberField", "number(123)")
            addField("booleanValue", "boolean(true)")
            addField("object", Message.newBuilder().apply {
                addField("objectField", "objectValue")
            })
            addField("listOfPrimitives", listValue().apply {
                this.add("number(1.1)")
                this.add("number(2.2)")
            })
            addField("listOfObjects", listValue().apply {
                this.add(Message.newBuilder().apply {
                    this.addField("objectField", "value")
                })
                this.add(Message.newBuilder().apply {
                    this.addField("numberField", "number(123)")
                })
            })
            parentEventIdBuilder.id = eventId
            metadataBuilder.protocol = PROTOCOL
        }
        val messageGroup = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setMessage(message).build()).build()
        val result = codec.encode(messageGroup).getMessages(0).rawMessage.body.toByteArray().toString(UTF_8)
        assertEquals(result, expectedJsonString)
    }
}