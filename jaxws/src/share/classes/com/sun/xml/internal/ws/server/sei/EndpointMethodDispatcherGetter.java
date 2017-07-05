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

package com.sun.xml.internal.ws.server.sei;

import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the list of {@link EndpointMethodDispatcher}s for {@link SEIInvokerTube}.
 * a request {@link Packet}. If WS-Addressing is enabled on the endpoint, then
 * only {@link ActionBasedDispatcher} is added to the list. Otherwise,
 * {@link PayloadQNameBasedDispatcher} is added to the list.
 *
 * <p>
 * {@link Message} payload's QName to obtain the handler. If no handler is
 * registered corresponding to that QName, then uses Action Message
 * Addressing Property value to get the handler.
 *
 * @author Arun Gupta
 */
final class EndpointMethodDispatcherGetter {
    private final List<EndpointMethodDispatcher> dispatcherList;

    EndpointMethodDispatcherGetter(AbstractSEIModelImpl model, WSBinding binding, SEIInvokerTube invokerTube) {
        dispatcherList = new ArrayList<EndpointMethodDispatcher>();

        if (binding.getAddressingVersion() != null) {
            dispatcherList.add(new ActionBasedDispatcher(model, binding, invokerTube));
        }

        // even when action based dispatching is in place,
        // we still need this because clients are alowed not to use addressing headers
        dispatcherList.add(new PayloadQNameBasedDispatcher(model, binding, invokerTube));
        dispatcherList.add(new SOAPActionBasedDispatcher(model, binding, invokerTube));
    }

    List<EndpointMethodDispatcher> getDispatcherList() {
        return dispatcherList;
    }
}
