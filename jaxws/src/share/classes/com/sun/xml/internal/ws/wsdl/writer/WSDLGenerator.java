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
package com.sun.xml.internal.ws.wsdl.writer;


import com.sun.xml.internal.bind.api.JAXBRIContext;
import static com.sun.xml.internal.bind.v2.schemagen.Util.*;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.output.ResultFactory;
import com.sun.xml.internal.txw2.output.XmlSerializer;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.model.JavaMethod;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGeneratorExtension;
import com.sun.xml.internal.ws.api.wsdl.writer.WSDLGenExtnContext;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.CheckedExceptionImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.ParameterImpl;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.wsdl.parser.SOAPConstants;
import com.sun.xml.internal.ws.wsdl.parser.WSDLConstants;
import com.sun.xml.internal.ws.wsdl.writer.document.Binding;
import com.sun.xml.internal.ws.wsdl.writer.document.BindingOperationType;
import com.sun.xml.internal.ws.wsdl.writer.document.Definitions;
import com.sun.xml.internal.ws.wsdl.writer.document.Fault;
import com.sun.xml.internal.ws.wsdl.writer.document.FaultType;
import com.sun.xml.internal.ws.wsdl.writer.document.Import;
import com.sun.xml.internal.ws.wsdl.writer.document.Message;
import com.sun.xml.internal.ws.wsdl.writer.document.Operation;
import com.sun.xml.internal.ws.wsdl.writer.document.ParamType;
import com.sun.xml.internal.ws.wsdl.writer.document.Port;
import com.sun.xml.internal.ws.wsdl.writer.document.PortType;
import com.sun.xml.internal.ws.wsdl.writer.document.Service;
import com.sun.xml.internal.ws.wsdl.writer.document.Types;
import com.sun.xml.internal.ws.wsdl.writer.document.soap.Body;
import com.sun.xml.internal.ws.wsdl.writer.document.soap.BodyType;
import com.sun.xml.internal.ws.wsdl.writer.document.soap.Header;
import com.sun.xml.internal.ws.wsdl.writer.document.soap.SOAPAddress;
import com.sun.xml.internal.ws.wsdl.writer.document.soap.SOAPFault;
import com.sun.xml.internal.ws.util.RuntimeVersion;

import javax.jws.soap.SOAPBinding.Style;
import javax.jws.soap.SOAPBinding.Use;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.ws.Holder;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


/**
 * Class used to generate WSDLs from a {@link SEIModel}.
 *
 * @author WS Development Team
 */
public class WSDLGenerator {
    private JAXWSOutputSchemaResolver resolver;
    private WSDLResolver wsdlResolver = null;
    private AbstractSEIModelImpl model;
    private Definitions serviceDefinitions;
    private Definitions portDefinitions;
    private Types types;
    /**
     * Constant String for ".wsdl"
     */
    private static final String DOT_WSDL         = ".wsdl";
    /**
     * Constant String appended to response message names
     */
    private static final String RESPONSE         = "Response";
    /**
     * constant String used for part name for wrapped request messages
     */
    private static final String PARAMETERS       = "parameters";
    /**
     * the part name for unwrappable response messages
     */
    private static final String RESULT           = "parameters";
    /**
     * the part name for response messages that are not unwrappable
     */
    private static final String UNWRAPPABLE_RESULT  = "result";
    /**
     * The WSDL namespace
     */
    private static final String WSDL_NAMESPACE   = WSDLConstants.NS_WSDL;

    /**
     * the XSD namespace
     */
    private static final String XSD_NAMESPACE    = SOAPNamespaceConstants.XSD;
    /**
     * the namespace prefix to use for the XSD namespace
     */
    private static final String XSD_PREFIX       = "xsd";
    /**
     * The SOAP 1.1 namespace
     */
    private static final String SOAP11_NAMESPACE = SOAPConstants.NS_WSDL_SOAP;
    /**
     * The SOAP 1.2 namespace
     */
    private static final String SOAP12_NAMESPACE = SOAPConstants.NS_WSDL_SOAP12;
    /**
     * The namespace prefix to use for the SOAP 1.1 namespace
     */
    private static final String SOAP_PREFIX      = "soap";
    /**
     * The namespace prefix to use for the SOAP 1.2 namespace
     */
    private static final String SOAP12_PREFIX    = "soap12";
    /**
     * The namespace prefix to use for the targetNamespace
     */
    private static final String TNS_PREFIX       = "tns";
    /**
     * The URI for the SOAP 1.1 HTTP Transport.  Used to create soapBindings
     */
    private static final String SOAP_HTTP_TRANSPORT = SOAPNamespaceConstants.TRANSPORT_HTTP;
    /**
     * The URI for the SOAP 1.2 HTTP Transport.  Used to create soapBindings
     */
    private static final String SOAP12_HTTP_TRANSPORT = SOAP12NamespaceConstants.TRANSPORT_HTTP;
    /**
     * Constant String "document" used to specify <code>document</code> style
     * soapBindings
     */
    private static final String DOCUMENT         = "document";
    /**
     * Constant String "rpc" used to specify <code>rpc</code> style
     * soapBindings
     */
    private static final String RPC              = "rpc";
    /**
     * Constant String "literal" used to create <code>literal</code> use binddings
     */
    private static final String LITERAL          = "literal";
    /**
     * Constant String to flag the URL to replace at runtime for the endpoint
     */
    private static final String REPLACE_WITH_ACTUAL_URL = "REPLACE_WITH_ACTUAL_URL";
    private Set<QName> processedExceptions = new HashSet<QName>();
    private WSBinding binding;
    private String wsdlLocation;
    private String portWSDLID;
    private String schemaPrefix;
    private WSDLGeneratorExtension extension;
    List<WSDLGeneratorExtension> extensionHandlers;

