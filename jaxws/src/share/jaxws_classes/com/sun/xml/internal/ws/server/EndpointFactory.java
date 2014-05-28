/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSFeatureList;
import com.sun.xml.internal.ws.api.databinding.DatabindingConfig;
import com.sun.xml.internal.ws.api.databinding.DatabindingFactory;
import com.sun.xml.internal.ws.api.databinding.MetadataReader;
import com.sun.xml.internal.ws.api.databinding.WSDLGenInfo;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLService;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;
import com.sun.xml.internal.ws.api.policy.PolicyResolverFactory;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.api.server.InstanceResolver;
import com.sun.xml.internal.ws.api.server.Invoker;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver.Parser;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.ReflectAnnotationReader;
import com.sun.xml.internal.ws.model.RuntimeModeler;
import com.sun.xml.internal.ws.model.SOAPSEIModel;
import com.sun.xml.internal.ws.policy.PolicyMap;
import com.sun.xml.internal.ws.policy.jaxws.PolicyUtil;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.server.provider.ProviderInvokerTube;
import com.sun.xml.internal.ws.server.sei.SEIInvokerTube;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;
import com.sun.xml.internal.ws.util.ServiceConfigurationError;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.wsdl.parser.RuntimeWSDLParser;

import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.soap.SOAPBinding;

