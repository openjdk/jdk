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
package common.catalog;

import common.util.TestBase;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;
//import org.testng.annotations.DataProvider;

/**
 * Tests the JDK Catalog
 */
public class CatalogTestBase extends TestBase {
    /*
     * DataProvider for testing configuring properties for parsers.
     *
     * Fields:
     *     file, FSP, state of setting, config file, system property, api property,
     *     Custom Catalog, error expected, error code or expected result
     */
    //@DataProvider(name = "configWCatalogForParsers")
    public Object[][] getConfigs(Processor processor) {
        // file with an external DTD that's not in JdkCatalog
        String fileDTDNotInC = "properties1.xml";
        // file with an external DTD that's in the Custom Catalog
        String fileDTDInCC = "test.xml";
        // file with an external DTD that's in JdkCatalog
        String javaDTD = "properties.xml";
        // file with an external DTD thats in the Custom Catalog
        String w3cDTD = "xhtml11.xml";

        // error code when CATALOG=strict; The cause for DOM
        String errCode = "JAXP09040001";

        // error (not from catalog) is expect when CATALOG=continue
        boolean isErrExpected = true;
        String expected1 = UNKNOWN_HOST;

        // expected when reference is resolved by Catalog
        String expected3 = "", expected4 = "";
        switch (processor) {
            case SAX:
                errCode = "JAXP00090001";
                break;
            case STAX:
                errCode = "JAXP00090001";
                //errCode = "JAXP00090001";
                // StAX is non-validating parser
                isErrExpected = false;
                expected1 = ".*[\\w\\s]*(value1)[\\w\\s]*.*";
                expected3 = "Minimal XHTML 1.1 DocumentThis is a minimal XHTML 1.1 document.";
                expected4 = ".*(123)[\\w\\s]*.*";
                break;
            default:
                break;
        }

        return new Object[][]{
            // Case 1: external reference not in the JDKCatalog
            /**
             * Case 1-1: default setting; no Config file; Catalog: continue (by default)
             * Expect: error as the parser continues and tries to access an invalid site
             *         java.net.UnknownHostException: invalid.site.com
             */
            {fileDTDNotInC, null, null, null, null, null, null, isErrExpected, expected1},

            /**
             * Case 1-2: set JDK Catalog to strict in a Config file
             * Expect: Exception since the external reference is not in the Catalog
             * Error Msg:
             * [Fatal Error] properties1.xml:2:75: JAXP00090001: The CatalogResolver is enabled with the catalog "JdkCatalog.xml", but a CatalogException is returned.
             * org.xml.sax.SAXException: javax.xml.catalog.CatalogException: JAXP09040001: No match found for publicId 'null' and systemId 'http://invalid.site.com/dtd/properties1.dtd'.
             * javax.xml.catalog.CatalogException: JAXP09040001: No match found for publicId 'null' and systemId 'http://invalid.site.com/dtd/properties1.dtd'.
             */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, null, true, errCode},

            /**
             * Case 1-3: set CATALOG back to continue through the System Property
             * Expect: error as the parser continues and tries to access an invalid site
             *         java.net.UnknownHostException: invalid.site.com
             */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, null, null, isErrExpected, expected1},

