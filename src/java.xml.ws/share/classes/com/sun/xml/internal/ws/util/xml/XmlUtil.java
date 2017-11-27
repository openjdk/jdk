/*
 * Copyright (c) 1997, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.sun.xml.internal.ws.util.xml;

import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.util.ByteArrayBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

/**
 * @author WS Development Team
 */
public class XmlUtil {

    // not in older JDK, so must be duplicated here, otherwise javax.xml.XMLConstants should be used
    private static final String ACCESS_EXTERNAL_SCHEMA = "http://javax.xml.XMLConstants/property/accessExternalSchema";

    private final static String LEXICAL_HANDLER_PROPERTY =
        "http://xml.org/sax/properties/lexical-handler";

    private static final String DISALLOW_DOCTYPE_DECL = "http://apache.org/xml/features/disallow-doctype-decl";
    private static final String EXTERNAL_GE = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PE = "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    private static final Logger LOGGER = Logger.getLogger(XmlUtil.class.getName());

    private static final String DISABLE_XML_SECURITY = "com.sun.xml.internal.ws.disableXmlSecurity";

    private static boolean XML_SECURITY_DISABLED = AccessController.doPrivileged(
            new PrivilegedAction<Boolean>() {
                @Override
                public Boolean run() {
                    return Boolean.getBoolean(DISABLE_XML_SECURITY);
                }
            }
    );

    public static String getPrefix(String s) {
        int i = s.indexOf(':');
        if (i == -1)
            return null;
        return s.substring(0, i);
    }

    public static String getLocalPart(String s) {
        int i = s.indexOf(':');
        if (i == -1)
            return s;
        return s.substring(i + 1);
    }



    public static String getAttributeOrNull(Element e, String name) {
        Attr a = e.getAttributeNode(name);
        if (a == null)
            return null;
        return a.getValue();
    }

    public static String getAttributeNSOrNull(
        Element e,
        String name,
        String nsURI) {
        Attr a = e.getAttributeNodeNS(nsURI, name);
        if (a == null)
            return null;
        return a.getValue();
    }

    public static String getAttributeNSOrNull(
        Element e,
        QName name) {
        Attr a = e.getAttributeNodeNS(name.getNamespaceURI(), name.getLocalPart());
        if (a == null)
            return null;
        return a.getValue();
    }

/*    public static boolean matchesTagNS(Element e, String tag, String nsURI) {
        try {
            return e.getLocalName().equals(tag)
                && e.getNamespaceURI().equals(nsURI);
        } catch (NullPointerException npe) {

            // localname not null since parsing would fail before here
            throw new WSDLParseException(
                "null.namespace.found",
                e.getLocalName());
        }
    }

    public static boolean matchesTagNS(
        Element e,
        javax.xml.namespace.QName name) {
        try {
            return e.getLocalName().equals(name.getLocalPart())
                && e.getNamespaceURI().equals(name.getNamespaceURI());
        } catch (NullPointerException npe) {

            // localname not null since parsing would fail before here
            throw new WSDLParseException(
                "null.namespace.found",
                e.getLocalName());
        }
    }*/

    public static Iterator getAllChildren(Element element) {
        return new NodeListIterator(element.getChildNodes());
    }

    public static Iterator getAllAttributes(Element element) {
        return new NamedNodeMapIterator(element.getAttributes());
    }

    public static List<String> parseTokenList(String tokenList) {
        List<String> result = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(tokenList, " ");
        while (tokenizer.hasMoreTokens()) {
            result.add(tokenizer.nextToken());
        }
        return result;
    }

    public static String getTextForNode(Node node) {
        StringBuilder sb = new StringBuilder();

        NodeList children = node.getChildNodes();
        if (children.getLength() == 0)
            return null;

        for (int i = 0; i < children.getLength(); ++i) {
            Node n = children.item(i);

            if (n instanceof Text)
                sb.append(n.getNodeValue());
            else if (n instanceof EntityReference) {
                String s = getTextForNode(n);
                if (s == null)
                    return null;
                else
                    sb.append(s);
            } else
                return null;
        }

        return sb.toString();
    }

