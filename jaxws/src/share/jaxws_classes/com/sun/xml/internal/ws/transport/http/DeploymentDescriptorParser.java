/*
 * Copyright (c) 1997, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.transport.http;

import com.oracle.webservices.internal.api.databinding.DatabindingModeFeature;
import com.oracle.webservices.internal.api.databinding.ExternalMetadataFeature;
import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;

import com.sun.xml.internal.ws.handler.HandlerChainsModel;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.resources.WsservletMessages;
import com.sun.xml.internal.ws.server.EndpointFactory;
import com.sun.xml.internal.ws.server.ServerRtException;
import com.sun.xml.internal.ws.streaming.Attributes;
import com.sun.xml.internal.ws.streaming.TidyXMLStreamReader;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.exception.LocatableWebServiceException;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.EntityResolver;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.MTOMFeature;
import javax.xml.ws.soap.SOAPBinding;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses {@code sun-jaxws.xml} into {@link WSEndpoint}.
 * <p/>
 * <p/>
 * Since {@code sun-jaxws.xml} captures more information than what {@link WSEndpoint}
 * represents (in particular URL pattern and name), this class
 * takes a parameterization 'A' so that the user of this parser can choose to
 * create another type that wraps {@link WSEndpoint}.
 * <p/>
 * {@link HttpAdapter} and its derived type is used for this often,
 * but it can be anything.
 *
 * @author WS Development Team
 * @author Kohsuke Kawaguchi
 */
public class DeploymentDescriptorParser<A> {

    public static final String NS_RUNTIME = "http://java.sun.com/xml/ns/jax-ws/ri/runtime";
    public static final String JAXWS_WSDL_DD_DIR = "WEB-INF/wsdl";

    public static final QName QNAME_ENDPOINTS = new QName(NS_RUNTIME, "endpoints");
    public static final QName QNAME_ENDPOINT = new QName(NS_RUNTIME, "endpoint");
    public static final QName QNAME_EXT_METADA = new QName(NS_RUNTIME, "external-metadata");

    public static final String ATTR_FILE = "file";
    public static final String ATTR_RESOURCE = "resource";

    public static final String ATTR_VERSION = "version";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_IMPLEMENTATION = "implementation";
    public static final String ATTR_WSDL = "wsdl";
    public static final String ATTR_SERVICE = "service";
    public static final String ATTR_PORT = "port";
    public static final String ATTR_URL_PATTERN = "url-pattern";
    public static final String ATTR_ENABLE_MTOM = "enable-mtom";
    public static final String ATTR_MTOM_THRESHOLD_VALUE = "mtom-threshold-value";
    public static final String ATTR_BINDING = "binding";
    public static final String ATTR_DATABINDING = "databinding";

    public static final List<String> ATTRVALUE_SUPPORTED_VERSIONS = Arrays.asList("2.0", "2.1");

