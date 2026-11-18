/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.tool;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import jdk.jfr.Description;
import jdk.jfr.Event;
import jdk.jfr.Recording;
import jdk.jfr.StackTrace;
import jdk.test.lib.json.JSONValue;
import jdk.test.lib.process.OutputAnalyzer;

/**
 * @test
 * @requires vm.flagless
 * @summary Tests printing of tricky characters
 * @requires vm.hasJFR
 *
 * @library /test/lib /test/jdk
 * @modules java.scripting java.xml jdk.jfr
 *
 * @run main/othervm jdk.jfr.tool.TestPrintUnicode
 */
public class TestPrintUnicode {

    private static final String UTF8_REPLACEMENT = StandardCharsets.UTF_8.newDecoder().replacement();
    // Surfer emoji. See https://www.unicode.org/Public/18.0.0/charts/PDF/U1F300.pdf
    private static final String SURFER_EMOJI = new String(Character.toChars(0x1F3C4));

    @StackTrace(false)
    static class MessageEvent extends Event {
        @Description("High and low surrogates that don't come in a pair")
        String unpairedSurrogates;

        @Description("Characters that are not valid according XML 1.0 specification")
        String illegalXML;

        @Description("Unicode character that requires a high and low surrogate")
        String emoji;
    }

    public static void main(String... args) throws Throwable {
        Path file = makeRecording();
        testXML(file);
        testJSON(file);
    }

    private static Path makeRecording() throws IOException {
        Path file = Path.of("recording.jfr");
        try (Recording r = new Recording()) {
            r.start();
            MessageEvent event = new MessageEvent();
            // Unicode code points larger than 0xFFFF (16 bits) are
            // encoded as two values called high and low surrogates (where 10 bits vary).
            // High surrogates start at 0xD800 and are followed
            // by a low surrogate starting at 0xDC00
            // By picking a second value that is not a low surrogate,
            // in this case 'G', the high surrogate becomes unpaired. After the 'G' a
            // lone low surrogate is added.
            event.unpairedSurrogates = "\uD800G\uDC00";
            // Illegal XML character.
            // See https://www.w3.org/TR/xml/#charsets Section 2.2
            event.illegalXML = "\u0008";
            event.emoji = SURFER_EMOJI;
            event.commit();
            r.stop();
            r.dump(file);
        }
        return file.toAbsolutePath();
    }

    private static void testJSON(Path file) throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("print", "--json", file.toString());
        String jsonText = output.getStdout();
        JSONValue json = JSONValue.parse(jsonText);
        JSONValue values = json.get("recording").get("events").elements().getFirst().get("values");

        String expectedSurrogates = UTF8_REPLACEMENT + "G" + UTF8_REPLACEMENT;
        String expectedIllegalXML = "\b";
        assertJSONValue(values, "unpairedSurrogates", expectedSurrogates);
        assertJSONValue(values, "illegalXML", expectedIllegalXML);
        assertJSONValue(values, "emoji", SURFER_EMOJI);
    }

    private static void assertJSONValue(JSONValue jsonValue, String field, String expected) throws Exception {
        String value = jsonValue.get(field).asString();
        if (!value.equals(expected)) {
            throw new Exception("Expected " + expected + " for field " + field + ", got " + value);
        }
    }

    private static void testXML(Path file) throws Throwable {
        OutputAnalyzer output = ExecuteHelper.jfr("print", "--xml", file.toString());
        String xml = output.getStdout();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser sp = factory.newSAXParser();
        XMLReader xr = sp.getXMLReader();
        xr.parse(new InputSource(new StringReader(xml)));

        String expectedSurrogates = """
                <value name="unpairedSurrogates">$RG$R</value>
                """;
        String expectedIllegalXML = """
                <value name="illegalXML">$R</value>
                """;
        String expectedEmoji = """
                <value name="emoji">&#x1f3c4;</value>
                """;
        assertXMLValue(xml, expectedSurrogates, "Expected replacement character for unpaired surrogate");
        assertXMLValue(xml, expectedIllegalXML, "Expected replacement character for illegal XML character");
        assertXMLValue(xml, expectedEmoji, "Expected unicode emoji to be &#x1f3c4;");
    }

    private static void assertXMLValue(String xml, String expected, String message) throws Exception {
        expected = expected.replace("$R", UTF8_REPLACEMENT).stripTrailing();
        if (!xml.contains(expected)) {
            throw new Exception(message);
        }
    }
}
