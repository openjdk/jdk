/*
 * Copyright (c) 2016, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8158084 8162438 8162442 8163535 8166220 8344800
 * @library /javax/xml/jaxp/libs /javax/xml/jaxp/unittest
 * @run junit/othervm catalog.CatalogSupport2
 * @summary extends CatalogSupport tests, verifies that the use of the Catalog may
 * be disabled through the System property.
 */

/**
 * For all of the JAXP processors that support the Catalog, the use of the Catalog
 * is turned on by default. It can then be turned off through the API and the
 * System property.
 *
 * @author huizhe.wang@oracle.com
 */
@TestInstance(Lifecycle.PER_CLASS)
public class CatalogSupport2 extends CatalogSupportBase {
    static final String TTIMEOUTREAD = "sun.net.client.defaultReadTimeout";
    static final String TIMEOUTCONNECT = "sun.net.client.defaultConnectTimeout";
    static String timeoutRead = System.getProperty(TTIMEOUTREAD);
    static String timeoutConnect = System.getProperty(TIMEOUTCONNECT);

    /*
     * Initializing fields
     */
    @BeforeAll
    public void setUpClass() throws Exception {
        setUp();
        System.setProperty(SP_USE_CATALOG, "false");
        System.setProperty(SP_ACCESS_EXTERNAL_DTD, "file");
        timeoutRead = System.getProperty(TTIMEOUTREAD);
        timeoutConnect = System.getProperty(TIMEOUTCONNECT);
        System.setProperty(TTIMEOUTREAD, "1000");
        System.setProperty(TIMEOUTCONNECT, "1000");
    }

    @AfterAll
    public void tearDownClass() {
        System.clearProperty(SP_USE_CATALOG);
        System.clearProperty(SP_ACCESS_EXTERNAL_DTD);
        System.setProperty(TIMEOUTCONNECT, "-1");
        System.setProperty(TTIMEOUTREAD, "-1");
    }

    /*
       Verifies the Catalog support on SAXParser.
    */
    @ParameterizedTest
    @MethodSource("getDataSAXC")
    public void testSAXC(boolean setUseCatalog, boolean useCatalog, String catalog, String
            xml, MyHandler handler, String expected) throws Exception {
        assertThrows(
                SAXParseException.class,
                () -> testSAX(setUseCatalog, useCatalog, catalog, xml, handler, expected));
    }

    /*
       Verifies the Catalog support on XMLReader.
    */
    @ParameterizedTest
    @MethodSource("getDataSAXC")
    public void testXMLReaderC(boolean setUseCatalog, boolean useCatalog, String catalog,
                               String xml, MyHandler handler, String expected) throws Exception {
        assertThrows(
                SAXParseException.class,
                () -> testXMLReader(setUseCatalog, useCatalog, catalog, xml, handler, expected));
    }

    /*
       Verifies the Catalog support on XInclude.
    */
    @ParameterizedTest
    @MethodSource("getDataXIC")
    public void testXIncludeC(boolean setUseCatalog, boolean useCatalog, String catalog,
                              String xml, MyHandler handler, String expected) throws Exception {
        assertThrows(
                SAXParseException.class,
                () -> testXInclude(setUseCatalog, useCatalog, catalog, xml, handler, expected));
    }

    /*
       Verifies the Catalog support on DOM parser.
    */
    @ParameterizedTest
    @MethodSource("getDataDOMC")
    public void testDOMC(boolean setUseCatalog, boolean useCatalog, String catalog,
                         String xml, MyHandler handler, String expected) throws Exception {
        assertThrows(
                SAXParseException.class,
                () -> testDOM(setUseCatalog, useCatalog, catalog, xml, handler, expected));
    }

    /*
       Verifies the Catalog support on XMLStreamReader.
    */
    @ParameterizedTest
    @MethodSource("getDataStAX")
    public void testStAXC(boolean setUseCatalog, boolean useCatalog, String catalog,
                          String xml, XMLResolver resolver, String expected) throws Exception {
        assertThrows(
                XMLStreamException.class,
                () -> testStAXNegative(setUseCatalog, useCatalog, catalog, xml, resolver, expected));
    }

    /*
       Verifies the Catalog support on resolving DTD, xsd import and include in
    Schema files.
    */
    @ParameterizedTest
    @MethodSource("getDataSchemaC")
    public void testValidationC(boolean setUseCatalog, boolean useCatalog, String catalog,
                                String xsd, LSResourceResolver resolver) {
        assertThrows(
                SAXParseException.class,
                () -> testValidation(setUseCatalog, useCatalog, catalog, xsd, resolver));
    }

    @ParameterizedTest
    @MethodSource("getDataValidator")
    public void testValidatorC(boolean setUseCatalog1, boolean setUseCatalog2, boolean useCatalog,
                               Source source, LSResourceResolver resolver1, LSResourceResolver resolver2,
                               String catalog1, String catalog2) {
        assertThrows(
                SAXException.class,
                () -> testValidator(setUseCatalog1, setUseCatalog2, useCatalog, source, resolver1, resolver2, catalog1, catalog2));
    }

