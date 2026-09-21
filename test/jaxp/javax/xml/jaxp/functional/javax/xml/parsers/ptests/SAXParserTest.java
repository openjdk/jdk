/*
 * Copyright (c) 1999, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.parsers.ptests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.HandlerBase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import static javax.xml.parsers.ptests.ParserTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Class contains the test cases for SAXParser API
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.parsers.ptests.SAXParserTest
 */
public class SAXParserTest {
    private static final DefaultHandler DEFAULT_HANDLER = new DefaultHandler();

    @SuppressWarnings("deprecation")
    private static final HandlerBase HANDLER_BASE = new HandlerBase();

    /**
     * Provide SAXParser.
     *
     * @return a data provider contains a SAXParser instance.
     * @throws Exception If any errors occur.
     */
    public static Object[][] getParser() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        SAXParser saxparser = spf.newSAXParser();
        return new Object[][] { { saxparser } };
    }

    /**
     * Test case with FileInputStream null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse01(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((InputStream) null, HANDLER_BASE));
    }

    /**
     * Test with by setting URI as null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse02(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((String) null, HANDLER_BASE));
    }

    /**
     * Test with non-existence URI, parsing should fail and throw IOException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse03(SAXParser saxparser) {
        assertThrows(SAXException.class, () -> saxparser.parse("", HANDLER_BASE));
    }

    /**
     * Test with File null, parsing should fail and throw
     * IllegalArgumentException.
     */
    public void testParse04(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((File) null, HANDLER_BASE));
    }

    /**
     * Test with empty string as File, parsing should fail and throw
     * SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse05(SAXParser saxparser) {
        File file = new File("");
        assertThrows(SAXException.class, () -> saxparser.parse(file, HANDLER_BASE));
    }

    /**
     * Test with input source null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse06(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((InputSource) null, HANDLER_BASE));
    }

    /**
     * Test with FileInputStream null, parsing should fail and throw
     * IllegalArgumentException.
     */
    public void testParse07(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((InputStream) null, DEFAULT_HANDLER));
    }

    /**
     * Test with URI null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse08(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((String) null, DEFAULT_HANDLER));
    }

    /**
     * Test with non-existence URI, parsing should fail and throw SAXException
     * or IOException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse09(SAXParser saxparser) {
        assertThrows(IOException.class, () -> saxparser.parse("no-such-file", DEFAULT_HANDLER));
    }

    /**
     * Test with empty string as File, parsing should fail and throw
     * SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse10(SAXParser saxparser) {
        File file = new File("");
        assertThrows(SAXException.class, () -> saxparser.parse(file, DEFAULT_HANDLER));
    }

    /**
     * Test with File null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse11(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((File) null, DEFAULT_HANDLER));
    }

    /**
     * Test with input source null, parsing should fail and throw
     * IllegalArgumentException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse12(SAXParser saxparser) {
        assertThrows(IllegalArgumentException.class, () -> saxparser.parse((InputSource) null, DEFAULT_HANDLER));
    }

    /**
     * Test with an error in XML file, parsing should fail and throw
     * SAXException.
     */
    public void testParse13(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(new File(
                XML_DIR, "invalid.xml"))) {
            assertThrows(SAXException.class, () -> saxparser.parse(instream, HANDLER_BASE));
        }
    }

    /**
     * Test with a valid in XML file, parser should parse the XML document.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse14(SAXParser saxparser) throws Exception {
        saxparser.parse(new File(XML_DIR, "parsertest.xml"), HANDLER_BASE);
    }

    /**
     * Test with valid input stream, parser should parse the XML document
     * successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse15(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(new File(XML_DIR, "correct.xml"))) {
            saxparser.parse(instream, HANDLER_BASE);
        }
    }

    /**
     * Test with valid input source, parser should parse the XML document
     * successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse16(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "parsertest.xml"))) {
            saxparser.parse(instream, HANDLER_BASE,
                    new File(XML_DIR).toURI().toASCIIString());
        }
    }

    /**
     * Test with proper URI, parser should parse successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse17(SAXParser saxparser) throws Exception {
        File file = new File(XML_DIR, "correct.xml");
        saxparser.parse(file.toURI().toASCIIString(), HANDLER_BASE);
    }

    /**
     * Test with XML file that has errors parsing should fail and throw
     * SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse18(SAXParser saxparser) {
        File file = new File(XML_DIR, "valid.xml");
        assertThrows(SAXException.class, () -> saxparser.parse(file, HANDLER_BASE));
    }

    /**
     * Test with XML file that has no errors Parser should successfully
     * parse the XML document.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse19(SAXParser saxparser) throws Exception {
        saxparser.parse(new File(XML_DIR, "correct.xml"), HANDLER_BASE);
    }

    /**
     * Test with input source attached an invalid XML, parsing should fail
     * and throw SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse20(SAXParser saxparser) throws Exception {
        try(FileInputStream instream = new FileInputStream(new File(XML_DIR,
                "invalid.xml"))) {
            InputSource is = new InputSource(instream);
            assertThrows(SAXException.class, () -> saxparser.parse(is, HANDLER_BASE));
        }
    }

    /**
     * Test with input source attached an valid XML, parser should
     * successfully parse the XML document.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse21(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(new File(XML_DIR,
                "correct.xml"))) {
            saxparser.parse(new InputSource(instream), HANDLER_BASE);
        }
    }

    /**
     * Test with an error in xml file, parsing should fail and throw
     * SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse22(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "invalid.xml"))) {
            assertThrows(SAXException.class, () -> saxparser.parse(instream, DEFAULT_HANDLER));
        }
    }

    /**
     * Test with valid input stream, parser should parse the XML document
     * successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse23(SAXParser saxparser) throws Exception {
        saxparser.parse(new File(XML_DIR, "parsertest.xml"), DEFAULT_HANDLER);
    }

    /**
     * Test with valid input stream, parser should parse the XML document
     * successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse24(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(new File(XML_DIR,
                "correct.xml"))) {
            saxparser.parse(instream, DEFAULT_HANDLER);
        }
    }

    /**
     * Test with valid input source, parser should parse the XML document
     * successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse25(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "parsertest.xml"))) {
            saxparser.parse(instream, DEFAULT_HANDLER,
                    new File(XML_DIR).toURI().toASCIIString());
        }
    }

    /**
     * Test with proper URI, parser should parse successfully.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse26(SAXParser saxparser) throws Exception {
        File file = new File(XML_DIR, "correct.xml");
        saxparser.parse(file.toURI().toASCIIString(), DEFAULT_HANDLER);
    }

    /**
     * Test with XML file that has errors, parsing should fail and throw
     * SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse27(SAXParser saxparser) {
        File file = new File(XML_DIR, "valid.xml");
        assertThrows(SAXException.class, () -> saxparser.parse(file, DEFAULT_HANDLER));
    }

    /**
     * Test with XML file that has no errors, parser should successfully
     * parse the XML document.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse28(SAXParser saxparser) throws Exception {
        saxparser.parse(new File(XML_DIR, "correct.xml"), DEFAULT_HANDLER);
    }

    /**
     * Test with an invalid XML file, parser should throw SAXException.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse29(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "invalid.xml"))) {
            InputSource is = new InputSource(instream);
            assertThrows(SAXException.class, () -> saxparser.parse(is, DEFAULT_HANDLER));
        }
    }

    /**
     * Test case to parse an XML file that not use namespaces.
     */
    @ParameterizedTest
    @MethodSource("getParser")
    public void testParse30(SAXParser saxparser) throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "correct.xml"))) {
            saxparser.parse(new InputSource(instream), DEFAULT_HANDLER);
        }
    }

    /**
     * Test case to parse an XML file that uses namespaces.
     */
    @Test
    public void testParse31() throws Exception {
        try (FileInputStream instream = new FileInputStream(
                new File(XML_DIR, "ns4.xml"))) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.newSAXParser().parse(instream, HANDLER_BASE);
        }
    }
}
