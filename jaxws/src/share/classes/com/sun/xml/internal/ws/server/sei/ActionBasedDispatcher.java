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

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Messages;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.model.JavaMethodImpl;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link EndpointMethodDispatcher} that uses
 * WS-Addressing Action Message Addressing Property, <code>wsa:Action</code>,
 * as the key for dispatching.
 * <p/>
 * A map of all wsa:Actions on the port and the corresponding {@link EndpointMethodHandler}
 * is initialized in the constructor. The wsa:Action value is extracted from
 * the request {@link Packet} and used as the key to return the correct
 * handler.
 *
 * @author Arun Gupta
 */
final class ActionBasedDispatcher implements EndpointMethodDispatcher {
    private final WSBinding binding;
    private final Map<String, EndpointMethodHandler> actionMethodHandlers;
    private final @NotNull AddressingVersion av;

    public ActionBasedDispatcher(AbstractSEIModelImpl model, WSBinding binding, SEIInvokerTube invokerTube) {
        this.binding = binding;
        assert binding.getAddressingVersion()!=null;    // this dispatcher can be only used when addressing is on.
        av = binding.getAddressingVersion();
        actionMethodHandlers = new HashMap<String, EndpointMethodHandler>();

        for( JavaMethodImpl m : model.getJavaMethods() ) {
            EndpointMethodHandler handler = new EndpointMethodHandler(invokerTube,m,binding);
            String action = m.getInputAction();
            //first look at annotations and then in wsdlmodel
            if(action != null && !action.equals("")) {
                actionMethodHandlers.put(action, handler);
            } else {
                action = m.getOperation().getOperation().getInput().getAction();
                if (action != null)
                    actionMethodHandlers.put(action, handler);
            }
        }
    }

    public EndpointMethodHandler getEndpointMethodHandler(Packet request) throws DispatchException {

        HeaderList hl = request.getMessage().getHeaders();

        String action = hl.getAction(av, binding.getSOAPVersion());

        if (action == null)
            // this message doesn't contain addressing headers, which is legal.
            // this happens when the server is capable of processing addressing but the client didn't send them
            return null;

        EndpointMethodHandler h = actionMethodHandlers.get(action);
        if (h != null)
            return h;

        // invalid action header
        Message result = Messages.create(action, av, binding.getSOAPVersion());

        throw new DispatchException(result);
    }
}
