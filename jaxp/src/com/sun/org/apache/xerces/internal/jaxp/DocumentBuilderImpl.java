/*
 * reserved comment block
 * DO NOT REMOVE OR ALTER!
 */
/*
 * Copyright 2000-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sun.org.apache.xerces.internal.jaxp;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.validation.Schema;
import javax.xml.XMLConstants;

import com.sun.org.apache.xerces.internal.dom.DOMImplementationImpl;
import com.sun.org.apache.xerces.internal.dom.DOMMessageFormatter;
import com.sun.org.apache.xerces.internal.impl.Constants;
import com.sun.org.apache.xerces.internal.impl.validation.ValidationManager;
import com.sun.org.apache.xerces.internal.impl.xs.XMLSchemaValidator;
import com.sun.org.apache.xerces.internal.jaxp.validation.XSGrammarPoolContainer;
import com.sun.org.apache.xerces.internal.parsers.DOMParser;
import com.sun.org.apache.xerces.internal.utils.XMLSecurityManager;
import com.sun.org.apache.xerces.internal.xni.XMLDocumentHandler;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponent;
import com.sun.org.apache.xerces.internal.xni.parser.XMLComponentManager;
import com.sun.org.apache.xerces.internal.xni.parser.XMLConfigurationException;
import com.sun.org.apache.xerces.internal.xni.parser.XMLDocumentSource;
import com.sun.org.apache.xerces.internal.xni.parser.XMLParserConfiguration;
import javax.xml.XMLConstants;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * @author Rajiv Mordani
 * @author Edwin Goei
 * @version $Id: DocumentBuilderImpl.java,v 1.8 2010-11-01 04:40:06 joehw Exp $
 */
