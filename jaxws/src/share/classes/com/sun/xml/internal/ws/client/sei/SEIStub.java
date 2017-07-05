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

package com.sun.xml.internal.ws.client.sei;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.Headers;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.MEP;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.client.RequestContext;
import com.sun.xml.internal.ws.client.ResponseContextReceiver;
import com.sun.xml.internal.ws.client.Stub;
import com.sun.xml.internal.ws.client.WSServiceDelegate;
import com.sun.xml.internal.ws.model.JavaMethodImpl;
import com.sun.xml.internal.ws.model.SOAPSEIModel;

import javax.xml.namespace.QName;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Stub} that handles method invocations
 * through a strongly-typed endpoint interface.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SEIStub extends Stub implements InvocationHandler {
    public SEIStub(WSServiceDelegate owner, BindingImpl binding, SOAPSEIModel seiModel, Tube master, WSEndpointReference epr) {
        super(owner,master, binding, seiModel.getPort(), seiModel.getPort().getAddress(),epr);
        this.seiModel = seiModel;
        this.soapVersion = binding.getSOAPVersion();

        Map<WSDLBoundOperation, JavaMethodImpl> syncs = new HashMap<WSDLBoundOperation, JavaMethodImpl>();

        // fill in methodHandlers.
        // first fill in sychronized versions
        for (JavaMethodImpl m : seiModel.getJavaMethods()) {
            if (!m.getMEP().isAsync) {
                SyncMethodHandler handler = new SyncMethodHandler(this, m);
                syncs.put(m.getOperation(), m);
                methodHandlers.put(m.getMethod(), handler);
            }
        }

        for (JavaMethodImpl jm : seiModel.getJavaMethods()) {
            JavaMethodImpl sync = syncs.get(jm.getOperation());
            if (jm.getMEP() == MEP.ASYNC_CALLBACK) {
                Method m = jm.getMethod();
                CallbackMethodHandler handler = new CallbackMethodHandler(
                        this, jm, sync, m.getParameterTypes().length - 1);
                methodHandlers.put(m, handler);
            }
            if (jm.getMEP() == MEP.ASYNC_POLL) {
                Method m = jm.getMethod();
                PollingMethodHandler handler = new PollingMethodHandler(this, jm, sync);
                methodHandlers.put(m, handler);
            }
        }
    }

    public final SOAPSEIModel seiModel;

    public final SOAPVersion soapVersion;


    /**
     * For each method on the port interface we have
     * a {@link MethodHandler} that processes it.
     */
    private final Map<Method, MethodHandler> methodHandlers = new HashMap<Method, MethodHandler>();

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodHandler handler = methodHandlers.get(method);
        if (handler != null) {
            return handler.invoke(proxy, args);
        } else {
            // we handle the other method invocations by ourselves
            try {
                return method.invoke(this, args);
            } catch (IllegalAccessException e) {
                // impossible
                throw new AssertionError(e);
            } catch (IllegalArgumentException e) {
                throw new AssertionError(e);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    public final Packet doProcess(Packet request, RequestContext rc, ResponseContextReceiver receiver) {
        return super.process(request, rc, receiver);
    }

    public final void doProcessAsync(Packet request, RequestContext rc, Fiber.CompletionCallback callback) {
        super.processAsync(request, rc, callback);
    }

    protected final @NotNull QName getPortName() {
        return wsdlPort.getName();
    }


    public void setOutboundHeaders(Object... headers) {
        if(headers==null)
            throw new IllegalArgumentException();
        Header[] hl = new Header[headers.length];
        for( int i=0; i<hl.length; i++ ) {
            if(headers[i]==null)
                throw new IllegalArgumentException();
            hl[i] = Headers.create(seiModel.getJAXBContext(),headers[i]);
        }
        super.setOutboundHeaders(hl);
    }
}
