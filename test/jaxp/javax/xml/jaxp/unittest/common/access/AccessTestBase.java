/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package common.access;

import common.util.TestBase;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.validation.SchemaFactory;

/**
 * Test base for Access Rule test.
 */
public class AccessTestBase extends TestBase {
    /*
     * Test scenarios for verifying preferences.
     *
     * Fields:
     *     file, FSP, state of setting, config file, system property, api property,
     *     Custom Catalog, error expected, error code or expected result
     */
    public Object[][] getConfigs(Processor processor) {
        // file with an external DTD that's not in JdkCatalog
        String fileDTDNotInC = "properties1.xml";
        // file with an external DTD that's in the Custom Catalog
        String fileDTDInCC = "test.xml";
        // file with an external DTD that's in JdkCatalog
        String javaDTD = "properties.xml";

        // error code when CATALOG=strict; The cause for DOM
        String errCode = "JAXP09040001";

        // error (not from catalog) is expect when CATALOG=continue
        boolean isErrExpected = true;
        String expected1 = UNKNOWN_HOST;

        // expected when reference is resolved by Catalog
        String expected2 = "";
        switch (processor) {
            case SAX:
                errCode = "JAXP00090001";
                break;
            case STAX:
                errCode = "JAXP00090001";
                // StAX is non-validating parser
                isErrExpected = false;
                expected1 = ".*[\\w\\s]*(value1)[\\w\\s]*.*";
                expected2 = ".*(123)[\\w\\s]*.*";
                break;
            default:
                break;
        }

        return new Object[][]{
            /**
             * Case 1: all properties must permit
             *          Note: External reference not in the built-in catalog
             * Expect: error as the parser continues and tries to access an invalid site
             *         java.net.UnknownHostException: invalid.site.com
             */
            /**
             * Case 1-1: by default, both RESOURCE_ACCESS and EAP allow access
            */
            {fileDTDNotInC, null, null, null, null, null, null, isErrExpected, expected1},
            /**
             * Case 1-2: FSP is set, both RESOURCE_ACCESS and EAP are set to allow
            */
            {fileDTDNotInC, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, null, new Properties[]{Properties.ACCESS0, Properties.AED0}, null, isErrExpected, expected1},
            /**
             * Case 2: access is denied unless all properties permit the operation
             *
             * Sample Error Message: [Fatal Error] properties1.xml:7:11: External DTD: Failed to read external DTD 'properties1.dtd',
             * because access is not allowed due to restriction set by 'Resource Access (jdk.xml.resource.access) and http://javax.xml.XMLConstants/property/accessExternalDTD'.
             *
            */
            /**
            * Case 2-1: FSP is set. RESOURCE_ACCESS is unchanged, and EAP denies access

            * Expect: [Fatal Error] access denied by ACCESS_EXTERNAL_DTD
            */
            {fileDTDNotInC, Properties.FSP, null, null, null, null, null, true, XMLConstants.ACCESS_EXTERNAL_DTD},
            /**
             * Case 2-2: RESOURCE_ACCESS is set to deny access though EAP allows it

             * Expect: [Fatal Error] access denied by RESOURCE_ACCESS
            */
            {fileDTDNotInC, Properties.FSP, PropertyState.API, null, null, new Properties[]{Properties.ACCESS1, Properties.AED0}, null, true, SP_ACCESS},
            /**
             * Case 2-3: ACCESS_EXTERNAL_DTD is set to deny access though RESOURCE_ACCESS allows it

             * Expect: [Fatal Error] access denied by ACCESS_EXTERNAL_DTD
            */
            {fileDTDNotInC, Properties.FSP, PropertyState.API, null, null, new Properties[]{Properties.ACCESS0, Properties.AED2}, null, true, XMLConstants.ACCESS_EXTERNAL_DTD},
            /**
             * Case 2-4: System properties override FSP secure values.
             *
             * Expect: error as the parser continues and tries to access an invalid site
             */
            {fileDTDNotInC, Properties.FSP, PropertyState.SYSTEM, null, new Properties[]{Properties.ACCESS0, Properties.AED0}, null, null, isErrExpected, expected1},
            /**
             * Case 2-5: API property setting overrides system property setting.
             *
             * Expect: error as the parser continues and tries to access an invalid site
             */
            {fileDTDNotInC, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, new Properties[]{Properties.ACCESS1, Properties.AED0}, new Properties[]{Properties.ACCESS0, Properties.AED0}, null, isErrExpected, expected1},
            /**
             * Case 2-6: API property setting overrides system property setting.
             *
             * Expect: [Fatal Error] access denied by RESOURCE_ACCESS
             */
            {fileDTDNotInC, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, new Properties[]{Properties.ACCESS0, Properties.AED0}, new Properties[]{Properties.ACCESS1, Properties.AED0}, null, true, SP_ACCESS},

            /**
             * Case 3: Resolvers and Catalogs take precedence in the resource resolution process
             *
             * Sample Error Message when access is denied by Catalog's Resolve property:
             * [Fatal Error] properties1.xml:7:11: JAXP00090001: The CatalogResolver is enabled with the catalog "JDKCatalog.xml", but a CatalogException is returned.
             * org.xml.sax.SAXException: javax.xml.catalog.CatalogException: JAXP09040001: No match found for publicId 'null' and systemId 'http://invalid.site.com/dtd/properties1.dtd'.
             *
            */
            /**
             * Case 3-1: the built-in catalog's Resolve property is set to "strict", both Resource Access (jdk.xml.resource.access) and External Access Properties (EAPs) have no effect,
             * regardless of their configured values
             *
             * Expect: error as access is denied by the Catalog's Resolve property
            */
            {fileDTDNotInC, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, null, new Properties[]{Properties.ACCESS0, Properties.AED0}, null, true, errCode},
            /**
             * Case 3-2: the reference is resolved by the built-in catalog before direct fetch.
             * FSP sets EAP to deny direct fetch, but catalog resolution completes first.
             *
             * Expect: no error
            */
            {javaDTD, Properties.FSP, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, null, false, expected1},
            /**
             * Case 3-3: the reference is resolved by a custom catalog before direct fetch.
             * FSP sets EAP to deny direct fetch, but catalog resolution completes first.
             *
             * Expect: no error
            */
            {fileDTDInCC, Properties.FSP, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, CustomCatalog.STRICT, false, expected2}
        };
    }

