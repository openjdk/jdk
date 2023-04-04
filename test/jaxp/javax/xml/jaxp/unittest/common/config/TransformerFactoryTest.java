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
import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test @bug 8303530
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @modules java.xml/jdk.xml.internal
 * @run testng/othervm common.config.TransformerFactoryTest
 * @summary verifies that JAXP configuration file is customizable with a system
 * property "java.xml.config.file".
 */
public class TransformerFactoryTest extends TransformerFactory {
    @DataProvider(name = "getImpl")
    public Object[][] getImpl() {

        return new Object[][]{
            {null, "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl"},
        };
    }

    @Test(dataProvider = "getImpl")
    public void testFactory(String config, String expected) throws Exception {
        if (config != null) {
            System.setProperty(ConfigurationTest.SP_CONFIG, getPath(config));
        }

        TransformerFactory tf = TransformerFactory.newInstance();
        System.clearProperty(ConfigurationTest.SP_CONFIG);
        Assert.assertEquals(tf.getClass().getName(), expected);
    }

    @Override
    public Transformer newTransformer(Source source) throws TransformerConfigurationException {
        return null;
    }

    @Override
    public Transformer newTransformer() throws TransformerConfigurationException {
        return null;
    }

    @Override
    public Templates newTemplates(Source source) throws TransformerConfigurationException {
        return null;
    }

    @Override
    public Source getAssociatedStylesheet(Source source, String media, String title, String charset) throws TransformerConfigurationException {
        return null;
    }

    @Override
    public void setURIResolver(URIResolver resolver) {
        // do nothing
    }

    @Override
    public URIResolver getURIResolver() {
        return null;
    }

    @Override
    public void setFeature(String name, boolean value) throws TransformerConfigurationException {
        // do nothing
    }

    @Override
    public boolean getFeature(String name) {
        return false;
    }

    @Override
    public void setAttribute(String name, Object value) {
        // do nothing
    }

    @Override
    public Object getAttribute(String name) {
        return null;
    }

    @Override
    public void setErrorListener(ErrorListener listener) {
        // do nothing
    }

    @Override
    public ErrorListener getErrorListener() {
        return null;
    }

}
