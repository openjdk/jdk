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

package com.sun.xml.internal.ws.wsdl.parser;

import com.sun.xml.internal.ws.api.model.wsdl.editable.EditableWSDLModel;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.wsdl.parser.WSDLParserExtensionContext;
import com.sun.xml.internal.ws.api.policy.PolicyResolver;

/**
 * Provides implementation of {@link WSDLParserExtensionContext}
 *
 * @author Vivek Pandey
 * @author Fabian Ritzmann
 */
final class WSDLParserExtensionContextImpl implements WSDLParserExtensionContext {
    private final boolean isClientSide;
    private final EditableWSDLModel wsdlModel;
    private final Container container;
    private final PolicyResolver policyResolver;

    /**
     * Construct {@link WSDLParserExtensionContextImpl} with information that whether its on client side
     * or server side.
     */
    protected WSDLParserExtensionContextImpl(EditableWSDLModel model, boolean isClientSide, Container container, PolicyResolver policyResolver) {
        this.wsdlModel = model;
        this.isClientSide = isClientSide;
        this.container = container;
        this.policyResolver = policyResolver;
    }

    public boolean isClientSide() {
        return isClientSide;
    }

    public EditableWSDLModel getWSDLModel() {
        return wsdlModel;
    }

    public Container getContainer() {
        return this.container;
    }

    public PolicyResolver getPolicyResolver() {
        return policyResolver;
    }
}
