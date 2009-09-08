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
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.server.Invoker;
import com.sun.xml.internal.ws.client.sei.MethodHandler;
import com.sun.xml.internal.ws.model.AbstractSEIModelImpl;
import com.sun.xml.internal.ws.server.InvokerTube;
import com.sun.xml.internal.ws.resources.ServerMessages;
import com.sun.xml.internal.ws.fault.SOAPFaultBuilder;

import java.util.List;
import java.text.MessageFormat;

/**
 * This pipe is used to invoke SEI based endpoints.
 *
 * @author Jitendra Kotamraju
 */
public class SEIInvokerTube extends InvokerTube {

    /**
     * For each method on the port interface we have
     * a {@link MethodHandler} that processes it.
     */
    private final SOAPVersion soapVersion;
    private final WSBinding binding;
    private final AbstractSEIModelImpl model;
    private final List<EndpointMethodDispatcher> dispatcherList;

    public SEIInvokerTube(AbstractSEIModelImpl model,Invoker invoker, WSBinding binding) {
        super(invoker);
        this.soapVersion = binding.getSOAPVersion();
        this.binding = binding;
        this.model = model;
        EndpointMethodDispatcherGetter methodDispatcherGetter = new EndpointMethodDispatcherGetter(model, binding, this);
        dispatcherList = methodDispatcherGetter.getDispatcherList();
    }

    /**
     * This binds the parameters for SEI endpoints and invokes the endpoint method. The
     * return value, and response Holder arguments are used to create a new {@link Message}
     * that traverses through the Pipeline to transport.
     */
    public @NotNull NextAction processRequest(@NotNull Packet req) {
        for (EndpointMethodDispatcher dispatcher : dispatcherList) {
            EndpointMethodHandler handler;
            try {
                handler = dispatcher.getEndpointMethodHandler(req);
            } catch(DispatchException e) {
                return doReturnWith(req.createServerResponse(e.fault, model.getPort(), null, binding));
            }
            if (handler != null) {
                Packet res = handler.invoke(req);
                assert res!=null;
                return doReturnWith(res);
            }
        }
        String err = MessageFormat.format("Request=[SOAPAction={0},Payload='{'{1}'}'{2}]",
                req.soapAction,req.getMessage().getPayloadNamespaceURI(),
                req.getMessage().getPayloadLocalPart());
        String faultString = ServerMessages.DISPATCH_CANNOT_FIND_METHOD(err);
        Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(
            binding.getSOAPVersion(), faultString, binding.getSOAPVersion().faultCodeClient);
        return doReturnWith(req.createServerResponse(faultMsg, model.getPort(), null, binding));
    }

    public @NotNull NextAction processResponse(@NotNull Packet response) {
        throw new IllegalStateException("InovkerPipe's processResponse shouldn't be called.");
    }

    public @NotNull NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException("InovkerPipe's processException shouldn't be called.");
    }

}