    private String endpointAddress = REPLACE_WITH_ACTUAL_URL;
    private Container container;
    private final Class implType;

    /**
     * Creates the WSDLGenerator
     * @param model The {@link AbstractSEIModelImpl} used to generate the WSDL
     * @param wsdlResolver The {@link WSDLResolver} to use resovle names while generating the WSDL
     * @param binding specifies which {@link javax.xml.ws.BindingType} to generate
     * @param extensions an array {@link WSDLGeneratorExtension} that will
     * be invoked to generate WSDL extensions
     */
    public WSDLGenerator(AbstractSEIModelImpl model, WSDLResolver wsdlResolver, WSBinding binding, Container container,
                         Class implType, WSDLGeneratorExtension... extensions) {
        this.model = model;
        resolver = new JAXWSOutputSchemaResolver();
        this.wsdlResolver = wsdlResolver;
        this.binding = binding;
        this.container = container;
        this.implType = implType;
        extensionHandlers = new ArrayList<WSDLGeneratorExtension>();

        // register handlers for default extensions
        register(new W3CAddressingWSDLGeneratorExtension());

        for (WSDLGeneratorExtension w : extensions)
            register(w);

        this.extension = new WSDLGeneratorExtensionFacade(extensionHandlers.toArray(new WSDLGeneratorExtension[0]));
    }

    /**
     * Sets the endpoint address string to be written.
     * Defaults to {@link #REPLACE_WITH_ACTUAL_URL}.
     */
    public void setEndpointAddress(String address) {
        this.endpointAddress = address;
    }

    /**
     * Performes the actual WSDL generation
     */
    public void doGeneration() {
        XmlSerializer serviceWriter;
        XmlSerializer portWriter = null;
        String fileName = JAXBRIContext.mangleNameToClassName(model.getServiceQName().getLocalPart());
        Result result = wsdlResolver.getWSDL(fileName+DOT_WSDL);
        wsdlLocation = result.getSystemId();
        serviceWriter = new CommentFilter(ResultFactory.createSerializer(result));
        if (model.getServiceQName().getNamespaceURI().equals(model.getTargetNamespace())) {
            portWriter = serviceWriter;
            schemaPrefix = fileName+"_";
        } else {
            String wsdlName = JAXBRIContext.mangleNameToClassName(model.getPortTypeName().getLocalPart());
            if (wsdlName.equals(fileName))
                wsdlName += "PortType";
            Holder<String> absWSDLName = new Holder<String>();
            absWSDLName.value = wsdlName+DOT_WSDL;
            result = wsdlResolver.getAbstractWSDL(absWSDLName);

            if (result != null) {
                portWSDLID = result.getSystemId();
                if (portWSDLID.equals(wsdlLocation)) {
                    portWriter = serviceWriter;
                } else {
                    portWriter = new CommentFilter(ResultFactory.createSerializer(result));
                }
            } else {
                portWSDLID = absWSDLName.value;
            }
            schemaPrefix = new java.io.File(portWSDLID).getName();
            int idx = schemaPrefix.lastIndexOf('.');
            if (idx > 0)
                schemaPrefix = schemaPrefix.substring(0, idx);
            schemaPrefix = JAXBRIContext.mangleNameToClassName(schemaPrefix)+"_";
        }
        generateDocument(serviceWriter, portWriter);
    }

    /**
     * Writing directly to XmlSerializer is a problem, since it doesn't suppress
     * xml declaration. Creating filter so that comment is written before TXW writes
     * anything in the WSDL.
     */
    private static class CommentFilter implements XmlSerializer {
        final XmlSerializer serializer;
        private static final String VERSION_COMMENT =
            " Generated by JAX-WS RI at http://jax-ws.dev.java.net. RI's version is "+RuntimeVersion.VERSION+". ";

        CommentFilter(XmlSerializer serializer) {
            this.serializer = serializer;
        }

        public void startDocument() {
            serializer.startDocument();
            comment(new StringBuilder(VERSION_COMMENT));
            text(new StringBuilder("\n"));
        }

        public void beginStartTag(String uri, String localName, String prefix) {
            serializer.beginStartTag(uri, localName, prefix);
        }

        public void writeAttribute(String uri, String localName, String prefix, StringBuilder value) {
            serializer.writeAttribute(uri, localName, prefix, value);
        }

