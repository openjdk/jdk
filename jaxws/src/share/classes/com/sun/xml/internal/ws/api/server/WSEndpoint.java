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

package com.sun.xml.internal.ws.api.server;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.BindingID;
import com.sun.xml.internal.ws.api.WSBinding;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.internal.ws.api.pipe.Codec;
import com.sun.xml.internal.ws.api.pipe.Engine;
import com.sun.xml.internal.ws.api.pipe.FiberContextSwitchInterceptor;
import com.sun.xml.internal.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.xml.internal.ws.api.pipe.Tube;
import com.sun.xml.internal.ws.server.EndpointFactory;
import com.sun.xml.internal.ws.util.xml.XmlUtil;
import org.xml.sax.EntityResolver;

import javax.xml.namespace.QName;
import javax.xml.ws.Binding;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Root object that hosts the {@link Packet} processing code
 * at the server.
 *
 * <p>
 * One instance of {@link WSEndpoint} is created for each deployed service
 * endpoint. A hosted service usually handles multiple concurrent
 * requests. To do this efficiently, an endpoint handles incoming
 * {@link Packet} through {@link PipeHead}s, where many copies can be created
 * for each endpoint.
 *
 * <p>
 * Each {@link PipeHead} is thread-unsafe, and request needs to be
 * serialized. A {@link PipeHead} represents a sizable resource
 * (in particular a whole pipeline), so the caller is expected to
 * reuse them and avoid excessive allocations as much as possible.
 * Making {@link PipeHead}s thread-unsafe allow the JAX-WS RI internal to
 * tie thread-local resources to {@link PipeHead}, and reduce the total
 * resource management overhead.
 *
 * <p>
 * To abbreviate this resource management (and for a few other reasons),
 * JAX-WS RI provides {@link Adapter} class. If you are hosting a JAX-WS
 * service, you'll most likely want to send requests to {@link WSEndpoint}
 * through {@link Adapter}.
 *
 * <p>
 * {@link WSEndpoint} is ready to handle {@link Packet}s as soon as
 * it's created. No separate post-initialization step is necessary.
 * However, to comply with the JAX-WS spec requirement, the caller
 * is expected to call the {@link #dispose()} method to allow an
 * orderly shut-down of a hosted service.
 *
 *
 *
 * <h3>Objects Exposed From Endpoint</h3>
 * <p>
 * {@link WSEndpoint} exposes a series of information that represents
 * how an endpoint is configured to host a service. See the getXXX methods
 * for more details.
 *
 *
 *
 * <h3>Implementation Notes</h3>
 * <p>
 * {@link WSEndpoint} owns a {@link WSWebServiceContext} implementation.
 * But a bulk of the work is delegated to {@link WebServiceContextDelegate},
 * which is passed in as a parameter to {@link PipeHead#process(Packet, WebServiceContextDelegate, TransportBackChannel)}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WSEndpoint<T> {

    /**
     * Gets the Endpoint's codec that is used to encode/decode {@link Message}s. This is a
     * copy of the master codec and it shouldn't be shared across two requests running
     * concurrently(unless it is stateless).
     *
     * @return codec to encode/decode
     */
    public abstract @NotNull Codec createCodec();

    /**
     * Gets the application endpoint's serviceName. It could be got from DD or annotations
     *
     * @return same as wsdl:service QName if WSDL exists or generated
     */
    public abstract @NotNull QName getServiceName();

    /**
     * Gets the application endpoint's portName. It could be got from DD or annotations
     *
     * @return same as wsdl:port QName if WSDL exists or generated
     */
    public abstract @NotNull QName getPortName();

    /**
     * Gets the application endpoint {@link Class} that eventually serves the request.
     *
     * <p>
     * This is the same value given to the {@link #create} method.
     */
    public abstract @NotNull Class<T> getImplementationClass();

    /**
     * Represents the binding for which this {@link WSEndpoint}
     * is created for.
     *
     * @return
     *      always same object.
     */
    public abstract @NotNull WSBinding getBinding();

    /**
     * Gets the {@link Container} object.
     *
     * <p>
     * The components inside {@link WSEndpoint} uses this reference
     * to communicate with the hosting environment.
     *
     * @return
     *      always same object. If no "real" {@link Container} instance
     *      is given, {@link Container#NONE} will be returned.
     */
    public abstract @NotNull Container getContainer();

    /**
     * Gets the port that this endpoint is serving.
     *
     * <p>
     * A service is not required to have a WSDL, and when it doesn't,
     * this method returns null. Otherwise it returns an object that
     * describes the port that this {@link WSEndpoint} is serving.
     *
     * @return
     *      Possibly null, but always the same value.
     */
    public abstract @Nullable WSDLPort getPort();

    /**
     * Set this {@link Executor} to run asynchronous requests using this executor.
     * This executor is set on {@link Engine} and must be set before
     * calling {@link #schedule(Packet,CompletionCallback) } and
     * {@link #schedule(Packet,CompletionCallback,FiberContextSwitchInterceptor)} methods.
     *
     * @param exec Executor to run async requests
     */
    public abstract void setExecutor(@NotNull Executor exec);

    /**
     * This method takes a {@link Packet} that represents
     * a request, run it through a {@link Tube}line, eventually
     * pass it to the user implementation code, which produces
     * a reply, then run that through the tubeline again,
     * and eventually return it as a return value through {@link CompletionCallback}.
     *
     * <p>
     * This takes care of pooling of {@link Tube}lines and reuses
     * tubeline for requests. Same instance of tubeline is not used concurrently
     * for two requests.
     *
     * <p>
     * If the transport is capable of asynchronous execution, use this
     * instead of using {@link PipeHead#process}.
     *
     * <p>
     * Before calling this method, set the executor using {@link #setExecutor}. The
     * executor may used multiple times to run this request in a asynchronous fashion.
     * The calling thread will be returned immediately, and the callback will be
     * called in a different a thread.
     *
     * <p>
     * {@link Packet#transportBackChannel} should have the correct value, so that
     * one-way message processing happens correctly. {@link Packet#webServiceContextDelegate}
     * should have the correct value, so that some {@link WebServiceContext} methods correctly.
     *
     * @see {@link Packet#transportBackChannel}
     * @see {@link Packet#webServiceContextDelegate}
     *
     * @param request web service request
     * @param callback callback to get response packet
     */
    public final void schedule(@NotNull Packet request, @NotNull CompletionCallback callback ) {
        schedule(request,callback,null);
    }

    /**
     * Schedule invocation of web service asynchronously.
     *
     * @see {@link #schedule(Packet, CompletionCallback)}
     *
     * @param request web service request
     * @param callback callback to get response packet(exception if there is one)
     * @param interceptor caller's interceptor to impose a context of execution
     */
    public abstract void schedule(@NotNull Packet request, @NotNull CompletionCallback callback, @Nullable FiberContextSwitchInterceptor interceptor );

    /**
     * Callback to notify that jax-ws runtime has finished execution of a request
     * submitted via schedule().
     */
    public interface CompletionCallback {
        /**
         * Indicates that the jax-ws runtime has finished execution of a request
         * submitted via schedule().
         *
         * <p>
         * Since the JAX-WS RI runs asynchronously,
         * this method maybe invoked by a different thread
         * than any of the threads that started it or run a part of tubeline.
         *
         * @param response {@link Packet}
         */
        void onCompletion(@NotNull Packet response);
    }

    /**
     * Creates a new {@link PipeHead} to process
     * incoming requests.
     *
     * <p>
     * This is not a cheap operation. The caller is expected
     * to reuse the returned {@link PipeHead}. See
     * {@link WSEndpoint class javadoc} for details.
     *
     * @return
     *      A newly created {@link PipeHead} that's ready to serve.
     */
    public abstract @NotNull PipeHead createPipeHead();

    /**
     * Represents a resource local to a thread.
     *
     * See {@link WSEndpoint} class javadoc for more discussion about
     * this.
     */
    public interface PipeHead {
        /**
         * Processes a request and produces a reply.
         *
         * <p>
         * This method takes a {@link Packet} that represents
         * a request, run it through a {@link Tube}line, eventually
         * pass it to the user implementation code, which produces
         * a reply, then run that through the pipeline again,
         * and eventually return it as a return value.
         *
         * @param request
         *      Unconsumed {@link Packet} that represents
         *      a request.
         * @param wscd
         *      {@link WebServiceContextDelegate} to be set to {@link Packet}.
         *      (we didn't have to take this and instead just ask the caller to
         *      set to {@link Packet#webServiceContextDelegate}, but that felt
         *      too error prone.)
         * @param tbc
         *      {@link TransportBackChannel} to be set to {@link Packet}.
         *      See the {@code wscd} parameter javadoc for why this is a parameter.
         *      Can be null.
         * @return
         *      Unconsumed {@link Packet} that represents
         *      a reply to the request.
         *
         * @throws WebServiceException
         *      This method <b>does not</b> throw a {@link WebServiceException}.
         *      The {@link WSEndpoint} must always produce a fault {@link Message}
         *      for it.
         *
         * @throws RuntimeException
         *      A {@link RuntimeException} thrown from this method, including
         *      {@link WebServiceException}, must be treated as a bug in the
         *      code (including JAX-WS and all the pipe implementations), not
         *      an operator error by the user.
         *
         *      <p>
         *      Therefore, it should be recorded by the caller in a way that
         *      allows developers to fix a bug.
         */
        @NotNull Packet process(
            @NotNull Packet request, @Nullable WebServiceContextDelegate wscd, @Nullable TransportBackChannel tbc);
    }

    /**
     * Indicates that the {@link WSEndpoint} is about to be turned off,
     * and will no longer serve any packet anymore.
     *
     * <p>
     * This method needs to be invoked for the JAX-WS RI to correctly
     * implement some of the spec semantics (TODO: pointer.)
     * It's the responsibility of the code that hosts a {@link WSEndpoint}
     * to invoke this method.
     *
     * <p>
     * Once this method is called, the behavior is undefed for
     * all in-progress {@link PipeHead#process} methods (by other threads)
     * and future {@link PipeHead#process} method invocations.
     */
    public abstract void dispose();

    /**
     * Gets the description of the service.
     *
     * <p>
     * A description is a set of WSDL/schema and other documents that together
     * describes a service.
     * A service is not required to have a description, and when it doesn't,
     * this method returns null.
     *
     * @return
     *      Possibly null, but always the same value.
     */
    public abstract @Nullable ServiceDefinition getServiceDefinition();



    /**
     * Creates an endpoint from deployment or programmatic configuration
     *
     * <p>
     * This method works like the following:
     * <ol>
     * <li>{@link ServiceDefinition} is modeleed from the given SEI type.
     * <li>{@link Invoker} that always serves <tt>implementationObject</tt> will be used.
     * </ol>
     * @param implType
     *      Endpoint class(not SEI). Enpoint class must have @WebService or @WebServiceProvider
     *      annotation.
     * @param processHandlerAnnotation
     *      Flag to control processing of @HandlerChain on Impl class
     *      if true, processes @HandlerChain on Impl
     *      if false, DD might have set HandlerChain no need to parse.
     * @param invoker
     *      Pass an object to invoke the actual endpoint object. If it is null, a default
     *      invoker is created using {@link InstanceResolver#createDefault}. Appservers
     *      could create its own invoker to do additional functions like transactions,
     *      invoking the endpoint through proxy etc.
     * @param serviceName
     *      Optional service name(may be from DD) to override the one given by the
     *      implementation class. If it is null, it will be derived from annotations.
     * @param portName
     *      Optional port name(may be from DD) to override the one given by the
     *      implementation class. If it is null, it will be derived from annotations.
     * @param container
     *      Allows technologies that are built on top of JAX-WS(such as WSIT) needs to
     *      negotiate private contracts between them and the container
     * @param binding
     *      JAX-WS implementation of {@link Binding}. This object can be created by
     *      {@link BindingID#createBinding()}. Usually the binding can be got from
     *      DD, {@link javax.xml.ws.BindingType}.
     *
     *
     * TODO: DD has a configuration for MTOM threshold.
     * Maybe we need something more generic so that other technologies
     * like Tango can get information from DD.
     *
     * TODO: does it really make sense for this to take EntityResolver?
     * Given that all metadata has to be given as a list anyway.
     *
     * @param primaryWsdl
     *      The {@link ServiceDefinition#getPrimary() primary} WSDL.
     *      If null, it'll be generated based on the SEI (if this is an SEI)
     *      or no WSDL is associated (if it's a provider.)
     *      TODO: shouldn't the implementation find this from the metadata list?
     * @param metadata
     *      Other documents that become {@link SDDocument}s. Can be null.
     * @param resolver
     *      Optional resolver used to de-reference resources referenced from
     *      WSDL. Must be null if the {@code url} is null.
     * @param isTransportSynchronous
     *      If the caller knows that the returned {@link WSEndpoint} is going to be
     *      used by a synchronous-only transport, then it may pass in <tt>true</tt>
     *      to allow the callee to perform an optimization based on that knowledge
     *      (since often synchronous version is cheaper than an asynchronous version.)
     *      This value is visible from {@link ServerTubeAssemblerContext#isSynchronous()}.
     *
     * @return newly constructed {@link WSEndpoint}.
     * @throws WebServiceException
     *      if the endpoint set up fails.
     */
    public static <T> WSEndpoint<T> create(
        @NotNull Class<T> implType,
        boolean processHandlerAnnotation,
        @Nullable Invoker invoker,
        @Nullable QName serviceName,
        @Nullable QName portName,
        @Nullable Container container,
        @Nullable WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        @Nullable EntityResolver resolver,
        boolean isTransportSynchronous) {
        return EndpointFactory.createEndpoint(
            implType,processHandlerAnnotation, invoker,serviceName,portName,container,binding,primaryWsdl,metadata,resolver,isTransportSynchronous);
    }

    /**
     * Deprecated version that assumes <tt>isTransportSynchronous==false</tt>
     */
    @Deprecated
    public static <T> WSEndpoint<T> create(
        @NotNull Class<T> implType,
        boolean processHandlerAnnotation,
        @Nullable Invoker invoker,
        @Nullable QName serviceName,
        @Nullable QName portName,
        @Nullable Container container,
        @Nullable WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        @Nullable EntityResolver resolver) {
        return create(implType,processHandlerAnnotation,invoker,serviceName,portName,container,binding,primaryWsdl,metadata,resolver,false);
    }


    /**
     * The same as
     * {@link #create(Class, boolean, Invoker, QName, QName, Container, WSBinding, SDDocumentSource, Collection, EntityResolver)}
     * except that this version takes an url of the <tt>jax-ws-catalog.xml</tt>.
     *
     * @param catalogUrl
     *      if not null, an {@link EntityResolver} is created from it and used.
     *      otherwise no resolution will be performed.
     */
    public static <T> WSEndpoint<T> create(
        @NotNull Class<T> implType,
        boolean processHandlerAnnotation,
        @Nullable Invoker invoker,
        @Nullable QName serviceName,
        @Nullable QName portName,
        @Nullable Container container,
        @Nullable WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        @Nullable URL catalogUrl) {
        return create(
            implType,processHandlerAnnotation,invoker,serviceName,portName,container,binding,primaryWsdl,metadata,
            XmlUtil.createEntityResolver(catalogUrl),false);
    }

    /**
     * Gives the wsdl:service default name computed from the endpoint implementaiton class
     */
    public static @NotNull QName getDefaultServiceName(Class endpointClass){
        return EndpointFactory.getDefaultServiceName(endpointClass);
    }

    /**
     * Gives the wsdl:service/wsdl:port default name computed from the endpoint implementaiton class
     */
    public static @NotNull QName getDefaultPortName(@NotNull QName serviceName, Class endpointClass){
        return EndpointFactory.getDefaultPortName(serviceName, endpointClass);
    }

}
