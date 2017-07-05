/*
 * Copyright 2005-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.xml.internal.ws.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.api.server.InstanceResolver;
import com.sun.xml.internal.ws.api.server.Invoker;
import com.sun.xml.internal.ws.api.server.SDDocument;
import com.sun.xml.internal.ws.api.server.SDDocumentSource;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver.Parser;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.binding.SOAPBindingImpl;
import com.sun.xml.internal.ws.binding.WebServiceFeatureList;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.RuntimeModeler;
import com.sun.xml.internal.ws.model.SOAPSEIModel;
import com.sun.xml.internal.ws.model.wsdl.WSDLModelImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.internal.ws.model.wsdl.WSDLServiceImpl;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.server.provider.ProviderInvokerTube;
import com.sun.xml.internal.ws.server.sei.SEIInvokerTube;
import com.sun.xml.internal.ws.util.HandlerAnnotationInfo;
import com.sun.xml.internal.ws.util.HandlerAnnotationProcessor;
import com.sun.xml.internal.ws.util.ServiceConfigurationError;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.wsdl.parser.RuntimeWSDLParser;
import com.sun.xml.internal.ws.wsdl.writer.WSDLGenerator;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.soap.SOAPBinding;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Entry point to the JAX-WS RI server-side runtime.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public class EndpointFactory {

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
        @Nullable Collection<? extends SDDocumentSource> metadata, EntityResolver resolver, boolean isTransportSynchronous) {

        if(implType ==null)
            throw new IllegalArgumentException();

        verifyImplementorClass(implType);

        if (invoker == null) {
            invoker = InstanceResolver.createDefault(implType).createInvoker();
        }

        List<SDDocumentSource> md = new ArrayList<SDDocumentSource>();
        if(metadata!=null)
            md.addAll(metadata);

        if(primaryWsdl!=null && !md.contains(primaryWsdl))
            md.add(primaryWsdl);

        if(container==null)
            container = ContainerResolver.getInstance().getContainer();

        if(serviceName==null)
            serviceName = getDefaultServiceName(implType);

        if(portName==null)
            portName = getDefaultPortName(serviceName,implType);

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

        if (primaryWsdl != null) {
            verifyPrimaryWSDL(primaryWsdl, serviceName);
        }

        QName portTypeName = null;
        if (implType.getAnnotation(WebServiceProvider.class)==null) {
            portTypeName = RuntimeModeler.getPortTypeName(implType);
        }

        // Categorises the documents as WSDL, Schema etc
        List<SDDocumentImpl> docList = categoriseMetadata(md, serviceName, portTypeName);
        // Finds the primary WSDL and makes sure that metadata doesn't have
        // two concrete or abstract WSDLs
        SDDocumentImpl primaryDoc = findPrimary(docList);

        InvokerTube terminal;
        WSDLPortImpl wsdlPort = null;
        AbstractSEIModelImpl seiModel = null;
        // create WSDL model
        if (primaryDoc != null) {
            wsdlPort = getWSDLPort(primaryDoc, docList, serviceName, portName, container);
        }

        WebServiceFeatureList features=((BindingImpl)binding).getFeatures();
        features.parseAnnotations(implType);

        // create terminal pipe that invokes the application
        if (implType.getAnnotation(WebServiceProvider.class)!=null) {
            //Provider case: Enable Addressing from WSDL only if it has RespectBindingFeature enabled
            if (wsdlPort != null)
                features.mergeFeatures(wsdlPort,true,true);
            terminal = ProviderInvokerTube.create(implType,binding,invoker);
        } else {
            // Create runtime model for non Provider endpoints
            seiModel = createSEIModel(wsdlPort, implType, serviceName, portName, binding);
            if(binding instanceof SOAPBindingImpl){
                //set portKnownHeaders on Binding, so that they can be used for MU processing
                ((SOAPBindingImpl)binding).setPortKnownHeaders(
                        ((SOAPSEIModel)seiModel).getKnownHeaders());
            }
            // Generate WSDL for SEI endpoints(not for Provider endpoints)
            if (primaryDoc == null) {
                primaryDoc = generateWSDL(binding, seiModel, docList, container, implType);
                // create WSDL model
                wsdlPort = getWSDLPort(primaryDoc, docList, serviceName, portName, container);
                seiModel.freeze(wsdlPort);
            }
            // New Features might have been added in WSDL through Policy.
            // This sets only the wsdl features that are not already set(enabled/disabled)
            features.mergeFeatures(wsdlPort, false, true);
            terminal= new SEIInvokerTube(seiModel,invoker,binding);
        }

        // Process @HandlerChain, if handler-chain is not set via Deployment Descriptor
        if (processHandlerAnnotation) {
            processHandlerAnnotation(binding, implType, serviceName, portName);
        }
        // Selects only required metadata for this endpoint from the passed-in metadata
        if (primaryDoc != null) {
            docList = findMetadataClosure(primaryDoc, docList);
        }
        ServiceDefinitionImpl serviceDefiniton = (primaryDoc != null) ? new ServiceDefinitionImpl(docList, primaryDoc) : null;

        return new WSEndpointImpl<T>(serviceName, portName, binding,container,seiModel,wsdlPort,implType, serviceDefiniton,terminal, isTransportSynchronous);
    }

    /**
     * Goes through the original metadata documents and collects the required ones.
     * This done traversing from primary WSDL and its imports until it builds a
     * complete set of documents(transitive closure) for the endpoint.
     *
     * @param primaryDoc primary WSDL doc
     * @param docList complete metadata
     * @return new metadata that doesn't contain extraneous documnets.
     */
    private static List<SDDocumentImpl> findMetadataClosure(SDDocumentImpl primaryDoc, List<SDDocumentImpl> docList) {
        // create a map for old metadata
        Map<String, SDDocumentImpl> oldMap = new HashMap<String, SDDocumentImpl>();
        for(SDDocumentImpl doc : docList) {
            oldMap.put(doc.getSystemId().toString(), doc);
        }
        // create a map for new metadata
        Map<String, SDDocumentImpl> newMap = new HashMap<String, SDDocumentImpl>();
        newMap.put(primaryDoc.getSystemId().toString(), primaryDoc);

        List<String> remaining = new ArrayList<String>();
        remaining.addAll(primaryDoc.getImports());
        while(!remaining.isEmpty()) {
            String url = remaining.remove(0);
            SDDocumentImpl doc = oldMap.get(url);
            if (doc == null) {
                // old metadata doesn't have this imported doc, may be external
                continue;
            }
            // Check if new metadata already contains this doc
            if (!newMap.containsKey(url)) {
                newMap.put(url, doc);
                remaining.addAll(doc.getImports());
            }
        }
        List<SDDocumentImpl> newMetadata = new ArrayList<SDDocumentImpl>();
        newMetadata.addAll(newMap.values());
        return newMetadata;
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
        WebServiceProvider wsProvider = clz.getAnnotation(WebServiceProvider.class);
        WebService ws = clz.getAnnotation(WebService.class);
        if (wsProvider == null && ws == null) {
            throw new IllegalArgumentException(clz +" has neither @WebSerivce nor @WebServiceProvider annotation");
        }
        if (wsProvider != null && ws != null) {
            throw new IllegalArgumentException(clz +" has both @WebSerivce and @WebServiceProvider annotations");
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
                                                       Class<?> implType, @NotNull QName serviceName, @NotNull QName portName, WSBinding binding) {

        RuntimeModeler rap;
        // Create runtime model for non Provider endpoints

        // wsdlPort will be null, means we will generate WSDL. Hence no need to apply
        // bindings or need to look in the WSDL
        if(wsdlPort == null){
            rap = new RuntimeModeler(implType,serviceName, binding.getBindingId(), binding.getFeatures().toArray());
        } else {
            /*
            This not needed anymore as wsdlFeatures are merged later anyway
            and so is the MTOMFeature.
            applyEffectiveMtomSetting(wsdlPort.getBinding(), binding);
            */
            //now we got the Binding so lets build the model
            rap = new RuntimeModeler(implType, serviceName, (WSDLPortImpl)wsdlPort, binding.getFeatures().toArray());
        }
        rap.setPortName(portName);
        return rap.buildRuntimeModel();
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
        QName serviceName;
        WebServiceProvider wsProvider = implType.getAnnotation(WebServiceProvider.class);
        if (wsProvider!=null) {
            String tns = wsProvider.targetNamespace();
            String local = wsProvider.serviceName();
            serviceName = new QName(tns, local);
        } else {
            serviceName = RuntimeModeler.getServiceName(implType);
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
        QName portName;
        WebServiceProvider wsProvider = implType.getAnnotation(WebServiceProvider.class);
        if (wsProvider!=null) {
            String tns = wsProvider.targetNamespace();
            String local = wsProvider.portName();
            portName = new QName(tns, local);
        } else {
            portName = RuntimeModeler.getPortName(implType, serviceName.getNamespaceURI());
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
        String wsdl;
        WebService ws = implType.getAnnotation(WebService.class);
        if (ws != null) {
            wsdl = ws.wsdlLocation();
        } else {
            WebServiceProvider wsProvider = implType.getAnnotation(WebServiceProvider.class);
            assert wsProvider != null;
            wsdl = wsProvider.wsdlLocation();
        }
        if (wsdl.length() < 1) {
            wsdl = null;
        }
        return wsdl;
    }

    /**
     * Generates the WSDL and XML Schema for the endpoint if necessary
     * It generates WSDL only for SOAP1.1, and for XSOAP1.2 bindings
     */
    private static SDDocumentImpl generateWSDL(WSBinding binding, AbstractSEIModelImpl seiModel, List<SDDocumentImpl> docs,
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
        WSDLGenerator wsdlGen = new WSDLGenerator(seiModel, wsdlResolver, binding, container, implType,
                ServiceFinder.find(WSDLGeneratorExtension.class).toArray());
        wsdlGen.doGeneration();
        return wsdlResolver.updateDocs();
    }

    /**
     * Builds {@link SDDocumentImpl} from {@link SDDocumentSource}.
     */
    private static List<SDDocumentImpl> categoriseMetadata(
        List<SDDocumentSource> src, QName serviceName, QName portTypeName) {

        List<SDDocumentImpl> r = new ArrayList<SDDocumentImpl>(src.size());
        for (SDDocumentSource doc : src) {
            r.add(SDDocumentImpl.create(doc,serviceName,portTypeName));
        }
        return r;
    }

    /**
     * Verifies whether the given primaryWsdl contains the given serviceName.
     * If the WSDL doesn't have the service, it throws an WebServiceException.
     */
    private static void verifyPrimaryWSDL(@NotNull SDDocumentSource primaryWsdl, @NotNull QName serviceName) {
        SDDocumentImpl primaryDoc = SDDocumentImpl.create(primaryWsdl,serviceName,null);
        if (!(primaryDoc instanceof SDDocument.WSDL)) {
            throw new WebServiceException("Not a primary WSDL="+primaryWsdl.getSystemId());
        }
        SDDocument.WSDL wsdlDoc = (SDDocument.WSDL)primaryDoc;
        if (!wsdlDoc.hasService()) {
            if(wsdlDoc.getAllServices().isEmpty())
                throw new WebServiceException("Not a primary WSDL="+primaryWsdl.getSystemId()+
                        " since it doesn't have Service "+serviceName);
            else
                throw new WebServiceException("WSDL "+primaryDoc.getSystemId()+" has the following services "+wsdlDoc.getAllServices()+" but not "+serviceName+". Maybe you forgot to specify a service name in @WebService/@WebServiceProvider?");
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
    private static @Nullable SDDocumentImpl findPrimary(@NotNull List<SDDocumentImpl> docList) {
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
    private static @NotNull WSDLPortImpl getWSDLPort(SDDocumentSource primaryWsdl, List<? extends SDDocumentSource> metadata,
                                                     @NotNull QName serviceName, @NotNull QName portName, Container container) {
        URL wsdlUrl = primaryWsdl.getSystemId();
        try {
            // TODO: delegate to another entity resolver
            WSDLModelImpl wsdlDoc = RuntimeWSDLParser.parse(
                new Parser(primaryWsdl), new EntityResolverImpl(metadata),
                    false, container, ServiceFinder.find(WSDLParserExtension.class).toArray());
            if(wsdlDoc.getServices().size() == 0) {
                throw new ServerRtException(ServerMessages.localizableRUNTIME_PARSER_WSDL_NOSERVICE_IN_WSDLMODEL(wsdlUrl));
            }
            WSDLServiceImpl wsdlService = wsdlDoc.getService(serviceName);
            if (wsdlService == null) {
                throw new ServerRtException(ServerMessages.localizableRUNTIME_PARSER_WSDL_INCORRECTSERVICE(serviceName,wsdlUrl));
            }
            WSDLPortImpl wsdlPort = wsdlService.get(portName);
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
        private Map<String,SDDocumentSource> metadata = new HashMap<String,SDDocumentSource>();

        public EntityResolverImpl(List<? extends SDDocumentSource> metadata) {
            for (SDDocumentSource doc : metadata) {
                this.metadata.put(doc.getSystemId().toExternalForm(),doc);
            }
        }

        public Parser resolveEntity (String publicId, String systemId) throws IOException, XMLStreamException {
            if (systemId != null) {
                SDDocumentSource doc = metadata.get(systemId);
                if (doc != null)
                    return new Parser(doc);
            }
            return null;
        }

    }

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.endpoint");
}
