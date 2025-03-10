package io.carbonintensity.executionplanner.runtime.impl.rest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonWriter;
import jakarta.json.stream.JsonParser;

import io.carbonintensity.executionplanner.runtime.impl.CarbonIntensity;

/**
 * A utility class to parse and serialize {@link CarbonIntensity} objects from and to JSON.
 */
public final class CarbonIntensityJsonParser {

    private static final Charset CHARSET = StandardCharsets.UTF_8;
    private static final String FIELD_START = "start";
    private static final String FIELD_END = "end";
    private static final String FIELD_RESOLUTION = "resolution";
    private static final String FIELD_ZONE = "zone";
    private static final String FIELD_DATA = "data";

    public CarbonIntensity parse(InputStream inputStream) {
        var result = new CarbonIntensity();
        try (JsonParser parser = Json.createParser(new InputStreamReader(inputStream, CHARSET))) {
            if (parser.hasNext()) {
                var event = parser.next();
                if (event == JsonParser.Event.START_OBJECT) {
                    var jsonWrapper = new JsonWrapper(parser.getObject());
                    result.setStart(jsonWrapper.getInstant(FIELD_START));
                    result.setEnd(jsonWrapper.getInstant(FIELD_END));
                    result.setResolution(jsonWrapper.getDuration(FIELD_RESOLUTION));
                    result.setZone(jsonWrapper.getString(FIELD_ZONE));
                    result.setData(jsonWrapper.getBigDecimalList(FIELD_DATA));
                }
            }
        }
        return result;
    }

    public String toJson(CarbonIntensity carbonIntensity) {
        var dataJsonArray = Json.createArrayBuilder();
        carbonIntensity.getData().forEach(dataJsonArray::add);
        var rootObject = Json.createObjectBuilder()
                .add(FIELD_START, carbonIntensity.getStart() != null ? carbonIntensity.getStart().toString() : "")
                .add(FIELD_END, carbonIntensity.getEnd() != null ? carbonIntensity.getEnd().toString() : "")
                .add(FIELD_RESOLUTION,
                        carbonIntensity.getResolution() != null ? carbonIntensity.getResolution().toString() : "")
                .add(FIELD_ZONE, carbonIntensity.getZone())
                .add(FIELD_DATA, dataJsonArray)
                .build();

        var stringWriter = new StringWriter();
        try (JsonWriter jsonWriter = Json.createWriter(stringWriter)) {
            jsonWriter.write(rootObject);
            return stringWriter.toString();
        }
    }

    private static class JsonWrapper {
        final JsonObject jsonObject;

        private JsonWrapper(JsonObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        Instant getInstant(String property) {
            var value = getString(property);
            return value != null && !value.isEmpty() ? Instant.parse(value) : null;
        }

        String getString(String property) {
            return jsonObject.get(property) != null ? jsonObject.getString(property) : null;
        }

        Duration getDuration(String property) {
            var value = getString(property);
            return value != null && !value.isEmpty() ? Duration.parse(value) : null;
        }

        List<BigDecimal> getBigDecimalList(String property) {
            var result = new ArrayList<BigDecimal>();
            if (jsonObject.get(property) != null) {
                var jsonArray = jsonObject.getJsonArray(property);
                jsonArray.forEach(jsonValue -> {
                    var jsonNumber = (JsonNumber) jsonValue;
                    result.add(jsonNumber.bigDecimalValue());
                });
            }
            return result;
        }

    }

}
