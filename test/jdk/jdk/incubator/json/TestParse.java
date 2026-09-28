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
 * @summary Checks non JSON subtype specific parse behavior
 * @modules jdk.incubator.json
 * @run junit TestParse
 */

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParse {

    private static final String JSON =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    // A basic parse and match example
    @Test
    void testBasicParseAndMatch() {
        var doc = Json.parse(JSON);
        if (doc instanceof JsonObject o && o.asMap() instanceof Map<String, JsonValue> members
                && members.get("name") instanceof JsonString js
                && members.get("shoeSize") instanceof JsonNumber jn) {
            assertEquals("Brian", js.asString());
            assertEquals(10, jn.asLong());
        } else {
            throw new RuntimeException("Test data incorrect");
        }
    }

    // Ensure modifying input char array passed to Json.parse has no impact on JsonValue
    @Test
    void testDefensiveCopy() {
        char[] in = JSON.toCharArray();
        var doc = Json.parse(in);

        // Mutate original char array with nonsense
        Arrays.fill(in, 'A');

        if (doc instanceof JsonObject o
                && o.asMap().get("name") instanceof JsonString js
                && o.asMap().get("shoeSize") instanceof JsonNumber jn) {
            assertEquals("Brian", js.asString());
            assertEquals(10, jn.asLong());
        } else {
            throw new RuntimeException("JsonValue corrupted by input array");
        }
    }

    private static final String JSON_WITH_SPACES =
            """
            [
                { "name": "John", "age": 30, "city": "New York" },
                { "name": "Jane", "age": 20, "city": "Boston" },
                true,
                false,
                null,
                [ "array", "inside", {"inner obj": true, "top-level": false}],
                "foo",
                42
            ]
            """;

    private static final String JSON_EXTRA_SPACES =
            """
            [
           \s
                { "name"    : "John",    "age"  : 30, "city": "New York" },
                {  "name": "Jane"  , "age": 20, "city": "Boston" },
                \s
               \s
                true,   \s
                false   ,
                null, \s
                [    "array"  , "inside", {"inner obj": true, "top-level" : false  } ] ,\s
                "foo",\s
                42
              ]
           \s""";

    // White space is allowed but should have no effect
    // on the underlying structure, and should not play a role during equality
    @Test
    void testWhiteSpaceEquality() {
        var obj = Json.parse(JSON_EXTRA_SPACES);
        var str = assertDoesNotThrow(obj::toString);
        var expStr = Json.parse(JSON_WITH_SPACES).toString();
        // Ensure equivalent Json (besides white space) generates equivalent
        // toString values
        assertEquals(expStr, str);
    }

    @Test
    void testDeepNestingParse() {
        int depth = 10_000;
        var json = "[".repeat(depth) + "0" + "]".repeat(depth);
        var parsed = assertDoesNotThrow(() -> Json.parse(json));
        assertEquals(json, parsed.toString());
    }
}
