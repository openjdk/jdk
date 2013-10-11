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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.MutableXMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.stream.buffer.XMLStreamBufferMark;
import com.sun.xml.internal.stream.buffer.stax.StreamReaderBufferCreator;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.BindingIDFactory;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSDLLocator;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;
import com.sun.xml.internal.ws.api.policy.PolicyResolverFactory;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLDescriptorKind;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundFault;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLFault;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLInput;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLMessage;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLOutput;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPart;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPort;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPortType;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLService;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.api.streaming.XMLStreamReaderFactory;
import com.sun.xml.internal.ws.api.wsdl.parser.MetaDataResolver;
import com.sun.xml.internal.ws.api.wsdl.parser.MetadataResolverFactory;
import com.sun.xml.internal.ws.api.wsdl.parser.ServiceDescriptor;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver;
import com.sun.xml.internal.ws.api.wsdl.parser.XMLEntityResolver.Parser;
import com.sun.xml.internal.ws.model.wsdl.*;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.resources.WsdlmodelMessages;
import com.sun.xml.internal.ws.streaming.SourceReaderFactory;
import com.sun.xml.internal.ws.streaming.TidyXMLStreamReader;
import com.sun.xml.internal.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.internal.ws.util.ServiceFinder;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import com.sun.xml.internal.ws.policy.jaxws.PolicyWSDLParserExtension;

import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.jws.soap.SOAPBinding.Style;
import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import java.io.IOException;
import java.io.InputStream;
import java.io.FilterInputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parses WSDL and builds {@link com.sun.xml.internal.ws.api.model.wsdl.WSDLModel}.
 *
 * @author Vivek Pandey
 * @author Rama Pulavarthi
 */
public class RuntimeWSDLParser {

    private final EditableWSDLModel wsdlDoc;
    /**
     * Target namespace URI of the WSDL that we are currently parsing.
     */
    private String targetNamespace;
    /**
     * System IDs of WSDLs that are already read.
     */
    private final Set<String> importedWSDLs = new HashSet<String>();
    /**
     * Must not be null.
     */
    private final XMLEntityResolver resolver;

    private final PolicyResolver policyResolver;

    /**
     * The {@link WSDLParserExtension}. Always non-null.
     */
    private final WSDLParserExtension extensionFacade;

    private final WSDLParserExtensionContextImpl context;

    List<WSDLParserExtension> extensions;

    //Capture namespaces declared on the ancestors of wsa:EndpointReference, so that valid XmlStreamBuffer is created
    // from the EndpointReference fragment.
    Map<String, String> wsdldef_nsdecl = new HashMap<String, String>();
    Map<String, String> service_nsdecl = new HashMap<String, String>();
    Map<String, String> port_nsdecl = new HashMap<String, String>();

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     *
     * @param wsdlLoc
     *      Either this or <tt>wsdl</tt> parameter must be given.
     *      Null location means the system won't be able to resolve relative references in the WSDL,
     */
    public static WSDLModel parse(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, @NotNull EntityResolver resolver,
                                      boolean isClientSide, Container container,
                                      WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        return parse(wsdlLoc, wsdlSource, resolver, isClientSide, container, Service.class, PolicyResolverFactory.create(),extensions);
    }

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     *
     * @param wsdlLoc
     *      Either this or <tt>wsdl</tt> parameter must be given.
     *      Null location means the system won't be able to resolve relative references in the WSDL,
     */
    public static WSDLModel parse(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, @NotNull EntityResolver resolver,
                                      boolean isClientSide, Container container, Class serviceClass,
                                      WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        return parse(wsdlLoc, wsdlSource, resolver, isClientSide, container, serviceClass, PolicyResolverFactory.create(),extensions);
    }

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     *
     * @param wsdlLoc
     *      Either this or <tt>wsdl</tt> parameter must be given.
     *      Null location means the system won't be able to resolve relative references in the WSDL,
     */
    public static WSDLModel parse(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, @NotNull EntityResolver resolver,
                                      boolean isClientSide, Container container, @NotNull PolicyResolver policyResolver,
                                      WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        return parse(wsdlLoc, wsdlSource, resolver, isClientSide, container, Service.class, policyResolver, extensions);
    }

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     *
     * @param wsdlLoc
     *      Either this or <tt>wsdl</tt> parameter must be given.
     *      Null location means the system won't be able to resolve relative references in the WSDL,
     */
    public static WSDLModel parse(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, @NotNull EntityResolver resolver,
                                      boolean isClientSide, Container container, Class serviceClass,
                                      @NotNull PolicyResolver policyResolver,
                                      WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        return parse(wsdlLoc, wsdlSource, resolver, isClientSide, container, serviceClass, policyResolver, false, extensions);
    }

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     *
     * @param wsdlLoc
     *      Either this or <tt>wsdl</tt> parameter must be given.
     *      Null location means the system won't be able to resolve relative references in the WSDL,
     */
    public static WSDLModel parse(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, @NotNull EntityResolver resolver,
                                      boolean isClientSide, Container container, Class serviceClass,
                                      @NotNull PolicyResolver policyResolver,
                                      boolean isUseStreamFromEntityResolverWrapper,
                                      WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        assert resolver != null;

        RuntimeWSDLParser wsdlParser = new RuntimeWSDLParser(wsdlSource.getSystemId(), new EntityResolverWrapper(resolver, isUseStreamFromEntityResolverWrapper), isClientSide, container, policyResolver, extensions);
        Parser parser;
        try{
            parser = wsdlParser.resolveWSDL(wsdlLoc, wsdlSource, serviceClass);
            if(!hasWSDLDefinitions(parser.parser)){
                throw new XMLStreamException(ClientMessages.RUNTIME_WSDLPARSER_INVALID_WSDL(parser.systemId,
                        WSDLConstants.QNAME_DEFINITIONS, parser.parser.getName(), parser.parser.getLocation()));
            }
        }catch(XMLStreamException e){
            //Try MEX if there is WSDLLoc available
            if(wsdlLoc == null)
                throw e;
            return tryWithMex(wsdlParser, wsdlLoc, resolver, isClientSide, container, e, serviceClass, policyResolver, extensions);

        }catch(IOException e){
            //Try MEX if there is WSDLLoc available
            if(wsdlLoc == null)
                throw e;
            return tryWithMex(wsdlParser, wsdlLoc, resolver, isClientSide, container, e, serviceClass, policyResolver, extensions);
        }
        wsdlParser.extensionFacade.start(wsdlParser.context);
        wsdlParser.parseWSDL(parser, false);
        wsdlParser.wsdlDoc.freeze();
        wsdlParser.extensionFacade.finished(wsdlParser.context);
        wsdlParser.extensionFacade.postFinished(wsdlParser.context);

        if(wsdlParser.wsdlDoc.getServices().isEmpty())
            throw new WebServiceException(ClientMessages.WSDL_CONTAINS_NO_SERVICE(wsdlLoc));

        return wsdlParser.wsdlDoc;
    }

