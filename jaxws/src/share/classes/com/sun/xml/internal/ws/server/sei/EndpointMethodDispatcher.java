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
/*
 * $Id: EndpointMethodDispatcher.java,v 1.1.2.5 2006/11/10 00:55:03 kohsuke Exp $
 */

package com.sun.xml.internal.ws.server.sei;

import com.sun.xml.internal.ws.api.message.Packet;

/**
 * This interface needs to be implemented if a new dispatching
 * mechanism needs to be plugged in. The dispatcher is plugged in the
 * constructor of {@link EndpointMethodDispatcherGetter}.
 *
 * @author Arun Gupta
 * @see EndpointMethodDispatcherGetter
 */
interface EndpointMethodDispatcher {
    /**
     * Returns the {@link EndpointMethodHandler} for the <code>request</code>
     * {@link Packet}.
     *
     * @param request request packet
     * @return
     *      non-null {@link EndpointMethodHandler} that will route the request packet.
     *      null to indicate that the request packet be processed by the next available
     *      {@link EndpointMethodDispatcher}.
     * @throws DispatchException
     *      If the request is invalid, and processing shall be aborted with a specific fault.
     */
    EndpointMethodHandler getEndpointMethodHandler(Packet request) throws DispatchException;
}
