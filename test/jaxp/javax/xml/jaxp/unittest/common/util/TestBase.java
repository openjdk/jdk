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
package common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.catalog.CatalogFeatures;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Test base for common/dtd
 */
public class TestBase {
    static final boolean DEBUG = true;
    public static final String ORACLE_JAXP_PROPERTY_PREFIX =
        "http://www.oracle.com/xml/jaxp/properties/";
    public static final String JDK_ENTITY_COUNT_INFO =
            ORACLE_JAXP_PROPERTY_PREFIX + "getEntityCountInfo";
    public static final String CATALOG_FILE = CatalogFeatures.Feature.FILES.getPropertyName();
    public static final boolean IS_WINDOWS = System.getProperty("os.name").contains("Windows");
    public static String SRC_DIR = System.getProperty("test.src", ".");
    public static String TEST_SOURCE_DIR;


    // configuration file system property
    private static final String CONFIG_FILE = "java.xml.config.file";

    // Xerces Property
    public static final String DISALLOW_DTD = "http://apache.org/xml/features/disallow-doctype-decl";
    public static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    // Zephyr Properties
    public static final String ZEPHYR_PROPERTY_PREFIX = "http://java.sun.com/xml/stream/properties/" ;
    public static final String IGNORE_EXTERNAL_DTD = ZEPHYR_PROPERTY_PREFIX + "ignore-external-dtd";

    // Impl Specific Properties
    public static final String SP_DTD = "jdk.xml.dtd.support";
    public static final String OVERRIDE_PARSER = "jdk.xml.overrideDefaultParser";

    // DTD/CATALOG constants
    public static final String RESOLVE_CONTINUE = "continue";
    public static final String RESOLVE_IGNORE = "ignore";
    public static final String RESOLVE_STRICT = "strict";

    public static final String DTD_ALLOW = "allow";
    public static final String DTD_IGNORE = "ignore";
    public static final String DTD_DENY = "deny";

    // JAXP Configuration File(JCF) location
    // DTD = deny
    public static final String JCF_DTD2 = "../config/files/dtd2.properties";


    String xmlExternalEntity, xmlExternalEntityId;
    String xmlGE_Expansion, xmlGE_ExpansionId;

    public static enum Processor { DOM, SAX, STAX, VALIDATOR, TRANSFORMER };
    static enum SourceType { STREAM, SAX, STAX, DOM };

    public static enum Properties {
        CONFIG_FILE_DTD2(null, CONFIG_FILE, Type.FEATURE, getPath(JCF_DTD2)),
        FSP(XMLConstants.FEATURE_SECURE_PROCESSING, null, Type.FEATURE, "true"),
        FSP_FALSE(XMLConstants.FEATURE_SECURE_PROCESSING, null, Type.FEATURE, "false"),

        // properties
        DTD0(SP_DTD, "ditto", Type.PROPERTY, DTD_ALLOW),
        DTD1(SP_DTD, "ditto", Type.PROPERTY, DTD_IGNORE),
        DTD2(SP_DTD, "ditto", Type.PROPERTY, DTD_DENY),

        // StAX properties
        SUPPORT_DTD(XMLInputFactory.SUPPORT_DTD, null, Type.FEATURE, "true"),
        SUPPORT_DTD_FALSE(XMLInputFactory.SUPPORT_DTD, null, Type.FEATURE, "false"),
        SUPPORT_EXTERNAL_ENTITIES(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, null, Type.FEATURE, "true"),
        SUPPORT_EXTERNAL_ENTITIES_FALSE(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, null, Type.FEATURE, "false"),
        REPLACE_ENTITY_REF(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, null, Type.FEATURE, "true"),
        REPLACE_ENTITY_REF_FALSE(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, null, Type.FEATURE, "false"),
        ZEPHY_IGNORE_EXTERNAL_DTD(IGNORE_EXTERNAL_DTD, null, Type.FEATURE, "true"),
        ZEPHY_IGNORE_EXTERNAL_DTD_FALSE(IGNORE_EXTERNAL_DTD, null, Type.FEATURE, "false"),

        // Xerces properties
        XERCES_DISALLOW_DTD(DISALLOW_DTD, null, Type.FEATURE, "true"),
        XERCES_ALLOW_DTD(DISALLOW_DTD, null, Type.FEATURE, "false"),
        XERCES_LOAD_EXTERNAL_DTD(LOAD_EXTERNAL_DTD, null, Type.FEATURE, "true"),
        XERCES_LOAD_EXTERNAL_DTD_FALSE(LOAD_EXTERNAL_DTD, null, Type.FEATURE, "false"),

        ;

        final String apiName, spName;
        final Type type;
        final String value;

