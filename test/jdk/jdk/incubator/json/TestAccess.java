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
 * @summary Unit tests for access methods.
 * @modules jdk.incubator.json
 * @run junit TestAccess
 */

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonValueException;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestAccess {

    private static final JsonValue JSON_ROOT_ARRAY = Json.parse(
            """
            [
              {     "name": "John",
                "age": 42
              },
              {
                "name": "Mary",
                "age": 31
              }
            ]
            """);

    private static final JsonValue JSON_ROOT_OBJECT = Json.parse(
            """
               {
                "id" : 1,
                "values" : [ "value", null ], "valuesWithCommas" : [ "v[a]lu}\\"e", "va,,,[lue,", "value,,", 15],
                "foo" : { "bar" : "baz" },
                "qux" : [ [true], { "in" : { } } ],
                "ba\\"zz" : ["Key with escape"],
                "obj" : { "1" : "{", "z" : 2}
            }
            """);

    private static final JsonValue JSON_NESTED_ARRAY =
            // Don't use the API we are testing (get(String))
            JSON_ROOT_OBJECT.asMap().get("values");

    @Test
    void basicAccessTest() {
        JSON_ROOT_OBJECT.get("id");
        assertEquals("value", JSON_ROOT_OBJECT.get("values").get(0).asString());
        assertTrue(JSON_ROOT_OBJECT.get("values").get(1) instanceof JsonNull);
        assertTrue(JSON_ROOT_OBJECT.get("qux").get(0).get(0).asBoolean());
    }

    @Test
    void boolAndNullFailureTest() {
        var json = Json.parse("{ \"foo\" : null, \"bar\" : false, \"baz\" : true }");
        assertThrows(JsonValueException.class, () -> json.get("foo").tryGet("_"));
        assertThrows(JsonValueException.class, () -> json.get("bar").tryGet("_"));
        assertThrows(JsonValueException.class, () -> json.get("baz").tryGet("_"));
    }

    @Test
    void basicAccessAbsenceTest() {
        var json = Json.parse("{ \"foo\" : null, \"bar\" : \"words\" }");
        assertEquals(Optional.empty(), json.tryGet("baz"));
        assertNull(json.tryGet("baz").map(JsonValue::asString).orElse(null));
        assertEquals("words", json.tryGet("bar").map(JsonValue::asString).orElse(null));
        assertThrows(JsonValueException.class, () -> json.get("foo").tryGet("baz"));
    }

    @Test
    void basicAccessNullTest() {
        var json = Json.parse("{ \"foo\" : null, \"bar\" : \"words\" }");
        assertEquals(Optional.empty(), json.get("foo").tryValue());
        assertNull(json.get("foo").tryValue().map(JsonValue::asString).orElse(null));
        assertEquals("words", json.get("bar").tryValue().map(JsonValue::asString).orElse(null));
    }

    // Ensure that syntactical chars w/in JsonString do not affect path building
    @Test
    void stringTest() {
        assertEquals("JsonNumber is not a JsonString. Path: \"{valuesWithCommas[3\". Location: line 2, position 96.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_OBJECT.get("valuesWithCommas").get(3).asString()).getMessage());
        assertEquals("JsonNumber is not a JsonBoolean. Path: \"{obj{z\". Location: line 6, position 31.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_OBJECT.get("obj").get("z").asBoolean()).getMessage());
    }

    @Test
    void leafExceptionTest() {
        assertEquals("JsonNumber is not a JsonString. Path: \"[1{age\". Location: line 6, position 11.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_ARRAY.get(1).get("age").asString()).getMessage());
    }

    @Test
    void rootArrayTest() {
        assertEquals("JsonObject member \"asge\" does not exist. Path: \"[1\". Location: line 4, position 2.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_ARRAY.get(1).get("asge").asLong()).getMessage());
    }

    // Ensure member name with escapes works
    @Test
    void escapedKeyTest() {
        assertEquals("JsonArray index 1 out of bounds for length 1. Path: \"{ba\\\"zz\". Location: line 5, position 15.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_OBJECT.get("ba\"zz").get(1)).getMessage());
    }

    @Test
    void multiNestedTest() {
        assertEquals("JsonObject member \"zap\" does not exist. Path: \"{qux[1{in\". Location: line 4, position 31.",
                assertThrows(JsonValueException.class,
                        () -> JSON_ROOT_OBJECT.get("qux").get(1).get("in").get("zap")).getMessage());
    }

    // Check array path building behavior for first element, expects '['.
    @Test
    void firstArrayElementTest() {
        assertEquals("JsonArray index 5 out of bounds for length 1. Path: \"{qux[0\". Location: line 4, position 14.",
                assertThrows(JsonValueException.class,
                    () -> JSON_ROOT_OBJECT.get("qux").get(0).get(5)).getMessage());
    }

    // Operations on JsonObject
    @Test
    void failObjectAccessTest() {
        // Points to the start of the root object -> { ...
        assertEquals("JsonObject is not a JsonArray. Path: \"\". Location: line 0, position 3.",
                assertThrows(JsonValueException.class, () -> JSON_ROOT_OBJECT.get(0)).getMessage());
        assertEquals("JsonObject member \"car\" does not exist. Path: \"\". Location: line 0, position 3.",
                assertThrows(JsonValueException.class, () -> JSON_ROOT_OBJECT.get("car")).getMessage());
    }

    // Operations on JsonArray
    @Test
    void failArrayAccessTest() {
        // Points to the JsonArray value of "values"; starts at -> [ "value", null ] ...
        assertEquals("JsonArray is not a JsonObject. Path: \"{values\". Location: line 2, position 15.",
                assertThrows(JsonValueException.class, () -> JSON_NESTED_ARRAY.get("foo")).getMessage());
        assertEquals("JsonArray index 3 out of bounds for length 2. Path: \"{values\". Location: line 2, position 15.",
                assertThrows(JsonValueException.class, () -> JSON_NESTED_ARRAY.get(3)).getMessage());
    }

    @Test
    void failNPETest() {
        // NPE at JsonObject
        assertThrows(NullPointerException.class, () -> JSON_ROOT_OBJECT.get(null));
        // NPE at JsonValue
        assertThrows(NullPointerException.class, () -> JSON_NESTED_ARRAY.get(null));
    }

    // Access on factory created JSON number/string should not have a path
    @Test
    void factoryPathTest() {
        assertFalse(assertThrows(JsonValueException.class, () -> JsonArray.of(
                List.of(JsonNumber.of(1.5))).get(0).asLong()).getMessage().contains("Path"));
        assertFalse(assertThrows(JsonValueException.class, () -> JsonArray.of(
                List.of(JsonNumber.of(1))).get(0).asBoolean()).getMessage().contains("Path"));
        assertFalse(assertThrows(JsonValueException.class, () -> JsonArray.of(
                List.of(JsonNumber.of(1L))).get(0).asBoolean()).getMessage().contains("Path"));
        assertFalse(assertThrows(JsonValueException.class, () -> JsonArray.of(
                List.of(JsonNumber.of("1.5"))).get(0).asBoolean()).getMessage().contains("Path"));
        assertFalse(assertThrows(JsonValueException.class, () -> JsonArray.of(
                List.of(JsonString.of("foo"))).get(0).asBoolean()).getMessage().contains("Path"));
    }
}
