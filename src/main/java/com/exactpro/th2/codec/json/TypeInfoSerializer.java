package com.exactpro.th2.codec.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeInfoSerializer extends StdSerializer<String> {
    private Pattern numberPattern = Pattern.compile("number\\((\\d*[.|,]\\d|\\d+)\\)");
    private Predicate<String> numberPredicate = numberPattern.asMatchPredicate();

    private Pattern booleanPattern = Pattern.compile("boolean\\((true|false)\\)");
    private Predicate<String> booleanPredicate = booleanPattern.asMatchPredicate();

    protected TypeInfoSerializer() {
        super(String.class);
    }

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        if(numberPredicate.test(value)) {
            Matcher matcher = numberPattern.matcher(value);
            matcher.matches();
            gen.writeNumber(new BigDecimal(matcher.group(1)));
            return;
        }
        if(booleanPredicate.test(value)) {
            Matcher matcher = booleanPattern.matcher(value);
            matcher.matches();
            gen.writeBoolean(Boolean.parseBoolean(matcher.group(1)));
            return;
        }
        gen.writeString(value);
    }
}
