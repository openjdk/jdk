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

package com.sun.xml.internal.ws.client;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.stream.buffer.XMLStreamBuffer;
import com.sun.xml.internal.ws.addressing.WSEPRExtension;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.ComponentFeature;
import com.sun.xml.internal.ws.api.ComponentFeature.Target;
import com.sun.xml.internal.ws.api.ComponentRegistry;
import com.sun.xml.internal.ws.api.ComponentsFeature;
import com.sun.xml.internal.ws.api.EndpointAddress;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.WSService;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.addressing.WSEndpointReference;
import com.sun.xml.internal.ws.api.client.WSPortInfo;
import com.sun.xml.internal.ws.api.message.AddressingUtils;
import com.sun.xml.internal.ws.api.message.Header;
import com.sun.xml.internal.ws.api.message.HeaderList;
import com.sun.xml.internal.ws.api.message.MessageHeaders;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.SEIModel;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Engine;
import com.sun.xml.internal.ws.api.pipe.Fiber;
import com.sun.xml.internal.ws.api.pipe.FiberContextSwitchInterceptorFactory;
import com.sun.xml.internal.ws.api.pipe.SyncStartForAsyncFeature;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.api.pipe.TubelineAssembler;
import com.sun.xml.internal.ws.api.pipe.TubelineAssemblerFactory;
import com.sun.xml.internal.ws.api.server.Container;
import com.sun.xml.internal.ws.api.server.ContainerResolver;
import com.sun.xml.internal.ws.binding.BindingImpl;
import com.sun.xml.internal.ws.developer.JAXWSProperties;
import com.sun.xml.internal.ws.developer.WSBindingProvider;
import com.sun.xml.internal.ws.model.wsdl.WSDLDirectProperties;
import com.sun.xml.internal.ws.model.wsdl.WSDLPortProperties;
import com.sun.xml.internal.ws.model.wsdl.WSDLProperties;
import com.sun.xml.internal.ws.resources.ClientMessages;
import com.sun.xml.internal.ws.util.Pool;
import com.sun.xml.internal.ws.util.Pool.TubePool;
import com.sun.xml.internal.ws.util.RuntimeVersion;
import com.sun.xml.internal.ws.wsdl.OperationDispatcher;
import com.sun.org.glassfish.gmbal.ManagedObjectManager;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.RespectBindingFeature;
import javax.xml.ws.Response;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.management.ObjectName;

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
public abstract class Stub implements WSBindingProvider, ResponseContextReceiver, ComponentRegistry  {
    /**
     * Internal flag indicating async dispatch should be used even when the
     * SyncStartForAsyncInvokeFeature is present on the binding associated
     * with a stub. There is no type associated with this property on the
     * request context. Its presence is what triggers the 'prevent' behavior.
     */
    public static final String PREVENT_SYNC_START_FOR_ASYNC_INVOKE = "com.sun.xml.internal.ws.client.StubRequestSyncStartForAsyncInvoke";

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
    protected
    @Nullable
    WSEndpointReference endpointReference;

    protected final BindingImpl binding;

    protected final WSPortInfo portInfo;

    /**
     * represents AddressingVersion on binding if enabled, otherwise null;
     */
    protected AddressingVersion addrVersion;

    public RequestContext requestContext = new RequestContext();

    private final RequestContext cleanRequestContext;

    /**
     * {@link ResponseContext} from the last synchronous operation.
     */
    private ResponseContext responseContext;
    @Nullable
    protected final WSDLPort wsdlPort;

    protected QName portname;

    /**
     * {@link Header}s to be added to outbound {@link Packet}.
     * The contents is determined by the user.
     */
    @Nullable
    private volatile Header[] userOutboundHeaders;

    private final
    @NotNull
    WSDLProperties wsdlProperties;
    protected OperationDispatcher operationDispatcher = null;
    private final
    @NotNull
    ManagedObjectManager managedObjectManager;
    private boolean managedObjectManagerClosed = false;

