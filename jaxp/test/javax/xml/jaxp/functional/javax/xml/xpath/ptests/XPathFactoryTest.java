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

package javax.xml.xpath.ptests;

import static javax.xml.xpath.XPathConstants.DOM_OBJECT_MODEL;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.AssertJUnit.assertNotNull;
import org.testng.annotations.Test;

/**
 * Class containing the test cases for XPathFactory API.
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
     * Test for constructor - XPathFactory.newInstance().
     */
    @Test
    public void testCheckXPathFactory01() {
        assertNotNull(XPathFactory.newInstance());
    }

    /**
     * XPathFactory.newInstance(String uri) throws NPE if uri is null.
     */
    @Test(expectedExceptions = NullPointerException.class)
    private void testCheckXPathFactory02() {
        try {
            XPathFactory.newInstance(null);
        } catch (XPathFactoryConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * XPathFactory.newInstance(String uri) throws XPFCE if uri is just a blank
     * string.
     *
     * @throws XPathFactoryConfigurationException
     */
    @Test(expectedExceptions = XPathFactoryConfigurationException.class)
    public void testCheckXPathFactory03() throws XPathFactoryConfigurationException {
        XPathFactory.newInstance(" ");
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with valid
     * url - "http://java.sun.com/jaxp/xpath/dom".
     */
    @Test
    public void testCheckXPathFactory04() {
        try {
            assertNotNull(XPathFactory.newInstance(VALID_URL));
        } catch (XPathFactoryConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with invalid
     * url - "http://java.sun.com/jaxp/xpath/dom1".
     *
     * @throws XPathFactoryConfigurationException
     */
    @Test(expectedExceptions = XPathFactoryConfigurationException.class)
    public void testCheckXPathFactory05() throws XPathFactoryConfigurationException {
        XPathFactory.newInstance(INVALID_URL);
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
     */
    @Test
    public void testCheckXPathFactory07() {
        try {
            assertNotNull(XPathFactory.newInstance(VALID_URL).newXPath());
        } catch (XPathFactoryConfigurationException ex) {
            failUnexpected(ex);
        }
    }

    /**
     * Test for constructor - XPathFactory.newInstance(String uri) with valid
     * uri - DOM_OBJECT_MODEL.toString().
     */
    @Test
    public void testCheckXPathFactory08() {
        try {
            assertNotNull(XPathFactory.newInstance(DOM_OBJECT_MODEL));
        } catch (XPathFactoryConfigurationException ex) {
            failUnexpected(ex);
        }
    }
}