public class DocumentBuilderImpl extends DocumentBuilder
        implements JAXPConstants
{
    /** Feature identifier: namespaces. */
    private static final String NAMESPACES_FEATURE =
        Constants.SAX_FEATURE_PREFIX + Constants.NAMESPACES_FEATURE;

    /** Feature identifier: include ignorable white space. */
    private static final String INCLUDE_IGNORABLE_WHITESPACE =
        Constants.XERCES_FEATURE_PREFIX + Constants.INCLUDE_IGNORABLE_WHITESPACE;

    /** Feature identifier: create entiry ref nodes feature. */
    private static final String CREATE_ENTITY_REF_NODES_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.CREATE_ENTITY_REF_NODES_FEATURE;

    /** Feature identifier: include comments feature. */
    private static final String INCLUDE_COMMENTS_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.INCLUDE_COMMENTS_FEATURE;

    /** Feature identifier: create cdata nodes feature. */
    private static final String CREATE_CDATA_NODES_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.CREATE_CDATA_NODES_FEATURE;

    /** Feature identifier: XInclude processing */
    private static final String XINCLUDE_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.XINCLUDE_FEATURE;

    /** feature identifier: XML Schema validation */
    private static final String XMLSCHEMA_VALIDATION_FEATURE =
        Constants.XERCES_FEATURE_PREFIX + Constants.SCHEMA_VALIDATION_FEATURE;

    /** Feature identifier: validation */
    private static final String VALIDATION_FEATURE =
        Constants.SAX_FEATURE_PREFIX + Constants.VALIDATION_FEATURE;

    /** Property identifier: security manager. */
    private static final String SECURITY_MANAGER =
        Constants.XERCES_PROPERTY_PREFIX + Constants.SECURITY_MANAGER_PROPERTY;

    /** property identifier: access external dtd. */
    public static final String ACCESS_EXTERNAL_DTD = XMLConstants.ACCESS_EXTERNAL_DTD;

    /** Property identifier: access to external schema */
    public static final String ACCESS_EXTERNAL_SCHEMA = XMLConstants.ACCESS_EXTERNAL_SCHEMA;

    private final DOMParser domParser;
    private final Schema grammar;

    private final XMLComponent fSchemaValidator;
    private final XMLComponentManager fSchemaValidatorComponentManager;
    private final ValidationManager fSchemaValidationManager;
    private final UnparsedEntityHandler fUnparsedEntityHandler;

    /** Initial ErrorHandler */
    private final ErrorHandler fInitErrorHandler;

    /** Initial EntityResolver */
    private final EntityResolver fInitEntityResolver;

    DocumentBuilderImpl(DocumentBuilderFactoryImpl dbf, Hashtable dbfAttrs, Hashtable features)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        this(dbf, dbfAttrs, features, false);
    }

    DocumentBuilderImpl(DocumentBuilderFactoryImpl dbf, Hashtable dbfAttrs, Hashtable features, boolean secureProcessing)
        throws SAXNotRecognizedException, SAXNotSupportedException
    {
        domParser = new DOMParser();

        // If validating, provide a default ErrorHandler that prints
        // validation errors with a warning telling the user to set an
        // ErrorHandler
        if (dbf.isValidating()) {
            fInitErrorHandler = new DefaultValidationErrorHandler();
            setErrorHandler(fInitErrorHandler);
        }
        else {
            fInitErrorHandler = domParser.getErrorHandler();
        }

        domParser.setFeature(VALIDATION_FEATURE, dbf.isValidating());

        // "namespaceAware" == SAX Namespaces feature
        domParser.setFeature(NAMESPACES_FEATURE, dbf.isNamespaceAware());

        // Set various parameters obtained from DocumentBuilderFactory
        domParser.setFeature(INCLUDE_IGNORABLE_WHITESPACE,
                !dbf.isIgnoringElementContentWhitespace());
        domParser.setFeature(CREATE_ENTITY_REF_NODES_FEATURE,
                !dbf.isExpandEntityReferences());
        domParser.setFeature(INCLUDE_COMMENTS_FEATURE,
                !dbf.isIgnoringComments());
        domParser.setFeature(CREATE_CDATA_NODES_FEATURE,
                !dbf.isCoalescing());

        // Avoid setting the XInclude processing feature if the value is false.
        // This will keep the configuration from throwing an exception if it
        // does not support XInclude.
        if (dbf.isXIncludeAware()) {
            domParser.setFeature(XINCLUDE_FEATURE, true);
        }

        // If the secure processing feature is on set a security manager.
        if (secureProcessing) {
            domParser.setProperty(SECURITY_MANAGER, new XMLSecurityManager());

            /**
             * By default, secure processing is set, no external access is allowed.
             * However, we need to check if it is actively set on the factory since we
             * allow the use of the System Property or jaxp.properties to override
             * the default value
             */
            if (features != null) {
                Object temp = features.get(XMLConstants.FEATURE_SECURE_PROCESSING);
                if (temp != null) {
                    boolean value = ((Boolean) temp).booleanValue();
                    if (value) {
                        domParser.setProperty(ACCESS_EXTERNAL_DTD, Constants.EXTERNAL_ACCESS_DEFAULT_FSP);
                        domParser.setProperty(ACCESS_EXTERNAL_SCHEMA, Constants.EXTERNAL_ACCESS_DEFAULT_FSP);
                    }
                }
            }
        }

        this.grammar = dbf.getSchema();
        if (grammar != null) {
            XMLParserConfiguration config = domParser.getXMLParserConfiguration();
            XMLComponent validatorComponent = null;
            /** For Xerces grammars, use built-in schema validator. **/
            if (grammar instanceof XSGrammarPoolContainer) {
                validatorComponent = new XMLSchemaValidator();
                fSchemaValidationManager = new ValidationManager();
                fUnparsedEntityHandler = new UnparsedEntityHandler(fSchemaValidationManager);
                config.setDTDHandler(fUnparsedEntityHandler);
                fUnparsedEntityHandler.setDTDHandler(domParser);
                domParser.setDTDSource(fUnparsedEntityHandler);
                fSchemaValidatorComponentManager = new SchemaValidatorConfiguration(config,
                        (XSGrammarPoolContainer) grammar, fSchemaValidationManager);
            }
            /** For third party grammars, use the JAXP validator component. **/
            else {
                validatorComponent = new JAXPValidatorComponent(grammar.newValidatorHandler());
                fSchemaValidationManager = null;
                fUnparsedEntityHandler = null;
                fSchemaValidatorComponentManager = config;
            }
            config.addRecognizedFeatures(validatorComponent.getRecognizedFeatures());
            config.addRecognizedProperties(validatorComponent.getRecognizedProperties());
            setFeatures(features);      // Must set before calling setDocumentHandler()
            config.setDocumentHandler((XMLDocumentHandler) validatorComponent);
            ((XMLDocumentSource)validatorComponent).setDocumentHandler(domParser);
            domParser.setDocumentSource((XMLDocumentSource) validatorComponent);
            fSchemaValidator = validatorComponent;
        }
        else {
            fSchemaValidationManager = null;
            fUnparsedEntityHandler = null;
            fSchemaValidatorComponentManager = null;
            fSchemaValidator = null;
            setFeatures(features);
        }

        // Set attributes
        setDocumentBuilderFactoryAttributes(dbfAttrs);

        // Initial EntityResolver
        fInitEntityResolver = domParser.getEntityResolver();
    }

    private void setFeatures(Hashtable features)
        throws SAXNotSupportedException, SAXNotRecognizedException {
        if (features != null) {
            Iterator entries = features.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry entry = (Map.Entry) entries.next();
                String feature = (String) entry.getKey();
                boolean value = ((Boolean) entry.getValue()).booleanValue();
                domParser.setFeature(feature, value);
            }
        }
    }

    /**
     * Set any DocumentBuilderFactory attributes of our underlying DOMParser
     *
     * Note: code does not handle possible conflicts between DOMParser
     * attribute names and JAXP specific attribute names,
     * eg. DocumentBuilderFactory.setValidating()
     */
    private void setDocumentBuilderFactoryAttributes(Hashtable dbfAttrs)
        throws SAXNotSupportedException, SAXNotRecognizedException
    {
        if (dbfAttrs == null) {
            // Nothing to do
            return;
        }

        Iterator entries = dbfAttrs.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry entry = (Map.Entry) entries.next();
            String name = (String) entry.getKey();
            Object val = entry.getValue();
            if (val instanceof Boolean) {
                // Assume feature
                domParser.setFeature(name, ((Boolean)val).booleanValue());
            } else {
                // Assume property
                if (JAXP_SCHEMA_LANGUAGE.equals(name)) {
                    // JAXP 1.2 support
                    //None of the properties will take effect till the setValidating(true) has been called
                    if ( W3C_XML_SCHEMA.equals(val) ) {
                        if( isValidating() ) {
                            domParser.setFeature(XMLSCHEMA_VALIDATION_FEATURE, true);
                            // this should allow us not to emit DTD errors, as expected by the
                            // spec when schema validation is enabled
                            domParser.setProperty(JAXP_SCHEMA_LANGUAGE, W3C_XML_SCHEMA);
                        }
                    }
                        } else if(JAXP_SCHEMA_SOURCE.equals(name)){
                        if( isValidating() ) {
                                                String value=(String)dbfAttrs.get(JAXP_SCHEMA_LANGUAGE);
                                                if(value !=null && W3C_XML_SCHEMA.equals(value)){
                                        domParser.setProperty(name, val);
                                                }else{
                            throw new IllegalArgumentException(
                                DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN,
                                "jaxp-order-not-supported",
                                new Object[] {JAXP_SCHEMA_LANGUAGE, JAXP_SCHEMA_SOURCE}));
                                                }
                                        }
                } else {
                    // Let Xerces code handle the property
                    domParser.setProperty(name, val);
                                }
                        }
                }
        }

    /**
     * Non-preferred: use the getDOMImplementation() method instead of this
     * one to get a DOM Level 2 DOMImplementation object and then use DOM
     * Level 2 methods to create a DOM Document object.
     */
    public Document newDocument() {
        return new com.sun.org.apache.xerces.internal.dom.DocumentImpl();
    }

    public DOMImplementation getDOMImplementation() {
        return DOMImplementationImpl.getDOMImplementation();
    }

    public Document parse(InputSource is) throws SAXException, IOException {
        if (is == null) {
            throw new IllegalArgumentException(
                DOMMessageFormatter.formatMessage(DOMMessageFormatter.DOM_DOMAIN,
                "jaxp-null-input-source", null));
        }
        if (fSchemaValidator != null) {
            if (fSchemaValidationManager != null) {
                fSchemaValidationManager.reset();
                fUnparsedEntityHandler.reset();
            }
            resetSchemaValidator();
        }
        domParser.parse(is);
        Document doc = domParser.getDocument();
        domParser.dropDocumentReferences();
        return doc;
    }

    public boolean isNamespaceAware() {
        try {
            return domParser.getFeature(NAMESPACES_FEATURE);
        }
        catch (SAXException x) {
            throw new IllegalStateException(x.getMessage());
        }
    }

    public boolean isValidating() {
        try {
            return domParser.getFeature(VALIDATION_FEATURE);
        }
        catch (SAXException x) {
            throw new IllegalStateException(x.getMessage());
        }
    }

    /**
     * Gets the XInclude processing mode for this parser
     * @return the state of XInclude processing mode
     */
    public boolean isXIncludeAware() {
        try {
            return domParser.getFeature(XINCLUDE_FEATURE);
        }
        catch (SAXException exc) {
            return false;
        }
    }

    public void setEntityResolver(EntityResolver er) {
        domParser.setEntityResolver(er);
    }

    public void setErrorHandler(ErrorHandler eh) {
        domParser.setErrorHandler(eh);
    }

    public Schema getSchema() {
        return grammar;
    }

    public void reset() {
        /** Restore the initial error handler. **/
        if (domParser.getErrorHandler() != fInitErrorHandler) {
            domParser.setErrorHandler(fInitErrorHandler);
        }
        /** Restore the initial entity resolver. **/
        if (domParser.getEntityResolver() != fInitEntityResolver) {
            domParser.setEntityResolver(fInitEntityResolver);
        }
    }

    // package private
    DOMParser getDOMParser() {
        return domParser;
    }

    private void resetSchemaValidator() throws SAXException {
        try {
            fSchemaValidator.reset(fSchemaValidatorComponentManager);
        }
        // This should never be thrown from the schema validator.
        catch (XMLConfigurationException e) {
            throw new SAXException(e);
        }
    }
}
