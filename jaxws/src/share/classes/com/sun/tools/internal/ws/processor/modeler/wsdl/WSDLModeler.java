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
package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.codemodel.internal.JCodeModel;
import com.sun.codemodel.internal.JType;
import com.sun.tools.internal.ws.processor.ProcessorOptions;
import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.generator.GeneratorConstants;
import com.sun.tools.internal.ws.processor.model.AsyncOperation;
import com.sun.tools.internal.ws.processor.model.AsyncOperationType;
import com.sun.tools.internal.ws.processor.model.Block;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.ModelException;
import com.sun.tools.internal.ws.processor.model.ModelObject;
import com.sun.tools.internal.ws.processor.model.ModelProperties;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Parameter;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.Request;
import com.sun.tools.internal.ws.processor.model.Response;
import com.sun.tools.internal.ws.processor.model.Service;
import com.sun.tools.internal.ws.processor.model.java.JavaException;
import com.sun.tools.internal.ws.processor.model.java.JavaInterface;
import com.sun.tools.internal.ws.processor.model.java.JavaMethod;
import com.sun.tools.internal.ws.processor.model.java.JavaParameter;
import com.sun.tools.internal.ws.processor.model.java.JavaSimpleType;
import com.sun.tools.internal.ws.processor.model.java.JavaStructureMember;
import com.sun.tools.internal.ws.processor.model.java.JavaType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBElementMember;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBProperty;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBStructuredType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBType;
import com.sun.tools.internal.ws.processor.model.jaxb.JAXBTypeAndAnnotation;
import com.sun.tools.internal.ws.processor.model.jaxb.RpcLitMember;
import com.sun.tools.internal.ws.processor.model.jaxb.RpcLitStructure;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.modeler.ModelerException;
import com.sun.tools.internal.ws.processor.modeler.ModelerUtils;
import com.sun.tools.internal.ws.processor.util.ClassNameCollector;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.internal.ws.wsdl.document.Binding;
import com.sun.tools.internal.ws.wsdl.document.BindingFault;
import com.sun.tools.internal.ws.wsdl.document.BindingOperation;
import com.sun.tools.internal.ws.wsdl.document.Documentation;
import com.sun.tools.internal.ws.wsdl.document.Kinds;
import com.sun.tools.internal.ws.wsdl.document.Message;
import com.sun.tools.internal.ws.wsdl.document.MessagePart;
import com.sun.tools.internal.ws.wsdl.document.OperationStyle;
import com.sun.tools.internal.ws.wsdl.document.PortType;
import com.sun.tools.internal.ws.wsdl.document.WSDLConstants;
import com.sun.tools.internal.ws.wsdl.document.WSDLDocument;
import com.sun.tools.internal.ws.wsdl.document.jaxws.CustomName;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEContent;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAP12Binding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAP12Constants;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPAddress;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBody;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPConstants;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPFault;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPHeader;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPOperation;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPStyle;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPUse;
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.Extensible;
import com.sun.tools.internal.ws.wsdl.framework.NoSuchEntityException;
import com.sun.tools.internal.ws.wsdl.framework.ParseException;
import com.sun.tools.internal.ws.wsdl.framework.ParserListener;
import com.sun.tools.internal.ws.wsdl.framework.ValidationException;
import com.sun.tools.internal.ws.wsdl.parser.SOAPEntityReferenceValidator;
import com.sun.tools.internal.ws.wsdl.parser.WSDLParser;
import com.sun.tools.internal.xjc.api.S2JJAXBModel;
import com.sun.tools.internal.xjc.api.TypeAndAnnotation;
import com.sun.tools.internal.xjc.api.XJC;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import com.sun.xml.internal.ws.model.Mode;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.InputSource;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;


/**
 * The WSDLModeler processes a WSDL to create a Model.
 *
 * @author WS Development Team
 */
public class WSDLModeler extends WSDLModelerBase {

    //map of wsdl:operation QName to <soapenv:Body> child, as per BP it must be unique in a port
    private final Map<QName, QName> uniqueBodyBlocks = new HashMap<QName, QName>();
    private final QName VOID_BODYBLOCK = new QName("");
    private ClassNameCollector classNameCollector;
    private boolean extensions = false;
    protected enum StyleAndUse  {RPC_LITERAL, DOC_LITERAL};
    private ModelerUtils modelerUtils;
    private JAXBModelBuilder jaxbModelBuilder;

    /**
     * @param modelInfo
     * @param options
     */
    public WSDLModeler(WSDLModelInfo modelInfo, Properties options) {
        super(modelInfo, options);
        classNameCollector = new ClassNameCollector();
    }