        public void writeXmlns(String prefix, String uri) {
            serializer.writeXmlns(prefix, uri);
        }

        public void endStartTag(String uri, String localName, String prefix) {
            serializer.endStartTag(uri, localName, prefix);
        }

        public void endTag() {
            serializer.endTag();
        }

        public void text(StringBuilder text) {
            serializer.text(text);
        }

        public void cdata(StringBuilder text) {
            serializer.cdata(text);
        }

        public void comment(StringBuilder comment) {
            serializer.comment(comment);
        }

        public void endDocument() {
            serializer.endDocument();
        }

        public void flush() {
            serializer.flush();
        }

    }

    private void generateDocument(XmlSerializer serviceStream, XmlSerializer portStream) {
        serviceDefinitions = TXW.create(Definitions.class, serviceStream);
        serviceDefinitions._namespace(WSDL_NAMESPACE, "");//WSDL_PREFIX);
        serviceDefinitions._namespace(XSD_NAMESPACE, XSD_PREFIX);
        serviceDefinitions.targetNamespace(model.getServiceQName().getNamespaceURI());
        serviceDefinitions._namespace(model.getServiceQName().getNamespaceURI(), TNS_PREFIX);
        if(binding.getSOAPVersion()== SOAPVersion.SOAP_12)
            serviceDefinitions._namespace(SOAP12_NAMESPACE, SOAP12_PREFIX);
        else
            serviceDefinitions._namespace(SOAP11_NAMESPACE, SOAP_PREFIX);
        serviceDefinitions.name(model.getServiceQName().getLocalPart());
        WSDLGenExtnContext serviceCtx = new WSDLGenExtnContext(serviceDefinitions, model, binding, container, implType);
        extension.start(serviceCtx);
        if (serviceStream != portStream && portStream != null) {
            // generate an abstract and concrete wsdl
            portDefinitions = TXW.create(Definitions.class, portStream);
            portDefinitions._namespace(WSDL_NAMESPACE, "");//WSDL_PREFIX);
            portDefinitions._namespace(XSD_NAMESPACE, XSD_PREFIX);
            if (model.getTargetNamespace() != null) {
                portDefinitions.targetNamespace(model.getTargetNamespace());
                portDefinitions._namespace(model.getTargetNamespace(), TNS_PREFIX);
            }

            String schemaLoc = relativize(portWSDLID, wsdlLocation);
            Import _import = serviceDefinitions._import().namespace(model.getTargetNamespace());
            _import.location(schemaLoc);
        } else if (portStream != null) {
            // abstract and concrete are the same
            portDefinitions = serviceDefinitions;
        } else {
            // import a provided abstract wsdl
            String schemaLoc = relativize(portWSDLID, wsdlLocation);
            Import _import = serviceDefinitions._import().namespace(model.getTargetNamespace());
            _import.location(schemaLoc);
        }
        extension.addDefinitionsExtension(serviceDefinitions);

        if (portDefinitions != null) {
            generateTypes();
            generateMessages();
            generatePortType();
        }
        generateBinding();
        generateService();
        //Give a chance to WSDLGeneratorExtensions to write stuff before closing </wsdl:defintions>
        extension.end(serviceCtx);
        serviceDefinitions.commit();
        if (portDefinitions != null && portDefinitions != serviceDefinitions)
            portDefinitions.commit();
    }


    /**
     * Generates the types section of the WSDL
     */
    protected void generateTypes() {
        types = portDefinitions.types();
        if (model.getJAXBContext() != null) {
            try {
                model.getJAXBContext().generateSchema(resolver);
            } catch (IOException e) {
                // TODO locallize and wrap this
                e.printStackTrace();
                throw new WebServiceException(e.getMessage());
            }
        }
    }

    /**
     * Generates the WSDL messages
     */
    protected void generateMessages() {
        for (JavaMethodImpl method : model.getJavaMethods()) {
            generateSOAPMessages(method, method.getBinding());
        }
    }

