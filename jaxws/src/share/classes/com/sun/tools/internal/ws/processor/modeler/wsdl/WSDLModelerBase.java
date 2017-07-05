
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

import com.sun.tools.internal.ws.processor.config.WSDLModelInfo;
import com.sun.tools.internal.ws.processor.generator.Names;
import com.sun.tools.internal.ws.processor.model.AbstractType;
import com.sun.tools.internal.ws.processor.model.Block;
import com.sun.tools.internal.ws.processor.model.Fault;
import com.sun.tools.internal.ws.processor.model.Model;
import com.sun.tools.internal.ws.processor.model.ModelObject;
import com.sun.tools.internal.ws.processor.model.ModelProperties;
import com.sun.tools.internal.ws.processor.model.Operation;
import com.sun.tools.internal.ws.processor.model.Parameter;
import com.sun.tools.internal.ws.processor.model.Port;
import com.sun.tools.internal.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.internal.ws.processor.modeler.Modeler;
import com.sun.tools.internal.ws.processor.modeler.ModelerException;
import com.sun.tools.internal.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.internal.ws.wsdl.document.Binding;
import com.sun.tools.internal.ws.wsdl.document.BindingFault;
import com.sun.tools.internal.ws.wsdl.document.BindingOperation;
import com.sun.tools.internal.ws.wsdl.document.Documentation;
import com.sun.tools.internal.ws.wsdl.document.Kinds;
import com.sun.tools.internal.ws.wsdl.document.Message;
import com.sun.tools.internal.ws.wsdl.document.MessagePart;
import com.sun.tools.internal.ws.wsdl.document.OperationStyle;
import com.sun.tools.internal.ws.wsdl.document.WSDLDocument;
import com.sun.tools.internal.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEContent;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEMultipartRelated;
import com.sun.tools.internal.ws.wsdl.document.mime.MIMEPart;
import com.sun.tools.internal.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPBody;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPFault;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPHeader;
import com.sun.tools.internal.ws.wsdl.document.soap.SOAPOperation;
import com.sun.tools.internal.ws.wsdl.framework.Extensible;
import com.sun.tools.internal.ws.wsdl.framework.Extension;
import com.sun.tools.internal.ws.wsdl.framework.GloballyKnown;
import com.sun.tools.internal.ws.wsdl.framework.NoSuchEntityException;
import com.sun.tools.internal.ws.wsdl.parser.Constants;
import com.sun.tools.internal.ws.wsdl.parser.Util;
import com.sun.tools.internal.ws.wsdl.parser.WSDLParser;
import com.sun.xml.internal.ws.util.localization.Localizable;
import com.sun.xml.internal.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;

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
 *
 * @author WS Development Team
 *
 * Base class for WSDL->Model classes.
 */
public abstract class WSDLModelerBase implements Modeler {
    public WSDLModelerBase(WSDLModelInfo modelInfo, Properties options) {
        //init();
        _modelInfo = modelInfo;
        _options = options;
        _messageFactory =
            new LocalizableMessageFactory("com.sun.tools.internal.ws.resources.modeler");
        _conflictingClassNames = null;
        _env = (ProcessorEnvironment)modelInfo.getParent().getEnvironment();
        hSet = null;
        reqResNames = new HashSet();
    }


    protected WSDLParser createWSDLParser(){
        return new WSDLParser(_modelInfo);
    }

    /**
     * Builds model from WSDL document. Model contains abstraction which is used by the
     * generators to generate the stub/tie/serializers etc. code.
     *
     * @see Modeler#buildModel()
     */
    public Model buildModel() {
        return null;
    }

    protected WSDLModelInfo getWSDLModelInfo(){
        return _modelInfo;
    }

    protected Documentation getDocumentationFor(Element e) {
        String s = XmlUtil.getTextForNode(e);
        if (s == null) {
            return null;
        } else {
            return new Documentation(s);
        }
    }

    protected void checkNotWsdlElement(Element e) {
        // possible extensibility element -- must live outside the WSDL namespace
        if (e.getNamespaceURI().equals(Constants.NS_WSDL))
            Util.fail("parsing.invalidWsdlElement", e.getTagName());
    }

