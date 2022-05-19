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

fun Map<*, *>.toProtoBuilder(): Builder = Message.newBuilder().apply {
    forEach { (name, value) -> this[name.toString()] = value.toProto() }
}

fun Iterable<*>.toProto(): Value = ListValue.newBuilder().apply {
    forEach { addValues(it.toProto()) }
}.toValue()

fun Any?.toProto(): Value = when (this) {
    is Iterable<*> -> toProto()
    is Map<*, *> -> toProtoBuilder().toValue()
    else -> this?.toValue() ?: nullValue()
}