/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamSource;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/*
 * @bug 8158084 8162438 8162442
 * @summary extends CatalogSupport tests, verifies that the use of the Catalog may
 * be disabled through the API property.
 */

/**
 * For all of the JAXP processors that support the Catalog, the use of the Catalog
 * is turned on by default. It can then be turned off through the API and the
 * System property.
 *
 * @author huizhe.wang@oracle.com
 */
public class CatalogSupport3 extends CatalogSupportBase {
    static final String TTIMEOUTREAD = "sun.net.client.defaultReadTimeout";
    static final String TIMEOUTCONNECT = "sun.net.client.defaultConnectTimeout";
    static String timeoutRead = System.getProperty(TTIMEOUTREAD);
    static String timeoutConnect = System.getProperty(TIMEOUTCONNECT);
    /*
     * Initializing fields
     */
    @BeforeClass
    public void setUpClass() throws Exception {
        setUp();
        timeoutRead = System.getProperty(TTIMEOUTREAD);
        timeoutConnect = System.getProperty(TIMEOUTCONNECT);
        System.setProperty(TTIMEOUTREAD, "1000");
        System.setProperty(TIMEOUTCONNECT, "1000");
    }

    @AfterClass
    public void tearDownClass() throws Exception {
        System.setProperty(TIMEOUTCONNECT, "-1");
        System.setProperty(TTIMEOUTREAD, "-1");
    }

    /*
       Verifies the Catalog support on SAXParser.
    */
    @Test(dataProvider = "data_SAXC", expectedExceptions = FileNotFoundException.class)
    public void testSAXC(boolean setUseCatalog, boolean useCatalog, String catalog,
            String xml, MyHandler handler, String expected) throws Exception {
        testSAX(setUseCatalog, useCatalog, catalog, xml, handler, expected);
    }

    /*
       Verifies the Catalog support on XMLReader.
    */
    @Test(dataProvider = "data_SAXC", expectedExceptions = FileNotFoundException.class)
    public void testXMLReaderC(boolean setUseCatalog, boolean useCatalog, String catalog,
            String xml, MyHandler handler, String expected) throws Exception {
        testXMLReader(setUseCatalog, useCatalog, catalog, xml, handler, expected);
    }

    /*
       Verifies the Catalog support on XInclude.
    */
    @Test(dataProvider = "data_XIC", expectedExceptions = SAXParseException.class)
    public void testXIncludeC(boolean setUseCatalog, boolean useCatalog, String catalog,
            String xml, MyHandler handler, String expected) throws Exception {
        testXInclude(setUseCatalog, useCatalog, catalog, xml, handler, expected);
    }

    /*
       Verifies the Catalog support on DOM parser.
    */
    @Test(dataProvider = "data_DOMC", expectedExceptions = {FileNotFoundException.class, SocketTimeoutException.class})
    public void testDOMC(boolean setUseCatalog, boolean useCatalog, String catalog,
            String xml, MyHandler handler, String expected) throws Exception {
        testDOM(setUseCatalog, useCatalog, catalog, xml, handler, expected);
    }

    /*
       Verifies the Catalog support on resolving DTD, xsd import and include in
    Schema files.
    */
    @Test(dataProvider = "data_SchemaC", expectedExceptions = SAXParseException.class)
    public void testValidationC(boolean setUseCatalog, boolean useCatalog, String catalog,
            String xsd, LSResourceResolver resolver)
            throws Exception {
        testValidation(setUseCatalog, useCatalog, catalog, xsd, resolver) ;
    }

    /*
       @bug 8158084 8162438 these tests also verifies the fix for 8162438
       Verifies the Catalog support on the Schema Validator.
    */
    @Test(dataProvider = "data_ValidatorC", expectedExceptions = {SAXException.class, FileNotFoundException.class})
    public void testValidatorC(boolean setUseCatalog1, boolean setUseCatalog2, boolean useCatalog,
            Source source, LSResourceResolver resolver1, LSResourceResolver resolver2,
            String catalog1, String catalog2)
            throws Exception {
        testValidator(setUseCatalog1, setUseCatalog2, useCatalog, source,
                resolver1, resolver2, catalog1, catalog2);
    }

    /*
       Verifies the Catalog support on resolving DTD, xsl import and include in
    XSL files.
    */
    @Test(dataProvider = "data_XSLC", expectedExceptions = TransformerException.class)
    public void testXSLImportC(boolean setUseCatalog, boolean useCatalog, String catalog, SAXSource xsl, StreamSource xml,
        URIResolver resolver, String expected) throws Exception {

        testXSLImport(setUseCatalog, useCatalog, catalog, xsl, xml, resolver, expected);
    }

