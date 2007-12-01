/*
 * Portions Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
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


import com.sun.xml.internal.ws.pept.presentation.MessageStruct;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import static com.sun.xml.internal.bind.v2.schemagen.Util.*;
import com.sun.xml.internal.txw2.TXW;
import com.sun.xml.internal.txw2.TypedXmlWriter;
import com.sun.xml.internal.txw2.output.ResultFactory;
import com.sun.xml.internal.txw2.output.XmlSerializer;
import com.sun.xml.internal.ws.encoding.soap.SOAPVersion;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAPNamespaceConstants;
import com.sun.xml.internal.ws.encoding.soap.streaming.SOAP12NamespaceConstants;
import com.sun.xml.internal.ws.model.CheckedException;
import com.sun.xml.internal.ws.model.JavaMethod;
import com.sun.xml.internal.ws.model.Parameter;
import com.sun.xml.internal.ws.model.ParameterBinding;
import com.sun.xml.internal.ws.model.RuntimeModel;
import com.sun.xml.internal.ws.model.WrapperParameter;
import com.sun.xml.internal.ws.model.soap.SOAPBinding;
import com.sun.xml.internal.ws.model.soap.Style;
import com.sun.xml.internal.ws.model.soap.Use;
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
 * Class used to generate WSDLs from a <code>RunTimeModel</code>
 *
 * @author WS Development Team
 */
public class WSDLGenerator {
    private JAXWSOutputSchemaResolver resolver;
    private WSDLOutputResolver wsdlResolver = null;
    private RuntimeModel model;
    private Definitions serviceDefinitions;
    private Definitions portDefinitions;
    private Types types;
    public static final String DOT_WSDL         = ".wsdl";
    public static final String RESPONSE         = "Response";
    public static final String PARAMETERS       = "parameters";
    public static final String RESULT           = "parameters";
    public static final String UNWRAPPABLE_RESULT  = "result";
    public static final String WSDL_NAMESPACE   = WSDLConstants.NS_WSDL;
    public static final String WSDL_PREFIX      = "wsdl";
    public static final String XSD_NAMESPACE    = SOAPNamespaceConstants.XSD;
    public static final String XSD_PREFIX       = "xsd";
    public static final String SOAP11_NAMESPACE = SOAPConstants.NS_WSDL_SOAP;
    public static final String SOAP12_NAMESPACE = SOAPConstants.NS_WSDL_SOAP12;
    public static final String SOAP_PREFIX      = "soap";
    public static final String SOAP12_PREFIX    = "soap12";
    public static final String TNS_PREFIX       = "tns";
    public static final String BINDING          = "Binding";
    public static final String SOAP_HTTP_TRANSPORT = SOAPNamespaceConstants.TRANSPORT_HTTP;
    public static final String SOAP12_HTTP_TRANSPORT = SOAP12NamespaceConstants.TRANSPORT_HTTP;
    public static final String DOCUMENT         = "document";
    public static final String RPC              = "rpc";
    public static final String LITERAL          = "literal";
    public static final String REPLACE_WITH_ACTUAL_URL = "REPLACE_WITH_ACTUAL_URL";
    private Set<QName> processedExceptions = new HashSet<QName>();
    private String bindingId;
    private String wsdlLocation;
    private String portWSDLID;
    private String schemaPrefix;


    public WSDLGenerator(RuntimeModel model, WSDLOutputResolver wsdlResolver, String bindingId) {
        this.model = model;
        resolver = new JAXWSOutputSchemaResolver();
        this.wsdlResolver = wsdlResolver;
        this.bindingId = bindingId;
    }

