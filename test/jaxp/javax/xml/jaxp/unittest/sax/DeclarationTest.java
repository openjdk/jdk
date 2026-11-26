/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

package sax;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8230814
 * @run junit sax.DeclarationTest
 * @summary Test SAX Parser's handling of XML Declarations.
 */
public class DeclarationTest {
    static String SRC_DIR = System.getProperty("test.src");
    final static String XML_NO_DECLARATION = "<a>abc</a>";
    final static String XML_NO_STANDALONE = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\" ?><a>abc</a>";
    final static String XML_STANDALONE_N = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\" ?><a>abc</a>";
    final static String XML_STANDALONE_Y = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"yes\" ?><a>abc</a>";

    /**
     * Provides XML strings for testing XML declaration.
     *
     * Fields:
     * XML string, expected version, encoding and standalone strings
     */
    public static Object[][] defaultHandlerData() throws Exception {
        return new Object[][] {
            { XML_NO_DECLARATION, null, null, null},
            { XML_NO_STANDALONE, null, null, null},
            { XML_STANDALONE_Y, null, null, null},
            { XML_STANDALONE_N, null, null, null},
        };
    }

    /**
     * Provides XML strings for testing XML declaration.
     *
     * Fields:
     * XML string, expected version, encoding and standalone strings
     */
    public static Object[][] xmlSAXData() throws Exception {
        return new Object[][] {
            { XML_NO_DECLARATION, null, null, null},
            { XML_NO_STANDALONE, "1.0", "ISO-8859-1", null},
            { XML_STANDALONE_Y, "1.0", "ISO-8859-1", "yes"},
            { XML_STANDALONE_N, "1.0", "ISO-8859-1", "no"},
        };
    }


    /**
     * Provides XML files for testing XML declaration.
     *
     * Fields:
     * Source files, expected version, encoding and standalone strings
     */
    public static Object[][] xmlSAXDataFiles() throws Exception {
        return new Object[][] {
            //the source contains no declaration
            { new File(SRC_DIR + "/../transform/SourceTest.xml"), null, null, null},
            //<?xml version="1.0" encoding="UTF-8"?>
            { new File(SRC_DIR + "/toys.xml"), "1.0", "UTF-8", null},
            //<?xml version="1.0" encoding="UTF-8" standalone="no"?>
            { new File(SRC_DIR + "/../dom/ElementTraversal.xml"), "1.0", "UTF-8", "no"},
            // <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            { new File(SRC_DIR + "/../validation/Bug6449797.xsd"), "1.0", "UTF-8", "yes"},
            //<?xml version="1.0" standalone="no" ?>
            { new File(SRC_DIR + "/../transform/5368141.xml"), "1.0", null, "no"},

            //<?xml version="1.0" encoding="ISO-8859-1" standalone="no"?>
            { new File(SRC_DIR + "/../transform/Bug6206491.xml"), "1.0", "ISO-8859-1", "no"},
            //<?xml version="1.0" encoding="ISO-8859-1"?>
            { new File(SRC_DIR + "/../transform/Bug6206491.xsl"), "1.0", "ISO-8859-1", null},

        };
    }

    /**
     * Verifies that the default handler does nothing.
     * @param xml xml string
     * @param version expected version string
     * @param encoding expected encoding string
     * @param standalone expected standalone string
     * @throws Exception if the test fails
     */
    @ParameterizedTest
    @MethodSource("defaultHandlerData")
    public void testDefault(String xml, String version, String encoding, String standalone)
            throws Exception {
        DefaultImpl h = new DefaultImpl();
        parseAndVerify(xml, h, version, encoding, standalone);
    }

    /**
     * Verifies that the SAX Parser returns the information of XML declaration
     * through the ContentHandler interface.
     * @param xml xml string
     * @param version expected version string
     * @param encoding expected encoding string
     * @param standalone expected standalone string
     * @throws Exception if the test fails
     */
    @ParameterizedTest
    @MethodSource("xmlSAXData")
    public void test(String xml, String version, String encoding, String standalone)
            throws Exception {
        NewMethodImpl h = new NewMethodImpl();
        parseAndVerify(xml, h, version, encoding, standalone);
    }

    /**
     * Verifies that the SAX Parser returns the information of XML declaration
     * through the ContentHandler interface.
     * @param xml xml files
     * @param version expected version string
     * @param encoding expected encoding string
     * @param standalone expected standalone string
     * @throws Exception if the test fails
     */
    @ParameterizedTest
    @MethodSource("xmlSAXDataFiles")
    public void testFiles(File xml, String version, String encoding, String standalone)
            throws Exception {
        SAXParser parser = SAXParserFactory.newDefaultInstance().newSAXParser();
        NewMethodImpl h = new NewMethodImpl();
        parser.parse(xml, h);
        assertEquals(version, h.version);
        assertEquals(encoding, h.encoding);
        assertEquals(standalone, h.standalone);
    }

    /**
     * Verifies the ContentHandler's XML Declaration feature by parsing an XML
     * string content.
     * @param xml xml string
     * @param version expected version string
     * @param encoding expected encoding string
     * @param standalone expected standalone string
     * @throws Exception if the test fails
     */
    private void parseAndVerify(String xml, DefaultImpl h,
            String version, String encoding, String standalone)
            throws Exception {
        XMLReader r = SAXParserFactory.newDefaultInstance().newSAXParser().getXMLReader();
        r.setContentHandler(h);
        r.parse(new InputSource(new StringReader(xml)));
        assertEquals(version, h.version);
        assertEquals(encoding, h.encoding);
        assertEquals(standalone, h.standalone);
    }

    static class DefaultImpl extends DefaultHandler {
        boolean startDocumentInvoked = false;
        String version, encoding, standalone;

        public void startDocument() throws SAXException {
            super.startDocument();
            startDocumentInvoked = true;
        }
    }

    static class NewMethodImpl extends DefaultImpl {

        public void startDocument() throws SAXException {
            super.startDocument();
        }

        @Override
        public void declaration(String version, String encoding, String standalone)
                throws SAXException
        {
            super.declaration(version, encoding, standalone);
            assertTrue(startDocumentInvoked, "declaration follows startDocument");
            this.version = version;
            this.encoding = encoding;
            this.standalone = standalone;
        }
    }
}
