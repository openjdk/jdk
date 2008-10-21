/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.servicetag;

import java.io.*;
import java.net.URL;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

// For write operation
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/**
 * XML Support Class for Product Registration.
 */
class RegistrationDocument {

    private static final String REGISTRATION_DATA_SCHEMA =
            "/com/sun/servicetag/resources/product_registration.xsd";
    private static final String REGISTRATION_DATA_VERSION = "1.0";
    private static final String SERVICE_TAG_VERSION = "1.0";
    final static String ST_NODE_REGISTRATION_DATA = "registration_data";
    final static String ST_ATTR_REGISTRATION_VERSION = "version";
    final static String ST_NODE_ENVIRONMENT = "environment";
    final static String ST_NODE_HOSTNAME = "hostname";
    final static String ST_NODE_HOST_ID = "hostId";
    final static String ST_NODE_OS_NAME = "osName";
    final static String ST_NODE_OS_VERSION = "osVersion";
    final static String ST_NODE_OS_ARCH = "osArchitecture";
    final static String ST_NODE_SYSTEM_MODEL = "systemModel";
    final static String ST_NODE_SYSTEM_MANUFACTURER = "systemManufacturer";
    final static String ST_NODE_CPU_MANUFACTURER = "cpuManufacturer";
    final static String ST_NODE_SERIAL_NUMBER = "serialNumber";
    final static String ST_NODE_REGISTRY = "registry";
    final static String ST_ATTR_REGISTRY_URN = "urn";
    final static String ST_ATTR_REGISTRY_VERSION = "version";
    final static String ST_NODE_SERVICE_TAG = "service_tag";
    final static String ST_NODE_INSTANCE_URN = "instance_urn";
    final static String ST_NODE_PRODUCT_NAME = "product_name";
    final static String ST_NODE_PRODUCT_VERSION = "product_version";
    final static String ST_NODE_PRODUCT_URN = "product_urn";
    final static String ST_NODE_PRODUCT_PARENT_URN = "product_parent_urn";
    final static String ST_NODE_PRODUCT_PARENT = "product_parent";
    final static String ST_NODE_PRODUCT_DEFINED_INST_ID = "product_defined_inst_id";
    final static String ST_NODE_PRODUCT_VENDOR = "product_vendor";
    final static String ST_NODE_PLATFORM_ARCH = "platform_arch";
    final static String ST_NODE_TIMESTAMP = "timestamp";
    final static String ST_NODE_CONTAINER = "container";
    final static String ST_NODE_SOURCE = "source";
    final static String ST_NODE_INSTALLER_UID = "installer_uid";

    static RegistrationData load(InputStream in) throws IOException {
        Document document = initializeDocument(in);

        // Gets the registration URN
        Element root = getRegistrationDataRoot(document);
        Element registryRoot =
                getSingletonElementFromRoot(root, ST_NODE_REGISTRY);
        String urn = registryRoot.getAttribute(ST_ATTR_REGISTRY_URN);

        // Construct a new RegistrationData object from the DOM tree
        // Initialize the environment map and service tags
        RegistrationData regData = new RegistrationData(urn);
        addServiceTags(registryRoot, regData);

        Element envRoot = getSingletonElementFromRoot(root, ST_NODE_ENVIRONMENT);
        buildEnvironmentMap(envRoot, regData);
        return regData;
    }

    static void store(OutputStream os, RegistrationData registration)
            throws IOException {
        // create a new document with the root node
        Document document = initializeDocument();

        // create the nodes for the environment map and the service tags
        // in the registration data
        addEnvironmentNodes(document,
                            registration.getEnvironmentMap());
        addServiceTagRegistry(document,
                              registration.getRegistrationURN(),
                              registration.getServiceTags());
        transform(document, os);
    }

