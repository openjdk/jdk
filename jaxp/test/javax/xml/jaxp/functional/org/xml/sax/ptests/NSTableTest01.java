/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.xml.sax.ptests;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;

/**
 * Class containing the test cases for Namespace Table defined at
 * http://www.megginson.com/SAX/Java/namespaces.html
 */
public class NSTableTest01 {
    private static final String NAMESPACES =
                        "http://xml.org/sax/features/namespaces";
    private static final String NAMESPACE_PREFIXES =
                        "http://xml.org/sax/features/namespace-prefixes";

    /**
     * Here namespace processing and namespace-prefixes are enabled.
     * The testcase tests XMLReader for this.
     */
    @Test
    public void xrNSTable01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxParser = spf.newSAXParser();

            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setFeature(NAMESPACE_PREFIXES, true);

            assertTrue(xmlReader.getFeature(NAMESPACES));
            assertTrue(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Here namespace processing is enabled. This will make namespace-prefixes
     * disabled. The testcase tests XMLReader for this.
     */
    @Test
    public void xrNSTable02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            SAXParser saxParser = spf.newSAXParser();

            XMLReader xmlReader = saxParser.getXMLReader();
            assertTrue(xmlReader.getFeature(NAMESPACES));
            assertFalse(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }

    }

    /**
     * Here namespace processing is disabled. This will make namespace-prefixes
     * enabled. The testcase tests XMLReader for this.
     */
    @Test
    public void xrNSTable03() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser saxParser = spf.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            assertFalse(xmlReader.getFeature(NAMESPACES));
            assertTrue(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Here namespace processing is disabled, and namespace-prefixes is
     * disabled. This will make namespace processing on.The testcase tests
     * XMLReader for this.  This behavior only apply to crimson, not
     * xerces
     */
    @Test
    public void xrNSTable04() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser saxParser = spf.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            xmlReader.setFeature(NAMESPACE_PREFIXES, false);

            assertFalse(xmlReader.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Here namespace processing and namespace-prefixes are enabled.
     * The testcase tests SAXParserFactory for this.
     */
    @Test
    public void spNSTable01() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setFeature(NAMESPACE_PREFIXES,true);
            assertTrue(spf.getFeature(NAMESPACES));
            assertTrue(spf.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXNotRecognizedException
                | SAXNotSupportedException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Here namespace processing is enabled. This will make namespace-prefixes
     * disabled. The testcase tests SAXParserFactory for this.
     */
    @Test
    public void spNSTable02() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            assertTrue(spf.getFeature(NAMESPACES));
            assertFalse(spf.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXNotRecognizedException
                | SAXNotSupportedException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Here namespace processing is disabled. This will make namespace-prefixes
     * enabled. The testcase tests SAXParserFactory for this.
     */
    @Test
    public void spNSTable03() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            assertFalse(spf.getFeature(NAMESPACES));
            assertTrue(spf.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXNotRecognizedException
                | SAXNotSupportedException ex) {
            failUnexpected(ex);
        }
    }
    /**
     * Here namespace processing is disabled, and namespace-prefixes is
     * disabled. This will make namespace processing on.The testcase tests
     * SAXParserFactory for this.  This behavior only apply to crimson,
     * not xerces.
     */
    @Test
    public void spNSTable04() {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setFeature(NAMESPACE_PREFIXES, false);

            assertFalse(spf.getFeature(NAMESPACE_PREFIXES));
        } catch (ParserConfigurationException | SAXNotRecognizedException
                | SAXNotSupportedException ex) {
            failUnexpected(ex);
        }
    }
}
