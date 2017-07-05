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

import com.sun.xml.internal.ws.api.message.Message;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterPipeImpl;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractPipeImpl;

import javax.annotation.PreDestroy;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPHandler;

/**
 * Abstraction of the intermediate layers in the processing chain
 * and transport.
 *
 * <h2>What is a {@link Pipe}?</h2>
 * <p>
 * Transport is a kind of pipe. It sends the {@link Packet}
 * through, say, HTTP connection, and receives the data back into another {@link Packet}.
 *
 * <p>
 * More often, a pipe is a filter. It acts on a packet,
 * and then it passes the packet into another pipe. It can
 * do the same on the way back.
 *
 * <p>
 * For example, XWSS will be a {@link Pipe}
 * that delegates to another {@link Pipe}, and it can wrap a {@link Packet} into
 * another {@link Packet} to encrypt the body and add a header, for example.
 *
 * <p>
 * Yet another kind of filter pipe is those that wraps {@link LogicalHandler}
 * and {@link SOAPHandler}. These pipes are heavy-weight; they often consume
 * a message in a packet and create a new one, and then pass it to the next pipe.
 * For performance reason it probably makes sense to have one {@link Pipe}
 * instance that invokes a series of {@link LogicalHandler}s, another one
 * for {@link SOAPHandler}.
 *
 * <p>
 * There would be a {@link Pipe} implementation that invokes {@link Provider}.
 * There would be a {@link Pipe} implementation that invokes a service method
 * on the user's code.
 * There would be a {@link Dispatch} implementation that invokes a {@link Pipe}.
 *
 * <p>
 * WS-MEX can be implemented as a {@link Pipe} that looks for
 * {@link Message#getPayloadNamespaceURI()} and serves the request.
 *
 *
 * <h2>Pipe Lifecycle</h2>
 * {@link Pipe}line is expensive to set up, so once it's created it will be reused.
 * A {@link Pipe}line is not reentrant; one pipeline is used to process one request/response
 * at at time. The same pipeline instance may serve request/response for different threads,
 * if one comes after another and they don't overlap.
 * <p>
 * Where a need arises to process multiple requests concurrently, a pipeline
 * gets cloned through {@link PipeCloner}. Note that this need may happen on
 * both server (because it quite often serves multiple requests concurrently)
 * and client (because it needs to support asynchronous method invocations.)
 * <p>
 * Created pipelines (including cloned ones and the original) may be discarded and GCed
 * at any time at the discretion of whoever owns pipelines. Pipes can, however, expect
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
 * <h2>Pipe and State</h2>
 * <p>
 * The lifecycle of pipelines is designed to allow a {@link Pipe} to store various
 * state in easily accessible fashion.
 *
 *
 * <h3>Per-packet state</h3>
 * <p>
 * Any information that changes from a packet to packet should be
 * stored in {@link Packet}. This includes information like
 * transport-specific headers.
 *
 * <h3>Per-thread state</h3>
 * <p>
 * Any expensive objects that are non-reentrant can be stored in
 * instance variables of a {@link Pipe}, since {@link #process(Packet)} is
 * non reentrant. When a pipe is copied, new instances should be allocated
 * so that two {@link Pipe} instances don't share thread-unsafe resources.
 * This includes things like canonicalizers, JAXB unmarshallers, buffers,
 * and so on.
 *
 * <h3>Per-proxy/per-endpoint state</h3>
 * <p>
 * Information that is tied to a particular proxy/dispatch can be stored
 * in a separate object that is referenced from a pipe. When
 * a new pipe is copied, you can simply hand out a reference to the newly
 * created one, so that all copied pipes refer to the same instance.
 * See the following code as an example:
 *
 * <pre>
 * class PipeImpl {
 *   // this object stores per-proxy state
 *   class DataStore {
 *     int counter;
 *   }
 *
 *   private DataStore ds;
 *
 *   // create a fresh new pipe
 *   public PipeImpl(...) {
 *     ....
 *     ds = new DataStore();
 *   }
 *
 *   // copy constructor
 *   private PipeImpl(PipeImpl that, PipeCloner cloner) {
 *     cloner.add(that,this);
 *     ...
 *     this.ds = that.ds;
 *   }
 *
 *   public PipeImpl copy(PipeCloner pc) {
 *     return new PipeImpl(this,pc);
 *   }
 * }
 * </pre>
 *
 * <p>
 * Note that access to such resource often needs to be synchronized,
 * since multiple copies of pipelines may execute concurrently.
 *
 * <p>
 * If such information is read-only,
 * it can be stored as instance variables of a pipe,
 * and its reference copied as pipes get copied. (The only difference between
 * this and per-thread state is that you just won't allocate new things when
 * pipes get copied here.)
 *
 *
 * <h3>VM-wide state</h3>
 * <p>
 * <tt>static</tt> is always there for you to use.
 *
 *
 *
 * <h2>Pipes and Handlers</h2>
 * <p>
 * JAX-WS has a notion of {@link LogicalHandler} and {@link SOAPHandler}, and
 * we intend to have one {@link Pipe} implementation that invokes all the
 * {@link LogicalHandler}s and another {@link Pipe} implementation that invokes
 * all the {@link SOAPHandler}s. Those implementations need to convert a {@link Message}
 * into an appropriate format, but grouping all the handlers together eliminates
 * the intermediate {@link Message} instanciation between such handlers.
 * <p>
 * This grouping also allows such implementations to follow the event notifications
 * to handlers (i.e. {@link Handler#close(MessageContext)} method.
 *
 *
 * <pre>
 * TODO: Possible types of pipe:
 *      creator: create message from wire
 *          to SAAJ SOAP message
 *          to cached representation
 *          directly to JAXB beans
 *      transformer: transform message from one representation to another
 *          JAXB beans to encoded SOAP message
 *          StAX writing + JAXB bean to encoded SOAP message
 *      modifier: modify message
 *          add SOAP header blocks
 *          security processing
 *      header block processor:
 *          process certain SOAP header blocks
 *      outbound initiator: input from the client
 *          Manage input e.g. JAXB beans and associated with parts of the SOAP message
 *      inbound invoker: invoke the service
 *         Inkoke SEI, e.g. EJB or SEI in servlet.
 * </pre>
 *
 * @see AbstractPipeImpl
 * @see AbstractFilterPipeImpl
 * @deprecated
 *      Use {@link Tube}.
 */