    public static InputStream getUTF8Stream(String s) {
        try {
            ByteArrayBuffer bab = new ByteArrayBuffer();
            Writer w = new OutputStreamWriter(bab, "utf-8");
            w.write(s);
            w.close();
            return bab.newInputStream();
        } catch (IOException e) {
            throw new RuntimeException("should not happen");
        }
    }

    static final ContextClassloaderLocal<TransformerFactory> transformerFactory = new ContextClassloaderLocal<TransformerFactory>() {
        @Override
        protected TransformerFactory initialValue() throws Exception {
            return TransformerFactory.newInstance();
        }
    };

    static final ContextClassloaderLocal<SAXParserFactory> saxParserFactory = new ContextClassloaderLocal<SAXParserFactory>() {
        @Override
        protected SAXParserFactory initialValue() throws Exception {
            SAXParserFactory factory = newSAXParserFactory(true);
            factory.setNamespaceAware(true);
            return factory;
        }
    };

    /**
     * Creates a new identity transformer.
     * @return
     */
    public static Transformer newTransformer() {
        try {
            return transformerFactory.get().newTransformer();
        } catch (TransformerConfigurationException tex) {
            throw new IllegalStateException("Unable to create a JAXP transformer");
        }
    }

    /**
     * Performs identity transformation.
     * @param <T>
     * @param src
     * @param result
     * @return
     * @throws javax.xml.transform.TransformerException
     * @throws java.io.IOException
     * @throws org.xml.sax.SAXException
     * @throws javax.xml.parsers.ParserConfigurationException
     */
    public static <T extends Result> T identityTransform(Source src, T result)
            throws TransformerException, SAXException, ParserConfigurationException, IOException {
        if (src instanceof StreamSource) {
            // work around a bug in JAXP in JDK6u4 and earlier where the namespace processing
            // is not turned on by default
            StreamSource ssrc = (StreamSource) src;
            TransformerHandler th = ((SAXTransformerFactory) transformerFactory.get()).newTransformerHandler();
            th.setResult(result);
            XMLReader reader = saxParserFactory.get().newSAXParser().getXMLReader();
            reader.setContentHandler(th);
            reader.setProperty(LEXICAL_HANDLER_PROPERTY, th);
            reader.parse(toInputSource(ssrc));
        } else {
            newTransformer().transform(src, result);
        }
        return result;
    }

    private static InputSource toInputSource(StreamSource src) {
        InputSource is = new InputSource();
        is.setByteStream(src.getInputStream());
        is.setCharacterStream(src.getReader());
        is.setPublicId(src.getPublicId());
        is.setSystemId(src.getSystemId());
        return is;
    }

    /**
     * Gets an EntityResolver using XML catalog
     *
     * @param catalogUrl
     * @return
     */
    public static EntityResolver createEntityResolver(@Nullable URL catalogUrl) {
        return XmlCatalogUtil.createEntityResolver(catalogUrl);
    }

    /**
     * Gets a default EntityResolver for catalog at META-INF/jaxws-catalog.xml
     *
     * @return
     */
    public static EntityResolver createDefaultCatalogResolver() {
        return XmlCatalogUtil.createDefaultCatalogResolver();
    }

