/*
 * Copyright (c) 1999, 2015, Oracle and/or its affiliates. All rights reserved.
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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import javax.xml.parsers.SAXParserFactory;
import jaxp.library.JAXPBaseTest;
import org.testng.annotations.Test;

/**
 * Class containing the test cases for SAXParserFactory API.
 */
public class SAXParserFactTest extends JAXPBaseTest {

    private static final String NAMESPACES = "http://xml.org/sax/features/namespaces";
    private static final String NAMESPACE_PREFIXES = "http://xml.org/sax/features/namespace-prefixes";
    private static final String STRING_INTERNING = "http://xml.org/sax/features/string-interning";
    private static final String VALIDATION = "http://xml.org/sax/features/validation";
    private static final String EXTERNAL_G_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_P_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    /**
     * Test if newSAXParser() method returns SAXParser.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testParser01() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.newSAXParser();
    }

    /**
     * Test the default functionality (No validation) of the parser.
     */
    @Test
    public void testValidate01() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertFalse(spf.isValidating());
    }

    /**
     * Test the functionality of setValidating and isvalidating
     * methods.
     */
    @Test
    public void testValidate02() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setValidating(true);
        assertTrue(spf.isValidating());
    }

    /**
     * Parser should not be namespace-aware by default.
     */
    @Test
    public void testNamespace01() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertFalse(spf.isNamespaceAware());
    }

    /**
     * Test the functionality of setNamespaceAware and
     * isNamespaceAware methods.
     */
    @Test
    public void testNamespace02() {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setNamespaceAware(true);
        assertTrue(spf.isNamespaceAware());
    }

    /**
     * Test the functionality of setNamespaceAware and getFeature()
     * methods for namespaces property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature01() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertFalse(spf.getFeature(NAMESPACES));

        spf.setNamespaceAware(true);
        assertTrue(spf.getFeature(NAMESPACES));
    }

    /**
     * Test the functionality of setFeature and getFeature methods
     * for namespaces property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature02() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();

        spf.setFeature(NAMESPACES, true);
        assertTrue(spf.getFeature(NAMESPACES));

        spf.setFeature(NAMESPACES, false);
        assertFalse(spf.getFeature(NAMESPACES));
    }

    /**
     * Test the functionality of setFeature and getFeature methods
     * for namespace-prefixes property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature03() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();

        spf.setFeature(NAMESPACE_PREFIXES, true);
        assertTrue(spf.getFeature(NAMESPACE_PREFIXES));

        spf.setFeature(NAMESPACE_PREFIXES, false);
        assertFalse(spf.getFeature(NAMESPACE_PREFIXES));
    }

    /**
     * Test the functionality of getFeature method for
     * string-interning property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature04() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertTrue(spf.getFeature(STRING_INTERNING));
    }

    /**
     * Test the functionality of getFeature and setValidating
     * methods for validation property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature05() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertFalse(spf.getFeature(VALIDATION));
        spf.setValidating(true);
        assertTrue(spf.getFeature(VALIDATION));
    }

    /**
     * Test the functionality of setFeature and getFeature methods
     * for validation property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature06() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature(VALIDATION, true);
        assertTrue(spf.getFeature(VALIDATION));
        spf.setFeature(VALIDATION, false);
        assertFalse(spf.getFeature(VALIDATION));
    }

    /**
     * Test the functionality of getFeature method for
     * external-general-entities property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature07() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertTrue(spf.getFeature(EXTERNAL_G_ENTITIES));
    }

    /**
     * Test the functionality of setFeature and getFeature methods
     * for external-general-entities property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature08() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature(EXTERNAL_G_ENTITIES, false);
        assertFalse(spf.getFeature(EXTERNAL_G_ENTITIES));
    }

    /**
     * Test the functionality of getFeature method for
     * external-parameter-entities property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature09() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        assertTrue(spf.getFeature(EXTERNAL_P_ENTITIES));
    }

    /**
     * Test the functionality of setFeature method for
     * external-parameter-entitie property.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testFeature10() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        spf.setFeature(EXTERNAL_P_ENTITIES, false);
        assertFalse(spf.getFeature(EXTERNAL_P_ENTITIES));
    }
}