import java.io.IOException;
import java.net.URL;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Entry point to the JAX-WS RI server-side runtime.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class EndpointFactory {
        private static final EndpointFactory instance = new EndpointFactory();

        public static EndpointFactory getInstance() {
                return instance;
        }

    /**
     * Implements {@link WSEndpoint#create}.
     *
     * No need to take WebServiceContext implementation. When InvokerPipe is
     * instantiated, it calls InstanceResolver to set up a WebServiceContext.
     * We shall only take delegate to getUserPrincipal and isUserInRole from adapter.
     *
     * <p>
     * Nobody else should be calling this method.
     */
    public static <T> WSEndpoint<T> createEndpoint(
        Class<T> implType, boolean processHandlerAnnotation, @Nullable Invoker invoker,
        @Nullable QName serviceName, @Nullable QName portName,
        @Nullable Container container, @Nullable WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        EntityResolver resolver, boolean isTransportSynchronous) {
        return createEndpoint(implType, processHandlerAnnotation, invoker, serviceName,
                        portName, container, binding, primaryWsdl, metadata, resolver, isTransportSynchronous, true);
    }

    public static <T> WSEndpoint<T> createEndpoint(
            Class<T> implType, boolean processHandlerAnnotation, @Nullable Invoker invoker,
            @Nullable QName serviceName, @Nullable QName portName,
            @Nullable Container container, @Nullable WSBinding binding,
            @Nullable SDDocumentSource primaryWsdl,
            @Nullable Collection<? extends SDDocumentSource> metadata,
            EntityResolver resolver, boolean isTransportSynchronous, boolean isStandard) {
        EndpointFactory factory = container != null ? container.getSPI(EndpointFactory.class) : null;
        if (factory == null)
                factory = EndpointFactory.getInstance();

        return factory.create(
                implType,processHandlerAnnotation, invoker,serviceName,portName,container,binding,primaryWsdl,metadata,resolver,isTransportSynchronous,isStandard);
    }

    /**
     * Implements {@link WSEndpoint#create}.
     *
     * No need to take WebServiceContext implementation. When InvokerPipe is
     * instantiated, it calls InstanceResolver to set up a WebServiceContext.
     * We shall only take delegate to getUserPrincipal and isUserInRole from adapter.
     *
     * <p>
     * Nobody else should be calling this method.
     */
    public <T> WSEndpoint<T> create(
            Class<T> implType, boolean processHandlerAnnotation, @Nullable Invoker invoker,
            @Nullable QName serviceName, @Nullable QName portName,
            @Nullable Container container, @Nullable WSBinding binding,
            @Nullable SDDocumentSource primaryWsdl,
            @Nullable Collection<? extends SDDocumentSource> metadata,
            EntityResolver resolver, boolean isTransportSynchronous) {
        return create(implType, processHandlerAnnotation, invoker, serviceName,
                        portName, container, binding, primaryWsdl, metadata, resolver, isTransportSynchronous,
                        true);

    }

    public <T> WSEndpoint<T> create(
        Class<T> implType, boolean processHandlerAnnotation, @Nullable Invoker invoker,
        @Nullable QName serviceName, @Nullable QName portName,
        @Nullable Container container, @Nullable WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        EntityResolver resolver, boolean isTransportSynchronous, boolean isStandard) {

        if(implType ==null)
            throw new IllegalArgumentException();

        MetadataReader metadataReader = getExternalMetadatReader(implType, binding);

        if (isStandard) {
            verifyImplementorClass(implType, metadataReader);
        }

        if (invoker == null) {
            invoker = InstanceResolver.createDefault(implType).createInvoker();
        }

        // Performance analysis indicates that reading and parsing imported schemas is
        // a major component of Endpoint creation time.  Therefore, modify SDDocumentSource
        // handling to delay iterating collection as long as possible.
        Collection<SDDocumentSource> md = new CollectionCollection<SDDocumentSource>();
        if(primaryWsdl!=null) {
            if(metadata!=null) {
                Iterator<? extends SDDocumentSource> it = metadata.iterator();
                if (it.hasNext() && primaryWsdl.equals(it.next()))
                    md.addAll(metadata);
                else {
                    md.add(primaryWsdl);
                    md.addAll(metadata);
                }
            } else
                md.add(primaryWsdl);
        } else if(metadata!=null)
            md.addAll(metadata);

        if(container==null)
            container = ContainerResolver.getInstance().getContainer();

        if(serviceName==null)
            serviceName = getDefaultServiceName(implType, metadataReader);

        if(portName==null)
            portName = getDefaultPortName(serviceName,implType, metadataReader);

        {// error check
            String serviceNS = serviceName.getNamespaceURI();
            String portNS = portName.getNamespaceURI();
            if (!serviceNS.equals(portNS)) {
                throw new ServerRtException("wrong.tns.for.port",portNS, serviceNS);
            }
        }

        // setting a default binding
        if (binding == null)
            binding = BindingImpl.create(BindingID.parse(implType));

        if ( isStandard && primaryWsdl != null) {
            verifyPrimaryWSDL(primaryWsdl, serviceName);
        }

        QName portTypeName = null;
        if (isStandard && implType.getAnnotation(WebServiceProvider.class)==null) {
            portTypeName = RuntimeModeler.getPortTypeName(implType, metadataReader);
        }

        // Categorises the documents as WSDL, Schema etc
        Collection<SDDocumentImpl> docList = categoriseMetadata(md.iterator(), serviceName, portTypeName);
        // Finds the primary WSDL and makes sure that metadata doesn't have
        // two concrete or abstract WSDLs
        SDDocumentImpl primaryDoc = primaryWsdl != null ? SDDocumentImpl.create(primaryWsdl,serviceName,portTypeName) : findPrimary(docList);

        EndpointAwareTube terminal;
        WSDLPort wsdlPort = null;
        AbstractSEIModelImpl seiModel = null;
        // create WSDL model
        if (primaryDoc != null) {
            wsdlPort = getWSDLPort(primaryDoc, docList, serviceName, portName, container, resolver);
        }

        WebServiceFeatureList features=((BindingImpl)binding).getFeatures();
        if (isStandard) {
                features.parseAnnotations(implType);
        }
        PolicyMap policyMap = null;
        // create terminal pipe that invokes the application
        if (isUseProviderTube(implType, isStandard)) {
            //TODO incase of Provider, provide a way to User for complete control of the message processing by giving
            // ability to turn off the WSDL/Policy based features and its associated tubes.

            //Even in case of Provider, merge all features configured via WSDL/Policy or deployment configuration
            Iterable<WebServiceFeature> configFtrs;
            if(wsdlPort != null) {
                 policyMap = wsdlPort.getOwner().getParent().getPolicyMap();
                 //Merge features from WSDL and other policy configuration
                configFtrs = wsdlPort.getFeatures();
            } else {
                //No WSDL, so try to merge features from Policy configuration
                policyMap = PolicyResolverFactory.create().resolve(
                        new PolicyResolver.ServerContext(null, container, implType, false));
                configFtrs = PolicyUtil.getPortScopedFeatures(policyMap,serviceName,portName);
            }
            features.mergeFeatures(configFtrs, true);
            terminal = createProviderInvokerTube(implType, binding, invoker, container);
        } else {
            // Create runtime model for non Provider endpoints
            seiModel = createSEIModel(wsdlPort, implType, serviceName, portName, binding, primaryDoc);
            if(binding instanceof SOAPBindingImpl){
                //set portKnownHeaders on Binding, so that they can be used for MU processing
                ((SOAPBindingImpl)binding).setPortKnownHeaders(
                        ((SOAPSEIModel)seiModel).getKnownHeaders());
            }
            // Generate WSDL for SEI endpoints(not for Provider endpoints)
            if (primaryDoc == null) {
                primaryDoc = generateWSDL(binding, seiModel, docList, container, implType);
                // create WSDL model
                wsdlPort = getWSDLPort(primaryDoc, docList, serviceName, portName, container, resolver);
                seiModel.freeze(wsdlPort);
            }
            policyMap = wsdlPort.getOwner().getParent().getPolicyMap();
            // New Features might have been added in WSDL through Policy.
            //Merge features from WSDL and other policy configuration
            // This sets only the wsdl features that are not already set(enabled/disabled)
            features.mergeFeatures(wsdlPort.getFeatures(), true);
            terminal = createSEIInvokerTube(seiModel,invoker,binding);
        }

        // Process @HandlerChain, if handler-chain is not set via Deployment Descriptor
        if (processHandlerAnnotation) {
            processHandlerAnnotation(binding, implType, serviceName, portName);
        }
        // Selects only required metadata for this endpoint from the passed-in metadata
        if (primaryDoc != null) {
            docList = findMetadataClosure(primaryDoc, docList, resolver);
        }

        ServiceDefinitionImpl serviceDefiniton = (primaryDoc != null) ? new ServiceDefinitionImpl(docList, primaryDoc) : null;

        return create(serviceName, portName, binding, container, seiModel, wsdlPort, implType, serviceDefiniton,
                        terminal, isTransportSynchronous, policyMap);
    }

    protected <T> WSEndpoint<T> create(QName serviceName, QName portName, WSBinding binding, Container container, SEIModel seiModel, WSDLPort wsdlPort, Class<T> implType, ServiceDefinitionImpl serviceDefinition, EndpointAwareTube terminal, boolean isTransportSynchronous, PolicyMap policyMap) {
        return new WSEndpointImpl<T>(serviceName, portName, binding, container, seiModel,
                        wsdlPort, implType, serviceDefinition, terminal, isTransportSynchronous, policyMap);
    }

    protected boolean isUseProviderTube(Class<?> implType, boolean isStandard) {
        return !isStandard || implType.getAnnotation(WebServiceProvider.class)!=null;
    }

    protected EndpointAwareTube createSEIInvokerTube(AbstractSEIModelImpl seiModel, Invoker invoker, WSBinding binding) {
        return new SEIInvokerTube(seiModel,invoker,binding);
    }

    protected <T> EndpointAwareTube createProviderInvokerTube(final Class<T> implType, final WSBinding binding,
                                                              final Invoker invoker, final Container container) {
        return ProviderInvokerTube.create(implType, binding, invoker, container);
    }
    /**
     * Goes through the original metadata documents and collects the required ones.
     * This done traversing from primary WSDL and its imports until it builds a
     * complete set of documents(transitive closure) for the endpoint.
     *
     * @param primaryDoc primary WSDL doc
     * @param docList complete metadata
     * @return new metadata that doesn't contain extraneous documents.
     */
    private static Collection<SDDocumentImpl> findMetadataClosure(
            final SDDocumentImpl primaryDoc, final Collection<SDDocumentImpl> docList, final EntityResolver resolver) {
        return new AbstractCollection<SDDocumentImpl>() {
            @Override
            public Iterator<SDDocumentImpl> iterator() {
                // create a map for old metadata
                Map<String, SDDocumentImpl> oldMap = new HashMap<String, SDDocumentImpl>();
                Iterator<SDDocumentImpl> oldDocs = docList.iterator();

                // create a map for new metadata
                Map<String, SDDocumentImpl> newMap = new HashMap<String, SDDocumentImpl>();
                newMap.put(primaryDoc.getSystemId().toString(), primaryDoc);

                List<String> remaining = new ArrayList<String>();
                remaining.addAll(primaryDoc.getImports());
                while(!remaining.isEmpty()) {
                    String url = remaining.remove(0);
                    SDDocumentImpl doc = oldMap.get(url);
                    if (doc == null) {
                        while (oldDocs.hasNext()) {
                            SDDocumentImpl old = oldDocs.next();
                            String id = old.getSystemId().toString();
                            oldMap.put(id, old);
                            if (id.equals(url)) {
                                doc = old;
                                break;
                            }
                        }

                        if (doc == null) {
                            // old metadata doesn't have this imported doc, may be external
                                if (resolver != null) {
                                        try {
                                                InputSource source = resolver.resolveEntity(null, url);
                                                if (source != null) {
                                                        MutableXMLStreamBuffer xsb = new MutableXMLStreamBuffer();
                                                        XMLStreamReader reader = XmlUtil.newXMLInputFactory(true).createXMLStreamReader(source.getByteStream());
                                                        xsb.createFromXMLStreamReader(reader);

                                                        SDDocumentSource sdocSource = SDDocumentImpl.create(new URL(url), xsb);
                                                        doc = SDDocumentImpl.create(sdocSource, null, null);
                                                }
                                        } catch (Exception ex) {
                                                ex.printStackTrace();
                                        }
                                }
                        }
                    }
                    // Check if new metadata already contains this doc
                    if (doc != null && !newMap.containsKey(url)) {
                        newMap.put(url, doc);
                        remaining.addAll(doc.getImports());
                    }
                }

                return newMap.values().iterator();
            }

            @Override
            public int size() {
                int size = 0;
                Iterator<SDDocumentImpl> it = iterator();
                while (it.hasNext()) {
                    it.next();
                    size++;
                }
                return size;
            }

            @Override
            public void clear() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isEmpty() {
                return docList.isEmpty();
            }
        };
    }

    private static <T> void processHandlerAnnotation(WSBinding binding, Class<T> implType, QName serviceName, QName portName) {
        HandlerAnnotationInfo chainInfo =
                HandlerAnnotationProcessor.buildHandlerInfo(
                        implType, serviceName, portName, binding);
        if (chainInfo != null) {
            binding.setHandlerChain(chainInfo.getHandlers());
            if (binding instanceof SOAPBinding) {
                ((SOAPBinding) binding).setRoles(chainInfo.getRoles());
            }
        }

    }

    /**
     * Verifies if the endpoint implementor class has @WebService or @WebServiceProvider
     * annotation
     *
     * @return
     *       true if it is a Provider or AsyncProvider endpoint
     *       false otherwise
     * @throws java.lang.IllegalArgumentException
     *      If it doesn't have any one of @WebService or @WebServiceProvider
     *      If it has both @WebService and @WebServiceProvider annotations
     */
    public static boolean verifyImplementorClass(Class<?> clz) {
        return verifyImplementorClass(clz, null);
    }

    /**
     * Verifies if the endpoint implementor class has @WebService or @WebServiceProvider
     * annotation; passing MetadataReader instance allows to read annotations from
     * xml descriptor instead of class's annotations
     *
     * @return
     *       true if it is a Provider or AsyncProvider endpoint
     *       false otherwise
     * @throws java.lang.IllegalArgumentException
     *      If it doesn't have any one of @WebService or @WebServiceProvider
     *      If it has both @WebService and @WebServiceProvider annotations
     */
    public static boolean verifyImplementorClass(Class<?> clz, MetadataReader metadataReader) {

        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

        WebServiceProvider wsProvider = metadataReader.getAnnotation(WebServiceProvider.class, clz);
        WebService ws = metadataReader.getAnnotation(WebService.class, clz);
        if (wsProvider == null && ws == null) {
            throw new IllegalArgumentException(clz +" has neither @WebService nor @WebServiceProvider annotation");
        }
        if (wsProvider != null && ws != null) {
            throw new IllegalArgumentException(clz +" has both @WebService and @WebServiceProvider annotations");
        }
        if (wsProvider != null) {
            if (Provider.class.isAssignableFrom(clz) || AsyncProvider.class.isAssignableFrom(clz)) {
                return true;
            }
            throw new IllegalArgumentException(clz +" doesn't implement Provider or AsyncProvider interface");
        }
        return false;
    }


    private static AbstractSEIModelImpl createSEIModel(WSDLPort wsdlPort,
                                                       Class<?> implType, @NotNull QName serviceName, @NotNull QName portName, WSBinding binding,
                                                       SDDocumentSource primaryWsdl) {
                DatabindingFactory fac = DatabindingFactory.newInstance();
                DatabindingConfig config = new DatabindingConfig();
                config.setEndpointClass(implType);
                config.getMappingInfo().setServiceName(serviceName);
                config.setWsdlPort(wsdlPort);
                config.setWSBinding(binding);
                config.setClassLoader(implType.getClassLoader());
                config.getMappingInfo().setPortName(portName);
                if (primaryWsdl != null) config.setWsdlURL(primaryWsdl.getSystemId());
        config.setMetadataReader(getExternalMetadatReader(implType, binding));

                com.sun.xml.internal.ws.db.DatabindingImpl rt = (com.sun.xml.internal.ws.db.DatabindingImpl)fac.createRuntime(config);
                return (AbstractSEIModelImpl) rt.getModel();
    }

    public static MetadataReader getExternalMetadatReader(Class<?> implType, WSBinding binding) {
        com.oracle.webservices.internal.api.databinding.ExternalMetadataFeature ef = binding.getFeature(
                com.oracle.webservices.internal.api.databinding.ExternalMetadataFeature.class);
        // TODO-Miran: would it be necessary to disable secure xml processing?
        if (ef != null)
            return ef.getMetadataReader(implType.getClassLoader(), false);
        return null;
    }

    /**
     *Set the mtom enable setting from wsdl model (mtom policy assertion) on to @link WSBinding} if DD has
     * not already set it on BindingID. Also check conflicts.
     */
    /*
    private static void applyEffectiveMtomSetting(WSDLBoundPortType wsdlBinding, WSBinding binding){
        if(wsdlBinding.isMTOMEnabled()){
            BindingID bindingId = binding.getBindingId();
            if(bindingId.isMTOMEnabled() == null){
                binding.setMTOMEnabled(true);
            }else if (bindingId.isMTOMEnabled() != null && bindingId.isMTOMEnabled() == Boolean.FALSE){
                //TODO: i18N
                throw new ServerRtException("Deployment failed! Mtom policy assertion in WSDL is enabled whereas the deplyment descriptor setting wants to disable it!");
            }
        }
    }
    */
    /**
     * If service name is not already set via DD or programmatically, it uses
     * annotations {@link WebServiceProvider}, {@link WebService} on implementorClass to get PortName.
     *
     * @return non-null service name
     */
    public static @NotNull QName getDefaultServiceName(Class<?> implType) {
        return getDefaultServiceName(implType, null);
    }

    public static @NotNull QName getDefaultServiceName(Class<?> implType, MetadataReader metadataReader) {
        return getDefaultServiceName(implType, true, metadataReader);
    }

    public static @NotNull QName getDefaultServiceName(Class<?> implType, boolean isStandard) {
        return getDefaultServiceName(implType, isStandard, null);
    }

    public static @NotNull QName getDefaultServiceName(Class<?> implType, boolean isStandard, MetadataReader metadataReader) {
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }
        QName serviceName;
        WebServiceProvider wsProvider = metadataReader.getAnnotation(WebServiceProvider.class, implType);
        if (wsProvider!=null) {
            String tns = wsProvider.targetNamespace();
            String local = wsProvider.serviceName();
            serviceName = new QName(tns, local);
        } else {
            serviceName = RuntimeModeler.getServiceName(implType, metadataReader, isStandard);
        }
        assert serviceName != null;
        return serviceName;
    }

    /**
     * If portName is not already set via DD or programmatically, it uses
     * annotations on implementorClass to get PortName.
     *
     * @return non-null port name
     */
    public static @NotNull QName getDefaultPortName(QName serviceName, Class<?> implType) {
        return getDefaultPortName(serviceName, implType, null);
    }

    public static @NotNull QName getDefaultPortName(QName serviceName, Class<?> implType, MetadataReader metadataReader) {
        return getDefaultPortName(serviceName, implType, true, metadataReader);
    }

    public static @NotNull QName getDefaultPortName(QName serviceName, Class<?> implType, boolean isStandard) {
        return getDefaultPortName(serviceName, implType, isStandard, null);
    }

    public static @NotNull QName getDefaultPortName(QName serviceName, Class<?> implType, boolean isStandard, MetadataReader metadataReader) {
        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }
        QName portName;
        WebServiceProvider wsProvider = metadataReader.getAnnotation(WebServiceProvider.class, implType);
        if (wsProvider!=null) {
            String tns = wsProvider.targetNamespace();
            String local = wsProvider.portName();
            portName = new QName(tns, local);
        } else {
            portName = RuntimeModeler.getPortName(implType, metadataReader, serviceName.getNamespaceURI(), isStandard);
        }
        assert portName != null;
        return portName;
    }

    /**
     * Returns the wsdl from @WebService, or @WebServiceProvider annotation using
     * wsdlLocation element.
     *
     * @param implType
     *      endpoint implementation class
     *      make sure that you called {@link #verifyImplementorClass} on it.
     * @return wsdl if there is wsdlLocation, else null
     */
    public static @Nullable String getWsdlLocation(Class<?> implType) {
        return getWsdlLocation(implType, new ReflectAnnotationReader());
    }

    /**
     * Returns the wsdl from @WebService, or @WebServiceProvider annotation using
     * wsdlLocation element.
     *
     * @param implType
     *      endpoint implementation class
     *      make sure that you called {@link #verifyImplementorClass} on it.
     * @return wsdl if there is wsdlLocation, else null
     */
    public static @Nullable String getWsdlLocation(Class<?> implType, MetadataReader metadataReader) {

        if (metadataReader == null) {
            metadataReader = new ReflectAnnotationReader();
        }

        WebService ws = metadataReader.getAnnotation(WebService.class, implType);
        if (ws != null) {
            return nullIfEmpty(ws.wsdlLocation());
        } else {
            WebServiceProvider wsProvider = implType.getAnnotation(WebServiceProvider.class);
            assert wsProvider != null;
            return nullIfEmpty(wsProvider.wsdlLocation());
        }
    }

    private static String nullIfEmpty(String string) {
        if (string.length() < 1) {
            string = null;
        }
        return string;
    }

    /**
     * Generates the WSDL and XML Schema for the endpoint if necessary
     * It generates WSDL only for SOAP1.1, and for XSOAP1.2 bindings
     */
    private static SDDocumentImpl generateWSDL(WSBinding binding, AbstractSEIModelImpl seiModel, Collection<SDDocumentImpl> docs,
                                               Container container, Class implType) {
        BindingID bindingId = binding.getBindingId();
        if (!bindingId.canGenerateWSDL()) {
            throw new ServerRtException("can.not.generate.wsdl", bindingId);
        }

        if (bindingId.toString().equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
            String msg = ServerMessages.GENERATE_NON_STANDARD_WSDL();
            logger.warning(msg);
        }

        // Generate WSDL and schema documents using runtime model
        WSDLGenResolver wsdlResolver = new WSDLGenResolver(docs,seiModel.getServiceQName(),seiModel.getPortTypeName());
        WSDLGenInfo wsdlGenInfo = new WSDLGenInfo();
        wsdlGenInfo.setWsdlResolver(wsdlResolver);
        wsdlGenInfo.setContainer(container);
        wsdlGenInfo.setExtensions(ServiceFinder.find(WSDLGeneratorExtension.class).toArray());
        wsdlGenInfo.setInlineSchemas(false);
        wsdlGenInfo.setSecureXmlProcessingDisabled(isSecureXmlProcessingDisabled(binding.getFeatures()));
        seiModel.getDatabinding().generateWSDL(wsdlGenInfo);
//        WSDLGenerator wsdlGen = new WSDLGenerator(seiModel, wsdlResolver, binding, container, implType, false,
//                ServiceFinder.find(WSDLGeneratorExtension.class).toArray());
//        wsdlGen.doGeneration();
        return wsdlResolver.updateDocs();
    }

    private static boolean isSecureXmlProcessingDisabled(WSFeatureList featureList) {
        // TODO-Miran: would it be necessary to disable secure xml processing?
        return false;
    }

    /**
     * Builds {@link SDDocumentImpl} from {@link SDDocumentSource}.
     */
    private static Collection<SDDocumentImpl> categoriseMetadata(
        final Iterator<SDDocumentSource> src, final QName serviceName, final QName portTypeName) {

        return new AbstractCollection<SDDocumentImpl>() {
            private final Collection<SDDocumentImpl> theConverted = new ArrayList<SDDocumentImpl>();

            @Override
            public boolean add(SDDocumentImpl arg0) {
                return theConverted.add(arg0);
            }

            @Override
            public Iterator<SDDocumentImpl> iterator() {
                return new Iterator<SDDocumentImpl>() {
                    private Iterator<SDDocumentImpl> convIt = theConverted.iterator();
                    @Override
                    public boolean hasNext() {
                        if (convIt != null && convIt.hasNext())
                            return true;
                        return src.hasNext();
                    }

                    @Override
                    public SDDocumentImpl next() {
                        if (convIt != null && convIt.hasNext())
                            return convIt.next();
                        convIt = null;
                        if (!src.hasNext())
                            throw new NoSuchElementException();
                        SDDocumentImpl next = SDDocumentImpl.create(src.next(),serviceName,portTypeName);
                        theConverted.add(next);
                        return next;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isEmpty() {
                if (!theConverted.isEmpty())
                    return false;
                return !src.hasNext();
            }
        };
    }

    /**
     * Verifies whether the given primaryWsdl contains the given serviceName.
     * If the WSDL doesn't have the service, it throws an WebServiceException.
     */
    private static void verifyPrimaryWSDL(@NotNull SDDocumentSource primaryWsdl, @NotNull QName serviceName) {
        SDDocumentImpl primaryDoc = SDDocumentImpl.create(primaryWsdl,serviceName,null);
        if (!(primaryDoc instanceof SDDocument.WSDL)) {
            throw new WebServiceException(primaryWsdl.getSystemId()+
                    " is not a WSDL. But it is passed as a primary WSDL");
        }
        SDDocument.WSDL wsdlDoc = (SDDocument.WSDL)primaryDoc;
        if (!wsdlDoc.hasService()) {
            if(wsdlDoc.getAllServices().isEmpty())
                throw new WebServiceException("Not a primary WSDL="+primaryWsdl.getSystemId()+
                        " since it doesn't have Service "+serviceName);
            else
                throw new WebServiceException("WSDL "+primaryDoc.getSystemId()
                        +" has the following services "+wsdlDoc.getAllServices()
                        +" but not "+serviceName+". Maybe you forgot to specify a serviceName and/or targetNamespace in @WebService/@WebServiceProvider?");
        }
    }

    /**
     * Finds the primary WSDL document from the list of metadata documents. If
     * there are two metadata documents that qualify for primary, it throws an
     * exception. If there are two metadata documents that qualify for porttype,
     * it throws an exception.
     *
     * @return primay wsdl document, null if is not there in the docList
     *
     */
    private static @Nullable SDDocumentImpl findPrimary(@NotNull Collection<SDDocumentImpl> docList) {
        SDDocumentImpl primaryDoc = null;
        boolean foundConcrete = false;
        boolean foundAbstract = false;
        for(SDDocumentImpl doc : docList) {
            if (doc instanceof SDDocument.WSDL) {
                SDDocument.WSDL wsdlDoc = (SDDocument.WSDL)doc;
                if (wsdlDoc.hasService()) {
                    primaryDoc = doc;
                    if (foundConcrete) {
                        throw new ServerRtException("duplicate.primary.wsdl", doc.getSystemId() );
                    }
                    foundConcrete = true;
                }
                if (wsdlDoc.hasPortType()) {
                    if (foundAbstract) {
                        throw new ServerRtException("duplicate.abstract.wsdl", doc.getSystemId());
                    }
                    foundAbstract = true;
                }
            }
        }
        return primaryDoc;
    }

    /**
     * Parses the primary WSDL and returns the {@link WSDLPort} for the given service and port names
     *
     * @param primaryWsdl Primary WSDL
     * @param metadata it may contain imported WSDL and schema documents
     * @param serviceName service name in wsdl
     * @param portName port name in WSDL
     * @param container container in which this service is running
     * @return non-null wsdl port object
     */
    private static @NotNull WSDLPort getWSDLPort(SDDocumentSource primaryWsdl, Collection<? extends SDDocumentSource> metadata,
                                                     @NotNull QName serviceName, @NotNull QName portName, Container container,
                                                     EntityResolver resolver) {
        URL wsdlUrl = primaryWsdl.getSystemId();
        try {
            // TODO: delegate to another entity resolver
            WSDLModel wsdlDoc = RuntimeWSDLParser.parse(
                new Parser(primaryWsdl), new EntityResolverImpl(metadata, resolver),
                    false, container, ServiceFinder.find(WSDLParserExtension.class).toArray());
            if(wsdlDoc.getServices().size() == 0) {
                throw new ServerRtException(ServerMessages.localizableRUNTIME_PARSER_WSDL_NOSERVICE_IN_WSDLMODEL(wsdlUrl));
            }
            WSDLService wsdlService = wsdlDoc.getService(serviceName);
            if (wsdlService == null) {
                throw new ServerRtException(ServerMessages.localizableRUNTIME_PARSER_WSDL_INCORRECTSERVICE(serviceName,wsdlUrl));
            }
            WSDLPort wsdlPort = wsdlService.get(portName);
            if (wsdlPort == null) {
                throw new ServerRtException(ServerMessages.localizableRUNTIME_PARSER_WSDL_INCORRECTSERVICEPORT(serviceName, portName, wsdlUrl));
            }
            return wsdlPort;
        } catch (IOException e) {
            throw new ServerRtException("runtime.parser.wsdl", wsdlUrl,e);
        } catch (XMLStreamException e) {
            throw new ServerRtException("runtime.saxparser.exception", e.getMessage(), e.getLocation(), e);
        } catch (SAXException e) {
            throw new ServerRtException("runtime.parser.wsdl", wsdlUrl,e);
        } catch (ServiceConfigurationError e) {
            throw new ServerRtException("runtime.parser.wsdl", wsdlUrl,e);
        }
    }

    /**
     * {@link XMLEntityResolver} that can resolve to {@link SDDocumentSource}s.
     */
    private static final class EntityResolverImpl implements XMLEntityResolver {
        private Iterator<? extends SDDocumentSource> origMetadata;
        private Map<String,SDDocumentSource> metadata = new ConcurrentHashMap<String,SDDocumentSource>();
        private EntityResolver resolver;

        public EntityResolverImpl(Collection<? extends SDDocumentSource> metadata, EntityResolver resolver) {
            this.origMetadata = metadata.iterator();
            this.resolver = resolver;
        }

        public Parser resolveEntity (String publicId, String systemId) throws IOException, XMLStreamException {
            if (systemId != null) {
                SDDocumentSource doc = metadata.get(systemId);
                if (doc != null)
                    return new Parser(doc);
                synchronized(this) {
                    while(origMetadata.hasNext()) {
                        doc = origMetadata.next();
                        String extForm = doc.getSystemId().toExternalForm();
                        this.metadata.put(extForm,doc);
                        if (systemId.equals(extForm))
                            return new Parser(doc);
                    }
                }
            }
            if (resolver != null) {
                try {
                    InputSource source = resolver.resolveEntity(publicId, systemId);
                    if (source != null) {
                        Parser p = new Parser(null, XMLStreamReaderFactory.create(source, true));
                        return p;
                    }
                } catch (SAXException e) {
                    throw new XMLStreamException(e);
                }
            }
            return null;
        }

    }

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.endpoint");

    private static class CollectionCollection<T> extends AbstractCollection<T> {

        private final Collection<Collection<? extends T>> cols = new ArrayList<Collection<? extends T>>();

        @Override
        public Iterator<T> iterator() {
            final Iterator<Collection<? extends T>> colIt = cols.iterator();
            return new Iterator<T>() {
                private Iterator<? extends T> current = null;

                @Override
                public boolean hasNext() {
                    if (current == null || !current.hasNext()) {
                        do {
                            if (!colIt.hasNext())
                                return false;
                            current = colIt.next().iterator();
                        } while (!current.hasNext());
                        return true;
                    }
                    return true;
                }

                @Override
                public T next() {
                    if (!hasNext())
                        throw new NoSuchElementException();
                    return current.next();
                }

                @Override
                public void remove() {
                    if (current == null)
                        throw new IllegalStateException();
                    current.remove();
                }
            };
        }

        @Override
        public int size() {
            int size = 0;
            for (Collection<? extends T> c : cols)
                size += c.size();
            return size;
        }

        @Override
        public boolean add(T arg0) {
            return cols.add(Collections.singleton(arg0));
        }

        @Override
        public boolean addAll(Collection<? extends T> arg0) {
            return cols.add(arg0);
        }

        @Override
        public void clear() {
            cols.clear();
        }

        @Override
        public boolean isEmpty() {
            return !iterator().hasNext();
        }
    }
}
