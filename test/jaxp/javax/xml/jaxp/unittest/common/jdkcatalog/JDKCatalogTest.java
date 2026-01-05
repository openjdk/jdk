/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package common.jdkcatalog;

import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.catalog.Catalog;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.catalog.CatalogManager;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import jaxp.library.JAXPTestUtilities;
import org.testng.Assert;
import org.testng.Assert.ThrowingRunnable;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/*
 * @test
 * @bug 8344800 8345353 8351969
 * @library /javax/xml/jaxp/libs
 * @run testng/othervm common.jdkcatalog.JDKCatalogTest
 * @summary Verifies the W3C DTDs and XSDs in the JDK built-in catalog.
 */
public class JDKCatalogTest {
    private static final String JDKCATALOG_RESOLVE = "jdk.xml.jdkcatalog.resolve";
    private static final String PUBLIC_ID = "{{publicId}}";
    private static final String SYSTEM_ID = "{{systemId}}";
    private static final String XSD_LOCATION = "{{SCHEMA_LOCATION}}";
    private static final String TARGET_NAMESPACE = "{{targetNamespace}}";
    private static final String ROOT_ELEMENT = "{{rootElement}}";
    private static final String JDKCATALOG_URL = "jrt:/java.xml/jdk/xml/internal/jdkcatalog/JDKCatalog.xml";

    private Catalog catalog = CatalogManager.catalog(CatalogFeatures.defaults(), URI.create(JDKCATALOG_URL));

    /*
     * DataProvider: DTDs in the JDK built-in Catalog
     * Data provided: public and system Ids, see test testDTDsInJDKCatalog
     */
    @DataProvider(name = "DTDsInJDKCatalog")
    public Object[][] getDTDsInJDKCatalog() {
        return new Object[][]{
            // Schema 1.0
            {"-//W3C//DTD XMLSCHEMA 200102//EN", "http://www.w3.org/2001/XMLSchema.dtd"},
            {"datatypes", "http://www.w3.org/2001/datatypes.dtd"},
            // XHTML 1.0
            {"-//W3C//DTD XHTML 1.0 Frameset//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd"},
            {"-//W3C//DTD XHTML 1.0 Strict//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd"},
            {"-//W3C//DTD XHTML 1.0 Transitional//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd"},
            // XHTML 1.1
            {"-//W3C//DTD XHTML 1.1//EN", "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd"},
            // DTD for W3C specifications
            {"-//W3C//DTD Specification V2.10//EN", "http://www.w3.org/2002/xmlspec/dtd/2.10/xmlspec.dtd"},
        };
    }

    /*
     * DataProvider: for verifying DTDs in the JDKCatalog
     * Data provided: see test testExternalDTD
     */
    @DataProvider(name = "externalDTD")
    public Object[][] getDTD() throws Exception {
        return new Object[][]{
            // verifies the test method correctly throws an exception if the specified
            // DTD can not be resolved
            {"-//ORG//DTD FOO 200102//EN", "http://foo.org/2001/bar.dtd", SAXException.class},
            // this test also verifies datatypes.dtd as it's referenced in XMLSchema.dtd
            {"-//W3C//DTD XMLSCHEMA 200102//EN", "http://www.w3.org/2001/XMLSchema.dtd", null},
            {"-//W3C//DTD XHTML 1.0 Frameset//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-frameset.dtd", null},
            {"-//W3C//DTD XHTML 1.0 Strict//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd", null},
            {"-//W3C//DTD XHTML 1.0 Transitional//EN", "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd", null},
            {"-//W3C//DTD XHTML 1.1//EN", "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd", null},
            {"-//W3C//DTD Specification V2.10//EN", "http://www.w3.org/2002/xmlspec/dtd/2.10/xmlspec.dtd", null},
        };
    }