    /*
       @bug 8158084 8162442
       Verifies the Catalog support on resolving DTD, xsl import and include in
    XSL files.
    */
    @Test(dataProvider = "data_XSLC", expectedExceptions = TransformerException.class)
    public void testXSLImportWTemplatesC(boolean setUseCatalog, boolean useCatalog, String catalog,
            SAXSource xsl, StreamSource xml,
        URIResolver resolver, String expected) throws Exception {
        testXSLImportWTemplates(setUseCatalog, useCatalog, catalog, xsl, xml, resolver, expected);
    }

    /*
       DataProvider: for testing the SAX parser
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    @DataProvider(name = "data_SAXC")
    Object[][] getDataSAXC() {
        return new Object[][]{
            {true, false, xml_catalog, xml_system, new MyHandler(elementInSystem), expectedWCatalog}

        };
    }

    /*
       DataProvider: for testing XInclude
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    @DataProvider(name = "data_XIC")
    Object[][] getDataXIC() {
        return new Object[][]{
            {true, false, xml_catalog, xml_xInclude, new MyHandler(elementInXISimple), contentInUIutf8Catalog},
        };
    }

    /*
       DataProvider: for testing DOM parser
       Data: set use_catalog, use_catalog, catalog file, xml file, handler, expected result string
     */
    @DataProvider(name = "data_DOMC")
    Object[][] getDataDOMC() {
        return new Object[][]{
            {true, false, xml_catalog, xml_system, new MyHandler(elementInSystem), expectedWCatalog}
        };
    }

    /*
       DataProvider: for testing Schema validation
       Data: set use_catalog, use_catalog, catalog file, xsd file, a LSResourceResolver
     */
    @DataProvider(name = "data_SchemaC")
    Object[][] getDataSchemaC() {

        return new Object[][]{
            // for resolving DTD in xsd
            {true, false, xml_catalog, xsd_xmlSchema, null},
            // for resolving xsd import
            {true, false, xml_catalog, xsd_xmlSchema_import, null},
            // for resolving xsd include
            {true, false, xml_catalog, xsd_include_company, null}
        };
    }


    /*
       DataProvider: for testing Schema Validator
       Data: source, resolver1, resolver2, catalog1, a catalog2
     */
    @DataProvider(name = "data_ValidatorC")
    Object[][] getDataValidator() {
        DOMSource ds = getDOMSource(xml_val_test, xml_val_test_id, false, true, xml_catalog);

        SAXSource ss = new SAXSource(new InputSource(xml_val_test));
        ss.setSystemId(xml_val_test_id);

        StAXSource stax = getStaxSource(xml_val_test, xml_val_test_id);
        StAXSource stax1 = getStaxSource(xml_val_test, xml_val_test_id);

        StreamSource source = new StreamSource(new File(xml_val_test));

        return new Object[][]{
            // use catalog disabled through factory
            {true, false, false, ds, null, null, xml_catalog, null},
            {true, false, false, ds, null, null, null, xml_catalog},
            {true, false, false, ss, null, null, xml_catalog, null},
            {true, false, false, ss, null, null, null, xml_catalog},
            {true, false, false, stax, null, null, xml_catalog, null},
            {true, false, false, stax1, null, null, null, xml_catalog},
            {true, false, false, source, null, null, xml_catalog, null},
            {true, false, false, source, null, null, null, xml_catalog},
            // use catalog disabled through validatory
            {false, true, false, ds, null, null, xml_catalog, null},
            {false, true, false, ds, null, null, null, xml_catalog},
            {false, true, false, ss, null, null, xml_catalog, null},
            {false, true, false, ss, null, null, null, xml_catalog},
            {false, true, false, stax, null, null, xml_catalog, null},
            {false, true, false, stax1, null, null, null, xml_catalog},
            {false, true, false, source, null, null, xml_catalog, null},
            {false, true, false, source, null, null, null, xml_catalog},
        };
    }

    /*
       DataProvider: for testing XSL import and include
       Data: set use_catalog, use_catalog, catalog file, xsl file, xml file, a URIResolver, expected
     */
    @DataProvider(name = "data_XSLC")
    Object[][] getDataXSLC() {
        SAXSource xslSourceDTD = new SAXSource(new InputSource(new StringReader(xsl_includeDTD)));
        StreamSource xmlSourceDTD = new StreamSource(new StringReader(xml_xslDTD));

        SAXSource xslDocSource = new SAXSource(new InputSource(new File(xsl_doc).toURI().toASCIIString()));
        StreamSource xmlDocSource = new StreamSource(new File(xml_doc));
        return new Object[][]{
            // for resolving DTD, import and include in xsl
            {true, false, xml_catalog, xslSourceDTD, xmlSourceDTD, null, ""},
            // for resolving reference by the document function
            {true, false, xml_catalog, xslDocSource, xmlDocSource, null, "Resolved by a catalog"},
        };
    }
}