   /**
     * Generates messages for a SOAPBinding
     * @param method The {@link JavaMethod} to generate messages for
     * @param binding The {@link com.sun.xml.internal.ws.api.model.soap.SOAPBinding} to add the generated messages to
     */
    protected void generateSOAPMessages(JavaMethodImpl method, com.sun.xml.internal.ws.api.model.soap.SOAPBinding binding) {
        boolean isDoclit = binding.isDocLit();
//        Message message = portDefinitions.message().name(method.getOperation().getName().getLocalPart());
        Message message = portDefinitions.message().name(method.getRequestMessageName());
        extension.addInputMessageExtension(message, method);
        com.sun.xml.internal.ws.wsdl.writer.document.Part part;
        JAXBRIContext jaxbContext = model.getJAXBContext();
        boolean unwrappable = true;
        for (ParameterImpl param : method.getRequestParameters()) {
            if (isDoclit) {
                if (isHeaderParameter(param))
                    unwrappable = false;
                if (param.isWrapperStyle()) {
                    part = message.part().name(PARAMETERS);
                    part.element(param.getName());
                } else {
                    part = message.part().name(param.getPartName());
                    part.element(param.getName());
                }
            } else {
                if (param.isWrapperStyle()) {
                    for (ParameterImpl childParam : ((WrapperParameter)param).getWrapperChildren()) {
                        part = message.part().name(childParam.getPartName());
                        part.type(jaxbContext.getTypeName(childParam.getBridge().getTypeReference()));
                    }
                } else {
                    part = message.part().name(param.getPartName());
                    part.element(param.getName());
                }
            }
        }
        if (method.getMEP() != MEP.ONE_WAY) {
//            message = portDefinitions.message().name(method.getOperation().getName().getLocalPart()+RESPONSE);
            message = portDefinitions.message().name(method.getResponseMessageName());
            extension.addOutputMessageExtension(message, method);
            if (unwrappable) {
                for (ParameterImpl param : method.getResponseParameters()) {
                   if (isHeaderParameter(param))
                       unwrappable = false;
                }
            }

            for (ParameterImpl param : method.getResponseParameters()) {
                if (isDoclit) {
                    if (param.isWrapperStyle()) {
                        // if its not really wrapper style dont use the same name as input message
                        if (unwrappable)
                            part = message.part().name(RESULT);
                        else
                            part = message.part().name(UNWRAPPABLE_RESULT);
                        part.element(param.getName());
                    } else {
                        part = message.part().name(param.getPartName());
                        part.element(param.getName());
                    }
                } else {
                    if (param.isWrapperStyle()) {
                        for (ParameterImpl childParam : ((WrapperParameter)param).getWrapperChildren()) {
                            part = message.part().name(childParam.getPartName());
                            part.type(jaxbContext.getTypeName(childParam.getBridge().getTypeReference()));
                        }
                    } else {
                        part = message.part().name(param.getPartName());
                        part.element(param.getName());
                    }
                }
            }
        }
        for (CheckedExceptionImpl exception : method.getCheckedExceptions()) {
            QName tagName = exception.getDetailType().tagName;
            String messageName = exception.getMessageName();
            QName messageQName = new QName(model.getTargetNamespace(), messageName);
            if (processedExceptions.contains(messageQName))
                continue;
            message = portDefinitions.message().name(messageName);

            extension.addFaultMessageExtension(message, method, exception);
            part = message.part().name("fault");//tagName.getLocalPart());
            part.element(tagName);
            processedExceptions.add(messageQName);
        }
    }

    /**
     * Generates the WSDL portType
     */
    protected void generatePortType() {

        PortType portType = portDefinitions.portType().name(model.getPortTypeName().getLocalPart());
        extension.addPortTypeExtension(portType);
        for (JavaMethodImpl method : model.getJavaMethods()) {
//            Operation operation = portType.operation().name(method.getOperation().getLocalName());
            Operation operation = portType.operation().name(method.getOperationName());
            generateParameterOrder(operation, method);
            switch (method.getMEP()) {
                case REQUEST_RESPONSE:
                    // input message
                    generateInputMessage(operation, method);
                    // output message
                    generateOutputMessage(operation, method);
                    break;
                case ONE_WAY:
                    generateInputMessage(operation, method);
                    break;
            }
            // faults
            for (CheckedExceptionImpl exception : method.getCheckedExceptions()) {
                QName messageName = new QName(model.getTargetNamespace(), exception.getMessageName());
                FaultType paramType = operation.fault().message(messageName).name(exception.getMessageName());
                extension.addOperationFaultExtension(paramType, method, exception);
            }
        }
    }

    /**
     * Determines if the <CODE>method</CODE> is wrapper style
     * @param method The {@link JavaMethod} to check if it is wrapper style
     * @return true if the method is wrapper style, otherwise, false.
     */
    protected boolean isWrapperStyle(JavaMethodImpl method) {
        if (method.getRequestParameters().size() > 0) {
            ParameterImpl param = method.getRequestParameters().iterator().next();
            return param.isWrapperStyle();
        }
        return false;
    }

    /**
     * Determines if a {@link JavaMethod} is rpc/literal
     * @param method The method to check
     * @return true if method is rpc/literal, otherwise, false
     */
    protected boolean isRpcLit(JavaMethodImpl method) {
        return method.getBinding().getStyle() == Style.RPC;
    }

    /**
     * Generates the parameterOrder for a PortType operation
     * @param operation The operation to generate the parameterOrder for
     * @param method The {@link JavaMethod} to generate the parameterOrder from
     */
    protected void generateParameterOrder(Operation operation, JavaMethodImpl method) {
        if (method.getMEP() == MEP.ONE_WAY)
            return;
        if (isRpcLit(method))
            generateRpcParameterOrder(operation, method);
        else
            generateDocumentParameterOrder(operation, method);
    }