    private static final Logger logger = Logger.getLogger(com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.http");

    private final Container container;
    private final ClassLoader classLoader;
    private final ResourceLoader loader;
    private final AdapterFactory<A> adapterFactory;

    /**
     * Endpoint names that are declared.
     * Used to catch double definitions.
     */
    private final Set<String> names = new HashSet<String>();

    /**
     * WSDL/schema documents collected from /WEB-INF/wsdl. Keyed by the system ID.
     */
    private final Map<String, SDDocumentSource> docs = new HashMap<String, SDDocumentSource>();

    /**
     * @param cl             Used to load service implementations.
     * @param loader         Used to locate resources, in particular WSDL.
     * @param container      Optional {@link Container} that {@link WSEndpoint}s receive.
     * @param adapterFactory Creates {@link HttpAdapter} (or its derived class.)
     */
    public DeploymentDescriptorParser(ClassLoader cl, ResourceLoader loader, Container container,
                                      AdapterFactory<A> adapterFactory) throws MalformedURLException {
        classLoader = cl;
        this.loader = loader;
        this.container = container;
        this.adapterFactory = adapterFactory;

        collectDocs("/WEB-INF/wsdl/");
        logger.log(Level.FINE, "war metadata={0}", docs);
    }

    /**
     * Parses the {@code sun-jaxws.xml} file and configures
     * a set of {@link HttpAdapter}s.
     */
    public @NotNull List<A> parse(String systemId, InputStream is) {
        XMLStreamReader reader = null;
        try {
            reader = new TidyXMLStreamReader(
                    XMLStreamReaderFactory.create(systemId, is, true), is);
            XMLStreamReaderUtil.nextElementContent(reader);
            return parseAdapters(reader);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    throw new ServerRtException("runtime.parser.xmlReader", e);
                }
            }
            try {
                is.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Parses the {@code sun-jaxws.xml} file and configures
     * a set of {@link HttpAdapter}s.
     */
    public @NotNull List<A> parse(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            return parse(f.getPath(), in);
        } finally {
            in.close();
        }
    }

    /**
     * Get all the WSDL & schema documents recursively.
     */
    private void collectDocs(String dirPath) throws MalformedURLException {
        Set<String> paths = loader.getResourcePaths(dirPath);
        if (paths != null) {
            for (String path : paths) {
                if (path.endsWith("/")) {
                    if (path.endsWith("/CVS/") || path.endsWith("/.svn/")) {
                        continue;
                    }
                    collectDocs(path);
                } else {
                    URL res = loader.getResource(path);
                    docs.put(res.toString(), SDDocumentSource.create(res));
                }
            }
        }
    }


    private List<A> parseAdapters(XMLStreamReader reader) {
        if (!reader.getName().equals(QNAME_ENDPOINTS)) {
            failWithFullName("runtime.parser.invalidElement", reader);
        }

        List<A> adapters = new ArrayList<A>();

        Attributes attrs = XMLStreamReaderUtil.getAttributes(reader);
        String version = getMandatoryNonEmptyAttribute(reader, attrs, ATTR_VERSION);
        if (!ATTRVALUE_SUPPORTED_VERSIONS.contains(version)) {
            failWithLocalName("runtime.parser.invalidVersionNumber", reader, version);
        }

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {

            if (reader.getName().equals(QNAME_ENDPOINT)) {
                attrs = XMLStreamReaderUtil.getAttributes(reader);

                String name = getMandatoryNonEmptyAttribute(reader, attrs, ATTR_NAME);
                if (!names.add(name)) {
                    logger.warning(
                            WsservletMessages.SERVLET_WARNING_DUPLICATE_ENDPOINT_NAME(/*name*/));
                }

                String implementationName =
                        getMandatoryNonEmptyAttribute(reader, attrs, ATTR_IMPLEMENTATION);
                Class<?> implementorClass = getImplementorClass(implementationName, reader);

                MetadataReader metadataReader = null;
                ExternalMetadataFeature externalMetadataFeature = null;

                // parse subelements to instantiate externalMetadataReader, if necessary ...
                XMLStreamReaderUtil.nextElementContent(reader);
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                    externalMetadataFeature = configureExternalMetadataReader(reader);
                    if (externalMetadataFeature != null) {
                        metadataReader = externalMetadataFeature.getMetadataReader(implementorClass.getClassLoader(), false);
                    }
                }

                QName serviceName = getQNameAttribute(attrs, ATTR_SERVICE);
                if (serviceName == null) {
                    serviceName = EndpointFactory.getDefaultServiceName(implementorClass, metadataReader);
                }

                QName portName = getQNameAttribute(attrs, ATTR_PORT);
                if (portName == null) {
                    portName = EndpointFactory.getDefaultPortName(serviceName, implementorClass, metadataReader);
                }

                //get enable-mtom attribute value
                String enable_mtom = getAttribute(attrs, ATTR_ENABLE_MTOM);
                String mtomThreshold = getAttribute(attrs, ATTR_MTOM_THRESHOLD_VALUE);
                String dbMode = getAttribute(attrs, ATTR_DATABINDING);
                String bindingId = getAttribute(attrs, ATTR_BINDING);
                if (bindingId != null) {
                    // Convert short-form tokens to API's binding ids
                    bindingId = getBindingIdForToken(bindingId);
                }
                WSBinding binding = createBinding(bindingId, implementorClass, enable_mtom, mtomThreshold, dbMode);
                if (externalMetadataFeature != null) {
                        binding.getFeatures().mergeFeatures(new WebServiceFeature[]{externalMetadataFeature},
                        true);
                }

                String urlPattern = getMandatoryNonEmptyAttribute(reader, attrs, ATTR_URL_PATTERN);

                // TODO use 'docs' as the metadata. If wsdl is non-null it's the primary.
                boolean handlersSetInDD = setHandlersAndRoles(binding, reader, serviceName, portName);

                EndpointFactory.verifyImplementorClass(implementorClass, metadataReader);
                SDDocumentSource primaryWSDL = getPrimaryWSDL(reader, attrs, implementorClass, metadataReader);

                WSEndpoint<?> endpoint = WSEndpoint.create(
                        implementorClass, !handlersSetInDD,
                        null,
                        serviceName, portName, container, binding,
                        primaryWSDL, docs.values(), createEntityResolver(), false
                );
                adapters.add(adapterFactory.createAdapter(name, urlPattern, endpoint));
            } else {
                failWithLocalName("runtime.parser.invalidElement", reader);
            }
        }
        return adapters;
    }

