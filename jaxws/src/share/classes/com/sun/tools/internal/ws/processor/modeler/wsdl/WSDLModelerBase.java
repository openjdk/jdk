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


package com.sun.tools.internal.ws.processor.modeler.wsdl;

import com.sun.tools.internal.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.internal.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.internal.ws.processor.generator.Names;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.model.java.JavaException;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.modeler.Modeler;
import com.sun.tools.internal.ws.resources.ModelerMessages;
import com.sun.tools.internal.ws.wscompile.AbortException;
import com.sun.tools.internal.ws.wscompile.ErrorReceiver;
import com.sun.tools.internal.ws.wscompile.ErrorReceiverFilter;
import com.sun.tools.internal.ws.wscompile.WsimportOptions;
import com.sun.tools.internal.ws.wsdl.document.*;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEContent;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEMultipartRelated;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEPart;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.soap.*;
import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.GloballyKnown;
import com.sun.tools.internal.ws.wsdl.framework.NoSuchEntityException;
import com.sun.tools.internal.ws.wsdl.parser.DOMForest;
import com.sun.tools.internal.ws.wsdl.parser.WSDLParser;
import com.sun.tools.internal.ws.wsdl.parser.MetadataFinder;
import com.sun.xml.internal.bind.api.JAXBRIContext;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.namespace.QName;
import java.util.*;

/**
 *
 * @author WS Development Team
 *
 * Base class for WSDL->Model classes.
 */
public abstract class WSDLModelerBase implements Modeler {
    protected final ErrorReceiverFilter errReceiver;
    protected final WsimportOptions options;
    protected MetadataFinder forest;


    public WSDLModelerBase(WsimportOptions options, ErrorReceiver receiver) {
        this.options = options;
        this.errReceiver = new ErrorReceiverFilter(receiver);;
    }

    /**
     *
     * @param port
     * @param wsdlPort
     */
    protected void applyPortMethodCustomization(Port port, com.sun.tools.internal.ws.wsdl.document.Port wsdlPort) {
        if(isProvider(wsdlPort))
            return;
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(wsdlPort, JAXWSBinding.class);

        String portMethodName = (jaxwsBinding != null)?((jaxwsBinding.getMethodName() != null)?jaxwsBinding.getMethodName().getName():null):null;
        if(portMethodName != null){
            port.setPortGetter(portMethodName);
        }else{
            portMethodName = Names.getPortName(port);
            portMethodName = JAXBRIContext.mangleNameToClassName(portMethodName);
            port.setPortGetter("get"+portMethodName);
        }

    }

    protected boolean isProvider(com.sun.tools.internal.ws.wsdl.document.Port wsdlPort){
        JAXWSBinding portCustomization = (JAXWSBinding)getExtensionOfType(wsdlPort, JAXWSBinding.class);
        Boolean isProvider = (portCustomization != null)?portCustomization.isProvider():null;
        if(isProvider != null){
            return isProvider;
        }

        JAXWSBinding jaxwsGlobalCustomization = (JAXWSBinding)getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        isProvider = (jaxwsGlobalCustomization != null)?jaxwsGlobalCustomization.isProvider():null;
        if(isProvider != null)
            return isProvider;
        return false;
    }

    protected SOAPBody getSOAPRequestBody() {
        SOAPBody requestBody =
            (SOAPBody)getAnyExtensionOfType(info.bindingOperation.getInput(),
                SOAPBody.class);
        if (requestBody == null) {
            // the WSDL document is invalid
            error(info.bindingOperation.getInput(), ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_MISSING_SOAP_BODY(info.bindingOperation.getName()));
        }
        return requestBody;
    }

