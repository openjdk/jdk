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
 * @modules jdk.incubator.json
 * @run junit TestJSONParsing
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import jdk.incubator.json.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJSONParsing {

    private static final String SRC_DIR = System.getProperty("test.src", ".");
    private static final String TEST_DIR_PARSING = "JSONTestSuite/test_parsing";
    private static final String TEST_DIR_TRANSFORM = "JSONTestSuite/test_transform";

    // These fail lists are dependent on our implementation
    // and may or may not be changed in the future
    private static final List<String> PARSING_I_FAIL = Arrays.asList(
            "i_string_UTF-16LE_with_BOM.json",
            "i_string_utf16BE_no_BOM.json",
            "i_string_utf16LE_no_BOM.json",
            "i_structure_UTF-8_BOM_empty_object.json");

    // See RFC 8259.4 - "The names within an object SHOULD be unique."
    // These should be recognized as n (pass to method reference)
    private static final List<String> PARSING_Y_FAIL = Arrays.asList(
            "y_object_duplicated_key_and_value.json",
            "y_object_duplicated_key.json");

    // See unique key note for PARSING_Y_FAIL
    private static final List<String> TRANSFORM_FAIL = Arrays.asList(
            "object_same_key_different_values.json",
            "object_same_key_unclear_values.json",
            "object_same_key_same_value.json");

    // All files in parsing prefixed with y_ or i_
    // that are not found in the designated parsing failure lists
    private static Stream<Path> parsingPass() throws IOException {
        return Files.walk(Paths.get(SRC_DIR, TEST_DIR_PARSING))
                .filter(a -> (a.getFileName().toString().startsWith("y_")
                        || a.getFileName().toString().startsWith("i_")))
                .filter(a -> !PARSING_I_FAIL.contains(a.getFileName().toString()))
                .filter(a -> !PARSING_Y_FAIL.contains(a.getFileName().toString()));
    }

    @ParameterizedTest
    @MethodSource
    public void parsingPass(Path p) {
        testJson(p, true);
    }

    // All files in parsing prefixed with n_
    // or files found in the designated parsing failure lists
    private static Stream<Path> parsingFail() throws IOException {
        return Stream.concat(
                Stream.concat(PARSING_I_FAIL.stream().map(
                        a -> Path.of(SRC_DIR, "JSONTestSuite/test_parsing/", a)),
                        PARSING_Y_FAIL.stream().map(
                                a -> Path.of(SRC_DIR, "JSONTestSuite/test_parsing/", a))),
                Files.walk(Paths.get(SRC_DIR, TEST_DIR_PARSING)).
                        filter(a -> (a.getFileName().toString()).startsWith("n_")));
    }

    @ParameterizedTest
    @MethodSource
    public void parsingFail(Path p) {
        testJson(p, false);
    }

    // Transform files that are not found in the designated transform failure list
    private static Stream<Path> transformPass() throws IOException {
        return Files.walk(Paths.get(SRC_DIR, TEST_DIR_TRANSFORM))
                .filter(a -> !TRANSFORM_FAIL.contains(a.getFileName().toString()));
    }

    @ParameterizedTest
    @MethodSource
    public void transformPass(Path p) {
        testJson(p, true);
    }

    // Transform files found in the designated transform failure list
    private static Stream<Path> transformFail() {
        return TRANSFORM_FAIL.stream().map(a -> Path.of(SRC_DIR, "JSONTestSuite/test_transform/", a));
    }

    @ParameterizedTest
    @MethodSource
    public void transformFail(Path p) {
        testJson(p, false);
    }

    private void testJson(Path p, boolean pass) {
        if (!p.toString().endsWith(".json")) {
            return;
        }
        var fileName = p.getFileName().toString();
        String json;
        try {
            json = new String(Files.readAllBytes(p));
        } catch (IOException e) {
            throw new RuntimeException(String.format("File: %s not found", fileName));
        }
        testJsonString(json, fileName, pass);
    }

    // Test public Json.parse(String)
    private static void testJsonString(String json, String file, boolean pass) {
        if (pass) {
            // Check the JSON String can be parsed
            var jsonValue = assertDoesNotThrow(() -> Json.parse(json),
                    "Parsing failed for : " + json);
            // Check the toString() representation of the JsonValue
            var jsonString = assertDoesNotThrow(jsonValue::toString,
                    "toString failed for : " + json);
            // Check the value representation of the JsonValue as well as the
            // round trip for supplying the value to the factory
            switch (jsonValue) {
                case JsonObject jo -> JsonObject.of(jo.asMap());
                case JsonArray ja -> JsonArray.of(ja.asList());
                case JsonString js -> JsonString.of(js.asString());
                case JsonNumber jn -> {
                    try {
                        JsonNumber.of(jn.asLong());
                    } catch (JsonValueException _) {
                        JsonNumber.of(jn.asDouble());
                    }
                }
                case JsonBoolean jb -> JsonBoolean.of(jb.asBoolean());
                case JsonNull _ -> {}
            }
            // Check round trip for parsing the toString() representation
            assertDoesNotThrow(() -> Json.parse(jsonString),
                    "Parse round trip for toString() failed : " + json);
        } else {
            assertThrows(JsonParseException.class, () -> Json.parse(json),
                    "Parsing did not throw JsonParseException for : " + json);
        }
    }
}