    private static WSDLModel tryWithMex(@NotNull RuntimeWSDLParser wsdlParser, @NotNull URL wsdlLoc, @NotNull EntityResolver resolver, boolean isClientSide, Container container, Throwable e, Class serviceClass, PolicyResolver policyResolver, WSDLParserExtension... extensions) throws SAXException, XMLStreamException {
        ArrayList<Throwable> exceptions = new ArrayList<Throwable>();
        try {
            WSDLModel wsdlModel = wsdlParser.parseUsingMex(wsdlLoc, resolver, isClientSide, container, serviceClass, policyResolver,extensions);
            if(wsdlModel == null){
                throw new WebServiceException(ClientMessages.FAILED_TO_PARSE(wsdlLoc.toExternalForm(), e.getMessage()), e);
            }
            return wsdlModel;
        } catch (URISyntaxException e1) {
            exceptions.add(e);
            exceptions.add(e1);
        } catch(IOException e1){
            exceptions.add(e);
            exceptions.add(e1);
        }
        throw new InaccessibleWSDLException(exceptions);
    }

    private WSDLModel parseUsingMex(@NotNull URL wsdlLoc, @NotNull EntityResolver resolver, boolean isClientSide, Container container, Class serviceClass, PolicyResolver policyResolver, WSDLParserExtension[] extensions) throws IOException, SAXException, XMLStreamException, URISyntaxException {
        //try MEX
        MetaDataResolver mdResolver = null;
        ServiceDescriptor serviceDescriptor = null;
        RuntimeWSDLParser wsdlParser = null;

        //Currently we try the first available MetadataResolverFactory that gives us a WSDL document
        for (MetadataResolverFactory resolverFactory : ServiceFinder.find(MetadataResolverFactory.class)) {
            mdResolver = resolverFactory.metadataResolver(resolver);
            serviceDescriptor = mdResolver.resolve(wsdlLoc.toURI());
            //we got the ServiceDescriptor, now break
            if (serviceDescriptor != null)
                break;
        }
        if (serviceDescriptor != null) {
            List<? extends Source> wsdls = serviceDescriptor.getWSDLs();
            wsdlParser = new RuntimeWSDLParser(wsdlLoc.toExternalForm(), new MexEntityResolver(wsdls), isClientSide, container, policyResolver, extensions);
            wsdlParser.extensionFacade.start(wsdlParser.context);

            for(Source src: wsdls ) {
                String systemId = src.getSystemId();
                Parser parser = wsdlParser.resolver.resolveEntity(null, systemId);
                wsdlParser.parseWSDL(parser, false);
            }
        }
        //Incase that mex is not present or it couldn't get the metadata, try by appending ?wsdl and give
        // it a last shot else fail
        if ((mdResolver == null || serviceDescriptor == null) && (wsdlLoc.getProtocol().equals("http") || wsdlLoc.getProtocol().equals("https")) && (wsdlLoc.getQuery() == null)) {
            String urlString = wsdlLoc.toExternalForm();
            urlString += "?wsdl";
            wsdlLoc = new URL(urlString);
            wsdlParser = new RuntimeWSDLParser(wsdlLoc.toExternalForm(),new EntityResolverWrapper(resolver), isClientSide, container, policyResolver, extensions);
            wsdlParser.extensionFacade.start(wsdlParser.context);
            Parser parser = resolveWSDL(wsdlLoc, new StreamSource(wsdlLoc.toExternalForm()), serviceClass);
            wsdlParser.parseWSDL(parser, false);
        }

        if(wsdlParser == null)
            return null;

        wsdlParser.wsdlDoc.freeze();
        wsdlParser.extensionFacade.finished(wsdlParser.context);
        wsdlParser.extensionFacade.postFinished(wsdlParser.context);
        return wsdlParser.wsdlDoc;
    }

