/*
 * Copyright 2025 Exactpro (Exactpro Systems Limited)
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

import com.exactpro.th2.codec.util.toProto
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.message.addField
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.Direction
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.EventId
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.GroupBatch
import com.exactpro.th2.common.schema.message.impl.rabbitmq.transport.MessageId
import com.exactpro.th2.common.value.add
import com.exactpro.th2.common.value.toValue
import com.fasterxml.jackson.databind.ObjectMapper
import java.lang.System.currentTimeMillis
import java.time.Instant


internal val MAPPER = ObjectMapper()
internal const val ROOT_ARRAY_FIELD = "test-root-array-field"
internal const val BOOK = "test-book"
internal const val SCOPE = "test-scope"
internal val TRANSPORT_EVENT_ID = EventId(currentTimeMillis().toString(), BOOK, SCOPE, Instant.now())
internal val PROTO_EVENT_ID = TRANSPORT_EVENT_ID.toProto()
internal val TRANSPORT_MESSAGE_ID = MessageId(
    "test-session-alias", Direction.OUTGOING,
    currentTimeMillis(), Instant.now()
)
internal val PROTO_MESSAGE_ID = TRANSPORT_MESSAGE_ID.toProto(GroupBatch("test-book", "test-session-group"))

internal fun wrapProto(orig: Any): Value = when (orig) {
    is Map<*, *> -> Value.newBuilder().apply {
        messageValueBuilder.apply {
            orig.forEach { (key, value) ->
                addField(requireNotNull(key).toString(), wrapProto(requireNotNull(value)))
            }
        }
    }.build()

    is List<*> -> Value.newBuilder().apply {
        listValueBuilder.apply {
            orig.forEach { value ->
                add(wrapProto(requireNotNull(value)))
            }
        }
    }.build()

    is Number -> Value.newBuilder().setSimpleValue("number(${orig})").build()
    is Boolean -> Value.newBuilder().setSimpleValue("boolean(${orig})").build()
    else -> orig.toValue()
}

internal fun wrapTransport(orig: Any): Any = when (orig) {
    is Map<*, *> -> buildMap {
        orig.forEach { (key, value) ->
            put(requireNotNull(key).toString(), wrapTransport(requireNotNull(value)))
        }
    }
    is List<*> -> buildList {
        orig.forEach { value ->
            add(wrapTransport(requireNotNull(value)))
        }
    }
    is Number -> "number(${orig})"
    is Boolean -> "boolean(${orig})"
    else -> orig.toString()
}