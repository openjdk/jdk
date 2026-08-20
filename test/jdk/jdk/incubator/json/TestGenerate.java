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
 * @bug 8381976
 * @summary Tests methods that generate JSON documents
 * @modules jdk.incubator.json
 * @run junit TestGenerate
 */

import java.util.List;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonArray;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestGenerate {

    private static final String SRC =
        """
        [
            { "name": "John", "age": 30, "city": "New-York" },
            { "name": "Jane", "age": 20, "city": "Boston" },
            true,
            false,
            null,
            [ "array", "inside", {"inner-obj": true, "top-level": false}],
            "foo",
            42
        ]
        """;

    @Test
    void testToString() {
        var jv = Json.parse(SRC);
        var result = jv.toString();
        var expected = SRC.replaceAll("[\n ]", "");
        assertEquals(expected, result);
    }

    @Test
    void testToDisplayString_NullIndent() {
        assertThrows(NullPointerException.class,
            () -> Json.toDisplayString(JsonString.of("foo"), null));
    }

    @Test
    void testToDisplayString_InvalidIndent() {
        assertThrows(IllegalArgumentException.class,
            () -> Json.toDisplayString(JsonString.of("foo"), "abc"));
    }

    private static final List<Arguments> DISPLAYSTRING = List.of(
        Arguments.of("",
            """
            [
            {
            "name": "John",
            "age": 30,
            "city": "New-York"
            },
            {
            "name": "Jane",
            "age": 20,
            "city": "Boston"
            },
            true,
            false,
            null,
            [
            "array",
            "inside",
            {
            "inner-obj": true,
            "top-level": false
            }
            ],
            "foo",
            42
            ]"""),
        Arguments.of("  ",
            """
            [
              {
                "name": "John",
                "age": 30,
                "city": "New-York"
              },
              {
                "name": "Jane",
                "age": 20,
                "city": "Boston"
              },
              true,
              false,
              null,
              [
                "array",
                "inside",
                {
                  "inner-obj": true,
                  "top-level": false
                }
              ],
              "foo",
              42
            ]"""),
        Arguments.of("    ",
            """
            [
                {
                    "name": "John",
                    "age": 30,
                    "city": "New-York"
                },
                {
                    "name": "Jane",
                    "age": 20,
                    "city": "Boston"
                },
                true,
                false,
                null,
                [
                    "array",
                    "inside",
                    {
                        "inner-obj": true,
                        "top-level": false
                    }
                ],
                "foo",
                42
            ]"""),
        Arguments.of("\t\r\n\u0020 ",
            """
            [
            \t\r\n\u0020 {
            \t\r\n\u0020 \t\r\n\u0020 "name": "John",
            \t\r\n\u0020 \t\r\n\u0020 "age": 30,
            \t\r\n\u0020 \t\r\n\u0020 "city": "New-York"
            \t\r\n\u0020 },
            \t\r\n\u0020 {
            \t\r\n\u0020 \t\r\n\u0020 "name": "Jane",
            \t\r\n\u0020 \t\r\n\u0020 "age": 20,
            \t\r\n\u0020 \t\r\n\u0020 "city": "Boston"
            \t\r\n\u0020 },
            \t\r\n\u0020 true,
            \t\r\n\u0020 false,
            \t\r\n\u0020 null,
            \t\r\n\u0020 [
            \t\r\n\u0020 \t\r\n\u0020 "array",
            \t\r\n\u0020 \t\r\n\u0020 "inside",
            \t\r\n\u0020 \t\r\n\u0020 {
            \t\r\n\u0020 \t\r\n\u0020 \t\r\n\u0020 "inner-obj": true,
            \t\r\n\u0020 \t\r\n\u0020 \t\r\n\u0020 "top-level": false
            \t\r\n\u0020 \t\r\n\u0020 }
            \t\r\n\u0020 ],
            \t\r\n\u0020 "foo",
            \t\r\n\u0020 42
            ]""")
    );

    @ParameterizedTest
    @FieldSource("DISPLAYSTRING")
    void testDisplayString(String indent, String expected) {
        assertEquals(expected, Json.toDisplayString(Json.parse(SRC), indent));
    }

    @Test
    void testEscapesMemberNames() {
        var json = Json.parse("{ \"a\\\"b\" : null }");
        var display = Json.toDisplayString(json, "  ");

        assertEquals("""
        {
          "a\\\"b": null
        }""", display);

        assertDoesNotThrow(() -> Json.parse(display));
    }

    @Test
    void testDeepNestingToString() {
        assertDoesNotThrow(() -> deepNest().toString());
    }

    @Test
    void testDeepNestingToDisplayString() {
        assertDoesNotThrow(() -> Json.toDisplayString(deepNest(), ""));
    }

    private static JsonValue deepNest() {
        int depth = 10_000;
        JsonValue jv = JsonNumber.of(0);
        for (int i = 0; i < depth; i++) {
            jv = JsonArray.of(List.of(jv));
        }
        return jv;
    }
}
