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
import javax.xml.parsers.ParserConfigurationException;
import jdk.jpackage.internal.util.PListReader.Raw;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;


public class PListReaderTest {

    enum QueryType {
        STRING(PListReader::queryValue),
        BOOLEAN(PListReader::queryBoolValue),
        STRING_ARRAY(PListReader::queryStringArrayValue);

        QueryType(BiFunction<PListReader, String, ?> queryMethod) {
            this.queryMethod = Objects.requireNonNull(queryMethod);
        }

        @SuppressWarnings("unchecked")
        <T> T queryValue(PListReader pListReader, String keyName) {
            return (T)queryMethod.apply(pListReader, keyName);
        }

        private final BiFunction<PListReader, String, ?> queryMethod;
    }

    public record QueryValueTestSpec(QueryType queryType, String keyName, Optional<Object> expectedValue,
            Optional<Class<? extends RuntimeException>> expectedException, String... xml) {

        public QueryValueTestSpec {
            Objects.requireNonNull(queryType);
            Objects.requireNonNull(keyName);
            Objects.requireNonNull(expectedValue);
            Objects.requireNonNull(expectedException);
            Objects.requireNonNull(xml);
            if (expectedValue.isEmpty() == expectedException.isEmpty()) {
                throw new IllegalArgumentException();
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

            Builder expectedValue(Object v) {
                expectedValue = v;
                if (v instanceof String) {
                    queryType(QueryType.STRING);
                } else if (v instanceof Boolean) {
                    queryType(QueryType.BOOLEAN);
                } else if (v instanceof List<?>) {
                    queryType(QueryType.STRING_ARRAY);
                }
                return this;
            }

            Builder expectedException(Class<? extends RuntimeException> v) {
                expectedException = v;
                return this;
            }

            Builder xml(String... v) {
                xml = v;
                return this;
            }

            QueryValueTestSpec create() {
                return new QueryValueTestSpec(queryType, keyName, Optional.ofNullable(expectedValue),
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
                final var actualValue = queryType.queryValue(plistReader, keyName);
                assertEquals(v, actualValue);
            });

            expectedException.ifPresent(v -> {
                assertThrows(v, () -> queryType.queryValue(plistReader, keyName));
            });
        }

        @Override
        public String toString() {
            final var sb = new StringBuilder();
            sb.append(queryType);
            sb.append("; key=").append(keyName);
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
    @EnumSource(QueryType.class)
    public void testNoSuchElement(QueryType queryType) {
        testSpec(queryType).create().test();
    }

    @ParameterizedTest
    @EnumSource(QueryType.class)
    public void testWrongValueType(QueryType queryType) {
        final var builder = testSpec(queryType).xml(
                "<key>string-key</key>",
                "<string>a</string>",
                "<key>boolean-true-key</key>",
                "<true/>",
                "<key>boolean-false-key</key>",
                "<false/>",
                "<key>array-key</key>",
                "<array><string>b</string></array>");

        List<QueryValueTestSpec> testSpecs = new ArrayList<>();

        switch (queryType) {
            case STRING -> {
                testSpecs.add(builder.keyName("boolean-true-key").create());
                testSpecs.add(builder.keyName("boolean-false-key").create());
                testSpecs.add(builder.keyName("array-key").create());
            }
            case BOOLEAN -> {
                testSpecs.add(builder.keyName("string-key").create());
                testSpecs.add(builder.keyName("array-key").create());
            }
            case STRING_ARRAY -> {
                testSpecs.add(builder.keyName("string-key").create());
                testSpecs.add(builder.keyName("boolean-true-key").create());
                testSpecs.add(builder.keyName("boolean-false-key").create());
            }
        }

        testSpecs.forEach(QueryValueTestSpec::test);
    }

    @ParameterizedTest
    @MethodSource
    public void testQueryValue(QueryValueTestSpec testSpec) {
        testSpec.test();
    }

    @Test
    public void testByteArrayCtor() throws ParserConfigurationException, SAXException, IOException {
        final var plistReader = new PListReader(xmlToString("<key>foo</key><string>A</string>").getBytes(StandardCharsets.UTF_8));
        final var actualValue = plistReader.queryValue("foo");
        assertEquals("A", actualValue);
    }

    @Test
    public void test_toMap() throws ParserConfigurationException, SAXException, IOException {
        var xml = xmlToString(
                "<key>AppName</key>",
                "<string>Hello</string>",
                "<key>AppVersion</key>",
                "<real>1.0</real>",
                "<key>Release</key>",
                "<true/>",
                "<key>Debug</key>",
                "<false/>",
                "<key>ReleaseDate</key>",
                "<date>2025-09-24T09:23:00Z</date>",
                "<key>UserData</key>",
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

        var actual = new PListReader(xml.getBytes(StandardCharsets.UTF_8)).toMap(true);

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

        assertEquals(expected, actual);
    }

    private static List<QueryValueTestSpec> testQueryValue() {
        return List.of(
                testSpec().expectedValue("A").xml("<key>foo</key><string>A</string>").create(),
                testSpec().expectedValue("").xml("<key>foo</key><string/>").create(),
                testSpec().xml("<key>foo</key><String/>").create(),
                testSpec().expectedValue(Boolean.TRUE).xml("<key>foo</key><true/>").create(),
                testSpec().expectedValue(Boolean.FALSE).xml("<key>foo</key><false/>").create(),
                testSpec(QueryType.BOOLEAN).xml("<key>foo</key><True/>").create(),
                testSpec(QueryType.BOOLEAN).xml("<key>foo</key><False/>").create(),
                testSpec().expectedValue(List.of("foo", "bar")).xml("<key>foo</key><array><string>foo</string><random/><dict/><string>bar</string><true/></array>").create(),
                testSpec().expectedValue(List.of()).xml("<key>foo</key><array/>").create(),
                testSpec(QueryType.STRING_ARRAY).xml("<key>foo</key><Array/>").create(),
                testSpec().expectedValue("A").xml("<key>foo</key><string>A</string><string>B</string>").create(),
                testSpec().expectedValue("A").xml("<key>foo</key><string>A</string><key>foo</key><string>B</string>").create()
        );
    }

    private static QueryValueTestSpec.Builder testSpec() {
        return new QueryValueTestSpec.Builder();
    }

    private static QueryValueTestSpec.Builder testSpec(QueryType queryType) {
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