    /**
     * Generates the parameterOrder for a PortType operation
     * @param operation the operation to generate the parameterOrder for
     * @param method the {@link JavaMethod} to generate the parameterOrder from
     */
    protected void generateRpcParameterOrder(Operation operation, JavaMethodImpl method) {
        String partName;
        StringBuffer paramOrder = new StringBuffer();
        Set<String> partNames = new HashSet<String>();
        List<ParameterImpl> sortedParams = sortMethodParameters(method);
        int i = 0;
        for (ParameterImpl parameter : sortedParams) {
            if (parameter.getIndex() >= 0) {
               partName = parameter.getPartName();
                if (!partNames.contains(partName)) {
                    if (i++ > 0)
                        paramOrder.append(' ');
                    paramOrder.append(partName);
                    partNames.add(partName);
                }
            }
        }
        operation.parameterOrder(paramOrder.toString());
    }


    /**
     * Generates the parameterOrder for a PortType operation
     * @param operation the operation to generate the parameterOrder for
     * @param method the {@link JavaMethod} to generate the parameterOrder from
     */
    protected void generateDocumentParameterOrder(Operation operation, JavaMethodImpl method) {
        String partName;
        StringBuffer paramOrder = new StringBuffer();
        Set<String> partNames = new HashSet<String>();
        List<ParameterImpl> sortedParams = sortMethodParameters(method);
        boolean isWrapperStyle = isWrapperStyle(method);
        int i = 0;
        for (ParameterImpl parameter : sortedParams) {
//            System.out.println("param: "+parameter.getIndex()+" name: "+parameter.getName().getLocalPart());
            if (parameter.getIndex() < 0)
                continue;
            if (isWrapperStyle && isBodyParameter(parameter)) {
//                System.out.println("isWrapper and is body");
                if (method.getRequestParameters().contains(parameter))
                    partName = PARAMETERS;
                else {
                    // really make sure this is a wrapper style wsdl we are creating
                    partName = RESPONSE;
                }
            } else {
               partName = parameter.getPartName();
            }
            if (!partNames.contains(partName)) {
                if (i++ > 0)
                    paramOrder.append(' ');
                paramOrder.append(partName);
                partNames.add(partName);
            }
        }
        if (i>1) {
            operation.parameterOrder(paramOrder.toString());
        }
    }

    /**
     * Sorts the parameters for the method by their position
     * @param method the {@link JavaMethod} used to sort the parameters
     * @return the sorted {@link List} of parameters
     */
    protected List<ParameterImpl> sortMethodParameters(JavaMethodImpl method) {
        Set<ParameterImpl> paramSet = new HashSet<ParameterImpl>();
        List<ParameterImpl> sortedParams = new ArrayList<ParameterImpl>();
        if (isRpcLit(method)) {
            for (ParameterImpl param : method.getRequestParameters()) {
                if (param instanceof WrapperParameter) {
                    paramSet.addAll(((WrapperParameter)param).getWrapperChildren());
                } else {
                    paramSet.add(param);
                }
            }
            for (ParameterImpl param : method.getResponseParameters()) {
                if (param instanceof WrapperParameter) {
                    paramSet.addAll(((WrapperParameter)param).getWrapperChildren());
                } else {
                    paramSet.add(param);
                }
            }
        } else  {
            paramSet.addAll(method.getRequestParameters());
            paramSet.addAll(method.getResponseParameters());
        }
        Iterator<ParameterImpl>params = paramSet.iterator();
        if (paramSet.size() == 0)
            return sortedParams;
        ParameterImpl param = params.next();
        sortedParams.add(param);
        ParameterImpl sortedParam;
        int pos;
        for (int i=1; i<paramSet.size();i++) {
            param = params.next();
            for (pos=0; pos<i; pos++) {
                sortedParam = sortedParams.get(pos);
                if (param.getIndex() == sortedParam.getIndex() &&
                    param instanceof WrapperParameter)
                    break;
                if (param.getIndex() < sortedParam.getIndex()) {
                    break;
                }
            }
            sortedParams.add(pos, param);
        }
        return sortedParams;
    }

    /**
     * Determines if a parameter is associated with the message Body
     * @param parameter the parameter to check
     * @return true if the parameter is a <code>body</code> parameter
     */
    protected boolean isBodyParameter(ParameterImpl parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isBody();
    }

    protected boolean isHeaderParameter(ParameterImpl parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isHeader();
    }

    protected boolean isAttachmentParameter(ParameterImpl parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isAttachment();
    }


    /**
     * Generates the Binding section of the WSDL
     */
    protected void generateBinding() {
        Binding binding = serviceDefinitions.binding().name(model.getBoundPortTypeName().getLocalPart());
        extension.addBindingExtension(binding);
        binding.type(model.getPortTypeName());
        boolean first = true;
        for (JavaMethodImpl method : model.getJavaMethods()) {
            if (first) {
                SOAPBinding sBinding = method.getBinding();
                SOAPVersion soapVersion = sBinding.getSOAPVersion();
                if (soapVersion == SOAPVersion.SOAP_12){
                    com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPBinding soapBinding = binding.soap12Binding();
                    soapBinding.transport(SOAP12_HTTP_TRANSPORT);
                    if (sBinding.getStyle().equals(Style.DOCUMENT))
                        soapBinding.style(DOCUMENT);
                    else
                        soapBinding.style(RPC);
                } else {
                com.sun.xml.internal.ws.wsdl.writer.document.soap.SOAPBinding soapBinding = binding.soapBinding();
                    soapBinding.transport(SOAP_HTTP_TRANSPORT);
                    if (sBinding.getStyle().equals(Style.DOCUMENT))
                        soapBinding.style(DOCUMENT);
                    else
                        soapBinding.style(RPC);
                }
                first = false;
            }
            if(this.binding.getBindingId().getSOAPVersion()==SOAPVersion.SOAP_12)
                generateSOAP12BindingOperation(method, binding);
            else
                generateBindingOperation(method, binding);
        }
    }

