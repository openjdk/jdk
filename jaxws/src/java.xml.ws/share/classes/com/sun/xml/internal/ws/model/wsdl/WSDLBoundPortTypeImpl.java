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

package com.sun.xml.internal.ws.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundOperation;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLBoundPortType;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPortType;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.util.QNameMap;
import com.sun.xml.internal.ws.util.exception.LocatableWebServiceException;

import javax.jws.WebParam.Mode;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.soap.MTOMFeature;

/**
 * Implementation of {@link WSDLBoundPortType}
 *
 * @author Vivek Pandey
 */
public final class WSDLBoundPortTypeImpl extends AbstractFeaturedObjectImpl implements EditableWSDLBoundPortType {
    private final QName name;
    private final QName portTypeName;
    private EditableWSDLPortType portType;
    private BindingID bindingId;
    private final @NotNull EditableWSDLModel owner;
    private final QNameMap<EditableWSDLBoundOperation> bindingOperations = new QNameMap<EditableWSDLBoundOperation>();

    /**
     * Operations keyed by the payload tag name.
     */
    private QNameMap<EditableWSDLBoundOperation> payloadMap;
    /**
     * {@link #payloadMap} doesn't allow null key, so we store the value for it here.
     */
    private EditableWSDLBoundOperation emptyPayloadOperation;



    public WSDLBoundPortTypeImpl(XMLStreamReader xsr,@NotNull EditableWSDLModel owner, QName name, QName portTypeName) {
        super(xsr);
        this.owner = owner;
        this.name = name;
        this.portTypeName = portTypeName;
        owner.addBinding(this);
    }

    public QName getName() {
        return name;
    }

    public @NotNull EditableWSDLModel getOwner() {
        return owner;
    }

    public EditableWSDLBoundOperation get(QName operationName) {
        return bindingOperations.get(operationName);
    }

    /**
     * Populates the Map that holds operation name as key and {@link WSDLBoundOperation} as the value.
     *
     * @param opName Must be non-null
     * @param ptOp   Must be non-null
     * @throws NullPointerException if either opName or ptOp is null
     */
    public void put(QName opName, EditableWSDLBoundOperation ptOp) {
        bindingOperations.put(opName,ptOp);
    }

    public QName getPortTypeName() {
        return portTypeName;
    }

    public EditableWSDLPortType getPortType() {
        return portType;
    }

    public Iterable<EditableWSDLBoundOperation> getBindingOperations() {
        return bindingOperations.values();
    }

    public BindingID getBindingId() {
        //Should the default be SOAP1.1/HTTP binding? For now lets keep it for
        //JBI bug 6509800
        return (bindingId==null)?BindingID.SOAP11_HTTP:bindingId;
    }

    public void setBindingId(BindingID bindingId) {
        this.bindingId = bindingId;
    }

    /**
     * sets whether the {@link WSDLBoundPortType} is rpc or lit
     */
    private Style style = Style.DOCUMENT;
    public void setStyle(Style style){
        this.style = style;
    }

    public SOAPBinding.Style getStyle() {
        return style;
    }

    public boolean isRpcLit(){
        return Style.RPC==style;
    }

    public boolean isDoclit(){
        return Style.DOCUMENT==style;
    }


    /**
     * Gets the {@link ParameterBinding} for a given operation, part name and the direction - IN/OUT
     *
     * @param operation wsdl:operation@name value. Must be non-null.
     * @param part      wsdl:part@name such as value of soap:header@part. Must be non-null.
     * @param mode      {@link Mode#IN} or {@link Mode#OUT}. Must be non-null.
     * @return null if the binding could not be resolved for the part.
     */
    public ParameterBinding getBinding(QName operation, String part, Mode mode) {
        EditableWSDLBoundOperation op = get(operation);
        if (op == null) {
            //TODO throw exception
            return null;
        }
        if ((Mode.IN == mode) || (Mode.INOUT == mode))
            return op.getInputBinding(part);
        else
            return op.getOutputBinding(part);
    }

    public EditableWSDLBoundOperation getOperation(String namespaceUri, String localName) {
        if(namespaceUri==null && localName == null)
            return emptyPayloadOperation;
        else{
            return payloadMap.get((namespaceUri==null)?"":namespaceUri,localName);
        }
    }

    public void freeze() {
        portType = owner.getPortType(portTypeName);
        if(portType == null){
            throw new LocatableWebServiceException(
                    ClientMessages.UNDEFINED_PORT_TYPE(portTypeName), getLocation());
        }
        portType.freeze();

        for (EditableWSDLBoundOperation op : bindingOperations.values()) {
            op.freeze(owner);
        }

        freezePayloadMap();
        owner.finalizeRpcLitBinding(this);
    }

    private void freezePayloadMap() {
        if(style== Style.RPC) {
            payloadMap = new QNameMap<EditableWSDLBoundOperation>();
            for(EditableWSDLBoundOperation op : bindingOperations.values()){
                payloadMap.put(op.getRequestPayloadName(), op);
            }
        } else {
            payloadMap = new QNameMap<EditableWSDLBoundOperation>();
            // For doclit The tag will be the operation that has the same input part descriptor value
            for(EditableWSDLBoundOperation op : bindingOperations.values()){
                QName name = op.getRequestPayloadName();
                if(name == null){
                    //empty payload
                    emptyPayloadOperation = op;
                    continue;
                }

                payloadMap.put(name, op);
            }
        }
    }
}
