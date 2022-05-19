package com.exactpro.th2.codec.json

import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.getField
import com.exactpro.th2.common.message.getList
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DecodeTest {

    @Test
    fun testDecodeJsonRequest() {
        val eventId = "123"
        val jsonString = """
            {
               "stringField": "value",
               "intField": 123,
               "decimalField": 123.1,
               "object": {
                  "objectField": "objectFieldValue"
               },
               "primitiveList": [ 1, 2, 3, 4 ],
               "objectList": [ { "objectField": 123 }, { "anotherObjectField": true } ]
            }
        """.trimIndent()

        val settings = JsonPipelineCodecSettings(true, true)
        val codec = JsonPipelineCodec(settings)
        val message = RawMessage.newBuilder().apply {
            parentEventIdBuilder.id = eventId
            metadataBuilder.protocol = PROTOCOL
            metadataBuilder.idBuilder.direction = Direction.SECOND
            body = ByteString.copyFrom(jsonString.toByteArray())
        }
        val messageGroup = MessageGroup.newBuilder().addMessages(AnyMessage.newBuilder().setRawMessage(message).build()).build()

        val decodedMessage = codec.decode(messageGroup).getMessages(0).message

        assertEquals(decodedMessage.getField("stringField")?.simpleValue, "value")
        assertEquals(decodedMessage.getField("intField")?.simpleValue, "number(123)")
        assertEquals(decodedMessage.getField("decimalField")?.simpleValue, "number(123.1)")

        val objectTest = decodedMessage.getField("object")?.messageValue
        assertNotNull(objectTest)
        assertEquals(objectTest.getField("objectField")?.simpleValue, "objectFieldValue")

        val list = decodedMessage.getField("primitiveList")?.listValue
        assertNotNull(list)
        assertEquals(list.getValues(0)?.simpleValue, "number(1)")

        val objectList = decodedMessage.getField("objectList")?.listValue
        assertNotNull(objectList)
        val listObject = objectList.getValues(1)?.messageValue
        assertNotNull(listObject)
        assertEquals(listObject.getField("anotherObjectField")?.simpleValue, "boolean(true)")
        assertEquals(decodedMessage.parentEventId.id, eventId)
    }
}