        String file, resolve;
        Properties(String apiName, String spName, Type t, String value) {
            this.apiName = apiName;
            // if spName not specified, it's the same as the API name
            if ("ditto".equals(spName)) {
                this.spName = apiName;
            } else {
                this.spName = spName;
            }
            this.type = t;
            this.value = value;
        }

        public Type type() {
            return type;
        }

        public String value() {
            return value;
        }
    }

    public static enum Type {
        CONFIGFILE,
        FEATURE,
        PROPERTY,
        LIMIT,
    }

    // the state of property setting
    public static enum PropertyState {
        // set through the factories
        API,
        // set through the System Property
        SYSTEM,
        // set in the Config file
        CONFIG_FILE,
        // set with both the Config file and System Property, the later shall prevail
        CONFIG_FILE_SYSTEM,
        // set: Config file, System Property and API, the later shall prevail
        CONFIG_FILE_SYSTEM_API,
    }

    protected void process(String filename, DocumentBuilderFactory dbf, boolean expectError,
            String error) throws Exception {
        //dbf.setAttribute(CatalogFeatures.Feature.RESOLVE.getPropertyName(), "continue");
        DocumentBuilder builder = dbf.newDocumentBuilder();
        File file = new File(getPath(filename));
        try {
            Document document = builder.parse(file);
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            e.printStackTrace();
            processError(expectError, error, e);
        }
    }

    protected void process(String filename, SAXParser parser, boolean expectError,
            String error) throws Exception {

        File file = new File(getPath(filename));
        try {
            parser.parse(file, new DefaultHandler());
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            //e.printStackTrace();
            processError(expectError, error, e);
        }
    }

    protected void process(String filename, XMLInputFactory xif, boolean expectError,
            String expected) throws Exception {

        String xml = getPath(filename);
        try {
            InputStream entityxml = new FileInputStream(xml);
            XMLStreamReader streamReader = xif.createXMLStreamReader(xml, entityxml);
            String text = getText(streamReader, XMLStreamConstants.CHARACTERS);
            System.out.println("Text: [" + text.trim() + "]");
            Assert.assertTrue(Pattern.matches(expected, text.trim()));
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            e.printStackTrace();
            processError(expectError, expected, e);
        }
    }

    protected void process(String filename, SchemaFactory sf, boolean expectError,
            String expected) throws Exception {

        String xsd = getPath(filename);
        try {
            Schema schema = sf.newSchema(new StreamSource(new File(xsd)));
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            e.printStackTrace();
            processError(expectError, expected, e);
        }
    }

    protected void process(String filename, TransformerFactory tf, boolean expectError,
            String expected) throws Exception {
        String xsl = getPath(filename);
        try {
            SAXSource xslSource = new SAXSource(new InputSource(xsl));
            xslSource.setSystemId(xsl);
            Transformer transformer = tf.newTransformer(xslSource);
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            //e.printStackTrace();
            processError(expectError, expected, e);
        }
    }

    protected void transform(String xmlFile, String xsl, TransformerFactory tf,
            boolean expectError, String expected) throws Exception {
        String xmlSysId = getPath(xmlFile);
        try {
            SAXSource xslSource = new SAXSource(new InputSource(new StringReader(xsl)));
            //SAXSource xslSource = new SAXSource(new InputSource(xslSysId));
            xslSource.setSystemId(xmlSysId);
            Transformer transformer = tf.newTransformer(xslSource);
            StringWriter sw = new StringWriter();
            transformer.transform(getSource(SourceType.STREAM, xmlSysId), new StreamResult(sw));
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            e.printStackTrace();
            processError(expectError, expected, e);
        }
    }

