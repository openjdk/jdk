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
 * @run junit TestJsonString
 */

import java.util.Arrays;
import java.util.List;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonParseException;
import jdk.incubator.json.JsonString;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonString {

    @Nested
    class TestValue {

        @Test
        void valueTest() {
            var untypedStr = "\t";
            var jsonStr = "\"\\u0009\"";
            // Both should compare as equals via asString() which is \t
            assertEquals(JsonString.of(untypedStr).asString(), ((JsonString) Json.parse(jsonStr)).asString());
            // Factory escapes \t to \\t but parse retains the original U sequence
            // (due to its lazy nature) and that is OK
            assertNotEquals(JsonString.of(untypedStr).toString(), Json.parse(jsonStr).toString());
        }

        // Escape sequence tests on asString()
        @ParameterizedTest
        @MethodSource
        void escapeTest(String src, String expected) {
            assertEquals(expected, ((JsonString)Json.parse(src)).asString());
        }
        private static Stream<Arguments> escapeTest() {
            return Stream.of(
                    Arguments.of("\"\\\"\"", "\""),
                    Arguments.of("\"\\\\\"", "\\"),
                    Arguments.of("\"\\/\"", "/"),
                    Arguments.of("\"\\b\"", "\b"),
                    Arguments.of("\"\\f\"", "\f"),
                    Arguments.of("\"\\n\"", "\n"),
                    Arguments.of("\"\\r\"", "\r"),
                    Arguments.of("\"\\t\"", "\t"),
                    Arguments.of("\"\\uD834\\uDD1E\"", "\uD834\uDD1E")
            );
        }
    }

    @Nested
    class TestParse {

        // All JsonString related parse failure messages
        private static final List<Arguments> FAIL_STRING = List.of(
                Arguments.of("\"\t", "Unescaped control code. Path: \"\". Location: line 0, position 1."),
                Arguments.of("\"foo\\a \"", "Unrecognized escape sequence: \"\\a\". Path: \"\". Location: line 0, position 5."),
                Arguments.of("\"foo\\u0\"", "Invalid Unicode escape sequence. Expected four hex digits. Path: \"\". Location: line 0, position 5."),
                Arguments.of("\"foo\\uZZZZ\"", "Invalid Unicode escape sequence. 'Z' is not a hex digit. Path: \"\". Location: line 0, position 6."),
                Arguments.of("\"foo ", "JSON String is not closed with a quotation mark. Path: \"\". Location: line 0, position 5."));

        @ParameterizedTest
        @FieldSource("FAIL_STRING")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            assertEquals(err, e.getMessage());
        }

        private static Stream<Arguments> testStringEquality() {
            return Stream.of(
                    Arguments.of("\"afo\"", "\"afo\"", true),
                    Arguments.of("\"afo\"", new char[]{'"', 'a', 'f', 'o', '"'}, true),
                    Arguments.of("\"afo\"", new char[]{'"', '\\', 'u', '0', '0', '6', '1', 'f', 'o', '"'}, true)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testStringEquality(Object arg1, Object arg2) {
            var jv1 = arg1 instanceof String s ? Json.parse(s) :
                    arg1 instanceof char[] ca ? Json.parse(ca) : null;
            var jv2 = arg2 instanceof String s ? Json.parse(s) :
                    arg2 instanceof char[] ca ? Json.parse(ca) : null;
            var val1 = jv1 instanceof JsonString js ? js.asString() : null;
            var val2 = jv2 instanceof JsonString js ? js.asString() : null;

            // two JsonValue arguments should have the same asString()
            assertEquals(val1, val2);

            // assert their toString() returns the original text
            assertEquals(arg1 instanceof char[] ca ? new String(ca) : arg1, jv1.toString());
        }

        // Ensure decoded escape sequences are translated to valid JSON
        // Supported 2 char escapes should be translated, otherwise U sequence
        // needs to be preserved.
        @Test
        void controlCodeRoundTripTest() {
            for (int i = 0; i < 32; i++) {
                var sequence = "\\u" + String.format("%04x", i);
                Json.parse(Json.parse("\"" + sequence + "\"").toString());
            }
        }
    }

    @Nested
    class TestFactory {

        private static final List<Arguments> ESCAPES = List.of(
                // No escape
                Arguments.of("foo", "\"foo\""),
                // Escape in front
                Arguments.of("\" foo", "\"\\\" foo\""),
                // Escape in back
                Arguments.of("foo \"", "\"foo \\\"\""),
                // Various escapes
                Arguments.of("foo \\\\ \\ \\u0008 \t \b \u0000 \u0001 \u0008"
                        , "\"foo \\\\\\\\ \\\\ \\\\u0008 \\t \\b \\u0000 \\u0001 \\b\""));

        @ParameterizedTest
        @FieldSource("ESCAPES")
        void escapeTest(String str, String expected) {
            assertEquals(expected, JsonString.of(str).toString());
        }

        // Ensure the String passed to the factory (which requires escaping) can be
        // round tripped both into a parse call and a factory call.
        @Test
        void controlCodeRoundTripTest() {
            // 0 -> 31 Control chars
            var reservedChars = Arrays.copyOf(IntStream.range(0, 32).toArray(), 34);
            reservedChars[32] = 34; // Double quote
            reservedChars[33] = 92; // Reverse solidus
            for (int i : reservedChars) {
                var js = JsonString.of(String.valueOf((char)i));
                Json.parse(js.toString());
                JsonString.of(js.asString());
            }
        }
    }
}
