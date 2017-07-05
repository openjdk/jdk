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
package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.model.Mode;
import com.sun.xml.internal.ws.model.ParameterBinding;

import javax.xml.namespace.QName;
import java.util.HashMap;

public class Binding extends HashMap<String, BindingOperation> {
    private QName name;
    private QName portTypeName;
    private PortType portType;
    private String bindingId;
    private WSDLDocument wsdlDoc;
    private boolean finalized = false;

    public Binding(QName name, QName portTypeName) {
        super();
        this.name = name;
        this.portTypeName = portTypeName;
    }

    public QName getName() {
        return name;
    }

    public QName getPortTypeName(){
        return portTypeName;
    }

    public PortType getPortType() {
        return portType;
    }

    public void setPortType(PortType portType) {
        this.portType = portType;
    }

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public void setWsdlDocument(WSDLDocument wsdlDoc) {
        this.wsdlDoc = wsdlDoc;
    }

    public ParameterBinding getBinding(String operation, String part, Mode mode){
        BindingOperation op = get(operation);
        if(op == null){
            //TODO throw exception
            return null;
        }
        if((Mode.IN == mode)||(Mode.INOUT == mode))
            return op.getInputBinding(part);
        else
            return op.getOutputBinding(part);
    }

    /**
     * Gives binding for a given {@link BindingOperation} a wsdl part and {@link Mode}
     * @param op  must be non-null
     * @param part must be non-null
     * @param mode must be non-null
     * @return  parameter Binding, null the binding could not be determined.
     */
    public ParameterBinding getBinding(BindingOperation op, String part, Mode mode){
        if((Mode.IN == mode)||(Mode.INOUT == mode))
            return op.getInputBinding(part);
        else
            return op.getOutputBinding(part);
    }

    public String getMimeType(String operation, String part, Mode mode){
        BindingOperation op = get(operation);
        if(Mode.IN == mode)
            return op.getMimeTypeForInputPart(part);
        else
            return op.getMimeTypeForOutputPart(part);
    }

    public void finalizeBinding(){
        if(!finalized){
            wsdlDoc.finalizeBinding(this);
            finalized = true;
        }
    }

}