    /*
     * Test scenarios for XInclude. XInclude direct fetches are controlled by
     * RESOURCE_ACCESS, while resolver/catalog results take precedence.
     */
    public Object[][] getXIncludeConfigs(Processor processor) {
        String xinclude = "XI_roottest.xml";

        return new Object[][]{
            /**
             * Case 1-1: by default, RESOURCE_ACCESS allows direct XInclude access.
             */
            {xinclude, null, null, null, null, null, null, false, ""},
            /**
             * Case 1-2: FSP is set, RESOURCE_ACCESS is explicitly set to allow.
             */
            {xinclude, Properties.FSP, PropertyState.API, null, null,
                    new Properties[]{Properties.ACCESS0}, null, false, ""},
            /**
             * Case 2-1: RESOURCE_ACCESS is set to deny direct XInclude access.
             *
             * Expect: access denied by RESOURCE_ACCESS
             */
            {xinclude, Properties.FSP, PropertyState.API, null, null,
                    new Properties[]{Properties.ACCESS1}, null, true, SP_ACCESS},
            /**
             * Case 2-2: system properties override FSP.
             */
            {xinclude, Properties.FSP, PropertyState.SYSTEM, null,
                    new Properties[]{Properties.ACCESS0}, null, null, false, ""},
            /**
             * Case 2-3: API property setting overrides system property setting.
             */
            {xinclude, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null,
                    new Properties[]{Properties.ACCESS1},
                    new Properties[]{Properties.ACCESS0}, null, false, ""},
            /**
             * Case 2-4: API property setting overrides system property setting.
             *
             * Expect: access denied by RESOURCE_ACCESS
             */
            {xinclude, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null,
                    new Properties[]{Properties.ACCESS0},
                    new Properties[]{Properties.ACCESS1}, null, true, SP_ACCESS},
            /**
             * Case 3-1: custom catalog resolves the reference before direct fetch.
             * FSP sets EAP to deny direct fetch, but catalog resolution completes first.
             *
             * Expect: no error
             */
            {xinclude, Properties.FSP, PropertyState.CONFIG_FILE,
                    Properties.CONFIG_FILE_CATALOG_STRICT, null, null,
                    CustomCatalog.STRICT, false, ""},
        };
    }

    /*
     * Test scenarios for configuring properties for validation or transform.
     *
     * Fields:
     *     xml file, xsd or xsl file, FSP, state of setting, config file, system property,
     *     api property, Custom Catalog, error expected, error code or expected result
     */
    public Object[][] getConfig(String m) {
        // Schema Import
        String xmlFile = "XSDImport_company.xsd";
        String xsdOrXsl = null;
        String expected = "";
        String errCode = "JAXP00090001";
        Properties eapAllow = Properties.AES0;
        Properties eapDeny = Properties.AES2;
        String eapName = "accessExternalDTD,accessExternalSchema";

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
                eapAllow = Properties.AED0;
                eapDeny = Properties.AED2;
                eapName = XMLConstants.ACCESS_EXTERNAL_DTD;
                break;
            case "Transform":
                xmlFile = "XSLPI.xml";
                errCode = "JAXP00090001";
                xsdOrXsl = "<?xml version='1.0'?>"
                + "<xsl:stylesheet "
                + "    xmlns:xsl='http://www.w3.org/1999/XSL/Transform' "
                + "    version='1.0'>"
                + "<xsl:include href='XSLPI_target.xsl'/>"
                + "<xsl:template match='/'>"
                + "<out/>"
                + "</xsl:template>"
                + "</xsl:stylesheet> ";
                eapAllow = Properties.AEX0;
                eapDeny = Properties.AEX2;
                eapName = XMLConstants.ACCESS_EXTERNAL_STYLESHEET;
                break;
            default:
                break;
        }

