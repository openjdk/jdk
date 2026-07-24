/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @modules jdk.incubator.json
 * @run junit TestJsonArray
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonArray {

    @Nested
    class TestParse {

        // Some basic malformed JSON arrays and expected error message
        static List<Arguments> BASIC_FAIL = List.of(
                Arguments.of("[ \"foo\"  ",
                    "JSON Array is not closed with a bracket. Path: \"[\". Location: line 0, position 9."),
                Arguments.of("[ \"foo\",  ",
                    "Expected a JSON Object, Array, String, Number, Boolean, or Null. Path: \"[1\". Location: line 0, position 10."),
                Arguments.of("[ ",
                    "JSON Array is not closed with a bracket. Path: \"[\". Location: line 0, position 2."),
                Arguments.of("null ]",
                    "Additional value(s) were found after the JSON Value. Path: \"\". Location: line 0, position 5."),
                Arguments.of("[ [ [ 0, 1, two ] ] ]",
                    "Unexpected value. Expected a JSON Object, Array, String, Number, Boolean, or Null. Path: \"[0[0[2\". Location: line 0, position 13."));

        @ParameterizedTest
        @FieldSource("BASIC_FAIL")
        void basicFailParse(String json, String expected) {
            var e = assertThrows(JsonParseException.class, () -> Json.parse(json),
                    "String parse did not fail for %s".formatted(json));
            assertEquals(expected, e.getMessage());
            e = assertThrows(JsonParseException.class, () -> Json.parse(json.toCharArray()),
                    "Char parse did not fail for %s".formatted(json));
            assertEquals(expected, e.getMessage());
        }
    }

    @Nested
    class TestFactory {

        // Ensure equivalence of JsonArray created from parse vs of factory
        @Test
        void testFactory() {

            var doc = Json.parse(
            """
            [1, "two", false, null, {"name": 42}, [1]]
            """);

            var expected = JsonArray.of(
                    List.of(
                            JsonNumber.of(1),
                            JsonString.of("two"),
                            JsonBoolean.of(Boolean.FALSE),
                            JsonNull.of(),
                            JsonObject.of(Map.of("name", JsonNumber.of(42))),
                            JsonArray.of(List.of(JsonNumber.of(1)))
                    )
            ).asList();
            if (doc instanceof JsonArray ja) {
                //only compare types
                compareTypes(expected, ja.asList());
            } else {
                throw new RuntimeException("JsonArray expected");
            }
        }

        private static void compareTypes(List<JsonValue> expected, List<JsonValue> actual) {
            assertEquals(expected.size(), actual.size());
            for (int index = 0; index < expected.size(); index++) {
                assertEquals(expected.get(index).getClass(), actual.get(index).getClass());
            }
        }

        @Test
        void immutabilityOfTest() {
            var list = new ArrayList<JsonValue>();
            list.add(JsonString.of("foo"));
            var ja = JsonArray.of(list);
            assertEquals(1, ja.asList().size());
            // Modifications to backed list should not change JsonArray
            list.add(JsonString.of("foo"));
            assertEquals(1, ja.asList().size());
            // Modifications to JsonArray asList() should throw
            assertThrows(UnsupportedOperationException.class,
                    () -> ja.asList().add(JsonNull.of()),
                    "Array values able to be modified");
        }

        @Test
        void nullTest() {
            // null list to of factory
            assertThrows(NullPointerException.class, () -> JsonArray.of(null));
            List<JsonValue> list = new ArrayList<>();
            list.add(null);
            // JsonArray.of() should throw as typed to JsonValue
            assertThrows(NullPointerException.class, () -> JsonArray.of(list));
        }
    }
}
