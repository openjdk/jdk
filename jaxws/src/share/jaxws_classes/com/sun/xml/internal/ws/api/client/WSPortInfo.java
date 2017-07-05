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

package com.sun.xml.internal.ws.api.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.policy.PolicyMap;

import javax.xml.ws.handler.PortInfo;

/**
 * JAX-WS RI's extension to {@link PortInfo}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface WSPortInfo extends PortInfo {
    /**
     * Returns {@link WSService} object that owns this port.
     */
    @NotNull WSService getOwner();

    /**
     * Returns the same information as {@link #getBindingID()}
     * but in a strongly-typed fashion
     */
    @NotNull BindingID getBindingId();

    /**
     * Gets the endpoint address of this port.
     */
    @NotNull EndpointAddress getEndpointAddress();

    /**
     * Gets the {@link WSDLPort} object that represents this port,
     * if {@link WSService} is configured with WSDL. Otherwise null.
     */
    @Nullable WSDLPort getPort();

    /**
     * Gives the PolicMap that captures the Policy for the PortInfo
     *
     * @return PolicyMap
     *
     * @deprecated
     * Do not use this method as the PolicyMap API is not final yet and might change in next few months.
     */

    public PolicyMap getPolicyMap();
}
