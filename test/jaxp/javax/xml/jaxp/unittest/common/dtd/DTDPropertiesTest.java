/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package common.dtd;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.XMLReader;

/*
 * @test
 * @bug 8322214
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run testng common.dtd.DTDPropertiesTest
 * @summary Verifies the getProperty function on DTD properties works the same
 * as before the property 'jdk.xml.dtd.support' was introduced.
 */
public class DTDPropertiesTest {
    // Xerces Property
    public static final String DISALLOW_DTD = "http://apache.org/xml/features/disallow-doctype-decl";

    /*
     * DataProvider for verifying Xerces' disallow-DTD feature
     * Fields: property name, setting (null indicates not specified), expected
     */
    @DataProvider(name = "XercesProperty")
    public Object[][] getXercesProperty() throws Exception {
        return new Object[][] {
            { DISALLOW_DTD, null, false},
            { DISALLOW_DTD, true, true},
            { DISALLOW_DTD, false, false},
        };
    }

    /*
     * DataProvider for verifying StAX's supportDTD feature
     * Fields: property name, setting (null indicates not specified), expected
     */
    @DataProvider(name = "StAXProperty")
    public Object[][] getStAXProperty() throws Exception {
        return new Object[][] {
            { XMLInputFactory.SUPPORT_DTD, null, true},
            { XMLInputFactory.SUPPORT_DTD, true, true},
            { XMLInputFactory.SUPPORT_DTD, false, false},
        };
    }

    /**
     * Verifies the disallow DTD feature with SAX.
     *
     * @param name the name of the property
     * @param setting the setting of the property, null means not specified
     * @param expected the expected value
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "XercesProperty")
    public void testSAX(String name, Boolean setting, Boolean expected) throws Exception {
        SAXParserFactory spf = SAXParserFactory.newDefaultInstance();
        if (setting != null) {
            spf.setFeature(name, setting);
        }
        Assert.assertEquals((Boolean)spf.getFeature(name), expected);
        System.out.println(spf.getFeature(name));


        SAXParser saxParser = spf.newSAXParser();
        XMLReader reader = saxParser.getXMLReader();
        Assert.assertEquals((Boolean)reader.getFeature(name), expected);
        System.out.println(reader.getFeature(name));
    }

    /**
     * Verifies the disallow DTD feature with DOM.
     *
     * @param name the name of the property
     * @param setting the setting of the property, null means not specified
     * @param expected the expected value
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "XercesProperty")
    public void testDOM(String name, Boolean setting, Boolean expected) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        if (setting != null) {
            dbf.setFeature(name, setting);
        }
        Assert.assertEquals((Boolean)dbf.getFeature(name), expected);
        System.out.println(dbf.getFeature(name));
    }

    /**
     * Verifies the StAX's supportDTD feature.
     *
     * @param name the name of the property
     * @param setting the setting of the property, null means not specified
     * @param expected the expected value
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "StAXProperty")
    public void testStAX(String name, Boolean setting, Boolean expected) throws Exception {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        if (setting != null) {
            xif.setProperty(name, setting);
        }
        Assert.assertEquals((Boolean)xif.getProperty(name), expected);
        System.out.println((Boolean)xif.getProperty(name));
    }
}