    private final Set<Component> components = new CopyOnWriteArraySet<Component>();

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
    @Deprecated
    protected Stub(WSServiceDelegate owner, Tube master, BindingImpl binding, WSDLPort wsdlPort, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
        this(owner, master, null, null, binding, wsdlPort, defaultEndPointAddress, epr);
    }

    /**
     * @param portname               The name of this port
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
    @Deprecated
    protected Stub(QName portname, WSServiceDelegate owner, Tube master, BindingImpl binding, WSDLPort wsdlPort, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
        this(owner, master, null, portname, binding, wsdlPort, defaultEndPointAddress, epr);
    }

    /**
     * @param portInfo               PortInfo  for this stub
     * @param binding                As a {@link BindingProvider}, this object will
     *                               return this binding from {@link BindingProvider#getBinding()}.
     * @param master                 The created stub will send messages to this pipe.
     * @param defaultEndPointAddress The destination of the message. The actual destination
     *                               could be overridden by {@link RequestContext}.
     * @param epr                    To create a stub that sends out reference parameters
     *                               of a specific EPR, give that instance. Otherwise null.
     *                               Its address field will not be used, and that should be given
     *                               separately as the <tt>defaultEndPointAddress</tt>.
     */
    protected Stub(WSPortInfo portInfo, BindingImpl binding, Tube master,EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
         this((WSServiceDelegate) portInfo.getOwner(), master, portInfo, null, binding,portInfo.getPort(), defaultEndPointAddress, epr);
    }

  /**
   * @param portInfo               PortInfo  for this stub
   * @param binding                As a {@link BindingProvider}, this object will
   *                               return this binding from {@link BindingProvider#getBinding()}.
   * @param defaultEndPointAddress The destination of the message. The actual destination
   *                               could be overridden by {@link RequestContext}.
   * @param epr                    To create a stub that sends out reference parameters
   *                               of a specific EPR, give that instance. Otherwise null.
   *                               Its address field will not be used, and that should be given
   *                               separately as the <tt>defaultEndPointAddress</tt>.
   */
  protected Stub(WSPortInfo portInfo, BindingImpl binding, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
       this(portInfo,binding,null, defaultEndPointAddress,epr);

  }

    private Stub(WSServiceDelegate owner, @Nullable Tube master, @Nullable WSPortInfo portInfo, QName portname, BindingImpl binding, @Nullable WSDLPort wsdlPort, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
        Container old = ContainerResolver.getDefault().enterContainer(owner.getContainer());
        try {
            this.owner = owner;
            this.portInfo = portInfo;
            this.wsdlPort = wsdlPort != null ? wsdlPort : (portInfo != null ? portInfo.getPort() : null);
            this.portname = portname;
            if (portname == null) {
                if (portInfo != null) {
                    this.portname = portInfo.getPortName();
                } else if (wsdlPort != null) {
                    this.portname = wsdlPort.getName();
                }
            }
            this.binding = binding;

            ComponentFeature cf = binding.getFeature(ComponentFeature.class);
            if (cf != null && Target.STUB.equals(cf.getTarget())) {
                components.add(cf.getComponent());
            }
            ComponentsFeature csf = binding.getFeature(ComponentsFeature.class);
            if (csf != null) {
                for (ComponentFeature cfi : csf.getComponentFeatures()) {
                    if (Target.STUB.equals(cfi.getTarget()))
                        components.add(cfi.getComponent());
                }
            }

            // if there is an EPR, EPR's address should be used for invocation instead of default address
            if (epr != null) {
                this.requestContext.setEndPointAddressString(epr.getAddress());
            } else {
                this.requestContext.setEndpointAddress(defaultEndPointAddress);
            }
            this.engine = new Engine(getStringId(), owner.getContainer(), owner.getExecutor());
            this.endpointReference = epr;
            wsdlProperties = (wsdlPort == null) ? new WSDLDirectProperties(owner.getServiceName(), portname) : new WSDLPortProperties(wsdlPort);

            this.cleanRequestContext = this.requestContext.copy();

            // ManagedObjectManager MUST be created before the pipeline
            // is constructed.

            managedObjectManager = new MonitorRootClient(this).createManagedObjectManager(this);

            if (master != null) {
                this.tubes = new TubePool(master);
            } else {
                this.tubes = new TubePool(createPipeline(portInfo, binding));
            }

            addrVersion = binding.getAddressingVersion();

            // This needs to happen after createPipeline.
            // TBD: Check if it needs to happen outside the Stub constructor.
            managedObjectManager.resumeJMXRegistration();
        } finally {
            ContainerResolver.getDefault().exitContainer(old);
        }
    }

