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
package com.sun.xml.internal.ws.model.wsdl;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundFault;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;

import javax.xml.stream.XMLStreamReader;
import javax.xml.namespace.QName;

/**
 * @author Vivek Pandey
 */
public class WSDLBoundFaultImpl extends AbstractExtensibleImpl implements WSDLBoundFault {
    private final String name;
    private WSDLFault fault;
    private WSDLBoundOperationImpl owner;

    public WSDLBoundFaultImpl(XMLStreamReader xsr, String name, WSDLBoundOperationImpl owner) {
        super(xsr);
        this.name = name;
        this.owner = owner;
    }

    public
    @NotNull
    String getName() {
        return name;
    }

    public QName getQName() {
        if(owner.getOperation() != null){
            return new QName(owner.getOperation().getName().getNamespaceURI(), name);
        }
        return null;
    }

    public WSDLFault getFault() {
        return fault;
    }

    @NotNull
    public WSDLBoundOperation getBoundOperation() {
        return owner;
    }

    void freeze(WSDLBoundOperationImpl root) {
        assert root != null;
        WSDLOperation op = root.getOperation();
        if (op != null) {
            for (WSDLFault f : op.getFaults()) {
                if (f.getName().equals(name)) {
                    this.fault = f;
                    break;
                }
            }
        }
    }
}