    protected boolean isRequestMimeMultipart() {
        for (TWSDLExtension extension: info.bindingOperation.getInput().extensions()) {
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isResponseMimeMultipart() {
        for (TWSDLExtension extension: info.bindingOperation.getOutput().extensions()) {
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                return true;
            }
        }
        return false;
    }




    protected SOAPBody getSOAPResponseBody() {
        SOAPBody responseBody =
            (SOAPBody)getAnyExtensionOfType(info.bindingOperation.getOutput(),
                SOAPBody.class);
        if (responseBody == null) {
            // the WSDL document is invalid
            error(info.bindingOperation.getOutput(),  ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_MISSING_SOAP_BODY(info.bindingOperation.getName()));
        }
        return responseBody;
    }

    protected com.sun.tools.internal.ws.wsdl.document.Message getOutputMessage() {
        if (info.portTypeOperation.getOutput() == null)
            return null;
        return info.portTypeOperation.getOutput().resolveMessage(info.document);
    }

    protected com.sun.tools.internal.ws.wsdl.document.Message getInputMessage() {
        return info.portTypeOperation.getInput().resolveMessage(info.document);
    }

    /**
     * @param body request or response body, represents soap:body
     * @param message Input or output message, equivalent to wsdl:message
     * @return iterator over MessagePart
     */
    protected List<MessagePart> getMessageParts(
        SOAPBody body,
        com.sun.tools.internal.ws.wsdl.document.Message message, boolean isInput) {
        String bodyParts = body.getParts();
        ArrayList<MessagePart> partsList = new ArrayList<MessagePart>();
        List<MessagePart> parts = new ArrayList<MessagePart>();

        //get Mime parts
        List mimeParts;
        if(isInput)
            mimeParts = getMimeContentParts(message, info.bindingOperation.getInput());
        else
            mimeParts = getMimeContentParts(message, info.bindingOperation.getOutput());

        if (bodyParts != null) {
            StringTokenizer in = new StringTokenizer(bodyParts.trim(), " ");
            while (in.hasMoreTokens()) {
                String part = in.nextToken();
                MessagePart mPart = message.getPart(part);
                if (null == mPart) {
                    error(message,  ModelerMessages.WSDLMODELER_ERROR_PARTS_NOT_FOUND(part, message.getName()));
                }
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        } else {
            for (MessagePart mPart : message.getParts()) {
                if (!mimeParts.contains(mPart))
                    mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        }

        for (MessagePart mPart : message.getParts()) {
            if(mimeParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
                parts.add(mPart);
            }else if(partsList.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                parts.add(mPart);
            }
        }

        return parts;
    }

    /**
     * @param message
     * @return MessageParts referenced by the mime:content
     */
    protected List<MessagePart> getMimeContentParts(Message message, TWSDLExtensible ext) {
        ArrayList<MessagePart> mimeContentParts = new ArrayList<MessagePart>();

        for (MIMEPart mimePart : getMimeParts(ext)) {
            MessagePart part = getMimeContentPart(message, mimePart);
            if (part != null)
                mimeContentParts.add(part);
        }
        return mimeContentParts;
    }

    /**
     * @param mimeParts
     */
    protected boolean validateMimeParts(Iterable<MIMEPart> mimeParts) {
        boolean gotRootPart = false;
        List<MIMEContent> mimeContents = new ArrayList<MIMEContent>();
        for (MIMEPart mPart : mimeParts) {
            for (TWSDLExtension obj : mPart.extensions()) {
                if (obj instanceof SOAPBody) {
                    if (gotRootPart) {
                        warning(mPart, ModelerMessages.MIMEMODELER_INVALID_MIME_PART_MORE_THAN_ONE_SOAP_BODY(info.operation.getName().getLocalPart()));
                        return false;
                    }
                    gotRootPart = true;
                } else if (obj instanceof MIMEContent) {
                    mimeContents.add((MIMEContent) obj);
                }
            }
            if(!validateMimeContentPartNames(mimeContents))
                return false;
            if(mPart.getName() != null) {
                warning(mPart, ModelerMessages.MIMEMODELER_INVALID_MIME_PART_NAME_NOT_ALLOWED(info.portTypeOperation.getName()));
            }
        }
        return true;

    }

    private MessagePart getMimeContentPart(Message message, MIMEPart part) {
        for( MIMEContent mimeContent : getMimeContents(part) ) {
            String mimeContentPartName = mimeContent.getPart();
            MessagePart mPart = message.getPart(mimeContentPartName);
            //RXXXX mime:content MUST have part attribute
            if(null == mPart) {
                error(mimeContent,  ModelerMessages.WSDLMODELER_ERROR_PARTS_NOT_FOUND(mimeContentPartName, message.getName()));
            }
            mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
            return mPart;
        }
        return null;
    }

    //List of mimeTypes
    protected List<String> getAlternateMimeTypes(List<MIMEContent> mimeContents) {
        List<String> mimeTypes = new ArrayList<String>();
        //validateMimeContentPartNames(mimeContents.iterator());
//        String mimeType = null;
        for(MIMEContent mimeContent:mimeContents){
            String mimeType = getMimeContentType(mimeContent);
            if(!mimeTypes.contains(mimeType))
                mimeTypes.add(mimeType);
        }
        return mimeTypes;
    }

    private boolean validateMimeContentPartNames(List<MIMEContent> mimeContents) {
        //validate mime:content(s) in the mime:part as per R2909
        for (MIMEContent mimeContent : mimeContents) {
            String mimeContnetPart;
            mimeContnetPart = getMimeContentPartName(mimeContent);
            if(mimeContnetPart == null) {
                warning(mimeContent, ModelerMessages.MIMEMODELER_INVALID_MIME_CONTENT_MISSING_PART_ATTRIBUTE(info.operation.getName().getLocalPart()));
                return false;
            }
        }
        return true;
    }

    protected Iterable<MIMEPart> getMimeParts(TWSDLExtensible ext) {
        MIMEMultipartRelated multiPartRelated =
            (MIMEMultipartRelated) getAnyExtensionOfType(ext,
                    MIMEMultipartRelated.class);
        if(multiPartRelated == null) {
            return Collections.emptyList();
        }
        return multiPartRelated.getParts();
    }

    //returns MIMEContents
    protected List<MIMEContent> getMimeContents(MIMEPart part) {
        List<MIMEContent> mimeContents = new ArrayList<MIMEContent>();
        for (TWSDLExtension mimeContent : part.extensions()) {
            if (mimeContent instanceof MIMEContent) {
                mimeContents.add((MIMEContent) mimeContent);
            }
        }
        //validateMimeContentPartNames(mimeContents.iterator());
        return mimeContents;
    }

    private String getMimeContentPartName(MIMEContent mimeContent){
        /*String partName = mimeContent.getPart();
        if(partName == null){
            throw new ModelerException("mimemodeler.invalidMimeContent.missingPartAttribute",
                    new Object[] {info.operation.getName().getLocalPart()});
        }
        return partName;*/
        return mimeContent.getPart();
    }

    private String getMimeContentType(MIMEContent mimeContent){
        String mimeType = mimeContent.getType();
        if(mimeType == null){
            error(mimeContent, ModelerMessages.MIMEMODELER_INVALID_MIME_CONTENT_MISSING_TYPE_ATTRIBUTE(info.operation.getName().getLocalPart()));
        }
        return mimeType;
    }

    /**
     * For Document/Lit the wsdl:part should only have element attribute and
     * for RPC/Lit or RPC/Encoded the wsdl:part should only have type attribute
     * inside wsdl:message.
     */
    protected boolean isStyleAndPartMatch(
        SOAPOperation soapOperation,
        MessagePart part) {

        // style attribute on soap:operation takes precedence over the
        // style attribute on soap:binding

        if ((soapOperation != null) && (soapOperation.getStyle() != null)) {
            if ((soapOperation.isDocument()
                && (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT))
                || (soapOperation.isRPC()
                    && (part.getDescriptorKind() != SchemaKinds.XSD_TYPE))) {
                return false;
            }
        } else {
            if ((info.soapBinding.isDocument()
                && (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT))
                || (info.soapBinding.isRPC()
                    && (part.getDescriptorKind() != SchemaKinds.XSD_TYPE))) {
                return false;
            }
        }

        return true;
    }



    protected String getRequestNamespaceURI(SOAPBody body) {
        String namespaceURI = body.getNamespace();
        if (namespaceURI == null) {
            if(options.isExtensionMode()){
                return info.modelPort.getName().getNamespaceURI();
            }
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            error(body, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_INPUT_SOAP_BODY_MISSING_NAMESPACE(info.bindingOperation.getName()));
        }
        return namespaceURI;
    }

    protected String getResponseNamespaceURI(SOAPBody body) {
        String namespaceURI = body.getNamespace();
        if (namespaceURI == null) {
            if(options.isExtensionMode()){
                return info.modelPort.getName().getNamespaceURI();
            }
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            error(body, ModelerMessages.WSDLMODELER_INVALID_BINDING_OPERATION_OUTPUT_SOAP_BODY_MISSING_NAMESPACE(info.bindingOperation.getName()));
        }
        return namespaceURI;
    }

    /**
     * @return List of SOAPHeader extensions
     */
    protected List<SOAPHeader> getHeaderExtensions(TWSDLExtensible extensible) {
        List<SOAPHeader> headerList = new ArrayList<SOAPHeader>();
        for (TWSDLExtension extension : extensible.extensions()) {
            if (extension.getClass()==MIMEMultipartRelated.class) {
                for( MIMEPart part : ((MIMEMultipartRelated) extension).getParts() ) {
                    boolean isRootPart = isRootPart(part);
                    for (TWSDLExtension obj : part.extensions()) {
                        if (obj instanceof SOAPHeader) {
                            //bug fix: 5024015
                            if (!isRootPart) {
                                warning((Entity) obj, ModelerMessages.MIMEMODELER_WARNING_IGNORINGINVALID_HEADER_PART_NOT_DECLARED_IN_ROOT_PART(info.bindingOperation.getName()));
                                return new ArrayList<SOAPHeader>();
                            }
                            headerList.add((SOAPHeader) obj);
                        }
                    }
                }
            } else if (extension instanceof SOAPHeader) {
                headerList.add((SOAPHeader) extension);
            }
        }
         return headerList;
    }

    /**
     * @param part
     * @return true if part is the Root part
     */
    private boolean isRootPart(MIMEPart part) {
        for (TWSDLExtension twsdlExtension : part.extensions()) {
            if (twsdlExtension instanceof SOAPBody)
                return true;
        }
        return false;
    }

    protected Set getDuplicateFaultNames() {
        // look for fault messages with the same soap:fault name
        Set<QName> faultNames = new HashSet<QName>();
        Set<QName> duplicateNames = new HashSet<QName>();
        for( BindingFault bindingFault : info.bindingOperation.faults() ) {
            com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault = null;
            for (com.sun.tools.internal.ws.wsdl.document.Fault aFault : info.portTypeOperation.faults()) {
                if (aFault.getName().equals(bindingFault.getName())) {
                    if (portTypeFault != null) {
                        // the WSDL document is invalid
                        error(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_NOT_UNIQUE(bindingFault.getName(),
                            info.bindingOperation.getName()));
                    } else {
                        portTypeFault = aFault;
                    }
                }
            }
            if (portTypeFault == null) {
                // the WSDL document is invalid
                error(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_NOT_FOUND(bindingFault.getName(),
                    info.bindingOperation.getName()));
            }
            SOAPFault soapFault =
                (SOAPFault)getExtensionOfType(bindingFault, SOAPFault.class);
            if (soapFault == null) {
                // the WSDL document is invalid
                if(options.isExtensionMode()){
                    warning(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(bindingFault.getName(),
                    info.bindingOperation.getName()));
                }else {
                    error(bindingFault, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_OUTPUT_MISSING_SOAP_FAULT(bindingFault.getName(),
                        info.bindingOperation.getName()));
                }
            }

            com.sun.tools.internal.ws.wsdl.document.Message faultMessage =
                portTypeFault.resolveMessage(info.document);
            if(faultMessage.getParts().isEmpty()) {
                // the WSDL document is invalid
                error(faultMessage, ModelerMessages.WSDLMODELER_INVALID_BINDING_FAULT_EMPTY_MESSAGE(bindingFault.getName(),
                    faultMessage.getName()));
            }
            //  bug fix: 4852729
            if (!options.isExtensionMode() && (soapFault != null && soapFault.getNamespace() != null)) {
                warning(soapFault, ModelerMessages.WSDLMODELER_WARNING_R_2716_R_2726("soapbind:fault", soapFault.getName()));
            }
            String faultNamespaceURI = (soapFault != null && soapFault.getNamespace() != null)?soapFault.getNamespace():portTypeFault.getMessage().getNamespaceURI();
            String faultName = faultMessage.getName();
            QName faultQName = new QName(faultNamespaceURI, faultName);
            if (faultNames.contains(faultQName)) {
                duplicateNames.add(faultQName);
            } else {
                faultNames.add(faultQName);
            }
        }
        return duplicateNames;
    }


    /**
     * @param operation
     * @return true if operation has valid body parts
     */
    protected boolean validateBodyParts(BindingOperation operation) {
        boolean isRequestResponse =
            info.portTypeOperation.getStyle()
            == OperationStyle.REQUEST_RESPONSE;
        List<MessagePart> inputParts = getMessageParts(getSOAPRequestBody(), getInputMessage(), true);
        if(!validateStyleAndPart(operation, inputParts))
            return false;

        if(isRequestResponse){
            List<MessagePart> outputParts = getMessageParts(getSOAPResponseBody(), getOutputMessage(), false);
            if(!validateStyleAndPart(operation, outputParts))
                return false;
        }
        return true;
    }

    /**
     * @param operation
     * @return true if operation has valid style and part
     */
    private boolean validateStyleAndPart(BindingOperation operation, List<MessagePart> parts) {
        SOAPOperation soapOperation =
            (SOAPOperation) getExtensionOfType(operation, SOAPOperation.class);
        for (MessagePart part : parts) {
            if (part.getBindingExtensibilityElementKind() == MessagePart.SOAP_BODY_BINDING) {
                if (!isStyleAndPartMatch(soapOperation, part))
                    return false;
            }
        }
        return true;
    }

    protected String getLiteralJavaMemberName(Fault fault) {
        String javaMemberName;

        QName memberName = fault.getElementName();
        javaMemberName = fault.getJavaMemberName();
        if (javaMemberName == null)
            javaMemberName = memberName.getLocalPart();
        return javaMemberName;
    }

    /**
     * @param ext
     * @param message
     * @param name
     * @return List of MimeContents from ext
     */
    protected List<MIMEContent> getMimeContents(TWSDLExtensible ext, Message message, String name) {
        for (MIMEPart mimePart : getMimeParts(ext)) {
            List<MIMEContent> mimeContents = getMimeContents(mimePart);
            for (MIMEContent mimeContent : mimeContents) {
                if (mimeContent.getPart().equals(name))
                    return mimeContents;
            }
        }
        return null;
    }

    protected String makePackageQualified(String s) {
        if (options.defaultPackage != null
            && !options.defaultPackage.equals("")) {
            return options.defaultPackage + "." + s;
        } else {
            return s;
        }
    }


    protected String getUniqueName(
        com.sun.tools.internal.ws.wsdl.document.Operation operation,
        boolean hasOverloadedOperations) {
        if (hasOverloadedOperations) {
            return operation.getUniqueKey().replace(' ', '_');
        } else {
            return operation.getName();
        }
    }

    protected static QName getQNameOf(GloballyKnown entity) {
        return new QName(
            entity.getDefining().getTargetNamespaceURI(),
            entity.getName());
    }

    protected static TWSDLExtension getExtensionOfType(
            TWSDLExtensible extensible,
            Class type) {
        for (TWSDLExtension extension:extensible.extensions()) {
            if (extension.getClass().equals(type)) {
                return extension;
            }
        }

        return null;
    }

    protected TWSDLExtension getAnyExtensionOfType(
        TWSDLExtensible extensible,
        Class type) {
        if(extensible == null)
            return null;
        for (TWSDLExtension extension:extensible.extensions()) {
            if(extension.getClass().equals(type)) {
                return extension;
            }else if (extension.getClass().equals(MIMEMultipartRelated.class) &&
                    (type.equals(SOAPBody.class) || type.equals(MIMEContent.class)
                            || type.equals(MIMEPart.class))) {
                for (MIMEPart part : ((MIMEMultipartRelated)extension).getParts()) {
                    //bug fix: 5024001
                    TWSDLExtension extn = getExtensionOfType(part, type);
                    if (extn != null)
                        return extn;
                }
            }
        }

        return null;
    }

    // bug fix: 4857100
    protected static com.sun.tools.internal.ws.wsdl.document.Message findMessage(
        QName messageName,
        ProcessSOAPOperationInfo info) {
        com.sun.tools.internal.ws.wsdl.document.Message message = null;
        try {
            message =
                (com.sun.tools.internal.ws.wsdl.document.Message)info.document.find(
                    Kinds.MESSAGE,
                    messageName);
        } catch (NoSuchEntityException e) {
        }
        return message;
    }

    protected static boolean tokenListContains(
        String tokenList,
        String target) {
        if (tokenList == null) {
            return false;
        }

        StringTokenizer tokenizer = new StringTokenizer(tokenList, " ");
        while (tokenizer.hasMoreTokens()) {
            String s = tokenizer.nextToken();
            if (target.equals(s)) {
                return true;
            }
        }
        return false;
    }

    protected String getUniqueClassName(String className) {
        int cnt = 2;
        String uniqueName = className;
        while (reqResNames.contains(uniqueName.toLowerCase())) {
            uniqueName = className + cnt;
            cnt++;
        }
        reqResNames.add(uniqueName.toLowerCase());
        return uniqueName;
    }

    protected boolean isConflictingClassName(String name) {
        if (_conflictingClassNames == null) {
            return false;
        }

        return _conflictingClassNames.contains(name);
    }

    protected boolean isConflictingServiceClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingStubClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingTieClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingPortClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingExceptionClassName(String name) {
        return isConflictingClassName(name);
    }

    int numPasses = 0;

    protected void warning(Entity entity, String message){
        //avoid duplicate warning for the second pass
        if(numPasses > 1)
            return;
        if(entity == null)
            errReceiver.warning(null, message);
        else
            errReceiver.warning(entity.getLocator(), message);
    }

    protected void error(Entity entity, String message){
        if(entity == null)
            errReceiver.error(null, message);
        else
            errReceiver.error(entity.getLocator(), message);
        throw new AbortException();
    }

    protected static final String OPERATION_HAS_VOID_RETURN_TYPE =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.operationHasVoidReturnType";
    protected static final String WSDL_PARAMETER_ORDER =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.parameterOrder";
    public static final String WSDL_RESULT_PARAMETER =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.resultParameter";
    public static final String MESSAGE_HAS_MIME_MULTIPART_RELATED_BINDING =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.mimeMultipartRelatedBinding";


    protected ProcessSOAPOperationInfo info;

    private Set _conflictingClassNames;
    protected Map<String,JavaException> _javaExceptions;
    protected Map _faultTypeToStructureMap;
    protected JavaSimpleTypeCreator _javaTypes;
    protected Map<QName, Port> _bindingNameToPortMap;
    protected boolean useWSIBasicProfile = true;

    private final Set<String> reqResNames = new HashSet<String>();

    public class ProcessSOAPOperationInfo {

        public ProcessSOAPOperationInfo(
            Port modelPort,
            com.sun.tools.internal.ws.wsdl.document.Port port,
            com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation,
            BindingOperation bindingOperation,
            SOAPBinding soapBinding,
            WSDLDocument document,
            boolean hasOverloadedOperations,
            Map headers) {
            this.modelPort = modelPort;
            this.port = port;
            this.portTypeOperation = portTypeOperation;
            this.bindingOperation = bindingOperation;
            this.soapBinding = soapBinding;
            this.document = document;
            this.hasOverloadedOperations = hasOverloadedOperations;
            this.headers = headers;
        }

        public Port modelPort;
        public com.sun.tools.internal.ws.wsdl.document.Port port;
        public com.sun.tools.internal.ws.wsdl.document.Operation portTypeOperation;
        public BindingOperation bindingOperation;
        public SOAPBinding soapBinding;
        public WSDLDocument document;
        public boolean hasOverloadedOperations;
        public Map headers;

        // additional data
        public Operation operation;
        public String uniqueOperationName;
    }

    public static class WSDLExceptionInfo {
        public String exceptionType;
        public QName wsdlMessage;
        public String wsdlMessagePartName;
        public HashMap constructorOrder; // mapping of element name to
                                             // constructor order (of type Integer)
    }


    protected WSDLParser parser;
    protected WSDLDocument document;
    protected static final LocatorImpl NULL_LOCATOR = new LocatorImpl();
}