    /**
     * Creates a new pipeline for the given port name.
     */
    private Tube createPipeline(WSPortInfo portInfo, WSBinding binding) {
        //Check all required WSDL extensions are understood
        checkAllWSDLExtensionsUnderstood(portInfo, binding);
        SEIModel seiModel = null;
        Class sei = null;
        if (portInfo instanceof SEIPortInfo) {
                SEIPortInfo sp = (SEIPortInfo) portInfo;
            seiModel = sp.model;
            sei = sp.sei;
        }
        BindingID bindingId = portInfo.getBindingId();

        TubelineAssembler assembler = TubelineAssemblerFactory.create(
                Thread.currentThread().getContextClassLoader(), bindingId, owner.getContainer());
        if (assembler == null) {
            throw new WebServiceException("Unable to process bindingID=" + bindingId); // TODO: i18n
        }
        return assembler.createClient(
                new ClientTubeAssemblerContext(
                        portInfo.getEndpointAddress(),
                        portInfo.getPort(),
                        this, binding, owner.getContainer(), ((BindingImpl) binding).createCodec(), seiModel, sei));
    }

    public WSDLPort getWSDLPort() {
        return wsdlPort;
    }

    public WSService getService() {
        return owner;
    }

    public Pool<Tube> getTubes() {
        return tubes;
    }

    /**
     * Checks only if RespectBindingFeature is enabled
     * checks if all required wsdl extensions in the
     * corresponding wsdl:Port are understood when RespectBindingFeature is enabled.
     * @throws WebServiceException
     *      when any wsdl extension that has wsdl:required=true is not understood
     */
    private static void checkAllWSDLExtensionsUnderstood(WSPortInfo port, WSBinding binding) {
        if (port.getPort() != null && binding.isFeatureEnabled(RespectBindingFeature.class)) {
            port.getPort().areRequiredExtensionsUnderstood();
        }
    }

    @Override
    public WSPortInfo getPortInfo() {
        return portInfo;
    }

    /**
     * Nullable when there is no associated WSDL Model
     * @return
     */
    public
    @Nullable
    OperationDispatcher getOperationDispatcher() {
        if (operationDispatcher == null && wsdlPort != null) {
            operationDispatcher = new OperationDispatcher(wsdlPort, binding, null);
        }
        return operationDispatcher;
    }

    /**
     * Gets the port name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the port name is always
     * the same as {@link WSDLPort#getName()}, but this method
     * returns a port name even if no WSDL is available for this stub.
     */
    protected abstract
    @NotNull
    QName getPortName();

