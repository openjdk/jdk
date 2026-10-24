/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderAdapter;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8158246 8316383
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm sax.XMLReaderTest
 * @summary This class contains tests that cover the creation of XMLReader.
 */
public class XMLReaderTest {
    private static final String SAX_PROPNAME = "org.xml.sax.driver";

    /*
     * @bug 8158246
     * Verifies that SAXException is reported when the classname specified can
     * not be found.
     *
     * Except test format, this test is the same as JCK's test Ctor003.
     */
    @Test
    public void testcreateXMLReader() throws SAXException, ParserConfigurationException {
        String className = SAXParserFactory.newInstance().newSAXParser()
                .getXMLReader().getClass().getName();
        System.setProperty(SAX_PROPNAME, className + "nosuch");
        try {
            assertThrows(SAXException.class, XMLReaderAdapter::new);
        } finally {
            System.clearProperty(SAX_PROPNAME);
        }
    }

    /*
     * @bug 8316383
     * Verifies that the XMLReader is initialized properly when it's created
     * with XMLReaderFactory.
     */
    @Test
    public void testCreateXMLReaderWithXMLReaderFactory() throws SAXException, ParserConfigurationException {
        XMLReader reader = XMLReaderFactory.createXMLReader();
        reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    }
}