    protected void generateBindingOperation(JavaMethodImpl method, Binding binding) {
        BindingOperationType operation = binding.operation().name(method.getOperationName());
        extension.addBindingOperationExtension(operation, method);
        String targetNamespace = model.getTargetNamespace();
        QName requestMessage = new QName(targetNamespace, method.getOperationName());
        List<ParameterImpl> bodyParams = new ArrayList<ParameterImpl>();
        List<ParameterImpl> headerParams = new ArrayList<ParameterImpl>();
        splitParameters(bodyParams, headerParams, method.getRequestParameters());
        SOAPBinding soapBinding = method.getBinding();
        operation.soapOperation().soapAction(soapBinding.getSOAPAction());

        // input
        TypedXmlWriter input = operation.input();
        extension.addBindingOperationInputExtension(input, method);
        BodyType body = input._element(Body.class);
        boolean isRpc = soapBinding.getStyle().equals(Style.RPC);
        if (soapBinding.getUse()==Use.LITERAL) {
            body.use(LITERAL);
            if (headerParams.size() > 0) {
                if (bodyParams.size() > 0) {
                    ParameterImpl param = bodyParams.iterator().next();
                    if (isRpc) {
                        StringBuffer parts = new StringBuffer();
                        int i=0;
                        for (ParameterImpl parameter : ((WrapperParameter)param).getWrapperChildren()) {
                            if (i++>0)
                                parts.append(' ');
                            parts.append(parameter.getPartName());
                        }
                        body.parts(parts.toString());
                    } else if (param.isWrapperStyle()) {
                        body.parts(PARAMETERS);
                    } else {
                       body.parts(param.getPartName());
                    }
                } else {
                    body.parts("");
                }
                generateSOAPHeaders(input, headerParams, requestMessage);
            }
            if (isRpc) {
                body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
            }
        } else {
            // TODO localize this
            throw new WebServiceException("encoded use is not supported");
        }

        if (method.getMEP() != MEP.ONE_WAY) {
            boolean unwrappable = headerParams.size() == 0;
            // output
            bodyParams.clear();
            headerParams.clear();
            splitParameters(bodyParams, headerParams, method.getResponseParameters());
            unwrappable = unwrappable ? headerParams.size() == 0 : unwrappable;
            TypedXmlWriter output = operation.output();
            extension.addBindingOperationOutputExtension(output, method);
            body = output._element(Body.class);
            body.use(LITERAL);
            if (headerParams.size() > 0) {
                String parts = "";
                if (bodyParams.size() > 0) {
                    ParameterImpl param = bodyParams.iterator().hasNext() ? bodyParams.iterator().next() : null;
                    if(param!=null){
                        if (isRpc) {
                            int i=0;
                            for (ParameterImpl parameter : ((WrapperParameter)param).getWrapperChildren()) {
                                if (i++>0)
                                    parts += " ";
                                parts += parameter.getPartName();
                            }
                        } else {
                            if (param.isWrapperStyle()) {
                                // if its not really wrapper style dont use the same name as input message
                                if (unwrappable)
                                    parts = RESULT;
                                else
                                    parts = UNWRAPPABLE_RESULT;
                            } else {
                                parts = param.getPartName();
                            }
                        }
                    }
                }
                body.parts(parts);
                QName responseMessage = new QName(targetNamespace, method.getResponseMessageName());
                generateSOAPHeaders(output, headerParams, responseMessage);
            }
            if (isRpc) {
                body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
            }
        }
        for (CheckedExceptionImpl exception : method.getCheckedExceptions()) {
            Fault fault = operation.fault().name(exception.getMessageName());
            SOAPFault soapFault = fault._element(SOAPFault.class).name(exception.getMessageName());
            soapFault.use(LITERAL);
        }
    }