    public Model buildModel() {
        try {

            parser = new WSDLParser(_modelInfo);
            parser.addParserListener(new ParserListener() {
                public void ignoringExtension(QName name, QName parent) {
                    if (parent.equals(WSDLConstants.QNAME_TYPES)) {
                        // check for a schema element with the wrong namespace URI
                        if (name.getLocalPart().equals("schema")
                            && !name.getNamespaceURI().equals("")) {
                            warn(
                                "wsdlmodeler.warning.ignoringUnrecognizedSchemaExtension",
                                name.getNamespaceURI());
                        }
                    }
                }
                public void doneParsingEntity(QName element, Entity entity) {
                }
            });
            hSet = parser.getUse();

            extensions = Boolean.valueOf(_options.getProperty(ProcessorOptions.EXTENSION));

            useWSIBasicProfile = !extensions;
            document =
                parser.parse();
            document.validateLocally();

            boolean validateWSDL =
                Boolean
                    .valueOf(
                        _options.getProperty(
                            ProcessorOptions.VALIDATE_WSDL_PROPERTY))
                    .booleanValue();
            if (validateWSDL) {
                document.validate(new SOAPEntityReferenceValidator());
            }


            Model model = internalBuildModel(document);
            //ClassNameCollector classNameCollector = new ClassNameCollector();
            classNameCollector.process(model);
            if (classNameCollector.getConflictingClassNames().isEmpty()) {
                return model;
            }
            // do another pass, this time with conflict resolution enabled
            model = internalBuildModel(document);
            classNameCollector.process(model);
            if (classNameCollector.getConflictingClassNames().isEmpty()) {
                // we're done
                return model;
            }
            // give up
            StringBuffer conflictList = new StringBuffer();
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
                conflictList.append((String)iter.next());
            }
            throw new ModelerException(
                "wsdlmodeler.unsolvableNamingConflicts",
                conflictList.toString());

        } catch (ModelException e) {
            throw new ModelerException((Exception)e);
        } catch (ParseException e) {
            throw new ModelerException((Exception)e);
        } catch (ValidationException e) {
            throw new ModelerException((Exception)e);
        }
    }

    private Model internalBuildModel(WSDLDocument document) {

        //build the jaxbModel to be used latter
        buildJAXBModel(document, _modelInfo, classNameCollector);

        QName modelName =
            new QName(
                document.getDefinitions().getTargetNamespaceURI(),
                document.getDefinitions().getName() == null
                    ? "model"
                    : document.getDefinitions().getName());
        Model model = new Model(modelName);
        model.setJAXBModel(getJAXBModelBuilder().getJAXBModel());

        // This fails with the changed classname (WSDLModeler to WSDLModeler11 etc.)
        // with this source comaptibility change the WSDL Modeler class name is changed. Right now hardcoding the
        // modeler class name to the same one being checked in WSDLGenerator.

        model.setProperty(
            ModelProperties.PROPERTY_MODELER_NAME,
            ModelProperties.WSDL_MODELER_NAME);

        _javaTypes = new JavaSimpleTypeCreator();
        _javaExceptions = new HashMap();
        _bindingNameToPortMap = new HashMap();

        // grab target namespace
        model.setTargetNamespaceURI(document.getDefinitions().getTargetNamespaceURI());

        setDocumentationIfPresent(model,
            document.getDefinitions().getDocumentation());

        boolean hasServices = document.getDefinitions().services().hasNext();
        if (hasServices) {
            for (Iterator iter = document.getDefinitions().services();
                iter.hasNext();
                ) {
                processService((com.sun.tools.internal.ws.wsdl.document.Service)iter.next(),
                                model, document);
                hasServices = true;
            }
        } else {
            // emit a warning if there are no service definitions
            warn("wsdlmodeler.warning.noServiceDefinitionsFound");
        }

        return model;
    }


    /* (non-Javadoc)
     * @see WSDLModelerBase#processService(Service, Model, WSDLDocument)
     */
    protected void processService(com.sun.tools.internal.ws.wsdl.document.Service wsdlService, Model model, WSDLDocument document) {
        String serviceInterface = "";
        QName serviceQName = getQNameOf(wsdlService);
        serviceInterface = getServiceInterfaceName(serviceQName, wsdlService);
        if (isConflictingServiceClassName(serviceInterface)) {
            serviceInterface += "_Service";
        }
        Service service =
            new Service(
                serviceQName,
                new JavaInterface(serviceInterface, serviceInterface + "Impl"));

        setDocumentationIfPresent(service, wsdlService.getDocumentation());
        boolean hasPorts = false;
        for (Iterator iter = wsdlService.ports(); iter.hasNext();) {
            boolean processed =
                processPort(
                    (com.sun.tools.internal.ws.wsdl.document.Port)iter.next(),
                    service,
                    document);
            hasPorts = hasPorts || processed;
        }
        if (!hasPorts) {
            // emit a warning if there are no ports
            warn("wsdlmodeler.warning.noPortsInService", wsdlService.getName());
        }else{
            model.addService(service);
        }
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#processPort(Port, Service, WSDLDocument)
     */
    protected boolean processPort(com.sun.tools.internal.ws.wsdl.document.Port wsdlPort,
            Service service, WSDLDocument document) {
        try {

            //clear the  unique block map
            uniqueBodyBlocks.clear();

            QName portQName = getQNameOf(wsdlPort);
            Port port = new Port(portQName);

            setDocumentationIfPresent(port, wsdlPort.getDocumentation());

            SOAPAddress soapAddress =
                (SOAPAddress)getExtensionOfType(wsdlPort, SOAPAddress.class);
            if (soapAddress == null) {
                // not a SOAP port, ignore it
                warn("wsdlmodeler.warning.ignoringNonSOAPPort.noAddress", wsdlPort.getName());
                return false;
            }

            port.setAddress(soapAddress.getLocation());
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
                    (SOAPBinding)getExtensionOfType(binding, SOAPBinding.class);

                if (soapBinding == null) {
                    soapBinding =
                            (SOAPBinding)getExtensionOfType(binding, SOAP12Binding.class);
                    if (soapBinding == null) {
                        // cannot deal with non-SOAP ports
                        warn(
                            "wsdlmodeler.warning.ignoringNonSOAPPort",
                            wsdlPort.getName());
                        return false;
                    }
                    // we can only do soap1.2 if extensions are on
                    if (extensions) {
                        warn("wsdlmodeler.warning.port.SOAPBinding12", wsdlPort.getName());
                    } else {
                        warn("wsdlmodeler.warning.ignoringSOAPBinding12",
                                wsdlPort.getName());
                        return false;
                    }
                }

                if (soapBinding.getTransport() == null
                    || (!soapBinding.getTransport().equals(
                        SOAPConstants.URI_SOAP_TRANSPORT_HTTP) && !soapBinding.getTransport().equals(
                        SOAP12Constants.URI_SOAP_TRANSPORT_HTTP))) {
                    // cannot deal with non-HTTP ports
                    warn(
                        "wsdlmodeler.warning.ignoringSOAPBinding.nonHTTPTransport",
                        wsdlPort.getName());
                    return false;
                }

                /**
                 * validate wsdl:binding uniqueness in style, e.g. rpclit or doclit
                 * ref: WSI BP 1.1 R 2705
                 */
                if(!validateWSDLBindingStyle(binding)){
                    if(extensions){
                        warn("wsdlmodeler.warning.port.SOAPBinding.mixedStyle", wsdlPort.getName());
                    }else{
                        fail("wsdlmodeler.warning.ignoringSOAPBinding.mixedStyle",
                                wsdlPort.getName());
                        return false;
                    }
                }

                port.setStyle(soapBinding.getStyle());
                boolean hasOverloadedOperations = false;
                Set<String> operationNames = new HashSet<String>();
                for (Iterator iter = portType.operations(); iter.hasNext();) {
                    com.sun.tools.internal.ws.wsdl.document.Operation operation =
                        (com.sun.tools.internal.ws.wsdl.document.Operation)iter.next();

                    if (operationNames.contains(operation.getName())) {
                        hasOverloadedOperations = true;
                        break;
                    }
                    operationNames.add(operation.getName());

                    for (Iterator itr = binding.operations();
                        iter.hasNext();
                        ) {
                        BindingOperation bindingOperation =
                            (BindingOperation)itr.next();
                        if (operation
                            .getName()
                            .equals(bindingOperation.getName())) {
                            break;
                        } else if (!itr.hasNext()) {
                            throw new ModelerException(
                                "wsdlmodeler.invalid.bindingOperation.notFound",
                                new Object[] {
                                    operation.getName(),
                                    binding.getName()});
                        }
                    }
                }

                Map headers = new HashMap();
                boolean hasOperations = false;
                for (Iterator iter = binding.operations(); iter.hasNext();) {
                    BindingOperation bindingOperation =
                        (BindingOperation)iter.next();

                    com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation =
                        null;
                    Set operations =
                        portType.getOperationsNamed(bindingOperation.getName());
                    if (operations.size() == 0) {
                        // the WSDL document is invalid
                        throw new ModelerException(
                            "wsdlmodeler.invalid.bindingOperation.notInPortType",
                            new Object[] {
                                bindingOperation.getName(),
                                binding.getName()});
                    } else if (operations.size() == 1) {
                        portTypeOperation =
                            (com.sun.tools.internal.ws.wsdl.document.Operation)operations
                                .iterator()
                                .next();
                    } else {
                        boolean found = false;
                        String expectedInputName =
                            bindingOperation.getInput().getName();
                        String expectedOutputName =
                            bindingOperation.getOutput().getName();

                        for (Iterator iter2 = operations.iterator();iter2.hasNext();) {
                            com.sun.tools.internal.ws.wsdl.document.Operation candidateOperation =
                                (com.sun.tools.internal.ws.wsdl.document.Operation)iter2
                                    .next();

                            if (expectedInputName == null) {
                                // the WSDL document is invalid
                                throw new ModelerException(
                                    "wsdlmodeler.invalid.bindingOperation.missingInputName",
                                    new Object[] {
                                        bindingOperation.getName(),
                                        binding.getName()});
                            }
                            if (expectedOutputName == null) {
                                // the WSDL document is invalid
                                throw new ModelerException(
                                    "wsdlmodeler.invalid.bindingOperation.missingOutputName",
                                    new Object[] {
                                        bindingOperation.getName(),
                                        binding.getName()});
                            }
                            if (expectedInputName
                                .equals(candidateOperation.getInput().getName())
                                && expectedOutputName.equals(
                                    candidateOperation
                                        .getOutput()
                                        .getName())) {
                                if (found) {
                                    // the WSDL document is invalid
                                    throw new ModelerException(
                                        "wsdlmodeler.invalid.bindingOperation.multipleMatchingOperations",
                                        new Object[] {
                                            bindingOperation.getName(),
                                            binding.getName()});
                                }
                                // got it!
                                found = true;
                                portTypeOperation = candidateOperation;
                            }
                        }
                        if (!found) {
                            // the WSDL document is invalid
                            throw new ModelerException(
                                "wsdlmodeler.invalid.bindingOperation.notFound",
                                new Object[] {
                                    bindingOperation.getName(),
                                    binding.getName()});
                        }
                    }
                    if(!isProvider){
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

                        Operation operation = processSOAPOperation();
                        if (operation != null) {
                            port.addOperation(operation);
                            hasOperations = true;
                        }
                    }
                }
                if (!isProvider && !hasOperations) {
                    // emit a warning if there are no operations, except when its a provider port
                    warn("wsdlmodeler.warning.noOperationsInPort",
                        wsdlPort.getName());
                    return false;
                }
                createJavaInterfaceForPort(port, isProvider);
                PortType pt = binding.resolvePortType(document);
                String jd = (pt.getDocumentation() != null)?pt.getDocumentation().getContent():null;
                port.getJavaInterface().setJavaDoc(jd);
                _bindingNameToPortMap.put(bindingName, port);
            }

            // now deal with the configured handlers
            port.setClientHandlerChainInfo(
                _modelInfo.getClientHandlerChainInfo());
            port.setServerHandlerChainInfo(
                _modelInfo.getServerHandlerChainInfo());

            service.addPort(port);
            applyPortMethodCustomization(port, wsdlPort);
            applyWrapperStyleCustomization(port, binding.resolvePortType(document));

            return true;

        } catch (NoSuchEntityException e) {
            warn(e);
            // should not happen
            return false;
        }
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#processSOAPOperation()
     */
    protected Operation processSOAPOperation() {
        Operation operation =
            new Operation(new QName(null, info.bindingOperation.getName()));

        setDocumentationIfPresent(
            operation,
            info.portTypeOperation.getDocumentation());

        if (info.portTypeOperation.getStyle()
            != OperationStyle.REQUEST_RESPONSE
            && info.portTypeOperation.getStyle() != OperationStyle.ONE_WAY) {
            if(extensions){
                warn(
                    "wsdlmodeler.warning.ignoringOperation.notSupportedStyle",
                    info.portTypeOperation.getName());
                return null;
            }
            fail("wsdlmodeler.invalid.operation.notSupportedStyle",
                    new Object[]{info.portTypeOperation.getName(),
                    info.port.resolveBinding(document).resolvePortType(document).getName()});
        }

        SOAPStyle soapStyle = info.soapBinding.getStyle();

        // find out the SOAP operation extension, if any
        SOAPOperation soapOperation =
            (SOAPOperation)getExtensionOfType(info.bindingOperation,
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
        info.uniqueOperationName = uniqueOperationName;

        //attachment
        SOAPBody soapRequestBody = getSOAPRequestBody();
        if (soapRequestBody == null) {
            // the WSDL document is invalid
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.inputMissingSoapBody",
                new Object[] { info.bindingOperation.getName()});
        }

        if (soapStyle == SOAPStyle.RPC) {
            if (soapRequestBody.isEncoded()) {
                throw new ModelerException("wsdlmodeler20.rpcenc.not.supported");
            }
            return processLiteralSOAPOperation(StyleAndUse.RPC_LITERAL);
        }
        // document style
        return processLiteralSOAPOperation(StyleAndUse.DOC_LITERAL);
    }

    protected Operation processLiteralSOAPOperation(StyleAndUse styleAndUse){
        //returns false if the operation name is not acceptable
        if(!applyOperationNameCustomization())
            return null;

        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        Request request = new Request();
        Response response = new Response();
        info.operation.setUse(SOAPUse.LITERAL);
        SOAPBody soapRequestBody = getSOAPRequestBody();
        if((StyleAndUse.DOC_LITERAL == styleAndUse) && (soapRequestBody.getNamespace() != null)){
            warn("wsdlmodeler.warning.r2716", new Object[]{"soapbind:body", info.bindingOperation.getName()});
        }

        Message inputMessage = getInputMessage();

        SOAPBody soapResponseBody = null;
        Message outputMessage = null;
        if (isRequestResponse) {
            soapResponseBody = getSOAPResponseBody();
            if (isOperationDocumentLiteral(styleAndUse) && (soapResponseBody.getNamespace() != null)) {
                warn("wsdlmodeler.warning.r2716", new Object[]{"soapbind:body", info.bindingOperation.getName()});
            }
            outputMessage = getOutputMessage();
        }

        //ignore operation if there are more than one root part
        if(!validateMimeParts(getMimeParts(info.bindingOperation.getInput())) ||
                !validateMimeParts(getMimeParts(info.bindingOperation.getOutput())))
            return null;


        if(!validateBodyParts(info.bindingOperation)){
            // BP 1.1
            // R2204   A document-literal binding in a DESCRIPTION MUST refer, in each of its soapbind:body element(s),
            // only to wsdl:part element(s) that have been defined using the element attribute.

            // R2203   An rpc-literal binding in a DESCRIPTION MUST refer, in its soapbind:body element(s),
            // only to wsdNl:part element(s) that have been defined using the type attribute.
            if(isOperationDocumentLiteral(styleAndUse))
                if(extensions)
                    warn("wsdlmodeler.warning.ignoringOperation.cannotHandleTypeMessagePart", info.portTypeOperation.getName());
                else
                    fail("wsdlmodeler.invalid.doclitoperation", info.portTypeOperation.getName());
            else if(isOperationRpcLiteral(styleAndUse)) {
                if(extensions)
                    warn("wsdlmodeler.warning.ignoringOperation.cannotHandleElementMessagePart", info.portTypeOperation.getName());
                else
                    fail("wsdlmodeler.invalid.rpclitoperation", info.portTypeOperation.getName());
            }
            return null;
        }

        // Process parameterOrder and get the parameterList
        List<MessagePart> parameterList = getParameterOrder();

        //binding is invalid in the wsdl, ignore the operation.
        if(!setMessagePartsBinding(styleAndUse))
            return null;

        List<Parameter> params = null;
        boolean unwrappable = isUnwrappable();
        info.operation.setWrapped(unwrappable);
        if(isOperationDocumentLiteral(styleAndUse)){
            params = getDoclitParameters(request, response, parameterList);
        }else if(isOperationRpcLiteral(styleAndUse)){
            String operationName = info.bindingOperation.getName();
            Block reqBlock = null;
            if (inputMessage != null) {
                QName name = new QName(getRequestNamespaceURI(soapRequestBody), operationName);
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                reqBlock = new Block(name, rpcStruct);
                request.addBodyBlock(reqBlock);
            }

            Block resBlock = null;
            if (isRequestResponse && outputMessage != null) {
                QName name = new QName(getResponseNamespaceURI(soapResponseBody), operationName + "Response");
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                resBlock = new Block(name, rpcStruct);
                response.addBodyBlock(resBlock);
            }
            params = getRpcLitParameters(request, response, reqBlock, resBlock, parameterList);
        }


        if(!validateParameterName(params)) {
            return null;
        }

        // create a definitive list of parameters to match what we'd like to get
        // in the java interface (which is generated much later), parameterOrder
        List<Parameter> definitiveParameterList = new ArrayList<Parameter>();
        for (Parameter param: params) {
            if(param.isReturn()){
                info.operation.setProperty(WSDL_RESULT_PARAMETER, param);
                response.addParameter(param);
                continue;
            }
            if(param.isIN()){
                request.addParameter(param);
            }else if(param.isOUT()){
                response.addParameter(param);
            }else if(param.isINOUT()){
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
        QName body = VOID_BODYBLOCK;
        QName opName = null;

        if(bb.hasNext()){
            body = bb.next().getName();
            opName = uniqueBodyBlocks.get(body);
        }else{
            //there is no body block
            body = VOID_BODYBLOCK;
            opName = uniqueBodyBlocks.get(VOID_BODYBLOCK);
        }
        if(opName != null){
            fail("wsdlmodeler.nonUnique.body", new Object[]{info.port.getName(), info.operation.getName(), opName, body});
        }else{
            uniqueBodyBlocks.put(body, info.operation.getName());
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
        if(isAsync(portType, info.portTypeOperation)){
            addAsyncOperations(info.operation, styleAndUse);
        }

        return info.operation;
    }

    /**
     *
     * @param params
     * @return
     */
    private boolean validateParameterName(List<Parameter> params) {
        Message msg = getInputMessage();
        for(Parameter param : params){
            if(param.isOUT())
                continue;
            if(param.getCustomName() != null){
                if(getEnvironment().getNames().isJavaReservedWord(param.getCustomName())){
                    if(extensions)
                        warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.customName",
                                new Object[]{info.operation.getName(), param.getCustomName()});
                    else
                        fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.customName",
                                new Object[]{info.operation.getName(), param.getCustomName()});
                    return false;
                }
                return true;
            }
            //process doclit wrapper style
            if(param.isEmbedded() && !(param.getBlock().getType() instanceof RpcLitStructure)){
                if(getEnvironment().getNames().isJavaReservedWord(param.getName())){
                    if(extensions)
                        warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.wrapperStyle", new Object[]{info.operation.getName(), param.getName(), param.getBlock().getName()});
                    else
                        fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.wrapperStyle", new Object[]{info.operation.getName(), param.getName(), param.getBlock().getName()});
                    return false;
                }
            }else{
                //non-wrapper style and rpclit
                if(getEnvironment().getNames().isJavaReservedWord(param.getName())){
                    if(extensions)
                        warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.nonWrapperStyle", new Object[]{info.operation.getName(), msg.getName(), param.getName()});
                    else
                        fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.nonWrapperStyle", new Object[]{info.operation.getName(), msg.getName(), param.getName()});
                    return false;
                }
            }
        }

        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        if(isRequestResponse){
            msg = getOutputMessage();
            for(Parameter param : params){
                if(param.isIN())
                    continue;
                if(param.getCustomName() != null){
                    if(getEnvironment().getNames().isJavaReservedWord(param.getCustomName())){
                        if(extensions)
                            warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.customName",
                                    new Object[]{info.operation.getName(), param.getCustomName()});
                        else
                            fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.customName",
                                    new Object[]{info.operation.getName(), param.getCustomName()});
                        return false;
                    }
                    return true;
                }
                //process doclit wrapper style
                if(param.isEmbedded() && !(param.getBlock().getType() instanceof RpcLitStructure)){
                    if(param.isReturn())
                        continue;
                    if(!param.getName().equals("return") && getEnvironment().getNames().isJavaReservedWord(param.getName())){
                        if(extensions)
                            warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.wrapperStyle",
                                    new Object[]{info.operation.getName(), param.getName(), param.getBlock().getName()});
                        else
                            fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.wrapperStyle",
                                    new Object[]{info.operation.getName(), param.getName(), param.getBlock().getName()});
                        return false;
                    }
                }else{
                    if(param.isReturn())
                        continue;

                    //non-wrapper style and rpclit
                    if(getEnvironment().getNames().isJavaReservedWord(param.getName())){
                        if(extensions)
                            warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.nonWrapperStyle", new Object[]{info.operation.getName(), msg.getName(), param.getName()});
                        else
                            fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.nonWrapperStyle",
                                    new Object[]{info.operation.getName(), msg.getName(), param.getName()});
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * @return
     */
    private boolean enableMimeContent() {
        //first we look at binding operation
        JAXWSBinding jaxwsCustomization = (JAXWSBinding)getExtensionOfType(info.bindingOperation, JAXWSBinding.class);
        Boolean mimeContentMapping = (jaxwsCustomization != null)?jaxwsCustomization.isEnableMimeContentMapping():null;
        if(mimeContentMapping != null)
            return mimeContentMapping;

        //then in wsdl:binding
        Binding binding = info.port.resolveBinding(info.document);
        jaxwsCustomization = (JAXWSBinding)getExtensionOfType(binding, JAXWSBinding.class);
        mimeContentMapping = (jaxwsCustomization != null)?jaxwsCustomization.isEnableMimeContentMapping():null;
        if(mimeContentMapping != null)
            return mimeContentMapping;

        //at last look in wsdl:definitions
        jaxwsCustomization = (JAXWSBinding)getExtensionOfType(info.document.getDefinitions(), JAXWSBinding.class);
        mimeContentMapping = (jaxwsCustomization != null)?jaxwsCustomization.isEnableMimeContentMapping():null;
        if(mimeContentMapping != null)
            return mimeContentMapping;
        return false;
    }

    /**
     *
     */
    private boolean applyOperationNameCustomization() {
        JAXWSBinding jaxwsCustomization = (JAXWSBinding)getExtensionOfType(info.portTypeOperation, JAXWSBinding.class);
        String operationName = (jaxwsCustomization != null)?((jaxwsCustomization.getMethodName() != null)?jaxwsCustomization.getMethodName().getName():null):null;
        if(operationName != null){
            if(getEnvironment().getNames().isJavaReservedWord(operationName)){
                if(extensions)
                    warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.customizedOperationName", new Object[]{info.operation.getName(), operationName});
                else
                    fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.customizedOperationName", new Object[]{info.operation.getName(), operationName});
                return false;
            }

            info.operation.setCustomizedName(operationName);
        }

        if(getEnvironment().getNames().isJavaReservedWord(info.operation.getJavaMethodName())){
            if(extensions)
                warn("wsdlmodeler.warning.ignoringOperation.javaReservedWordNotAllowed.operationName", new Object[]{info.operation.getName()});
            else
                fail("wsdlmodeler.invalid.operation.javaReservedWordNotAllowed.operationName", new Object[]{info.operation.getName()});
            return false;
        }
        return true;
    }

    protected String getAsyncOperationName(Operation operation){
        String name = operation.getCustomizedName();
        if(name == null)
            name = operation.getUniqueName();
        return name;
    }

    /**
     * @param styleAndUse
     */
    private void addAsyncOperations(Operation syncOperation, StyleAndUse styleAndUse) {
        Operation operation = createAsyncOperation(syncOperation, styleAndUse, AsyncOperationType.POLLING);
        if(operation != null)
            info.modelPort.addOperation(operation);

        operation = createAsyncOperation(syncOperation, styleAndUse, AsyncOperationType.CALLBACK);
        if(operation != null)
            info.modelPort.addOperation(operation);
    }

    /**
     *
     * @param syncOperation
     * @param styleAndUse
     * @param asyncType
     * @return
     */
    private Operation createAsyncOperation(Operation syncOperation, StyleAndUse styleAndUse, AsyncOperationType asyncType) {
        boolean isRequestResponse = info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
        if(!isRequestResponse)
            return null;
        Request request = new Request();
        Response response = new Response();

        //create async operations
        AsyncOperation operation = new AsyncOperation(info.operation);

        //creation the async operation name: operationName+Async or customized name
        //operation.setName(new QName(operation.getName().getNamespaceURI(), getAsyncOperationName(info.portTypeOperation, operation)));
        if(asyncType.equals(AsyncOperationType.CALLBACK))
            operation.setUniqueName(info.operation.getUniqueName()+"_async_callback");
        else if(asyncType.equals(AsyncOperationType.POLLING))
            operation.setUniqueName(info.operation.getUniqueName()+"_async_polling");

        setDocumentationIfPresent(
            operation,
            info.portTypeOperation.getDocumentation());

        operation.setAsyncType(asyncType);
        operation.setSOAPAction(info.operation.getSOAPAction());
        boolean unwrappable = info.operation.isWrapped();
        operation.setWrapped(unwrappable);
        SOAPBody soapRequestBody = getSOAPRequestBody();

        Message inputMessage = getInputMessage();

        SOAPBody soapResponseBody = null;
        Message outputMessage = null;
        if (isRequestResponse) {
            soapResponseBody = getSOAPResponseBody();
            outputMessage = getOutputMessage();
        }

        // Process parameterOrder and get the parameterList
        java.util.List<String> parameterList = getAsynParameterOrder();

        List<Parameter> inParameters = null;
        if(isOperationDocumentLiteral(styleAndUse)){
            inParameters = getRequestParameters(request, parameterList);
            // outParameters = getResponseParameters(response);
            // re-create parameterList with unwrapped parameters
            if(unwrappable){
                List<String> unwrappedParameterList = new ArrayList<String>();
                if(inputMessage != null){
                    Iterator<MessagePart> parts = inputMessage.parts();
                    if(parts.hasNext()){
                        MessagePart part = parts.next();
                        JAXBType jaxbType = getJAXBType(part.getDescriptor());
                        List<JAXBProperty> memberList = jaxbType.getWrapperChildren();
                        Iterator<JAXBProperty> props = memberList.iterator();
                        while(props.hasNext()){
                            JAXBProperty prop = props.next();
                            unwrappedParameterList.add(prop.getElementName().getLocalPart());
                        }
                    }
                }

                parameterList.clear();
                parameterList.addAll(unwrappedParameterList);
            }
        }else if(isOperationRpcLiteral(styleAndUse)){
            String operationName = info.bindingOperation.getName();
            Block reqBlock = null;
            if (inputMessage != null) {
                QName name = new QName(getRequestNamespaceURI(soapRequestBody), operationName);
                RpcLitStructure rpcStruct = new RpcLitStructure(name, getJAXBModelBuilder().getJAXBModel());
                rpcStruct.setJavaType(new JavaSimpleType("com.sun.xml.internal.ws.encoding.jaxb.RpcLitPayload", null));
                reqBlock = new Block(name, rpcStruct);
                request.addBodyBlock(reqBlock);
            }
            inParameters = createRpcLitRequestParameters(request, parameterList, reqBlock);
        }

        // add response blocks, we dont need to create respnse parameters, just blocks will be fine, lets
        // copy them from sync optraions
        //copy the response blocks from the sync operation
        Iterator<Block> blocks = info.operation.getResponse().getBodyBlocks();

        while(blocks.hasNext()){
            response.addBodyBlock(blocks.next());
        }

        blocks = info.operation.getResponse().getHeaderBlocks();
        while(blocks.hasNext()){
            response.addHeaderBlock(blocks.next());
        }

        blocks = info.operation.getResponse().getAttachmentBlocks();
        while(blocks.hasNext()){
            response.addAttachmentBlock(blocks.next());
        }

        List<MessagePart> outputParts = outputMessage.getParts();

        // handle headers
        int numOfOutMsgParts = outputParts.size();

        if(isRequestResponse){
            if(numOfOutMsgParts == 1){
                MessagePart part = outputParts.get(0);
                if(isOperationDocumentLiteral(styleAndUse)){
                    JAXBType type = getJAXBType(part.getDescriptor());
                    operation.setResponseBean(type);
                }else if(isOperationRpcLiteral(styleAndUse)){
                    String operationName = info.bindingOperation.getName();
                    Block resBlock = null;
                    if (isRequestResponse && outputMessage != null) {
                        resBlock = info.operation.getResponse().getBodyBlocksMap().get(new QName(getResponseNamespaceURI(soapResponseBody),
                                operationName + "Response"));
                    }
                    RpcLitStructure resBean = (resBlock == null) ? null : (RpcLitStructure)resBlock.getType();
                    List<RpcLitMember> members = resBean.getRpcLitMembers();

                    operation.setResponseBean(members.get(0));
                }
            }else{
                //create response bean
                String nspace = "";
                QName responseBeanName = new QName(nspace,getAsyncOperationName(info.operation) +"Response");
                JAXBType responseBeanType = getJAXBType(responseBeanName);
                operation.setResponseBean(responseBeanType);
            }
        }
        QName respBeanName = new QName(soapResponseBody.getNamespace(),getAsyncOperationName(info.operation)+"Response");
        Block block = new Block(respBeanName, operation.getResponseBeanType());
        JavaType respJavaType = operation.getResponseBeanJavaType();
        JAXBType respType = new JAXBType(respBeanName, respJavaType);
        Parameter respParam = ModelerUtils.createParameter(info.operation.getName()+"Response", respType, block);
        respParam.setParameterIndex(-1);
        response.addParameter(respParam);
        operation.setProperty(WSDL_RESULT_PARAMETER, respParam.getName());


        List<String> definitiveParameterList = new ArrayList<String>();
        int parameterOrderPosition = 0;
        for (String name: parameterList) {
            Parameter inParameter = null;

            inParameter = ModelerUtils.getParameter(name, inParameters);
            if(inParameter == null){
                if(extensions)
                    warn("wsdlmodeler.warning.ignoringOperation.partNotFound", new Object[]{info.operation.getName().getLocalPart(), name});
                else
                    fail("wsdlmodeler.error.partNotFound", new Object[]{info.operation.getName().getLocalPart(), name});
                return null;
            }
            request.addParameter(inParameter);
            inParameter.setParameterIndex(parameterOrderPosition);
            definitiveParameterList.add(name);
            parameterOrderPosition++;
        }

        if (isRequestResponse) {
            operation.setResponse(response);
        }

        //  add callback handlerb Parameter to request
        if(operation.getAsyncType().equals(AsyncOperationType.CALLBACK)){
            JavaType cbJavaType = operation.getCallBackType();
            JAXBType callbackType = new JAXBType(respBeanName, cbJavaType);
            Parameter cbParam = ModelerUtils.createParameter("asyncHandler", callbackType, block);
            request.addParameter(cbParam);
        }

        operation.setRequest(request);

        return operation;
    }

    protected boolean isAsync(com.sun.tools.internal.ws.wsdl.document.PortType portType, com.sun.tools.internal.ws.wsdl.document.Operation wsdlOperation){
        //First look into wsdl:operation
        JAXWSBinding jaxwsCustomization = (JAXWSBinding)getExtensionOfType(wsdlOperation, JAXWSBinding.class);
        Boolean isAsync = (jaxwsCustomization != null)?jaxwsCustomization.isEnableAsyncMapping():null;

        if(isAsync != null)
            return isAsync;

        // then into wsdl:portType
        QName portTypeName = new QName(portType.getDefining().getTargetNamespaceURI(), portType.getName());
        if(portTypeName != null){
            jaxwsCustomization = (JAXWSBinding)getExtensionOfType(portType, JAXWSBinding.class);
            isAsync = (jaxwsCustomization != null)?jaxwsCustomization.isEnableAsyncMapping():null;
            if(isAsync != null)
                return isAsync;
        }

        //then wsdl:definitions
        jaxwsCustomization = (JAXWSBinding)getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        isAsync = (jaxwsCustomization != null)?jaxwsCustomization.isEnableAsyncMapping():null;
        if(isAsync != null)
            return isAsync;
        return false;
    }

    protected void handleLiteralSOAPHeaders(Request request, Response response, Iterator headerParts, Set duplicateNames, List definitiveParameterList, boolean processRequest) {
        QName headerName = null;
        Block headerBlock = null;
        JAXBType jaxbType = null;
        int parameterOrderPosition = definitiveParameterList.size();
        while(headerParts.hasNext()){
            MessagePart part = (MessagePart)headerParts.next();
            headerName = part.getDescriptor();
            jaxbType = getJAXBType(headerName);
            headerBlock = new Block(headerName, jaxbType);
            Extensible ext;
            if(processRequest){
                ext = info.bindingOperation.getInput();
            }else{
                ext = info.bindingOperation.getOutput();
            }
            Message headerMessage = getHeaderMessage(part, ext);

            if(processRequest){
                request.addHeaderBlock(headerBlock);
            }else{
                response.addHeaderBlock(headerBlock);
            }

            Parameter parameter = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
            parameter.setParameterIndex(parameterOrderPosition);
            setCustomizedParameterName(info.bindingOperation, headerMessage, part, parameter, false);
            if (processRequest && definitiveParameterList != null) {
                request.addParameter(parameter);
                definitiveParameterList.add(parameter.getName());
            } else {
                if (definitiveParameterList != null) {
                    for (Iterator iterInParams = definitiveParameterList.iterator(); iterInParams.hasNext();) {
                        String inParamName =
                            (String)iterInParams.next();
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
                }
                response.addParameter(parameter);
            }
            parameterOrderPosition++;
        }

    }

    protected void handleLiteralSOAPFault(Response response, Set duplicateNames){
        for (Iterator iter = info.bindingOperation.faults(); iter.hasNext();){
            BindingFault bindingFault = (BindingFault)iter.next();
            com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault = null;
            for(Iterator iter2 = info.portTypeOperation.faults(); iter2.hasNext();){
                com.sun.tools.internal.ws.wsdl.document.Fault aFault =
                    (com.sun.tools.internal.ws.wsdl.document.Fault)iter2.next();
                if(aFault.getName().equals(bindingFault.getName())){
                    if(portTypeFault != null){
                        // the WSDL document is invalid, a wsld:fault in a wsdl:operation of a portType can be bound only once
                        throw new ModelerException("wsdlmodeler.invalid.bindingFault.notUnique",
                                new Object[]{bindingFault.getName(), info.bindingOperation.getName()});
                    }
                    portTypeFault = aFault;
                }
            }

            // The WSDL document is invalid, the wsdl:fault in abstract operation is does not have any binding
            if(portTypeFault == null){
                throw new ModelerException("wsdlmodeler.invalid.bindingFault.notFound",
                        new Object[] {bindingFault.getName(), info.bindingOperation.getName()});

            }

            // wsdl:fault message name is used to create the java exception name later on
            String faultName = getFaultClassName(portTypeFault);
            Fault fault = new Fault(faultName);
            setDocumentationIfPresent(fault, portTypeFault.getDocumentation());

            //get the soapbind:fault from wsdl:fault in the binding
            SOAPFault soapFault = (SOAPFault)getExtensionOfType(bindingFault, SOAPFault.class);

            // The WSDL document is invalid, can't have wsdl:fault without soapbind:fault
            if(soapFault == null){
                throw new ModelerException("wsdlmodeler.invalid.bindingFault.outputMissingSoapFault",
                    new Object[]{bindingFault.getName(), info.bindingOperation.getName()});
            }

            //the soapbind:fault must have use="literal" or no use attribute, in that case its assumed "literal"
            if(!soapFault.isLiteral()){
                if(extensions)
                warn("wsdlmodeler.warning.ignoringFault.notLiteral",
                    new Object[]{bindingFault.getName(), info.bindingOperation.getName()});
                else
                    fail("wsdlmodeler.invalid.operation.fault.notLiteral",
                            new Object[]{bindingFault.getName(), info.bindingOperation.getName()});
                continue;
            }

            // the soapFault name must be present
            if(soapFault.getName() == null){
                warn("wsdlmodeler.invalid.bindingFault.noSoapFaultName",
                    new Object[]{bindingFault.getName(), info.bindingOperation.getName()});
            }else if (!soapFault.getName().equals(bindingFault.getName())) {
                // the soapFault name must match bindingFault name
                warn("wsdlmodeler.invalid.bindingFault.wrongSoapFaultName",
                    new Object[]{soapFault.getName(), bindingFault.getName(), info.bindingOperation.getName()});
            }else if(soapFault.getNamespace() != null){
                // bug fix: 4852729
                warn("wsdlmodeler.warning.r2716r2726",
                    new Object[] { "soapbind:fault", soapFault.getName()});
            }

            String faultNamespaceURI = soapFault.getNamespace();
            if(faultNamespaceURI == null){
                faultNamespaceURI = portTypeFault.getMessage().getNamespaceURI();
            }

            com.sun.tools.internal.ws.wsdl.document.Message faultMessage = portTypeFault.resolveMessage(info.document);
            Iterator iter2 = faultMessage.parts();
            if(!iter2.hasNext()){
                // the WSDL document is invalid
                throw new ModelerException("wsdlmodeler.invalid.bindingFault.emptyMessage",
                    new Object[]{bindingFault.getName(), faultMessage.getName()});
            }
            MessagePart faultPart = (MessagePart)iter2.next();
            QName faultQName = faultPart.getDescriptor();

            // Don't include fault messages with non-unique soap:fault names
            if (duplicateNames.contains(faultQName)) {
                warn("wsdlmodeler.duplicate.fault.soap.name",
                    new Object[] {bindingFault.getName(), info.portTypeOperation.getName(), faultPart.getName()});
                continue;
            }

            if (iter2.hasNext()) {
                // the WSDL document is invalid
                throw new ModelerException("wsdlmodeler.invalid.bindingFault.messageHasMoreThanOnePart",
                    new Object[]{bindingFault.getName(), faultMessage.getName()});
            }

            if (faultPart.getDescriptorKind() != SchemaKinds.XSD_ELEMENT) {
                throw new ModelerException("wsdlmodeler.invalid.message.partMustHaveElementDescriptor",
                    new Object[]{faultMessage.getName(), faultPart.getName()});
            }

            JAXBType jaxbType = getJAXBType(faultPart.getDescriptor());

            fault.setElementName(faultPart.getDescriptor());
            fault.setJavaMemberName(getEnvironment().getNames().getExceptionClassMemberName());

            Block faultBlock = new Block(faultQName, jaxbType);
            fault.setBlock(faultBlock);
            createParentFault(fault);
            createSubfaults(fault);
            if(!response.getFaultBlocksMap().containsKey(faultBlock.getName()))
                response.addFaultBlock(faultBlock);
            info.operation.addFault(fault);
        }
    }

    /**
     * @param portTypeFault
     * @return
     */
    private String getFaultClassName(com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(portTypeFault, JAXWSBinding.class);
        if(jaxwsBinding != null){
            CustomName className = jaxwsBinding.getClassName();
            if(className != null){
                return className.getName();
            }
        }
        return portTypeFault.getMessage().getLocalPart();
    }

    protected  boolean setMessagePartsBinding(StyleAndUse styleAndUse){
        SOAPBody inBody = getSOAPRequestBody();
        Message inMessage = getInputMessage();
        if(!setMessagePartsBinding(inBody, inMessage, styleAndUse, true))
            return false;

        if(isRequestResponse()){
            SOAPBody outBody = getSOAPResponseBody();
            Message outMessage = getOutputMessage();
            if(!setMessagePartsBinding(outBody, outMessage, styleAndUse, false))
                return false;
        }
        return true;
    }

    //returns false if the wsdl is invalid and operation should be ignored
    protected boolean setMessagePartsBinding(SOAPBody body, Message message, StyleAndUse styleAndUse, boolean isInput) {
        List<MessagePart> parts = new ArrayList<MessagePart>();

        //get Mime parts
        List<MessagePart> mimeParts = null;
        List<MessagePart> headerParts = null;
        List<MessagePart> bodyParts = getBodyParts(body, message);

        if(isInput){
            headerParts = getHeaderPartsFromMessage(message, isInput);
            mimeParts = getMimeContentParts(message, info.bindingOperation.getInput());
        }else{
            headerParts = getHeaderPartsFromMessage(message, isInput);
            mimeParts = getMimeContentParts(message, info.bindingOperation.getOutput());
        }

        //As of now WSDL MIME binding is not supported, so throw the exception when such binding is encounterd
//        if(mimeParts.size() > 0){
//            fail("wsdlmodeler.unsupportedBinding.mime", new Object[]{});
//        }

        //if soap:body parts attribute not there, then all unbounded message parts will
        // belong to the soap body
        if(bodyParts == null){
            bodyParts = new ArrayList<MessagePart>();
            for(Iterator<MessagePart> iter = message.parts();iter.hasNext();) {
                MessagePart mPart = iter.next();
                //Its a safe assumption that the parts in the message not belonging to header or mime will
                // belong to the body?
                if(mimeParts.contains(mPart) || headerParts.contains(mPart) || boundToFault(mPart.getName())){
                    //throw error that a part cant be bound multiple times, not ignoring operation, if there
                    //is conflict it will fail latter
                    if(extensions)
                        warn("wsdlmodeler.warning.bindingOperation.multiplePartBinding",
                                new Object[]{info.bindingOperation.getName(), mPart.getName()});
                    else
                        fail("wsdlmodeler.invalid.bindingOperation.multiplePartBinding",
                                new Object[]{info.bindingOperation.getName(), mPart.getName()});
                }
                bodyParts.add(mPart);
            }
        }

        //now build the final parts list with header, mime parts and body parts
        for(Iterator iter = message.parts();iter.hasNext();) {
            MessagePart mPart = (MessagePart)iter.next();
            if(mimeParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
                parts.add(mPart);
            }else if(headerParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_HEADER_BINDING);
                parts.add(mPart);
            }else if(bodyParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                parts.add(mPart);
            }else{
                mPart.setBindingExtensibilityElementKind(MessagePart.PART_NOT_BOUNDED);
            }
        }

        if(isOperationDocumentLiteral(styleAndUse) && bodyParts.size() > 1){
            if(extensions)
                warn("wsdlmodeler.warning.operation.MoreThanOnePartInMessage",
                            info.portTypeOperation.getName());
            else
                fail("wsdlmodeler.invalid.operation.MoreThanOnePartInMessage", info.portTypeOperation.getName());
            return false;
        }
        return true;
    }

    private boolean boundToFault(String partName){
        for (Iterator iter = info.bindingOperation.faults(); iter.hasNext();){
            BindingFault bindingFault = (BindingFault)iter.next();
            if(partName.equals(bindingFault.getName()))
                return true;
        }
        return false;
    }

    //get MessagePart(s) referenced by parts attribute of soap:body element
    private List<MessagePart> getBodyParts(SOAPBody body, Message message){
        String bodyParts = body.getParts();
        if (bodyParts != null) {
            List<MessagePart> partsList = new ArrayList<MessagePart>();
            StringTokenizer in = new StringTokenizer(bodyParts.trim(), " ");
            while (in.hasMoreTokens()) {
                String part = in.nextToken();
                MessagePart mPart = message.getPart(part);
                if (null == mPart) {
                    throw new ModelerException(
                        "wsdlmodeler.error.partsNotFound",
                        new Object[] { part, message.getName()});
                }
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
            return partsList;
        }
        return null;
    }

    private List<MessagePart> getHeaderPartsFromMessage(Message message, boolean isInput){
        List<MessagePart> headerParts = new ArrayList<MessagePart>();
        Iterator<MessagePart> parts = message.parts();
        List<MessagePart> headers = getHeaderParts(isInput);
        while(parts.hasNext()){
            MessagePart part = parts.next();
            if(headers.contains(part)){
                headerParts.add(part);
            }
        }
        return headerParts;
    }

    private Message getHeaderMessage(MessagePart part, Extensible ext) {
        Iterator<SOAPHeader> headers =  getHeaderExtensions(ext).iterator();
        while(headers.hasNext()){
            SOAPHeader header = headers.next();
            if (!header.isLiteral())
                continue;
            com.sun.tools.internal.ws.wsdl.document.Message headerMessage = findMessage(header.getMessage(), info);
            if (headerMessage == null)
                continue;

            MessagePart headerPart = headerMessage.getPart(header.getPart());
            if(headerPart == part)
                return headerMessage;
        }
        return null;
    }

    private List<MessagePart> getHeaderPartsNotFromMessage(Message message, boolean isInput){
        List<MessagePart> headerParts = new ArrayList<MessagePart>();
        List<MessagePart> parts = message.getParts();
        Iterator<MessagePart> headers = getHeaderParts(isInput).iterator();
        while(headers.hasNext()){
            MessagePart part = headers.next();
            if(!parts.contains(part)){
                headerParts.add(part);
            }
        }
        return headerParts;
    }

    private List<MessagePart> getHeaderParts(boolean isInput) {
        Extensible ext;
        if(isInput){
            ext = info.bindingOperation.getInput();
        }else{
            ext = info.bindingOperation.getOutput();
        }

        List<MessagePart> parts = new ArrayList<MessagePart>();
        Iterator<SOAPHeader> headers =  getHeaderExtensions(ext).iterator();
        while(headers.hasNext()){
            SOAPHeader header = headers.next();
            if (!header.isLiteral()){
                fail("wsdlmodeler.invalid.header.notLiteral",
                        new Object[] {header.getPart(), info.bindingOperation.getName()});
            }

            if (header.getNamespace() != null){
                warn("wsdlmodeler.warning.r2716r2726",
                        new Object[]{"soapbind:header", info.bindingOperation.getName()});
            }
            com.sun.tools.internal.ws.wsdl.document.Message headerMessage = findMessage(header.getMessage(), info);
            if (headerMessage == null){
                fail("wsdlmodeler.invalid.header.cant.resolve.message",
                        new Object[]{header.getMessage(), info.bindingOperation.getName()});
            }

            MessagePart part = headerMessage.getPart(header.getPart());
            if (part == null){
                fail("wsdlmodeler.invalid.header.notFound",
                        new Object[]{header.getPart(), info.bindingOperation.getName()});
            }
            if (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT) {
                fail("wsdlmodeler.invalid.header.message.partMustHaveElementDescriptor",
                        new Object[]{part.getName(), info.bindingOperation.getName()});
            }
            part.setBindingExtensibilityElementKind(MessagePart.SOAP_HEADER_BINDING);
            parts.add(part);
        }
        return parts;
    }

    private boolean isOperationDocumentLiteral(StyleAndUse styleAndUse){
        return StyleAndUse.DOC_LITERAL == styleAndUse;
    }

    private boolean isOperationRpcLiteral(StyleAndUse styleAndUse){
        return StyleAndUse.RPC_LITERAL == styleAndUse;
    }

    /**
     * @param part
     * @return Returns a JAXBType object
     */
    private JAXBType getJAXBType(MessagePart part){
        JAXBType type=null;
        QName name = part.getDescriptor();
        if(part.getDescriptorKind().equals(SchemaKinds.XSD_ELEMENT)){
            type = getJAXBType(name);
        }else {
            S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
            TypeAndAnnotation typeAnno = jaxbModel.getJavaType(name);
            if(typeAnno == null){
                fail("wsdlmodeler.jaxb.javatype.notfound", new Object[]{name, part.getName()});
            }
            JavaType javaType = new  JavaSimpleType(new JAXBTypeAndAnnotation(typeAnno));
            type = new JAXBType(new QName("", part.getName()), javaType);
        }
        return type;
    }

    private List<Parameter> getDoclitParameters(Request req, Response res, List<MessagePart> parameterList){
        if(parameterList.size() == 0)
            return new ArrayList<Parameter>();
        List<Parameter> params = null;
        Message inMsg = getInputMessage();
        Message outMsg = getOutputMessage();
        boolean unwrappable = isUnwrappable();
        List<Parameter> outParams = null;
        int pIndex = 0;
        for(MessagePart part:parameterList){
            QName reqBodyName = part.getDescriptor();
            JAXBType jaxbType = getJAXBType(part);
            Block block = new Block(reqBodyName, jaxbType);
            if(unwrappable){
                //So build body and header blocks and set to request and response
                JAXBStructuredType jaxbStructType = ModelerUtils.createJAXBStructureType(jaxbType);
                block = new Block(reqBodyName, jaxbStructType);
                if(ModelerUtils.isBoundToSOAPBody(part)){
                    if(part.isIN()){
                        req.addBodyBlock(block);
                    }else if(part.isOUT()){
                        res.addBodyBlock(block);
                    }else if(part.isINOUT()){
                        req.addBodyBlock(block);
                        res.addBodyBlock(block);
                    }
                }else if(ModelerUtils.isUnbound(part)){
                    if(part.isIN())
                        req.addUnboundBlock(block);
                    else if(part.isOUT())
                        res.addUnboundBlock(block);
                    else if(part.isINOUT()){
                        req.addUnboundBlock(block);
                        res.addUnboundBlock(block);
                    }

                }
                if(part.isIN() || part.isINOUT()){
                    params = ModelerUtils.createUnwrappedParameters(jaxbStructType, block);
                    int index = 0;
                    Mode mode = (part.isINOUT())?Mode.INOUT:Mode.IN;
                    for(Parameter param: params){
                        param.setParameterIndex(index++);
                        param.setMode(mode);
                        setCustomizedParameterName(info.portTypeOperation, inMsg, part, param, unwrappable);
                    }
                }else if(part.isOUT()){
                    outParams = ModelerUtils.createUnwrappedParameters(jaxbStructType, block);
                    for(Parameter param: outParams){
                        param.setMode(Mode.OUT);
                        setCustomizedParameterName(info.portTypeOperation, outMsg, part, param, unwrappable);
                    }
                }
            }else{
                if(ModelerUtils.isBoundToSOAPBody(part)){
                    if(part.isIN()){
                        req.addBodyBlock(block);
                    }else if(part.isOUT()){
                        res.addBodyBlock(block);
                    }else if(part.isINOUT()){
                        req.addBodyBlock(block);
                        res.addBodyBlock(block);
                    }
                }else if(ModelerUtils.isBoundToSOAPHeader(part)){
                    if(part.isIN()){
                        req.addHeaderBlock(block);
                    }else if(part.isOUT()){
                        res.addHeaderBlock(block);
                    }else if(part.isINOUT()){
                        req.addHeaderBlock(block);
                        res.addHeaderBlock(block);
                    }
                }else if(ModelerUtils.isBoundToMimeContent(part)){
                    List<MIMEContent> mimeContents = null;

                    if(part.isIN()){
                        mimeContents = getMimeContents(info.bindingOperation.getInput(),
                                        getInputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType);
                        req.addAttachmentBlock(block);
                    }else if(part.isOUT()){
                        mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                                        getOutputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType);
                        res.addAttachmentBlock(block);
                    }else if(part.isINOUT()){
                        mimeContents = getMimeContents(info.bindingOperation.getInput(),
                                        getInputMessage(), part.getName());
                        jaxbType = getAttachmentType(mimeContents, part);
                        block = new Block(jaxbType.getName(), jaxbType);
                        req.addAttachmentBlock(block);
                        res.addAttachmentBlock(block);

                        mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                                        getOutputMessage(), part.getName());
                        JAXBType outJaxbType = getAttachmentType(mimeContents, part);

                        String inType = jaxbType.getJavaType().getType().getName();
                        String outType = outJaxbType.getJavaType().getType().getName();

                        TypeAndAnnotation inTa = jaxbType.getJavaType().getType().getTypeAnn();
                        TypeAndAnnotation outTa = outJaxbType.getJavaType().getType().getTypeAnn();
                        if((((inTa != null) && (outTa != null) && inTa.equals(outTa))) && !inType.equals(outType)){
                            String javaType = "javax.activation.DataHandler";

                            S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
                            JCodeModel cm = jaxbModel.generateCode(null,
                                        new ConsoleErrorReporter(getEnvironment(), false));
                            JType jt= null;
                            jt = cm.ref(javaType);
                            JAXBTypeAndAnnotation jaxbTa = jaxbType.getJavaType().getType();
                            jaxbTa.setType(jt);
                        }
                    }
                }else if(ModelerUtils.isUnbound(part)){
                    if(part.isIN()){
                        req.addUnboundBlock(block);
                    }else if(part.isOUT()){
                        res.addUnboundBlock(block);
                    }else if(part.isINOUT()){
                        req.addUnboundBlock(block);
                        res.addUnboundBlock(block);
                    }
                }
                if(params == null)
                    params = new ArrayList<Parameter>();
                Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, block);
                param.setMode(part.getMode());
                if(part.isReturn()){
                    param.setParameterIndex(-1);
                }else{
                    param.setParameterIndex(pIndex++);
                }

                if(part.isIN())
                    setCustomizedParameterName(info.portTypeOperation, inMsg, part, param, false);
                else if(outMsg != null)
                    setCustomizedParameterName(info.portTypeOperation, outMsg, part, param, false);

                params.add(param);
            }
        }
        if(unwrappable && (outParams != null)){
            int index = params.size();
            for(Parameter param:outParams){
                if(param.getName().equals("return")){
                    param.setParameterIndex(-1);
                }else{
                    Parameter inParam = ModelerUtils.getParameter(param.getName(), params);
                    if((inParam != null) && inParam.isIN()){
                        QName inElementName = ((JAXBType)inParam.getType()).getName();
                        QName outElementName = ((JAXBType)param.getType()).getName();
                        String inJavaType = inParam.getTypeName();
                        String outJavaType = param.getTypeName();
                        TypeAndAnnotation inTa = inParam.getType().getJavaType().getType().getTypeAnn();
                        TypeAndAnnotation outTa = param.getType().getJavaType().getType().getTypeAnn();
                        if(inElementName.getLocalPart().equals(outElementName.getLocalPart()) &&
                                inJavaType.equals(outJavaType) &&
                                ((inTa == null || outTa == null)||
                                ((inTa != null) && (outTa != null) && inTa.equals(outTa)))) {
                            inParam.setMode(Mode.INOUT);
                            continue;
                        }
                    }else if(outParams.size() == 1){
                        param.setParameterIndex(-1);
                    }else{
                        param.setParameterIndex(index++);
                    }
                }
                params.add(param);
            }
        }
        return params;
    }

    private List<Parameter> getRpcLitParameters(Request req, Response res, Block reqBlock, Block resBlock, List<MessagePart> paramList){
        List<Parameter> params = new ArrayList<Parameter>();
        Message inMsg = getInputMessage();
        Message outMsg = getOutputMessage();
        S2JJAXBModel jaxbModel = ((RpcLitStructure)reqBlock.getType()).getJaxbModel().getS2JJAXBModel();
        List<Parameter> inParams = ModelerUtils.createRpcLitParameters(inMsg, reqBlock, jaxbModel);
        List<Parameter> outParams = null;
        if(outMsg != null)
            outParams = ModelerUtils.createRpcLitParameters(outMsg, resBlock, jaxbModel);

        //create parameters for header and mime parts
        int index = 0;
        for(MessagePart part: paramList){
             Parameter param = null;
            if(ModelerUtils.isBoundToSOAPBody(part)){
                if(part.isIN()){
                    param = ModelerUtils.getParameter(part.getName(), inParams);
                }else if(outParams != null){
                    param = ModelerUtils.getParameter(part.getName(), outParams);
                }
            }else if(ModelerUtils.isBoundToSOAPHeader(part)){
                QName headerName = part.getDescriptor();
                JAXBType jaxbType = getJAXBType(headerName);
                Block headerBlock = new Block(headerName, jaxbType);
                param = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
                if(part.isIN()){
                    req.addHeaderBlock(headerBlock);
                }else if(part.isOUT()){
                    res.addHeaderBlock(headerBlock);
                }else if(part.isINOUT()){
                    req.addHeaderBlock(headerBlock);
                    res.addHeaderBlock(headerBlock);
                }
            }else if(ModelerUtils.isBoundToMimeContent(part)){
                List<MIMEContent> mimeContents = null;
                if(part.isIN() || part.isINOUT())
                    mimeContents = getMimeContents(info.bindingOperation.getInput(),
                            getInputMessage(), part.getName());
                else
                    mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                            getOutputMessage(), part.getName());

                JAXBType type = getAttachmentType(mimeContents, part);
                //create Parameters in request or response
                //Block mimeBlock = new Block(new QName(part.getName()), type);
                Block mimeBlock = new Block(type.getName(), type);
                param = ModelerUtils.createParameter(part.getName(), type, mimeBlock);
                if(part.isIN()){
                    req.addAttachmentBlock(mimeBlock);
                }else if(part.isOUT()){
                    res.addAttachmentBlock(mimeBlock);
                }else if(part.isINOUT()){
                    mimeContents = getMimeContents(info.bindingOperation.getOutput(),
                                    getOutputMessage(), part.getName());
                    JAXBType outJaxbType = getAttachmentType(mimeContents, part);

                    String inType = type.getJavaType().getType().getName();
                    String outType = outJaxbType.getJavaType().getType().getName();
                    if(!inType.equals(outType)){
                        String javaType = "javax.activation.DataHandler";
                        JCodeModel cm = jaxbModel.generateCode(null,
                                    new ConsoleErrorReporter(getEnvironment(), false));
                        JType jt= null;
                        jt = cm.ref(javaType);
                        JAXBTypeAndAnnotation jaxbTa = type.getJavaType().getType();
                        jaxbTa.setType(jt);
                    }
                    req.addAttachmentBlock(mimeBlock);
                    res.addAttachmentBlock(mimeBlock);
                }
            }else if(ModelerUtils.isUnbound(part)){
                QName name = part.getDescriptor();
                JAXBType type = getJAXBType(part);
                Block unboundBlock = new Block(name, type);
                if(part.isIN()){
                    req.addUnboundBlock(unboundBlock);
                }else if(part.isOUT()){
                    res.addUnboundBlock(unboundBlock);
                }else if(part.isINOUT()){
                    req.addUnboundBlock(unboundBlock);
                    res.addUnboundBlock(unboundBlock);
                }
                param = ModelerUtils.createParameter(part.getName(), type, unboundBlock);
            }
            if(param != null){
                if(part.isReturn()){
                    param.setParameterIndex(-1);
                }else{
                    param.setParameterIndex(index++);
                }
                param.setMode(part.getMode());
                params.add(param);
            }
        }
        for(Parameter param : params){
            if(param.isIN())
                setCustomizedParameterName(info.portTypeOperation, inMsg, inMsg.getPart(param.getName()), param, false);
            else if(outMsg != null)
                setCustomizedParameterName(info.portTypeOperation, outMsg, outMsg.getPart(param.getName()), param, false);
        }
        return params;
    }

    private List<Parameter> getRequestParameters(Request request, List<String> parameterList) {
        Message inputMessage = getInputMessage();
        //there is no input message, return zero parameters
        if(inputMessage != null && !inputMessage.parts().hasNext())
            return new ArrayList<Parameter>();

        List<Parameter> inParameters = null;
        QName reqBodyName = null;
        Block reqBlock = null;
        JAXBType jaxbReqType = null;
        boolean unwrappable = isUnwrappable();
        boolean doneSOAPBody = false;
        //setup request parameters
        for(String inParamName: parameterList){
            MessagePart part = inputMessage.getPart(inParamName);
            if(part == null)
                continue;
            reqBodyName = part.getDescriptor();
            jaxbReqType = getJAXBType(part);
            if(unwrappable){
                //So build body and header blocks and set to request and response
                JAXBStructuredType jaxbRequestType = ModelerUtils.createJAXBStructureType(jaxbReqType);
                reqBlock = new Block(reqBodyName, jaxbRequestType);
                if(ModelerUtils.isBoundToSOAPBody(part)){
                    request.addBodyBlock(reqBlock);
                }else if(ModelerUtils.isUnbound(part)){
                    request.addUnboundBlock(reqBlock);
                }
                inParameters = ModelerUtils.createUnwrappedParameters(jaxbRequestType, reqBlock);
                for(Parameter param: inParameters){
                    setCustomizedParameterName(info.portTypeOperation, inputMessage, part, param, unwrappable);
                }
            }else{
                reqBlock = new Block(reqBodyName, jaxbReqType);
                if(ModelerUtils.isBoundToSOAPBody(part) && !doneSOAPBody){
                    doneSOAPBody = true;
                    request.addBodyBlock(reqBlock);
                }else if(ModelerUtils.isBoundToSOAPHeader(part)){
                    request.addHeaderBlock(reqBlock);
                }else if(ModelerUtils.isBoundToMimeContent(part)){
                    List<MIMEContent> mimeContents = getMimeContents(info.bindingOperation.getInput(),
                        getInputMessage(), part.getName());
                    jaxbReqType = getAttachmentType(mimeContents, part);
                    //reqBlock = new Block(new QName(part.getName()), jaxbReqType);
                    reqBlock = new Block(jaxbReqType.getName(), jaxbReqType);
                    request.addAttachmentBlock(reqBlock);
                }else if(ModelerUtils.isUnbound(part)){
                    request.addUnboundBlock(reqBlock);
                }
                if(inParameters == null)
                    inParameters = new ArrayList<Parameter>();
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
    private void setCustomizedParameterName(Extensible extension, Message msg, MessagePart part, Parameter param, boolean wrapperStyle) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(extension, JAXWSBinding.class);
        if(jaxwsBinding == null)
            return;
        String paramName = part.getName();
        QName elementName = part.getDescriptor();
        if(wrapperStyle)
            elementName = param.getType().getName();
        String customName = jaxwsBinding.getParameterName(msg.getName(), paramName, elementName, wrapperStyle);
        if(customName != null && !customName.equals("")){
            param.setCustomName(customName);
        }
    }

    /**
     * @param name
     * @return
     */
    private JAXBType getJAXBType(QName name) {
        return jaxbModelBuilder.getJAXBType(name);
    }

    protected boolean isConflictingPortClassName(String name) {
        return false;
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#getJAXBSchemaAnalyzerInstnace(WSDLModelInfo, Properties, org.w3c.dom.Element)
     */
    protected JAXBModelBuilder getJAXBSchemaAnalyzerInstnace(WSDLModelInfo info,
                                                             Properties options,
                                                             ClassNameCollector classNameCollector, List elements) {
        return new JAXBModelBuilder(info, options, classNameCollector, elements);
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#isUnwrappable()
     */
    protected boolean isUnwrappable() {
        if(!getWrapperStyleCustomization())
            return false;

        com.sun.tools.internal.ws.wsdl.document.Message inputMessage = getInputMessage();
        com.sun.tools.internal.ws.wsdl.document.Message outputMessage = getOutputMessage();

        // Wrapper style if the operation's input and output messages each contain
        // only a single part
        if ((inputMessage != null && inputMessage.numParts() != 1)
            || (outputMessage != null && outputMessage.numParts() != 1)) {
            return false;
        }

        MessagePart inputPart = inputMessage != null
                ? (MessagePart)inputMessage.parts().next() : null;
        MessagePart outputPart = outputMessage != null
                ? (MessagePart)outputMessage.parts().next() : null;
        String operationName = info.portTypeOperation.getName();

        // Wrapper style if the input message part refers to a global element declaration whose localname
        // is equal to the operation name
        // Wrapper style if the output message part refers to a global element declaration
        if ((inputPart != null && !inputPart.getDescriptor().getLocalPart().equals(operationName)) ||
            (outputPart != null && outputPart.getDescriptorKind() != SchemaKinds.XSD_ELEMENT))
            return false;

        //check to see if either input or output message part not bound to soapbing:body
        //in that case the operation is not wrapper style
        if(((inputPart != null) && (inputPart.getBindingExtensibilityElementKind() != MessagePart.SOAP_BODY_BINDING)) ||
                ((outputPart != null) &&(outputPart.getBindingExtensibilityElementKind() != MessagePart.SOAP_BODY_BINDING)))
            return false;

        // Wrapper style if the elements referred to by the input and output message parts
        // (henceforth referred to as wrapper elements) are both complex types defined
        // using the xsd:sequence compositor
        // Wrapper style if the wrapper elements only contain child elements, they must not
        // contain other structures such as xsd:choice, substitution groups1 or attributes
        //These checkins are done by jaxb, we just check if jaxb has wrapper children. If there
        // are then its wrapper style
        //if(inputPart != null && outputPart != null){
        if(inputPart != null){
            boolean inputWrappable = false;
            JAXBType inputType = getJAXBType(inputPart.getDescriptor());
            if(inputType != null){
                inputWrappable = inputType.isUnwrappable();
            }
            //if there are no output part (oneway), the operation can still be wrapper style
            if(outputPart == null){
               return inputWrappable;
            }
            JAXBType outputType = getJAXBType(outputPart.getDescriptor());
            if((inputType != null) && (outputType != null))
                return inputType.isUnwrappable() && outputType.isUnwrappable();
        }

        return false;
    }

    /**
     * @return
     */
    private boolean getWrapperStyleCustomization() {
        //first we look into wsdl:portType/wsdl:operation
        com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation = info.portTypeOperation;
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(portTypeOperation, JAXWSBinding.class);
        if(jaxwsBinding != null){
             Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
             if(isWrappable != null)
                 return isWrappable;
        }

        //then into wsdl:portType
        PortType portType = info.port.resolveBinding(document).resolvePortType(document);
        jaxwsBinding = (JAXWSBinding)getExtensionOfType(portType, JAXWSBinding.class);
        if(jaxwsBinding != null){
             Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
             if(isWrappable != null)
                 return isWrappable;
        }

        //then wsdl:definitions
        jaxwsBinding = (JAXWSBinding)getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        if(jaxwsBinding != null){
             Boolean isWrappable = jaxwsBinding.isEnableWrapperStyle();
             if(isWrappable != null)
                 return isWrappable;
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
        if((soapOperation != null) && (soapOperation.isDocument() || info.soapBinding.isDocument())) {
            Iterator iter = getInputMessage().parts();
            while(iter.hasNext()){
                MessagePart part = (MessagePart)iter.next();
                if(outputPart.getName().equals(part.getName()) && outputPart.getDescriptor().equals(part.getDescriptor()))
                    return true;
            }
        }else if(soapOperation != null && soapOperation.isRPC()|| info.soapBinding.isRPC()){
            com.sun.tools.internal.ws.wsdl.document.Message inputMessage = getInputMessage();
            if(inputParameterNames.contains(outputPart.getName())) {
                if (inputMessage.getPart(outputPart.getName()).getDescriptor().equals(outputPart.getDescriptor())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Parameter> createRpcLitRequestParameters(Request request, List<String> parameterList, Block block) {
        Message message = getInputMessage();
        S2JJAXBModel jaxbModel = ((RpcLitStructure)block.getType()).getJaxbModel().getS2JJAXBModel();
        List<Parameter> parameters = ModelerUtils.createRpcLitParameters(message, block, jaxbModel);

        //create parameters for header and mime parts
        for(String paramName: parameterList){
            MessagePart part = message.getPart(paramName);
            if(part == null)
                continue;
            if(ModelerUtils.isBoundToSOAPHeader(part)){
                if(parameters == null)
                    parameters = new ArrayList<Parameter>();
                QName headerName = part.getDescriptor();
                JAXBType jaxbType = getJAXBType(headerName);
                Block headerBlock = new Block(headerName, jaxbType);
                request.addHeaderBlock(headerBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), jaxbType, headerBlock);
                if(param != null){
                    parameters.add(param);
                }
            }else if(ModelerUtils.isBoundToMimeContent(part)){
                if(parameters == null)
                    parameters = new ArrayList<Parameter>();
                List<MIMEContent> mimeContents = getMimeContents(info.bindingOperation.getInput(),
                        getInputMessage(), paramName);

                JAXBType type = getAttachmentType(mimeContents, part);
                //create Parameters in request or response
                //Block mimeBlock = new Block(new QName(part.getName()), type);
                Block mimeBlock = new Block(type.getName(), type);
                request.addAttachmentBlock(mimeBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), type, mimeBlock);
                if(param != null){
                    parameters.add(param);
                }
            }else if(ModelerUtils.isUnbound(part)){
                if(parameters == null)
                    parameters = new ArrayList<Parameter>();
                QName name = part.getDescriptor();
                JAXBType type = getJAXBType(part);
                Block unboundBlock = new Block(name, type);
                request.addUnboundBlock(unboundBlock);
                Parameter param = ModelerUtils.createParameter(part.getName(), type, unboundBlock);
                if(param != null){
                    parameters.add(param);
                }
            }
        }
        for(Parameter param : parameters){
            setCustomizedParameterName(info.portTypeOperation, message, message.getPart(param.getName()), param, false);
        }
        return parameters;
    }

    private String getJavaTypeForMimeType(String mimeType){
        if(mimeType.equals("image/jpeg") || mimeType.equals("image/gif")){
            return "java.awt.Image";
        }else if(mimeType.equals("text/xml") || mimeType.equals("application/xml")){
            return "javax.xml.transform.Source";
        }
        return "javax.activation.DataHandler";
    }

    /**
     * @param mimeContents
     * @return
     */
    private JAXBType getAttachmentType(List<MIMEContent> mimeContents, MessagePart part) {
        if(!enableMimeContent()){
            return getJAXBType(part);
        }
        String javaType = null;
        List<String> mimeTypes = getAlternateMimeTypes(mimeContents);
        if(mimeTypes.size() > 1) {
            javaType = "javax.activation.DataHandler";
        }else{
           javaType = getJavaTypeForMimeType(mimeTypes.get(0));
        }

        S2JJAXBModel jaxbModel = getJAXBModelBuilder().getJAXBModel().getS2JJAXBModel();
        JCodeModel cm = jaxbModel.generateCode(null,
                    new ConsoleErrorReporter(getEnvironment(), false));
        JType jt= null;
        jt = cm.ref(javaType);
        QName desc = part.getDescriptor();
        TypeAndAnnotation typeAnno = null;

        if (part.getDescriptorKind() == SchemaKinds.XSD_TYPE) {
            typeAnno = jaxbModel.getJavaType(desc);
            desc = new QName("", part.getName());
        } else if (part.getDescriptorKind()== SchemaKinds.XSD_ELEMENT) {
            typeAnno = getJAXBModelBuilder().getElementTypeAndAnn(desc);
            for(Iterator mimeTypeIter = mimeTypes.iterator(); mimeTypeIter.hasNext();) {
                String mimeType = (String)mimeTypeIter.next();
                if((!mimeType.equals("text/xml") &&
                        !mimeType.equals("application/xml"))){
                    //According to AP 1.0,
                    //RZZZZ: In a DESCRIPTION, if a wsdl:part element refers to a
                    //global element declaration (via the element attribute of the wsdl:part
                    //element) then the value of the type attribute of a mime:content element
                    //that binds that part MUST be a content type suitable for carrying an
                    //XML serialization.
                    //should we throw warning?
                    //type = MimeHelper.javaType.DATA_HANDLER_JAVATYPE;
                    warn("mimemodeler.elementPart.invalidElementMimeType",
                            new Object[] {
                            part.getName(), mimeType});
                }
            }
        }
        if(typeAnno == null){
            fail("wsdlmodeler.jaxb.javatype.notfound", new Object[]{desc, part.getName()});
        }
        return new JAXBType(desc, new JavaSimpleType(new JAXBTypeAndAnnotation(typeAnno, jt)),
                null, getJAXBModelBuilder().getJAXBModel());
    }

    protected void buildJAXBModel(WSDLDocument wsdlDocument, WSDLModelInfo modelInfo, ClassNameCollector classNameCollector) {
        JAXBModelBuilder jaxbModelBuilder = new JAXBModelBuilder(getWSDLModelInfo(), _options, classNameCollector, parser.getSchemaElements());
        //set the java package where wsdl artifacts will be generated
        //if user provided package name  using -p switch (or package property on wsimport ant task)
        //ignore the package customization in the wsdl and schema bidnings
        if(getWSDLModelInfo().getDefaultJavaPackage() != null){
            getWSDLModelInfo().setJavaPackageName(getWSDLModelInfo().getDefaultJavaPackage());
            jaxbModelBuilder.getJAXBSchemaCompiler().forcePackageName(getWSDLModelInfo().getJavaPackageName());
        }else{
            String jaxwsPackage = getJavaPackage();
            getWSDLModelInfo().setJavaPackageName(jaxwsPackage);
        }

        //create pseudo schema for async operations(if any) response bean
        List<InputSource> schemas = PseudoSchemaBuilder.build(this, _modelInfo);
        for(InputSource schema : schemas){
            jaxbModelBuilder.getJAXBSchemaCompiler().parseSchema(schema);
        }
        jaxbModelBuilder.bind();
        this.jaxbModelBuilder = jaxbModelBuilder;
    }

    protected String getJavaPackage(){
        String jaxwsPackage = null;
        JAXWSBinding jaxwsCustomization = (JAXWSBinding)getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        if(jaxwsCustomization != null && jaxwsCustomization.getJaxwsPackage() != null){
            jaxwsPackage = jaxwsCustomization.getJaxwsPackage().getName();
        }
        if(jaxwsPackage != null){
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
        if(isProvider){
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

            for(JavaParameter jParam : operation.getJavaMethod().getParametersList()){
                Parameter param = jParam.getParameter();
                if(param.getCustomName() != null)
                    jParam.setName(param.getCustomName());
            }
        }

        port.setJavaInterface(intf);
    }

    protected String getServiceInterfaceName(QName serviceQName, com.sun.tools.internal.ws.wsdl.document.Service wsdlService) {
        String serviceName = wsdlService.getName();
        JAXWSBinding jaxwsCust = (JAXWSBinding)getExtensionOfType(wsdlService, JAXWSBinding.class);
        if(jaxwsCust != null && jaxwsCust.getClassName() != null){
            CustomName name = jaxwsCust.getClassName();
            if(name != null && !name.equals(""))
                serviceName = name.getName();
        }
        String serviceInterface = "";
        String javaPackageName = null;
        if (_modelInfo.getJavaPackageName() != null
            && !_modelInfo.getJavaPackageName().equals("")) {
            javaPackageName = _modelInfo.getJavaPackageName();
        }
        if (javaPackageName != null) {
            serviceInterface = javaPackageName + ".";
        }
        serviceInterface
            += getEnvironment().getNames().validJavaClassName(serviceName);
        return serviceInterface;
    }

    protected String getJavaNameOfSEI(Port port) {
        QName portTypeName =
            (QName)port.getProperty(
                ModelProperties.PROPERTY_WSDL_PORT_TYPE_NAME);
        PortType pt = (PortType)document.find(Kinds.PORT_TYPE, portTypeName);
        JAXWSBinding jaxwsCust = (JAXWSBinding)getExtensionOfType(pt, JAXWSBinding.class);
        if(jaxwsCust != null && jaxwsCust.getClassName() != null){
            CustomName name = jaxwsCust.getClassName();
            if(name != null && !name.equals("")){
                return makePackageQualified(
                        name.getName(),
                        portTypeName,
                        false);
            }
        }

        String interfaceName = null;
        if (portTypeName != null) {
            // got portType information from WSDL, use it to name the interface
            interfaceName =
                makePackageQualified(JAXBRIContext.mangleNameToClassName(portTypeName.getLocalPart()),
                                        portTypeName,
                                        false);
        } else {
            // somehow we only got the port name, so we use that
            interfaceName =
                makePackageQualified(
                    JAXBRIContext.mangleNameToClassName(port.getName().getLocalPart()),
                    port.getName(),
                    false);
        }
        return interfaceName;
    }

    private void createJavaMethodForAsyncOperation(Port port, Operation operation,
            JavaInterface intf){
        String candidateName = getJavaNameForOperation(operation);
        JavaMethod method = new JavaMethod(candidateName);
        method.setThrowsRemoteException(false);
        Request request = operation.getRequest();
        Iterator requestBodyBlocks = request.getBodyBlocks();
        Block requestBlock =
            (requestBodyBlocks.hasNext()
                ? (Block)request.getBodyBlocks().next()
                : null);

        Response response = operation.getResponse();
        Iterator responseBodyBlocks = null;
        Block responseBlock = null;
        if (response != null) {
            responseBodyBlocks = response.getBodyBlocks();
            responseBlock =
                responseBodyBlocks.hasNext()
                    ? (Block)response.getBodyBlocks().next()
                    : null;
        }

        // build a signature of the form "opName%arg1type%arg2type%...%argntype so that we
        // detect overloading conflicts in the generated java interface/classes
        String signature = candidateName;
        for (Iterator iter = request.getParameters(); iter.hasNext();) {
            Parameter parameter = (Parameter)iter.next();

            if (parameter.getJavaParameter() != null) {
                throw new ModelerException(
                    "wsdlmodeler.invalidOperation",
                    operation.getName().getLocalPart());
            }

            JavaType parameterType = parameter.getType().getJavaType();
            JavaParameter javaParameter =
                new JavaParameter(
                    JAXBRIContext.mangleNameToVariableName(parameter.getName()),
                    parameterType,
                    parameter,
                    parameter.getLinkedParameter() != null);
            if (javaParameter.isHolder()) {
                javaParameter.setHolderName(javax.xml.ws.Holder.class.getName());
            }
            method.addParameter(javaParameter);
            parameter.setJavaParameter(javaParameter);

            signature += "%" + parameterType.getName();
        }

        if (response != null) {
            String resultParameterName =
                (String)operation.getProperty(WSDL_RESULT_PARAMETER);
            Parameter resultParameter =
                response.getParameterByName(resultParameterName);
            JavaType returnType = resultParameter.getType().getJavaType();
            method.setReturnType(returnType);

        }
        operation.setJavaMethod(method);
        intf.addMethod(method);
    }

    /* (non-Javadoc)
     * @see WSDLModelerBase#createJavaMethodForOperation(Port, Operation, JavaInterface, Set, Set)
     */
    protected void createJavaMethodForOperation(Port port, Operation operation, JavaInterface intf) {
        if((operation instanceof AsyncOperation)){
            createJavaMethodForAsyncOperation(port, operation, intf);
            return;
        }
        String candidateName = getJavaNameForOperation(operation);
        JavaMethod method = new JavaMethod(candidateName);
        Request request = operation.getRequest();
        Parameter returnParam = (Parameter)operation.getProperty(WSDL_RESULT_PARAMETER);
        if(returnParam != null){
            JavaType parameterType = returnParam.getType().getJavaType();
            method.setReturnType(parameterType);
        }else{
            JavaType ret = new JavaSimpleTypeCreator().VOID_JAVATYPE;
            method.setReturnType(ret);
        }
        List<Parameter> parameterOrder = (List<Parameter>)operation.getProperty(WSDL_PARAMETER_ORDER);
        for(Parameter param:parameterOrder){
            JavaType parameterType = param.getType().getJavaType();
            String name = (param.getCustomName() != null)?param.getCustomName():param.getName();
            JavaParameter javaParameter =
                new JavaParameter(
                    JAXBRIContext.mangleNameToVariableName(name),
                    parameterType,
                    param,
                    param.isINOUT()||param.isOUT());
            if (javaParameter.isHolder()) {
                javaParameter.setHolderName(javax.xml.ws.Holder.class.getName());
            }
            method.addParameter(javaParameter);
            param.setJavaParameter(javaParameter);
        }
        operation.setJavaMethod(method);
        intf.addMethod(method);

        String opName = JAXBRIContext.mangleNameToVariableName(operation.getName().getLocalPart());
        for (Iterator iter = operation.getFaults();
            iter != null && iter.hasNext();
            ) {
            Fault fault = (Fault)iter.next();
            createJavaExceptionFromLiteralType(fault, port, opName);
        }
        JavaException javaException;
        Fault fault;
        for (Iterator iter = operation.getFaults(); iter.hasNext();) {
            fault = (Fault)iter.next();
            javaException = fault.getJavaException();
            method.addException(javaException.getName());
        }

    }

    protected boolean createJavaExceptionFromLiteralType(Fault fault, com.sun.tools.internal.ws.processor.model.Port port, String operationName) {
        ProcessorEnvironment _env = getProcessorEnvironment();

        JAXBType faultType = (JAXBType)fault.getBlock().getType();

        String exceptionName =
            makePackageQualified(
                _env.getNames().validJavaClassName(fault.getName()),
                port.getName());

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
        javaMember.setReadMethod(_env.getNames().getJavaMemberReadMethod(javaMember));
        javaMember.setInherited(false);
        jaxbMember.setJavaStructureMember(javaMember);
        jaxbStruct.add(jaxbMember);

        if (isConflictingExceptionClassName(exceptionName)) {
            exceptionName += "_Exception";
        }

        JavaException existingJavaException = (JavaException)_javaExceptions.get(exceptionName);
        if (existingJavaException != null) {
            if (existingJavaException.getName().equals(exceptionName)) {
                if (((JAXBType)existingJavaException.getOwner()).getName().equals(jaxbStruct.getName())
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

    protected boolean isRequestResponse(){
        return info.portTypeOperation.getStyle() == OperationStyle.REQUEST_RESPONSE;
    }

    protected java.util.List<String> getAsynParameterOrder(){
        //for async operation ignore the parameterOrder
        java.util.List<String> parameterList = new ArrayList<String>();
        Message inputMessage = getInputMessage();
        List<MessagePart> inputParts = inputMessage.getParts();
        for(MessagePart part: inputParts){
            parameterList.add(part.getName());
        }
        return parameterList;
    }


    protected List<MessagePart> getParameterOrder(){
        List<MessagePart> params = new ArrayList<MessagePart>();
        String parameterOrder = info.portTypeOperation.getParameterOrder();
        java.util.List<String> parameterList = new ArrayList<String>();
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
        for(MessagePart part:inputParts){
            part.setMode(Mode.IN);
            part.setReturn(false);
        }
        if(isRequestResponse()){
            outputParts = outputMessage.getParts();
            for(MessagePart part:outputParts){
                part.setMode(Mode.OUT);
                part.setReturn(false);
            }
        }

        if(parameterOrderPresent){
            boolean validParameterOrder = true;
            Iterator<String> paramOrders = parameterList.iterator();
            // If any part in the parameterOrder is not present in the request or
            // response message, we completely ignore the parameterOrder hint
            while(paramOrders.hasNext()){
                String param = paramOrders.next();
                boolean partFound = false;
                for(MessagePart part : inputParts){
                    if(param.equals(part.getName())){
                        partFound = true;
                        break;
                    }
                }
                // if not found, check in output parts
                if(!partFound){
                    for(MessagePart part : outputParts){
                        if(param.equals(part.getName())){
                            partFound = true;
                            break;
                        }
                    }
                }
                if(!partFound){
                    warn("wsdlmodeler.invalid.parameterorder.parameter",
                            new Object[] {param, info.operation.getName().getLocalPart()});
                    validParameterOrder = false;
                }
            }

            List<MessagePart> inputUnlistedParts = new ArrayList<MessagePart>();
            List<MessagePart> outputUnlistedParts = new ArrayList<MessagePart>();

            //gather input Parts
            if(validParameterOrder){
                for(String param:parameterList){
                    MessagePart part = inputMessage.getPart(param);
                    if(part != null){
                        params.add(part);
                        continue;
                    }
                    if(isRequestResponse()){
                        MessagePart outPart = outputMessage.getPart(param);
                        if(outPart != null){
                            params.add(outPart);
                            continue;
                        }
                    }
                }

                for(MessagePart part: inputParts){
                    if(!parameterList.contains(part.getName())) {
                        inputUnlistedParts.add(part);
                    }
                }

                if(isRequestResponse()){
                    // at most one output part should be unlisted
                    for(MessagePart part: outputParts){
                        if(!parameterList.contains(part.getName())) {
                            MessagePart inPart = inputMessage.getPart(part.getName());
                            //dont add inout as unlisted part
                            if((inPart != null) && inPart.getDescriptor().equals(part.getDescriptor())){
                                inPart.setMode(Mode.INOUT);
                            }else{
                                outputUnlistedParts.add(part);
                            }
                        }else{
                            //param list may contain it, check if its INOUT
                            MessagePart inPart = inputMessage.getPart(part.getName());
                            //dont add inout as unlisted part
                            if((inPart != null) && inPart.getDescriptor().equals(part.getDescriptor())){
                                inPart.setMode(Mode.INOUT);
                            }else if(!params.contains(part)){
                                params.add(part);
                            }
                        }
                    }
                    if(outputUnlistedParts.size() == 1){
                        MessagePart resultPart = outputUnlistedParts.get(0);
                        resultPart.setReturn(true);
                        params.add(resultPart);
                        outputUnlistedParts.clear();
                    }
                }

                //add the input and output unlisted parts
                for(MessagePart part : inputUnlistedParts){
                    params.add(part);
                }

                for(MessagePart part : outputUnlistedParts){
                    params.add(part);
                }
                return params;

            }
            //parameterOrder attribute is not valid, we ignore it
            warn("wsdlmodeler.invalid.parameterOrder.invalidParameterOrder",
                    new Object[] {info.operation.getName().getLocalPart()});
            parameterOrderPresent = false;
            parameterList.clear();
        }

        List<MessagePart> outParts = new ArrayList<MessagePart>();

        //construct input parameter list with the same order as in input message
        for(MessagePart part: inputParts){
            params.add(part);
        }

        if(isRequestResponse()){
            for(MessagePart part:outputParts){
                MessagePart inPart = inputMessage.getPart(part.getName());
                if(inPart != null && part.getDescriptorKind() == inPart.getDescriptorKind() &&
                        part.getDescriptor().equals(inPart.getDescriptor())){
                    inPart.setMode(Mode.INOUT);
                    continue;
                }
                outParts.add(part);
            }

            //append the out parts to the parameterList
            for(MessagePart part : outParts){
                if(outParts.size() == 1)
                    part.setReturn(true);
                params.add(part);
            }
        }
        return params;
    }

    /**
     *
     * @param port
     * @param suffix
     * @return the Java ClassName for a port
     */
    protected String getClassName(Port port, String suffix) {
        String name = "";
        String javaPackageName = "";
        if (_modelInfo.getJavaPackageName() != null
            && !_modelInfo.getJavaPackageName().equals("")) {
            javaPackageName = _modelInfo.getJavaPackageName();
        }
        String prefix = getEnvironment().getNames().validJavaClassName(port.getName().getLocalPart());
        name = javaPackageName+"."+prefix+suffix;
        return name;
    }

    protected boolean isConflictingServiceClassName(String name) {
       if(conflictsWithSEIClass(name) || conflictsWithJAXBClass(name) ||conflictsWithExceptionClass(name)){
            return true;
        }
        return false;
    }

    private boolean conflictsWithSEIClass(String name){
        Set<String> seiNames = classNameCollector.getSeiClassNames();
        if(seiNames != null && seiNames.contains(name))
            return true;
        return false;
    }

    private boolean conflictsWithJAXBClass(String name){
        Set<String> jaxbNames = classNameCollector.getJaxbGeneratedClassNames();
        if(jaxbNames != null && jaxbNames.contains(name))
            return true;
        return false;
    }

    private boolean conflictsWithExceptionClass(String name){
        Set<String> exceptionNames = classNameCollector.getExceptionClassNames();
        if(exceptionNames != null && exceptionNames.contains(name))
            return true;
        return false;
    }

    protected boolean isConflictingExceptionClassName(String name) {
        if(conflictsWithSEIClass(name) || conflictsWithJAXBClass(name)){
            return true;
        }
        return false;
    }

    protected JAXBModelBuilder getJAXBModelBuilder() {
        return jaxbModelBuilder;
    }

    protected boolean validateWSDLBindingStyle(Binding binding) {
        boolean mixedStyle = false;
        SOAPBinding soapBinding =
            (SOAPBinding)getExtensionOfType(binding, SOAPBinding.class);

        //dont process the binding
        if(soapBinding == null)
            soapBinding =
                (SOAPBinding)getExtensionOfType(binding, SOAP12Binding.class);
        if(soapBinding == null)
            return false;

        //if soapbind:binding has no style attribute, the default is DOCUMENT
        if(soapBinding.getStyle() == null)
            soapBinding.setStyle(SOAPStyle.DOCUMENT);

        SOAPStyle opStyle = soapBinding.getStyle();
        for (Iterator iter = binding.operations(); iter.hasNext();) {
            BindingOperation bindingOperation =
                (BindingOperation)iter.next();
            SOAPOperation soapOperation =
                (SOAPOperation) getExtensionOfType(bindingOperation,
                    SOAPOperation.class);
            if(soapOperation != null){
                SOAPStyle currOpStyle = (soapOperation.getStyle() != null)?soapOperation.getStyle():soapBinding.getStyle();
                //dont check for the first operation
                if(!currOpStyle.equals(opStyle))
                    return false;
            }
        }
        return true;
    }

    /**
     * @param port
     */
    private void applyWrapperStyleCustomization(Port port, PortType portType) {
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(portType, JAXWSBinding.class);
        Boolean wrapperStyle = (jaxwsBinding != null)?jaxwsBinding.isEnableWrapperStyle():null;
        if(wrapperStyle != null){
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
        if(getEnvironment().getNames().isJavaReservedWord(name)){
            name = "_"+name;
        }
        return name;
    }

    protected void fail(String key, String arg){
        throw new ModelerException(key, arg);
    }
    protected void fail(String key, Object[] args){
        throw new ModelerException(key, args);
    }
}