    public void doGeneration() {
        XmlSerializer serviceWriter;
        XmlSerializer portWriter = null;
        String fileName = JAXBRIContext.mangleNameToClassName(model.getServiceQName().getLocalPart());
//        System.out.println("concrete name: "+ fileName);
        Result result = wsdlResolver.getWSDLOutput(fileName+DOT_WSDL);
        if (result == null)
            return;
        wsdlLocation = result.getSystemId();
        serviceWriter = ResultFactory.createSerializer(result);
        if (model.getServiceQName().getNamespaceURI().equals(model.getTargetNamespace())) {
            portWriter = serviceWriter;
            schemaPrefix = fileName+"_";
        } else {
            String wsdlName = JAXBRIContext.mangleNameToClassName(model.getPortTypeName().getLocalPart());
            if (wsdlName.equals(fileName))
                wsdlName += "PortType";
//            System.out.println("abstract name: "+ wsdlName);
            Holder<String> absWSDLName = new Holder<String>();
            absWSDLName.value = wsdlName+DOT_WSDL;
//            System.out.println("absWSDLName.value: "+ absWSDLName.value);
            result = wsdlResolver.getAbstractWSDLOutput(absWSDLName);
//            System.out.println("absWSDLName.value: "+ absWSDLName.value);
//             schemaPrefix = model.getJAXBContext().mangleNameToClassName(portWSDLID)+"_";

            if (result != null) {
                portWSDLID = result.getSystemId();
                if (portWSDLID.equals(wsdlLocation)) {
                    portWriter = serviceWriter;
                } else {
                    portWriter = ResultFactory.createSerializer(result);
                }
            } else {
                portWSDLID = absWSDLName.value;
            }
            schemaPrefix = new java.io.File(portWSDLID).getName();
            int idx = schemaPrefix.lastIndexOf('.');
            if (idx > 0)
                schemaPrefix = schemaPrefix.substring(0, idx);
            schemaPrefix = JAXBRIContext.mangleNameToClassName(schemaPrefix)+"_";
//            System.out.println("portWSDLID: "+ portWSDLID);
//            schemaPrefix = model.getJAXBContext().mangleNameToClassName(portWSDLID)+"_";
//            System.out.println("schemaPrefix: "+ schemaPrefix);
        }
        generateDocument(serviceWriter, portWriter);
    }

    private void generateDocument(XmlSerializer serviceStream, XmlSerializer portStream) {
        serviceDefinitions = TXW.create(Definitions.class, serviceStream);
        serviceDefinitions._namespace(WSDL_NAMESPACE, "");//WSDL_PREFIX);
        serviceDefinitions._namespace(XSD_NAMESPACE, XSD_PREFIX);
        serviceDefinitions.targetNamespace(model.getServiceQName().getNamespaceURI());
        serviceDefinitions._namespace(model.getServiceQName().getNamespaceURI(), TNS_PREFIX);
        if(bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING))
            serviceDefinitions._namespace(SOAP12_NAMESPACE, SOAP12_PREFIX);
        else
            serviceDefinitions._namespace(SOAP11_NAMESPACE, SOAP_PREFIX);
        serviceDefinitions.name(model.getServiceQName().getLocalPart());
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

