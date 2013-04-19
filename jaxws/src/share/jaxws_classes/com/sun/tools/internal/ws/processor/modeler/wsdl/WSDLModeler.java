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

package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.codemodel.internal.JType;
import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.SAXParseException2;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.processor.generator.Names;
import com.sun.tools.internal.ws.processor.model.*;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.*;
import com.sun.tools.internal.ws.processor.model.jaxb.*;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.util.ClassNameCollector;
import com.sun.tools.internal.ws.resources.ModelerMessages;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.document.*;
import com.sun.tools.internal.ws.wsdl.document.Message;
import com.sun.tools.internal.ws.wsdl.document.jaxws.CustomName;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEContent;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.soap.*;
import com.sun.tools.internal.ws.wsdl.framework.*;
import com.sun.tools.internal.ws.wsdl.parser.MetadataFinder;
import com.sun.tools.internal.ws.wsdl.parser.WSDLParser;
import com.sun.tools.internal.xjc.api.S2JJAXBModel;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.tools.internal.xjc.api.XJC;
import com.sun.xml.internal.ws.spi.db.BindingHelper;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.LocatorImpl;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import java.util.*;
import java.io.IOException;


/**
 * The WSDLModeler processes a WSDL to create a Model.
 *
 * @author WS Development Team
 */
public class WSDLModeler extends WSDLModelerBase {

    //map of wsdl:operation QName to <soapenv:Body> child, as per BP it must be unique in a port
    private final Map<QName, Operation> uniqueBodyBlocks = new HashMap<QName, Operation>();
    private final QName VOID_BODYBLOCK = new QName("");
    private final ClassNameCollector classNameCollector;
    private final String explicitDefaultPackage;

    public WSDLModeler(WsimportOptions options, ErrorReceiver receiver, MetadataFinder forest) {
        super(options, receiver,forest);
        this.classNameCollector = new ClassNameCollector();
        this.explicitDefaultPackage = options.defaultPackage;
    }


    protected enum StyleAndUse {
        RPC_LITERAL, DOC_LITERAL
    }

    private JAXBModelBuilder jaxbModelBuilder;

    @Override
    public Model buildModel() {
        try {
            parser = new WSDLParser(options, errReceiver, forest);
            parser.addParserListener(new ParserListener() {
                @Override
                public void ignoringExtension(Entity entity, QName name, QName parent) {
                    if (parent.equals(WSDLConstants.QNAME_TYPES)) {
                        // check for a schema element with the wrong namespace URI
                        if (name.getLocalPart().equals("schema")
                                && !name.getNamespaceURI().equals("")) {
                            warning(entity, ModelerMessages.WSDLMODELER_WARNING_IGNORING_UNRECOGNIZED_SCHEMA_EXTENSION(name.getNamespaceURI()));
                        }
                    }

                }

                @Override
                public void doneParsingEntity(QName element, Entity entity) {
                }
            });

            document = parser.parse();
            if (document == null || document.getDefinitions() == null) {
                return null;
            }

            document.validateLocally();
            Model model = internalBuildModel(document);
            if (model == null || errReceiver.hadError()) {
                return null;
            }
            //ClassNameCollector classNameCollector = new ClassNameCollector();
            classNameCollector.process(model);
            if (classNameCollector.getConflictingClassNames().isEmpty()) {
                if (errReceiver.hadError()) {
                    return null;
                }
                return model;
            }
            // do another pass, this time with conflict resolution enabled
            model = internalBuildModel(document);

            classNameCollector.process(model);
            if (classNameCollector.getConflictingClassNames().isEmpty()) {
                // we're done
                if (errReceiver.hadError()) {
                    return null;
                }
                return model;
            }
            // give up
            StringBuilder conflictList = new StringBuilder();
            boolean first = true;
            for (Iterator iter =
                    classNameCollector.getConflictingClassNames().iterator();
                 iter.hasNext();
                    ) {
                if (!first) {
                    conflictList.append(", ");
                } else {
                    first = false;
                }
                conflictList.append((String) iter.next());
            }
            error(document.getDefinitions(), ModelerMessages.WSDLMODELER_UNSOLVABLE_NAMING_CONFLICTS(conflictList.toString()));
        } catch (ModelException e) {
            reportError(document.getDefinitions(), e.getMessage(), e);
        } catch (ParseException e) {
            errReceiver.error(e);
        } catch (ValidationException e) {
            errReceiver.error(e.getMessage(), e);
        } catch (SAXException e) {
            errReceiver.error(e);
        } catch (IOException e) {
            errReceiver.error(e);
        }
        //should never reach here
        return null;
    }