    /**
     * Gets the service name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the service name is always
     * the same as the one that's inferred from {@link WSDLPort#getOwner()},
     * but this method returns a port name even if no WSDL is available for
     * this stub.
     */
    protected final
    @NotNull
    QName getServiceName() {
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
        packet.isSynchronousMEP = true;
        packet.component = this;
        configureRequestPacket(packet, requestContext);
        Pool<Tube> pool = tubes;
        if (pool == null) {
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n
        }

        Fiber fiber = engine.createFiber();
        configureFiber(fiber);

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

    private void configureRequestPacket(Packet packet, RequestContext requestContext) {
        // fill in Packet
        packet.proxy = this;
        packet.handlerConfig = binding.getHandlerConfig();

        // to make it multi-thread safe we need to first get a stable snapshot
        Header[] hl = userOutboundHeaders;
        if (hl != null) {
            MessageHeaders mh = packet.getMessage().getHeaders();
            for (Header h : hl) {
                mh.add(h);
            }
        }

        requestContext.fill(packet, (binding.getAddressingVersion() != null));
        packet.addSatellite(wsdlProperties);

        if (addrVersion != null) {
            // populate request WS-Addressing headers
            MessageHeaders headerList = packet.getMessage().getHeaders();
            AddressingUtils.fillRequestAddressingHeaders(headerList, wsdlPort, binding, packet);


            // Spec is not clear on if ReferenceParameters are to be added when addressing is not enabled,
            // but the EPR has ReferenceParameters.
            // Current approach: Add ReferenceParameters only if addressing enabled.
            if (endpointReference != null) {
                endpointReference.addReferenceParametersToList(packet.getMessage().getHeaders());
            }
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
     * @param receiver       The {@link Response} implementation
     * @param request         The message to be sent to the server
     * @param requestContext The {@link RequestContext} when this invocation is originally scheduled.
     *                       This must be the same object as {@link #requestContext} for synchronous
     *                       invocations, but for asynchronous invocations, it needs to be a snapshot
     *                       captured at the point of invocation, to correctly satisfy the spec requirement.
     * @param completionCallback Once the processing is done, the callback is invoked.
     */
    protected final void processAsync(AsyncResponseImpl<?> receiver, Packet request, RequestContext requestContext, final Fiber.CompletionCallback completionCallback) {
        // fill in Packet
        request.component = this;
        configureRequestPacket(request, requestContext);

        final Pool<Tube> pool = tubes;
        if (pool == null) {
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n
        }

        final Fiber fiber = engine.createFiber();
        configureFiber(fiber);

        receiver.setCancelable(fiber);

        // check race condition on cancel
        if (receiver.isCancelled()) {
            return;
        }

        FiberContextSwitchInterceptorFactory fcsif = owner.getSPI(FiberContextSwitchInterceptorFactory.class);
        if (fcsif != null) {
            fiber.addInterceptor(fcsif.create());
        }

        // then send it away!
        final Tube tube = pool.take();

        Fiber.CompletionCallback fiberCallback = new Fiber.CompletionCallback() {
            @Override
            public void onCompletion(@NotNull Packet response) {
                pool.recycle(tube);
                completionCallback.onCompletion(response);
            }

            @Override
            public void onCompletion(@NotNull Throwable error) {
                // let's not reuse tubes as they might be in a wrong state, so not
                // calling pool.recycle()
                completionCallback.onCompletion(error);
            }
        };

        // Check for SyncStartForAsyncInvokeFeature

        fiber.start(tube, request, fiberCallback,
                        getBinding().isFeatureEnabled(SyncStartForAsyncFeature.class) &&
                        !requestContext.containsKey(PREVENT_SYNC_START_FOR_ASYNC_INVOKE));
    }

    protected void configureFiber(Fiber fiber) {
        // no-op in the base class, but can be used by derived classes to configure the Fiber prior
        // to invocation
    }

    private static final Logger monitoringLogger = Logger.getLogger(com.sun.xml.internal.ws.util.Constants.LoggingDomain + ".monitoring");

    @Override
    public void close() {
        TubePool tp = (TubePool) tubes;
        if (tp != null) {
            // multi-thread safety of 'close' needs to be considered more carefully.
            // some calls might be pending while this method is invoked. Should we
            // block until they are complete, or should we abort them (but how?)
            Tube p = tp.takeMaster();
            p.preDestroy();
            tubes = null;
        }
        if (!managedObjectManagerClosed) {
            try {
                final ObjectName name = managedObjectManager.getObjectName(managedObjectManager.getRoot());
                // The name is null when the MOM is a NOOP.
                if (name != null) {
                    monitoringLogger.log(Level.INFO, "Closing Metro monitoring root: {0}", name);
                }
                managedObjectManager.close();
            } catch (java.io.IOException e) {
                monitoringLogger.log(Level.WARNING, "Ignoring error when closing Managed Object Manager", e);
            }
            managedObjectManagerClosed = true;
        }
    }

    @Override
    public final WSBinding getBinding() {
        return binding;
    }

    @Override
    public final Map<String, Object> getRequestContext() {
        return requestContext.asMap();
    }

    public void resetRequestContext() {
        requestContext = cleanRequestContext.copy();
    }

    @Override
    public final ResponseContext getResponseContext() {
        return responseContext;
    }

    @Override
    public void setResponseContext(ResponseContext rc) {
        this.responseContext = rc;
    }

    private String getStringId() {
        return RuntimeVersion.VERSION + ": Stub for " + getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);
    }

    @Override
    public String toString() {
        return getStringId();
    }

    @Override
    public final WSEndpointReference getWSEndpointReference() {
        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING)) {
            throw new java.lang.UnsupportedOperationException(
                        ClientMessages.UNSUPPORTED_OPERATION("BindingProvider.getEndpointReference(Class<T> class)", "XML/HTTP Binding", "SOAP11 or SOAP12 Binding")
                    );
        }

        if (endpointReference != null) {
            return endpointReference;
        }

        String eprAddress = requestContext.getEndpointAddress().toString();
        QName portTypeName = null;
        String wsdlAddress = null;
        List<WSEndpointReference.EPRExtension> wsdlEPRExtensions = new ArrayList<WSEndpointReference.EPRExtension>();
        if (wsdlPort != null) {
            portTypeName = wsdlPort.getBinding().getPortTypeName();
            wsdlAddress = eprAddress + "?wsdl";

            //gather EPRExtensions specified in WSDL.
            try {
                WSEndpointReference wsdlEpr = wsdlPort.getEPR();
                if (wsdlEpr != null) {
                    for (WSEndpointReference.EPRExtension extnEl : wsdlEpr.getEPRExtensions()) {
                        wsdlEPRExtensions.add(new WSEPRExtension(
                                XMLStreamBuffer.createNewBufferFromXMLStreamReader(extnEl.readAsXMLStreamReader()), extnEl.getQName()));
                    }
                }

            } catch (XMLStreamException ex) {
                throw new WebServiceException(ex);
            }
        }
        AddressingVersion av = AddressingVersion.W3C;
        this.endpointReference = new WSEndpointReference(
                av, eprAddress, getServiceName(), getPortName(), portTypeName, null, wsdlAddress, null, wsdlEPRExtensions, null);

        return this.endpointReference;
    }


    @Override
    public final W3CEndpointReference getEndpointReference() {
        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING)) {
            throw new java.lang.UnsupportedOperationException(
                        ClientMessages.UNSUPPORTED_OPERATION("BindingProvider.getEndpointReference()", "XML/HTTP Binding", "SOAP11 or SOAP12 Binding"));
        }
        return getEndpointReference(W3CEndpointReference.class);
    }

    @Override
    public final <T extends EndpointReference> T getEndpointReference(Class<T> clazz) {
        return getWSEndpointReference().toSpec(clazz);
    }

    public
    @NotNull
    @Override
    ManagedObjectManager getManagedObjectManager() {
        return managedObjectManager;
    }

    //
//
// WSBindingProvider methods
//
//
    @Override
    public final void setOutboundHeaders(List<Header> headers) {
        if (headers == null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if (h == null) {
                    throw new IllegalArgumentException();
                }
            }
            userOutboundHeaders = headers.toArray(new Header[headers.size()]);
        }
    }

    @Override
    public final void setOutboundHeaders(Header... headers) {
        if (headers == null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if (h == null) {
                    throw new IllegalArgumentException();
                }
            }
            Header[] hl = new Header[headers.length];
            System.arraycopy(headers, 0, hl, 0, headers.length);
            userOutboundHeaders = hl;
        }
    }

    @Override
    public final List<Header> getInboundHeaders() {
        return Collections.unmodifiableList(((MessageHeaders)
                responseContext.get(JAXWSProperties.INBOUND_HEADER_LIST_PROPERTY)).asList());
    }

    @Override
    public final void setAddress(String address) {
        requestContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, address);
    }

    @Override
    public <S> S getSPI(Class<S> spiType) {
        for (Component c : components) {
            S s = c.getSPI(spiType);
            if (s != null) {
                return s;
            }
        }
        return owner.getSPI(spiType);
    }

    @Override
    public Set<Component> getComponents() {
        return components;
    }
}
