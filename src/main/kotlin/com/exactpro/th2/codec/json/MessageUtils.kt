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

import com.exactpro.th2.common.grpc.ListValue
import com.exactpro.th2.common.grpc.Message
import com.exactpro.th2.common.grpc.Value
import com.exactpro.th2.common.grpc.Value.KindCase.LIST_VALUE
import com.exactpro.th2.common.grpc.Value.KindCase.MESSAGE_VALUE
import com.exactpro.th2.common.grpc.Value.KindCase.NULL_VALUE
import com.exactpro.th2.common.grpc.Value.KindCase.SIMPLE_VALUE
import com.exactpro.th2.common.grpc.Message.Builder
import com.exactpro.th2.common.message.set
import com.exactpro.th2.common.value.nullValue
import com.exactpro.th2.common.value.toValue

fun Message.toMap(): MutableMap<String, Any?> = fieldsMap.mapValuesTo(HashMap()) { it.value.toObject() }

fun ListValue.toList(): MutableList<Any?> = MutableList(valuesCount) { valuesList[it].toObject() }

fun Value.toObject(): Any? = when (kindCase) {
    NULL_VALUE -> null
    SIMPLE_VALUE -> simpleValue
    MESSAGE_VALUE -> messageValue.toMap()
    LIST_VALUE -> listValue.toList()
    else -> error("Unknown value kind: $this")
}

fun Map<String, Any>.toProtoBuilder(): Builder = Message.newBuilder().apply {
    forEach { (name, value) -> this[name] = value.toProto() }
}

fun Iterable<*>.toProto(): Value = ListValue.newBuilder().apply {
    forEach { addValues(it.toProto()) }
}.toValue()

@Suppress("UNCHECKED_CAST")
fun Any?.toProto(): Value = when (this) {
    is Iterable<*> -> toProto()
    is Map<*, *> -> (this as Map<String, Any>).toProtoBuilder().toValue()
    else -> this?.toValue() ?: nullValue()
}