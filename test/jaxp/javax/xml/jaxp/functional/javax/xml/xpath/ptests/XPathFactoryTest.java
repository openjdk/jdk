/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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

package javax.xml.xpath.ptests;

import jaxp.library.JAXPDataProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

import static javax.xml.xpath.XPathConstants.DOM_OBJECT_MODEL;
import static javax.xml.xpath.XPathFactory.DEFAULT_OBJECT_MODEL_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Class containing the test cases for XPathFactory API.
 */
/*
 * @test
 * @bug 8169778
 * @library /javax/xml/jaxp/libs
 * @build jaxp.library.JAXPDataProvider
 * @run junit/othervm javax.xml.xpath.ptests.XPathFactoryTest
 */
public class XPathFactoryTest {
    /**
     * Valid URL for creating a XPath factory.
     */
    private static final String VALID_URL = "http://java.sun.com/jaxp/xpath/dom";

    /**
     * Invalid URL not able to create a XPath factory.
     */
    private static final String INVALID_URL = "http://java.sun.com/jaxp/xpath/dom1";

    /**
     * XPathFactory builtin system-default implementation class name.
     */
    private static final String DEFAULT_IMPL_CLASS =
        "com.sun.org.apache.xpath.internal.jaxp.XPathFactoryImpl";

    /**
     * XPathFactory implementation class name.
     */
    private static final String XPATH_FACTORY_CLASSNAME = DEFAULT_IMPL_CLASS;


    /**
     * Provide valid XPathFactory instantiation parameters.
     *
     * @return a data provider contains XPathFactory instantiation parameters.
     */
    public static Object[][] getValidateParameters() {
        return new Object[][] {
                { VALID_URL, XPATH_FACTORY_CLASSNAME, null },
                { VALID_URL, XPATH_FACTORY_CLASSNAME, XPathFactoryTest.class.getClassLoader() },
        };
    }

    /**
     * Test if newDefaultInstance() method returns an instance
     * of the expected factory.
     * @throws Exception If any errors occur.
     */
    @Test
    public void testDefaultInstance() throws Exception {
        XPathFactory xpf1 = XPathFactory.newDefaultInstance();
        XPathFactory xpf2 = XPathFactory.newInstance(DEFAULT_OBJECT_MODEL_URI);
        assertNotSame(xpf1, xpf2, "same instance returned:");
        assertSame(xpf1.getClass(), xpf2.getClass(),
                "unexpected class mismatch for newDefaultInstance():");
        assertEquals(DEFAULT_IMPL_CLASS, xpf1.getClass().getName());
        assertTrue(xpf1.isObjectModelSupported(DEFAULT_OBJECT_MODEL_URI),
                   "isObjectModelSupported(DEFAULT_OBJECT_MODEL_URI):");
        assertFalse(xpf1.isObjectModelSupported(INVALID_URL),
                   "isObjectModelSupported(INVALID_URL):");
    }

    /**
     * Test for XPathFactory.newInstance(java.lang.String uri, java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader) factoryClassName
     * points to correct implementation of javax.xml.xpath.XPathFactory , should
     * return newInstance of XPathFactory
     */
    @ParameterizedTest
    @MethodSource("getValidateParameters")
    public void testNewInstance(String uri, String factoryClassName, ClassLoader classLoader) throws XPathFactoryConfigurationException {
        XPathFactory xpf = XPathFactory.newInstance(uri, factoryClassName, classLoader);
        XPath xpath = xpf.newXPath();
        assertNotNull(xpath);
    }

    /**
     * Test for XPathFactory.newInstance(java.lang.String uri, java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader)
     */
    @ParameterizedTest
    @MethodSource("jaxp.library.JAXPDataProvider#newInstanceNeg")
    public void testNewInstanceWithNullFactoryClassName(String factoryClassName, ClassLoader classLoader) {
        assertThrows(
                XPathFactoryConfigurationException.class,
                () -> XPathFactory.newInstance(VALID_URL, factoryClassName, classLoader));
    }

    /**
     * Test for XPathFactory.newInstance(java.lang.String uri, java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader) uri is null , should
     * throw NPE
     */
    @Test
    public void testNewInstanceWithNullUri() {
        assertThrows(
                NullPointerException.class,
                () -> XPathFactory.newInstance(null, XPATH_FACTORY_CLASSNAME, this.getClass().getClassLoader()));
    }

    /**
     * Test for XPathFactory.newInstance(java.lang.String uri, java.lang.String
     * factoryClassName, java.lang.ClassLoader classLoader)
     */
    @Test
    public void testNewInstanceWithEmptyUri() {
        assertThrows(IllegalArgumentException.class, () -> XPathFactory.newInstance("", XPATH_FACTORY_CLASSNAME, this.getClass().getClassLoader()));
    }

    /**
     * Test for constructor - XPathFactory.newInstance().
     */
    @Test
    public void testCheckXPathFactory01() {
        assertNotNull(XPathFactory.newInstance());
    }

    /**
     * XPathFactory.newInstance(String uri) throws NPE if uri is null.
     *
     */
    @Test
    public void testCheckXPathFactory02() {
        assertThrows(NullPointerException.class, () -> XPathFactory.newInstance(null));
    }

    /**
     * XPathFactory.newInstance(String uri) throws XPFCE if uri is just a blank
     * string.
     *
     */
    @Test
    public void testCheckXPathFactory03() {
        assertThrows(XPathFactoryConfigurationException.class, () -> XPathFactory.newInstance(" "));
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with valid
     * url - "http://java.sun.com/jaxp/xpath/dom".
     *
     * @throws XPathFactoryConfigurationException If the specified object model
    *          is unavailable, or if there is a configuration error.
     */
    @Test
    public void testCheckXPathFactory04() throws XPathFactoryConfigurationException {
        assertNotNull(XPathFactory.newInstance(VALID_URL));
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with invalid
     * url - "http://java.sun.com/jaxp/xpath/dom1".
     *
     */
    @Test
    public void testCheckXPathFactory05() {
        assertThrows(XPathFactoryConfigurationException.class, () -> XPathFactory.newInstance(INVALID_URL));
    }

    /**
     * Test for constructor - XPathFactory.newInstance() and creating XPath with
     * newXPath().
     */
    @Test
    public void testCheckXPathFactory06() {
        assertNotNull(XPathFactory.newInstance().newXPath());
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with valid
     * url - "http://java.sun.com/jaxp/xpath/dom" and creating XPath with
     * newXPath().
     *
     * @throws XPathFactoryConfigurationException If the specified object model
    *          is unavailable, or if there is a configuration error.
     */
    @Test
    public void testCheckXPathFactory07() throws XPathFactoryConfigurationException {
        assertNotNull(XPathFactory.newInstance(VALID_URL).newXPath());
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with valid
     * uri - DOM_OBJECT_MODEL.toString().
     *
     * @throws XPathFactoryConfigurationException If the specified object model
    *          is unavailable, or if there is a configuration error.
     */
    @Test
    public void testCheckXPathFactory08() throws XPathFactoryConfigurationException {
        assertNotNull(XPathFactory.newInstance(DOM_OBJECT_MODEL));
    }
}
