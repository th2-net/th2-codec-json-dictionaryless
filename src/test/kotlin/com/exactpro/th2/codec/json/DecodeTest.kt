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

import com.exactpro.th2.codec.json.JsonCodecFactory.Companion.PROTOCOL
import com.exactpro.th2.common.grpc.AnyMessage
import com.exactpro.th2.common.grpc.Direction
import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.MessageGroup
import com.exactpro.th2.common.grpc.RawMessage
import com.exactpro.th2.common.message.get
import com.exactpro.th2.common.message.getField
import com.exactpro.th2.common.message.getList
import com.exactpro.th2.common.message.getMessage
import com.exactpro.th2.common.message.getString
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
               "decimalField": 123.100000000000000000,
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

        assertEquals(decodedMessage.getString("stringField"), "value")
        assertEquals(decodedMessage.getString("intField"), "number(123)")
        assertEquals(decodedMessage.getString("decimalField"), "number(123.1)")

        val objectTest = decodedMessage.getMessage("object")
        assertNotNull(objectTest)
        assertEquals(objectTest.getString("objectField"), "objectFieldValue")

        val list = decodedMessage.getList("primitiveList")
        assertNotNull(list)
        assertEquals(list[0]?.simpleValue, "number(1)")

        val objectList = decodedMessage.getList("objectList")
        assertNotNull(objectList)
        val listObject = objectList[1]?.messageValue
        assertNotNull(listObject)
        assertEquals(listObject.getString("anotherObjectField"), "boolean(true)")
        assertEquals(decodedMessage.parentEventId.id, eventId)
    }
}