    /**
     * @param ddBindingId   binding id explicitlyspecified in the DeploymentDescriptor or parameter
     * @param implClass     Endpoint Implementation class
     * @param mtomEnabled   represents mtom-enabled attribute in DD
     * @param mtomThreshold threshold value specified in DD
     * @return is returned with only MTOMFeature set resolving the various precendece rules
     */
    private static WSBinding createBinding(String ddBindingId, Class implClass,
                                           String mtomEnabled, String mtomThreshold, String dataBindingMode) {
        // Features specified through DD
        WebServiceFeatureList features;

        MTOMFeature mtomfeature = null;
        if (mtomEnabled != null) {
            if (mtomThreshold != null) {
                mtomfeature = new MTOMFeature(Boolean.valueOf(mtomEnabled),
                        Integer.valueOf(mtomThreshold));
            } else {
                mtomfeature = new MTOMFeature(Boolean.valueOf(mtomEnabled));
            }
        }

        BindingID bindingID;
        if (ddBindingId != null) {
            bindingID = BindingID.parse(ddBindingId);
            features = bindingID.createBuiltinFeatureList();

            if (checkMtomConflict(features.get(MTOMFeature.class), mtomfeature)) {
                throw new ServerRtException(ServerMessages.DD_MTOM_CONFLICT(ddBindingId, mtomEnabled));
            }
        } else {
            bindingID = BindingID.parse(implClass);
            // Since bindingID is coming from implclass,
            // mtom through Feature annotation or DD takes precendece

            features = new WebServiceFeatureList();
            if (mtomfeature != null) {  // this wins over MTOM setting in bindingID
                features.add(mtomfeature);
            }
            features.addAll(bindingID.createBuiltinFeatureList());
        }

        if (dataBindingMode != null) {
            features.add(new DatabindingModeFeature(dataBindingMode));
        }

        return bindingID.createBinding(features.toArray());
    }

    private static boolean checkMtomConflict(MTOMFeature lhs, MTOMFeature rhs) {
        if (lhs == null || rhs == null) {
            return false;
        }
        return lhs.isEnabled() ^ rhs.isEnabled();
    }