    protected void validate(String filename, SchemaFactory sf, boolean expectError,
            String expected) throws Exception {
        String xml = getPath(filename);
        try {
            Schema schema = sf.newSchema();
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new File(xml)));
            Assert.assertTrue(!expectError);
        } catch (Exception e) {
            e.printStackTrace();
            processError(expectError, expected, e);
        }
    }

    protected void processError(boolean expectError, String error, Exception e)
            throws Exception {
        //e.printStackTrace();
        String str = e.getMessage();
//        System.out.println("Exp Msg: " + str);
        //e.printStackTrace();
        if (!expectError) {
            Assert.assertTrue(false, "Expected pass, but Exception is thrown " +
                    str);
        } else {
            Assert.assertTrue((str != null) && str.contains(error));
        }
    }

    /**
     * Returns a DocumentBuilderFactory with settings as specified.
     *
     * @param fsp FSP setting
     * @param state the setting method
     * @param config the configuration file setting
     * @param sysProp properties to be set through the System Property API
     * @param apiProp the properties to be set via the factory
     * @return a DocumentBuilderFactory
     */
    protected DocumentBuilderFactory getDBF(Properties fsp, PropertyState state,
            Properties config, Properties[] sysProp, Properties[] apiProp) {
        setSystemProperty(config, state, sysProp);

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultNSInstance();
        dbf.setXIncludeAware(true);
        if (fsp != null) {
            try {
                dbf.setFeature(fsp.apiName, Boolean.parseBoolean(fsp.value));
            } catch (ParserConfigurationException ex) {
                // shouldn't happen
                //ex.printStackTrace();
                Assert.fail("Test error: setting " + fsp.apiName + " to " + fsp.value);
            }
        }
        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                if (property.type == Type.FEATURE) {
                    try {
                        dbf.setFeature(property.apiName, Boolean.parseBoolean(property.value));
                    } catch (ParserConfigurationException ex) {
                        Assert.fail("Test error: setting " + fsp.apiName + " to " + fsp.value);
                    }
                } else {
                    dbf.setAttribute(property.apiName, property.value);
                }
            }
        }

        clearSystemProperty(state, sysProp);

        return dbf;
    }

    /**
     * Returns an instance of SAXParser with a catalog if one is provided.
     *
     * @param fsp Feature Secure Processing
     * @param state the state of property settings
     * @param config the config file
     * @param sysProp the system properties
     * @param apiProp the properties to be set via the factory
     * @return an instance of SAXParser
     * @throws ParserConfigurationException
     * @throws Exception
     */
    public SAXParser getSAXParser(Properties fsp, PropertyState state, Properties config,
            Properties[] sysProp, Properties[] apiProp) throws Exception {
        setSystemProperty(config, state, sysProp);

        SAXParserFactory spf = SAXParserFactory.newDefaultNSInstance();
        spf.setXIncludeAware(true);
        if (fsp != null) {
            try {
                spf.setFeature(fsp.apiName, Boolean.parseBoolean(fsp.value));
            } catch (ParserConfigurationException ex) {
                Assert.fail("Test error: setting " + fsp.apiName + " to " + fsp.value);
            }
        }

        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                if (property.type == Type.FEATURE) {
                    try {
                        spf.setFeature(property.apiName, Boolean.parseBoolean(property.value));
                    } catch (ParserConfigurationException ex) {
                        Assert.fail("Test error: setting " + fsp.apiName + " to " + fsp.value);
                    }
                }
            }
        }
        SAXParser parser = spf.newSAXParser();
        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                if (property.type != Type.FEATURE) {
                    parser.setProperty(property.apiName, property.value);
                }
            }
        }

        clearSystemProperty(state, sysProp);
        return parser;
    }

    protected XMLInputFactory getXMLInputFactory(PropertyState state,
            Properties config, Properties[] sysProp, Properties[] apiProp) {
        setSystemProperty(config, state, sysProp);
        XMLInputFactory factory = XMLInputFactory.newInstance();

        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                factory.setProperty(property.apiName, property.value);
            }
        }

        clearSystemProperty(state, sysProp);

        return factory;
    }

    protected SchemaFactory getSchemaFactory(Properties fsp, PropertyState state,
            Properties config, Properties[] sysProp, Properties[] apiProp)
            throws Exception {
        setSystemProperty(config, state, sysProp);
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

        if (fsp != null) {
            factory.setFeature(fsp.apiName, Boolean.parseBoolean(fsp.value));
        }

        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                if (property.type == Type.FEATURE) {
                    factory.setFeature(property.apiName, Boolean.parseBoolean(property.value));
                } else {
                    factory.setProperty(property.apiName, property.value);
                }
            }
        }

        clearSystemProperty(state, sysProp);

        return factory;
    }

    protected TransformerFactory getTransformerFactory(Properties fsp, PropertyState state,
            Properties config, Properties[] sysProp, Properties[] apiProp)
            throws Exception {
        setSystemProperty(config, state, sysProp);
        TransformerFactory tf = TransformerFactory.newInstance();
        //tf.setAttribute(JDK_ENTITY_COUNT_INFO, "yes");
        if (fsp != null) {
            tf.setFeature(fsp.apiName, Boolean.parseBoolean(fsp.value));
        }
        if (state == PropertyState.API || state == PropertyState.CONFIG_FILE_SYSTEM_API) {
            for (Properties property : apiProp) {
                if (property.type == Type.FEATURE) {
                    tf.setFeature(property.apiName, Boolean.parseBoolean(property.value));
                } else {
                    tf.setAttribute(property.apiName, property.value);
                }
            }
        }

        clearSystemProperty(state, sysProp);

        return tf;
    }

    XMLStreamReader getStreamReader(boolean setUseCatalog, boolean useCatalog,
            String catalog, String xml, XMLResolver resolver)
            throws FileNotFoundException, XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        if (catalog != null) {
            factory.setProperty(CatalogFeatures.Feature.FILES.getPropertyName(), catalog);
        }

        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);

        if (resolver != null) {
            factory.setProperty(XMLInputFactory.RESOLVER, resolver);
        }

        if (setUseCatalog) {
            factory.setProperty(XMLConstants.USE_CATALOG, useCatalog);
        }

        InputStream entityxml = new FileInputStream(xml);
        XMLStreamReader streamReader = factory.createXMLStreamReader(xml, entityxml);
        return streamReader;
    }


    /**
     * Returns the accumulated text of an event type.
     *
     * @param streamReader the XMLStreamReader
     * @param type the type of event requested
     * @return the text of the accumulated text for the request type
     * @throws XMLStreamException
     */
    String getText(XMLStreamReader streamReader, int type) throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        StringBuilder entityRef = new StringBuilder();

        while(streamReader.hasNext()){
            int eventType = streamReader.next();
            switch (eventType) {
                case XMLStreamConstants.START_ELEMENT:
                    break;
                case XMLStreamConstants.CHARACTERS:
                    text.append(streamReader.getText());
                    break;
                case XMLStreamConstants.ENTITY_REFERENCE:
                    entityRef.append(streamReader.getText());
                    break;
            }
        }
        if (type == XMLStreamConstants.CHARACTERS) {
            return text.toString();
        } else {
            return entityRef.toString();
        }
    }

    /**
     * Build a Source for _xmlFile depending on the value of sourceType.
     * @return
     * @throws FileNotFoundException
     * @throws XMLStreamException
     */
    private Source getSource(SourceType sourceType, String xmlFile)
            throws FileNotFoundException, XMLStreamException {
        if (sourceType == null) {
            throw new Error("Test Bug: Please check that sourceType is set");
        }
        switch(sourceType) {
            case SAX: return new SAXSource(new InputSource(xmlFile));
            case STAX: return new StAXSource(XMLInputFactory.newFactory()
                    .createXMLEventReader(xmlFile, new FileInputStream(xmlFile)));
            case DOM: return new DOMSource(null,xmlFile);
            default: return new StreamSource(xmlFile);
        }
    }

    /**
     * Sets the System Property via the System Property API and/or the Config file.
     *
     * @param config the configuration file setting
     * @param state the setting method
     * @param sysProp properties to be set through the System Property API
     */
    protected void setSystemProperty(Properties config, PropertyState state, Properties[] sysProp) {
        // no System Property
        if (state == null) return;
        if (sysProp != null) {
            for (Properties property : sysProp) {
                setSystemProperty1(config, state, property);
            }
        } else {
            setSystemProperty1(config, state, null);
        }
    }

    protected void setSystemProperty1(Properties config, PropertyState state, Properties property) {
        switch (state) {
            case SYSTEM:
                System.setProperty(property.spName, property.value);
                break;
            case CONFIG_FILE:
                System.setProperty(CONFIG_FILE, config.value);
                break;
            case CONFIG_FILE_SYSTEM:
            case CONFIG_FILE_SYSTEM_API:
                System.setProperty(CONFIG_FILE, config.value);
                if (property != null) {
                    System.setProperty(property.spName, property.value);
                }
                break;
        }
    }

    /**
     * Clears the System Properties.
     *
     * @param state the state of setting, refer to {@link PropertyState}.
     * @param sysProp the system properties
     */
    protected void clearSystemProperty(PropertyState state, Properties[] sysProp) {
        if (state == null) return;
        if (sysProp != null) {
            for (Properties property : sysProp) {
                clearSystemProperty1(state, property);
            }
        } else {
            clearSystemProperty1(state, null);
        }
    }
    protected void clearSystemProperty1(PropertyState m, Properties property) {
        if (m == null) return;
        switch (m) {
            case SYSTEM:
                System.clearProperty(property.spName);
                break;
            case CONFIG_FILE:
                System.clearProperty(CONFIG_FILE);
                break;
            case CONFIG_FILE_SYSTEM:
            case CONFIG_FILE_SYSTEM_API:
                System.clearProperty(CONFIG_FILE);
                if (property != null) {
                    System.clearProperty(property.spName);
                }
                break;
        }
    }

    static String getPath(String file) {
        String temp = TEST_SOURCE_DIR + file;
        if (IS_WINDOWS) {
            temp = "/" + temp;
        }
        return temp;
    }

    static class Assert {
        public static void assertTrue(boolean condition) {
            assertTrue(condition, null);
        }

        public static void assertTrue(boolean condition, String message) {
            if (!condition) {
                if (message != null) {
                    throw new RuntimeException("Expected true but was false. " + message);
                } else {
                    throw new RuntimeException("Expected true but was false. ");
                }
            }
        }

        public static void fail(String message) {
            throw new RuntimeException("Test failed. " + message);
        }
    }
}
