/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package catalog;

import java.io.IOException;
import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogFeatures.Feature;
import javax.xml.catalog.CatalogManager;
import javax.xml.catalog.CatalogResolver;
import javax.xml.catalog.CatalogUriResolver;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DefaultHandler2;

/*
 * @bug 8081248, 8144966, 8146606, 8146237
 * @summary Tests basic Catalog functions.
 */

public class CatalogTest {
    /**
     * @bug 8146237
     * PREFER from Features API taking precedence over catalog file
     */
    @Test
    public void testJDK8146237() {
        String catalogFile = getClass().getResource("JDK8146237_catalog.xml").getFile();

        try {
            CatalogFeatures features = CatalogFeatures.builder().with(CatalogFeatures.Feature.PREFER, "system").build();
            Catalog catalog = CatalogManager.catalog(features, catalogFile);
            CatalogResolver catalogResolver = CatalogManager.catalogResolver(catalog);
            String actualSystemId = catalogResolver.resolveEntity("-//FOO//DTD XML Dummy V0.0//EN", "http://www.oracle.com/alt1sys.dtd").getSystemId();
            Assert.assertTrue(actualSystemId.contains("dummy.dtd"), "Resulting id should contain dummy.dtd, indicating a match by publicId");

        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    /*
       @bug 8146606
       Verifies that the resulting systemId does not contain duplicate slashes
    */
    @Test
    public void testRewriteSystem() {
        String catalog = getClass().getResource("rewriteCatalog.xml").getFile();

        try {
            CatalogResolver resolver = CatalogManager.catalogResolver(CatalogFeatures.defaults(), catalog);
            String actualSystemId = resolver.resolveEntity(null, "http://remote.com/dtd/book.dtd").getSystemId();
            Assert.assertTrue(!actualSystemId.contains("//"), "result contains duplicate slashes");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }

    }

    /*
       @bug 8146606
       Verifies that the resulting systemId does not contain duplicate slashes
    */
    @Test
    public void testRewriteUri() {
        String catalog = getClass().getResource("rewriteCatalog.xml").getFile();

        try {

            CatalogUriResolver resolver = CatalogManager.catalogUriResolver(CatalogFeatures.defaults(), catalog);
            String actualSystemId = resolver.resolve("http://remote.com/import/import.xsl", null).getSystemId();
            Assert.assertTrue(!actualSystemId.contains("//"), "result contains duplicate slashes");
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    /*
       @bug 8144966
       Verifies that passing null as CatalogFeatures will result in a NPE.
    */
    @Test(expectedExceptions = NullPointerException.class)
    public void testFeatureNull() {
        CatalogResolver resolver = CatalogManager.catalogResolver(null, "");

    }

    /*
       @bug 8144966
       Verifies that passing null as the path will result in a NPE.
    */
    @Test(expectedExceptions = NullPointerException.class)
    public void testPathNull() {
        String path = null;
        CatalogResolver resolver = CatalogManager.catalogResolver(CatalogFeatures.defaults(), path);
    }

    /*
       Tests basic catalog feature by using a CatalogResolver instance to
    resolve a DTD reference to a locally specified DTD file. If the resolution
    is successful, the Handler shall return the value of the entity reference
    that matches the expected value.
     */
    @Test(dataProvider = "catalog")
    public void testCatalogResolver(String test, String expected, String catalogFile, String xml, SAXParser saxParser) {
        String catalog = null;
        if (catalogFile != null) {
            catalog = getClass().getResource(catalogFile).getFile();
        }
        String url = getClass().getResource(xml).getFile();
        try {
            CatalogResolver cr = CatalogManager.catalogResolver(CatalogFeatures.defaults(), catalog);
            XMLReader reader = saxParser.getXMLReader();
            reader.setEntityResolver(cr);
            MyHandler handler = new MyHandler(saxParser);
            reader.setContentHandler(handler);
            reader.parse(url);
            System.out.println(test + ": expected [" + expected + "] <> actual [" + handler.getResult() + "]");
            Assert.assertEquals(handler.getResult(), expected);
        } catch (SAXException | IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    /*
       Verifies that when there's no match, in this case only an invalid
    catalog is provided, the resolver will throw an exception by default.
    */
    @Test
    public void testInvalidCatalog() {
        String catalog = getClass().getResource("catalog_invalid.xml").getFile();

        String test = "testInvalidCatalog";
        try {
            CatalogResolver resolver = CatalogManager.catalogResolver(CatalogFeatures.defaults(), catalog);
            String actualSystemId = resolver.resolveEntity(null, "http://remote/xml/dtd/sys/alice/docAlice.dtd").getSystemId();
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("No match found for publicId")) {
                    Assert.assertEquals(msg, "No match found for publicId 'null' and systemId 'http://remote/xml/dtd/sys/alice/docAlice.dtd'.");
                    System.out.println(test + ": expected [No match found for publicId 'null' and systemId 'http://remote/xml/dtd/sys/alice/docAlice.dtd'.]");
                    System.out.println("actual [" + msg + "]");
                }
            }
        }
    }

    /*
       Verifies that if resolve is "ignore", an empty InputSource will be returned
    when there's no match. The systemId is then null.
    */
    @Test
    public void testIgnoreInvalidCatalog() {
        String catalog = getClass().getResource("catalog_invalid.xml").getFile();
        CatalogFeatures f = CatalogFeatures.builder()
                .with(Feature.FILES, catalog)
                .with(Feature.PREFER, "public")
                .with(Feature.DEFER, "true")
                .with(Feature.RESOLVE, "ignore")
                .build();

        String test = "testInvalidCatalog";
        try {
            CatalogResolver resolver = CatalogManager.catalogResolver(f, "");
            String actualSystemId = resolver.resolveEntity(null, "http://remote/xml/dtd/sys/alice/docAlice.dtd").getSystemId();
            System.out.println("testIgnoreInvalidCatalog: expected [null]");
            System.out.println("testIgnoreInvalidCatalog: expected [null]");
            System.out.println("actual [" + actualSystemId + "]");
            Assert.assertEquals(actualSystemId, null);
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }


    /*
       DataProvider: provides test name, expected string, the catalog, and XML
       document.
     */
    @DataProvider(name = "catalog")
    Object[][] getCatalog() {
        return new Object[][]{
            {"testSystem", "Test system entry", "catalog.xml", "system.xml", getParser()},
            {"testRewriteSystem", "Test rewritesystem entry", "catalog.xml", "rewritesystem.xml", getParser()},
            {"testRewriteSystem1", "Test rewritesystem entry", "catalog.xml", "rewritesystem1.xml", getParser()},
            {"testSystemSuffix", "Test systemsuffix entry", "catalog.xml", "systemsuffix.xml", getParser()},
            {"testDelegateSystem", "Test delegatesystem entry", "catalog.xml", "delegatesystem.xml", getParser()},
            {"testPublic", "Test public entry", "catalog.xml", "public.xml", getParser()},
            {"testDelegatePublic", "Test delegatepublic entry", "catalog.xml", "delegatepublic.xml", getParser()},
        };
    }

    SAXParser getParser() {
        SAXParser saxParser = null;
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            saxParser = factory.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
        }

        return saxParser;
    }


    /**
     * SAX handler
     */
    public class MyHandler extends DefaultHandler2 implements ErrorHandler {

        StringBuilder textContent = new StringBuilder();
        SAXParser saxParser;

        MyHandler(SAXParser saxParser) {
            textContent.setLength(0);
            this.saxParser = saxParser;
        }

        String getResult() {
            return textContent.toString();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            textContent.delete(0, textContent.length());
            try {
                System.out.println("Element: " + uri + ":" + localName + " " + qName);
            } catch (Exception e) {
                throw new SAXException(e);
            }

        }

        @Override
        public void characters(char ch[], int start, int length) throws SAXException {
            textContent.append(ch, start, length);
        }
    }
}