    /**
     * JSR-109 defines short-form tokens for standard binding Ids. These are
     * used only in DD. So stand alone deployment descirptor should also honor
     * these tokens. This method converts the tokens to API's standard
     * binding ids
     *
     * @param lexical binding attribute value from DD. Always not null
     * @return returns corresponding API's binding ID or the same lexical
     */
    public static @NotNull String getBindingIdForToken(@NotNull String lexical) {
        if (lexical.equals("##SOAP11_HTTP")) {
            return SOAPBinding.SOAP11HTTP_BINDING;
        } else if (lexical.equals("##SOAP11_HTTP_MTOM")) {
            return SOAPBinding.SOAP11HTTP_MTOM_BINDING;
        } else if (lexical.equals("##SOAP12_HTTP")) {
            return SOAPBinding.SOAP12HTTP_BINDING;
        } else if (lexical.equals("##SOAP12_HTTP_MTOM")) {
            return SOAPBinding.SOAP12HTTP_MTOM_BINDING;
        } else if (lexical.equals("##XML_HTTP")) {
            return HTTPBinding.HTTP_BINDING;
        }
        return lexical;
    }

    /**
     * Creates a new "Adapter".
     * <p/>
     * Normally 'A' would be {@link HttpAdapter} or some derived class.
     * But the parser doesn't require that to be of any particular type.
     */
    public static interface AdapterFactory<A> {
        A createAdapter(String name, String urlPattern, WSEndpoint<?> endpoint);
    }

    /**
     * Checks the deployment descriptor or {@link @WebServiceProvider} annotation
     * to see if it points to any WSDL. If so, returns the {@link SDDocumentSource}.
     *
     * @return The pointed WSDL, if any. Otherwise null.
     */
    private SDDocumentSource getPrimaryWSDL(XMLStreamReader xsr, Attributes attrs, Class<?> implementorClass, MetadataReader metadataReader) {

        String wsdlFile = getAttribute(attrs, ATTR_WSDL);
        if (wsdlFile == null) {
            wsdlFile = EndpointFactory.getWsdlLocation(implementorClass, metadataReader);
        }

        if (wsdlFile != null) {
            if (!wsdlFile.startsWith(JAXWS_WSDL_DD_DIR)) {
                logger.log(Level.WARNING, "Ignoring wrong wsdl={0}. It should start with {1}. Going to generate and publish a new WSDL.", new Object[]{wsdlFile, JAXWS_WSDL_DD_DIR});
                return null;
            }

            URL wsdl;
            try {
                wsdl = loader.getResource('/' + wsdlFile);
            } catch (MalformedURLException e) {
                throw new LocatableWebServiceException(
                        ServerMessages.RUNTIME_PARSER_WSDL_NOT_FOUND(wsdlFile), e, xsr);
            }
            if (wsdl == null) {
                throw new LocatableWebServiceException(
                        ServerMessages.RUNTIME_PARSER_WSDL_NOT_FOUND(wsdlFile), xsr);
            }
            SDDocumentSource docInfo = docs.get(wsdl.toExternalForm());
            assert docInfo != null;
            return docInfo;
        }

        return null;
    }

    /**
     * Creates an {@link EntityResolver} that consults {@code /WEB-INF/jax-ws-catalog.xml}.
     */
    private EntityResolver createEntityResolver() {
        try {
            return XmlUtil.createEntityResolver(loader.getCatalogFile());
        } catch (MalformedURLException e) {
            throw new WebServiceException(e);
        }
    }

    protected String getAttribute(Attributes attrs, String name) {
        String value = attrs.getValue(name);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }

    protected QName getQNameAttribute(Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value == null || value.equals("")) {
            return null;
        } else {
            return QName.valueOf(value);
        }
    }

    protected String getNonEmptyAttribute(XMLStreamReader reader, Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value != null && value.equals("")) {
            failWithLocalName(
                    "runtime.parser.invalidAttributeValue",
                    reader,
                    name);
        }
        return value;
    }

    protected String getMandatoryAttribute(XMLStreamReader reader, Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value == null) {
            failWithLocalName("runtime.parser.missing.attribute", reader, name);
        }
        return value;
    }

    protected String getMandatoryNonEmptyAttribute(XMLStreamReader reader, Attributes attributes,
                                                   String name) {
        String value = getAttribute(attributes, name);
        if (value == null) {
            failWithLocalName("runtime.parser.missing.attribute", reader, name);
        } else if (value.equals("")) {
            failWithLocalName(
                    "runtime.parser.invalidAttributeValue",
                    reader,
                    name);
        }
        return value;
    }

    /**
     * Parses the handler and role information and sets it
     * on the {@link WSBinding}.
     *
     * @return true if <handler-chains> element present in DD
     *         false otherwise.
     */
    protected boolean setHandlersAndRoles(WSBinding binding, XMLStreamReader reader, QName serviceName, QName portName) {

        if (reader.getEventType() == XMLStreamConstants.END_ELEMENT ||
                !reader.getName().equals(HandlerChainsModel.QNAME_HANDLER_CHAINS)) {
            return false;
        }

        HandlerAnnotationInfo handlerInfo = HandlerChainsModel.parseHandlerFile(
                reader, classLoader, serviceName, portName, binding);

        binding.setHandlerChain(handlerInfo.getHandlers());
        if (binding instanceof SOAPBinding) {
            ((SOAPBinding) binding).setRoles(handlerInfo.getRoles());
        }

        // move past </handler-chains>
        XMLStreamReaderUtil.nextContent(reader);
        return true;
    }

    protected ExternalMetadataFeature configureExternalMetadataReader(XMLStreamReader reader) {

        ExternalMetadataFeature.Builder featureBuilder = null;
        while (QNAME_EXT_METADA.equals(reader.getName())) {

            if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
                Attributes attrs = XMLStreamReaderUtil.getAttributes(reader);
                String file = getAttribute(attrs, ATTR_FILE);
                if (file != null) {
                    if (featureBuilder == null) {
                        featureBuilder = ExternalMetadataFeature.builder();
                    }
                    featureBuilder.addFiles(new File(file));
                }

                String res = getAttribute(attrs, ATTR_RESOURCE);
                if (res != null) {
                    if (featureBuilder == null) {
                        featureBuilder = ExternalMetadataFeature.builder();
                    }
                    featureBuilder.addResources(res);
                }
            }

            XMLStreamReaderUtil.nextElementContent(reader);
        }

        return buildFeature(featureBuilder);
    }

    private ExternalMetadataFeature buildFeature(ExternalMetadataFeature.Builder builder) {
        return builder != null ? builder.build() : null;
    }

    protected static void fail(String key, XMLStreamReader reader) {
        logger.log(Level.SEVERE, "{0}{1}", new Object[]{key, reader.getLocation().getLineNumber()});
        throw new ServerRtException(
                key,
                Integer.toString(reader.getLocation().getLineNumber()));
    }

    protected static void failWithFullName(String key, XMLStreamReader reader) {
        throw new ServerRtException(
                key,
                reader.getLocation().getLineNumber(),
                reader.getName());
    }

    protected static void failWithLocalName(String key, XMLStreamReader reader) {
        throw new ServerRtException(
                key,
                reader.getLocation().getLineNumber(),
                reader.getLocalName());
    }

    protected static void failWithLocalName(String key, XMLStreamReader reader, String arg) {
        throw new ServerRtException(
                key,
                reader.getLocation().getLineNumber(),
                reader.getLocalName(),
                arg);
    }

    protected Class loadClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                    "runtime.parser.classNotFound",
                    name);
        }
    }


    /**
     * Loads the class of the given name.
     *
     * @param xsr Used to report the source location information if there's any error.
     */
    private Class getImplementorClass(String name, XMLStreamReader xsr) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new LocatableWebServiceException(
                    ServerMessages.RUNTIME_PARSER_CLASS_NOT_FOUND(name), e, xsr);
        }
    }

}
