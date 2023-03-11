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
import javax.xml.XMLConstants;
import javax.xml.transform.Source;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * @test @bug 8303530
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run testng/othervm common.config.SchemaFactoryTest
 * @summary verifies that JAXP configuration file is customizable with a system
 * property "jdk.xml.config.file".
 */
public class SchemaFactoryTest extends SchemaFactory {
    @DataProvider(name = "getImpl")
    public Object[][] getImpl() {

        return new Object[][]{
            {null, "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory"},
            {"jaxpImpls.properties", "common.config.SchemaFactoryTest"},

        };
    }

    @Test(dataProvider = "getImpl")
    public void testFactory(String config, String expected) throws Exception {
        if (config != null) {
            System.out.println(getPath(config));
            System.setProperty(ConfigurationTest.SP_CONFIG, getPath(config));
        }

        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        System.clearProperty(ConfigurationTest.SP_CONFIG);
        System.out.println(sf.getClass().getName());
        Assert.assertEquals(sf.getClass().getName(), expected);
    }

    @Override
    public boolean isSchemaLanguageSupported(String schemaLanguage) {
        return false;
    }

    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {
        // do nothing
    }

    @Override
    public ErrorHandler getErrorHandler() {
        return null;
    }

    @Override
    public void setResourceResolver(LSResourceResolver resourceResolver) {
        // do nothing
    }

    @Override
    public LSResourceResolver getResourceResolver() {
        return null;
    }

    @Override
    public Schema newSchema(Source[] schemas) throws SAXException {
        return null;
    }

    @Override
    public Schema newSchema() throws SAXException {
        return null;
    }
}
