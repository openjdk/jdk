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
 * @enablePreview
 * @run junit TestJsonNumber
 */

import java.util.List;
import jdk.incubator.json.Json;
import jdk.incubator.json.JsonValueException;
import jdk.incubator.json.JsonNumber;
import jdk.incubator.json.JsonParseException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonNumber {

    @Nested
    class TestValue {

        @ParameterizedTest
        @MethodSource
        void testUniformRepresentations(String str, double db, long l, int i) {
            var json = Json.parse(str);
            assertEquals(db, json.asDouble());
            assertEquals(l, json.asLong());
            assertEquals(i, json.asInt());
            json = Json.parse("-" + str);
            assertEquals(-db, json.asDouble());
            assertEquals(-l, json.asLong());
            assertEquals(-i, json.asInt());
        }

        private static Stream<Arguments> testUniformRepresentations() {
            return Stream.of(
                    Arguments.of("5", 5d, 5L, 5),
                    Arguments.of("5.0", 5d, 5L, 5),
                    Arguments.of("5.00", 5d, 5L, 5),
                    Arguments.of("5e0", 5d, 5L, 5),
                    Arguments.of("5e+0", 5d, 5L, 5),
                    Arguments.of("5e-0", 5d, 5L, 5),
                    Arguments.of("5e3", 5e3, 5000L, 5000),
                    Arguments.of("50e-1", 50e-1, 5L, 5),
                    Arguments.of("50.0e-1", 50.0e-1, 5L, 5),
                    Arguments.of("555.5e5", 555.5e5, 55550000L, 55550000),
                    Arguments.of("555.5e1", 555.5e1, 5555L, 5555),
                    Arguments.of("0e999999999999", 0d, 0L, 0),
                    Arguments.of("0.0e-999999999999", 0d, 0L, 0)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testDoubleRepresentation(String str, double d) {
            var json = Json.parse(str);
            assertEquals(d, json.asDouble());
            assertThrows(JsonValueException.class, json::asLong);
            json = Json.parse("-" + str);
            assertEquals(-d, json.asDouble());
            assertThrows(JsonValueException.class, json::asLong);
        }

        private static Stream<Arguments> testDoubleRepresentation() {
            return Stream.of(
                    Arguments.of("0.01", 0.01),
                    Arguments.of("0.3232e-3", 0.3232e-3),
                    Arguments.of("0.55", 0.55),
                    Arguments.of("55.55", 55.55),
                    Arguments.of("5e-5", 5e-5),
                    Arguments.of("5.55e-5", 5.55e-5),
                    Arguments.of("5.00e-5", 5.00e-5),
                    Arguments.of("5e100", 5e100),
                    Arguments.of("55.55e1", 55.55e1),
                    Arguments.of("55.55e+1", 55.55e1),
                    Arguments.of("1.7976931348623157E308", 1.7976931348623157E308),
                    Arguments.of("9223372036854775999", 9223372036854775999d),
                    Arguments.of("9223372036854775999e0", 9223372036854775999d),
                    Arguments.of("5e-100", 5e-100),
                    Arguments.of("5.000e-100", 5e-100),
                    Arguments.of("1e-999999999999", 0.0)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testLongRepresentation(String str, long l) {
            var json = Json.parse(str);
            assertEquals(l, json.asLong());
            assertDoesNotThrow(json::asDouble);
            json = Json.parse("-" + str);
            assertEquals(-l, json.asLong());
            assertDoesNotThrow(json::asDouble);
        }

        private static Stream<Arguments> testLongRepresentation() {
            return Stream.of(
                    Arguments.of("9007199254740993", 9007199254740993L),
                    Arguments.of("9007199254740993.0", 9007199254740993L),
                    Arguments.of("9007199254740993.0e0", 9007199254740993L),
                    Arguments.of("9223372036854775807", 9223372036854775807L),
                    Arguments.of("9223372036854775807.0", 9223372036854775807L),
                    Arguments.of("9223372036854775807.0e0", 9223372036854775807L),
                    Arguments.of("9223372036854775807.0e-0", 9223372036854775807L),
                    Arguments.of("92233720368547758070.0e-1", 9223372036854775807L),
                    Arguments.of("922337203685477580700.0e-2", 9223372036854775807L)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testIntRepresentation(String str, int i) {
            var json = Json.parse(str);
            assertEquals(i, json.asInt());
            assertDoesNotThrow(json::asLong);
            assertDoesNotThrow(json::asDouble);
            json = Json.parse("-" + str);
            assertEquals(-i, json.asInt());
            assertDoesNotThrow(json::asLong);
            assertDoesNotThrow(json::asDouble);
        }

        private static Stream<Arguments> testIntRepresentation() {
            return Stream.of(
                Arguments.of("2147483647", Integer.MAX_VALUE),
                Arguments.of("2147483647.0", Integer.MAX_VALUE),
                Arguments.of("2147483647.0e0", Integer.MAX_VALUE)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testDoubleOutOfRange(String str) {
            var json = Json.parse(str);
            assertThrows(JsonValueException.class, json::asDouble);
        }

        private static Stream<Arguments> testDoubleOutOfRange() {
            return Stream.of(
                Arguments.of("9e111111111111"),
                Arguments.of("-9e111111111111"),
                Arguments.of("1.7976931348623159E308"),
                Arguments.of("-1.7976931348623159E308")
            );
        }

        @ParameterizedTest
        @MethodSource
        void testLongOutOfRange(String str) {
            var json = Json.parse(str);
            assertThrows(JsonValueException.class, json::asLong);
        }

        private static Stream<Arguments> testLongOutOfRange() {
            return Stream.of(
                Arguments.of("9e111111111111"),
                Arguments.of("-9e111111111111"),
                Arguments.of("9223372036854775808"),
                Arguments.of("-9223372036854775809")
            );
        }

        @ParameterizedTest
        @MethodSource
        void testIntOutOfRange(String str) {
            var json = Json.parse(str);
            assertThrows(JsonValueException.class, json::asInt);
        }

        private static Stream<Arguments> testIntOutOfRange() {
            return Stream.of(
                Arguments.of("2147483648"),
                Arguments.of("-2147483649")
            );
        }
    }

    @Nested
    class TestParse {

        @ParameterizedTest
        @MethodSource("parseCases")
        void testToString_Parsed(String src) {
            // assert their toString() returns the original text
            assertEquals(src, Json.parse(src).toString());
        }

        private static Stream<Arguments> parseCases() {
            return Stream.of(
                    Arguments.of("1"),
                    Arguments.of("0"),
                    Arguments.of("9223372036854775807"),
                    Arguments.of("-9223372036854775808"),
                    Arguments.of("1.0"),
                    Arguments.of("9223372036854775807.0"),
                    Arguments.of("-9223372036854775808.0"),
                    Arguments.of("9223372036854775807e0"),
                    Arguments.of("-9223372036854775807e0"),
                    Arguments.of("1e0"),
                    Arguments.of("1e-0"),
                    Arguments.of("0.0"),
                    Arguments.of("-0.0"),
                    Arguments.of("0e0"),
                    Arguments.of("0e1"),
                    Arguments.of("0e-0"),
                    Arguments.of("0e-1"),
                    Arguments.of("5.5e1"),
                    Arguments.of("1.0e1"),
                    Arguments.of("1.0"),
                    Arguments.of("1.000"),
                    Arguments.of("0.001e3"),
                    Arguments.of("5.5"),
                    Arguments.of("4.9999999"),
                    Arguments.of("4.999999999999999999999999999999999999"),
                    Arguments.of("9007199254740989.5"),
                    Arguments.of("9007199254740990.999999999999"),
                    Arguments.of("0.0000123E-0000000045"),
                    Arguments.of("5."+"5".repeat(17)),
                    Arguments.of("55.55e1"),
                    Arguments.of("1e-1"),
                    Arguments.of("9223372036854775806.5"),
                    Arguments.of("-9223372036854775807.5"),
                    Arguments.of("4.9E-324"),
                    Arguments.of("9223372036854775807.5e0"),
                    Arguments.of("9223372036854775808e0"),
                    Arguments.of("1.7976931348623157E308")
            );
        }

        private static Stream<Arguments> testToStringEquality() {
            return Stream.of(
                    // true
                    Arguments.of("3", "3"),
                    Arguments.of("3", "   3   "),
                    Arguments.of("3.0", "3.0"),
                    Arguments.of("3e0", "3E0"),
                    Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383279"),

                    // false
                    Arguments.of("3", "3.0"),
                    Arguments.of("3.0", "3.000"),
                    Arguments.of("3", "3e0"),
                    Arguments.of("0.0", "-0.0"),
                    Arguments.of("3", "4"),
                    Arguments.of("3.0", "3.1"),
                    Arguments.of("3", "3.1"),
                    Arguments.of("3.0", "3.001"),
                    Arguments.of("3", "3e1"),
                    Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383278")
            );
        }

        @ParameterizedTest
        @MethodSource
        void testToStringEquality(String arg1, String arg2) {
            var jv1 = Json.parse(arg1);
            var jv2 = Json.parse(arg2);

            // assert their toString() returns the original text (w/o leading/trailing spaces)
            var a1 = arg1.trim();
            var a2 = arg2.trim();
            assertEquals(a1, jv1.toString());
            assertEquals(a2, jv2.toString());
        }

        private static final List<Arguments> INVALID_NUMBER = List.of(
                Arguments.of("00", "Invalid position of '0' within JSON Number. Path: \"\". Location: line 0, position 1."),
                Arguments.of("-00", "Invalid position of '0' within JSON Number. Path: \"\". Location: line 0, position 2."),
                Arguments.of("01", "Invalid position of '0' within JSON Number. Path: \"\". Location: line 0, position 1."),
                Arguments.of("5e-2+2", "Invalid position of '+' within JSON Number. Path: \"\". Location: line 0, position 4."),
                Arguments.of("+5", "Invalid position of '+' within JSON Number. Path: \"\". Location: line 0, position 0."),
                Arguments.of("5e+2-2", "Invalid position of '-' within JSON Number. Path: \"\". Location: line 0, position 4."),
                Arguments.of("5e2+", "Invalid position of '+' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of("5e2+2", "Invalid position of '+' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of("5e2-", "Invalid position of '-' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of("5e2-2", "Invalid position of '-' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of(".5", "Invalid position of '.' within JSON Number. Path: \"\". Location: line 0, position 0."),
                Arguments.of("5e.2", "Invalid position of '.' within JSON Number. Path: \"\". Location: line 0, position 2."),
                Arguments.of("5.5.5", "Invalid position of '.' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of("5e3e", "Invalid position of 'e' within JSON Number. Path: \"\". Location: line 0, position 3."),
                Arguments.of("e2", "Invalid position of 'e' within JSON Number. Path: \"\". Location: line 0, position 0."),
                Arguments.of("e", "Invalid position of 'e' within JSON Number. Path: \"\". Location: line 0, position 0."),
                Arguments.of("5.", "Input expected after '[.|e|E]'. Path: \"\". Location: line 0, position 2."),
                Arguments.of("5e", "Input expected after '[.|e|E]'. Path: \"\". Location: line 0, position 2."));

        @ParameterizedTest
        @FieldSource("INVALID_NUMBER")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            assertEquals(err, e.getMessage());
        }
    }

    @Nested
    class TestFactory {

        @Test
        void testInfinity() {
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.POSITIVE_INFINITY));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NEGATIVE_INFINITY));
        }

        @Test
        void testNan() {
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NaN));
            // parse test not required for Nan, cannot parse "NaN"
        }

        @Test
        void testToString_factory() {
            assertEquals("42", JsonNumber.of((byte)42).toString());
            assertEquals("42", JsonNumber.of((short)42).toString());
            assertEquals("42", JsonNumber.of(42).toString());
            assertEquals("42", JsonNumber.of(42L).toString());
            assertEquals(JsonNumber.of(Integer.MAX_VALUE).toString(), Integer.valueOf(Integer.MAX_VALUE).toString());
            assertEquals(JsonNumber.of(Long.MAX_VALUE).toString(), Long.valueOf(Long.MAX_VALUE).toString());
            assertEquals(JsonNumber.of(0.1f).toString(), Double.valueOf(0.1f).toString());
            assertEquals("0.1", JsonNumber.of(0.1d).toString());
            assertEquals("42.0", JsonNumber.of(42.0d).toString());
            assertEquals("42.0", JsonNumber.of(420e-1).toString());
            assertEquals("4.2E7", JsonNumber.of(42e6).toString());
            assertEquals(JsonNumber.of(Double.MAX_VALUE).toString(), Double.valueOf(Double.MAX_VALUE).toString());
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("foo"));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("true"));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("\"foo\""));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("null"));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("[1, 2]"));
            assertThrows(IllegalArgumentException.class, () -> JsonNumber.of("{\"foo\": 42}"));
        }

        @Test
        void testRoundTrip() {
            // factories

            // int
            assertEquals(42, JsonNumber.of((byte)42).asInt());
            assertEquals(42, JsonNumber.of((short)42).asInt());
            assertEquals(42, JsonNumber.of(42).asInt());
            assertEquals(42, JsonNumber.of(42L).asInt());
            assertEquals(42, JsonNumber.of(42.0d).asInt());
            assertEquals(42, JsonNumber.of(420e-1).asInt());
            assertEquals(42_000_000, JsonNumber.of(42e6).asInt());
            assertEquals(42, JsonNumber.of("42").asInt());
            assertEquals(Integer.MAX_VALUE, JsonNumber.of(Integer.MAX_VALUE).asInt());
            assertEquals(Integer.MAX_VALUE, JsonNumber.of("2147483647").asInt());

            // long
            assertEquals(42L, JsonNumber.of((byte)42).asLong());
            assertEquals(42L, JsonNumber.of((short)42).asLong());
            assertEquals(42L, JsonNumber.of(42).asLong());
            assertEquals(42L, JsonNumber.of(42L).asLong());
            assertEquals(42L, JsonNumber.of(42.0d).asLong());
            assertEquals(42L, JsonNumber.of(420e-1).asLong());
            assertEquals(42_000_000L, JsonNumber.of(42e6).asLong());
            assertEquals(42L, JsonNumber.of("42").asLong());
            assertEquals(Long.MAX_VALUE, JsonNumber.of("9223372036854775807").asLong());
            assertEquals(Long.MAX_VALUE, JsonNumber.of(Long.MAX_VALUE).asLong());

            // double
            assertEquals((double)0.1f, JsonNumber.of(0.1f).asDouble());
            assertEquals(0.1d, JsonNumber.of(0.1d).asDouble());
            assertEquals(1d, JsonNumber.of(1e0).asDouble());
            assertEquals(Double.MAX_VALUE, JsonNumber.of(Double.MAX_VALUE).asDouble());
            assertEquals(0.1d, JsonNumber.of("0.1").asDouble());
            assertEquals(1d, JsonNumber.of("1e0").asDouble());
            assertEquals(Double.MAX_VALUE, JsonNumber.of("1.7976931348623157E308").asDouble());
        }

        @ParameterizedTest
        @MethodSource
        void factoryTest(Number n) {
            var str = switch (n) {
                case byte b -> JsonNumber.of(b).toString();
                case short s -> JsonNumber.of(s).toString();
                case int i -> JsonNumber.of(i).toString();
                case long l -> JsonNumber.of(l).toString();
                case float f -> JsonNumber.of(f).toString();
                case double d -> JsonNumber.of(d).toString();
                default -> throw new IllegalArgumentException("incorrect test argument");
            };
            var expected = switch (n) {
                case byte b -> Long.toString(b);
                case short s -> Long.toString(s);
                case int i -> Long.toString(i);
                case long l -> Long.toString(l);
                case float f -> Double.toString(f);
                case double d -> Double.toString(d);
                default -> throw new IllegalArgumentException("incorrect test argument");
            };
            assertEquals(str, expected);
        }

        private static Stream<Arguments> factoryTest() {
            return Stream.of(
                    Arguments.of((byte)1),
                    Arguments.of((short)1),
                    Arguments.of((int)1),
                    Arguments.of(1L),
                    Arguments.of(1.0000000596046448f), // 1.0000001f -> 1.0000001192092896d
                    Arguments.of(1.0000000596046448d)
            );
        }
    }
}