    /*
       Verifies the Catalog support on resolving DTD, xsl import and include in
    XSL files.
    */
    @ParameterizedTest
    @MethodSource("getDataXSLC")
    public void testXSLImportC(boolean setUseCatalog, boolean useCatalog, String catalog,
                               SAXSource xsl, StreamSource xml, URIResolver resolver, String expected) {

        assertThrows(
                TransformerException.class,
                () -> testXSLImport(setUseCatalog, useCatalog, catalog, xsl, xml, resolver, expected));
    }

    /*
       @bug 8158084 8162442
       Verifies the Catalog support on resolving DTD, xsl import and include in
    XSL files.
    */
    @ParameterizedTest
    @MethodSource("getDataXSLC")
    public void testXSLImportWTemplatesC(boolean setUseCatalog, boolean useCatalog, String catalog,
                                         SAXSource xsl, StreamSource xml, URIResolver resolver, String expected) {
        assertThrows(
                TransformerException.class,
                () -> testXSLImportWTemplates(setUseCatalog, useCatalog, catalog, xsl, xml, resolver, expected));
    }

    /*
       DataProvider: for testing the SAX parser
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    public Object[][] getDataSAXC() {
        return new Object[][]{
            {false, true, xml_catalog, xml_system, new MyHandler(elementInSystem), expectedWCatalog}

        };
    }

    /*
       DataProvider: for testing XInclude
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    public Object[][] getDataXIC() {
        return new Object[][]{
            {false, true, xml_catalog, xml_xInclude, new MyHandler(elementInXISimple), contentInUIutf8Catalog},
        };
    }

    /*
       DataProvider: for testing DOM parser
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    public Object[][] getDataDOMC() {
        return new Object[][]{
            {false, true, xml_catalog, xml_system, new MyHandler(elementInSystem), expectedWCatalog}
        };
    }

    /*
       DataProvider: for testing the StAX parser
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    public Object[][] getDataStAX() {
        return new Object[][]{
            {false, true, xml_catalog, xml_system, null, "null"},
        };
    }

    /*
       DataProvider: for testing Schema validation
       Data: set use_catalog, use_catalog, catalog file, xsd file, a LSResourceResolver
     */
    public Object[][] getDataSchemaC() {

        return new Object[][]{
            // for resolving DTD in xsd
            {false, true, xml_catalog, xsd_val_test_dtd, null},
            // for resolving xsd import
            {false, true, xml_catalog, xsd_xmlSchema_import, null},
            // for resolving xsd include
            {false, true, xml_catalog, xsd_include_company, null}
        };
    }


    /*
       DataProvider: for testing Schema Validator
       Data: source, resolver1, resolver2, catalog1, a catalog2
     */
    public Object[][] getDataValidator() {
        DOMSource ds = getDOMSource(xml_val_test, xml_val_test_id, true, true, xml_catalog);

        SAXSource ss = new SAXSource(new InputSource(xml_val_test));
        ss.setSystemId(xml_val_test_id);

        StAXSource stax = getStaxSource(xml_val_test, xml_val_test_id, false, true, xml_catalog);
        StAXSource stax1 = getStaxSource(xml_val_test, xml_val_test_id, false, true, xml_catalog);

        StreamSource source = new StreamSource(new File(xml_val_test));

        return new Object[][]{
            // use catalog
            {false, false, true, ds, null, null, xml_catalog, null},
            {false, false, true, ds, null, null, null, xml_catalog},
            {false, false, true, ss, null, null, xml_catalog, null},
            {false, false, true, ss, null, null, null, xml_catalog},
            {false, false, true, stax, null, null, xml_catalog, null},
            {false, false, true, stax1, null, null, null, xml_catalog},
            {false, false, true, source, null, null, xml_catalog, null},
            {false, false, true, source, null, null, null, xml_catalog},
        };
    }

    /*
       DataProvider: for testing XSL import and include
       Data: set use_catalog, use_catalog, catalog file, xsl file, xml file, a URIResolver, expected
     */
    public Object[][] getDataXSLC() {
        SAXSource xslSourceDTD = new SAXSource(new InputSource(new StringReader(xsl_includeDTD)));
        StreamSource xmlSourceDTD = new StreamSource(new StringReader(xml_xslDTD));

        SAXSource xslDocSource = new SAXSource(new InputSource(new File(xsl_doc).toURI().toASCIIString()));
        StreamSource xmlDocSource = new StreamSource(new File(xml_doc));
        return new Object[][]{
            // for resolving DTD, import and include in xsl
            {false, true, xml_catalog, xslSourceDTD, xmlSourceDTD, null, ""},
            // for resolving reference by the document function
            {false, true, xml_catalog, xslDocSource, xmlDocSource, null, "Resolved by a catalog"},
        };
    }
}