    /*
     * DataProvider: for verifying XSDs in the JDKCatalog
     * Data provided: see test testXSD
     */
    @DataProvider(name = "getXSD")
    public Object[][] getXSD() throws Exception {
        return new Object[][]{
            // verifies the test method correctly throws an exception if the specified
            // XSD can not be resolved
            {"xsdtest.xml", "http://foo.org/2001/bar.xsd", "http://foo.org/2001/bar", "root", null, SAXException.class},
            // application XSD is resolved by a custom catalog, the W3C XSD then by the JDKCatalog
            {"testXML.xml", "http://www.w3.org/2001/xml.xsd", "http://www.w3.org/XML/1998/namespace", "testXMLXSD", "TestCatalog.xml", null},
            // this test also verifies XMLSchema.dtd and xml.xsd as they are referenced
            {"testXMLSchema.xml", "http://www.w3.org/2001/XMLSchema.xsd", "http://www.w3.org/2001/XMLSchema", "xs:schema", null, null},
            {"testDatatypes.xml", "http://www.w3.org/2009/XMLSchema/XMLSchema-datatypes.xsd", "http://www.w3.org/2001/XMLSchema-datatypes", "testDatatypes", "TestCatalog.xml", null},
            {"xhtml-frameset.xml", "https://www.w3.org/2002/08/xhtml/xhtml1-frameset.xsd", "http://www.w3.org/1999/xhtml", "html", null, null},
            {"xhtml.xml", "https://www.w3.org/2002/08/xhtml/xhtml1-strict.xsd", "http://www.w3.org/1999/xhtml", "html", null, null},
            {"xhtml.xml", "https://www.w3.org/2002/08/xhtml/xhtml1-transitional.xsd", "http://www.w3.org/1999/xhtml", "html", null, null},
            {"xhtml.xml", "http://www.w3.org/MarkUp/SCHEMA/xhtml11.xsd", "http://www.w3.org/1999/xhtml", "html", null, null},
        };
    }

    /**
     * Verifies that the JDK built-in Catalog supports both the Public and System
     * identifiers for DTDs.
     * @param publicId the public Id
     * @param systemId the system Id
     */
    @Test(dataProvider = "DTDsInJDKCatalog")
    public void testDTDsInJDKCatalog(String publicId, String systemId) {
        String matchingPubId = catalog.matchPublic(publicId);
        String matchingSysId = catalog.matchSystem(systemId);
        Assert.assertEquals(matchingPubId, matchingSysId);
    }

    /**
     * Verifies that references to the W3C DTDs are resolved by the JDK built-in
     * catalog.
     * @param publicId the PUBLIC identifier
     * @param systemId the SYSTEM identifier
     * @param expectedThrow the expected throw if the specified DTD can not be
     *                      resolved.
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "externalDTD")
    public void testExternalDTD(String publicId, String systemId, Class<Throwable> expectedThrow)
            throws Exception {
        final String xmlString = generateXMLWithDTDRef(publicId, systemId);

        if (expectedThrow == null) {
            assertDoesNotThrow(() -> parseWithResolveStrict(xmlString),
                    "JDKCatalog shall resolve " + systemId + " but exception is thrown.");
        } else {
            Assert.assertThrows(expectedThrow,
                () -> parseWithResolveStrict(xmlString));
        }
    }

    /**
     * Verifies that references to the W3C DTDs are resolved by the JDK built-in
     * catalog.
     * @param xmlTemplate a template used to generate an XML instance
     * @param xsdLocation the XSD to be resolved
     * @param targetNS the target namespace
     * @param rootElement the root element
     * @param catalog the custom catalog to be used to resolve XSDs used by the
     *                test.
     * @param expectedThrow the expected throw if the specified DTD can not be
     *                      resolved.
     * @throws Exception if the test fails
     */
    @Test(dataProvider = "getXSD")
    public void testXSD(String xmlTemplate, String xsdLocation, String targetNS, String rootElement, String catalog,
            Class<Throwable> expectedThrow)
            throws Exception {
        String xmlSrcPath = JAXPTestUtilities.SRC_DIR + "/" + xmlTemplate;
        final String xmlSrcId = getSysId(xmlSrcPath);

        final String customCatalog = getSysId((catalog != null) ? JAXPTestUtilities.SRC_DIR + "/" + catalog : null);

        final String xmlString = generateXMLWithXSDRef(xmlSrcPath, xsdLocation,
                targetNS, rootElement);
        if (expectedThrow == null) {
            assertDoesNotThrow(() -> validateWithResolveStrict(xmlString, xmlSrcId, customCatalog),
                    "JDKCatalog shall resolve " + xsdLocation + " but exception is thrown.");
        } else {
            Assert.assertThrows(expectedThrow,
                () -> validateWithResolveStrict(xmlString, xmlSrcId, customCatalog));
        }
    }

