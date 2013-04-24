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

package com.sun.xml.internal.ws.binding;

import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.client.HandlerConfiguration;
import com.sun.xml.internal.ws.resources.ClientMessages;

import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceFeature;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.http.HTTPBinding;
import java.util.Collections;
import java.util.List;

/**
 * @author WS Development Team
 */
public class HTTPBindingImpl extends BindingImpl implements HTTPBinding {

    /**
     * Use {@link BindingImpl#create(BindingID)} to create this.
     */
    HTTPBindingImpl() {
        this(EMPTY_FEATURES);
    }

    HTTPBindingImpl(WebServiceFeature ... features) {
        super(BindingID.XML_HTTP, features);
    }

    /**
     * This method separates the logical and protocol handlers and
     * sets the HandlerConfiguration.
     * Only logical handlers are allowed with HTTPBinding.
     * Setting SOAPHandlers throws WebServiceException
     */
    public void setHandlerChain(List<Handler> chain) {
        for (Handler handler : chain) {
            if (!(handler instanceof LogicalHandler)) {
                throw new WebServiceException(ClientMessages.NON_LOGICAL_HANDLER_SET(handler.getClass()));
            }
        }
        setHandlerConfig(new HandlerConfiguration(Collections.<String>emptySet(), chain));
    }
}