        return new Object[][]{
            /**
             * Case 1: all properties must permit.
             *
             * Case 1-1: by default, both RESOURCE_ACCESS and EAP allow access.
             */
            {xmlFile, xsdOrXsl, null, null, null, null, null, null, false, expected},
            /**
             * Case 1-2: FSP is set, both RESOURCE_ACCESS and EAP are set to allow.
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, null, new Properties[]{Properties.ACCESS0, Properties.AED0, eapAllow}, null, false, expected},
            /**
             * Case 2: access is denied unless all properties permit the operation.
             *
             * Case 2-1: FSP is set. RESOURCE_ACCESS is unchanged, and EAP denies access.
             *
             * Expect: access denied by EAP
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.CONFIG_FILE, null, null, null, null, true, eapName},
            /**
             * Case 2-2: RESOURCE_ACCESS is set to deny access though EAP allows it.
             *
             * Expect: access denied by RESOURCE_ACCESS
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.API, null, null, new Properties[]{Properties.ACCESS1, Properties.AED0, eapAllow}, null, true, SP_ACCESS},
            /**
             * Case 2-3: EAP is set to deny access though RESOURCE_ACCESS allows it.
             *
             * Expect: access denied by EAP
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.API, null, null, new Properties[]{Properties.ACCESS0, Properties.AED0, eapDeny}, null, true, eapName},
            /**
             * Case 2-4: system properties override FSP secure values.
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.SYSTEM, null, new Properties[]{Properties.ACCESS0, Properties.AED0, eapAllow}, null, null, false, expected},
            /**
             * Case 2-5: API property setting overrides system property setting.
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, new Properties[]{Properties.ACCESS1, Properties.AED0, eapAllow}, new Properties[]{Properties.ACCESS0, Properties.AED0, eapAllow}, null, false, expected},
            /**
             * Case 2-6: API property setting overrides system property setting.
             *
             * Expect: access denied by RESOURCE_ACCESS
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.CONFIG_FILE_SYSTEM_API, null, new Properties[]{Properties.ACCESS0, Properties.AED0, eapAllow}, new Properties[]{Properties.ACCESS1, Properties.AED0, eapAllow}, null, true, SP_ACCESS},
            /**
             * Case 3: Catalogs take precedence in the resource resolution process.
             *
             * Case 3-1: the built-in catalog's Resolve property is set to "strict",
             * and the reference is not in the built-in catalog.
             *
             * Expect: error as access is denied by the Catalog's Resolve property
             */
            {xmlFile, xsdOrXsl, null, PropertyState.CONFIG_FILE_SYSTEM_API, Properties.CONFIG_FILE_CATALOG_STRICT, null, new Properties[]{Properties.ACCESS0, Properties.AED0, eapAllow}, null, true, errCode},
            /**
             * Case 3-2: custom catalog resolves the reference before direct fetch.
             * FSP sets EAP to deny direct fetch, but catalog resolution completes first.
             *
             * Expect: no error
             */
            {xmlFile, xsdOrXsl, Properties.FSP, PropertyState.CONFIG_FILE, Properties.CONFIG_FILE_CATALOG_STRICT, null, null, CustomCatalog.STRICT, false, expected},
        };
    }

    public void testDOM(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        DocumentBuilderFactory dbf = getDBF(fsp, state, config, sysProp, apiProp, cc);
        process(filename, dbf, expectError, error);
    }

    public void testSAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SAXParser parser = getSAXParser(fsp, state, config, sysProp, apiProp, cc);
        process(filename, parser, expectError, error);
    }

    public void testStAX(String filename, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        XMLInputFactory xif = getXMLInputFactory(state, config, sysProp, apiProp, cc);
        process(filename, xif, expectError, error);
    }

    public void testSchema1(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, sf, expectError, error);
    }

    public void testSchema2(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {
        testSchema1(filename, xsd, fsp, state, config, sysProp, apiProp, cc, expectError, error);
    }

    public void testValidation(String filename, String xsd, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        SchemaFactory sf = getSchemaFactory(fsp, state, config, sysProp, apiProp, cc);
        validate(filename, sf, expectError, error);
    }

    public void testStylesheet(String filename, String xsl, Properties fsp, PropertyState state,
        Properties config, Properties[] sysProp, Properties[] apiProp, CustomCatalog cc,
        boolean expectError, String error) throws Exception {

        TransformerFactory tf = getTransformerFactory(fsp, state, config, sysProp, apiProp, cc);
        process(filename, tf, expectError, error);
    }

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

    // Maps the scenario array to individual parameters
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

    public void paramMapXInclude(Processor processor, String index) {
        Object[] param = getXIncludeConfigs(processor)[Integer.parseInt(index)];
        filename = (String)param[0];
        fsp = (Properties)param[1];
        state = (PropertyState)param[2];
        config = (Properties)param[3];
        sysProp = (Properties[])param[4];
        apiProp = (Properties[])param[5];
        cc = (CustomCatalog)param[6];
        expectError = (boolean)param[7];
        error = (String)param[8];
    }
}
