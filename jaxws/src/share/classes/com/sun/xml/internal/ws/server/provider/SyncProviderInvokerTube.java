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
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.NextAction;
import com.sun.xml.internal.ws.api.server.Invoker;

import javax.xml.ws.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This tube is used to invoke the {@link Provider} endpoints.
 *
 * @author Jitendra Kotamraju
 */
class SyncProviderInvokerTube<T> extends ProviderInvokerTube<T> {

    private static final Logger LOGGER = Logger.getLogger(
        com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".server.SyncProviderInvokerTube");

    public SyncProviderInvokerTube(Invoker invoker, ProviderArgumentsBuilder<T> argsBuilder) {
        super(invoker, argsBuilder);
    }

    /*
    * This binds the parameter for Provider endpoints and invokes the
    * invoke() method of {@linke Provider} endpoint. The return value from
    * invoke() is used to create a new {@link Message} that traverses
    * through the Pipeline to transport.
    */
    public NextAction processRequest(Packet request) {
        WSDLPort port = getEndpoint().getPort();
        WSBinding binding = getEndpoint().getBinding();
        T param = argsBuilder.getParameter(request);

        LOGGER.fine("Invoking Provider Endpoint");

        T returnValue;
        try {
            returnValue = getInvoker(request).invokeProvider(request, param);
        } catch(Exception e) {
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
            Packet response = argsBuilder.getResponse(request,e,port,binding);
            return doReturnWith(response);
        }
        if (returnValue == null) {
            // Oneway. Send response code immediately for transports(like HTTP)
            // Don't do this above, since close() may generate some exceptions
            if (request.transportBackChannel != null) {
                request.transportBackChannel.close();
            }
        }
        Packet response = argsBuilder.getResponse(request,returnValue,port,binding);
        return doReturnWith(response);
    }

    public NextAction processResponse(Packet response) {
        throw new IllegalStateException("InovkerPipe's processResponse shouldn't be called.");
    }

    public NextAction processException(@NotNull Throwable t) {
        throw new IllegalStateException("InovkerPipe's processException shouldn't be called.");
    }

}