    private static boolean hasWSDLDefinitions(XMLStreamReader reader) {
        XMLStreamReaderUtil.nextElementContent(reader);
        return reader.getName().equals(WSDLConstants.QNAME_DEFINITIONS);
    }

    public static WSDLModel parse(XMLEntityResolver.Parser wsdl, XMLEntityResolver resolver, boolean isClientSide, Container container, PolicyResolver policyResolver, WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        assert resolver != null;
        RuntimeWSDLParser parser = new RuntimeWSDLParser( wsdl.systemId.toExternalForm(), resolver, isClientSide, container, policyResolver, extensions);
        parser.extensionFacade.start(parser.context);
        parser.parseWSDL(wsdl, false);
        parser.wsdlDoc.freeze();
        parser.extensionFacade.finished(parser.context);
        parser.extensionFacade.postFinished(parser.context);
        return parser.wsdlDoc;
    }

    public static WSDLModel parse(XMLEntityResolver.Parser wsdl, XMLEntityResolver resolver, boolean isClientSide, Container container, WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        assert resolver != null;
        RuntimeWSDLParser parser = new RuntimeWSDLParser( wsdl.systemId.toExternalForm(), resolver, isClientSide, container, PolicyResolverFactory.create(), extensions);
        parser.extensionFacade.start(parser.context);
        parser.parseWSDL(wsdl, false);
        parser.wsdlDoc.freeze();
        parser.extensionFacade.finished(parser.context);
        parser.extensionFacade.postFinished(parser.context);
        return parser.wsdlDoc;
    }

    private RuntimeWSDLParser(@NotNull String sourceLocation, XMLEntityResolver resolver, boolean isClientSide, Container container, PolicyResolver policyResolver, WSDLParserExtension... extensions) {
        this.wsdlDoc = sourceLocation!=null ? new WSDLModelImpl(sourceLocation) : new WSDLModelImpl();
        this.resolver = resolver;
        this.policyResolver = policyResolver;
        this.extensions = new ArrayList<WSDLParserExtension>();
        this.context = new WSDLParserExtensionContextImpl(wsdlDoc, isClientSide, container, policyResolver);

        boolean isPolicyExtensionFound = false;
        for (WSDLParserExtension e : extensions) {
                if (e instanceof com.sun.xml.internal.ws.api.wsdl.parser.PolicyWSDLParserExtension)
                        isPolicyExtensionFound = true;
            register(e);
        }

        // register handlers for default extensions
        if (!isPolicyExtensionFound)
                register(new PolicyWSDLParserExtension());
        register(new MemberSubmissionAddressingWSDLParserExtension());
        register(new W3CAddressingWSDLParserExtension());
        register(new W3CAddressingMetadataWSDLParserExtension());

        this.extensionFacade =  new WSDLParserExtensionFacade(this.extensions.toArray(new WSDLParserExtension[0]));
    }

    private Parser resolveWSDL(@Nullable URL wsdlLoc, @NotNull Source wsdlSource, Class serviceClass) throws IOException, SAXException, XMLStreamException {
        String systemId = wsdlSource.getSystemId();

        XMLEntityResolver.Parser parser = resolver.resolveEntity(null, systemId);
        if (parser == null && wsdlLoc != null) {
                String exForm = wsdlLoc.toExternalForm();
            parser = resolver.resolveEntity(null, exForm);

            if (parser == null && serviceClass != null) {
                URL ru = serviceClass.getResource(".");
                if (ru != null) {
                        String ruExForm = ru.toExternalForm();
                        if (exForm.startsWith(ruExForm)) {
                                parser = resolver.resolveEntity(null, exForm.substring(ruExForm.length()));
                        }
                }
            }
        }
        if (parser == null) {
            //If a WSDL source is provided that is known to be readable, then
            //prioritize that over the URL - this avoids going over the network
            //an additional time if a valid WSDL Source is provided - Deva Sagar 09/20/2011
            if (isKnownReadableSource(wsdlSource)) {
                parser = new Parser(wsdlLoc, createReader(wsdlSource));
            } else if (wsdlLoc != null) {
                parser = new Parser(wsdlLoc, createReader(wsdlLoc, serviceClass));
            }

            //parser could still be null if isKnownReadableSource returns
            //false and wsdlLoc is also null. Fall back to using Source based
            //parser since Source is not null
            if (parser == null) {
                parser = new Parser(wsdlLoc, createReader(wsdlSource));
            }
        }
        return parser;
    }