        if (portDefinitions != null) {
            generateTypes();
            generateMessages();
            generatePortType();
        }
        generateBinding();
        generateService();
        serviceDefinitions.commit();
        if (portDefinitions != null && portDefinitions != serviceDefinitions)
            portDefinitions.commit();
    }



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

    protected void generateMessages() {
        for (JavaMethod method : model.getJavaMethods()) {
            if (method.getBinding() instanceof SOAPBinding)
                generateSOAPMessages(method, (SOAPBinding)method.getBinding());
        }
    }

    protected void generateSOAPMessages(JavaMethod method, SOAPBinding binding) {
        boolean isDoclit = binding.isDocLit();
        Message message = portDefinitions.message().name(method.getOperationName());
        com.sun.xml.internal.ws.wsdl.writer.document.Part part;
        JAXBRIContext jaxbContext = model.getJAXBContext();
        boolean unwrappable = true;
        for (Parameter param : method.getRequestParameters()) {
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
                    for (Parameter childParam : ((WrapperParameter)param).getWrapperChildren()) {
                        part = message.part().name(childParam.getPartName());
                        part.type(jaxbContext.getTypeName(childParam.getTypeReference()));
                    }
                } else {
                    part = message.part().name(param.getPartName());
                    part.element(param.getName());
                }
            }
        }
        if (method.getMEP() != MessageStruct.ONE_WAY_MEP) {
            message = portDefinitions.message().name(method.getOperationName()+RESPONSE);
            if (unwrappable) {
                for (Parameter param : method.getResponseParameters()) {
                   if (isHeaderParameter(param))
                       unwrappable = false;
                }
            }

            for (Parameter param : method.getResponseParameters()) {
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
                        for (Parameter childParam : ((WrapperParameter)param).getWrapperChildren()) {
                            part = message.part().name(childParam.getPartName());
                            part.type(jaxbContext.getTypeName(childParam.getTypeReference()));
                        }
                    } else {
                        part = message.part().name(param.getPartName());
                        part.element(param.getName());
                    }
                }
            }
        }
        for (CheckedException exception : method.getCheckedExceptions()) {
            QName tagName = exception.getDetailType().tagName;
//            if (processedExceptions.contains(tagName))
//                continue;
//            message = portDefinitions.message().name(tagName.getLocalPart());
            String messageName = exception.getMessageName();
            QName messageQName = new QName(model.getTargetNamespace(), messageName);
            if (processedExceptions.contains(messageQName))
                continue;
            message = portDefinitions.message().name(messageName);

            part = message.part().name("fault");//tagName.getLocalPart());
            part.element(tagName);
//            processedExceptions.add(tagName);
            processedExceptions.add(messageQName);
        }
    }

    protected void generatePortType() {

        PortType portType = portDefinitions.portType().name(model.getPortTypeName().getLocalPart());
        for (JavaMethod method : model.getJavaMethods()) {
            Operation operation = portType.operation().name(method.getOperationName());
            generateParameterOrder(operation, method);
            switch (method.getMEP()) {
                case MessageStruct.REQUEST_RESPONSE_MEP:
                    // input message
                    generateInputMessage(operation, method);
                    // output message
                    generateOutputMessage(operation, method);
                    break;
                case MessageStruct.ONE_WAY_MEP:
                    generateInputMessage(operation, method);
                    break;
            }
            // faults
            for (CheckedException exception : method.getCheckedExceptions()) {
                QName tagName = exception.getDetailType().tagName;
//                QName messageName = new QName(model.getTargetNamespace(), tagName.getLocalPart());
//                FaultType paramType = operation.fault().name(tagName.getLocalPart()).message(messageName);
                QName messageName = new QName(model.getTargetNamespace(), exception.getMessageName());
                FaultType paramType = operation.fault().name(exception.getMessageName()).message(messageName);
            }
        }
    }

    protected boolean isWrapperStyle(JavaMethod method) {
        if (method.getRequestParameters().size() > 0) {
            Parameter param = method.getRequestParameters().iterator().next();
            return param.isWrapperStyle();
        }
        return false;
    }

    protected boolean isRpcLit(JavaMethod method) {
        if (method.getBinding() instanceof SOAPBinding) {
            if (((SOAPBinding)method.getBinding()).getStyle().equals(Style.RPC))
                return true;
        }
        return false;
    }

    protected void generateParameterOrder(Operation operation, JavaMethod method) {
        if (method.getMEP() == MessageStruct.ONE_WAY_MEP)
            return;
        if (isRpcLit(method))
            generateRpcParameterOrder(operation, method);
        else
            generateDocumentParameterOrder(operation, method);
    }

    protected void generateRpcParameterOrder(Operation operation, JavaMethod method) {
        String partName;
        StringBuffer paramOrder = new StringBuffer();
        Set<String> partNames = new HashSet<String>();
        List<Parameter> sortedParams = sortMethodParameters(method);
        int i = 0;
        for (Parameter parameter : sortedParams) {
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


    protected void generateDocumentParameterOrder(Operation operation, JavaMethod method) {
        String partName;
        StringBuffer paramOrder = new StringBuffer();
        Set<String> partNames = new HashSet<String>();
        List<Parameter> sortedParams = sortMethodParameters(method);
        boolean isWrapperStyle = isWrapperStyle(method);
        int i = 0;
        for (Parameter parameter : sortedParams) {
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

    protected List<Parameter> sortMethodParameters(JavaMethod method) {
        Set<Parameter> paramSet = new HashSet<Parameter>();
        List<Parameter> sortedParams = new ArrayList<Parameter>();
        if (isRpcLit(method)) {
            for (Parameter param : method.getRequestParameters()) {
                if (param instanceof WrapperParameter) {
                    paramSet.addAll(((WrapperParameter)param).getWrapperChildren());
                } else {
                    paramSet.add(param);
                }
            }
            for (Parameter param : method.getResponseParameters()) {
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
        Iterator<Parameter>params = paramSet.iterator();
        if (paramSet.size() == 0)
            return sortedParams;
        Parameter param = params.next();
        sortedParams.add(param);
        Parameter sortedParam;
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

    protected boolean isBodyParameter(Parameter parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isBody();
    }

    protected boolean isHeaderParameter(Parameter parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isHeader();
    }

    protected boolean isAttachmentParameter(Parameter parameter) {
        ParameterBinding paramBinding = parameter.getBinding();
        return paramBinding.isAttachment();
    }


    protected void generateBinding() {
        Binding binding = serviceDefinitions.binding().name(model.getPortName().getLocalPart()+BINDING);
        binding.type(model.getPortTypeName());
        boolean first = true;
        for (JavaMethod method : model.getJavaMethods()) {
            if (first) {
                if (method.getBinding() instanceof SOAPBinding) {
                    SOAPBinding sBinding = (SOAPBinding)method.getBinding();
                    SOAPVersion soapVersion = sBinding.getSOAPVersion();

                    if(soapVersion.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)){
                        com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPBinding soapBinding = binding.soap12Binding();
                        soapBinding.transport(SOAP12_HTTP_TRANSPORT);
                        if (sBinding.getStyle().equals(Style.DOCUMENT))
                            soapBinding.style(DOCUMENT);
                        else
                            soapBinding.style(RPC);
                    }else{
                        com.sun.xml.internal.ws.wsdl.writer.document.soap.SOAPBinding soapBinding = binding.soapBinding();
                        soapBinding.transport(SOAP_HTTP_TRANSPORT);
                        if (sBinding.getStyle().equals(Style.DOCUMENT))
                            soapBinding.style(DOCUMENT);
                        else
                            soapBinding.style(RPC);
                    }
                }
                first = false;
            }
            if(bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING))
                generateSOAP12BindingOperation(method, binding);
            else
                generateBindingOperation(method, binding);
        }
    }

    protected void generateBindingOperation(JavaMethod method, Binding binding) {
        BindingOperationType operation = binding.operation().name(method.getOperationName());
        String targetNamespace = model.getTargetNamespace();
        QName requestMessage = new QName(targetNamespace, method.getOperationName());
        QName responseMessage = new QName(targetNamespace, method.getOperationName()+RESPONSE);
        if (method.getBinding() instanceof SOAPBinding) {
            List<Parameter> bodyParams = new ArrayList<Parameter>();
            List<Parameter> headerParams = new ArrayList<Parameter>();
            splitParameters(bodyParams, headerParams, method.getRequestParameters());
            SOAPBinding soapBinding = (SOAPBinding)method.getBinding();
            operation.soapOperation().soapAction(soapBinding.getSOAPAction());

            // input
            TypedXmlWriter input = operation.input();
            BodyType body = input._element(Body.class);
            boolean isRpc = soapBinding.getStyle().equals(Style.RPC);
            if (soapBinding.getUse().equals(Use.LITERAL)) {
                body.use(LITERAL);
                if (headerParams.size() > 0) {
                    if (bodyParams.size() > 0) {
                        Parameter param = bodyParams.iterator().next();
                        if (isRpc) {
                            StringBuffer parts = new StringBuffer();
                            int i=0;
                            for (Parameter parameter : ((WrapperParameter)param).getWrapperChildren()) {
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

            if (method.getMEP() != MessageStruct.ONE_WAY_MEP) {
                boolean unwrappable = headerParams.size() == 0;
                // output
                bodyParams.clear();
                headerParams.clear();
                splitParameters(bodyParams, headerParams, method.getResponseParameters());
                unwrappable = unwrappable ? headerParams.size() == 0 : unwrappable;
                TypedXmlWriter output = operation.output();
                body = output._element(Body.class);
                body.use(LITERAL);
                if (headerParams.size() > 0) {
                    String parts = "";
                    if (bodyParams.size() > 0) {
                        Parameter param = bodyParams.iterator().next();
                        if (isRpc) {
                            int i=0;
                            for (Parameter parameter : ((WrapperParameter)param).getWrapperChildren()) {
                                if (i++>0)
                                    parts += " ";
                                parts += parameter.getPartName();
                            }
                        } else {
                            if (param != null) {
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
                    generateSOAPHeaders(output, headerParams, responseMessage);
                }
                if (isRpc) {
                    body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
                }
            }
            for (CheckedException exception : method.getCheckedExceptions()) {
//                QName tagName = exception.getDetailType().tagName;
//                Fault fault = operation.fault().name(tagName.getLocalPart());
//                SOAPFault soapFault = fault._element(SOAPFault.class).name(tagName.getLocalPart());
                Fault fault = operation.fault().name(exception.getMessageName());
                SOAPFault soapFault = fault._element(SOAPFault.class).name(exception.getMessageName());
                soapFault.use(LITERAL);
            }
        }
    }

    protected void generateSOAP12BindingOperation(JavaMethod method, Binding binding) {
        BindingOperationType operation = binding.operation().name(method.getOperationName());
        String targetNamespace = model.getTargetNamespace();
        QName requestMessage = new QName(targetNamespace, method.getOperationName());
        QName responseMessage = new QName(targetNamespace, method.getOperationName()+RESPONSE);
        if (method.getBinding() instanceof SOAPBinding) {
            List<Parameter> bodyParams = new ArrayList<Parameter>();
            List<Parameter> headerParams = new ArrayList<Parameter>();
            splitParameters(bodyParams, headerParams, method.getRequestParameters());
            SOAPBinding soapBinding = (SOAPBinding)method.getBinding();
            operation.soap12Operation().soapAction(soapBinding.getSOAPAction());

            // input
            TypedXmlWriter input = operation.input();

            com.sun.xml.internal.ws.wsdl.writer.document.soap12.BodyType body = input._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.Body.class);
            boolean isRpc = soapBinding.getStyle().equals(Style.RPC);
            if (soapBinding.getUse().equals(Use.LITERAL)) {
                body.use(LITERAL);
                if (headerParams.size() > 0) {
                    if (bodyParams.size() > 0) {
                        Parameter param = bodyParams.iterator().next();
                        if (isRpc) {
                            StringBuffer parts = new StringBuffer();
                            int i=0;
                            for (Parameter parameter : ((WrapperParameter)param).getWrapperChildren()) {
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

            if (method.getMEP() != MessageStruct.ONE_WAY_MEP) {
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
                        Parameter param = bodyParams.iterator().next();
                        if (isRpc) {
                            String parts = "";
                            int i=0;
                            for (Parameter parameter : ((WrapperParameter)param).getWrapperChildren()) {
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
                    generateSOAP12Headers(output, headerParams, responseMessage);
                }
                if (isRpc) {
                    body.namespace(method.getRequestParameters().iterator().next().getName().getNamespaceURI());
                }
            }
            for (CheckedException exception : method.getCheckedExceptions()) {
//                QName tagName = exception.getDetailType().tagName;
//                Fault fault = operation.fault().name(tagName.getLocalPart());
//                com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault soapFault = fault._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault.class).name(tagName.getLocalPart());
                Fault fault = operation.fault().name(exception.getMessageName());
                com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault soapFault = fault._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPFault.class).name(exception.getMessageName());
                soapFault.use(LITERAL);
            }
        }
    }

    protected void splitParameters(List<Parameter> bodyParams, List<Parameter>headerParams, List<Parameter>params) {
        for (Parameter parameter : params) {
            if (isBodyParameter(parameter)) {
                bodyParams.add(parameter);
            } else {
                headerParams.add(parameter);
            }
        }
    }

    protected void generateSOAPHeaders(TypedXmlWriter writer, List<Parameter> parameters, QName message) {

        for (Parameter headerParam : parameters) {
            Header header = writer._element(Header.class);
            header.message(message);
            header.part(headerParam.getPartName());
            header.use(LITERAL);
        }
    }

    protected void generateSOAP12Headers(TypedXmlWriter writer, List<Parameter> parameters, QName message) {

        for (Parameter headerParam : parameters) {
            com.sun.xml.internal.ws.wsdl.writer.document.soap12.Header header = writer._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.Header.class);
            header.message(message);


            header.part(headerParam.getPartName());
            header.use(LITERAL);
        }
    }

    protected void generateService() {
        QName portQName = model.getPortName();
        QName serviceQName = model.getServiceQName();
        Service service = serviceDefinitions.service().name(serviceQName.getLocalPart());
        Port port = service.port().name(portQName.getLocalPart());
        port.binding(new QName(serviceQName.getNamespaceURI(), portQName.getLocalPart()+BINDING));
        if (model.getJavaMethods().size() == 0)
            return;
        if (model.getJavaMethods().iterator().next().getBinding() instanceof SOAPBinding) {
            if(bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)){
                com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPAddress address = port._element(com.sun.xml.internal.ws.wsdl.writer.document.soap12.SOAPAddress.class);
                address.location(REPLACE_WITH_ACTUAL_URL);
            }else{
                SOAPAddress address = port._element(SOAPAddress.class);
                address.location(REPLACE_WITH_ACTUAL_URL);
            }
        }
    }

    protected void generateInputMessage(Operation operation, JavaMethod method) {
        ParamType paramType = operation.input();
        paramType.message(new QName(model.getTargetNamespace(), method.getOperationName()));
    }

    protected void generateOutputMessage(Operation operation, JavaMethod method) {
        ParamType paramType = operation.output();
        paramType.message(new QName(model.getTargetNamespace(), method.getOperationName()+RESPONSE));
    }

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
     *
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


    protected class JAXWSOutputSchemaResolver extends SchemaOutputResolver {
        public Result createOutput(String namespaceUri, String suggestedFileName) throws IOException {
            return createOutputFile(namespaceUri, suggestedFileName);
        }
    }
}
