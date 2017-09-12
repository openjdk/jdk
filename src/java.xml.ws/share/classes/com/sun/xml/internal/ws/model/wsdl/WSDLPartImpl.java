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

import com.sun.xml.internal.ws.api.model.ParameterBinding;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPart;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPartDescriptor;
import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLPart;

import javax.xml.stream.XMLStreamReader;

/**
 * Implementation of {@link WSDLPart}
 *
 * @author Vivek Pandey
 */
public final class WSDLPartImpl extends AbstractObjectImpl implements EditableWSDLPart {
    private final String name;
    private ParameterBinding binding;
    private int index;
    private final WSDLPartDescriptor descriptor;

    public WSDLPartImpl(XMLStreamReader xsr, String partName, int index, WSDLPartDescriptor descriptor) {
        super(xsr);
        this.name = partName;
        this.binding = ParameterBinding.UNBOUND;
        this.index = index;
        this.descriptor = descriptor;

    }

    public String getName() {
        return name;
    }

    public ParameterBinding getBinding() {
        return binding;
    }

    public void setBinding(ParameterBinding binding) {
        this.binding = binding;
    }

    public int getIndex() {
        return index;
    }

    //need to set the index in case of rpclit to reorder the body parts
    public void setIndex(int index){
        this.index = index;
    }

    public WSDLPartDescriptor getDescriptor() {
        return descriptor;
    }
}
