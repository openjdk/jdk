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

package javax.xml.parsers.ptests;

import static jaxp.library.JAXPTestUtilities.FILE_SEP;
import static jaxp.library.JAXPTestUtilities.failUnexpected;
import static org.testng.Assert.assertFalse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This checks for the methods of DocumentBuilder
 */
public class DocumentBuilderImpl01 implements EntityResolver {

    /**
     * Provide DocumentBuilder.
     *
     * @throws ParserConfigurationException
     */
    @DataProvider(name = "builder-provider")
    public Object[][] getBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        return new Object[][] { { docBuilder } };
    }

    /**
     * Testcase to test the default functionality of isValidation method. Expect
     * to return false because not setting the validation.
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl01(DocumentBuilder docBuilder) {
        assertFalse(docBuilder.isValidating());

    }

    /**
     * Testcase to test the default functionality of isNamespaceAware method.
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl02(DocumentBuilder docBuilder) {
        assertFalse(docBuilder.isNamespaceAware());
    }

    /**
     * Testcase to test the parse(InputStream).
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl04(DocumentBuilder docBuilder) {
        try {
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderImpl01.xml")));
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the parse(File).
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl05(DocumentBuilder docBuilder) {
        try {
            Document doc = docBuilder.parse(new File(TestUtils.XML_DIR, "DocumentBuilderImpl01.xml"));
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the parse(InputStream,systemId).
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl06(DocumentBuilder docBuilder) {
        try {
            Document doc = docBuilder.parse(new FileInputStream(new File(TestUtils.XML_DIR, "DocumentBuilderImpl02.xml")), new File(TestUtils.XML_DIR).toURI()
                    .toASCIIString() + FILE_SEP);
        } catch (SAXException | IOException e) {
            failUnexpected(e);
        }
    }

    /**
     * Testcase to test the setEntityResolver.
     */
    @Test(dataProvider = "builder-provider")
    public void testCheckDocumentBuilderImpl07(DocumentBuilder docBuilder) {
        docBuilder.setEntityResolver(this);
        resolveEntity("publicId", "http://www.myhost.com/today");
    }

    public InputSource resolveEntity(String publicId, String systemId) {
        if (systemId.equals("http://www.myhost.com/today"))
            return new InputSource(systemId);
        else
            return null;
    }
}
