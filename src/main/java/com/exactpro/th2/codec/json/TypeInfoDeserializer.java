package com.exactpro.th2.codec.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.JsonTokenId;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.std.UntypedObjectDeserializer;

import java.io.IOException;

public class TypeInfoDeserializer extends UntypedObjectDeserializer {
    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        int tokenId = p.getCurrentTokenId();
        if (tokenId == JsonTokenId.ID_TRUE || tokenId == JsonTokenId.ID_FALSE) {
            return "boolean(" + p.getBooleanValue() + ")";
        }
        if (tokenId == JsonTokenId.ID_NUMBER_INT || tokenId == JsonTokenId.ID_NUMBER_FLOAT) {
            return "number(" + p.getNumberValue().toString() + ")";
        }
        return super.deserialize(p, ctxt);
    }
}
