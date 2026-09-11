/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8381976
 * @summary Tests for other implementations that extend JsonValue sub-interfaces
 * @modules jdk.incubator.json
 * @run junit TestOtherImpl
 */

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestOtherImpl {

    private static final JsonString STANDARD_JSON_STRING = JsonString.of("bar");
    private static final JsonString ALT_JSON_STRING =
            new JsonFooString("bar".getBytes(StandardCharsets.UTF_8));

    @Test
    void equalsToStringTest() {
        assertEquals(STANDARD_JSON_STRING.toString(), ALT_JSON_STRING.toString());
    }

    @Test
    void displayStringTest() {
        assertEquals(Json.toDisplayString(STANDARD_JSON_STRING, "  "), Json.toDisplayString(ALT_JSON_STRING, "  "));
        // Wrap it in a JsonObject, and check display string equality again
        assertEquals(Json.toDisplayString(JsonObject.of(Map.of("foo", STANDARD_JSON_STRING)), "  "),
                Json.toDisplayString(JsonObject.of(Map.of("foo", ALT_JSON_STRING)), "  "));
    }

    static class JsonFooString implements JsonString {

        private final String theString;

        public JsonFooString(byte[] bytes) {
            theString = new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public String asString() {
            // For testing purposes, just return the String.
            // Real implementations must adhere to un-escaping as specified.
            return theString;
        }

        @Override
        public String toString() {
            return "\""+theString+"\"";
        }
    }

    // These implementation classes exist to verify that each JsonValue
    // sub-interface is non-sealed. No test execution is required;
    // successful compilation is sufficient.
    static class JsonFooArray implements JsonArray {
        @Override
        public List<JsonValue> asList() {
            return List.of();
        }
    }
    static class JsonFooBoolean implements JsonBoolean {
        @Override
        public boolean asBoolean() {
            return false;
        }
    }
    static class JsonFooNull implements JsonNull {}
    static class JsonFooNumber implements JsonNumber {
        @Override
        public int asInt() {
            return 0;
        }
        @Override
        public long asLong() {
            return 0;
        }
        @Override
        public double asDouble() {
            return 0;
        }
    }
    static class JsonFooObject implements JsonObject {
        @Override
        public Map<String, JsonValue> asMap() {
            return Map.of();
        }
    }
}

