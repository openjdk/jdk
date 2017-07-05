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

import com.oracle.webservices.internal.api.message.BasePropertySet;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;

import javax.xml.namespace.QName;
import javax.xml.ws.handler.MessageContext;


import org.xml.sax.InputSource;

/**
 * Properties exposed from {@link WSDLPort} for {@link MessageContext}.
 * Donot add this satellite if {@link WSDLPort} is null.
 *
 * @author Jitendra Kotamraju
 */
public abstract class WSDLProperties extends BasePropertySet {

    private static final PropertyMap model;
    static {
        model = parse(WSDLProperties.class);
    }

    private final @Nullable SEIModel seiModel;

    protected WSDLProperties(@Nullable SEIModel seiModel) {
        this.seiModel = seiModel;
    }

    @Property(MessageContext.WSDL_SERVICE)
    public abstract QName getWSDLService();

    @Property(MessageContext.WSDL_PORT)
    public abstract QName getWSDLPort();

    @Property(MessageContext.WSDL_INTERFACE)
    public abstract QName getWSDLPortType();

    @Property(MessageContext.WSDL_DESCRIPTION)
    public InputSource getWSDLDescription() {
        return seiModel != null ? new InputSource(seiModel.getWSDLLocation()) : null;
    }

    @Override
    protected PropertyMap getPropertyMap() {
        return model;
    }

}
