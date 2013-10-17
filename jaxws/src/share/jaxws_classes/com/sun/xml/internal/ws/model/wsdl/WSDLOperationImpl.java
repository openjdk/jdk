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
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLFault;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLInput;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLMessage;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLModel;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLOutput;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPart;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPortType;
import com.sun.xml.internal.ws.util.QNameMap;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementaiton of {@link WSDLOperation}
 *
 * @author Vivek Pandey
 */
public final class WSDLOperationImpl extends AbstractExtensibleImpl implements EditableWSDLOperation {
    private final QName name;
    private String parameterOrder;
    private EditableWSDLInput input;
    private EditableWSDLOutput output;
    private final List<EditableWSDLFault> faults;
    private final QNameMap<EditableWSDLFault> faultMap;
    protected Iterable<EditableWSDLMessage> messages;
    private final EditableWSDLPortType owner;

    public WSDLOperationImpl(XMLStreamReader xsr, EditableWSDLPortType owner, QName name) {
        super(xsr);
        this.name = name;
        this.faults = new ArrayList<EditableWSDLFault>();
        this.faultMap = new QNameMap<EditableWSDLFault>();
        this.owner = owner;
    }

    public QName getName() {
        return name;
    }

    public String getParameterOrder() {
        return parameterOrder;
    }

    public void setParameterOrder(String parameterOrder) {
        this.parameterOrder = parameterOrder;
    }

    public EditableWSDLInput getInput() {
        return input;
    }

    public void setInput(EditableWSDLInput input) {
        this.input = input;
    }

    public EditableWSDLOutput getOutput() {
        return output;
    }

    public boolean isOneWay() {
        return output == null;
    }

    public void setOutput(EditableWSDLOutput output) {
        this.output = output;
    }

    public Iterable<EditableWSDLFault> getFaults() {
        return faults;
    }

    public EditableWSDLFault getFault(QName faultDetailName) {
        EditableWSDLFault fault = faultMap.get(faultDetailName);
        if(fault != null)
            return fault;

        for(EditableWSDLFault fi : faults){
            assert fi.getMessage().parts().iterator().hasNext();
            EditableWSDLPart part = fi.getMessage().parts().iterator().next();
            if(part.getDescriptor().name().equals(faultDetailName)){
                faultMap.put(faultDetailName, fi);
                return fi;
            }
        }
        return null;
    }

    @NotNull
    public QName getPortTypeName() {
        return owner.getName();
    }

    public void addFault(EditableWSDLFault fault) {
        faults.add(fault);
    }

    public void freeze(EditableWSDLModel root) {
        assert input != null;
        input.freeze(root);
        if(output != null)
            output.freeze(root);
        for(EditableWSDLFault fault : faults){
            fault.freeze(root);
        }
    }
}
