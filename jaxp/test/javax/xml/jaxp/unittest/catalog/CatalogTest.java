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
import javax.xml.catalog.CatalogException;
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
 * @bug 8081248, 8144966, 8146606, 8146237, 8151154, 8150969, 8151162
 * @summary Tests basic Catalog functions.
 */
public class CatalogTest {
    /*
     * @bug 8151162
     * Verifies that the Catalog matches specified publicId or systemId and returns
     * results as expected.
     */
    @Test(dataProvider = "matchWithPrefer")
    public void matchWithPrefer(String prefer, String cfile, String publicId, String systemId, String expected) {
        String catalogFile = getClass().getResource(cfile).getFile();
        Catalog c = CatalogManager.catalog(CatalogFeatures.builder().with(CatalogFeatures.Feature.PREFER, prefer).build(), catalogFile);
        String result;
        if (publicId != null && publicId.length() > 0) {
            result = c.matchPublic(publicId);
        } else {
            result = c.matchSystem(systemId);
        }
        Assert.assertEquals(expected, result);
    }

    /*
     * @bug 8151162
     * Verifies that the CatalogResolver resolves specified publicId or systemId
     * in accordance with the prefer setting.
     * prefer "system": resolves with a system entry.
     *                  Exception: use the public entry when the catalog contains
     *                  only public entry and only publicId is specified.
     * prefer "public": attempts to resolve with a system entry;
     *                  attempts to resolve with a public entry if no matching
     *                  system entry is found.
     */
    @Test(dataProvider = "resolveWithPrefer")
    public void resolveWithPrefer(String prefer, String cfile, String publicId, String systemId, String expected) {
        String catalogFile = getClass().getResource(cfile).getFile();
        CatalogFeatures f = CatalogFeatures.builder().with(CatalogFeatures.Feature.PREFER, prefer).with(CatalogFeatures.Feature.RESOLVE, "ignore").build();
        CatalogResolver catalogResolver = CatalogManager.catalogResolver(f, catalogFile);
        String result = catalogResolver.resolveEntity(publicId, systemId).getSystemId();
        Assert.assertEquals(expected, result);
    }

    /**
     * @bug 8150969
     * Verifies that the defer attribute set in the catalog file takes precedence
     * over other settings, in which case, whether next and delegate Catalogs will
     * be loaded is determined by the defer attribute.
     */
    @Test(dataProvider = "invalidAltCatalogs", expectedExceptions = CatalogException.class)
    public void testDeferAltCatalogs(String file) {
        String catalogFile = getClass().getResource(file).getFile();
        CatalogFeatures features = CatalogFeatures.builder().with(CatalogFeatures.Feature.DEFER, "true").build();
        /*
          Since the defer attribute is set to false in the specified catalog file,
          the parent catalog will try to load the alt catalog, which will fail
          since it points to an invalid catalog.
        */
        Catalog catalog = CatalogManager.catalog(features, catalogFile);
    }

    /**
     * @bug 8151154
     * Verifies that the CatalogFeatures' builder throws IllegalArgumentException
     * on invalid file inputs.
     * @param file the file path
     */
    @Test(dataProvider = "invalidPaths", expectedExceptions = IllegalArgumentException.class)
    public void testFileInput(String file) {
            CatalogFeatures features = CatalogFeatures.builder()
                .with(CatalogFeatures.Feature.FILES, file)
                .build();
    }

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

    static String id = "http://openjdk.java.net/xml/catalog/dtd/system.dtd";
    /*
       DataProvider: used to verify how prefer settings affect the result of the
        Catalog's matching operation.
        Data columns:
        prefer, catalog, publicId, systemId, expected result
     */
    @DataProvider(name = "matchWithPrefer")
    Object[][] getDataForMatch() {
        return new Object[][]{
            {"public", "pubOnly.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"public", "sysOnly.xml", id, "", null},
            {"public", "sysAndPub.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"system", "pubOnly.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"system", "sysOnly.xml", id, "", null},
            {"system", "sysAndPub.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"public", "pubOnly.xml", "", id, null},
            {"public", "sysOnly.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"public", "sysAndPub.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"system", "pubOnly.xml", "", id, null},
            {"system", "sysOnly.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"system", "sysAndPub.xml", "", id, "http://local/base/dtd/system.dtd"},
        };
    }

    /*
       DataProvider: used to verify how prefer settings affect the result of the
        CatalogResolver's resolution operation.
        Data columns:
        prefer, catalog, publicId, systemId, expected result
     */
    @DataProvider(name = "resolveWithPrefer")
    Object[][] getDataForResolve() {
        return new Object[][]{
            {"system", "pubOnly.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"system", "pubOnly.xml", "", id, null},
            {"system", "pubOnly.xml", id, id, null},
            {"public", "pubOnly.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"public", "pubOnly.xml", "", id, null},
            {"public", "pubOnly.xml", id, id, "http://local/base/dtd/public.dtd"},
            {"system", "sysOnly.xml", id, "", null},
            {"system", "sysOnly.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"system", "sysOnly.xml", id, id, "http://local/base/dtd/system.dtd"},
            {"public", "sysOnly.xml", id, "", null},
            {"public", "sysOnly.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"public", "sysOnly.xml", id, id, "http://local/base/dtd/system.dtd"},
            {"system", "sysAndPub.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"system", "sysAndPub.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"system", "sysAndPub.xml", id, id, "http://local/base/dtd/system.dtd"},
            {"public", "sysAndPub.xml", id, "", "http://local/base/dtd/public.dtd"},
            {"public", "sysAndPub.xml", "", id, "http://local/base/dtd/system.dtd"},
            {"public", "sysAndPub.xml", id, id, "http://local/base/dtd/system.dtd"},
        };
    }
    /*
       DataProvider: catalogs that contain invalid next or delegate catalogs.
                     The defer attribute is set to false.
     */
    @DataProvider(name = "invalidAltCatalogs")
    Object[][] getCatalogs() {
        return new Object[][]{
            {"defer_false_2.xml"},
            {"defer_del_false.xml"}
        };
    }

    /*
       DataProvider: for testing the verification of file paths by
                     the CatalogFeatures builder
     */
    @DataProvider(name = "invalidPaths")
    Object[][] getFiles() {
        return new Object[][]{
            {null},
            {""},
            {"file:a/b\\c"},
            {"file:/../../.."},
            {"c:/te:t"},
            {"c:/te?t"},
            {"c/te*t"},
            {"in|valid.txt"},
            {"shema:invalid.txt"},
        };
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