    protected void generateSOAP12BindingOperation(JavaMethodImpl method, Binding binding) {
        BindingOperationType operation = binding.operation().name(method.getOperationName());
        String targetNamespace = model.getTargetNamespace();
        QName requestMessage = new QName(targetNamespace, method.getOperationName());

        ArrayList<ParameterImpl> bodyParams = new ArrayList<ParameterImpl>();
        ArrayList<ParameterImpl> headerParams = new ArrayList<ParameterImpl>();
        splitParameters(bodyParams, headerParams, method.getRequestParameters());
        SOAPBinding soapBinding = method.getBinding();
        operation.soap12Operation().soapAction(soapBinding.getSOAPAction());

        // input
        TypedXmlWriter input = operation.input();

        com.sun.xml.internal.ws.wsdl.writer.document.soap12.BodyType body = input._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.Body.class);
        boolean isRpc = soapBinding.getStyle().equals(Style.RPC);
        if (soapBinding.getUse().equals(Use.LITERAL)) {
            body.use(LITERAL);
            if (headerParams.size() > 0) {
                if (bodyParams.size() > 0) {
                    ParameterImpl param = bodyParams.iterator().next();
                    if (isRpc) {
                        StringBuffer parts = new StringBuffer();
                        int i=0;
                        for (ParameterImpl parameter : ((WrapperParameter)param).getWrapperChildren()) {
                            if (i++>0)
                                parts.append(' ');
                            parts.append(parameter.getPartName());
                        }
                        body.parts(parts.toString());
                    } else if (param.isWrapperStyle()) {
                        body.parts(PARAMETERS);
                    } else {
                       body.parts(param.getPartName());
                    }
                } else {
                    body.parts("");
                }
                generateSOAP12Headers(input, headerParams, requestMessage);
            }
            if (isRpc) {
                body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
            }
        } else {
            // TODO localize this
            throw new WebServiceException("encoded use is not supported");
        }