    private Model internalBuildModel(WSDLDocument document) {
        numPasses++;

        //build the jaxbModel to be used latter
        buildJAXBModel(document);

        QName modelName =
                new QName(
                        document.getDefinitions().getTargetNamespaceURI(),
                        document.getDefinitions().getName() == null
                                ? "model"
                                : document.getDefinitions().getName());
        Model model = new Model(modelName, document.getDefinitions());
        model.setJAXBModel(getJAXBModelBuilder().getJAXBModel());

        // This fails with the changed classname (WSDLModeler to WSDLModeler11 etc.)
        // with this source comaptibility change the WSDL Modeler class name is changed. Right now hardcoding the
        // modeler class name to the same one being checked in WSDLGenerator.

        model.setProperty(
                ModelProperties.PROPERTY_MODELER_NAME,
                ModelProperties.WSDL_MODELER_NAME);

        _javaExceptions = new HashMap<String, JavaException>();
        _bindingNameToPortMap = new HashMap<QName, Port>();

        // grab target namespace
        model.setTargetNamespaceURI(document.getDefinitions().getTargetNamespaceURI());

        setDocumentationIfPresent(model,
                document.getDefinitions().getDocumentation());

        boolean hasServices = document.getDefinitions().services().hasNext();
        if (hasServices) {
            for (Iterator iter = document.getDefinitions().services();
                 iter.hasNext();
                    ) {
                processService((com.sun.tools.internal.ws.wsdl.document.Service) iter.next(),
                        model, document);
            }
        } else {
            // emit a warning if there are no service definitions
            warning(model.getEntity(), ModelerMessages.WSDLMODELER_WARNING_NO_SERVICE_DEFINITIONS_FOUND());
        }

        return model;
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#processService(Service, Model, WSDLDocument)
     */
    protected void processService(com.sun.tools.internal.ws.wsdl.document.Service wsdlService, Model model, WSDLDocument document) {
        QName serviceQName = getQNameOf(wsdlService);
        String serviceInterface = getServiceInterfaceName(serviceQName, wsdlService);
        if (isConflictingServiceClassName(serviceInterface)) {
            serviceInterface += "_Service";
        }
        Service service =
                new Service(
                        serviceQName,
                        new JavaInterface(serviceInterface, serviceInterface + "Impl"), wsdlService);

        setDocumentationIfPresent(service, wsdlService.getDocumentation());
        boolean hasPorts = false;
        for (Iterator iter = wsdlService.ports(); iter.hasNext();) {
            boolean processed =
                    processPort(
                            (com.sun.tools.internal.ws.wsdl.document.Port) iter.next(),
                            service,
                            document);
            hasPorts = hasPorts || processed;
        }
        if (!hasPorts) {
            // emit a warning if there are no ports
            warning(wsdlService, ModelerMessages.WSDLMODELER_WARNING_NO_PORTS_IN_SERVICE(wsdlService.getName()));
        } else {
            model.addService(service);
        }
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#processPort(WSDLPort, Service, WSDLDocument)
     */
    protected boolean processPort(com.sun.tools.internal.ws.wsdl.document.Port wsdlPort,
                                  Service service, WSDLDocument document) {
        try {

            //clear the  unique block map
            uniqueBodyBlocks.clear();

            QName portQName = getQNameOf(wsdlPort);
            Port port = new Port(portQName, wsdlPort);

            setDocumentationIfPresent(port, wsdlPort.getDocumentation());

            SOAPAddress soapAddress =
                    (SOAPAddress) getExtensionOfType(wsdlPort, SOAPAddress.class);
            if (soapAddress == null) {
                if(options.isExtensionMode()){
                    warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_NO_SOAP_ADDRESS(wsdlPort.getName()));
                }else{
                    // not a SOAP port, ignore it
                    warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT_NO_ADDRESS(wsdlPort.getName()));
                    return false;
                }
            }
            if (soapAddress != null) {
                port.setAddress(soapAddress.getLocation());
            }
            Binding binding = wsdlPort.resolveBinding(document);
            QName bindingName = getQNameOf(binding);
            PortType portType = binding.resolvePortType(document);

            port.setProperty(
                    ModelProperties.PROPERTY_WSDL_PORT_NAME,
                    getQNameOf(wsdlPort));
            port.setProperty(
                    ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME,
                    getQNameOf(portType));
            port.setProperty(
                    ModelProperties.PROPERTY_WSDL_BINDING_NAME,
                    bindingName);

            boolean isProvider = isProvider(wsdlPort);
            if (_bindingNameToPortMap.containsKey(bindingName) && !isProvider) {
                // this binding has been processed before
                Port existingPort =
                        _bindingNameToPortMap.get(bindingName);
                port.setOperations(existingPort.getOperations());
                port.setJavaInterface(existingPort.getJavaInterface());
                port.setStyle(existingPort.getStyle());
                port.setWrapped(existingPort.isWrapped());
            } else {
                // find out the SOAP binding extension, if any
                SOAPBinding soapBinding =
                        (SOAPBinding) getExtensionOfType(binding, SOAPBinding.class);

                if (soapBinding == null) {
                    soapBinding =
                            (SOAPBinding) getExtensionOfType(binding, SOAP12Binding.class);
                    if (soapBinding == null) {
                        if(!options.isExtensionMode()){
                            // cannot deal with non-SOAP ports
                            warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_IGNORING_NON_SOAP_PORT(wsdlPort.getName()));
                            return false;
                        }else{
                            warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_NON_SOAP_PORT(wsdlPort.getName()));
                        }
                    }else{
                        // we can only do soap1.2 if extensions are on
                        if (options.isExtensionMode()) {
                            warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_PORT_SOAP_BINDING_12(wsdlPort.getName()));
                        } else {
                            warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_12(wsdlPort.getName()));
                            return false;
                        }
                    }
                }

                if (soapBinding != null  && (soapBinding.getTransport() == null
                        || (!soapBinding.getTransport().equals(
                        SOAPConstants.URI_SOAP_TRANSPORT_HTTP) && !soapBinding.getTransport().equals(
                        SOAP12Constants.URI_SOAP_TRANSPORT_HTTP)))) {
                    if (!options.isExtensionMode()) {
                        // cannot deal with non-HTTP ports
                        warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_NON_HTTP_TRANSPORT(wsdlPort.getName()));
                        return false;
                    }

                }

                /**
                 * validate wsdl:binding uniqueness in style, e.g. rpclit or doclit
                 * ref: WSI BP 1.1 R 2705
                 */
                if (soapBinding != null && !validateWSDLBindingStyle(binding)) {
                    if (options.isExtensionMode()) {
                        warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_PORT_SOAP_BINDING_MIXED_STYLE(wsdlPort.getName()));
                    } else {
                        error(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_IGNORING_SOAP_BINDING_MIXED_STYLE(wsdlPort.getName()));
                    }
                }

                if(soapBinding != null){
                    port.setStyle(soapBinding.getStyle());
                }

                boolean hasOverloadedOperations = false;
                Set<String> operationNames = new HashSet<String>();
                for (Iterator iter = portType.operations(); iter.hasNext();) {
                    com.sun.tools.internal.ws.wsdl.document.Operation operation =
                            (com.sun.tools.internal.ws.wsdl.document.Operation) iter.next();

                    if (operationNames.contains(operation.getName())) {
                        hasOverloadedOperations = true;
                        break;
                    }
                    operationNames.add(operation.getName());

                    for (Iterator itr = binding.operations();
                         iter.hasNext();
                            ) {
                        BindingOperation bindingOperation =
                                (BindingOperation) itr.next();
                        if (operation
                                .getName()
                                .equals(bindingOperation.getName())) {
                            break;
                        } else if (!itr.hasNext()) {
                            error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_NOT_FOUND(operation.getName(), bindingOperation.getName()));
                        }
                    }
                }

                Map headers = new HashMap();
                boolean hasOperations = false;
                for (Iterator iter = binding.operations(); iter.hasNext();) {
                    BindingOperation bindingOperation =
                            (BindingOperation) iter.next();

                    com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation =
                            null;
                    Set operations =
                            portType.getOperationsNamed(bindingOperation.getName());
                    if (operations.isEmpty()) {
                        // the WSDL document is invalid
                        error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_NOT_IN_PORT_TYPE(bindingOperation.getName(), binding.getName()));
                    } else if (operations.size() == 1) {
                        portTypeOperation =
                                (com.sun.tools.internal.ws.wsdl.document.Operation) operations
                                        .iterator()
                                        .next();
                    } else {
                        boolean found = false;
                        String expectedInputName =
                                bindingOperation.getInput().getName();
                        String expectedOutputName =
                                bindingOperation.getOutput().getName();

                        for (Iterator iter2 = operations.iterator(); iter2.hasNext();) {
                            com.sun.tools.internal.ws.wsdl.document.Operation candidateOperation =
                                    (com.sun.tools.internal.ws.wsdl.document.Operation) iter2
                                            .next();

                            if (expectedInputName == null) {
                                // the WSDL document is invalid
                                error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_MISSING_INPUT_NAME(bindingOperation.getName()));
                            }
                            if (expectedOutputName == null) {
                                // the WSDL document is invalid
                                error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_MISSING_OUTPUT_NAME(bindingOperation.getName()));
                            }
                            if (expectedInputName
                                    .equals(candidateOperation.getInput().getName())
                                    && expectedOutputName.equals(
                                    candidateOperation
                                            .getOutput()
                                            .getName())) {
                                if (found) {
                                    // the WSDL document is invalid
                                    error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_MATCHING_OPERATIONS(bindingOperation.getName(), bindingOperation.getName()));
                                }
                                // got it!
                                found = true;
                                portTypeOperation = candidateOperation;
                            }
                        }
                        if (!found) {
                            // the WSDL document is invalid
                            error(bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_NOT_FOUND(bindingOperation.getName(), binding.getName()));
                        }
                    }
                    if (!isProvider) {
                        this.info =
                                new ProcessSOAPOperationInfo(
                                        port,
                                        wsdlPort,
                                        portTypeOperation,
                                        bindingOperation,
                                        soapBinding,
                                        document,
                                        hasOverloadedOperations,
                                        headers);


                        Operation operation;
                        if (soapBinding != null) {
                            operation = processSOAPOperation();
                        } else {
                            operation = processNonSOAPOperation();
                        }
                        if (operation != null) {
                            port.addOperation(operation);
                            hasOperations = true;
                        }
                    }
                }
                if (!isProvider && !hasOperations) {
                    // emit a warning if there are no operations, except when its a provider port
                    warning(wsdlPort, ModelerMessages.WSDLMODELER_WARNING_NO_OPERATIONS_IN_PORT(wsdlPort.getName()));
                    return false;
                }
                createJavaInterfaceForPort(port, isProvider);
                PortType pt = binding.resolvePortType(document);
                String jd = (pt.getDocumentation() != null) ? pt.getDocumentation().getContent() : null;
                port.getJavaInterface().setJavaDoc(jd);
                _bindingNameToPortMap.put(bindingName, port);
            }

            service.addPort(port);
            applyPortMethodCustomization(port, wsdlPort);
            applyWrapperStyleCustomization(port, binding.resolvePortType(document));

            return true;

        } catch (NoSuchEntityException e) {
            warning(document.getDefinitions(), e.getMessage());
            // should not happen
            return false;
        }
    }

    /**
     * Returns an operation purely from abstract operation
     */
    private Operation processNonSOAPOperation() {
        Operation operation =
                new Operation(new QName(null, info.bindingOperation.getName()), info.bindingOperation);

        setDocumentationIfPresent(
                operation,
                info.portTypeOperation.getDocumentation());

        if (info.portTypeOperation.getStyle()
                != OperationStyle.REQUEST_RESPONSE
                && info.portTypeOperation.getStyle() != OperationStyle.ONE_WAY) {
            if (options.isExtensionMode()) {
                warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_SUPPORTED_STYLE(info.portTypeOperation.getName()));
                return null;
            } else {
                error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_OPERATION_NOT_SUPPORTED_STYLE(info.portTypeOperation.getName(),
                        info.port.resolveBinding(document).resolvePortType(document).getName()));
            }
        }

        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        Message inputMessage = getInputMessage();
        Request request = new Request(inputMessage, errReceiver);
        request.setErrorReceiver(errReceiver);
        info.operation = operation;
        info.operation.setWSDLPortTypeOperation(info.portTypeOperation);

        Response response;

        Message outputMessage = null;
        if (isRequestResponse) {
            outputMessage = getOutputMessage();
            response = new Response(outputMessage, errReceiver);
        }else{
            response = new Response(null, errReceiver);
        }

        //set the style based on heuristic that message has either all parts defined
        // using type(RPC) or element(DOCUMENT)
        setNonSoapStyle(inputMessage, outputMessage);

        // Process parameterOrder and get the parameterList
        List<MessagePart> parameterList = getParameterOrder();

        boolean unwrappable = isUnwrappable();
        info.operation.setWrapped(unwrappable);
        List<Parameter> params = getDoclitParameters(request, response, parameterList);
        if (!validateParameterName(params)) {
            return null;
        }

        // create a definitive list of parameters to match what we'd like to get
        // in the java interface (which is generated much later), parameterOrder
        List<Parameter> definitiveParameterList = new ArrayList<Parameter>();
        for (Parameter param : params) {
            if (param.isReturn()) {
                info.operation.setProperty(WSDL_RESULT_PARAMETER, param);
                response.addParameter(param);
                continue;
            }
            if (param.isIN()) {
                request.addParameter(param);
            } else if (param.isOUT()) {
                response.addParameter(param);
            } else if (param.isINOUT()) {
                request.addParameter(param);
                response.addParameter(param);
            }
            definitiveParameterList.add(param);
        }

        info.operation.setRequest(request);

        if (isRequestResponse) {
            info.operation.setResponse(response);
        }

        // faults with duplicate names
        Set duplicateNames = getDuplicateFaultNames();

        // handle soap:fault
        handleLiteralSOAPFault(response, duplicateNames);
        info.operation.setProperty(
                WSDL_PARAMETER_ORDER,
                definitiveParameterList);

        Binding binding = info.port.resolveBinding(document);
        PortType portType = binding.resolvePortType(document);
        if (isAsync(portType, info.portTypeOperation)) {
            warning(portType, "Can not generate Async methods for non-soap binding!");
        }
        return info.operation;
    }

    /**
     * This method is added to fix one of the use case for j2ee se folks, so that we determine
     * for non_soap wsdl what could be the style - rpc or document based on parts in the message.
     *
     * We assume that the message parts could have either all of them with type attribute (RPC)
     * or element (DOCUMENT)
     *
     * Shall this check if parts are mixed and throw error message?
     */
    private void setNonSoapStyle(Message inputMessage, Message outputMessage) {
        SOAPStyle style = SOAPStyle.DOCUMENT;
        for(MessagePart part:inputMessage.getParts()){
            if (part.getDescriptorKind() == SchemaKinds.XSD_TYPE) {
                style = SOAPStyle.RPC;
            } else {
                style = SOAPStyle.DOCUMENT;
            }
        }

        //check the outputMessage parts
        if(outputMessage != null){
            for(MessagePart part:outputMessage.getParts()){
                if (part.getDescriptorKind() == SchemaKinds.XSD_TYPE) {
                    style = SOAPStyle.RPC;
                } else {
                    style = SOAPStyle.DOCUMENT;
                }
            }
        }
        info.modelPort.setStyle(style);
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#processSOAPOperation()
     */
    protected Operation processSOAPOperation() {
        Operation operation =
                new Operation(new QName(null, info.bindingOperation.getName()), info.bindingOperation);

        setDocumentationIfPresent(
                operation,
                info.portTypeOperation.getDocumentation());

        if (info.portTypeOperation.getStyle()
                != OperationStyle.REQUEST_RESPONSE
                && info.portTypeOperation.getStyle() != OperationStyle.ONE_WAY) {
            if (options.isExtensionMode()) {
                warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_NOT_SUPPORTED_STYLE(info.portTypeOperation.getName()));
                return null;
            } else {
                error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_OPERATION_NOT_SUPPORTED_STYLE(info.portTypeOperation.getName(),
                        info.port.resolveBinding(document).resolvePortType(document).getName()));
            }
        }

        SOAPStyle soapStyle = info.soapBinding.getStyle();

        // find out the SOAP operation extension, if any
        SOAPOperation soapOperation =
                (SOAPOperation) getExtensionOfType(info.bindingOperation,
                        SOAPOperation.class);

        if (soapOperation != null) {
            if (soapOperation.getStyle() != null) {
                soapStyle = soapOperation.getStyle();
            }
            if (soapOperation.getSOAPAction() != null) {
                operation.setSOAPAction(soapOperation.getSOAPAction());
            }
        }

        operation.setStyle(soapStyle);

        String uniqueOperationName =
                getUniqueName(info.portTypeOperation, info.hasOverloadedOperations);
        if (info.hasOverloadedOperations) {
            operation.setUniqueName(uniqueOperationName);
        }

        info.operation = operation;

        //attachment
        SOAPBody soapRequestBody = getSOAPRequestBody();
        if (soapRequestBody == null) {
            // the WSDL document is invalid
            error(info.bindingOperation, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_MISSING_SOAP_BODY(info.bindingOperation.getName()));
        }

        if (soapStyle == SOAPStyle.RPC) {
            if (soapRequestBody.isEncoded()) {
                if(options.isExtensionMode()){
                    warning(soapRequestBody, ModelerMessages.WSDLMODELER_20_RPCENC_NOT_SUPPORTED());
                    processNonSOAPOperation();
                }else{
                    error(soapRequestBody, ModelerMessages.WSDLMODELER_20_RPCENC_NOT_SUPPORTED());
                }
            }
            return processLiteralSOAPOperation(StyleAndUse.RPC_LITERAL);
        }
        // document style
        return processLiteralSOAPOperation(StyleAndUse.DOC_LITERAL);
    }

    protected Operation processLiteralSOAPOperation(StyleAndUse styleAndUse) {
        //returns false if the operation name is not acceptable
        if (!applyOperationNameCustomization()) {
            return null;
        }

        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        Message inputMessage = getInputMessage();
        Request request = new Request(inputMessage, errReceiver);
        request.setErrorReceiver(errReceiver);
        info.operation.setUse(SOAPUse.LITERAL);
        info.operation.setWSDLPortTypeOperation(info.portTypeOperation);
        SOAPBody soapRequestBody = getSOAPRequestBody();
        if ((StyleAndUse.DOC_LITERAL == styleAndUse) && (soapRequestBody.getNamespace() != null)) {
            warning(soapRequestBody, ModelerMessages.WSDLMODELER_WARNING_R_2716("soapbind:body", info.bindingOperation.getName()));
        }


        Response response;

        SOAPBody soapResponseBody = null;
        Message outputMessage = null;
        if (isRequestResponse) {
            soapResponseBody = getSOAPResponseBody();
            if (isOperationDocumentLiteral(styleAndUse) && (soapResponseBody.getNamespace() != null)) {
                warning(soapResponseBody, ModelerMessages.WSDLMODELER_WARNING_R_2716("soapbind:body", info.bindingOperation.getName()));
            }
            outputMessage = getOutputMessage();
            response = new Response(outputMessage, errReceiver);
        }else{
            response = new Response(null, errReceiver);
        }

        //ignore operation if there are more than one root part
        if (!validateMimeParts(getMimeParts(info.bindingOperation.getInput())) ||
                !validateMimeParts(getMimeParts(info.bindingOperation.getOutput()))) {
            return null;
        }

        if (!validateBodyParts(info.bindingOperation)) {
            // BP 1.1
            // R2204   A document-literal binding in a DESCRIPTION MUST refer, in each of its soapbind:body element(s),
            // only to wsdl:part element(s) that have been defined using the element attribute.

            // R2203   An rpc-literal binding in a DESCRIPTION MUST refer, in its soapbind:body element(s),
            // only to wsdNl:part element(s) that have been defined using the type attribute.
            if (isOperationDocumentLiteral(styleAndUse)) {
                if (options.isExtensionMode()) {
                    warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_TYPE_MESSAGE_PART(info.portTypeOperation.getName()));
                } else {
                    error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_DOCLITOPERATION(info.portTypeOperation.getName()));
                }
            } else if (isOperationRpcLiteral(styleAndUse)) {
                if (options.isExtensionMode()) {
                    warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_CANNOT_HANDLE_ELEMENT_MESSAGE_PART(info.portTypeOperation.getName()));
                } else {
                    error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_RPCLITOPERATION(info.portTypeOperation.getName()));
                }
            }
            return null;
        }

        // Process parameterOrder and get the parameterList
        List<MessagePart> parameterList = getParameterOrder();

        //binding is invalid in the wsdl, ignore the operation.
        if (!setMessagePartsBinding(styleAndUse)) {
            return null;
        }

        List<Parameter> params = null;
        boolean unwrappable = isUnwrappable();
        info.operation.setWrapped(unwrappable);
        if (isOperationDocumentLiteral(styleAndUse)) {
            params = getDoclitParameters(request, response, parameterList);
        } else if (isOperationRpcLiteral(styleAndUse)) {
            String operationName = info.bindingOperation.getName();
            Block reqBlock = null;
            if (inputMessage != null) {
                QName name = new QName(getRequestNamespaceURI(soapRequestBody), operationName);
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                reqBlock = new Block(name, rpcStruct, inputMessage);
                request.addBodyBlock(reqBlock);
            }

            Block resBlock = null;
            if (isRequestResponse && outputMessage != null) {
                QName name = new QName(getResponseNamespaceURI(soapResponseBody), operationName + "Response");
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                resBlock = new Block(name, rpcStruct, outputMessage);
                response.addBodyBlock(resBlock);
            }
            params = getRpcLitParameters(request, response, reqBlock, resBlock, parameterList);
        }


        if (!validateParameterName(params)) {
            return null;
        }

        // create a definitive list of parameters to match what we'd like to get
        // in the java interface (which is generated much later), parameterOrder
        List<Parameter> definitiveParameterList = new ArrayList<Parameter>();
        for (Parameter param : params) {
            if (param.isReturn()) {
                info.operation.setProperty(WSDL_RESULT_PARAMETER, param);
                response.addParameter(param);
                continue;
            }
            if (param.isIN()) {
                request.addParameter(param);
            } else if (param.isOUT()) {
                response.addParameter(param);
            } else if (param.isINOUT()) {
                request.addParameter(param);
                response.addParameter(param);
            }
            definitiveParameterList.add(param);
        }

        info.operation.setRequest(request);

        if (isRequestResponse) {
            info.operation.setResponse(response);
        }

        Iterator<Block> bb = request.getBodyBlocks();
        QName body;
        Operation thatOp;
        if (bb.hasNext()) {
            body = bb.next().getName();
            thatOp = uniqueBodyBlocks.get(body);
        } else {
            //there is no body block
            body = VOID_BODYBLOCK;
            thatOp = uniqueBodyBlocks.get(VOID_BODYBLOCK);
        }

        if(thatOp != null){
            if(options.isExtensionMode()){
                warning(info.port, ModelerMessages.WSDLMODELER_NON_UNIQUE_BODY_WARNING(info.port.getName(), info.operation.getName(), thatOp.getName(), body));
            }else{
                error(info.port, ModelerMessages.WSDLMODELER_NON_UNIQUE_BODY_ERROR(info.port.getName(), info.operation.getName(), thatOp.getName(), body));
            }
        }else{
            uniqueBodyBlocks.put(body, info.operation);
        }

        //Add additional headers
        if (options.additionalHeaders) {
            List<Parameter> additionalHeaders = new ArrayList<Parameter>();
            if (inputMessage != null) {
                for (MessagePart part : getAdditionHeaderParts(info.bindingOperation, inputMessage, true)) {
                    QName name = part.getDescriptor();
                    JAXBType jaxbType = getJAXBType(part);
                    Block block = new Block(name, jaxbType, part);
                    Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, block);
                    additionalHeaders.add(param);
                    request.addHeaderBlock(block);
                    request.addParameter(param);
                    definitiveParameterList.add(param);
                }
            }

            if (isRequestResponse && outputMessage != null) {
                List<Parameter> outParams = new ArrayList<Parameter>();
                for (MessagePart part : getAdditionHeaderParts(info.bindingOperation,outputMessage, false)) {
                    QName name = part.getDescriptor();
                    JAXBType jaxbType = getJAXBType(part);
                    Block block = new Block(name, jaxbType, part);
                    Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, block);
                    param.setMode(Mode.OUT);
                    outParams.add(param);
                    response.addHeaderBlock(block);
                    response.addParameter(param);
                }
                for (Parameter outParam : outParams) {
                    for (Parameter inParam : additionalHeaders) {
                        if (inParam.getName().equals(outParam.getName()) &&
                                inParam.getBlock().getName().equals(outParam.getBlock().getName())) {
                            //it is INOUT
                            inParam.setMode(Mode.INOUT);
                            outParam.setMode(Mode.INOUT);
                            break;
                        }
                    }
                    if (outParam.isOUT()) {
                        definitiveParameterList.add(outParam);
                    }
                }
            }
        }

        // faults with duplicate names
        Set duplicateNames = getDuplicateFaultNames();

        // handle soap:fault
        handleLiteralSOAPFault(response, duplicateNames);
        info.operation.setProperty(
                WSDL_PARAMETER_ORDER,
                definitiveParameterList);

        //set Async property
        Binding binding = info.port.resolveBinding(document);
        PortType portType = binding.resolvePortType(document);
        if (isAsync(portType, info.portTypeOperation)) {
            addAsyncOperations(info.operation, styleAndUse);
        }

        return info.operation;
    }


    private boolean validateParameterName(List<Parameter> params) {
        if (options.isExtensionMode()) {
            return true;
        }

        Message msg = getInputMessage();
        for (Parameter param : params) {
            if (param.isOUT()) {
                continue;
            }
            if (param.getCustomName() != null) {
                if (Names.isJavaReservedWord(param.getCustomName())) {
                    error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(info.operation.getName(), param.getCustomName()));
                    return false;
                }
                return true;
            }
            //process doclit wrapper style
            if (param.isEmbedded() && !(param.getBlock().getType() instanceof RpcLitStructure)) {
                if (Names.isJavaReservedWord(param.getName())) {
                    error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(info.operation.getName(), param.getName(), param.getBlock().getName()));
                    return false;
                }
            } else {
                //non-wrapper style and rpclit
                if (Names.isJavaReservedWord(param.getName())) {
                    error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(info.operation.getName(), msg.getName(), param.getName()));
                    return false;
                }
            }
        }

        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        if (isRequestResponse) {
            msg = getOutputMessage();
            for (Parameter param : params) {
                if (param.isIN()) {
                    continue;
                }
                if (param.getCustomName() != null) {
                    if (Names.isJavaReservedWord(param.getCustomName())) {
                        error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOM_NAME(info.operation.getName(), param.getCustomName()));
                        return false;
                    }
                    return true;
                }
                //process doclit wrapper style
                if (param.isEmbedded() && !(param.getBlock().getType() instanceof RpcLitStructure)) {
                    if (param.isReturn()) {
                        continue;
                    }
                    if (!param.getName().equals("return") && Names.isJavaReservedWord(param.getName())) {
                        error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_WRAPPER_STYLE(info.operation.getName(), param.getName(), param.getBlock().getName()));
                        return false;
                    }
                } else {
                    if (param.isReturn()) {
                        continue;
                    }

                    //non-wrapper style and rpclit
                    if (Names.isJavaReservedWord(param.getName())) {
                        error(param.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_NON_WRAPPER_STYLE(info.operation.getName(), msg.getName(), param.getName()));
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean enableMimeContent() {
        //first we look at binding operation
        JAXWSBinding jaxwsCustomization = (JAXWSBinding) getExtensionOfType(info.bindingOperation, JAXWSBinding.class);
        Boolean mimeContentMapping = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableMimeContentMapping() : null;
        if (mimeContentMapping != null) {
            return mimeContentMapping;
        }

        //then in wsdl:binding
        Binding binding = info.port.resolveBinding(info.document);
        jaxwsCustomization = (JAXWSBinding) getExtensionOfType(binding, JAXWSBinding.class);
        mimeContentMapping = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableMimeContentMapping() : null;
        if (mimeContentMapping != null) {
            return mimeContentMapping;
        }

        //at last look in wsdl:definitions
        jaxwsCustomization = (JAXWSBinding) getExtensionOfType(info.document.getDefinitions(), JAXWSBinding.class);
        mimeContentMapping = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableMimeContentMapping() : null;
        if (mimeContentMapping != null) {
            return mimeContentMapping;
        }
        return false;
    }

    private boolean applyOperationNameCustomization() {
        JAXWSBinding jaxwsCustomization = (JAXWSBinding) getExtensionOfType(info.portTypeOperation, JAXWSBinding.class);
        String operationName = (jaxwsCustomization != null) ? ((jaxwsCustomization.getMethodName() != null) ? jaxwsCustomization.getMethodName().getName() : null) : null;
        if (operationName != null) {
            if (Names.isJavaReservedWord(operationName)) {
                if (options.isExtensionMode()) {
                    warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(info.operation.getName(), operationName));
                } else {
                    error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_CUSTOMIZED_OPERATION_NAME(info.operation.getName(), operationName));
                }
                return false;
            }

            info.operation.setCustomizedName(operationName);
        }

        if (Names.isJavaReservedWord(info.operation.getJavaMethodName())) {
            if (options.isExtensionMode()) {
                warning(info.portTypeOperation, ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(info.operation.getName()));
            } else {
                error(info.portTypeOperation, ModelerMessages.WSDLMODELER_INVALID_OPERATION_JAVA_RESERVED_WORD_NOT_ALLOWED_OPERATION_NAME(info.operation.getName()));
            }
            return false;
        }
        return true;
    }

    protected String getAsyncOperationName(Operation operation) {
        String name = operation.getCustomizedName();
        if (name == null) {
            name = operation.getUniqueName();
        }
        return name;
    }

    /**
     * @param styleAndUse
     */
    private void addAsyncOperations(Operation syncOperation, StyleAndUse styleAndUse) {
        Operation operation = createAsyncOperation(syncOperation, styleAndUse, AsyncOperationType.POLLING);
        if (operation != null) {
            info.modelPort.addOperation(operation);
        }

        operation = createAsyncOperation(syncOperation, styleAndUse, AsyncOperationType.CALLBACK);
        if (operation != null) {
            info.modelPort.addOperation(operation);
        }
    }

    private Operation createAsyncOperation(Operation syncOperation, StyleAndUse styleAndUse, AsyncOperationType asyncType) {
        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        if (!isRequestResponse) {
            return null;
        }

        //create async operations
        AsyncOperation operation = new AsyncOperation(info.operation, info.bindingOperation);

        //creation the async operation name: operationName+Async or customized name
        //operation.setName(new QName(operation.getName().getNamespaceURI(), getAsyncOperationName(info.portTypeOperation, operation)));
        if (asyncType.equals(AsyncOperationType.CALLBACK)) {
            operation.setUniqueName(info.operation.getUniqueName() + "_async_callback");
        } else if (asyncType.equals(AsyncOperationType.POLLING)) {
            operation.setUniqueName(info.operation.getUniqueName() + "_async_polling");
        }

        setDocumentationIfPresent(
                operation,
                info.portTypeOperation.getDocumentation());

        operation.setAsyncType(asyncType);
        operation.setSOAPAction(info.operation.getSOAPAction());
        boolean unwrappable = info.operation.isWrapped();
        operation.setWrapped(unwrappable);
        SOAPBody soapRequestBody = getSOAPRequestBody();

        Message inputMessage = getInputMessage();
        Request request = new Request(inputMessage, errReceiver);

        SOAPBody soapResponseBody = getSOAPResponseBody();
        Message outputMessage = getOutputMessage();
        Response response = new Response(outputMessage, errReceiver);

        // Process parameterOrder and get the parameterList
        java.util.List<String> parameterList = getAsynParameterOrder();

        List<Parameter> inParameters = null;
        if (isOperationDocumentLiteral(styleAndUse)) {
            inParameters = getRequestParameters(request, parameterList);
            // outParameters = getResponseParameters(response);
            // re-create parameterList with unwrapped parameters
            if (unwrappable) {
                List<String> unwrappedParameterList = new ArrayList<String>();
                if (inputMessage != null) {
                    Iterator<MessagePart> parts = inputMessage.parts();
                    if (parts.hasNext()) {
                        MessagePart part = parts.next();
                        JAXBType jaxbType = getJAXBType(part);
                        List<JAXBProperty> memberList = jaxbType.getWrapperChildren();
                        Iterator<JAXBProperty> props = memberList.iterator();
                        while (props.hasNext()) {
                            JAXBProperty prop = props.next();
                            unwrappedParameterList.add(prop.getElementName().getLocalPart());
                        }
                    }
                }

                parameterList.clear();
                parameterList.addAll(unwrappedParameterList);
            }
        } else if (isOperationRpcLiteral(styleAndUse)) {
            String operationName = info.bindingOperation.getName();
            Block reqBlock = null;
            if (inputMessage != null) {
                QName name = new QName(getRequestNamespaceURI(soapRequestBody), operationName);
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                reqBlock = new Block(name, rpcStruct, inputMessage);
                request.addBodyBlock(reqBlock);
            }
            inParameters = createRpcLitRequestParameters(request, parameterList, reqBlock);
        }

        // add response blocks, we dont need to create respnse parameters, just blocks will be fine, lets
        // copy them from sync optraions
        //copy the response blocks from the sync operation
        Iterator<Block> blocks = info.operation.getResponse().getBodyBlocks();

        while (blocks.hasNext()) {
            response.addBodyBlock(blocks.next());
        }

        blocks = info.operation.getResponse().getHeaderBlocks();
        while (blocks.hasNext()) {
            response.addHeaderBlock(blocks.next());
        }

        blocks = info.operation.getResponse().getAttachmentBlocks();
        while (blocks.hasNext()) {
            response.addAttachmentBlock(blocks.next());
        }

        List<MessagePart> outputParts = outputMessage.getParts();

        // handle headers
        int numOfOutMsgParts = outputParts.size();

        if (numOfOutMsgParts == 1) {
            MessagePart part = outputParts.get(0);
            if (isOperationDocumentLiteral(styleAndUse)) {
                JAXBType type = getJAXBType(part);
                operation.setResponseBean(type);
            } else if (isOperationRpcLiteral(styleAndUse)) {
                String operationName = info.bindingOperation.getName();
                Block resBlock = info.operation.getResponse().getBodyBlocksMap().get(new QName(getResponseNamespaceURI(soapResponseBody),
                        operationName + "Response"));

                RpcLitStructure resBean = (RpcLitStructure) resBlock.getType();
                List<RpcLitMember> members = resBean.getRpcLitMembers();
                operation.setResponseBean(members.get(0));
            }
        } else {
            //create response bean
            String nspace = "";
            QName responseBeanName = new QName(nspace, getAsyncOperationName(info.operation) + "Response");
            JAXBType responseBeanType = getJAXBModelBuilder().getJAXBType(responseBeanName);
            if (responseBeanType == null) {
                error(info.operation.getEntity(), ModelerMessages.WSDLMODELER_RESPONSEBEAN_NOTFOUND(info.operation.getName()));
            }
            operation.setResponseBean(responseBeanType);
        }

        QName respBeanName = new QName(soapResponseBody.getNamespace(), getAsyncOperationName(info.operation) + "Response");
        Block block = new Block(respBeanName, operation.getResponseBeanType(), outputMessage);
        JavaType respJavaType = operation.getResponseBeanJavaType();
        JAXBType respType = new JAXBType(respBeanName, respJavaType);
        Parameter respParam = ModelerUtils.createParameter(info.operation.getName() + "Response", respType, block);
        respParam.setParameterIndex(-1);
        response.addParameter(respParam);
        operation.setProperty(WSDL_RESULT_PARAMETER, respParam.getName());


        int parameterOrderPosition = 0;
        for (String name : parameterList) {
            Parameter inParameter = ModelerUtils.getParameter(name, inParameters);
            if (inParameter == null) {
                if (options.isExtensionMode()) {
                    warning(info.operation.getEntity(), ModelerMessages.WSDLMODELER_WARNING_IGNORING_OPERATION_PART_NOT_FOUND(info.operation.getName().getLocalPart(), name));
                } else {
                    error(info.operation.getEntity(), ModelerMessages.WSDLMODELER_ERROR_PART_NOT_FOUND(info.operation.getName().getLocalPart(), name));
                }
                return null;
            }
            request.addParameter(inParameter);
            inParameter.setParameterIndex(parameterOrderPosition);
            parameterOrderPosition++;
        }

        operation.setResponse(response);

        //  add callback handlerb Parameter to request
        if (operation.getAsyncType().equals(AsyncOperationType.CALLBACK)) {
            JavaType cbJavaType = operation.getCallBackType();
            JAXBType callbackType = new JAXBType(respBeanName, cbJavaType);
            Parameter cbParam = ModelerUtils.createParameter("asyncHandler", callbackType, block);
            request.addParameter(cbParam);
        }

        operation.setRequest(request);

        return operation;
    }

    protected boolean isAsync(com.sun.tools.internal.ws.wsdl.document.PortType portType, com.sun.tools.internal.ws.wsdl.document.Operation wsdlOperation) {
        //First look into wsdl:operation
        JAXWSBinding jaxwsCustomization = (JAXWSBinding) getExtensionOfType(wsdlOperation, JAXWSBinding.class);
        Boolean isAsync = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableAsyncMapping() : null;

        if (isAsync != null) {
            return isAsync;
        }

        // then into wsdl:portType
        jaxwsCustomization = (JAXWSBinding) getExtensionOfType(portType, JAXWSBinding.class);
        isAsync = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableAsyncMapping() : null;
        if (isAsync != null) {
            return isAsync;
        }

        //then wsdl:definitions
        jaxwsCustomization = (JAXWSBinding) getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        isAsync = (jaxwsCustomization != null) ? jaxwsCustomization.isEnableAsyncMapping() : null;
        if (isAsync != null) {
            return isAsync;
        }
        return false;
    }

    protected void handleLiteralSOAPHeaders(Request request, Response response, Iterator headerParts, Set duplicateNames, @NotNull List<String> definitiveParameterList, boolean processRequest) {
        QName headerName;
        Block headerBlock;
        JAXBType jaxbType;
        int parameterOrderPosition = definitiveParameterList.size();
        while (headerParts.hasNext()) {
            MessagePart part = (MessagePart) headerParts.next();
            headerName = part.getDescriptor();
            jaxbType = getJAXBType(part);
            headerBlock = new Block(headerName, jaxbType, part);
            TWSDLExtensible ext;
            if (processRequest) {
                ext = info.bindingOperation.getInput();
            } else {
                ext = info.bindingOperation.getOutput();
            }
            Message headerMessage = getHeaderMessage(part, ext);

            if (processRequest) {
                request.addHeaderBlock(headerBlock);
            } else {
                response.addHeaderBlock(headerBlock);
            }

            Parameter parameter = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
            parameter.setParameterIndex(parameterOrderPosition);
            setCustomizedParameterName(info.bindingOperation, headerMessage, part, parameter, false);
            if (processRequest) {
                request.addParameter(parameter);
                definitiveParameterList.add(parameter.getName());
            } else {
                for (String inParamName : definitiveParameterList) {
                    if (inParamName.equals(parameter.getName())) {
                        Parameter inParam = request.getParameterByName(inParamName);
                        parameter.setLinkedParameter(inParam);
                        inParam.setLinkedParameter(parameter);
                        //its in/out parameter, input and output parameter have the same order position.
                        parameter.setParameterIndex(inParam.getParameterIndex());
                    }
                }
                if (!definitiveParameterList.contains(parameter.getName())) {
                    definitiveParameterList.add(parameter.getName());
                }
                response.addParameter(parameter);
            }
            parameterOrderPosition++;
        }

    }

    protected void handleLiteralSOAPFault(Response response, Set duplicateNames) {
        for (BindingFault bindingFault : info.bindingOperation.faults()) {
            com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault = null;
            for (com.sun.tools.internal.ws.wsdl.document.Fault aFault : info.portTypeOperation.faults()) {
                if (aFault.getName().equals(bindingFault.getName())) {
                    if (portTypeFault != null) {
                        // the WSDL document is invalid, a wsld:fault in a wsdl:operation of a portType can be bound only once
                        error(portTypeFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_NOT_UNIQUE(bindingFault.getName(), info.bindingOperation.getName()));
                    }
                    portTypeFault = aFault;
                }
            }

            // The WSDL document is invalid, the wsdl:fault in abstract operation is does not have any binding
            if (portTypeFault == null) {
                error(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_NOT_FOUND(bindingFault.getName(), info.bindingOperation.getName()));

            }
        }
        for ( com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault : info.portTypeOperation.faults()) {

            BindingFault bindingFault = null ;
            for(BindingFault bFault: info.bindingOperation.faults()) {
                if (bFault.getName().equals(portTypeFault.getName())) {
                    bindingFault = bFault;
                }
            }

            if(bindingFault == null) {
                warning(portTypeFault,ModelerMessages.WSDLMODELER_INVALID_PORT_TYPE_FAULT_NOT_FOUND(portTypeFault.getName(),info.portTypeOperation.getName()));
            }
            // wsdl:fault message name is used to create the java exception name later on
            String faultName = getFaultClassName(portTypeFault);
            Fault fault = new Fault(faultName, portTypeFault);
            fault.setWsdlFaultName(portTypeFault.getName());
            setDocumentationIfPresent(fault, portTypeFault.getDocumentation());
            if (bindingFault != null) {
                //get the soapbind:fault from wsdl:fault in the binding
                SOAPFault soapFault = (SOAPFault) getExtensionOfType(bindingFault, SOAPFault.class);

                // The WSDL document is invalid, can't have wsdl:fault without soapbind:fault
                if (soapFault == null) {
                    if (options.isExtensionMode()) {
                        warning(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(bindingFault.getName(), info.bindingOperation.getName()));
                        soapFault = new SOAPFault(new LocatorImpl());
                    } else {
                        error(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(bindingFault.getName(), info.bindingOperation.getName()));
                    }
                }

                //the soapbind:fault must have use="literal" or no use attribute, in that case its assumed "literal"
                if (!soapFault.isLiteral()) {
                    if (options.isExtensionMode()) {
                        warning(soapFault, ModelerMessages.WSDLMODELER_WARNING_IGNORING_FAULT_NOT_LITERAL(bindingFault.getName(), info.bindingOperation.getName()));
                    } else {
                        error(soapFault, ModelerMessages.WSDLMODELER_INVALID_OPERATION_FAULT_NOT_LITERAL(bindingFault.getName(), info.bindingOperation.getName()));
                    }
                    continue;
                }

                // the soapFault name must be present
                if (soapFault.getName() == null) {
                    warning(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_NO_SOAP_FAULT_NAME(bindingFault.getName(), info.bindingOperation.getName()));
                } else if (!soapFault.getName().equals(bindingFault.getName())) {
                    warning(soapFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_WRONG_SOAP_FAULT_NAME(soapFault.getName(), bindingFault.getName(), info.bindingOperation.getName()));
                } else if (soapFault.getNamespace() != null) {
                    warning(soapFault, ModelerMessages.WSDLMODELER_WARNING_R_2716_R_2726("soapbind:fault", soapFault.getName()));
                }

            }

            com.sun.tools.internal.ws.wsdl.document.Message faultMessage = portTypeFault.resolveMessage(info.document);
            Iterator iter2 = faultMessage.parts();
            if (!iter2.hasNext()) {
                // the WSDL document is invalid
                error(faultMessage, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_EMPTY_MESSAGE(portTypeFault.getName(), faultMessage.getName()));
            }
            MessagePart faultPart = (MessagePart) iter2.next();
            QName faultQName = faultPart.getDescriptor();

            // Don't include fault messages with non-unique soap:fault names
            if (duplicateNames.contains(faultQName)) {
                warning(faultPart, ModelerMessages.WSDLMODELER_DUPLICATE_FAULT_SOAP_NAME(portTypeFault.getName(), info.portTypeOperation.getName(), faultPart.getName()));
                continue;
            }

            if (iter2.hasNext()) {
                // the WSDL document is invalid
                error(faultMessage, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_MESSAGE_HAS_MORE_THAN_ONE_PART(portTypeFault.getName(), faultMessage.getName()));
            }

            if (faultPart.getDescriptorKind() != SchemaKinds.XSD_ELEMENT) {
                if (options.isExtensionMode()) {
                    warning(faultPart, ModelerMessages.WSDLMODELER_INVALID_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(faultMessage.getName(), faultPart.getName()));
                } else {
                    error(faultPart, ModelerMessages.WSDLMODELER_INVALID_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(faultMessage.getName(), faultPart.getName()));
                }
            }

            JAXBType jaxbType = getJAXBType(faultPart);

            fault.setElementName(faultPart.getDescriptor());
            fault.setJavaMemberName(Names.getExceptionClassMemberName());

            Block faultBlock = new Block(faultQName, jaxbType, faultPart);
            fault.setBlock(faultBlock);
            //createParentFault(fault);
            //createSubfaults(fault);
            if (!response.getFaultBlocksMap().containsKey(faultBlock.getName())) {
                response.addFaultBlock(faultBlock);
            }
            info.operation.addFault(fault);
        }
    }

    private String getFaultClassName(com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding) getExtensionOfType(portTypeFault, JAXWSBinding.class);
        if (jaxwsBinding != null) {
            CustomName className = jaxwsBinding.getClassName();
            if (className != null) {
                return makePackageQualified(className.getName());
            }
        }
        return makePackageQualified(BindingHelper.mangleNameToClassName(portTypeFault.getMessage().getLocalPart()));
    }

    protected boolean setMessagePartsBinding(StyleAndUse styleAndUse) {
        SOAPBody inBody = getSOAPRequestBody();
        Message inMessage = getInputMessage();
        if (!setMessagePartsBinding(inBody, inMessage, styleAndUse, true)) {
            return false;
        }

        if (isRequestResponse()) {
            SOAPBody outBody = getSOAPResponseBody();
            Message outMessage = getOutputMessage();
            if (!setMessagePartsBinding(outBody, outMessage, styleAndUse, false)) {
                return false;
            }
        }
        return true;
    }

    //returns false if the wsdl is invalid and operation should be ignored
    protected boolean setMessagePartsBinding(SOAPBody body, Message message, StyleAndUse styleAndUse, boolean isInput) {

        //get Mime parts
        List<MessagePart> mimeParts;
        List<MessagePart> headerParts;
        List<MessagePart> bodyParts = getBodyParts(body, message);

        if (isInput) {
            headerParts = getHeaderPartsFromMessage(message, isInput);
            mimeParts = getMimeContentParts(message, info.bindingOperation.getInput());
        } else {
            headerParts = getHeaderPartsFromMessage(message, isInput);
            mimeParts = getMimeContentParts(message, info.bindingOperation.getOutput());
        }

        //As of now WSDL MIME binding is not supported, so throw the exception when such binding is encounterd
//        if(mimeParts.size() > 0){
//            fail("wsdlmodeler.unsupportedBinding.mime", new Object[]{});
//        }

        //if soap:body parts attribute not there, then all unbounded message parts will
        // belong to the soap body
        if (bodyParts == null) {
            bodyParts = new ArrayList<MessagePart>();
            for (Iterator<MessagePart> iter = message.parts(); iter.hasNext();) {
                MessagePart mPart = iter.next();
                //Its a safe assumption that the parts in the message not belonging to header or mime will
                // belong to the body?
                if (mimeParts.contains(mPart) || headerParts.contains(mPart) || boundToFault(mPart.getName())) {
                    //throw error that a part cant be bound multiple times, not ignoring operation, if there
                    //is conflict it will fail latter
                    if (options.isExtensionMode()) {
                        warning(mPart, ModelerMessages.WSDLMODELER_WARNING_BINDING_OPERATION_MULTIPLE_PART_BINDING(info.bindingOperation.getName(), mPart.getName()));
                    } else {
                        error(mPart, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_MULTIPLE_PART_BINDING(info.bindingOperation.getName(), mPart.getName()));
                    }
                }
                bodyParts.add(mPart);
            }
        }

        //now build the final parts list with header, mime parts and body parts
        for (Iterator iter = message.parts(); iter.hasNext();) {
            MessagePart mPart = (MessagePart) iter.next();
            if (mimeParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
            } else if (headerParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_HEADER_BINDING);
            } else if (bodyParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
            } else {
                mPart.setBindingExtensibilityElementKind(MessagePart.PART_NOT_BOUNDED);
            }
        }

        if (isOperationDocumentLiteral(styleAndUse) && bodyParts.size() > 1) {
            if (options.isExtensionMode()) {
                warning(message, ModelerMessages.WSDLMODELER_WARNING_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(info.portTypeOperation.getName()));
            } else {
                error(message, ModelerMessages.WSDLMODELER_INVALID_OPERATION_MORE_THAN_ONE_PART_IN_MESSAGE(info.portTypeOperation.getName()));
            }
            return false;
        }
        return true;
    }

    private boolean boundToFault(String partName) {
        for (BindingFault bindingFault : info.bindingOperation.faults()) {
            if (partName.equals(bindingFault.getName())) {
                return true;
            }
        }
        return false;
    }

    //get MessagePart(s) referenced by parts attribute of soap:body element
    private List<MessagePart> getBodyParts(SOAPBody body, Message message) {
        String bodyParts = body.getParts();
        if (bodyParts != null) {
            List<MessagePart> partsList = new ArrayList<MessagePart>();
            StringTokenizer in = new StringTokenizer(bodyParts.trim(), " ");
            while (in.hasMoreTokens()) {
                String part = in.nextToken();
                MessagePart mPart = message.getPart(part);
                if (null == mPart) {
                    error(message, ModelerMessages.WSDLMODELER_ERROR_PARTS_NOT_FOUND(part, message.getName()));
                }
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
            return partsList;
        }
        return null;
    }

    List<MessagePart> getAdditionHeaderParts(BindingOperation bindingOperation,Message message, boolean isInput){
        List<MessagePart> headerParts = new ArrayList<MessagePart>();
        List<MessagePart> parts = message.getParts();
        List<MessagePart> headers = getHeaderParts(bindingOperation, isInput);

        for(MessagePart part: headers){
            if (parts.contains(part)) {
                continue;
            }
            headerParts.add(part);
        }
        return headerParts;
    }

    private List<MessagePart> getHeaderPartsFromMessage(Message message, boolean isInput) {
        List<MessagePart> headerParts = new ArrayList<MessagePart>();
        Iterator<MessagePart> parts = message.parts();
        List<MessagePart> headers = getHeaderParts(info.bindingOperation, isInput);
        while (parts.hasNext()) {
            MessagePart part = parts.next();
            if (headers.contains(part)) {
                headerParts.add(part);
            }
        }
        return headerParts;
    }

    private Message getHeaderMessage(MessagePart part, TWSDLExtensible ext) {
        Iterator<SOAPHeader> headers = getHeaderExtensions(ext).iterator();
        while (headers.hasNext()) {
            SOAPHeader header = headers.next();
            if (!header.isLiteral()) {
                continue;
            }
            com.sun.tools.internal.ws.wsdl.document.Message headerMessage = findMessage(header.getMessage(), document);
            if (headerMessage == null) {
                continue;
            }

            MessagePart headerPart = headerMessage.getPart(header.getPart());
            if (headerPart == part) {
                return headerMessage;
            }
        }
        return null;
    }

    private List<MessagePart> getHeaderParts(BindingOperation bindingOperation, boolean isInput) {
        TWSDLExtensible ext;
        if (isInput) {
            ext = bindingOperation.getInput();
        } else {
            ext = bindingOperation.getOutput();
        }

        List<MessagePart> parts = new ArrayList<MessagePart>();
        Iterator<SOAPHeader> headers = getHeaderExtensions(ext).iterator();
        while (headers.hasNext()) {
            SOAPHeader header = headers.next();
            if (!header.isLiteral()) {
                error(header, ModelerMessages.WSDLMODELER_INVALID_HEADER_NOT_LITERAL(header.getPart(), bindingOperation.getName()));
            }

            if (header.getNamespace() != null) {
                warning(header, ModelerMessages.WSDLMODELER_WARNING_R_2716_R_2726("soapbind:header", bindingOperation.getName()));
            }
            com.sun.tools.internal.ws.wsdl.document.Message headerMessage = findMessage(header.getMessage(),document);
            if (headerMessage == null) {
                error(header, ModelerMessages.WSDLMODELER_INVALID_HEADER_CANT_RESOLVE_MESSAGE(header.getMessage(), bindingOperation.getName()));
            }

            MessagePart part = headerMessage.getPart(header.getPart());
            if (part == null) {
                error(header, ModelerMessages.WSDLMODELER_INVALID_HEADER_NOT_FOUND(header.getPart(), bindingOperation.getName()));
            }
            if (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT) {
                if (options.isExtensionMode()) {
                    warning(part, ModelerMessages.WSDLMODELER_INVALID_HEADER_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(part.getName(), bindingOperation.getName()));
                } else {
                    error(part, ModelerMessages.WSDLMODELER_INVALID_HEADER_MESSAGE_PART_MUST_HAVE_ELEMENT_DESCRIPTOR(part.getName(), bindingOperation.getName()));
                }
            }
            part.setBindingExtensibilityElementKind(MessagePart.SOAP_HEADER_BINDING);
            parts.add(part);
        }
        return parts;
    }

    private boolean isOperationDocumentLiteral(StyleAndUse styleAndUse) {
        return StyleAndUse.DOC_LITERAL == styleAndUse;
    }

    private boolean isOperationRpcLiteral(StyleAndUse styleAndUse) {
        return StyleAndUse.RPC_LITERAL == styleAndUse;
    }

    /**
     * @param part
     * @return Returns a JAXBType object
     */
    private JAXBType getJAXBType(MessagePart part) {
        JAXBType type;
        QName name = part.getDescriptor();
        if (part.getDescriptorKind().equals(SchemaKinds.XSD_ELEMENT)) {
            type = getJAXBModelBuilder().getJAXBType(name);
            if(type == null){
                error(part, ModelerMessages.WSDLMODELER_JAXB_JAVATYPE_NOTFOUND(name, part.getName()));
            }
        } else {
            S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
            TypeAndAnnotation typeAnno = jaxbModel.getJavaType(name);
            if (typeAnno == null) {
                error(part, ModelerMessages.WSDLMODELER_JAXB_JAVATYPE_NOTFOUND(name, part.getName()));
            }
            JavaType javaType = new JavaSimpleType(new JAXBTypeAndAnnotation(typeAnno));
            type = new JAXBType(new QName("", part.getName()), javaType);
        }
        return type;
    }

    private List<Parameter> getDoclitParameters(Request req, Response res, List<MessagePart> parameterList) {
        if (parameterList.isEmpty()) {
            return new ArrayList<Parameter>();
        }
        List<Parameter> params = new ArrayList<Parameter>();
        Message inMsg = getInputMessage();
        Message outMsg = getOutputMessage();
        boolean unwrappable = isUnwrappable();
        List<Parameter> outParams = null;
        int pIndex = 0;
        for (MessagePart part : parameterList) {
            QName reqBodyName = part.getDescriptor();
            JAXBType jaxbType = getJAXBType(part);
            Block block = new Block(reqBodyName, jaxbType, part);
            if (unwrappable) {
                //So build body and header blocks and set to request and response
                JAXBStructuredType jaxbStructType = ModelerUtils.createJAXBStructureType(jaxbType);
                block = new Block(reqBodyName, jaxbStructType, part);
                if (ModelerUtils.isBoundToSOAPBody(part)) {
                    if (part.isIN()) {
                        req.addBodyBlock(block);
                    } else if (part.isOUT()) {
                        res.addBodyBlock(block);
                    } else if (part.isINOUT()) {
                        req.addBodyBlock(block);
                        res.addBodyBlock(block);
                    }
                } else if (ModelerUtils.isUnbound(part)) {
                    if (part.isIN()) {
                        req.addUnboundBlock(block);
                    } else if (part.isOUT()) {
                        res.addUnboundBlock(block);
                    } else if (part.isINOUT()) {
                        req.addUnboundBlock(block);
                        res.addUnboundBlock(block);
                    }
                }
                if (part.isIN() || part.isINOUT()) {
                    params = ModelerUtils.createUnwrappedParameters(jaxbStructType, block);
                    int index = 0;
                    Mode mode = part.isINOUT() ? Mode.INOUT : Mode.IN;
                    for (Parameter param : params) {
                        param.setParameterIndex(index++);
                        param.setMode(mode);
                        setCustomizedParameterName(info.portTypeOperation, inMsg, part, param, unwrappable);
                    }
                } else if (part.isOUT()) {
                    outParams = ModelerUtils.createUnwrappedParameters(jaxbStructType, block);
                    for (Parameter param : outParams) {
                        param.setMode(Mode.OUT);
                        setCustomizedParameterName(info.portTypeOperation, outMsg, part, param, unwrappable);
                    }
                }
            } else {
                if (ModelerUtils.isBoundToSOAPBody(part)) {
                    if (part.isIN()) {
                        req.addBodyBlock(block);
                    } else if (part.isOUT()) {
                        res.addBodyBlock(block);
                    } else if (part.isINOUT()) {
                        req.addBodyBlock(block);
                        res.addBodyBlock(block);
                    }
                } else if (ModelerUtils.isBoundToSOAPHeader(part)) {
                    if (part.isIN()) {
                        req.addHeaderBlock(block);
                    } else if (part.isOUT()) {
                        res.addHeaderBlock(block);
                    } else if (part.isINOUT()) {
                        req.addHeaderBlock(block);
                        res.addHeaderBlock(block);
                    }
                } else if (ModelerUtils.isBoundToMimeContent(part)) {
                    List<MIMEContent> mimeContents;

                    if (part.isIN()) {
                        mimeContents = getMimeContents(info.bindingOperation.getInput(),
                                getInputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType, part);
                        req.addAttachmentBlock(block);
                    } else if (part.isOUT()) {
                        mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                                getOutputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType, part);
                        res.addAttachmentBlock(block);
                    } else if (part.isINOUT()) {
                        mimeContents = getMimeContents(info.bindingOperation.getInput(),
                                getInputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType, part);
                        req.addAttachmentBlock(block);
                        res.addAttachmentBlock(block);

                        mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                                getOutputMessage(), part.getName());
                        JAXBType outJaxbType = getAttachmentType(mimeContents, part);

                        String inType = jaxbType.getJavaType().getType().getName();
                        String outType = outJaxbType.getJavaType().getType().getName();

                        TypeAndAnnotation inTa = jaxbType.getJavaType().getType().getTypeAnn();
                        TypeAndAnnotation outTa = outJaxbType.getJavaType().getType().getTypeAnn();
                        if ((((inTa != null) && (outTa != null) && inTa.equals(outTa))) && !inType.equals(outType)) {
                            String javaType = "javax.activation.DataHandler";

                            //S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
                            //JCodeModel cm = jaxbModel.generateCode(null, errReceiver);
                            JType jt = options.getCodeModel().ref(javaType);
                            JAXBTypeAndAnnotation jaxbTa = jaxbType.getJavaType().getType();
                            jaxbTa.setType(jt);
                        }
                    }
                } else if (ModelerUtils.isUnbound(part)) {
                    if (part.isIN()) {
                        req.addUnboundBlock(block);
                    } else if (part.isOUT()) {
                        res.addUnboundBlock(block);
                    } else if (part.isINOUT()) {
                        req.addUnboundBlock(block);
                        res.addUnboundBlock(block);
                    }
                }
                Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, block);
                param.setMode(part.getMode());
                if (part.isReturn()) {
                    param.setParameterIndex(-1);
                } else {
                    param.setParameterIndex(pIndex++);
                }

                if (part.isIN()) {
                    setCustomizedParameterName(info.portTypeOperation, inMsg, part, param, false);
                } else if (outMsg != null) {
                    setCustomizedParameterName(info.portTypeOperation, outMsg, part, param, false);
                }

                params.add(param);
            }
        }
        if (unwrappable && (outParams != null)) {
            int index = params.size();
            for (Parameter param : outParams) {
                if (BindingHelper.mangleNameToVariableName(param.getName()).equals("return")) {
                    param.setParameterIndex(-1);
                } else {
                    Parameter inParam = ModelerUtils.getParameter(param.getName(), params);
                    if ((inParam != null) && inParam.isIN()) {
                        QName inElementName = inParam.getType().getName();
                        QName outElementName = param.getType().getName();
                        String inJavaType = inParam.getTypeName();
                        String outJavaType = param.getTypeName();
                        TypeAndAnnotation inTa = inParam.getType().getJavaType().getType().getTypeAnn();
                        TypeAndAnnotation outTa = param.getType().getJavaType().getType().getTypeAnn();
                        QName inRawTypeName = ModelerUtils.getRawTypeName(inParam);
                        QName outRawTypeName = ModelerUtils.getRawTypeName(param);
                        if (inElementName.getLocalPart().equals(outElementName.getLocalPart()) &&
                                inJavaType.equals(outJavaType) &&
                                (inTa == null || outTa == null || inTa.equals(outTa)) &&
                                (inRawTypeName == null || outRawTypeName == null || inRawTypeName.equals(outRawTypeName))) {
                            inParam.setMode(Mode.INOUT);
                            continue;
                        }
                    }
                    if (outParams.size() == 1) {
                        param.setParameterIndex(-1);
                    } else {
                        param.setParameterIndex(index++);
                    }
                }
                params.add(param);
            }
        }
        return params;
    }

    private List<Parameter> getRpcLitParameters(Request req, Response res, Block reqBlock, Block resBlock, List<MessagePart> paramList) {
        List<Parameter> params = new ArrayList<Parameter>();
        Message inMsg = getInputMessage();
        Message outMsg = getOutputMessage();
        S2JJAXBModel jaxbModel = ((RpcLitStructure) reqBlock.getType()).getJaxbModel().getS2JJAXBModel();
        List<Parameter> inParams = ModelerUtils.createRpcLitParameters(inMsg, reqBlock, jaxbModel, errReceiver);
        List<Parameter> outParams = null;
        if (outMsg != null) {
            outParams = ModelerUtils.createRpcLitParameters(outMsg, resBlock, jaxbModel, errReceiver);
        }

        //create parameters for header and mime parts
        int index = 0;
        for (MessagePart part : paramList) {
            Parameter param = null;
            if (ModelerUtils.isBoundToSOAPBody(part)) {
                if (part.isIN()) {
                    param = ModelerUtils.getParameter(part.getName(), inParams);
                } else if (outParams != null) {
                    param = ModelerUtils.getParameter(part.getName(), outParams);
                }
            } else if (ModelerUtils.isBoundToSOAPHeader(part)) {
                QName headerName = part.getDescriptor();
                JAXBType jaxbType = getJAXBType(part);
                Block headerBlock = new Block(headerName, jaxbType, part);
                param = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
                if (part.isIN()) {
                    req.addHeaderBlock(headerBlock);
                } else if (part.isOUT()) {
                    res.addHeaderBlock(headerBlock);
                } else if (part.isINOUT()) {
                    req.addHeaderBlock(headerBlock);
                    res.addHeaderBlock(headerBlock);
                }
            } else if (ModelerUtils.isBoundToMimeContent(part)) {
                List<MIMEContent> mimeContents;
                if (part.isIN() || part.isINOUT()) {
                    mimeContents = getMimeContents(info.bindingOperation.getInput(),
                            getInputMessage(), part.getName());
                } else {
                    mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                            getOutputMessage(), part.getName());
                }

                JAXBType type = getAttachmentType(mimeContents, part);
                //create Parameters in request or response
                //Block mimeBlock = new Block(new QName(part.getName()), type);
                Block mimeBlock = new Block(type.getName(), type, part);
                param = ModelerUtils.createParameter(part.getName(), type, mimeBlock);
                if (part.isIN()) {
                    req.addAttachmentBlock(mimeBlock);
                } else if (part.isOUT()) {
                    res.addAttachmentBlock(mimeBlock);
                } else if (part.isINOUT()) {
                    mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                            getOutputMessage(), part.getName());
                    JAXBType outJaxbType = getAttachmentType(mimeContents, part);

                    String inType = type.getJavaType().getType().getName();
                    String outType = outJaxbType.getJavaType().getType().getName();
                    if (!inType.equals(outType)) {
                        String javaType = "javax.activation.DataHandler";
                        JType jt = options.getCodeModel().ref(javaType);
                        JAXBTypeAndAnnotation jaxbTa = type.getJavaType().getType();
                        jaxbTa.setType(jt);
                    }
                    req.addAttachmentBlock(mimeBlock);
                    res.addAttachmentBlock(mimeBlock);
                }
            } else if (ModelerUtils.isUnbound(part)) {
                QName name = part.getDescriptor();
                JAXBType type = getJAXBType(part);
                Block unboundBlock = new Block(name, type, part);
                if (part.isIN()) {
                    req.addUnboundBlock(unboundBlock);
                } else if (part.isOUT()) {
                    res.addUnboundBlock(unboundBlock);
                } else if (part.isINOUT()) {
                    req.addUnboundBlock(unboundBlock);
                    res.addUnboundBlock(unboundBlock);
                }
                param = ModelerUtils.createParameter(part.getName(), type, unboundBlock);
            }
            if (param != null) {
                if (part.isReturn()) {
                    param.setParameterIndex(-1);
                } else {
                    param.setParameterIndex(index++);
                }
                param.setMode(part.getMode());
                params.add(param);
            }
        }
        for (Parameter param : params) {
            if (param.isIN()) {
                setCustomizedParameterName(info.portTypeOperation, inMsg, inMsg.getPart(param.getName()), param, false);
            } else if (outMsg != null) {
                setCustomizedParameterName(info.portTypeOperation, outMsg, outMsg.getPart(param.getName()), param, false);
            }
        }
        return params;
    }

    private List<Parameter> getRequestParameters(Request request, List<String> parameterList) {
        Message inputMessage = getInputMessage();
        //there is no input message, return zero parameters
        if (inputMessage != null && !inputMessage.parts().hasNext()) {
            return new ArrayList<Parameter>();
        }

        List<Parameter> inParameters = null;
        QName reqBodyName;
        Block reqBlock;
        JAXBType jaxbReqType;
        boolean unwrappable = isUnwrappable();
        boolean doneSOAPBody = false;
        //setup request parameters
        for (String inParamName : parameterList) {
            MessagePart part = inputMessage.getPart(inParamName);
            if (part == null) {
                continue;
            }
            reqBodyName = part.getDescriptor();
            jaxbReqType = getJAXBType(part);
            if (unwrappable) {
                //So build body and header blocks and set to request and response
                JAXBStructuredType jaxbRequestType = ModelerUtils.createJAXBStructureType(jaxbReqType);
                reqBlock = new Block(reqBodyName, jaxbRequestType, part);
                if (ModelerUtils.isBoundToSOAPBody(part)) {
                    request.addBodyBlock(reqBlock);
                } else if (ModelerUtils.isUnbound(part)) {
                    request.addUnboundBlock(reqBlock);
                }
                inParameters = ModelerUtils.createUnwrappedParameters(jaxbRequestType, reqBlock);
                for (Parameter param : inParameters) {
                    setCustomizedParameterName(info.portTypeOperation, inputMessage, part, param, unwrappable);
                }
            } else {
                reqBlock = new Block(reqBodyName, jaxbReqType, part);
                if (ModelerUtils.isBoundToSOAPBody(part) && !doneSOAPBody) {
                    doneSOAPBody = true;
                    request.addBodyBlock(reqBlock);
                } else if (ModelerUtils.isBoundToSOAPHeader(part)) {
                    request.addHeaderBlock(reqBlock);
                } else if (ModelerUtils.isBoundToMimeContent(part)) {
                    List<MIMEContent> mimeContents = getMimeContents(info.bindingOperation.getInput(),
                            getInputMessage(), part.getName());
                    jaxbReqType = getAttachmentType(mimeContents, part);
                    //reqBlock = new Block(new QName(part.getName()), jaxbReqType);
                    reqBlock = new Block(jaxbReqType.getName(), jaxbReqType, part);
                    request.addAttachmentBlock(reqBlock);
                } else if (ModelerUtils.isUnbound(part)) {
                    request.addUnboundBlock(reqBlock);
                }
                if (inParameters == null) {
                    inParameters = new ArrayList<Parameter>();
                }
                Parameter param = ModelerUtils.createParameter(part.getName(), jaxbReqType, reqBlock);
                setCustomizedParameterName(info.portTypeOperation, inputMessage, part, param, false);
                inParameters.add(param);
            }
        }
        return inParameters;
    }

    /**
     * @param part
     * @param param
     * @param wrapperStyle TODO
     */
    private void setCustomizedParameterName(TWSDLExtensible extension, Message msg, MessagePart part, Parameter param, boolean wrapperStyle) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding) getExtensionOfType(extension, JAXWSBinding.class);
        if (jaxwsBinding == null) {
            return;
        }
        String paramName = part.getName();
        QName elementName = part.getDescriptor();
        if (wrapperStyle) {
            elementName = param.getType().getName();
        }
        String customName = jaxwsBinding.getParameterName(msg.getName(), paramName, elementName, wrapperStyle);
        if (customName != null && !customName.equals("")) {
            param.setCustomName(customName);
        }
    }

    @Override
    protected boolean isConflictingPortClassName(String name) {
        return false;
    }

    protected boolean isUnwrappable() {
        if (!getWrapperStyleCustomization()) {
            return false;
        }

        com.sun.tools.internal.ws.wsdl.document.Message inputMessage = getInputMessage();
        com.sun.tools.internal.ws.wsdl.document.Message outputMessage = getOutputMessage();

        // Wrapper style if the operation's input and output messages each contain
        // only a single part
        if ((inputMessage != null && inputMessage.numParts() != 1)
                || (outputMessage != null && outputMessage.numParts() != 1)) {
            return false;
        }

        MessagePart inputPart = inputMessage != null
                ? inputMessage.parts().next() : null;
        MessagePart outputPart = outputMessage != null
                ? outputMessage.parts().next() : null;
        String operationName = info.portTypeOperation.getName();

        // Wrapper style if the input message part refers to a global element declaration whose localname
        // is equal to the operation name
        // Wrapper style if the output message part refers to a global element declaration
        if ((inputPart != null && !inputPart.getDescriptor().getLocalPart().equals(operationName)) ||
                (outputPart != null && outputPart.getDescriptorKind() != SchemaKinds.XSD_ELEMENT)) {
            return false;
        }

        //check to see if either input or output message part not bound to soapbing:body
        //in that case the operation is not wrapper style
        if (((inputPart != null) && (inputPart.getBindingExtensibilityElementKind() != MessagePart.SOAP_BODY_BINDING)) ||
                ((outputPart != null) && (outputPart.getBindingExtensibilityElementKind() != MessagePart.SOAP_BODY_BINDING))) {
            return false;
        }

        // Wrapper style if the elements referred to by the input and output message parts
        // (henceforth referred to as wrapper elements) are both complex types defined
        // using the xsd:sequence compositor
        // Wrapper style if the wrapper elements only contain child elements, they must not
        // contain other structures such as xsd:choice, substitution groups1 or attributes
        //These checkins are done by jaxb, we just check if jaxb has wrapper children. If there
        // are then its wrapper style
        //if(inputPart != null && outputPart != null){
        if (inputPart != null) {
            boolean inputWrappable = false;
            JAXBType inputType = getJAXBType(inputPart);
            if (inputType != null) {
                inputWrappable = inputType.isUnwrappable();
            }
            //if there are no output part (oneway), the operation can still be wrapper style
            if (outputPart == null) {
                return inputWrappable;
            }
            JAXBType outputType = getJAXBType(outputPart);
            if ((inputType != null) && (outputType != null)) {
                return inputType.isUnwrappable() && outputType.isUnwrappable();
            }
        }

        return false;
    }

    private boolean getWrapperStyleCustomization() {
        //first we look into wsdl:portType/wsdl:operation
        com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation = info.portTypeOperation;
        JAXWSBinding jaxwsBinding = (JAXWSBinding) getExtensionOfType(portTypeOperation, JAXWSBinding.class);
        if (jaxwsBinding != null) {
            Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
            if (isWrappable != null) {
                return isWrappable;
            }
        }

        //then into wsdl:portType
        PortType portType = info.port.resolveBinding(document).resolvePortType(document);
        jaxwsBinding = (JAXWSBinding) getExtensionOfType(portType, JAXWSBinding.class);
        if (jaxwsBinding != null) {
            Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
            if (isWrappable != null) {
                return isWrappable;
            }
        }

        //then wsdl:definitions
        jaxwsBinding = (JAXWSBinding) getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        if (jaxwsBinding != null) {
            Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
            if (isWrappable != null) {
                return isWrappable;
            }
        }
        return true;
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#isSingleInOutPart(Set, MessagePart)
     */
    protected boolean isSingleInOutPart(Set inputParameterNames,
                                        MessagePart outputPart) {
        // As of now, we dont have support for in/out in doc-lit. So return false.
        SOAPOperation soapOperation =
                (SOAPOperation) getExtensionOfType(info.bindingOperation,
                        SOAPOperation.class);
        if ((soapOperation != null) && (soapOperation.isDocument() || info.soapBinding.isDocument())) {
            Iterator iter = getInputMessage().parts();
            while (iter.hasNext()) {
                MessagePart part = (MessagePart) iter.next();
                if (outputPart.getName().equals(part.getName()) && outputPart.getDescriptor().equals(part.getDescriptor())) {
                    return true;
                }
            }
        } else if (soapOperation != null && soapOperation.isRPC() || info.soapBinding.isRPC()) {
            com.sun.tools.internal.ws.wsdl.document.Message inputMessage = getInputMessage();
            if (inputParameterNames.contains(outputPart.getName())) {
                if (inputMessage.getPart(outputPart.getName()).getDescriptor().equals(outputPart.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Parameter> createRpcLitRequestParameters(Request request, List<String> parameterList, Block block) {
        Message message = getInputMessage();
        S2JJAXBModel jaxbModel = ((RpcLitStructure) block.getType()).getJaxbModel().getS2JJAXBModel();
        List<Parameter> parameters = ModelerUtils.createRpcLitParameters(message, block, jaxbModel, errReceiver);

        //create parameters for header and mime parts
        for (String paramName : parameterList) {
            MessagePart part = message.getPart(paramName);
            if (part == null) {
                continue;
            }
            if (ModelerUtils.isBoundToSOAPHeader(part)) {
                if (parameters == null) {
                    parameters = new ArrayList<Parameter>();
                }
                QName headerName = part.getDescriptor();
                JAXBType jaxbType = getJAXBType(part);
                Block headerBlock = new Block(headerName, jaxbType, part);
                request.addHeaderBlock(headerBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
                if (param != null) {
                    parameters.add(param);
                }
            } else if (ModelerUtils.isBoundToMimeContent(part)) {
                if (parameters == null) {
                    parameters = new ArrayList<Parameter>();
                }
                List<MIMEContent> mimeContents = getMimeContents(info.bindingOperation.getInput(),
                        getInputMessage(), paramName);

                JAXBType type = getAttachmentType(mimeContents, part);
                //create Parameters in request or response
                //Block mimeBlock = new Block(new QName(part.getName()), type);
                Block mimeBlock = new Block(type.getName(), type, part);
                request.addAttachmentBlock(mimeBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), type, mimeBlock);
                if (param != null) {
                    parameters.add(param);
                }
            } else if (ModelerUtils.isUnbound(part)) {
                if (parameters == null) {
                    parameters = new ArrayList<Parameter>();
                }
                QName name = part.getDescriptor();
                JAXBType type = getJAXBType(part);
                Block unboundBlock = new Block(name, type, part);
                request.addUnboundBlock(unboundBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), type, unboundBlock);
                if (param != null) {
                    parameters.add(param);
                }
            }
        }
        for (Parameter param : parameters) {
            setCustomizedParameterName(info.portTypeOperation, message, message.getPart(param.getName()), param, false);
        }
        return parameters;
    }

    private String getJavaTypeForMimeType(String mimeType) {
        if (mimeType.equals("image/jpeg") || mimeType.equals("image/gif")) {
            return "java.awt.Image";
        } else if (mimeType.equals("text/xml") || mimeType.equals("application/xml")) {
            return "javax.xml.transform.Source";
        }
        return "javax.activation.DataHandler";
    }

    private JAXBType getAttachmentType(List<MIMEContent> mimeContents, MessagePart part) {
        if (!enableMimeContent()) {
            return getJAXBType(part);
        }
        String javaType;
        List<String> mimeTypes = getAlternateMimeTypes(mimeContents);
        if (mimeTypes.size() > 1) {
            javaType = "javax.activation.DataHandler";
        } else {
            javaType = getJavaTypeForMimeType(mimeTypes.get(0));
        }

        S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
        JType jt = options.getCodeModel().ref(javaType);
        QName desc = part.getDescriptor();
        TypeAndAnnotation typeAnno = null;

        if (part.getDescriptorKind() == SchemaKinds.XSD_TYPE) {
            typeAnno = jaxbModel.getJavaType(desc);
            desc = new QName("", part.getName());
        } else if (part.getDescriptorKind() == SchemaKinds.XSD_ELEMENT) {
            typeAnno = getJAXBModelBuilder().getElementTypeAndAnn(desc);
            if(typeAnno == null){
                error(part, ModelerMessages.WSDLMODELER_JAXB_JAVATYPE_NOTFOUND(part.getDescriptor(), part.getName()));
            }
            for (String mimeType : mimeTypes) {
                if ((!mimeType.equals("text/xml") && !mimeType.equals("application/xml"))) {
                    //According to AP 1.0,
                    //RZZZZ: In a DESCRIPTION, if a wsdl:part element refers to a
                    //global element declaration (via the element attribute of the wsdl:part
                    //element) then the value of the type attribute of a mime:content element
                    //that binds that part MUST be a content type suitable for carrying an
                    //XML serialization.
                    //should we throw warning?
                    //type = MimeHelper.javaType.DATA_HANDLER_JAVATYPE;
                    warning(part, ModelerMessages.MIMEMODELER_ELEMENT_PART_INVALID_ELEMENT_MIME_TYPE(part.getName(), mimeType));
                }
            }
        }
        if (typeAnno == null) {
            error(part, ModelerMessages.WSDLMODELER_JAXB_JAVATYPE_NOTFOUND(desc, part.getName()));
        }
        return new JAXBType(desc, new JavaSimpleType(new JAXBTypeAndAnnotation(typeAnno, jt)),
                null, getJAXBModelBuilder().getJAXBModel());
    }

    protected void buildJAXBModel(WSDLDocument wsdlDocument) {
        JAXBModelBuilder tempJaxbModelBuilder = new JAXBModelBuilder(options, classNameCollector, forest, errReceiver);
        //set the java package where wsdl artifacts will be generated
        //if user provided package name  using -p switch (or package property on wsimport ant task)
        //ignore the package customization in the wsdl and schema bidnings
        //formce the -p option only in the first pass
        if (explicitDefaultPackage != null) {
            tempJaxbModelBuilder.getJAXBSchemaCompiler().forcePackageName(options.defaultPackage);
        } else {
            options.defaultPackage = getJavaPackage();
        }

        //create pseudo schema for async operations(if any) response bean
        List<InputSource> schemas = PseudoSchemaBuilder.build(this, options, errReceiver);
        for (InputSource schema : schemas) {
            tempJaxbModelBuilder.getJAXBSchemaCompiler().parseSchema(schema);
        }
        tempJaxbModelBuilder.bind();
        this.jaxbModelBuilder = tempJaxbModelBuilder;
    }

    protected String getJavaPackage() {
        String jaxwsPackage = null;
        JAXWSBinding jaxwsCustomization = (JAXWSBinding) getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        if (jaxwsCustomization != null && jaxwsCustomization.getJaxwsPackage() != null) {
            jaxwsPackage = jaxwsCustomization.getJaxwsPackage().getName();
        }
        if (jaxwsPackage != null) {
            return jaxwsPackage;
        }
        String wsdlUri = document.getDefinitions().getTargetNamespaceURI();
        return XJC.getDefaultPackageName(wsdlUri);

    }

    protected void createJavaInterfaceForProviderPort(Port port) {
        String interfaceName = "javax.xml.ws.Provider";
        JavaInterface intf = new JavaInterface(interfaceName);
        port.setJavaInterface(intf);
    }

    protected void createJavaInterfaceForPort(Port port, boolean isProvider) {
        if (isProvider) {
            createJavaInterfaceForProviderPort(port);
            return;
        }
        String interfaceName = getJavaNameOfSEI(port);

        if (isConflictingPortClassName(interfaceName)) {
            interfaceName += "_PortType";
        }

        JavaInterface intf = new JavaInterface(interfaceName);
        for (Operation operation : port.getOperations()) {
            createJavaMethodForOperation(
                    port,
                    operation,
                    intf);

            for (JavaParameter jParam : operation.getJavaMethod().getParametersList()) {
                Parameter param = jParam.getParameter();
                if (param.getCustomName() != null) {
                    jParam.setName(param.getCustomName());
                }
            }
        }

        port.setJavaInterface(intf);
    }

    protected String getServiceInterfaceName(QName serviceQName, com.sun.tools.internal.ws.wsdl.document.Service wsdlService) {
        String serviceName = wsdlService.getName();
        JAXWSBinding jaxwsCust = (JAXWSBinding) getExtensionOfType(wsdlService, JAXWSBinding.class);
        if (jaxwsCust != null && jaxwsCust.getClassName() != null) {
            CustomName name = jaxwsCust.getClassName();
            if (name != null && !name.getName().equals("")) {
                return makePackageQualified(name.getName());
            }
        }
        return makePackageQualified(BindingHelper.mangleNameToClassName(serviceName));
    }

    protected String getJavaNameOfSEI(Port port) {
        QName portTypeName =
                (QName) port.getProperty(
                        ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
        PortType pt = (PortType) document.find(Kinds.PORT_TYPE, portTypeName);
        //populate the portType map here. We should get rid of all these properties
        // lets not do it as it may break NB
        //TODO: clean all these stuff part of NB RFE
        port.portTypes.put(portTypeName, pt);
        JAXWSBinding jaxwsCust = (JAXWSBinding) getExtensionOfType(pt, JAXWSBinding.class);
        if (jaxwsCust != null && jaxwsCust.getClassName() != null) {
            CustomName name = jaxwsCust.getClassName();
            if (name != null && !name.getName().equals("")) {
                return makePackageQualified(name.getName());
            }
        }

        String interfaceName;
        if (portTypeName != null) {
            // got portType information from WSDL, use it to name the interface
            interfaceName =
                    makePackageQualified(BindingHelper.mangleNameToClassName(portTypeName.getLocalPart()));
        } else {
            // somehow we only got the port name, so we use that
            interfaceName =
                    makePackageQualified(BindingHelper.mangleNameToClassName(port.getName().getLocalPart()));
        }
        return interfaceName;
    }

    private void createJavaMethodForAsyncOperation(Port port, Operation operation,
                                                   JavaInterface intf) {
        String candidateName = getJavaNameForOperation(operation);
        JavaMethod method = new JavaMethod(candidateName, options, errReceiver);

        assert (operation.getRequest() != null);
        Response response = operation.getResponse();

        // build a signature of the form "opName%arg1type%arg2type%...%argntype so that we
        // detect overloading conflicts in the generated java interface/classes
        for (Iterator iter = operation.getRequest().getParameters(); iter.hasNext();) {
            Parameter parameter = (Parameter) iter.next();

            if (parameter.getJavaParameter() != null) {
                error(operation.getEntity(), ModelerMessages.WSDLMODELER_INVALID_OPERATION(operation.getName().getLocalPart()));
            }

            JavaType parameterType = parameter.getType().getJavaType();
            JavaParameter javaParameter =
                    new JavaParameter(
                                BindingHelper.mangleNameToVariableName(parameter.getName()),
                            parameterType,
                            parameter,
                            parameter.getLinkedParameter() != null);
            if (javaParameter.isHolder()) {
                javaParameter.setHolderName(javax.xml.ws.Holder.class.getName());
            }
            method.addParameter(javaParameter);
            parameter.setJavaParameter(javaParameter);

        }

        if (response != null) {
            String resultParameterName =
                    (String) operation.getProperty(WSDL_RESULT_PARAMETER);
            Parameter resultParameter =
                    response.getParameterByName(resultParameterName);
            JavaType returnType = resultParameter.getType().getJavaType();
            method.setReturnType(returnType);

        }
        operation.setJavaMethod(method);
        intf.addMethod(method);
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#createJavaMethodForOperation(WSDLPort, WSDLOperation, JavaInterface, Set, Set)
     */
    protected void createJavaMethodForOperation(Port port, Operation operation, JavaInterface intf) {
        if ((operation instanceof AsyncOperation)) {
            createJavaMethodForAsyncOperation(port, operation, intf);
            return;
        }
        String candidateName = getJavaNameForOperation(operation);
        JavaMethod method = new JavaMethod(candidateName, options, errReceiver);
        Parameter returnParam = (Parameter) operation.getProperty(WSDL_RESULT_PARAMETER);
        if (returnParam != null) {
            JavaType parameterType = returnParam.getType().getJavaType();
            method.setReturnType(parameterType);
        } else {
            method.setReturnType(JavaSimpleTypeCreator.VOID_JAVATYPE);
        }
        List<Parameter> parameterOrder = (List<Parameter>) operation.getProperty(WSDL_PARAMETER_ORDER);
        for (Parameter param : parameterOrder) {
            JavaType parameterType = param.getType().getJavaType();
            String name = (param.getCustomName() != null) ? param.getCustomName() : param.getName();
            name = BindingHelper.mangleNameToVariableName(name);
            //if its a java keyword after name mangling, then we simply put underscore as there is no
            //need to ask user to customize the parameter name if its java keyword
            if(Names.isJavaReservedWord(name)){
                name = "_"+name;
            }
            JavaParameter javaParameter =
                    new JavaParameter(
                            name,
                            parameterType,
                            param,
                            param.isINOUT() || param.isOUT());
            if (javaParameter.isHolder()) {
                javaParameter.setHolderName(javax.xml.ws.Holder.class.getName());
            }
            method.addParameter(javaParameter);
            param.setJavaParameter(javaParameter);
        }
        operation.setJavaMethod(method);
        intf.addMethod(method);

        String opName = BindingHelper.mangleNameToVariableName(operation.getName().getLocalPart());
        for (Iterator iter = operation.getFaults();
             iter != null && iter.hasNext();
                ) {
            Fault fault = (Fault) iter.next();
            createJavaExceptionFromLiteralType(fault, port, opName);
        }
        JavaException javaException;
        Fault fault;
        for (Iterator iter = operation.getFaults(); iter.hasNext();) {
            fault = (Fault) iter.next();
            javaException = fault.getJavaException();
            method.addException(javaException.getName());
        }

    }

    protected boolean createJavaExceptionFromLiteralType(Fault fault, com.sun.tools.internal.ws.processor.model.Port port, String operationName) {
        JAXBType faultType = (JAXBType) fault.getBlock().getType();

        String exceptionName = fault.getName();

        // use fault namespace attribute
        JAXBStructuredType jaxbStruct = new JAXBStructuredType(new QName(
                fault.getBlock().getName().getNamespaceURI(),
                fault.getName()));

        QName memberName = fault.getElementName();
        JAXBElementMember jaxbMember =
                new JAXBElementMember(memberName, faultType);
        //jaxbMember.setNillable(faultType.isNillable());

        String javaMemberName = getLiteralJavaMemberName(fault);
        JavaStructureMember javaMember = new JavaStructureMember(
                javaMemberName,
                faultType.getJavaType(),
                jaxbMember);
        jaxbMember.setJavaStructureMember(javaMember);
        javaMember.setReadMethod(Names.getJavaMemberReadMethod(javaMember));
        javaMember.setInherited(false);
        jaxbMember.setJavaStructureMember(javaMember);
        jaxbStruct.add(jaxbMember);

        if (isConflictingExceptionClassName(exceptionName)) {
            exceptionName += "_Exception";
        }

        JavaException existingJavaException = _javaExceptions.get(exceptionName);
        if (existingJavaException != null) {
            if (existingJavaException.getName().equals(exceptionName)) {
                if (((JAXBType) existingJavaException.getOwner()).getName().equals(jaxbStruct.getName())
                        || ModelerUtils.isEquivalentLiteralStructures(jaxbStruct, (JAXBStructuredType) existingJavaException.getOwner())) {
                    // we have mapped this fault already
                    if (faultType instanceof JAXBStructuredType) {
                        fault.getBlock().setType((JAXBType) existingJavaException.getOwner());
                    }
                    fault.setJavaException(existingJavaException);
                    return false;
                }
            }
        }

        JavaException javaException = new JavaException(exceptionName, false, jaxbStruct);
        javaException.add(javaMember);
        jaxbStruct.setJavaType(javaException);

        _javaExceptions.put(javaException.getName(), javaException);

        fault.setJavaException(javaException);
        return true;
    }

    protected boolean isRequestResponse() {
        return info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
    }

    protected java.util.List<String> getAsynParameterOrder() {
        //for async operation ignore the parameterOrder
        java.util.List<String> parameterList = new ArrayList<String>();
        Message inputMessage = getInputMessage();
        List<MessagePart> inputParts = inputMessage.getParts();
        for (MessagePart part : inputParts) {
            parameterList.add(part.getName());
        }
        return parameterList;
    }


    protected List<MessagePart> getParameterOrder() {
        List<MessagePart> params = new ArrayList<MessagePart>();
        String parameterOrder = info.portTypeOperation.getParameterOrder();
        java.util.List<String> parameterList;
        boolean parameterOrderPresent = false;
        if ((parameterOrder != null) && !(parameterOrder.trim().equals(""))) {
            parameterList = XmlUtil.parseTokenList(parameterOrder);
            parameterOrderPresent = true;
        } else {
            parameterList = new ArrayList<String>();
        }
        Message inputMessage = getInputMessage();
        Message outputMessage = getOutputMessage();
        List<MessagePart> outputParts = null;
        List<MessagePart> inputParts = inputMessage.getParts();
        //reset the mode and ret flag, as MEssagePArts aer shared across ports
        for (MessagePart part : inputParts) {
            part.setMode(Mode.IN);
            part.setReturn(false);
        }
        if (isRequestResponse()) {
            outputParts = outputMessage.getParts();
            for (MessagePart part : outputParts) {
                part.setMode(Mode.OUT);
                part.setReturn(false);
            }
        }

        if (parameterOrderPresent) {
            boolean validParameterOrder = true;
            Iterator<String> paramOrders = parameterList.iterator();
            // If any part in the parameterOrder is not present in the request or
            // response message, we completely ignore the parameterOrder hint
            while (paramOrders.hasNext()) {
                String param = paramOrders.next();
                boolean partFound = false;
                for (MessagePart part : inputParts) {
                    if (param.equals(part.getName())) {
                        partFound = true;
                        break;
                    }
                }
                // if not found, check in output parts
                if (!partFound) {
                    for (MessagePart part : outputParts) {
                        if (param.equals(part.getName())) {
                            partFound = true;
                            break;
                        }
                    }
                }
                if (!partFound) {
                    warning(info.operation.getEntity(), ModelerMessages.WSDLMODELER_INVALID_PARAMETERORDER_PARAMETER(param, info.operation.getName().getLocalPart()));
                    validParameterOrder = false;
                }
            }

            List<MessagePart> inputUnlistedParts = new ArrayList<MessagePart>();
            List<MessagePart> outputUnlistedParts = new ArrayList<MessagePart>();

            //gather input Parts
            if (validParameterOrder) {
                for (String param : parameterList) {
                    MessagePart part = inputMessage.getPart(param);
                    if (part != null) {
                        params.add(part);
                        continue;
                    }
                    if (isRequestResponse()) {
                        MessagePart outPart = outputMessage.getPart(param);
                        if (outPart != null) {
                            params.add(outPart);
                        }
                    }
                }

                for (MessagePart part : inputParts) {
                    if (!parameterList.contains(part.getName())) {
                        inputUnlistedParts.add(part);
                    }
                }

                if (isRequestResponse()) {
                    // at most one output part should be unlisted
                    for (MessagePart part : outputParts) {
                        if (!parameterList.contains(part.getName())) {
                            MessagePart inPart = inputMessage.getPart(part.getName());
                            //dont add inout as unlisted part
                            if ((inPart != null) && inPart.getDescriptor().equals(part.getDescriptor())) {
                                inPart.setMode(Mode.INOUT);
                            } else {
                                outputUnlistedParts.add(part);
                            }
                        } else {
                            //param list may contain it, check if its INOUT
                            MessagePart inPart = inputMessage.getPart(part.getName());
                            //dont add inout as unlisted part
                            if ((inPart != null) && inPart.getDescriptor().equals(part.getDescriptor())) {
                                inPart.setMode(Mode.INOUT);
                            } else if (!params.contains(part)) {
                                params.add(part);
                            }
                        }
                    }
                    if (outputUnlistedParts.size() == 1) {
                        MessagePart resultPart = outputUnlistedParts.get(0);
                        resultPart.setReturn(true);
                        params.add(resultPart);
                        outputUnlistedParts.clear();
                    }
                }

                //add the input and output unlisted parts
                for (MessagePart part : inputUnlistedParts) {
                    params.add(part);
                }

                for (MessagePart part : outputUnlistedParts) {
                    params.add(part);
                }
                return params;

            }
            //parameterOrder attribute is not valid, we ignore it
            warning(info.operation.getEntity(), ModelerMessages.WSDLMODELER_INVALID_PARAMETER_ORDER_INVALID_PARAMETER_ORDER(info.operation.getName().getLocalPart()));
            parameterList.clear();
        }

        List<MessagePart> outParts = new ArrayList<MessagePart>();

        //construct input parameter list with the same order as in input message
        for (MessagePart part : inputParts) {
            params.add(part);
        }

        if (isRequestResponse()) {
            for (MessagePart part : outputParts) {
                MessagePart inPart = inputMessage.getPart(part.getName());
                if (inPart != null && part.getDescriptorKind() == inPart.getDescriptorKind() &&
                        part.getDescriptor().equals(inPart.getDescriptor())) {
                    inPart.setMode(Mode.INOUT);
                    continue;
                }
                outParts.add(part);
            }

            //append the out parts to the parameterList
            for (MessagePart part : outParts) {
                if (outParts.size() == 1) {
                    part.setReturn(true);
                }
                params.add(part);
            }
        }
        return params;
    }

    /**
     * @param port
     * @param suffix
     * @return the Java ClassName for a port
     */
    protected String getClassName(Port port, String suffix) {
        String prefix = BindingHelper.mangleNameToClassName((port.getName().getLocalPart()));
        return options.defaultPackage + "." + prefix + suffix;
    }

    @Override
    protected boolean isConflictingServiceClassName(String name) {
        return conflictsWithSEIClass(name) || conflictsWithJAXBClass(name) || conflictsWithExceptionClass(name);
    }

    private boolean conflictsWithSEIClass(String name) {
        Set<String> seiNames = classNameCollector.getSeiClassNames();
        return seiNames != null && seiNames.contains(name);
    }

    private boolean conflictsWithJAXBClass(String name) {
        Set<String> jaxbNames = classNameCollector.getJaxbGeneratedClassNames();
        return jaxbNames != null && jaxbNames.contains(name);
    }

    private boolean conflictsWithExceptionClass(String name) {
        Set<String> exceptionNames = classNameCollector.getExceptionClassNames();
        return exceptionNames != null && exceptionNames.contains(name);
    }

    @Override
    protected boolean isConflictingExceptionClassName(String name) {
        return conflictsWithSEIClass(name) || conflictsWithJAXBClass(name);
    }

    protected JAXBModelBuilder getJAXBModelBuilder() {
        return jaxbModelBuilder;
    }

    protected boolean validateWSDLBindingStyle(Binding binding) {
        SOAPBinding soapBinding =
                (SOAPBinding) getExtensionOfType(binding, SOAPBinding.class);

        //dont process the binding
        if (soapBinding == null) {
            soapBinding = (SOAPBinding) getExtensionOfType(binding, SOAP12Binding.class);
        }
        if (soapBinding == null) {
            return false;
        }

        //if soapbind:binding has no style attribute, the default is DOCUMENT
        if (soapBinding.getStyle() == null) {
            soapBinding.setStyle(SOAPStyle.DOCUMENT);
        }

        SOAPStyle opStyle = soapBinding.getStyle();
        for (Iterator iter = binding.operations(); iter.hasNext();) {
            BindingOperation bindingOperation =
                    (BindingOperation) iter.next();
            SOAPOperation soapOperation =
                    (SOAPOperation) getExtensionOfType(bindingOperation,
                            SOAPOperation.class);
            if (soapOperation != null) {
                SOAPStyle currOpStyle = (soapOperation.getStyle() != null) ? soapOperation.getStyle() : soapBinding.getStyle();
                //dont check for the first operation
                if (!currOpStyle.equals(opStyle)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * @param port
     */
    private void applyWrapperStyleCustomization(Port port, PortType portType) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding) getExtensionOfType(portType, JAXWSBinding.class);
        Boolean wrapperStyle = (jaxwsBinding != null) ? jaxwsBinding.isEnableWrapperStyle() : null;
        if (wrapperStyle != null) {
            port.setWrapped(wrapperStyle);
        }
    }

    protected static void setDocumentationIfPresent(
            ModelObject obj,
            Documentation documentation) {
        if (documentation != null && documentation.getContent() != null) {
            obj.setJavaDoc(documentation.getContent());
        }
    }

    protected String getJavaNameForOperation(Operation operation) {
        String name = operation.getJavaMethodName();
        if (Names.isJavaReservedWord(name)) {
            name = "_" + name;
        }
        return name;
    }

    private void reportError(Entity entity,
        String formattedMsg, Exception nestedException ) {
        Locator locator = (entity == null)?null:entity.getLocator();

        SAXParseException e = new SAXParseException2( formattedMsg,
            locator,
            nestedException );
        errReceiver.error(e);
    }

}
