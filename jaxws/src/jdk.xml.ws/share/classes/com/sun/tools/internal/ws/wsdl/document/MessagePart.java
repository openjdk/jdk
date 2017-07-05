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

package com.sun.tools.internal.ws.wsdl.document;

import com.sun.tools.internal.ws.wsdl.framework.Entity;
import com.sun.tools.internal.ws.wsdl.framework.EntityReferenceAction;
import com.sun.tools.internal.ws.wsdl.framework.Kind;
import com.sun.tools.internal.ws.wsdl.framework.QNameAction;
import org.xml.sax.Locator;

import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;

/**
 * Entity corresponding to a WSDL message part.
 *
 * @author WS Development Team
 */
public class MessagePart extends Entity {

    public static final int SOAP_BODY_BINDING = 1;
    public static final int SOAP_HEADER_BINDING = 2;
    public static final int SOAP_HEADERFAULT_BINDING = 3;
    public static final int SOAP_FAULT_BINDING = 4;
    public static final int WSDL_MIME_BINDING = 5;
    public static final int PART_NOT_BOUNDED = -1;

    public MessagePart(Locator locator) {
        super(locator);
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public QName getDescriptor() {
        return _descriptor;
    }

    public void setDescriptor(QName n) {
        _descriptor = n;
    }

    public Kind getDescriptorKind() {
        return _descriptorKind;
    }

    public void setDescriptorKind(Kind k) {
        _descriptorKind = k;
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_PART;
    }

    public int getBindingExtensibilityElementKind(){
        return _bindingKind;
    }

    public void setBindingExtensibilityElementKind(int kind) {
        _bindingKind = kind;
    }

    public void withAllQNamesDo(QNameAction action) {
        if (_descriptor != null) {
            action.perform(_descriptor);
        }
    }

    public void withAllEntityReferencesDo(EntityReferenceAction action) {
        super.withAllEntityReferencesDo(action);
        if (_descriptor != null && _descriptorKind != null) {
            action.perform(_descriptorKind, _descriptor);
        }
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.visit(this);
    }

    public void validateThis() {
        if(_descriptor != null && _descriptor.getLocalPart().equals("")){
            failValidation("validation.invalidElement", _descriptor.toString());
        }
    }

    public void setMode(Mode mode){
        this.mode = mode;
    }

    public Mode getMode(){
        return mode;
    }

    public boolean isINOUT(){
        if(mode!=null)
            return (mode == Mode.INOUT);
        return false;
    }

    public boolean isIN(){
        if(mode!=null)
            return (mode == Mode.IN);
        return false;
    }

    public boolean isOUT(){
        if(mode!=null)
            return (mode == Mode.OUT);
        return false;
    }

    public void setReturn(boolean ret){
        isRet=ret;
    }

    public boolean isReturn(){
        return isRet;
    }


    private boolean isRet;
    private String _name;
    private QName _descriptor;
    private Kind _descriptorKind;
    private int _bindingKind;

    private Mode mode;
}