    /**
     * {@link ErrorHandler} that always treat the error as fatal.
     */
    public static final ErrorHandler DRACONIAN_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException exception) {
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    };

    public static DocumentBuilderFactory newDocumentBuilderFactory(boolean disableSecurity) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        String featureToSet = XMLConstants.FEATURE_SECURE_PROCESSING;
        try {
            boolean securityOn = !xmlSecurityDisabled(disableSecurity);
            factory.setFeature(featureToSet, securityOn);
            factory.setNamespaceAware(true);
            if (securityOn) {
               factory.setExpandEntityReferences(false);
               featureToSet = DISALLOW_DOCTYPE_DECL;
               factory.setFeature(featureToSet, true);
               featureToSet = EXTERNAL_GE;
               factory.setFeature(featureToSet, false);
               featureToSet = EXTERNAL_PE;
               factory.setFeature(featureToSet, false);
               featureToSet = LOAD_EXTERNAL_DTD;
               factory.setFeature(featureToSet, false);
            }
        } catch (ParserConfigurationException e) {
            LOGGER.log(Level.WARNING, "Factory [{0}] doesn't support "+featureToSet+" feature!", new Object[] {factory.getClass().getName()} );
        }
        return factory;
    }

    public static TransformerFactory newTransformerFactory(boolean disableSecurity) {
        TransformerFactory factory = TransformerFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, !xmlSecurityDisabled(disableSecurity));
        } catch (TransformerConfigurationException e) {
            LOGGER.log(Level.WARNING, "Factory [{0}] doesn't support secure xml processing!", new Object[]{factory.getClass().getName()});
        }
        return factory;
    }

    public static SAXParserFactory newSAXParserFactory(boolean disableSecurity) {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        String featureToSet = XMLConstants.FEATURE_SECURE_PROCESSING;
        try {
            boolean securityOn = !xmlSecurityDisabled(disableSecurity);
            factory.setFeature(featureToSet, securityOn);
            factory.setNamespaceAware(true);
            if (securityOn) {
                featureToSet = DISALLOW_DOCTYPE_DECL;
                factory.setFeature(featureToSet, true);
                featureToSet = EXTERNAL_GE;
                factory.setFeature(featureToSet, false);
                featureToSet = EXTERNAL_PE;
                factory.setFeature(featureToSet, false);
                featureToSet = LOAD_EXTERNAL_DTD;
                factory.setFeature(featureToSet, false);
            }
        } catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
            LOGGER.log(Level.WARNING, "Factory [{0}] doesn't support "+featureToSet+" feature!", new Object[]{factory.getClass().getName()});
        }
        return factory;
    }

    public static XPathFactory newXPathFactory(boolean disableSecurity) {
        XPathFactory factory = XPathFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, !xmlSecurityDisabled(disableSecurity));
        } catch (XPathFactoryConfigurationException e) {
            LOGGER.log(Level.WARNING, "Factory [{0}] doesn't support secure xml processing!", new Object[] { factory.getClass().getName() } );
        }
        return factory;
    }

    public static XMLInputFactory newXMLInputFactory(boolean disableSecurity)  {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        if (xmlSecurityDisabled(disableSecurity)) {
            // TODO-Miran: are those apppropriate defaults?
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        }
        return factory;
    }

    private static boolean xmlSecurityDisabled(boolean runtimeDisabled) {
        return XML_SECURITY_DISABLED || runtimeDisabled;
    }

    public static SchemaFactory allowExternalAccess(SchemaFactory sf, String value, boolean disableSecurity) {

        // if xml security (feature secure processing) disabled, nothing to do, no restrictions applied
        if (xmlSecurityDisabled(disableSecurity)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Xml Security disabled, no JAXP xsd external access configuration necessary.");
            }
            return sf;
        }

        if (System.getProperty("javax.xml.accessExternalSchema") != null) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Detected explicitly JAXP configuration, no JAXP xsd external access configuration necessary.");
            }
            return sf;
        }

        try {
            sf.setProperty(ACCESS_EXTERNAL_SCHEMA, value);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Property \"{0}\" is supported and has been successfully set by used JAXP implementation.", new Object[]{ACCESS_EXTERNAL_SCHEMA});
            }
        } catch (SAXException ignored) {
            // nothing to do; support depends on version JDK or SAX implementation
            if (LOGGER.isLoggable(Level.CONFIG)) {
                LOGGER.log(Level.CONFIG, "Property \"{0}\" is not supported by used JAXP implementation.", new Object[]{ACCESS_EXTERNAL_SCHEMA});
            }
        }
        return sf;
    }

}