public interface Pipe {
    /**
     * Sends a {@link Packet} and returns a response {@link Packet} to it.
     *
     * @throws WebServiceException
     *      On the server side, this signals an error condition where
     *      a fault reply is in order (or the exception gets eaten by
     *      the top-most transport {@link Pipe} if it's one-way.)
     *      This frees each {@link Pipe} from try/catching a
     *      {@link WebServiceException} in every layer.
     *
     *      Note that this method is also allowed to return a {@link Packet}
     *      that has a fault as the payload.
     *
     *      <p>
     *      On the client side, the {@link WebServiceException} thrown
     *      will be propagated all the way back to the calling client
     *      applications. (The consequence of that is that if you are
     *      a filtering {@link Pipe}, you must not catch the exception
     *      that your next {@link Pipe} threw.
     *
     * @throws RuntimeException
     *      Other runtime exception thrown by this method must
     *      be treated as a bug in the pipe implementation,
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
     *      The packet that represents a request message. Must not be null.
     *      If the packet has a non-null message, it must be a valid
     *      unconsumed {@link Message}. This message represents the
     *      SOAP message to be sent as a request.
     *      <p>
     *      The packet is also allowed to carry no message, which indicates
     *      that this is an output-only request.
     *      (that's called "solicit", right? - KK)
     *
     * @return
     *      The packet that represents a response message. Must not be null.
     *      If the packet has a non-null message, it must be
     *      a valid unconsumed {@link Message}. This message represents
     *      a response to the request message passed as a parameter.
     *      <p>
     *      The packet is also allowed to carry no message, which indicates
     *      that there was no response. This is used for things like
     *      one-way message and/or one-way transports.
     */
    Packet process( Packet request);

    /**
     * Invoked before the last copy of the pipeline is about to be discarded,
     * to give {@link Pipe}s a chance to clean up any resources.
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
     * Creates an identical clone of this {@link Pipe}.
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
     * {@link PipeCloner#add(Pipe,Pipe)} to register the copied pipe
     * with the original. This is required before you start copying
     * the other {@link Pipe} references you have, or else there's a
     * risk of infinite recursion.
     * <p>
     * For most {@link Pipe} implementations that delegate to another
     * {@link Pipe}, this method requires that you also copy the {@link Pipe}
     * that you delegate to.
     * <p>
     * For limited number of {@link Pipe}s that do not maintain any
     * thread unsafe resource, it is allowed to simply return <tt>this</tt>
     * from this method (notice that even if you are stateless, if you
     * got a delegating {@link Pipe} and that one isn't stateless, you
     * still have to copy yourself.)
     *
     * <p>
     * Note that this method might be invoked by one thread while another
     * thread is executing the {@link #process(Packet)} method. See
     * the {@link Codec#copy()} for more discussion about this.
     *
     * @param cloner
     *      Use this object (in particular its {@link PipeCloner#copy(Pipe)} method
     *      to clone other pipe references you have
     *      in your pipe. See {@link PipeCloner} for more discussion
     *      about why.
     *
     * @return
     *      always non-null {@link Pipe}.
     * @param cloner
     */
    Pipe copy(PipeCloner cloner);
}