    // initialize a document from an input stream
    private static Document initializeDocument(InputStream in) throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            // XML schema for validation
            SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            URL xsdUrl = RegistrationDocument.class.getResource(REGISTRATION_DATA_SCHEMA);
            Schema schema = sf.newSchema(xsdUrl);
            Validator validator = schema.newValidator();

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(in));
            validator.validate(new DOMSource(doc));
            return doc;
        } catch (SAXException sxe) {
            IllegalArgumentException e = new IllegalArgumentException("Error generated in parsing");
            e.initCause(sxe);
            throw e;
        } catch (ParserConfigurationException pce) {
            // Parser with specific options can't be built
            // should not reach here
            InternalError x = new InternalError("Error in creating the new document");
            x.initCause(pce);
            throw x;
        }
    }

    // initialize a new document for the registration data
    private static Document initializeDocument() throws IOException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.newDocument();

            // initialize the document with the registration_data root
            Element root = doc.createElement(ST_NODE_REGISTRATION_DATA);
            doc.appendChild(root);
            root.setAttribute(ST_ATTR_REGISTRATION_VERSION, REGISTRATION_DATA_VERSION);

            return doc;
        } catch (ParserConfigurationException pce) {
            // Parser with specified options can't be built
            // should not reach here
            InternalError x = new InternalError("Error in creating the new document");
            x.initCause(pce);
            throw x;
        }
    }

    // Transform the current DOM tree with the given output stream.
    private static void transform(Document document, OutputStream os) {
        try {
            // Use a Transformer for output
            TransformerFactory tFactory = TransformerFactory.newInstance();
            tFactory.setAttribute("indent-number", new Integer(3));

            Transformer transformer = tFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.STANDALONE, "yes");
            transformer.transform(new DOMSource(document),
                new StreamResult(new BufferedWriter(new OutputStreamWriter(os, "UTF-8"))));
        } catch (UnsupportedEncodingException ue) {
            // Should not reach here
            InternalError x = new InternalError("Error generated during transformation");
            x.initCause(ue);
            throw x;
        } catch (TransformerConfigurationException tce) {
            // Error generated by the parser
            // Should not reach here
            InternalError x = new InternalError("Error in creating the new document");
            x.initCause(tce);
            throw x;
        } catch (TransformerException te) {
            // Error generated by the transformer
            InternalError x = new InternalError("Error generated during transformation");
            x.initCause(te);
            throw x;
        }
    }

    private static void addServiceTagRegistry(Document document,
                                              String registryURN,
                                              Set<ServiceTag> svcTags) {
        // add service tag registry node and its attributes
        Element reg = document.createElement(ST_NODE_REGISTRY);
        reg.setAttribute(ST_ATTR_REGISTRY_URN, registryURN);
        reg.setAttribute(ST_ATTR_REGISTRY_VERSION, SERVICE_TAG_VERSION);

        Element root = getRegistrationDataRoot(document);
        root.appendChild(reg);

        // adds the elements for the service tags
        for (ServiceTag st : svcTags) {
            addServiceTagElement(document, reg, st);
        }
    }

    private static void addServiceTagElement(Document document,
                                             Element registryRoot,
                                             ServiceTag st) {
        Element svcTag = document.createElement(ST_NODE_SERVICE_TAG);
        registryRoot.appendChild(svcTag);
        addChildElement(document, svcTag,
                        ST_NODE_INSTANCE_URN, st.getInstanceURN());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_NAME, st.getProductName());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_VERSION, st.getProductVersion());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_URN, st.getProductURN());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_PARENT_URN, st.getProductParentURN());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_PARENT, st.getProductParent());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_DEFINED_INST_ID,
                        st.getProductDefinedInstanceID());
        addChildElement(document, svcTag,
                        ST_NODE_PRODUCT_VENDOR, st.getProductVendor());
        addChildElement(document, svcTag,
                        ST_NODE_PLATFORM_ARCH, st.getPlatformArch());
        addChildElement(document, svcTag,
                        ST_NODE_TIMESTAMP, Util.formatTimestamp(st.getTimestamp()));
        addChildElement(document, svcTag,
                        ST_NODE_CONTAINER, st.getContainer());
        addChildElement(document, svcTag,
                        ST_NODE_SOURCE, st.getSource());
        addChildElement(document, svcTag,
                        ST_NODE_INSTALLER_UID,
                        String.valueOf(st.getInstallerUID()));
    }

    private static void addChildElement(Document document, Element root,
                                        String element, String text) {
        Element node = document.createElement(element);
        node.appendChild(document.createTextNode(text));
        root.appendChild(node);
    }

    // Constructs service tags from the document
    private static void addServiceTags(Element registryRoot,
                                       RegistrationData registration) {
        NodeList children = registryRoot.getElementsByTagName(ST_NODE_SERVICE_TAG);
        int length = (children == null ? 0 : children.getLength());
        for (int i = 0; i < length; i++) {
            Element svcTagElement = (Element) children.item(i);
            ServiceTag st = getServiceTag(svcTagElement);
            registration.addServiceTag(st);
        }
    }

    // build environment map from the document
    private static void buildEnvironmentMap(Element envRoot,
                                         RegistrationData registration) {
        registration.setEnvironment(ST_NODE_HOSTNAME, getTextValue(envRoot, ST_NODE_HOSTNAME));
        registration.setEnvironment(ST_NODE_HOST_ID, getTextValue(envRoot, ST_NODE_HOST_ID));
        registration.setEnvironment(ST_NODE_OS_NAME, getTextValue(envRoot, ST_NODE_OS_NAME));
        registration.setEnvironment(ST_NODE_OS_VERSION, getTextValue(envRoot, ST_NODE_OS_VERSION));
        registration.setEnvironment(ST_NODE_OS_ARCH, getTextValue(envRoot, ST_NODE_OS_ARCH));
        registration.setEnvironment(ST_NODE_SYSTEM_MODEL, getTextValue(envRoot, ST_NODE_SYSTEM_MODEL));
        registration.setEnvironment(ST_NODE_SYSTEM_MANUFACTURER, getTextValue(envRoot, ST_NODE_SYSTEM_MANUFACTURER));
        registration.setEnvironment(ST_NODE_CPU_MANUFACTURER, getTextValue(envRoot, ST_NODE_CPU_MANUFACTURER));
        registration.setEnvironment(ST_NODE_SERIAL_NUMBER, getTextValue(envRoot, ST_NODE_SERIAL_NUMBER));
    }

    // add the nodes representing the environment map in the document
    private static void addEnvironmentNodes(Document document,
                                            Map<String, String> envMap) {
        Element root = getRegistrationDataRoot(document);
        Element env = document.createElement(ST_NODE_ENVIRONMENT);
        root.appendChild(env);
        Set<Map.Entry<String, String>> keys = envMap.entrySet();
        for (Map.Entry<String, String> entry : keys) {
            addChildElement(document, env, entry.getKey(), entry.getValue());
        }
    }

    private static Element getRegistrationDataRoot(Document doc) {
        Element root = doc.getDocumentElement();
        if (!root.getNodeName().equals(ST_NODE_REGISTRATION_DATA)) {
            throw new IllegalArgumentException("Not a " +
                    ST_NODE_REGISTRATION_DATA +
                    " node \"" + root.getNodeName() + "\"");
        }
        return root;
    }

    private static Element getSingletonElementFromRoot(Element root, String name) {
        NodeList children = root.getElementsByTagName(name);
        int length = (children == null ? 0 : children.getLength());
        if (length != 1) {
            throw new IllegalArgumentException("Invalid number of " + name +
                    " nodes = " + length);
        }
        Element e = (Element) children.item(0);
        if (!e.getNodeName().equals(name)) {
            throw new IllegalArgumentException("Not a  " + name +
                    " node \"" + e.getNodeName() + "\"");
        }
        return e;
    }

    // Constructs one ServiceTag instance from a service tag element root
    private static ServiceTag getServiceTag(Element svcTagElement) {
        return new ServiceTag(
            getTextValue(svcTagElement, ST_NODE_INSTANCE_URN),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_NAME),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_VERSION),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_URN),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_PARENT),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_PARENT_URN),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_DEFINED_INST_ID),
            getTextValue(svcTagElement, ST_NODE_PRODUCT_VENDOR),
            getTextValue(svcTagElement, ST_NODE_PLATFORM_ARCH),
            getTextValue(svcTagElement, ST_NODE_CONTAINER),
            getTextValue(svcTagElement, ST_NODE_SOURCE),
            Util.getIntValue(getTextValue(svcTagElement, ST_NODE_INSTALLER_UID)),
            Util.parseTimestamp(getTextValue(svcTagElement, ST_NODE_TIMESTAMP))
        );
    }

    private static String getTextValue(Element e, String tagName) {
        String value = "";
        NodeList nl = e.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            Element el = (Element) nl.item(0);
            Node node = el.getFirstChild();
            if (node != null) {
                value = node.getNodeValue();
            }
        }
        return value;
    }
}
