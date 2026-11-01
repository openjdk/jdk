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
package javax.xml.transform.ptests;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;

import static javax.xml.transform.ptests.TransformerTestConst.XML_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * URIResolver should be invoked when transform happens.
 */
/*
 * @test
 * @library /javax/xml/jaxp/libs
 * @run junit/othervm javax.xml.transform.ptests.URIResolverTest
 */
public class URIResolverTest {
    /**
     * System ID constant.
     */
    private final static String SYSTEM_ID = "file:///" + XML_DIR;

    /**
     * XML file include link file.
     */
    private final static String XSL_INCLUDE_FILE = XML_DIR + "citiesinclude.xsl";

    /**
     * XML file import link file.
     */
    private final static String XSL_IMPORT_FILE = XML_DIR + "citiesimport.xsl";

    /**
     * TEMP XML file.
     */
    private final static String XSL_TEMP_FILE = "temp/cities.xsl";

    record TestResolver(String expectedHref, String expectedBase) implements URIResolver {
        /**
         * Called by the processor when it encounters an xsl:include, xsl:import,
         * or document() function.
         */
        @Override
        public Source resolve(String href, String base) {
            assertEquals(expectedHref, href);
            assertEquals(expectedBase, base);
            // Return null if the href cannot be resolved.
            return null;
        }
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using StreamSource. style-sheet file has xsl:include in it.
     */
    @Test
    public void resolver01() throws Exception {
        try (FileInputStream fis = new FileInputStream(XSL_INCLUDE_FILE)) {
            TransformerFactory tfactory = TransformerFactory.newInstance();
            TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
            tfactory.setURIResolver(resolver);

            StreamSource streamSource = new StreamSource(fis);
            streamSource.setSystemId(SYSTEM_ID);
            assertNotNull(tfactory.newTransformer(streamSource));
        }
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using DOMSource. style-sheet file has xsl:include in it.
     */
    @Test
    public void resolver02() throws Exception {
        TransformerFactory tfactory = TransformerFactory.newInstance();
        TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
        tfactory.setURIResolver(resolver);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(XSL_INCLUDE_FILE);
        DOMSource domSource = new DOMSource(document, SYSTEM_ID);

        assertNotNull(tfactory.newTransformer(domSource));
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using SAXSource. style-sheet file has xsl:include in it.
     */
    @Test
    public void resolver03() throws Exception {
        try (FileInputStream fis = new FileInputStream(XSL_INCLUDE_FILE)) {
            TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
            TransformerFactory tfactory = TransformerFactory.newInstance();
            tfactory.setURIResolver(resolver);
            InputSource is = new InputSource(fis);
            is.setSystemId(SYSTEM_ID);
            SAXSource saxSource = new SAXSource(is);
            assertNotNull(tfactory.newTransformer(saxSource));
        }
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using StreamSource. style-sheet file has xsl:import in it.
     */
    @Test
    public void resolver04() throws Exception {
        try (FileInputStream fis = new FileInputStream(XSL_IMPORT_FILE)) {
            TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
            TransformerFactory tfactory = TransformerFactory.newInstance();
            tfactory.setURIResolver(resolver);
            StreamSource streamSource = new StreamSource(fis);
            streamSource.setSystemId(SYSTEM_ID);
            assertNotNull(tfactory.newTransformer(streamSource));
        }
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using DOMSource. style-sheet file has xsl:import in it.
     */
    @Test
    public void resolver05() throws Exception {
        TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
        TransformerFactory tfactory = TransformerFactory.newInstance();
        tfactory.setURIResolver(resolver);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(new File(XSL_IMPORT_FILE));
        DOMSource domSource = new DOMSource(document, SYSTEM_ID);
        assertNotNull(tfactory.newTransformer(domSource));
    }

    /**
     * This is to test the URIResolver.resolve() method when a transformer is
     * created using SAXSource. style-sheet file has xsl:import in it.
     */
    @Test
    public void resolver06() throws Exception {
        try (FileInputStream fis = new FileInputStream(XSL_IMPORT_FILE)) {
            TestResolver resolver = new TestResolver(XSL_TEMP_FILE, SYSTEM_ID);
            TransformerFactory tfactory = TransformerFactory.newInstance();
            tfactory.setURIResolver(resolver);
            InputSource is = new InputSource(fis);
            is.setSystemId(SYSTEM_ID);
            SAXSource saxSource = new SAXSource(is);
            assertNotNull(tfactory.newTransformer(saxSource));
        }
    }

    /**
     * This is to test the URIResolver.resolve() method when there is an error
     * in the file.
     */
    @Test
    public void docResolver01() throws Exception {
        try (FileInputStream fis = new FileInputStream(XML_DIR + "doctest.xsl")) {
            TestResolver resolver = new TestResolver("temp/colors.xml", SYSTEM_ID);
            StreamSource streamSource = new StreamSource(fis);
            streamSource.setSystemId(SYSTEM_ID);

            Transformer transformer = TransformerFactory.newInstance().newTransformer(streamSource);
            transformer.setURIResolver(resolver);

            File f = new File(XML_DIR + "myFake.xml");
            Document document = DocumentBuilderFactory.newInstance().
                    newDocumentBuilder().parse(f);

            // Use a Transformer for output
            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(System.err);
            // No exception is expected because resolver resolve wrong URI.
            transformer.transform(source, result);
        }
    }
}
