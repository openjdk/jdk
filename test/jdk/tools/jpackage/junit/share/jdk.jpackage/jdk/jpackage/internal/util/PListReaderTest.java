/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import javax.xml.parsers.ParserConfigurationException;
import jdk.jpackage.internal.util.PListReader.Raw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class PListReaderTest {

    enum QueryType {
        STRING(PListReader::queryValue),
        BOOLEAN(PListReader::queryBoolValue),
        DICT((plistReader, keyName) -> {
            return plistReader.queryDictValue(keyName).toMap(true);
        }),
        STRING_ARRAY(PListReader::queryStringArrayValue),
        RAW_ARRAY((plistReader, keyName) -> {
            return plistReader.queryArrayValue(keyName, false).toList();
        }),
        RAW_ARRAY_RECURSIVE((plistReader, keyName) -> {
            return plistReader.queryArrayValue(keyName, true).toList();
        }),
        TO_MAP((plistReader, _) -> {
            return plistReader.toMap(false);
        }),
        TO_MAP_RECURSIVE((plistReader, _) -> {
            return plistReader.toMap(true);
        }),
        ;

        QueryType(BiFunction<PListReader, String, ?> queryMethod) {
            this.queryMethod = Objects.requireNonNull(queryMethod);
        }

        @SuppressWarnings("unchecked")
        <T> T queryValue(PListReader pListReader, String keyName) {
            return (T)queryMethod.apply(pListReader, keyName);
        }

        private final BiFunction<PListReader, String, ?> queryMethod;
    }

    public record TestSpec(QueryType queryType, Optional<String> keyName, Optional<Object> expectedValue,
            Optional<Class<? extends RuntimeException>> expectedException, String... xml) {

        public TestSpec {
            Objects.requireNonNull(queryType);
            Objects.requireNonNull(keyName);
            Objects.requireNonNull(expectedValue);
            Objects.requireNonNull(expectedException);
            Objects.requireNonNull(xml);
            if (expectedValue.isEmpty() == expectedException.isEmpty()) {
                throw new IllegalArgumentException();
            }
            if (keyName.isEmpty()) {
                switch (queryType) {
                    case TO_MAP, TO_MAP_RECURSIVE -> {}
                    default -> {
                        throw new IllegalArgumentException();
                    }
                }
            }
        }

        static final class Builder {

            Builder queryType(QueryType v) {
                queryType = v;
                return this;
            }

            Builder keyName(String v) {
                keyName = v;
                return this;
            }

            Builder expect(Object v) {
                expectedValue = v;
                if (v instanceof String) {
                    queryType(QueryType.STRING);
                } else if (v instanceof Boolean) {
                    queryType(QueryType.BOOLEAN);
                } else if (v instanceof List<?>) {
                    queryType(QueryType.STRING_ARRAY);
                } else if (v instanceof Map<?, ?>) {
                    queryType(QueryType.DICT);
                }
                return this;
            }

            Builder expectException(Class<? extends RuntimeException> v) {
                expectedException = v;
                return this;
            }

            Builder xml(String... v) {
                xml = v;
                return this;
            }

            TestSpec create() {
                return new TestSpec(
                        queryType,
                        Optional.ofNullable(keyName),
                        Optional.ofNullable(expectedValue),
                        validatedExpectedException(), xml);
            }

            private Optional<Class<? extends RuntimeException>> validatedExpectedException() {
                if (expectedValue == null && expectedException == null) {
                    return Optional.of(NoSuchElementException.class);
                } else {
                    return Optional.ofNullable(expectedException);
                }
            }

            private QueryType queryType = QueryType.STRING;
            private String keyName = "foo";
            private Object expectedValue;
            private Class<? extends RuntimeException> expectedException;
            private String[] xml = new String[0];
        }

        void test() {
            final var plistReader = new PListReader(createXml(xml));

            expectedValue.ifPresent(v -> {
                final var actualValue = queryType.queryValue(plistReader, keyName.orElse(null));
                assertEquals(v, actualValue);
            });

            expectedException.ifPresent(v -> {
                assertThrows(v, () -> queryType.queryValue(plistReader, keyName.orElse(null)));
            });
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(queryType);
            if (keyName != null) {
                sb.append("; key=").append(keyName);
            }
            expectedValue.ifPresent(v -> {
                sb.append("; expected=");
                sb.append(v);
            });
            expectedException.ifPresent(v -> {
                sb.append("; throws=");
                sb.append(v);
            });
            sb.append("; xml=");
            sb.append(String.join("", xml));
            return sb.toString();
        }
    }

    @ParameterizedTest
    @EnumSource(mode = Mode.MATCH_NONE, names = {"TO_MAP.*"})
    public void testNoSuchElement(QueryType queryType) {
        testSpec(queryType).create().test();
    }

    @ParameterizedTest
    @EnumSource(mode = Mode.MATCH_NONE, names = {"TO_MAP.*"})
    public void testWrongValueType(QueryType queryType) {
        final var builder = testSpec(queryType).xml(
                "<key>string-key</key>",
                "<string>a</string>",
                "<key>boolean-true-key</key>",
                "<true/>",
                "<key>boolean-false-key</key>",
                "<false/>",
                "<key>array-key</key>",
                "<array><string>b</string></array>",
                "<key>dict-key</key>",
                "<dict><key>nested-dict-key</key><integer>345</integer></dict>");

        List<TestSpec> testSpecs = new ArrayList<>();

        switch (queryType) {
            case STRING -> {
                testSpecs.add(builder.keyName("boolean-true-key").create());
                testSpecs.add(builder.keyName("boolean-false-key").create());
                testSpecs.add(builder.keyName("array-key").create());
                testSpecs.add(builder.keyName("dict-key").create());
            }
            case BOOLEAN -> {
                testSpecs.add(builder.keyName("string-key").create());
                testSpecs.add(builder.keyName("array-key").create());
                testSpecs.add(builder.keyName("dict-key").create());
            }
            case STRING_ARRAY, RAW_ARRAY, RAW_ARRAY_RECURSIVE -> {
                testSpecs.add(builder.keyName("string-key").create());
                testSpecs.add(builder.keyName("boolean-true-key").create());
                testSpecs.add(builder.keyName("boolean-false-key").create());
                testSpecs.add(builder.keyName("dict-key").create());
            }
            case DICT -> {
                testSpecs.add(builder.keyName("string-key").create());
                testSpecs.add(builder.keyName("boolean-true-key").create());
                testSpecs.add(builder.keyName("boolean-false-key").create());
                testSpecs.add(builder.keyName("array-key").create());
            }
            case TO_MAP, TO_MAP_RECURSIVE -> {
                throw new UnsupportedOperationException();
            }
        }

        testSpecs.forEach(TestSpec::test);

        builder.keyName(null).expect(Map.of(
                "string-key", new Raw("a", Raw.Type.STRING),
                "boolean-true-key", new Raw(Boolean.TRUE.toString(), Raw.Type.BOOLEAN),
                "boolean-false-key", new Raw(Boolean.FALSE.toString(), Raw.Type.BOOLEAN),
                "array-key", List.of(new Raw("b", Raw.Type.STRING)),
                "dict-key", Map.of("nested-dict-key", new Raw("345", Raw.Type.INTEGER))
        )).queryType(QueryType.TO_MAP_RECURSIVE).create().test();

    }

    @ParameterizedTest
    @MethodSource
    public void test(TestSpec testSpec) {
        testSpec.test();
    }

    @Test
    public void testByteArrayCtor() throws ParserConfigurationException, SAXException, IOException {
        final var plistReader = new PListReader(xmlToString("<key>foo</key><string>A</string>").getBytes(StandardCharsets.UTF_8));
        final var actualValue = plistReader.queryValue("foo");
        assertEquals("A", actualValue);
    }

    @Test
    public void test_toMap() {

        var builder = testSpec();

        builder.xml(
                "<key>AppName</key>",
                "<string>Hello</string>",
                "<!-- Application version -->",
                "<key>AppVersion</key>",
                "<real>1.0</real>",
                "<key>Release</key>",
                "<true/>",
                "<key>Debug</key>",
                "<false/>",
                "<key>ReleaseDate</key>",
                "<date>2025-09-24T09:23:00Z</date>",
                "<key>UserData</key>",
                "<!-- User data -->",
                "<dict>",
                "  <key>Foo</key>",
                "  <array>",
                "    <string>Str</string>",
                "    <array>",
                "      <string>Another Str</string>",
                "      <true/>",
                "      <false/>",
                "    </array>",
                "  </array>",
                "</dict>",
                "<key>Checksum</key>",
                "<data>7841ff0076cdde93bdca02cfd332748c40620ce4</data>",
                "<key>Plugins</key>",
                "<array>",
                "  <dict>",
                "    <key>PluginName</key>",
                "    <string>Foo</string>",
                "    <key>Priority</key>",
                "    <integer>13</integer>",
                "    <key>History</key>",
                "    <array>",
                "      <string>New File</string>",
                "      <string>Another New File</string>",
                "    </array>",
                "  </dict>",
                "  <dict>",
                "    <key>PluginName</key>",
                "    <string>Bar</string>",
                "    <key>Priority</key>",
                "    <real>23</real>",
                "    <key>History</key>",
                "    <array/>",
                "  </dict>",
                "</array>"
        );

        var expected = Map.of(
                "AppName", new Raw("Hello", Raw.Type.STRING),
                "AppVersion", new Raw("1.0", Raw.Type.REAL),
                "Release", new Raw(Boolean.TRUE.toString(), Raw.Type.BOOLEAN),
                "Debug", new Raw(Boolean.FALSE.toString(), Raw.Type.BOOLEAN),
                "ReleaseDate", new Raw("2025-09-24T09:23:00Z", Raw.Type.DATE),
                "Checksum", new Raw("7841ff0076cdde93bdca02cfd332748c40620ce4", Raw.Type.DATA),
                "UserData", Map.of(
                        "Foo", List.of(
                                new Raw("Str", Raw.Type.STRING),
                                List.of(
                                        new Raw("Another Str", Raw.Type.STRING),
                                        new Raw(Boolean.TRUE.toString(), Raw.Type.BOOLEAN),
                                        new Raw(Boolean.FALSE.toString(), Raw.Type.BOOLEAN)
                                )
                        )
                ),
                "Plugins", List.of(
                        Map.of(
                                "PluginName", new Raw("Foo", Raw.Type.STRING),
                                "Priority", new Raw("13", Raw.Type.INTEGER),
                                "History", List.of(
                                        new Raw("New File", Raw.Type.STRING),
                                        new Raw("Another New File", Raw.Type.STRING)
                                )
                        ),
                        Map.of(
                                "PluginName", new Raw("Bar", Raw.Type.STRING),
                                "Priority", new Raw("23", Raw.Type.REAL),
                                "History", List.of()
                        )
                )
        );

        builder.expect(expected).queryType(QueryType.TO_MAP_RECURSIVE).create().test();
    }

    private static List<TestSpec> test() {

        List<TestSpec> data = new ArrayList<>();

        Stream.of(
                testSpec().expect("A").xml("<key>foo</key><string>A</string>"),
                testSpec().expect("A").xml("<a><string>B</string></a><key>foo</key><string>A</string>"),
                testSpec().expect("").xml("<key>foo</key> some text <string/>"),
                testSpec().xml("<key>foo</key><String/>"),
                testSpec().xml("<key>foo</key>"),
                testSpec().xml("<key>foo</key><foo/><string>A</string>"),
                testSpec().expect(Boolean.TRUE).xml("<key>foo</key><true/>"),
                testSpec().expect(Boolean.FALSE).xml("<key>foo</key><false/>"),
                testSpec(QueryType.BOOLEAN).xml("<key>foo</key><True/>"),
                testSpec(QueryType.BOOLEAN).xml("<key>foo</key><False/>"),
                testSpec().expect(List.of("foo", "bar")).xml("<key>foo</key><array><string>foo</string><random/><dict/><string>bar</string><true/></array>"),
                testSpec().expect(List.of()).xml("<key>foo</key><array/>"),
                testSpec(QueryType.STRING_ARRAY).xml("<key>foo</key><Array/>"),
                testSpec().expect("A").xml("<key>foo</key><string>A</string><string>B</string>"),
                testSpec().expect("A").xml("<key>foo</key><string>A</string><key>foo</key><string>B</string>"),

                testSpec().expect(Map.of()).xml("<key>foo</key><dict/>"),

                //
                // Test that if there are multiple keys with the same name, all but the first are ignored.
                //
                testSpec().expect("A").xml("<key>foo</key><string>A</string><key>foo</key><string>B</string><key>foo</key><string>C</string>"),
                testSpec().expect("A").xml("<key>foo</key><string>A</string><key>foo</key><String>B</String>"),
                testSpec(QueryType.STRING).xml("<key>foo</key><String>B</String><key>foo</key><string>A</string>"),
                testSpec().expect(Boolean.TRUE).xml("<key>foo</key><true/><key>foo</key><false/>"),
                testSpec().expect(Boolean.TRUE).xml("<key>foo</key><true/><key>foo</key><False/>"),
                testSpec(QueryType.BOOLEAN).xml("<key>foo</key><False/><key>foo</key><true/>"),

                //
                // Test that it doesn't look up keys in nested "dict" or "array" elements.
                //
                testSpec().xml("<key>foo</key><dict><key>foo</key><string>A</string></dict>"),
                testSpec().expect("B").xml("<key>bar</key><dict><key>foo</key><string>A</string></dict><key>foo</key><string>B</string>"),
                testSpec().xml("<key>foo</key><array><dict><key>foo</key><string>A</string></dict></array>"),
                testSpec().expect("B").xml("<key>bar</key><array><dict><key>foo</key><string>A</string></dict></array><key>foo</key><string>B</string>"),

                //
                // Test empty arrays.
                //
                testSpec().expect(List.of()).queryType(QueryType.RAW_ARRAY_RECURSIVE).xml("<key>foo</key><array/>"),
                testSpec().expect(List.of()).queryType(QueryType.RAW_ARRAY).xml("<key>foo</key><array/>")

        ).map(TestSpec.Builder::create).forEach(data::add);

        //
        // Test toMap() method.
        //
        Stream.of(
                testSpec().expect(Map.of()).xml(),
                testSpec().expect(Map.of()).xml("<key>foo</key><key>bar</key>"),
                testSpec().expect(Map.of()).xml("<string>A</string><key>bar</key>"),
                testSpec().expect(Map.of()).xml("<string>A</string>"),
                testSpec().expect(Map.of()).xml("<key>foo</key><a/><string>A</string>"),
                testSpec().expect(Map.of("foo", new Raw("A", Raw.Type.STRING))).xml("<key>foo</key><string>A</string><string>B</string>"),
                testSpec().expect(Map.of("foo", new Raw("A", Raw.Type.STRING))).xml("<key>foo</key><string>A</string> hello <key>foo</key> bye <string>B</string>"),
                testSpec().expect(Map.of("foo", new Raw("A", Raw.Type.STRING), "Foo", new Raw("B", Raw.Type.STRING))).xml("<key>foo</key><string>A</string><key>Foo</key><string>B</string>")
        ).map(builder -> {
            return builder.queryType(QueryType.TO_MAP_RECURSIVE);
        }).map(TestSpec.Builder::create).forEach(data::add);

        var arrayTestSpec = testSpec().expect(List.of(
                new Raw("Hello", Raw.Type.STRING),
                Map.of("foo", new Raw("Bye", Raw.Type.STRING)),
                new Raw("integer", Raw.Type.INTEGER),
                Map.of(),
                new Raw(Boolean.TRUE.toString(), Raw.Type.BOOLEAN)
        )).queryType(QueryType.RAW_ARRAY_RECURSIVE);

        Stream.of(
                "<string>Hello</string><random/><dict><key>foo</key><string>Bye</string></dict><integer>integer</integer><dict/><true/>",
                "<string>Hello</string><dict><data>Bingo</data><key>foo</key><string>Bye</string></dict><integer>integer</integer><dict/><true/>",
                "<a><string>B</string></a><string>Hello</string><random/><dict><key>foo</key><string>Bye</string><string>Byeee</string></dict><integer>integer</integer><dict/><true/>",
                "<string>Hello</string><random/><dict><key>bar</key><key>foo</key><string>Bye</string></dict><integer>integer</integer><dict/><true/>",
                "<string>Hello</string><random/><dict><key>foo</key><string>Bye</string><key>foo</key><string>ByeBye</string></dict><integer>integer</integer><dict/><true/>"
        ).map(xml -> {
            return "<key>foo</key><array>" + xml + "</array>";
        }).map(arrayTestSpec::xml).map(TestSpec.Builder::create).forEach(data::add);

        return data;
    }

    private static TestSpec.Builder testSpec() {
        return new TestSpec.Builder();
    }

    private static TestSpec.Builder testSpec(QueryType queryType) {
        return testSpec().queryType(queryType);
    }

    private static String xmlToString(String ...xml) {
        final List<String> content = new ArrayList<>();
        content.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        content.add("<plist version=\"1.0\">");
        content.add("<dict>");
        content.addAll(List.of(xml));
        content.add("</dict>");
        content.add("</plist>");
        return String.join("", content.toArray(String[]::new));
    }

    private static Node createXml(String ...xml) {
        try {
            return XmlUtils.initDocumentBuilder().parse(new InputSource(new StringReader(xmlToString(xml))));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        } catch (SAXException ex) {
            throw new RuntimeException(ex);
        }
    }
}
