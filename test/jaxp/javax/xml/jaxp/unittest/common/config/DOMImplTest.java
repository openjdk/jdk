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

import static common.config.ConfigurationTest.SP_CONFIG;
import static common.config.ConfigurationTest.getPath;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test @bug 8303530
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run testng/othervm common.config.DOMImplTest
 * @summary verifies that JAXP configuration file is customizable with a system
 * property "java.xml.config.file".
 */
public class DOMImplTest extends DocumentBuilderFactory {
    /*
     * DataProvider for testing the configuration file and system property.
     *
     * Fields:
     *     configuration file, factory implementation class
     */
    @DataProvider(name = "getImpl")
    public Object[][] getImpl() {

        return new Object[][]{
            {"jaxpImpls.properties", "common.config.DOMImplTest"},
        };
    }

    @Test(dataProvider = "getImpl")
    public void testDOMImpl(String config, String expected) throws Exception {
        if (config != null) {
            System.setProperty(SP_CONFIG, getPath(config));
        }
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        System.clearProperty(SP_CONFIG);
        Assert.assertEquals(dbf.getClass().getName(), expected);
    }

    @Override
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        return null;
    }

    @Override
    public void setAttribute(String name, Object value) throws IllegalArgumentException {
        // do nothing
    }

    @Override
    public Object getAttribute(String name) throws IllegalArgumentException {
        return null;
    }

    @Override
    public void setFeature(String name, boolean value) throws ParserConfigurationException {
        // do nothing
    }

    @Override
    public boolean getFeature(String name) throws ParserConfigurationException {
        return false;
    }
}