            /**
             * Case 1-4: override the settings in Case 3 with the API property, and set Catalog to strict
             * Expect: Exception since the external reference is not in the Catalog
             */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, null, true, errCode},

            // Case 2: external reference in the JDKCatalog
            /**
             * Case 2-1: set CATALOG to strict in a Config file
             * Compare to: case 1-2
             * Expect: pass without error
             */
            {javaDTD, null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, null, false, expected1},

            /**
             * Case 2-2: override the settings in Case 3 with the API property, and set Catalog to strict
             * Compare to: case 1-4
             * Expect: pass without error
             */
            {javaDTD, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, null, false, expected1},

            // Case 3: external reference in the Custom Catalog
            /**
             * Case 3-1: set CATALOG to strict in a Config file
             * Compare to: case 1-2, would have resulted in an error without the
             *         custom catalog
             * Expect: pass without error because the external reference is in
             *         the custom catalog
             */
            {fileDTDInCC, null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, CustomCatalog.STRICT, false, expected4},

            /**
             * Case 3-2: override the settings in Case 3 with the API property, and set Catalog to strict
             * Compare to: case 1-4, would have resulted in an error without the
             *         custom catalog
             * Expect: pass without error
             */
            {fileDTDInCC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, CustomCatalog.STRICT, false, expected4},

            // Case 4: Parameter Entity reference
            /**
             * Case 4-1: set CATALOG to strict in a Config file
             * Compare to: case 1-2, would have resulted in an error since the external
             * reference can not be found
             * Expect: pass without error because the external reference is in
             *         the custom catalog
             */
            {"testExternalParameter.xml", null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, CustomCatalog.STRICT, false, expected1},

            // Case 5: resolve xInclude with the Custom Catalog
            /**
             * Case 5-1: set CATALOG to strict in a Config file
             * Compare to: case 1-2, would have resulted in an error without the
             *         custom catalog
             * Expect: pass without error because the external reference is in
             *         the custom catalog
             */
            {"XI_roottest.xml", null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, CustomCatalog.STRICT, false, ""},

        };
    }

    /*
     * DataProvider for testing configuring properties for validation or transform.
     *
     * Fields:
     *     xml file, xsd or xsl file, FSP, state of setting, config file, system property,
     *     api property, Custom Catalog, error expected, error code or expected result
     */
    //@DataProvider(name = "validationOrTransform")
    public Object[][] getConfig(String m) {
        // Schema Import
        String xmlFile = "XSDImport_company.xsd";
        String xsdOrXsl = null;
        String expected = "";
        String errCode = "JAXP00090001";

        switch (m) {
            case "SchemaTest2":
                // Schema Include
                xmlFile = "XSDInclude_company.xsd";
                break;
            case "Validation":
                // Schema Location
                xmlFile = "val_test.xml";
                break;
            case "Stylesheet":
                errCode = "JAXP09040001";
                xmlFile = "XSLDTD.xsl";
                break;
            case "Transform":
                xmlFile = "XSLPI.xml";
                errCode = "JAXP09040001";
                xsdOrXsl = "<?xml version='1.0'?>"
                + "<!DOCTYPE top SYSTEM 'test.dtd'"
                + "["
                + "<!ENTITY % pe \"x\">"
                + "<!ENTITY   x1 \"AAAAA\">"
                + "<!ENTITY   x2 \"bbb\">"
                +"]>"
                + "<?xml-stylesheet href=\""
                + TEST_SOURCE_DIR
                + "/XSLPI_target.xsl\" type=\"text/xml\"?>"
                + "<xsl:stylesheet "
                + "    xmlns:xsl='http://www.w3.org/1999/XSL/Transform' "
                + "    version='1.0'>"
                + "</xsl:stylesheet> ";
                break;
            default:
                break;
        }

        return new Object[][]{
            // Case 1: external reference not in the JDKCatalog
            /**
             * Case 1-1: default setting; no Config file; Catalog: continue
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, null, null, null, null, null, false, expected},

            /**
             * Case 1-2: set CATALOG to strict in a Config file
             * Expect: Exception since the external reference is not in the Catalog
             * Sample Error Msg:
             * org.xml.sax.SAXParseException; systemId: file:path/XSDImport_company.xsd;
             * lineNumber: 10; columnNumber: 11;
             * JAXP00090001: The CatalogResolver is enabled with the catalog "JdkCatalog.xml",
             * but a CatalogException is returned.
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, null, true, errCode},

            /**
             * Case 1-3: set CATALOG back to continue through the System Property
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, null, null, false, expected},

            /**
             * Case 1-4: override the settings in Case 3 with the API property, and set Catalog to strict
             * Expect: Exception since the external reference is not in the Catalog
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, null, true, errCode},

            /**
             * Case 1-5: use Custom Catalog to resolve external references
             * Expect: pass without error
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, new Properties[]{Properties.CATALOG0}, new Properties[]{Properties.CATALOG2}, CustomCatalog.STRICT, false, expected},

        };
    }

//    @Test(dataProvider = "configWCatalogForParsers", priority=0)
    public void testDOM(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        DocumentBuilderFactory dbf = getDBF(fsp, state, config, sysProp, apiProp, cc);
        process(filename, dbf, expectError, error);
    }

//    @Test(dataProvider = "configWCatalogForParsers")
    public void testSAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SAXParser parser = getSAXParser(fsp, state, config, sysProp, apiProp, cc);
        process(filename, parser, expectError, error);
    }

//    @Test(dataProvider = "configWCatalogForParsers")
    public void testStAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        XMLInputFactory xif = getXMLInputFactory(state, config, sysProp, apiProp, cc);
        process(filename, xif, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testSchema1(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, sf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testSchema2(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {
        testSchema1(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testValidation(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        validate(filename, sf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testStylesheet(String filename, String xsl, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        TransformerFactory tf = getTransformerFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, tf, expectError, error);
    }

//    @Test(dataProvider = "validationOrTransform")
    public void testTransform(String filename, String xsl, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        TransformerFactory tf = getTransformerFactory(fsp, state, config, sysProp, apiProp, cc);
        transform(filename, xsl, tf, expectError, error);
    }

    // parameters in the same order as the test method
    String filename; String xsd; String xsl; Properties fsp; PropertyState state;
    Properties config; Properties[] sysProp; Properties[] apiProp; CustomCatalog cc;
    boolean expectError; String error;

    // Maps the DataProvider array to individual parameters
    public void paramMap(Processor processor, String method, String index) {
        int i = 0;
        Object[][] params;
        if (processor == Processor.VALIDATOR ||
                processor == Processor.TRANSFORMER) {
            params = getConfig(method);
            i = 1;
        } else {
            params = getConfigs(processor);
        }
        Object[] param = params[Integer.parseInt(index)];
        filename = (String)param[0];
        if (processor == Processor.VALIDATOR) {
            xsd = (String)param[i];
        } else if (processor == Processor.TRANSFORMER) {
            xsl = (String)param[i];
        }
        fsp = (Properties)param[i + 1];
        state = (PropertyState)param[i + 2];
        config = (Properties)param[i + 3];
        sysProp = (Properties[])param[i + 4];
        apiProp = (Properties[])param[i + 5];
        cc = (CustomCatalog)param[i + 6];
        expectError = (boolean)param[i + 7];
        error = (String)param[i + 8];
    }
}
