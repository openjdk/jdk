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

package com.sun.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.Closeable;
import com.sun.xml.internal.ws.model.wsdl.WSDLProperties;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Engine;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.developer.JAXWSProperties;
import com.sun.xml.internal.ws.developer.WSBindingProvider;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.util.Pool;
import com.sun.xml.internal.ws.util.Pool.TubePool;
import com.sun.xml.internal.ws.util.RuntimeVersion;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Base class for stubs, which accept method invocations from
 * client applications and pass the message to a {@link Tube}
 * for processing.
 *
 * <p>
 * This class implements the management of pipe instances,
 * and most of the {@link BindingProvider} methods.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Stub implements WSBindingProvider, ResponseContextReceiver, Closeable {

    /**
     * Reuse pipelines as it's expensive to create.
     * <p>
     * Set to null when {@link #close() closed}.
     */
    private Pool<Tube> tubes;

    private final Engine engine;

    /**
     * The {@link WSServiceDelegate} object that owns us.
     */
    protected final WSServiceDelegate owner;

    /**
     * Non-null if this stub is configured to talk to an EPR.
     * <p>
     * When this field is non-null, its reference parameters are sent as out-bound headers.
     * This field can be null even when addressing is enabled, but if the addressing is
     * not enabled, this field must be null.
     * <p>
     * Unlike endpoint address, we are not letting users to change the EPR,
     * as it contains references to services and so on that we don't want to change.
     */
    protected final @Nullable WSEndpointReference endpointReference;

    protected final BindingImpl binding;

    /**
     * represents AddressingVersion on binding if enabled, otherwise null;
     */
    protected final AddressingVersion addrVersion;

    public final RequestContext requestContext = new RequestContext();

    /**
     * {@link ResponseContext} from the last synchronous operation.
     */
    private ResponseContext responseContext;
    @Nullable protected final WSDLPort wsdlPort;

    /**
     * {@link Header}s to be added to outbound {@link Packet}.
     * The contents is determined by the user.
     */
    @Nullable private volatile Header[] userOutboundHeaders;

    private final @Nullable WSDLProperties wsdlProperties;

    /**
     * @param master                 The created stub will send messages to this pipe.
     * @param binding                As a {@link BindingProvider}, this object will
     *                               return this binding from {@link BindingProvider#getBinding()}.
     * @param defaultEndPointAddress The destination of the message. The actual destination
     *                               could be overridden by {@link RequestContext}.
     * @param epr                    To create a stub that sends out reference parameters
     *                               of a specific EPR, give that instance. Otherwise null.
     *                               Its address field will not be used, and that should be given
     *                               separately as the <tt>defaultEndPointAddress</tt>.
     */
    protected Stub(WSServiceDelegate owner, Tube master, BindingImpl binding, WSDLPort wsdlPort, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
        this.owner = owner;
        this.tubes = new TubePool(master);
        this.wsdlPort = wsdlPort;
        this.binding = binding;
        addrVersion = binding.getAddressingVersion();
        // if there is an EPR, EPR's address should be used for invocation instead of default address
        if(epr != null)
            this.requestContext.setEndPointAddressString(epr.getAddress());
        else
            this.requestContext.setEndpointAddress(defaultEndPointAddress);
        this.engine = new Engine(toString());
        this.endpointReference = epr;
        wsdlProperties = (wsdlPort==null) ? null : new WSDLProperties(wsdlPort);
    }

    /**
     * Gets the port name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the port name is always
     * the same as {@link WSDLPort#getName()}, but this method
     * returns a port name even if no WSDL is available for this stub.
     */
    protected abstract @NotNull QName getPortName();

    /**
     * Gets the service name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the service name is always
     * the same as the one that's inferred from {@link WSDLPort#getOwner()},
     * but this method returns a port name even if no WSDL is available for
     * this stub.
     */
    protected final @NotNull QName getServiceName() {
        return owner.getServiceName();
    }

    /**
     * Gets the {@link Executor} to be used for asynchronous method invocations.
     * <p>
     * Note that the value this method returns may different from invocations
     * to invocations. The caller must not cache.
     *
     * @return always non-null.
     */
    public final Executor getExecutor() {
        return owner.getExecutor();
    }

    /**
     * Passes a message to a pipe for processing.
     * <p>
     * Unlike {@link Tube} instances,
     * this method is thread-safe and can be invoked from
     * multiple threads concurrently.
     *
     * @param packet         The message to be sent to the server
     * @param requestContext The {@link RequestContext} when this invocation is originally scheduled.
     *                       This must be the same object as {@link #requestContext} for synchronous
     *                       invocations, but for asynchronous invocations, it needs to be a snapshot
     *                       captured at the point of invocation, to correctly satisfy the spec requirement.
     * @param receiver       Receives the {@link ResponseContext}. Since the spec requires
     *                       that the asynchronous invocations must not update response context,
     *                       depending on the mode of invocation they have to go to different places.
     *                       So we take a setter that abstracts that away.
     */
    protected final Packet process(Packet packet, RequestContext requestContext, ResponseContextReceiver receiver) {
        {// fill in Packet
            packet.proxy = this;
            packet.handlerConfig = binding.getHandlerConfig();
            requestContext.fill(packet);
            if (wsdlProperties != null) {
                packet.addSatellite(wsdlProperties);
            }
            if (addrVersion != null) {
                // populate request WS-Addressing headers
                HeaderList headerList = packet.getMessage().getHeaders();
                headerList.fillRequestAddressingHeaders(wsdlPort, binding, packet);


                // Spec is not clear on if ReferenceParameters are to be added when addressing is not enabled,
                // but the EPR has ReferenceParameters.
                // Current approach: Add ReferenceParameters only if addressing enabled.
                if (endpointReference != null)
                    endpointReference.addReferenceParameters(packet.getMessage().getHeaders());
            }

            // to make it multi-thread safe we need to first get a stable snapshot
            Header[] hl = userOutboundHeaders;
            if(hl!=null)
                packet.getMessage().getHeaders().addAll(hl);
        }


        Pool<Tube> pool = tubes;
        if (pool == null)
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n

        Fiber fiber = engine.createFiber();
        // then send it away!
        Tube tube = pool.take();

        try {
            return fiber.runSync(tube, packet);
        } finally {
            // this allows us to capture the packet even when the call failed with an exception.
            // when the call fails with an exception it's no longer a 'reply' but it may provide some information
            // about what went wrong.

            // note that Packet can still be updated after
            // ResponseContext is created.
            Packet reply = (fiber.getPacket() == null) ? packet : fiber.getPacket();
            receiver.setResponseContext(new ResponseContext(reply));

            pool.recycle(tube);
        }
    }

    /**
     * Passes a message through a {@link Tube}line for processing. The processing happens
     * asynchronously and when the response is available, Fiber.CompletionCallback is
     * called. The processing could happen on multiple threads.
     *
     * <p>
     * Unlike {@link Tube} instances,
     * this method is thread-safe and can be invoked from
     * multiple threads concurrently.
     *
     * @param request         The message to be sent to the server
     * @param requestContext The {@link RequestContext} when this invocation is originally scheduled.
     *                       This must be the same object as {@link #requestContext} for synchronous
     *                       invocations, but for asynchronous invocations, it needs to be a snapshot
     *                       captured at the point of invocation, to correctly satisfy the spec requirement.
     * @param completionCallback Once the processing is done, the callback is invoked.
     */
    protected final void processAsync(Packet request, RequestContext requestContext, final Fiber.CompletionCallback completionCallback) {
        // fill in Packet
        request.proxy = this;
        request.handlerConfig = binding.getHandlerConfig();
        requestContext.fill(request);
        if (wsdlProperties != null) {
            request.addSatellite(wsdlProperties);
        }
        if (AddressingVersion.isEnabled(binding)) {
            if(endpointReference!=null)
                endpointReference.addReferenceParameters(request.getMessage().getHeaders());
        }

        final Pool<Tube> pool = tubes;
        if (pool == null)
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n

        Fiber fiber = engine.createFiber();
        // then send it away!
        final Tube tube = pool.take();
        fiber.start(tube, request, new Fiber.CompletionCallback() {
            public void onCompletion(@NotNull Packet response) {
                pool.recycle(tube);
                completionCallback.onCompletion(response);
            }
            public void onCompletion(@NotNull Throwable error) {
                // let's not reuse tubes as they might be in a wrong state, so not
                // calling pool.recycle()
                completionCallback.onCompletion(error);
            }
        });
    }

    public void close() {
        if (tubes != null) {
            // multi-thread safety of 'close' needs to be considered more carefully.
            // some calls might be pending while this method is invoked. Should we
            // block until they are complete, or should we abort them (but how?)
            Tube p = tubes.take();
            tubes = null;
            p.preDestroy();
        }
    }

    public final WSBinding getBinding() {
        return binding;
    }

    public final Map<String, Object> getRequestContext() {
        return requestContext.getMapView();
    }

    public final ResponseContext getResponseContext() {
        return responseContext;
    }

    public void setResponseContext(ResponseContext rc) {
        this.responseContext = rc;
    }

    public String toString() {
        return RuntimeVersion.VERSION + ": Stub for " + getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);
    }

    public final W3CEndpointReference getEndpointReference() {
        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING))
            throw new java.lang.UnsupportedOperationException(ClientMessages.UNSUPPORTED_OPERATION("BindingProvider.getEndpointReference()", "XML/HTTP Binding", "SOAP11 or SOAP12 Binding"));
        return getEndpointReference(W3CEndpointReference.class);
    }

    public final <T extends EndpointReference>
    T getEndpointReference(Class<T> clazz) {

        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING))
            throw new java.lang.UnsupportedOperationException(ClientMessages.UNSUPPORTED_OPERATION("BindingProvider.getEndpointReference(Class<T> class)", "XML/HTTP Binding", "SOAP11 or SOAP12 Binding"));

        // we need to expand WSEndpointAddress class to be able to return EPR with arbitrary address.
        if (endpointReference != null) {
            return endpointReference.toSpec(clazz);
        }
        String eprAddress = requestContext.getEndpointAddress().toString();
        QName portTypeName = null;
        String wsdlAddress = null;
        if(wsdlPort!=null) {
            portTypeName = wsdlPort.getBinding().getPortTypeName();
            wsdlAddress = eprAddress +"?wsdl";
        }
        AddressingVersion av = AddressingVersion.fromSpecClass(clazz);
        if (av == AddressingVersion.W3C) {
            // Supress writing ServiceName and EndpointName in W3C EPR,
            // Until the ns for those metadata elements is resolved.
            return new WSEndpointReference(
                    AddressingVersion.W3C,
                    eprAddress, null /*getServiceName()*/, null/*getPortName()*/, null /* portTypeName*/, null, null /*wsdlAddress*/, null).toSpec(clazz);
        } else {
            return new WSEndpointReference(
                    AddressingVersion.MEMBER,
                    eprAddress, getServiceName(), getPortName(), portTypeName, null, wsdlAddress, null).toSpec(clazz);
        }
    }

//
//
// WSBindingProvider methods
//
//
    public final void setOutboundHeaders(List<Header> headers) {
        if(headers==null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if(h==null)
                    throw new IllegalArgumentException();
            }
            userOutboundHeaders = headers.toArray(new Header[headers.size()]);
        }
    }

    public final void setOutboundHeaders(Header... headers) {
        if(headers==null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if(h==null)
                    throw new IllegalArgumentException();
            }
            Header[] hl = new Header[headers.length];
            System.arraycopy(headers,0,hl,0,headers.length);
            userOutboundHeaders = hl;
        }
    }

    public final List<Header> getInboundHeaders() {
        return Collections.unmodifiableList((HeaderList)
            responseContext.get(JAXWSProperties.INBOUND_HEADER_LIST_PROPERTY));
    }
}