    private boolean isKnownReadableSource(Source wsdlSource) {
                if (wsdlSource instanceof StreamSource) {
                        return (((StreamSource) wsdlSource).getInputStream() != null ||
                                        ((StreamSource) wsdlSource).getReader() != null);
                } else {
                        return false;
                }
        }

    private XMLStreamReader createReader(@NotNull Source src) throws XMLStreamException {
        return new TidyXMLStreamReader(SourceReaderFactory.createSourceReader(src, true), null);
    }

    private void parseImport(@NotNull URL wsdlLoc) throws XMLStreamException, IOException, SAXException {
        String systemId = wsdlLoc.toExternalForm();
        XMLEntityResolver.Parser parser = resolver.resolveEntity(null, systemId);
        if (parser == null) {
            parser = new Parser(wsdlLoc, createReader(wsdlLoc));
        }
        parseWSDL(parser, true);
    }

    private void parseWSDL(Parser parser, boolean imported) throws XMLStreamException, IOException, SAXException {
        XMLStreamReader reader = parser.parser;
        try {
            // avoid processing the same WSDL twice.
            // if no system ID is given, the check won't work
            if (parser.systemId != null && !importedWSDLs.add(parser.systemId.toExternalForm()))
                return;

            if(reader.getEventType() == XMLStreamConstants.START_DOCUMENT)
                XMLStreamReaderUtil.nextElementContent(reader);
            if (WSDLConstants.QNAME_DEFINITIONS.equals(reader.getName())) {
                readNSDecl(wsdldef_nsdecl, reader);
            }
            if (reader.getEventType()!= XMLStreamConstants.END_DOCUMENT && reader.getName().equals(WSDLConstants.QNAME_SCHEMA)) {
                if (imported) {
                    // wsdl:import could be a schema. Relaxing BP R2001 requirement.
                    LOGGER.warning(WsdlmodelMessages.WSDL_IMPORT_SHOULD_BE_WSDL(parser.systemId));
                    return;
                }
            }

            //get the targetNamespace of the service
            String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);

            final String oldTargetNamespace = targetNamespace;
            targetNamespace = tns;

            while (XMLStreamReaderUtil.nextElementContent(reader) !=
                    XMLStreamConstants.END_ELEMENT) {
                if (reader.getEventType() == XMLStreamConstants.END_DOCUMENT)
                    break;

                QName name = reader.getName();
                if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                    parseImport(parser.systemId, reader);
                } else if (WSDLConstants.QNAME_MESSAGE.equals(name)) {
                    parseMessage(reader);
                } else if (WSDLConstants.QNAME_PORT_TYPE.equals(name)) {
                    parsePortType(reader);
                } else if (WSDLConstants.QNAME_BINDING.equals(name)) {
                    parseBinding(reader);
                } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                    parseService(reader);
                } else {
                    extensionFacade.definitionsElements(reader);
                }
            }
            targetNamespace = oldTargetNamespace;
        } finally {
            this.wsdldef_nsdecl = new HashMap<String,String>();
            reader.close();
        }
    }

    private void parseService(XMLStreamReader reader) {
        service_nsdecl.putAll(wsdldef_nsdecl);
        readNSDecl(service_nsdecl,reader);

        String serviceName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        EditableWSDLService service = new WSDLServiceImpl(reader,wsdlDoc,new QName(targetNamespace, serviceName));
        extensionFacade.serviceAttributes(service, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_PORT.equals(name)) {
                parsePort(reader, service);
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                    XMLStreamReaderUtil.next(reader);
                }
            } else {
                extensionFacade.serviceElements(service, reader);
            }
        }
        wsdlDoc.addService(service);
        service_nsdecl =  new HashMap<String, String>();
    }

    private void parsePort(XMLStreamReader reader, EditableWSDLService service) {
        port_nsdecl.putAll(service_nsdecl);
        readNSDecl(port_nsdecl,reader);

        String portName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        String binding = ParserUtil.getMandatoryNonEmptyAttribute(reader, "binding");

        QName bindingName = ParserUtil.getQName(reader, binding);
        QName portQName = new QName(service.getName().getNamespaceURI(), portName);
        EditableWSDLPort port = new WSDLPortImpl(reader,service, portQName, bindingName);

        extensionFacade.portAttributes(port, reader);

        String location;
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (SOAPConstants.QNAME_ADDRESS.equals(name) || SOAPConstants.QNAME_SOAP12ADDRESS.equals(name)) {
                location = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_LOCATION);
                if (location != null) {
                    try {
                        port.setAddress(new EndpointAddress(location));
                    } catch (URISyntaxException e) {
                        //Lets not throw any exception, latter on it should be thrown when invocation happens. At this
                        // time user has option to set the endopint address using request contexxt property.
                    }
                }
                XMLStreamReaderUtil.next(reader);
            } else if (AddressingVersion.W3C.nsUri.equals(name.getNamespaceURI()) &&
                    "EndpointReference".equals(name.getLocalPart())) {
                try {
                    StreamReaderBufferCreator creator = new StreamReaderBufferCreator(new MutableXMLStreamBuffer());
                    XMLStreamBuffer eprbuffer = new XMLStreamBufferMark(port_nsdecl, creator);
                    creator.createElementFragment(reader, false);

                    WSEndpointReference wsepr = new WSEndpointReference(eprbuffer, AddressingVersion.W3C);
                    //wsepr.toSpec().writeTo(new StreamResult(System.out));
                    port.setEPR(wsepr);
                    /** XMLStreamBuffer.createNewBufferFromXMLStreamReader(reader) called from inside WSEndpointReference()
                     *  consumes the complete EPR infoset and moves to the next element. This breaks the normal wsdl parser
                     *  processing where it expects anyone reading the infoset to move to the end of the element that its reading
                     *  and not to the next element.
                     */
                    if(reader.getEventType() == XMLStreamConstants.END_ELEMENT && reader.getName().equals(WSDLConstants.QNAME_PORT))
                        break;
                } catch (XMLStreamException e) {
                    throw new WebServiceException(e);
                }
            } else {

                extensionFacade.portElements(port, reader);
            }
        }
        if (port.getAddress() == null) {
            try {
                port.setAddress(new EndpointAddress(""));
            } catch (URISyntaxException e) {
                //Lets not throw any exception, latter on it should be thrown when invocation happens. At this
                //time user has option to set the endopint address using request contexxt property.
            }
        }
        service.put(portQName, port);
        port_nsdecl =new HashMap<String, String>();
    }

    private void parseBinding(XMLStreamReader reader) {
        String bindingName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        String portTypeName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "type");
        if ((bindingName == null) || (portTypeName == null)) {
            //TODO: throw exception?
            //
            //  wsdl:binding element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }
        EditableWSDLBoundPortType binding = new WSDLBoundPortTypeImpl(reader,wsdlDoc, new QName(targetNamespace, bindingName),
                ParserUtil.getQName(reader, portTypeName));
        extensionFacade.bindingAttributes(binding, reader);

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.NS_SOAP_BINDING.equals(name)) {
                String transport = reader.getAttributeValue(null, WSDLConstants.ATTR_TRANSPORT);
                binding.setBindingId(createBindingId(transport, SOAPVersion.SOAP_11));

                String style = reader.getAttributeValue(null, "style");

                if ((style != null) && (style.equals("rpc"))) {
                    binding.setStyle(Style.RPC);
                } else {
                    binding.setStyle(Style.DOCUMENT);
                }
                goToEnd(reader);
            } else if (WSDLConstants.NS_SOAP12_BINDING.equals(name)) {
                String transport = reader.getAttributeValue(null, WSDLConstants.ATTR_TRANSPORT);
                binding.setBindingId(createBindingId(transport, SOAPVersion.SOAP_12));

                String style = reader.getAttributeValue(null, "style");
                if ((style != null) && (style.equals("rpc"))) {
                    binding.setStyle(Style.RPC);
                } else {
                    binding.setStyle(Style.DOCUMENT);
                }
                goToEnd(reader);
            } else if (WSDLConstants.QNAME_OPERATION.equals(name)) {
                parseBindingOperation(reader, binding);
            } else {
                extensionFacade.bindingElements(binding, reader);
            }
        }
    }

    private static BindingID createBindingId(String transport, SOAPVersion soapVersion) {
        if (!transport.equals(SOAPConstants.URI_SOAP_TRANSPORT_HTTP)) {
            for( BindingIDFactory f : ServiceFinder.find(BindingIDFactory.class) ) {
                BindingID bindingId = f.create(transport, soapVersion);
                if(bindingId!=null) {
                    return bindingId;
                }
            }
        }
        return soapVersion.equals(SOAPVersion.SOAP_11)?BindingID.SOAP11_HTTP:BindingID.SOAP12_HTTP;
    }


    private void parseBindingOperation(XMLStreamReader reader, EditableWSDLBoundPortType binding) {
        String bindingOpName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        if (bindingOpName == null) {
            //TODO: throw exception?
            //skip wsdl:binding element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }

        QName opName = new QName(binding.getPortTypeName().getNamespaceURI(), bindingOpName);
        EditableWSDLBoundOperation bindingOp = new WSDLBoundOperationImpl(reader,binding, opName);
        binding.put(opName, bindingOp);
        extensionFacade.bindingOperationAttributes(bindingOp, reader);

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            String style = null;
            if (WSDLConstants.QNAME_INPUT.equals(name)) {
                parseInputBinding(reader, bindingOp);
            } else if (WSDLConstants.QNAME_OUTPUT.equals(name)) {
                parseOutputBinding(reader, bindingOp);
            } else if (WSDLConstants.QNAME_FAULT.equals(name)) {
                parseFaultBinding(reader, bindingOp);
            } else if (SOAPConstants.QNAME_OPERATION.equals(name) ||
                    SOAPConstants.QNAME_SOAP12OPERATION.equals(name)) {
                style = reader.getAttributeValue(null, "style");
                String soapAction = reader.getAttributeValue(null, "soapAction");

                if (soapAction != null)
                    bindingOp.setSoapAction(soapAction);

                goToEnd(reader);
            } else {
                extensionFacade.bindingOperationElements(bindingOp, reader);
            }
            /**
             *  If style attribute is present set it otherwise set the style as defined
             *  on the <soap:binding> element
             */
            if (style != null) {
                if (style.equals("rpc"))
                    bindingOp.setStyle(Style.RPC);
                else
                    bindingOp.setStyle(Style.DOCUMENT);
            } else {
                bindingOp.setStyle(binding.getStyle());
            }
        }
    }

    private void parseInputBinding(XMLStreamReader reader, EditableWSDLBoundOperation bindingOp) {
        boolean bodyFound = false;
        extensionFacade.bindingOperationInputAttributes(bindingOp, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if ((SOAPConstants.QNAME_BODY.equals(name) || SOAPConstants.QNAME_SOAP12BODY.equals(name)) && !bodyFound) {
                bodyFound = true;
                bindingOp.setInputExplicitBodyParts(parseSOAPBodyBinding(reader, bindingOp, BindingMode.INPUT));
                goToEnd(reader);
            } else if ((SOAPConstants.QNAME_HEADER.equals(name) || SOAPConstants.QNAME_SOAP12HEADER.equals(name))) {
                parseSOAPHeaderBinding(reader, bindingOp.getInputParts());
            } else if (MIMEConstants.QNAME_MULTIPART_RELATED.equals(name)) {
                parseMimeMultipartBinding(reader, bindingOp, BindingMode.INPUT);
            } else {
                extensionFacade.bindingOperationInputElements(bindingOp, reader);
            }
        }
    }

    private void parseOutputBinding(XMLStreamReader reader, EditableWSDLBoundOperation bindingOp) {
        boolean bodyFound = false;
        extensionFacade.bindingOperationOutputAttributes(bindingOp, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if ((SOAPConstants.QNAME_BODY.equals(name) || SOAPConstants.QNAME_SOAP12BODY.equals(name)) && !bodyFound) {
                bodyFound = true;
                bindingOp.setOutputExplicitBodyParts(parseSOAPBodyBinding(reader, bindingOp, BindingMode.OUTPUT));
                goToEnd(reader);
            } else if ((SOAPConstants.QNAME_HEADER.equals(name) || SOAPConstants.QNAME_SOAP12HEADER.equals(name))) {
                parseSOAPHeaderBinding(reader, bindingOp.getOutputParts());
            } else if (MIMEConstants.QNAME_MULTIPART_RELATED.equals(name)) {
                parseMimeMultipartBinding(reader, bindingOp, BindingMode.OUTPUT);
            } else {
                extensionFacade.bindingOperationOutputElements(bindingOp, reader);
            }
        }
    }

    private void parseFaultBinding(XMLStreamReader reader, EditableWSDLBoundOperation bindingOp) {
        String faultName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        EditableWSDLBoundFault wsdlBoundFault = new WSDLBoundFaultImpl(reader, faultName, bindingOp);
        bindingOp.addFault(wsdlBoundFault);

        extensionFacade.bindingOperationFaultAttributes(wsdlBoundFault, reader);

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.bindingOperationFaultElements(wsdlBoundFault, reader);
        }
    }

    private enum BindingMode {
        INPUT, OUTPUT, FAULT}

    private static boolean parseSOAPBodyBinding(XMLStreamReader reader, EditableWSDLBoundOperation op, BindingMode mode) {
        String namespace = reader.getAttributeValue(null, "namespace");
        if (mode == BindingMode.INPUT) {
            op.setRequestNamespace(namespace);
            return parseSOAPBodyBinding(reader, op.getInputParts());
        }
        //resp
        op.setResponseNamespace(namespace);
        return parseSOAPBodyBinding(reader, op.getOutputParts());
    }

    /**
     * Returns true if body has explicit parts declaration
     */
    private static boolean parseSOAPBodyBinding(XMLStreamReader reader, Map<String, ParameterBinding> parts) {
        String partsString = reader.getAttributeValue(null, "parts");
        if (partsString != null) {
            List<String> partsList = XmlUtil.parseTokenList(partsString);
            if (partsList.isEmpty()) {
                parts.put(" ", ParameterBinding.BODY);
            } else {
                for (String part : partsList) {
                    parts.put(part, ParameterBinding.BODY);
                }
            }
            return true;
        }
        return false;
    }

    private static void parseSOAPHeaderBinding(XMLStreamReader reader, Map<String, ParameterBinding> parts) {
        String part = reader.getAttributeValue(null, "part");
        //if(part == null| part.equals("")||message == null || message.equals("")){
        if (part == null || part.equals("")) {
            return;
        }

        //lets not worry about message attribute for now, probably additional headers wont be there
        //String message = reader.getAttributeValue(null, "message");
        //QName msgName = ParserUtil.getQName(reader, message);
        parts.put(part, ParameterBinding.HEADER);
        goToEnd(reader);
    }


    private static void parseMimeMultipartBinding(XMLStreamReader reader, EditableWSDLBoundOperation op, BindingMode mode) {
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (MIMEConstants.QNAME_PART.equals(name)) {
                parseMIMEPart(reader, op, mode);
            } else {
                XMLStreamReaderUtil.skipElement(reader);
            }
        }
    }

    private static void parseMIMEPart(XMLStreamReader reader, EditableWSDLBoundOperation op, BindingMode mode) {
        boolean bodyFound = false;
        Map<String, ParameterBinding> parts = null;
        if (mode == BindingMode.INPUT) {
            parts = op.getInputParts();
        } else if (mode == BindingMode.OUTPUT) {
            parts = op.getOutputParts();
        } else if (mode == BindingMode.FAULT) {
            parts = op.getFaultParts();
        }
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (SOAPConstants.QNAME_BODY.equals(name) && !bodyFound) {
                bodyFound = true;
                parseSOAPBodyBinding(reader, op, mode);
                XMLStreamReaderUtil.next(reader);
            } else if (SOAPConstants.QNAME_HEADER.equals(name)) {
                bodyFound = true;
                parseSOAPHeaderBinding(reader, parts);
                XMLStreamReaderUtil.next(reader);
            } else if (MIMEConstants.QNAME_CONTENT.equals(name)) {
                String part = reader.getAttributeValue(null, "part");
                String type = reader.getAttributeValue(null, "type");
                if ((part == null) || (type == null)) {
                    XMLStreamReaderUtil.skipElement(reader);
                    continue;
                }
                ParameterBinding sb = ParameterBinding.createAttachment(type);
                if (parts != null && sb != null && part != null)
                    parts.put(part, sb);
                XMLStreamReaderUtil.next(reader);
            } else {
                XMLStreamReaderUtil.skipElement(reader);
            }
        }
    }

    protected void parseImport(@Nullable URL baseURL, XMLStreamReader reader) throws IOException, SAXException, XMLStreamException {
        // expand to the absolute URL of the imported WSDL.
        String importLocation =
                ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_LOCATION);
        URL importURL;
        if(baseURL!=null)
            importURL = new URL(baseURL, importLocation);
        else // no base URL. this better be absolute
            importURL = new URL(importLocation);
        parseImport(importURL);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            XMLStreamReaderUtil.skipElement(reader);
        }
    }

    private void parsePortType(XMLStreamReader reader) {
        String portTypeName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        if (portTypeName == null) {
            //TODO: throw exception?
            //skip wsdl:portType element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }
        EditableWSDLPortType portType = new WSDLPortTypeImpl(reader,wsdlDoc, new QName(targetNamespace, portTypeName));
        extensionFacade.portTypeAttributes(portType, reader);
        wsdlDoc.addPortType(portType);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_OPERATION.equals(name)) {
                parsePortTypeOperation(reader, portType);
            } else {
                extensionFacade.portTypeElements(portType, reader);
            }
        }
    }


    private void parsePortTypeOperation(XMLStreamReader reader, EditableWSDLPortType portType) {
        String operationName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        if (operationName == null) {
            //TODO: throw exception?
            //skip wsdl:portType element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }

        QName operationQName = new QName(portType.getName().getNamespaceURI(), operationName);
        EditableWSDLOperation operation = new WSDLOperationImpl(reader,portType, operationQName);
        extensionFacade.portTypeOperationAttributes(operation, reader);
        String parameterOrder = ParserUtil.getAttribute(reader, "parameterOrder");
        operation.setParameterOrder(parameterOrder);
        portType.put(operationName, operation);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (name.equals(WSDLConstants.QNAME_INPUT)) {
                parsePortTypeOperationInput(reader, operation);
            } else if (name.equals(WSDLConstants.QNAME_OUTPUT)) {
                parsePortTypeOperationOutput(reader, operation);
            } else if (name.equals(WSDLConstants.QNAME_FAULT)) {
                parsePortTypeOperationFault(reader, operation);
            } else {
                extensionFacade.portTypeOperationElements(operation, reader);
            }
        }
    }


    private void parsePortTypeOperationFault(XMLStreamReader reader, EditableWSDLOperation operation) {
        String msg = ParserUtil.getMandatoryNonEmptyAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        EditableWSDLFault fault = new WSDLFaultImpl(reader,name, msgName, operation);
        operation.addFault(fault);
        extensionFacade.portTypeOperationFaultAttributes(fault, reader);
        extensionFacade.portTypeOperationFault(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationFaultElements(fault, reader);
        }
    }

    private void parsePortTypeOperationInput(XMLStreamReader reader, EditableWSDLOperation operation) {
        String msg = ParserUtil.getMandatoryNonEmptyAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getAttribute(reader, "name");
        EditableWSDLInput input = new WSDLInputImpl(reader, name, msgName, operation);
        operation.setInput(input);
        extensionFacade.portTypeOperationInputAttributes(input, reader);
        extensionFacade.portTypeOperationInput(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationInputElements(input, reader);
        }
    }

    private void parsePortTypeOperationOutput(XMLStreamReader reader, EditableWSDLOperation operation) {
        String msg = ParserUtil.getAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getAttribute(reader, "name");
        EditableWSDLOutput output = new WSDLOutputImpl(reader,name, msgName, operation);
        operation.setOutput(output);
        extensionFacade.portTypeOperationOutputAttributes(output, reader);
        extensionFacade.portTypeOperationOutput(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationOutputElements(output, reader);
        }
    }

    private void parseMessage(XMLStreamReader reader) {
        String msgName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        EditableWSDLMessage msg = new WSDLMessageImpl(reader,new QName(targetNamespace, msgName));
        extensionFacade.messageAttributes(msg, reader);
        int partIndex = 0;
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_PART.equals(name)) {
                String part = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                String desc = null;
                int index = reader.getAttributeCount();
                WSDLDescriptorKind kind = WSDLDescriptorKind.ELEMENT;
                for (int i = 0; i < index; i++) {
                    QName descName = reader.getAttributeName(i);
                    if (descName.getLocalPart().equals("element"))
                        kind = WSDLDescriptorKind.ELEMENT;
                    else if (descName.getLocalPart().equals("type"))
                        kind = WSDLDescriptorKind.TYPE;

                    if (descName.getLocalPart().equals("element") || descName.getLocalPart().equals("type")) {
                        desc = reader.getAttributeValue(i);
                        break;
                    }
                }
                if (desc != null) {
                    EditableWSDLPart wsdlPart = new WSDLPartImpl(reader, part, partIndex, new WSDLPartDescriptorImpl(reader,ParserUtil.getQName(reader, desc), kind));
                    msg.add(wsdlPart);
                }
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT)
                    goToEnd(reader);
            } else {
                extensionFacade.messageElements(msg, reader);
            }
        }
        wsdlDoc.addMessage(msg);
        if (reader.getEventType() != XMLStreamConstants.END_ELEMENT)
            goToEnd(reader);
    }

    private static void goToEnd(XMLStreamReader reader) {
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            XMLStreamReaderUtil.skipElement(reader);
        }
    }

    /**
     * Make sure to return a "fresh" reader each time it is called because
     * more than one active reader may be needed within a single thread
     * to parse a WSDL file.
     */
    private static XMLStreamReader createReader(URL wsdlLoc) throws IOException, XMLStreamException {
        return createReader(wsdlLoc, null);
    }

    /**
     * Make sure to return a "fresh" reader each time it is called because
     * more than one active reader may be needed within a single thread
     * to parse a WSDL file.
     */
    private static XMLStreamReader createReader(URL wsdlLoc, Class<Service> serviceClass) throws IOException, XMLStreamException {
        InputStream stream;
        try {
                stream = wsdlLoc.openStream();
        } catch (IOException io) {
                out:
                do {
                        if (serviceClass != null) {
                                WSDLLocator locator = ContainerResolver.getInstance().getContainer().getSPI(WSDLLocator.class);
                                if (locator != null) {
                                  String exForm = wsdlLoc.toExternalForm();
                                  URL ru = serviceClass.getResource(".");
                                  String loc = wsdlLoc.getPath();
                                  if (ru != null) {
                                    String ruExForm = ru.toExternalForm();
                                    if (exForm.startsWith(ruExForm)) {
                                      loc = exForm.substring(ruExForm.length());
                                    }
                                  }
                                  wsdlLoc = locator.locateWSDL(serviceClass, loc);
                                  if (wsdlLoc != null) {
                                                stream = new FilterInputStream(wsdlLoc.openStream()) {
                                                    boolean closed;

                                                    @Override
                                                    public void close() throws IOException {
                                                        if (!closed) {
                                                            closed = true;
                                                            byte[] buf = new byte[8192];
                                                            while(read(buf) != -1);
                                                            super.close();
                                                        }
                                                    }
                                                };
                                          break out;
                                  }
                                }
                        }
                        throw io;
                } while(true);
        }

        return new TidyXMLStreamReader(XMLStreamReaderFactory.create(wsdlLoc.toExternalForm(), stream, false), stream);
    }

    private void register(WSDLParserExtension e) {
        // protect JAX-WS RI from broken parser extension
        extensions.add(new FoolProofParserExtension(e));
    }

    /**
     * Reads the namespace declarations from the reader's current position in to the map. The reader is expected to be
     * on the start element.
     *
     * @param ns_map
     * @param reader
     */
    private static void readNSDecl(Map<String, String> ns_map, XMLStreamReader reader) {
        if (reader.getNamespaceCount() > 0) {
            for (int i = 0; i < reader.getNamespaceCount(); i++) {
                ns_map.put(reader.getNamespacePrefix(i), reader.getNamespaceURI(i));
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RuntimeWSDLParser.class.getName());
}