        if (method.getMEP() != MEP.ONE_WAY) {
            // output
            boolean unwrappable = headerParams.size() == 0;
            bodyParams.clear();
            headerParams.clear();
            splitParameters(bodyParams, headerParams, method.getResponseParameters());
            unwrappable = unwrappable ? headerParams.size() == 0 : unwrappable;
            TypedXmlWriter output = operation.output();
            body = output._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.Body.class);
            body.use(LITERAL);
            if (headerParams.size() > 0) {
                if (bodyParams.size() > 0) {
                    ParameterImpl param = bodyParams.iterator().next();
                    if (isRpc) {
                        String parts = "";
                        int i=0;
                        for (ParameterImpl parameter : ((WrapperParameter)param).getWrapperChildren()) {
                            if (i++>0)
                                parts += " ";
                            parts += parameter.getPartName();
                        }
                        body.parts(parts);
                    } else if (param.isWrapperStyle()) {
                        // if its not really wrapper style dont use the same name as input message
                        if (unwrappable)
                            body.parts(RESULT);
                        else
                            body.parts(UNWRAPPABLE_RESULT);
                    } else {
                        body.parts(param.getPartName());
                    }
                } else {
                    body.parts("");
                }
                QName responseMessage = new QName(targetNamespace, method.getResponseMessageName());
                generateSOAP12Headers(output, headerParams, responseMessage);
            }
            if (isRpc) {
                body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
            }
        }
        for (CheckedExceptionImpl exception : method.getCheckedExceptions()) {
            Fault fault = operation.fault().name(exception.getMessageName());
            com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault soapFault = fault._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault.class).name(exception.getMessageName());
            soapFault.use(LITERAL);
        }
    }

    /**
     *
     * @param bodyParams
     * @param headerParams
     * @param params
     */
    protected void splitParameters(List<ParameterImpl> bodyParams, List<ParameterImpl>headerParams, List<ParameterImpl>params) {
        for (ParameterImpl parameter : params) {
            if (isBodyParameter(parameter)) {
                bodyParams.add(parameter);
            } else {
                headerParams.add(parameter);
            }
        }
    }

    /**
     *
     * @param writer
     * @param parameters
     * @param message
     */
    protected void generateSOAPHeaders(TypedXmlWriter writer, List<ParameterImpl> parameters, QName message) {

        for (ParameterImpl headerParam : parameters) {
            Header header = writer._element(Header.class);
            header.message(message);
            header.part(headerParam.getPartName());
            header.use(LITERAL);
        }
    }

    /**
     *
     * @param writer
     * @param parameters
     * @param message
     */
    protected void generateSOAP12Headers(TypedXmlWriter writer, List<ParameterImpl> parameters, QName message) {

        for (ParameterImpl headerParam : parameters) {
            com.sun.xml.internal.ws.wsdl.writer.document.soap12.Header header = writer._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.Header.class);
            header.message(message);


            header.part(headerParam.getPartName());
            header.use(LITERAL);
        }
    }

    /**
     * Generates the Service section of the WSDL
     */
    protected void generateService() {
        QName portQName = model.getPortName();
        QName serviceQName = model.getServiceQName();
        Service service = serviceDefinitions.service().name(serviceQName.getLocalPart());
        extension.addServiceExtension(service);
        Port port = service.port().name(portQName.getLocalPart());
        port.binding(model.getBoundPortTypeName());
        extension.addPortExtension(port);
        if (model.getJavaMethods().size() == 0)
            return;

        if(this.binding.getBindingId().getSOAPVersion()== SOAPVersion.SOAP_12){
            com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPAddress address = port._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPAddress.class);
            address.location(endpointAddress);
        }else{
            SOAPAddress address = port._element(SOAPAddress.class);
            address.location(endpointAddress);
        }
    }

    /**
     *
     * @param operation
     * @param method
     */
    protected void generateInputMessage(Operation operation, JavaMethodImpl method) {
        ParamType paramType = operation.input();
        extension.addOperationInputExtension(paramType, method);
//        paramType.message(method.getOperation().getName());
        paramType.message(new QName(model.getTargetNamespace(), method.getRequestMessageName()));
    }

    /**
     *
     * @param operation
     * @param method
     */
    protected void generateOutputMessage(Operation operation, JavaMethodImpl method) {
        ParamType paramType = operation.output();
        extension.addOperationOutputExtension(paramType, method);
//        paramType.message(new QName(model.getTargetNamespace(), method.getOperation().getLocalName()+RESPONSE));
        paramType.message(new QName(model.getTargetNamespace(), method.getResponseMessageName()));
    }

    /**
     * Creates the {@link Result} object used by JAXB to generate a schema for the
     * namesapceUri namespace.
     * @param namespaceUri The namespace for the schema being generated
     * @param suggestedFileName the JAXB suggested file name for the schema file
     * @return the {@link Result} for JAXB to generate the schema into
     * @throws java.io.IOException thrown if on IO error occurs
     */
    public Result createOutputFile(String namespaceUri, String suggestedFileName) throws IOException {
        Result result;
        if (namespaceUri.equals("")) {
            return null;
        }
        com.sun.xml.internal.ws.wsdl.writer.document.xsd.Import _import = types.schema()._import().namespace(namespaceUri);

        Holder<String> fileNameHolder = new Holder<String>();
        fileNameHolder.value = schemaPrefix+suggestedFileName;
        result = wsdlResolver.getSchemaOutput(namespaceUri, fileNameHolder);
//        System.out.println("schema file: "+fileNameHolder.value);
//        System.out.println("result: "+result);
        String schemaLoc;
        if (result == null)
            schemaLoc = fileNameHolder.value;
        else
            schemaLoc = relativize(result.getSystemId(), wsdlLocation);
//        System.out.println("schemaLoca: "+schemaLoc);
        _import.schemaLocation(schemaLoc);
        return result;
    }

   /**
     * Relativizes a URI by using another URI (base URI.)
     *
     * <p>
     * For example, {@code relative("http://www.sun.com/abc/def","http://www.sun.com/pqr/stu") => "../abc/def"}
     *
     * <p>
     * This method only works on hierarchical URI's, not opaque URI's (refer to the
     * <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/net/URI.html">java.net.URI</a>
     * javadoc for complete definitions of these terms.
     *
     * <p>
     * This method will not normalize the relative URI.
     * @param uri the URI to relativize
     *
     *
     * @param baseUri the base URI to use for the relativization
     * @return the relative URI or the original URI if a relative one could not be computed
     */
    protected static String relativize(String uri, String baseUri) {
        try {
            assert uri!=null;

            if(baseUri==null)   return uri;

            URI theUri = new URI(escapeURI(uri));
            URI theBaseUri = new URI(escapeURI(baseUri));

            if (theUri.isOpaque() || theBaseUri.isOpaque())
                return uri;

            if (!equalsIgnoreCase(theUri.getScheme(), theBaseUri.getScheme()) ||
                    !equal(theUri.getAuthority(), theBaseUri.getAuthority()))
                return uri;

            String uriPath = theUri.getPath();
            String basePath = theBaseUri.getPath();

            // normalize base path
            if (!basePath.endsWith("/")) {
                basePath = normalizeUriPath(basePath);
            }

            if( uriPath.equals(basePath))
                return ".";

            String relPath = calculateRelativePath(uriPath, basePath);

            if (relPath == null)
                return uri; // recursion found no commonality in the two uris at all
            StringBuffer relUri = new StringBuffer();
            relUri.append(relPath);
            if (theUri.getQuery() != null)
                relUri.append('?').append(theUri.getQuery());
            if (theUri.getFragment() != null)
                relUri.append('#').append(theUri.getFragment());

            return relUri.toString();
        } catch (URISyntaxException e) {
            throw new InternalError("Error escaping one of these uris:\n\t"+uri+"\n\t"+baseUri);
        }
    }

    private static String calculateRelativePath(String uri, String base) {
        if (base == null) {
            return null;
        }
        if (uri.startsWith(base)) {
            return uri.substring(base.length());
        } else {
            return "../" + calculateRelativePath(uri, getParentUriPath(base));
        }
    }


    /**
     * Implements the SchemaOutputResolver used by JAXB to
     */
    protected class JAXWSOutputSchemaResolver extends SchemaOutputResolver {

        /**
         * Creates the {@link Result} object used by JAXB to generate a schema for the
         * namesapceUri namespace.
         * @param namespaceUri The namespace for the schema being generated
         * @param suggestedFileName the JAXB suggested file name for the schema file
         * @return the {@link Result} for JAXB to generate the schema into
         * @throws java.io.IOException thrown if on IO error occurs
         */
        public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
            return createOutputFile(namespaceUri, suggestedFileName);
        }
    }

    private void register(WSDLGeneratorExtension h) {
        extensionHandlers.add(h);
    }
}
