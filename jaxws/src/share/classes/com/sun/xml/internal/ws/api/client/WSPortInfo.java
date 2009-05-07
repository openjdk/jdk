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

package com.sun.xml.internal.ws.api.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;

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
}
