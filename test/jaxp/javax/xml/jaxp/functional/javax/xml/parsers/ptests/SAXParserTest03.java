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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;

import static javax.xml.parsers.ptests.ParserTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class contains the test cases for SAXParser API
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.parsers.ptests.SAXParserTest03
 */
public class SAXParserTest03 {

    /**
     * Provide SAXParserFactory.
     *
     * @return a dimensional contains.
     */
    public static Object[][] getFactory() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(true);
        return new Object[][] { { spf, MyErrorHandler.newInstance() } };
    }

    /**
     * parsertest.xml holds a valid document. This method tests the validating
     * parser.
     *
     * @param spf a Parser factory.
     * @param handler an error handler for capturing events.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getFactory")
    public void testParseValidate01(SAXParserFactory spf, MyErrorHandler handler)
            throws Exception {
            spf.newSAXParser().parse(new File(XML_DIR, "parsertest.xml"), handler);
            assertFalse(handler.isErrorOccured());
    }

    /**
     * validns.xml holds a valid document with XML namespaces in it. This method
     * tests the Validating parser with namespace processing on.
     *
     * @param spf a Parser factory.
     * @param handler an error handler for capturing events.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getFactory")
    public void testParseValidate02(SAXParserFactory spf, MyErrorHandler handler)
            throws Exception {
            spf.setNamespaceAware(true);
            spf.newSAXParser().parse(new File(XML_DIR, "validns.xml"), handler);
            assertFalse(handler.isErrorOccured());
    }

    /**
     * invalidns.xml holds an invalid document with XML namespaces in it. This
     * method tests the validating parser with namespace processing on. It
     * should throw validation error.
     *
     * @param spf a Parser factory.
     * @param handler an error handler for capturing events.
     * @throws Exception If any errors occur.
     */
    @ParameterizedTest
    @MethodSource("getFactory")
    public void testParseValidate03(SAXParserFactory spf, MyErrorHandler handler)
            throws Exception {
        spf.setNamespaceAware(true);
        SAXParser saxparser = spf.newSAXParser();
        assertThrows(SAXException.class, () -> saxparser.parse(new File(XML_DIR, "invalidns.xml"), handler));
        assertTrue(handler.isErrorOccured());
    }
}
