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
package com.sun.xml.internal.ws.server.provider;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.server.AsyncProvider;
import com.sun.xml.internal.ws.api.server.AsyncProviderCallback;
import com.sun.xml.internal.ws.api.server.Invoker;
import com.sun.xml.internal.ws.api.server.WSEndpoint;
import com.sun.xml.internal.ws.server.AbstractWebServiceContext;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This {@link Tube} is used to invoke the {@link AsyncProvider} endpoints.
 *
 * @author Jitendra Kotamraju
 */
class AsyncProviderInvokerTube<T> extends ProviderInvokerTube<T> {

    private static final Logger LOGGER = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.AsyncProviderInvokerTube");

    public AsyncProviderInvokerTube(Invoker invoker, ProviderArgumentsBuilder<T> argsBuilder) {
        super(invoker, argsBuilder);
    }

   /*
    * This binds the parameter for Provider endpoints and invokes the
    * invoke() method of {@linke Provider} endpoint. The return value from
    * invoke() is used to create a new {@link Message} that traverses
    * through the Pipeline to transport.
    */
    public @NotNull NextAction processRequest(@NotNull Packet request) {
        T param = argsBuilder.getParameter(request);
        AsyncProviderCallback callback = new AsyncProviderInvokerTube.AsyncProviderCallbackImpl(request);
        AsyncWebServiceContext ctxt = new AsyncWebServiceContext(getEndpoint(),request);

        AsyncProviderInvokerTube.LOGGER.fine("Invoking AsyncProvider Endpoint");
        try {
            getInvoker(request).invokeAsyncProvider(request, param, callback, ctxt);
        } catch(Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            return doThrow(e);
        }
        // Suspend the Fiber. AsyncProviderCallback will resume the Fiber after
        // it receives response.
        return doSuspend();
    }

    private class AsyncProviderCallbackImpl implements AsyncProviderCallback<T> {
        private final Packet request;
        private final Fiber fiber;

        public AsyncProviderCallbackImpl(Packet request) {
            this.request = request;
            this.fiber = Fiber.current();
        }

        public void send(@Nullable T param) {
            if (param == null) {
                if (request.transportBackChannel != null) {
                    request.transportBackChannel.close();
                }
            }
            Packet packet = argsBuilder.getResponse(request, param, getEndpoint().getPort(), getEndpoint().getBinding());
            fiber.resume(packet);
        }

        public void sendError(@NotNull Throwable t) {
            Exception e;
            if (t instanceof RuntimeException) {
                e = (RuntimeException)t;
            } else {
                e = new RuntimeException(t);
            }
            Packet packet = argsBuilder.getResponse(request, e, getEndpoint().getPort(), getEndpoint().getBinding());
            fiber.resume(packet);
        }
    }

    /**
     * The single {@link javax.xml.ws.WebServiceContext} instance injected into application.
     */
    private static final class AsyncWebServiceContext extends AbstractWebServiceContext {
        final Packet packet;

        AsyncWebServiceContext(WSEndpoint endpoint, Packet packet) {
            super(endpoint);
            this.packet = packet;
        }

        public @NotNull Packet getRequestPacket() {
            return packet;
        }
    }

    public @NotNull NextAction processResponse(@NotNull Packet response) {
        return doReturnWith(response);
    }

    public @NotNull NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException("AsyncProviderInvokerTube's processException shouldn't be called.");
    }

}