    /**
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
            portMethodName = getEnvironment().getNames().validJavaClassName(portMethodName);
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

    protected void createParentFault(Fault fault) {
        AbstractType faultType = fault.getBlock().getType();
        AbstractType parentType = null;


        if (parentType == null) {
            return;
        }

        if (fault.getParentFault() != null) {
            return;
        }
        Fault parentFault =
            new Fault(((AbstractType)parentType).getName().getLocalPart());
        /* this is what it really should be but for interop with JAXRPC 1.0.1 we are not doing
         * this at this time.
         *
         * TODO - we should double-check this; the above statement might not be true anymore.
         */
        QName faultQName =
            new QName(
                fault.getBlock().getName().getNamespaceURI(),
                parentFault.getName());
        Block block = new Block(faultQName);
        block.setType((AbstractType)parentType);
        parentFault.setBlock(block);
        parentFault.addSubfault(fault);
        createParentFault(parentFault);
    }

    protected void createSubfaults(Fault fault) {
        AbstractType faultType = fault.getBlock().getType();
        Iterator subtypes = null;
        if (subtypes != null) {
            AbstractType subtype;
            while (subtypes.hasNext()) {
                subtype = (AbstractType)subtypes.next();
                Fault subFault = new Fault(subtype.getName().getLocalPart());
                /* this is what it really is but for interop with JAXRPC 1.0.1 we are not doing
                 * this at this time
                 *
                 * TODO - we should double-check this; the above statement might not be true anymore.
                 */
                QName faultQName =
                    new QName(
                        fault.getBlock().getName().getNamespaceURI(),
                        subFault.getName());
                Block block = new Block(faultQName);
                block.setType(subtype);
                subFault.setBlock(block);
                fault.addSubfault(subFault);
                createSubfaults(subFault);
            }
        }
    }

    protected SOAPBody getSOAPRequestBody() {
        SOAPBody requestBody =
            (SOAPBody)getAnyExtensionOfType(info.bindingOperation.getInput(),
                SOAPBody.class);
        if (requestBody == null) {
            // the WSDL document is invalid
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.inputMissingSoapBody",
                new Object[] { info.bindingOperation.getName()});
        }
        return requestBody;
    }

    protected boolean isRequestMimeMultipart() {
        for (Iterator iter = info.bindingOperation.getInput().extensions(); iter.hasNext();) {
            Extension extension = (Extension)iter.next();
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isResponseMimeMultipart() {
        for (Iterator iter = info.bindingOperation.getOutput().extensions(); iter.hasNext();) {
            Extension extension = (Extension)iter.next();
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
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.outputMissingSoapBody",
                new Object[] { info.bindingOperation.getName()});
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
    protected List getMessageParts(
        SOAPBody body,
        com.sun.tools.internal.ws.wsdl.document.Message message, boolean isInput) {
        String bodyParts = body.getParts();
        ArrayList partsList = new ArrayList();
        List parts = new ArrayList();

        //get Mime parts
        List mimeParts = null;
        if(isInput)
            mimeParts = getMimeContentParts(message, info.bindingOperation.getInput());
        else
            mimeParts = getMimeContentParts(message, info.bindingOperation.getOutput());

        if (bodyParts != null) {
            StringTokenizer in = new StringTokenizer(bodyParts.trim(), " ");
            while (in.hasMoreTokens()) {
                String part = in.nextToken();
                MessagePart mPart = (MessagePart)message.getPart(part);
                if (null == mPart) {
                    throw new ModelerException(
                        "wsdlmodeler.error.partsNotFound",
                        new Object[] { part, message.getName()});
                }
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        } else {
            for(Iterator iter = message.parts();iter.hasNext();) {
                MessagePart mPart = (MessagePart)iter.next();
                if(!mimeParts.contains(mPart))
                    mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        }

        for(Iterator iter = message.parts();iter.hasNext();) {
            MessagePart mPart = (MessagePart)iter.next();
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
    protected List getMimeContentParts(Message message, Extensible ext) {
        ArrayList mimeContentParts = new ArrayList();
        String mimeContentPartName = null;
        Iterator mimeParts = getMimeParts(ext);

        while(mimeParts.hasNext()) {
            MessagePart part = getMimeContentPart(message, (MIMEPart)mimeParts.next());
            if(part != null)
                mimeContentParts.add(part);
        }
        return mimeContentParts;
    }

    /**
     * @param mimeParts
     */
    protected boolean validateMimeParts(Iterator mimeParts) {
        boolean gotRootPart = false;
        List mimeContents = new ArrayList();
        while(mimeParts.hasNext()) {
            MIMEPart mPart = (MIMEPart)mimeParts.next();
            Iterator extns = mPart.extensions();
            while(extns.hasNext()){
                Object obj = extns.next();
                if(obj instanceof SOAPBody){
                    if(gotRootPart) {
                        //bug fix: 5024020
                        warn("mimemodeler.invalidMimePart.moreThanOneSOAPBody",
                                new Object[] {info.operation.getName().getLocalPart()});
                        return false;
                    }
                    gotRootPart = true;
                }else if (obj instanceof MIMEContent) {
                    mimeContents.add((MIMEContent)obj);
                }
            }
            if(!validateMimeContentPartNames(mimeContents.iterator()))
                return false;
            if(mPart.getName() != null) {
                //bug fix: 5024018
                warn("mimemodeler.invalidMimePart.nameNotAllowed",
                        info.portTypeOperation.getName());
            }
        }
        return true;

    }

    private MessagePart getMimeContentPart(Message message, MIMEPart part) {
        String mimeContentPartName = null;
        Iterator mimeContents = getMimeContents(part).iterator();
        if(mimeContents.hasNext()) {
            mimeContentPartName = ((MIMEContent)mimeContents.next()).getPart();
            MessagePart mPart = (MessagePart)message.getPart(mimeContentPartName);
            //RXXXX mime:content MUST have part attribute
            if(null == mPart) {
                throw new ModelerException("wsdlmodeler.error.partsNotFound",
                        new Object[] {mimeContentPartName, message.getName()});
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

    /**
     * @param iterator
     */
    private boolean validateMimeContentPartNames(Iterator mimeContents) {
        //validate mime:content(s) in the mime:part as per R2909
        while(mimeContents.hasNext()){
            String mimeContnetPart = null;
            if(mimeContnetPart == null) {
                mimeContnetPart = getMimeContentPartName((MIMEContent)mimeContents.next());
                if(mimeContnetPart == null) {
                    warn("mimemodeler.invalidMimeContent.missingPartAttribute",
                            new Object[] {info.operation.getName().getLocalPart()});
                    return false;
                }
            }else {
                String newMimeContnetPart = getMimeContentPartName((MIMEContent)mimeContents.next());
                if(newMimeContnetPart == null) {
                    warn("mimemodeler.invalidMimeContent.missingPartAttribute",
                            new Object[] {info.operation.getName().getLocalPart()});
                    return false;
                }else if(!newMimeContnetPart.equals(mimeContnetPart)) {
                    //throw new ModelerException("mimemodeler.invalidMimeContent.differentPart");
                    warn("mimemodeler.invalidMimeContent.differentPart");
                    return false;
                }
            }
        }
        return true;
    }

    protected Iterator<MIMEPart> getMimeParts(Extensible ext) {
        MIMEMultipartRelated multiPartRelated =
            (MIMEMultipartRelated) getAnyExtensionOfType(ext,
                    MIMEMultipartRelated.class);
        if(multiPartRelated == null) {
            List<MIMEPart> parts = new ArrayList<MIMEPart>();
            return parts.iterator();
        }
        return multiPartRelated.getParts();
    }

    //returns MIMEContents
    protected List<MIMEContent> getMimeContents(MIMEPart part) {
        List<MIMEContent> mimeContents = new ArrayList<MIMEContent>();
        Iterator parts = part.extensions();
        while(parts.hasNext()) {
            Extension mimeContent = (Extension) parts.next();
            if (mimeContent instanceof MIMEContent) {
                mimeContents.add((MIMEContent)mimeContent);
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
            throw new ModelerException("mimemodeler.invalidMimeContent.missingTypeAttribute",
                    new Object[] {info.operation.getName().getLocalPart()});
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
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.inputSoapBody.missingNamespace",
                new Object[] { info.bindingOperation.getName()});
        }
        return namespaceURI;
    }

    protected String getResponseNamespaceURI(SOAPBody body) {
        String namespaceURI = body.getNamespace();
        if (namespaceURI == null) {
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.outputSoapBody.missingNamespace",
                new Object[] { info.bindingOperation.getName()});
        }
        return namespaceURI;
    }

    /**
     * @return List of SOAPHeader extensions
     */
    protected List<SOAPHeader> getHeaderExtensions(Extensible extensible) {
        List<SOAPHeader> headerList = new ArrayList<SOAPHeader>();
        Iterator bindingIter = extensible.extensions();
        while (bindingIter.hasNext()) {
            Extension extension = (Extension) bindingIter.next();
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                for (Iterator parts = ((MIMEMultipartRelated) extension).getParts();
                parts.hasNext();) {
                    Extension part = (Extension) parts.next();
                    if (part.getClass().equals(MIMEPart.class)) {
                        boolean isRootPart = isRootPart((MIMEPart)part);
                        Iterator iter = ((MIMEPart)part).extensions();
                        while(iter.hasNext()) {
                            Object obj = iter.next();
                            if(obj instanceof SOAPHeader){
                                //bug fix: 5024015
                                if(!isRootPart) {
                                    warn(
                                            "mimemodeler.warning.IgnoringinvalidHeaderPart.notDeclaredInRootPart",
                                            new Object[] {
                                                    info.bindingOperation.getName()});
                                    return new ArrayList<SOAPHeader>();
                                }
                                headerList.add((SOAPHeader)obj);
                            }
                        }
                    }

                }
            }else if(extension instanceof SOAPHeader) {
                headerList.add((SOAPHeader)extension);
            }
         }
         return headerList;
    }

    /**
     * @param part
     * @return true if part is the Root part
     */
    private boolean isRootPart(MIMEPart part) {
        Iterator iter = part.extensions();
        while(iter.hasNext()){
            if(iter.next() instanceof SOAPBody)
                return true;
        }
        return false;
    }

    protected Set getDuplicateFaultNames() {
        // look for fault messages with the same soap:fault name
        Set faultNames = new HashSet();
        Set duplicateNames = new HashSet();
        for (Iterator iter = info.bindingOperation.faults(); iter.hasNext();) {
            BindingFault bindingFault = (BindingFault)iter.next();
            com.sun.tools.internal.ws.wsdl.document.Fault portTypeFault = null;
            for (Iterator iter2 = info.portTypeOperation.faults();
                iter2.hasNext();
                ) {
                com.sun.tools.internal.ws.wsdl.document.Fault aFault =
                    (com.sun.tools.internal.ws.wsdl.document.Fault)iter2.next();

                if (aFault.getName().equals(bindingFault.getName())) {
                    if (portTypeFault != null) {
                        // the WSDL document is invalid
                        throw new ModelerException(
                            "wsdlmodeler.invalid.bindingFault.notUnique",
                            new Object[] {
                                bindingFault.getName(),
                                info.bindingOperation.getName()});
                    } else {
                        portTypeFault = aFault;
                    }
                }
            }
            if (portTypeFault == null) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.notFound",
                    new Object[] {
                        bindingFault.getName(),
                        info.bindingOperation.getName()});

            }
            SOAPFault soapFault =
                (SOAPFault)getExtensionOfType(bindingFault, SOAPFault.class);
            if (soapFault == null) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.outputMissingSoapFault",
                    new Object[] {
                        bindingFault.getName(),
                        info.bindingOperation.getName()});
            }

            com.sun.tools.internal.ws.wsdl.document.Message faultMessage =
                portTypeFault.resolveMessage(info.document);
            Iterator iter2 = faultMessage.parts();
            if (!iter2.hasNext()) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.emptyMessage",
                    new Object[] {
                        bindingFault.getName(),
                        faultMessage.getName()});
            }
            //  bug fix: 4852729
            if (useWSIBasicProfile && (soapFault.getNamespace() != null)) {
                warn(
                    "wsdlmodeler.warning.r2716r2726",
                    new Object[] { "soapbind:fault", soapFault.getName()});
            }
            String faultNamespaceURI = soapFault.getNamespace();
            if (faultNamespaceURI == null) {
                faultNamespaceURI =
                    portTypeFault.getMessage().getNamespaceURI();
            }
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
        List inputParts = getMessageParts(getSOAPRequestBody(), getInputMessage(), true);
        if(!validateStyleAndPart(operation, inputParts))
            return false;

        if(isRequestResponse){
            List outputParts = getMessageParts(getSOAPResponseBody(), getOutputMessage(), false);
            if(!validateStyleAndPart(operation, outputParts))
                return false;
        }
        return true;
    }

    /**
     * @param operation
     * @return true if operation has valid style and part
     */
    private boolean validateStyleAndPart(BindingOperation operation, List parts) {
        SOAPOperation soapOperation =
            (SOAPOperation) getExtensionOfType(operation, SOAPOperation.class);
        for(Iterator iter = parts.iterator(); iter.hasNext();){
            MessagePart part = (MessagePart)iter.next();
            if(part.getBindingExtensibilityElementKind() == MessagePart.SOAP_BODY_BINDING){
                if(!isStyleAndPartMatch(soapOperation, part))
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
    protected List<MIMEContent> getMimeContents(Extensible ext, Message message, String name) {
        Iterator mimeParts = getMimeParts(ext);
        while(mimeParts.hasNext()){
            MIMEPart mimePart = (MIMEPart)mimeParts.next();
            List<MIMEContent> mimeContents = getMimeContents(mimePart);
            for(MIMEContent mimeContent:mimeContents){
                if(mimeContent.getPart().equals(name))
                    return mimeContents;
            }
        }
        return null;
    }

    protected ProcessorEnvironment getEnvironment() {
        return _env;
    }

    protected void warn(Localizable msg) {
        getEnvironment().warn(msg);
    }

    protected void warn(String key) {
        getEnvironment().warn(_messageFactory.getMessage(key));
    }

    protected void warn(String key, String arg) {
        getEnvironment().warn(_messageFactory.getMessage(key, arg));
    }

    protected void error(String key, String arg) {
        getEnvironment().error(_messageFactory.getMessage(key, arg));
    }

    protected void warn(String key, Object[] args) {
        getEnvironment().warn(_messageFactory.getMessage(key, args));
    }

    protected void info(String key) {
        getEnvironment().info(_messageFactory.getMessage(key));
    }

    protected void info(String key, String arg) {
        getEnvironment().info(_messageFactory.getMessage(key, arg));
    }

    protected String makePackageQualified(String s, QName name) {
        return makePackageQualified(s, name, true);
    }

    protected String makePackageQualified(
        String s,
        QName name,
        boolean useNamespaceMapping) {
        String javaPackageName = null;
        if (useNamespaceMapping) {
            javaPackageName = getJavaPackageName(name);
        }
        if (javaPackageName != null) {
            return javaPackageName + "." + s;
        } else if (
            _modelInfo.getJavaPackageName() != null
                && !_modelInfo.getJavaPackageName().equals("")) {
            return _modelInfo.getJavaPackageName() + "." + s;
        } else {
            return s;
        }
    }

    protected QName makePackageQualified(QName name) {
        return makePackageQualified(name, true);
    }

    protected QName makePackageQualified(
        QName name,
        boolean useNamespaceMapping) {
        return new QName(
            name.getNamespaceURI(),
            makePackageQualified(name.getLocalPart(), name));
    }

    protected String makeNameUniqueInSet(String candidateName, Set names) {
        String baseName = candidateName;
        String name = baseName;
        for (int i = 2; names.contains(name); ++i) {
            name = baseName + Integer.toString(i);
        }
        return name;
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

    protected String getUniqueParameterName(
        Operation operation,
        String baseName) {
        Set names = new HashSet();
        for (Iterator iter = operation.getRequest().getParameters();
            iter.hasNext();
            ) {
            Parameter p = (Parameter)iter.next();
            names.add(p.getName());
        }
        for (Iterator iter = operation.getResponse().getParameters();
            iter.hasNext();
            ) {
            Parameter p = (Parameter)iter.next();
            names.add(p.getName());
        }
        String candidateName = baseName;
        while (names.contains(candidateName)) {
            candidateName += "_prime";
        }
        return candidateName;
    }

    protected String getNonQualifiedNameFor(QName name) {
        return _env.getNames().validJavaClassName(name.getLocalPart());
    }

    protected static void setDocumentationIfPresent(
        ModelObject obj,
        Documentation documentation) {
        if (documentation != null && documentation.getContent() != null) {
            obj.setProperty(WSDL_DOCUMENTATION, documentation.getContent());
        }
    }

    protected static QName getQNameOf(GloballyKnown entity) {
        return new QName(
            entity.getDefining().getTargetNamespaceURI(),
            entity.getName());
    }

    protected static Extension getExtensionOfType(
            Extensible extensible,
            Class type) {
        for (Iterator iter = extensible.extensions(); iter.hasNext();) {
            Extension extension = (Extension)iter.next();
            if (extension.getClass().equals(type)) {
                return extension;
            }
        }

        return null;
    }

    protected Extension getAnyExtensionOfType(
        Extensible extensible,
        Class type) {
        if(extensible == null)
            return null;
        for (Iterator iter = extensible.extensions(); iter.hasNext();) {
            Extension extension = (Extension)iter.next();
            if(extension.getClass().equals(type)) {
                return extension;
            }else if (extension.getClass().equals(MIMEMultipartRelated.class) &&
                    (type.equals(SOAPBody.class) || type.equals(MIMEContent.class)
                            || type.equals(MIMEPart.class))) {
                for (Iterator parts =
                    ((MIMEMultipartRelated) extension).getParts();
                parts.hasNext();
                ) {
                    Extension part = (Extension) parts.next();
                    if (part.getClass().equals(MIMEPart.class)) {
                        MIMEPart mPart = (MIMEPart)part;
                        //bug fix: 5024001
                        Extension extn =  getExtensionOfType((Extensible) part, type);
                        if(extn != null)
                            return extn;
                    }
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

    private String getJavaPackageName(QName name) {
        String packageName = null;
/*        if (_modelInfo.getNamespaceMappingRegistry() != null) {
            NamespaceMappingInfo i =
                _modelInfo
                    .getNamespaceMappingRegistry()
                    .getNamespaceMappingInfo(
                    name);
            if (i != null)
                return i.getJavaPackageName();
        }*/
        return packageName;
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

    protected static final String OPERATION_HAS_VOID_RETURN_TYPE =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.operationHasVoidReturnType";
    private static final String WSDL_DOCUMENTATION =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.documentation";
    protected static final String WSDL_PARAMETER_ORDER =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.parameterOrder";
    public static final String WSDL_RESULT_PARAMETER =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.resultParameter";
    public static final String MESSAGE_HAS_MIME_MULTIPART_RELATED_BINDING =
        "com.sun.xml.internal.ws.processor.modeler.wsdl.mimeMultipartRelatedBinding";


    public ProcessorEnvironment getProcessorEnvironment(){
        return _env;
    }
    protected ProcessSOAPOperationInfo info;

    protected WSDLModelInfo _modelInfo;
    protected Properties _options;
    protected LocalizableMessageFactory _messageFactory;
    private Set _conflictingClassNames;
    protected Map _javaExceptions;
    protected Map _faultTypeToStructureMap;
    private ProcessorEnvironment _env;
    protected JavaSimpleTypeCreator _javaTypes;
    protected Map<QName, Port> _bindingNameToPortMap;
    protected boolean useWSIBasicProfile = true;

    private Set reqResNames;
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
    };


    protected WSDLParser parser;
    protected WSDLDocument document;
    protected HashSet hSet;
}