    /**
     * Validate the specified XML document with jdk.xml.jdkCatalog.resolve set to strict.
     * @param xml  the XML document to be validated
     * @param xmlSrcPathId the URI to the XML source (template in this case)
     * @param customCatalog the custom catalog used to resolve local XSDs
     * @throws Exception if validation fails
     */
    public void validateWithResolveStrict(String xml, String xmlSrcPathId, String customCatalog)
            throws Exception {
        SAXSource ss = new SAXSource(new InputSource(new StringReader(xml)));
        ss.setSystemId(xmlSrcPathId);
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setProperty(JDKCATALOG_RESOLVE, "strict");
        if (customCatalog != null) {
            schemaFactory.setProperty(CatalogFeatures.Feature.FILES.getPropertyName(), customCatalog);
            schemaFactory.setProperty(CatalogFeatures.Feature.RESOLVE.getPropertyName(), "continue");
        }
        Validator validator = schemaFactory.newSchema().newValidator();
        validator.validate(ss);
    }

    /**
     * Parses the XML with jdk.xml.jdkCatalog.resolve set to strict.
     * @param xml the XML document to be parsed
     * @throws Exception if external access is denied
     */
    public void parseWithResolveStrict(String xml)
            throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
        saxParserFactory.setNamespaceAware(true);
        XMLReader xmlReader = saxParserFactory.newSAXParser().getXMLReader();
        xmlReader.setProperty(JDKCATALOG_RESOLVE, "strict");
        xmlReader.setContentHandler(new DefaultHandler());
        xmlReader.parse(new InputSource(new StringReader(xml)));
    }

    /**
     * Generates an XML with the specified PUBLIC and SYSTEM identifiers.
     * @param publicId the public identifier
     * @param systemId the system identifier
     * @return an XML
     * @throws Exception if error happens
     */
    private String generateXMLWithDTDRef(String publicId, String systemId)
            throws Exception {
        Path path = Paths.get(JAXPTestUtilities.SRC_DIR + "/dtdtest.xml");
        String xmlString = Files.lines(path).map(line -> {
            line = line.replace(PUBLIC_ID, publicId);
            line = line.replace(SYSTEM_ID, systemId);
            return line;
        }).collect(Collectors.joining(System.lineSeparator()));
        return xmlString;
    }

    /**
     * Generates an XML with the specified XSD location.
     * @param xmlSrcPath the path to the XML source
     * @param xsd the XSD location
     * @return an XML
     * @throws Exception if error happens
     */
    private String generateXMLWithXSDRef(String xmlSrcPath, String xsd,
            String targetNS, String rootElement)
            throws Exception {
        String xmlString = Files.lines(Paths.get(xmlSrcPath)).map(line -> {
            if (line.contains(XSD_LOCATION)) {
                line = line.replace(XSD_LOCATION, xsd);
            }
            if (line.contains(TARGET_NAMESPACE)) {
                line = line.replace(TARGET_NAMESPACE, targetNS);
            }
            if (line.contains(ROOT_ELEMENT)) {
                line = line.replace(ROOT_ELEMENT, rootElement);
            }
            return line;
        }).collect(Collectors.joining(System.lineSeparator()));
        return xmlString;
    }

    /**
     * Returns the System identifier (URI) of the source.
     * @param path the path to the source
     * @return the System identifier
     */
    private String getSysId(String path) {
        if (path == null) return null;
        String xmlSysId = "file://" + path;
        if (JAXPTestUtilities.isWindows) {
            path = path.replace('\\', '/');
            xmlSysId = "file:///" + path;
        }
        return xmlSysId;
    }

    /**
     * Asserts the run does not cause a Throwable.
     * @param runnable the runnable
     * @param message the message if the test fails
     */
    private void assertDoesNotThrow(ThrowingRunnable runnable, String message) {
        try {
            runnable.run();
        } catch (Throwable t) {
            Assert.fail(message + "\n Exception thrown: " + t.getMessage());
        }
    }
}
