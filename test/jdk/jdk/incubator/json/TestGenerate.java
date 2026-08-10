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
import jdk.incubator.json.JsonString;

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
    void testToDisplayString_NegativeIndent() {
        assertThrows(IllegalArgumentException.class,
            () -> Json.toDisplayString(JsonString.of("foo"), -1));
    }

    private static final List<Arguments> DISPLAYSTRING = List.of(
        Arguments.of(0,
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
        Arguments.of(2,
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
        Arguments.of(4,
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
            ]""")
    );

    @ParameterizedTest
    @FieldSource("DISPLAYSTRING")
    void testDisplayString(int indent, String expected) {
        assertEquals(expected, Json.toDisplayString(Json.parse(SRC), indent));
    }

    @Test
    void testEscapesMemberNames() {
        var json = Json.parse("{ \"a\\\"b\" : null }");
        var display = Json.toDisplayString(json, 2);

        assertEquals("""
        {
          "a\\\"b": null
        }""", display);

        assertDoesNotThrow(() -> Json.parse(display));
    }
}
