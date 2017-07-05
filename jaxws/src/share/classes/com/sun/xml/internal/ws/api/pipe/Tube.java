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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.internal.ws.api.server.Adapter;

import javax.annotation.PreDestroy;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.soap.SOAPHandler;
import java.text.SimpleDateFormat;

/**
 * Abstraction of the intermediate layers in the processing chain
 * and transport.
 *
 * <h2>What is a {@link Tube}?</h2>
 * <p>
 * {@link Tube} is a basic processing unit that represents SOAP-level
 * protocol handling code. Mutliple tubes are often put together in
 * a line (it needs not one dimensional &mdash; more later), and act on
 * {@link Packet}s in a sequential fashion.
 *
 * <p>
 * {@link Tube}s run asynchronously. That is, there is no guarantee that
 * {@link #processRequest(Packet)} and {@link #processResponse(Packet)} runs
 * in the same thread, nor is there any guarantee that this tube and next
 * tube runs in the same thread. Furthermore, one thread may be used to
 * run multiple pipeline in turn (just like a real CPU runs multiple
 * threads in turn.)
 *
 *
 * <h2>Tube examples</h2>
 * <p>
 * Transport is a kind of tube. It sends the {@link Packet}
 * through, say, HTTP connection, and receives the data back into another {@link Packet}.
 *
 * <p>
 * More often, a tube works like a filter. It acts on a packet,
 * and then it tells the JAX-WS that the packet should be passed into another
 * tube. It can do the same on the way back.
 *
 * <p>
 * For example, XWSS will be a {@link Tube}. It will act on a request
 * {@link Packet}, then perhaps wrap it into
 * another {@link Packet} to encrypt the body and add a header, then
 * the processing will go on to the next tube.
 *
 * <p>
 * Yet another kind of filter tube is those that wraps {@link LogicalHandler}
 * and {@link SOAPHandler}. These tubes are heavy-weight; they often consume
 * a message in a packet and create a new one, and then pass it to the next tube.
 *
 * <p>
 * There would be a {@link Tube} implementation that invokes {@link Provider}.
 * There would be a {@link Tube} implementation that invokes a service method
 * on the user's code.
 * There would be a {@link Dispatch} implementation that invokes a {@link Tube}.
 *
 * <p>
 * WS-MEX can be implemented as a {@link Tube} that looks for
 * {@link Message#getPayloadNamespaceURI()} and serves the request.
 *
 *
 *
 *
 * <h2>Tube Lifecycle</h2>
 * Pipeline is expensive to set up, so once it's created it will be reused.
 * A pipeline is not reentrant; one pipeline is used to process one request/response
 * at at time. The same pipeline instance may serve multiple request/response,
 * if one comes after another and they don't overlap.
 * <p>
 * Where a need arises to process multiple requests concurrently, a pipeline
 * gets cloned through {@link TubeCloner}. Note that this need may happen on
 * both server (because it quite often serves multiple requests concurrently)
 * and client (because it needs to support asynchronous method invocations.)
 * <p>
 * Created pipelines (including cloned ones and the original) may be discarded and GC-ed
 * at any time at the discretion of whoever owns pipelines. Tubes can, however, expect
 * at least one copy (or original) of pipeline to live at any given time while a pipeline
 * owner is interested in the given pipeline configuration (in more concerete terms,
 * for example, as long as a dispatch object lives, it's going to keep at least one
 * copy of a pipeline alive.)
 * <p>
 * Before a pipeline owner dies, it may invoke {@link #preDestroy()} on the last
 * remaining pipeline. It is "may" for pipeline owners that live in the client-side
 * of JAX-WS (such as dispatches and proxies), but it is a "must" for pipeline owners
 * that live in the server-side of JAX-WS.
 * <p>
 * This last invocation gives a chance for some pipes to clean up any state/resource
 * acquired (such as WS-RM's sequence, WS-Trust's SecurityToken), although as stated above,
 * this is not required for clients.
 *
 *
 *
 * <h2>Tube and state</h2>
 * <p>
 * The lifecycle of pipelines is designed to allow a {@link Tube} to store various
 * state in easily accessible fashion.
 *
 *
 * <h3>Per-packet state</h3>
 * <p>
 * Any information that changes from a packet to packet should be
 * stored in {@link Packet} (if such informaton is specific to your problem domain,
 * then most likely {@link Packet#invocationProperties}.)
 * This includes information like transport-specific headers.
 *
 * <h3>Per-thread state</h3>
 * <p>
 * Any expensive-to-create objects that are non-reentrant can be stored
 * either in instance variables of a {@link Tube}, or a static {@link ThreadLocal}.
 *
 * <p>
 * The first approach works, because {@link Tube} is
 * non reentrant. When a tube is copied, new instances should be allocated
 * so that two {@link Tube} instances don't share thread-unsafe resources.
 *
 * Similarly the second approach works, since {@link ThreadLocal} guarantees
 * that each thread gets its own private copy.
 *
 * <p>
 * The former is faster to access, and you need not worry about clean up.
 * On the other hand, because there can be many more concurrent requests
 * than # of threads, you may end up holding onto more resources than necessary.
 *
 * <p>
 * This includes state like canonicalizers, JAXB unmarshallers,
 * {@link SimpleDateFormat}, etc.
 *
 *
 * <h3>Per-proxy/per-endpoint state</h3>
 * <p>
 * Information that is tied to a particular proxy/dispatch can be stored
 * in a separate object that is referenced from a tube. When
 * a new tube is copied, you can simply hand out a reference to the newly
 * created one, so that all copied tubes refer to the same instance.
 * See the following code as an example:
 *
 * <pre>
 * class TubeImpl {
 *   // this object stores per-proxy state
 *   class DataStore {
 *     int counter;
 *   }
 *
 *   private DataStore ds;
 *
 *   // create a fresh new pipe
 *   public TubeImpl(...) {
 *     ....
 *     ds = new DataStore();
 *   }
 *
 *   // copy constructor
 *   private TubeImpl(TubeImpl that, PipeCloner cloner) {
 *     cloner.add(that,this);
 *     ...
 *     this.ds = that.ds;
 *   }
 *
 *   public TubeImpl copy(PipeCloner pc) {
 *     return new TubeImpl(this,pc);
 *   }
 * }
 * </pre>
 *
 * <p>
 * Note that access to such resource may need to be synchronized,
 * since multiple copies of pipelines may execute concurrently.
 *
 *
 *
 * <h3>VM-wide state</h3>
 * <p>
 * <tt>static</tt> is always there for you to use.
 *
 *
 *
 * @see AbstractTubeImpl
 * @see AbstractFilterTubeImpl
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public interface Tube {
    /**
     * Acts on a request and perform some protocol specific operation.
     *
     * TODO: exception handling semantics need more discussion
     *
     * @throws WebServiceException
     *      On the server side, this signals an error condition where
     *      a fault reply is in order (or the exception gets eaten by
     *      the top-most transport {@link Adapter} if it's one-way.)
     *      This frees each {@link Tube} from try/catching a
     *      {@link WebServiceException} in every layer.
     *
     *      Note that this method is also allowed to return
     *      {@link NextAction#returnWith(Packet)} with
     *      a {@link Packet} that has a fault as the payload.
     *
     *      <p>
     *      On the client side, the {@link WebServiceException} thrown
     *      will be propagated all the way back to the calling client
     *      applications. (The consequence of that is that if you are
     *      a filtering {@link Tube}, you must not eat the exception
     *      that was given to {@link #processException(Throwable)} .
     *
     * @throws RuntimeException
     *      Other runtime exception thrown by this method must
     *      be treated as a bug in the tube implementation,
     *      and therefore should not be converted into a fault.
     *      (Otherwise it becomes very difficult to debug implementation
     *      problems.)
     *
     *      <p>
     *      On the server side, this exception should be most likely
     *      just logged. On the client-side it gets propagated to the
     *      client application.
     *
     *      <p>
     *      The consequence of this is that if a pipe calls
     *      into an user application (such as {@link SOAPHandler}
     *      or {@link LogicalHandler}), where a {@link RuntimeException}
     *      is *not* a bug in the JAX-WS implementation, it must be catched
     *      and wrapped into a {@link WebServiceException}.
     *
     * @param request
     *      The packet that represents a request message.
     *      If the packet has a non-null message, it must be a valid
     *      unconsumed {@link Message}. This message represents the
     *      SOAP message to be sent as a request.
     *      <p>
     *      The packet is also allowed to carry no message, which indicates
     *      that this is an output-only request.
     *      (that's called "solicit", right? - KK)
     *
     * @return
     *      A {@link NextAction} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    @NotNull NextAction processRequest(@NotNull Packet request);

    /**
     * Acts on a response and performs some protocol specific operation.
     *
     * <p>
     * Once a {@link #processRequest(Packet)} is invoked, this method
     * will be always invoked with the response, before this {@link Tube}
     * processes another request.
     *
     * @param response
     *      If the packet has a non-null message, it must be
     *      a valid unconsumed {@link Message}. This message represents
     *      a response to the request message passed to
     *      {@link #processRequest(Packet)} earlier.
     *      <p>
     *      The packet is also allowed to carry no message, which indicates
     *      that there was no response. This is used for things like
     *      one-way message and/or one-way transports.
     *
     * TODO: exception handling semantics need more discussion
     *
     * @return
     *      A {@link NextAction} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    @NotNull NextAction processResponse(@NotNull Packet response);


    /**
     * Acts on a exception and performs some clean up operations.
     *
     * <p>
     * If a {@link #processRequest(Packet)}, {@link #processResponse(Packet)},
     * {@link #processException(Throwable)} throws an exception, this method
     * will be always invoked on all the {@link Tube}s in the remaining
     * {@link NextAction}s.
     *
     * <p>
     * On the server side, the {@link Throwable} thrown will be propagated to the
     * top-most transport. The transport converts the exception to fault reply or
     * simply logs in case of one-way MEP. If you are a filtering {@link Tube} like
     * {@link AbstractTubeImpl}, you don't have to override the implementation). On
     * the other hand, any intermediate {@link Tube} may want to convert the exception
     * to a fault message.
     *
     * <p>
     * On the client side, the {@link Throwable} thrown
     * will be propagated all the way back to the calling client
     * applications. (The consequence of that is that if you are
     * a filtering {@link Tube} like {@link AbstractTubeImpl}, you don't have to
     * override the implementation)
     *
     * @param t
     *
     * @return
     *      A {@link NextAction} object that represents the next action
     *      to be taken by the JAX-WS runtime.
     */
    @NotNull NextAction processException(@NotNull Throwable t);

    /**
     * Invoked before the last copy of the pipeline is about to be discarded,
     * to give {@link Tube}s a chance to clean up any resources.
     *
     * <p>
     * This can be used to invoke {@link PreDestroy} lifecycle methods
     * on user handler. The invocation of it is optional on the client side,
     * but mandatory on the server side.
     *
     * <p>
     * When multiple copies of pipelines are created, this method is called
     * only on one of them.
     *
     * @throws WebServiceException
     *      If the clean up fails, {@link WebServiceException} can be thrown.
     *      This exception will be propagated to users (if this is client),
     *      or recorded (if this is server.)
     */
    void preDestroy();

    /**
     * Creates an identical clone of this {@link Tube}.
     *
     * <p>
     * This method creates an identical pipeline that can be used
     * concurrently with this pipeline. When the caller of a pipeline
     * is multi-threaded and need concurrent use of the same pipeline,
     * it can do so by creating copies through this method.
     *
     * <h3>Implementation Note</h3>
     * <p>
     * It is the implementation's responsibility to call
     * {@link TubeCloner#add(Tube,Tube)} to register the copied pipe
     * with the original. This is required before you start copying
     * the other {@link Tube} references you have, or else there's a
     * risk of infinite recursion.
     * <p>
     * For most {@link Tube} implementations that delegate to another
     * {@link Tube}, this method requires that you also copy the {@link Tube}
     * that you delegate to.
     * <p>
     * For limited number of {@link Tube}s that do not maintain any
     * thread unsafe resource, it is allowed to simply return <tt>this</tt>
     * from this method (notice that even if you are stateless, if you
     * got a delegating {@link Tube} and that one isn't stateless, you
     * still have to copy yourself.)
     *
     * <p>
     * Note that this method might be invoked by one thread while another
     * thread is executing the other process method. See
     * the {@link Codec#copy()} for more discussion about this.
     *
     * @param cloner
     *      Use this object (in particular its {@link TubeCloner#copy(Tube)} method
     *      to clone other pipe references you have
     *      in your pipe. See {@link TubeCloner} for more discussion
     *      about why.
     *
     * @return
     *      always non-null {@link Tube}.
     * @param cloner
     */
    Tube copy(TubeCloner cloner);
}
