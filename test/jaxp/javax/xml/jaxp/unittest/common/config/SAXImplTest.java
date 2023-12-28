/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package common.config;

import static common.config.ConfigurationTest.getPath;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * @test @bug 8303530
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run testng/othervm common.config.SAXImplTest
 * @summary verifies that JAXP configuration file is customizable with a system
 * property "java.xml.config.file".
 */
public class SAXImplTest extends SAXParserFactory {
    @DataProvider(name = "getImpl")
    public Object[][] getImpl() {

        return new Object[][]{
            {"jaxpImpls.properties", "common.config.SAXImplTest"},
        };
    }

    @Test(dataProvider = "getImpl")
    public void testSAXImpl(String config, String expected) throws Exception {
        if (config != null) {
            System.setProperty(ConfigurationTest.SP_CONFIG, getPath(config));
        }
        SAXParserFactory spf = SAXParserFactory.newInstance();
        System.clearProperty(ConfigurationTest.SP_CONFIG);
        Assert.assertEquals(spf.getClass().getName(), expected);
    }

    @Override
    public SAXParser newSAXParser() throws ParserConfigurationException, SAXException {
        return null;
    }

    @Override
    public void setFeature(String name, boolean value)
            throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        //
    }

    @Override
    public boolean getFeature(String name)
            throws ParserConfigurationException, SAXNotRecognizedException, SAXNotSupportedException {
        return false;
    }
}
