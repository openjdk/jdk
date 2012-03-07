/*
 * Copyright (c) 1997, 2010, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.Cancelable;
import com.sun.xml.internal.ws.api.Component;
import com.sun.xml.internal.ws.api.ComponentRegistry;
import com.sun.xml.internal.ws.api.SOAPVersion;
import com.sun.xml.internal.ws.api.addressing.AddressingVersion;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.server.Adapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User-level thread&#x2E; Represents the execution of one request/response processing.
 * <p/>
 * <p/>
 * JAX-WS RI is capable of running a large number of request/response concurrently by
 * using a relatively small number of threads. This is made possible by utilizing
 * a {@link Fiber} &mdash; a user-level thread that gets created for each request/response
 * processing.
 * <p/>
 * <p/>
 * A fiber remembers where in the pipeline the processing is at, what needs to be
 * executed on the way out (when processing response), and other additional information
 * specific to the execution of a particular request/response.
 * <p/>
 * <h2>Suspend/Resume</h2>
 * <p/>
 * Fiber can be {@link NextAction#suspend() suspended} by a {@link Tube}.
 * When a fiber is suspended, it will be kept on the side until it is
 * {@link #resume(Packet) resumed}. This allows threads to go execute
 * other runnable fibers, allowing efficient utilization of smaller number of
 * threads.
 * <p/>
 * <h2>Context-switch Interception</h2>
 * <p/>
 * {@link FiberContextSwitchInterceptor} allows {@link Tube}s and {@link Adapter}s
 * to perform additional processing every time a thread starts running a fiber
 * and stops running it.
 * <p/>
 * <h2>Context ClassLoader</h2>
 * <p/>
 * Just like thread, a fiber has a context class loader (CCL.) A fiber's CCL
 * becomes the thread's CCL when it's executing the fiber. The original CCL
 * of the thread will be restored when the thread leaves the fiber execution.
 * <p/>
 * <p/>
 * <h2>Debugging Aid</h2>
 * <p/>
 * Because {@link Fiber} doesn't keep much in the call stack, and instead use
 * {@link #conts} to store the continuation, debugging fiber related activities
 * could be harder.
 * <p/>
 * <p/>
 * Setting the {@link #LOGGER} for FINE would give you basic start/stop/resume/suspend
 * level logging. Using FINER would cause more detailed logging, which includes
 * what tubes are executed in what order and how they behaved.
 * <p/>
 * <p/>
 * When you debug the server side, consider setting {@link Fiber#serializeExecution}
 * to true, so that execution of fibers are serialized. Debugging a server
 * with more than one running threads is very tricky, and this switch will
 * prevent that. This can be also enabled by setting the system property on.
 * See the source code.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public final class Fiber implements Runnable, Cancelable, ComponentRegistry {

        /**
         * Callback interface for notification of suspend and resume.
         *
         * @since 2.2.6
         */
    public interface Listener {
        /**
         * Fiber has been suspended.  Implementations of this callback may resume the Fiber.
         * @param fiber Fiber
         */
        public void fiberSuspended(Fiber fiber);

        /**
         * Fiber has been resumed.  Behavior is undefined if implementations of this callback attempt to suspend the Fiber.
         * @param fiber Fiber
         */
        public void fiberResumed(Fiber fiber);
    }

    private List<Listener> _listeners = new ArrayList<Listener>();

    /**
     * Adds suspend/resume callback listener
     * @param listener Listener
     * @since 2.2.6
     */
    public void addListener(Listener listener) {
        synchronized(_listeners) {
            if (!_listeners.contains(listener)) {
                _listeners.add(listener);
            }
        }
    }

    /**
     * Removes suspend/resume callback listener
     * @param listener Listener
     * @since 2.2.6
     */
    public void removeListener(Listener listener) {
        synchronized(_listeners) {
            _listeners.remove(listener);
        }
    }

    private List<Listener> getCurrentListeners() {
      synchronized(_listeners) {
         return new ArrayList<Listener>(_listeners);
      }
    }

    private void clearListeners() {
        synchronized(_listeners) {
            _listeners.clear();
        }
    }

    /**
     * {@link Tube}s whose {@link Tube#processResponse(Packet)} method needs
     * to be invoked on the way back.
     */
    private Tube[] conts = new Tube[16];
    private int contsSize;

    /**
     * If this field is non-null, the next instruction to execute is
     * to call its {@link Tube#processRequest(Packet)}. Otherwise
     * the instruction is to call {@link #conts}.
     */
    private Tube next;

    private Packet packet;

    private Throwable/*but really it's either RuntimeException or Error*/ throwable;

    public final Engine owner;

    /**
     * Is this thread suspended? 0=not suspended, 1=suspended.
     * <p/>
     * <p/>
     * Logically this is just a boolean, but we need to prepare for the case
     * where the thread is {@link #resume(Packet) resumed} before we get to the {@link #suspend()}.
     * This happens when things happen in the following order:
     * <p/>
     * <ol>
     * <li>Tube decides that the fiber needs to be suspended to wait for the external event.
     * <li>Tube hooks up fiber with some external mechanism (like NIO channel selector)
     * <li>Tube returns with {@link NextAction#suspend()}.
     * <li>"External mechanism" becomes signal state and invokes {@link Fiber#resume(Packet)}
     * to wake up fiber
     * <li>{@link Fiber#doRun} invokes {@link Fiber#suspend()}.
     * </ol>
     * <p/>
     * <p/>
     * Using int, this will work OK because {@link #suspendedCount} becomes -1 when
     * {@link #resume(Packet)} occurs before {@link #suspend()}.
     * <p/>
     * <p/>
     * Increment and decrement is guarded by 'this' object.
     */
    private volatile int suspendedCount = 0;

    private volatile boolean isInsideSuspendCallbacks = false;

    /**
     * Is this fiber completed?
     */
    private volatile boolean completed;

    /**
     * Is this {@link Fiber} currently running in the synchronous mode?
     */
    private boolean synchronous;

    private boolean interrupted;

    private final int id;

    /**
     * Active {@link FiberContextSwitchInterceptor}s for this fiber.
     */
    private List<FiberContextSwitchInterceptor> interceptors;

    /**
     * Not null when {@link #interceptors} is not null.
     */
    private InterceptorHandler interceptorHandler;

    /**
     * This flag is set to true when a new interceptor is added.
     * <p/>
     * When that happens, we need to first exit the current interceptors
     * and then reenter them, so that the newly added interceptors start
     * taking effect. This flag is used to control that flow.
     */
    private boolean needsToReenter;

    /**
     * Fiber's context {@link ClassLoader}.
     */
    private
    @Nullable
    ClassLoader contextClassLoader;

    private
    @Nullable
    CompletionCallback completionCallback;

    /**
     * The thread on which this Fiber is currently executing, if applicable.
     */
    private Thread currentThread;

    private volatile boolean isCanceled;

    /**
     * Set to true if this fiber is started asynchronously, to avoid
     * doubly-invoking completion code.
     */
    private boolean started;

    /**
     * Set to true if this fiber is started sync but allowed to run async.
     * This property exists for use cases where the processing model is fundamentally async
     * but some requirement or feature mandates that part of the tubeline run synchronously.  For
     * instance, WS-ReliableMessaging with non-anonymous addressing is compatible with running
     * asynchronously, but if in-order message delivery is used then message processing must assign
     * a message number before the remainder of the processing can be asynchronous.
     */
    private boolean startedSync;

    /**
     * Callback to be invoked when a {@link Fiber} finishs execution.
     */
    public interface CompletionCallback {
        /**
         * Indicates that the fiber has finished its execution.
         * <p/>
         * <p/>
         * Since the JAX-WS RI runs asynchronously,
         * this method maybe invoked by a different thread
         * than any of the threads that started it or run a part of tubeline.
         */
        void onCompletion(@NotNull Packet response);

        /**
         * Indicates that the fiber has finished abnormally, by throwing a given {@link Throwable}.
         */
        void onCompletion(@NotNull Throwable error);
    }

    Fiber(Engine engine) {
        this.owner = engine;
        if (isTraceEnabled()) {
            id = iotaGen.incrementAndGet();
            LOGGER.fine(getName() + " created");
        } else {
            id = -1;
        }

        // if this is run from another fiber, then we naturally inherit its context classloader,
        // so this code works for fiber->fiber inheritance just fine.
        contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Starts the execution of this fiber asynchronously.
     * <p/>
     * <p/>
     * This method works like {@link Thread#start()}.
     *
     * @param tubeline           The first tube of the tubeline that will act on the packet.
     * @param request            The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @param completionCallback The callback to be invoked when the processing is finished and the
     *                           final response packet is available.
     * @see #runSync(Tube, Packet)
     */
    public void start(@NotNull Tube tubeline, @NotNull Packet request, @Nullable CompletionCallback completionCallback) {
        start(tubeline, request, completionCallback, false);
    }

    private void dumpFiberContext(String desc) {
        if(isTraceEnabled()) {
            String action = null;
            String msgId = null;
            if (packet != null) {
                for (SOAPVersion sv: SOAPVersion.values()) {
                    for (AddressingVersion av: AddressingVersion.values()) {
                        action = packet.getMessage() != null ? packet.getMessage().getHeaders().getAction(av, sv) : null;
                        msgId = packet.getMessage() != null ? packet.getMessage().getHeaders().getMessageID(av, sv) : null;
                        if (action != null || msgId != null) {
                           break;
                        }
                    }
                    if (action != null || msgId != null) {
                        break;
                    }
                }
            }
            String actionAndMsgDesc;
            if (action == null && msgId == null) {
                actionAndMsgDesc = "NO ACTION or MSG ID";
            } else {
                actionAndMsgDesc = "'" + action + "' and msgId '" + msgId + "'";
            }

            String tubeDesc;
            if (next != null) {
                tubeDesc = next.toString() + ".processRequest()";
            } else {
                tubeDesc = peekCont() + ".processResponse()";
            }

            LOGGER.fine(getName() + " " + desc + " with " + actionAndMsgDesc  + " and 'current' tube " + tubeDesc + " from thread " + Thread.currentThread().getName() + " with Packet: " + (packet != null ? packet.toShortString() : null));
        }
    }

    /**
     * Starts the execution of this fiber.
     *
     * If forceSync is true, then the fiber is started for an ostensibly async invocation,
     * but allows for some portion of the tubeline to run sync with the calling
     * client instance (Port/Dispatch instance). This allows tubes that enforce
     * ordering to see requests in the order they were sent at the point the
     * client invoked them.
     * <p>
     * The forceSync parameter will be true only when the caller (e.g. AsyncInvoker or
     * SEIStub) knows one or more tubes need to enforce ordering and thus need
     * to run sync with the client. Such tubes can return
     * NextAction.INVOKE_ASYNC to indicate that the next tube in the tubeline
     * should be invoked async to the current thread.
     *
     * <p>
     * This method works like {@link Thread#start()}.
     *
     * @param tubeline
     *      The first tube of the tubeline that will act on the packet.
     * @param request
     *      The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @param completionCallback
     *      The callback to be invoked when the processing is finished and the
     *      final response packet is available.
     *
     * @see #start(Tube,Packet,CompletionCallback)
     * @see #runSync(Tube,Packet)
     * @since 2.2.6
     */
    public void start(@NotNull Tube tubeline, @NotNull Packet request, @Nullable CompletionCallback completionCallback, boolean forceSync) {
        next = tubeline;
        this.packet = request;
        this.completionCallback = completionCallback;

        if (forceSync) {
                this.startedSync = true;
                dumpFiberContext("starting (sync)");
                run();
        } else {
                this.started = true;
                dumpFiberContext("starting (async)");
                owner.addRunnable(this);
        }
    }

    /**
     * Wakes up a suspended fiber.
     * <p/>
     * <p/>
     * If a fiber was suspended without specifying the next {@link Tube},
     * then the execution will be resumed in the response processing direction,
     * by calling the {@link Tube#processResponse(Packet)} method on the next/first
     * {@link Tube} in the {@link Fiber}'s processing stack with the specified resume
     * packet as the parameter.
     * <p/>
     * <p/>
     * If a fiber was suspended with specifying the next {@link Tube},
     * then the execution will be resumed in the request processing direction,
     * by calling the next tube's {@link Tube#processRequest(Packet)} method with the
     * specified resume packet as the parameter.
     * <p/>
     * <p/>
     * This method is implemented in a race-free way. Another thread can invoke
     * this method even before this fiber goes into the suspension mode. So the caller
     * need not worry about synchronizing {@link NextAction#suspend()} and this method.
     *
     * @param resumePacket packet used in the resumed processing
     */
    public void resume(@NotNull Packet resumePacket) {
        resume(resumePacket, false);
    }

    /**
     * Similar to resume(Packet) but allowing the Fiber to be resumed
     * synchronously (in the current Thread). If you want to know when the
     * fiber completes (not when this method returns) then add/wrap a
     * CompletionCallback on this Fiber.
     * For example, an asynchronous response endpoint that supports WS-ReliableMessaging
     * including in-order message delivery may need to resume the Fiber synchronously
     * until message order is confirmed prior to returning to asynchronous processing.
     * @since 2.2.6
     */
    public synchronized void resume(@NotNull Packet resumePacket,
                                    boolean forceSync) {
      resume(resumePacket, forceSync, null);
    }

    /**
     * Similar to resume(Packet, boolean) but allowing the Fiber to be resumed
     * and at the same time atomically assign a new CompletionCallback to it.
     * @since 2.2.6
     */
    public void resume(@NotNull Packet resumePacket,
                       boolean forceSync,
                       CompletionCallback callback) {

       synchronized(this) {
           if (callback != null) {
             setCompletionCallback(callback);
           }
           if(isTraceEnabled())
                LOGGER.fine(getName()+" resuming. Will have suspendedCount=" + (suspendedCount-1));
            packet = resumePacket;
            if( --suspendedCount == 0 ) {
                if (!isInsideSuspendCallbacks) {
                        List<Listener> listeners = getCurrentListeners();
                        for (Listener listener: listeners) {
                            try {
                                listener.fiberResumed(this);
                            } catch (Throwable e) {
                                if (isTraceEnabled())
                                        LOGGER.fine("Listener " + listener + " threw exception: "  + e.getMessage());
                            }
                        }

                        if(synchronous) {
                            notifyAll();
                        } else if (forceSync || startedSync) {
                            run();
                        } else {
                            dumpFiberContext("resuming (async)");
                            owner.addRunnable(this);
                        }
                }
            } else {
                if (isTraceEnabled()) {
                    LOGGER.fine(getName() + " taking no action on resume because suspendedCount != 0: " + suspendedCount);
                }
            }
        }
    }

    /**
     * Wakes up a suspended fiber and begins response processing.
     * @since 2.2.6
     */
    public synchronized void resumeAndReturn(@NotNull Packet resumePacket,
                                             boolean forceSync) {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" resumed with Return Packet");
        next = null;
        resume(resumePacket, forceSync);
    }

    /**
     * Wakes up a suspended fiber with an exception.
     * <p/>
     * <p/>
     * The execution of the suspended fiber will be resumed in the response
     * processing direction, by calling the {@link Tube#processException(Throwable)} method
     * on the next/first {@link Tube} in the {@link Fiber}'s processing stack with
     * the specified exception as the parameter.
     * <p/>
     * <p/>
     * This method is implemented in a race-free way. Another thread can invoke
     * this method even before this fiber goes into the suspension mode. So the caller
     * need not worry about synchronizing {@link NextAction#suspend()} and this method.
     *
     * @param throwable exception that is used in the resumed processing
     */
    public synchronized void resume(@NotNull Throwable throwable) {
        resume(throwable, false);
    }

    /**
     * Wakes up a suspend fiber with an exception.
     *
     * If forceSync is true, then the suspended fiber will resume with
     * synchronous processing on the current thread.  This will continue
     * until some Tube indicates that it is safe to switch to asynchronous
     * processing.
     *
     * @param error exception that is used in the resumed processing
     * @param forceSync if processing begins synchronously
     * @since 2.2.6
     */
    public synchronized void resume(@NotNull Throwable error,
                                            boolean forceSync) {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" resumed with Return Throwable");
        next = null;
        throwable = error;
        resume(packet, forceSync);
    }

    /**
     * Marks this Fiber as cancelled.  A cancelled Fiber will never invoke its completion callback
     * @param mayInterrupt if cancel should use {@link Thread.interrupt()}
     * @see java.util.Future.cancel
     * @since 2.2.6
     */
    public void cancel(boolean mayInterrupt) {
        isCanceled = true;
        if (mayInterrupt) {
                synchronized(this) {
                        if (currentThread != null)
                                currentThread.interrupt();
                }
        }
    }

    /**
     * Suspends this fiber's execution until the resume method is invoked.
     * <p/>
     * The call returns immediately, and when the fiber is resumed
     * the execution picks up from the last scheduled continuation.
     */
    private boolean suspend() {

        synchronized(this) {
            if(isTraceEnabled()) {
                LOGGER.fine(getName()+" suspending. Will have suspendedCount=" + (suspendedCount+1));
                if (suspendedCount > 0) {
                    LOGGER.fine("WARNING - " + getName()+" suspended more than resumed. Will require more than one resume to actually resume this fiber.");
                }
            }

            List<Listener> listeners = getCurrentListeners();
            if (++suspendedCount == 1) {
                isInsideSuspendCallbacks = true;
                try {
                        for (Listener listener: listeners) {
                            try {
                                listener.fiberSuspended(this);
                            } catch (Throwable e) {
                                if(isTraceEnabled())
                                        LOGGER.fine("Listener " + listener + " threw exception: "  + e.getMessage());
                            }
                        }
                } finally {
                        isInsideSuspendCallbacks = false;
                }
            }

            if (suspendedCount <= 0) {
                // suspend callback caused fiber to resume
                for (Listener listener: listeners) {
                    try {
                        listener.fiberResumed(this);
                    } catch (Throwable e) {
                        if(isTraceEnabled())
                                LOGGER.fine("Listener " + listener + " threw exception: "  + e.getMessage());
                    }
                }

                return false;
            }

            return true;
        }
    }

    /**
     * Adds a new {@link FiberContextSwitchInterceptor} to this fiber.
     * <p/>
     * <p/>
     * The newly installed fiber will take effect immediately after the current
     * tube returns from its {@link Tube#processRequest(Packet)} or
     * {@link Tube#processResponse(Packet)}, before the next tube begins processing.
     * <p/>
     * <p/>
     * So when the tubeline consists of X and Y, and when X installs an interceptor,
     * the order of execution will be as follows:
     * <p/>
     * <ol>
     * <li>X.processRequest()
     * <li>interceptor gets installed
     * <li>interceptor.execute() is invoked
     * <li>Y.processRequest()
     * </ol>
     */
    public void addInterceptor(@NotNull FiberContextSwitchInterceptor interceptor) {
        if (interceptors == null) {
            interceptors = new ArrayList<FiberContextSwitchInterceptor>();
            interceptorHandler = new InterceptorHandler();
        }
        interceptors.add(interceptor);
        needsToReenter = true;
    }

    /**
     * Removes a {@link FiberContextSwitchInterceptor} from this fiber.
     * <p/>
     * <p/>
     * The removal of the interceptor takes effect immediately after the current
     * tube returns from its {@link Tube#processRequest(Packet)} or
     * {@link Tube#processResponse(Packet)}, before the next tube begins processing.
     * <p/>
     * <p/>
     * <p/>
     * So when the tubeline consists of X and Y, and when Y uninstalls an interceptor
     * on the way out, then the order of execution will be as follows:
     * <p/>
     * <ol>
     * <li>Y.processResponse() (notice that this happens with interceptor.execute() in the callstack)
     * <li>interceptor gets uninstalled
     * <li>interceptor.execute() returns
     * <li>X.processResponse()
     * </ol>
     *
     * @return true if the specified interceptor was removed. False if
     *         the specified interceptor was not registered with this fiber to begin with.
     */
    public boolean removeInterceptor(@NotNull FiberContextSwitchInterceptor interceptor) {
        if (interceptors != null && interceptors.remove(interceptor)) {
            needsToReenter = true;
            return true;
        }
        return false;
    }

    /**
     * Gets the context {@link ClassLoader} of this fiber.
     */
    public
    @Nullable
    ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Sets the context {@link ClassLoader} of this fiber.
     */
    public ClassLoader setContextClassLoader(@Nullable ClassLoader contextClassLoader) {
        ClassLoader r = this.contextClassLoader;
        this.contextClassLoader = contextClassLoader;
        return r;
    }

    /**
     * DO NOT CALL THIS METHOD. This is an implementation detail
     * of {@link Fiber}.
     */
    @Deprecated
    public void run() {
        assert !synchronous;
        doRun();
        if (startedSync && suspendedCount == 0 &&
            (next != null || contsSize > 0)) {
            // We bailed out of running this fiber we started as sync, and now
            // want to finish running it async
            startedSync = false;
            // Start back up as an async fiber
            dumpFiberContext("restarting (async) after startSync");
            owner.addRunnable(this);
        } else {
            completionCheck();
        }
    }

    /**
     * Runs a given {@link Tube} (and everything thereafter) synchronously.
     * <p/>
     * <p/>
     * This method blocks and returns only when all the successive {@link Tube}s
     * complete their request/response processing. This method can be used
     * if a {@link Tube} needs to fallback to synchronous processing.
     * <p/>
     * <h3>Example:</h3>
     * <pre>
     * class FooTube extends {@link AbstractFilterTubeImpl} {
     *   NextAction processRequest(Packet request) {
     *     // run everything synchronously and return with the response packet
     *     return doReturnWith(Fiber.current().runSync(next,request));
     *   }
     *   NextAction processResponse(Packet response) {
     *     // never be invoked
     *   }
     * }
     * </pre>
     *
     * @param tubeline The first tube of the tubeline that will act on the packet.
     * @param request  The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @return The response packet to the <tt>request</tt>.
     * @see #start(Tube, Packet, CompletionCallback)
     */
    public synchronized
    @NotNull
    Packet runSync(@NotNull Tube tubeline, @NotNull Packet request) {
        // save the current continuation, so that we return runSync() without executing them.
        final Tube[] oldCont = conts;
        final int oldContSize = contsSize;
        final boolean oldSynchronous = synchronous;
        final Tube oldNext = next;

        if (oldContSize > 0) {
            conts = new Tube[16];
            contsSize = 0;
        }

        try {
            synchronous = true;
            this.packet = request;
            next = tubeline;
            doRun();
            if (throwable != null) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }
                if (throwable instanceof Error) {
                    throw (Error) throwable;
                }
                // our system is supposed to only accept Error or RuntimeException
                throw new AssertionError(throwable);
            }
            return this.packet;
        } finally {
            conts = oldCont;
            contsSize = oldContSize;
            synchronous = oldSynchronous;
            next = oldNext;
            if(interrupted) {
                Thread.currentThread().interrupt();
                interrupted = false;
            }
            if(!started && !startedSync)
                completionCheck();
        }
    }

    private synchronized void completionCheck() {
        // Don't trigger completion and callbacks if fiber is suspended
        if(!isCanceled && contsSize==0 && suspendedCount == 0) {
            if(isTraceEnabled())
                LOGGER.fine(getName()+" completed");
            completed = true;
            clearListeners();
            notifyAll();
            if (completionCallback != null) {
                if (throwable != null)
                    completionCallback.onCompletion(throwable);
                else
                    completionCallback.onCompletion(packet);
            }
        }
    }

    ///**
    // * Blocks until the fiber completes.
    // */
    //public synchronized void join() throws InterruptedException {
    //    while(!completed)
    //        wait();
    //}

    /**
     * Invokes all registered {@link InterceptorHandler}s and then call into
     * {@link Fiber#__doRun()}.
     */
    private class InterceptorHandler implements FiberContextSwitchInterceptor.Work<Tube, Tube> {
        /**
         * Index in {@link Fiber#interceptors} to invoke next.
         */
        private int idx;

        /**
         * Initiate the interception, and eventually invokes {@link Fiber#__doRun()}.
         */
        Tube invoke(Tube next) {
            idx = 0;
            return execute(next);
        }

        public Tube execute(Tube next) {
            if (idx == interceptors.size()) {
                Fiber.this.next = next;
                __doRun();
            } else {
                FiberContextSwitchInterceptor interceptor = interceptors.get(idx++);
                return interceptor.execute(Fiber.this, next, this);
            }
            return Fiber.this.next;
        }
    }

    /**
     * Executes the fiber as much as possible.
     *
     */
    @SuppressWarnings({"LoopStatementThatDoesntLoop"}) // IntelliJ reports this bogus error
    private void doRun() {

        dumpFiberContext("running");

        if (serializeExecution) {
            serializedExecutionLock.lock();
            try {
                _doRun(next);
            } finally {
                serializedExecutionLock.unlock();
            }
        } else {
            _doRun(next);
        }
    }

    private String currentThreadMonitor = "CurrentThreadMonitor";

    private void _doRun(Tube next) {
        Thread thread;
        synchronized(currentThreadMonitor) {
          if (currentThread != null && !synchronous) {
            if (LOGGER.isLoggable(Level.FINE)) {
              LOGGER.fine("Attempt to run Fiber ['" + this + "'] in more than one thread. Current Thread: " + currentThread + " Attempted Thread: " + Thread.currentThread());
            }
            while (currentThread != null) {
              try {
                currentThreadMonitor.wait();
              } catch (Exception e) {
                // ignore
              }
            }
          }
          currentThread = Thread.currentThread();
          thread = currentThread;
          if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Thread entering _doRun(): " + thread);
          }
        }

        ClassLoader old = thread.getContextClassLoader();
        thread.setContextClassLoader(contextClassLoader);
        try {
            do {
                needsToReenter = false;

                // if interceptors are set, go through the interceptors.
                if(interceptorHandler ==null) {
                    this.next = next;
                    __doRun();
                }
                else
                    next = interceptorHandler.invoke(next);
            } while (needsToReenter);

        } finally {
            thread.setContextClassLoader(old);
            synchronized(currentThreadMonitor) {
                currentThread = null;
              if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Thread leaving _doRun(): " + thread);
              }
              currentThreadMonitor.notify();
            }
        }
    }

    /**
     * To be invoked from {@link #doRun()}.
     *
     * @see #doRun()
     */
    private void __doRun() {
        final Fiber old = CURRENT_FIBER.get();
        CURRENT_FIBER.set(this);

        // if true, lots of debug messages to show what's being executed
        final boolean traceEnabled = LOGGER.isLoggable(Level.FINER);

        try {
            boolean abortResponse = false;
            boolean justSuspended = false;
            while(!isCanceled && !isBlocking(justSuspended) && !needsToReenter) {
                try {
                    NextAction na;
                    Tube last;
                    if(throwable!=null) {
                        if(contsSize==0 || abortResponse) {
                            contsSize = 0; // abortResponse case
                            // nothing else to execute. we are done.
                            return;
                        }
                        last = popCont();
                        if (traceEnabled)
                            LOGGER.finer(getName() + ' ' + last + ".processException(" + throwable + ')');
                        na = last.processException(throwable);
                    } else {
                        if(next!=null) {
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+next+".processRequest("+(packet != null ? "Packet@"+Integer.toHexString(packet.hashCode()) : "null")+')');
                            na = next.processRequest(packet);
                            last = next;
                        } else {
                            if(contsSize==0 || abortResponse) {
                                // nothing else to execute. we are done.
                                contsSize = 0;
                                return;
                            }
                            last = popCont();
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+last+".processResponse("+(packet != null ? "Packet@"+Integer.toHexString(packet.hashCode()) : "null")+')');
                            na = last.processResponse(packet);
                        }
                    }

                    if (traceEnabled)
                        LOGGER.finer(getName() + ' ' + last + " returned with " + na);

                    // If resume is called before suspend, then make sure
                    // resume(Packet) is not lost
                    if (na.kind != NextAction.SUSPEND) {
                        // preserve in-flight packet so that processException may inspect
                        if (na.kind != NextAction.THROW &&
                          na.kind != NextAction.THROW_ABORT_RESPONSE)
                                packet = na.packet;
                        throwable = na.throwable;
                    }

                    switch(na.kind) {
                    case NextAction.INVOKE:
                    case NextAction.INVOKE_ASYNC:
                        pushCont(last);
                        // fall through next
                    case NextAction.INVOKE_AND_FORGET:
                        next = na.next;
                        if (na.kind == NextAction.INVOKE_ASYNC
                            && startedSync) {
                          // Break out here
                          return;
                        }
                        break;
                    case NextAction.THROW_ABORT_RESPONSE:
                    case NextAction.ABORT_RESPONSE:
                        abortResponse = true;
                        if (LOGGER.isLoggable(Level.FINE)) {
                          LOGGER.fine("Fiber " + this + " is aborting a response due to exception: " + na.throwable);
                        }
                    case NextAction.RETURN:
                    case NextAction.THROW:
                        next = null;
                        break;
                    case NextAction.SUSPEND:
                        if (next != null) {
                          // Only store the 'last' tube when we're processing
                          // a request, since conts array is for processResponse
                          pushCont(last);
                        }
                        next = na.next;
                        justSuspended = suspend();
                        break;
                    default:
                        throw new AssertionError();
                    }
                } catch (RuntimeException t) {
                    if (traceEnabled)
                        LOGGER.log(Level.FINER, getName() + " Caught " + t + ". Start stack unwinding", t);
                    throwable = t;
                } catch (Error t) {
                    if (traceEnabled)
                        LOGGER.log(Level.FINER, getName() + " Caught " + t + ". Start stack unwinding", t);
                    throwable = t;
                }

                dumpFiberContext("After tube execution");
            }

            if (isCanceled) {
                next = null;
                throwable = null;
                contsSize = 0;
            }

            // there's nothing we can execute right away.
            // we'll be back when this fiber is resumed.

        } finally {
            CURRENT_FIBER.set(old);
        }
    }

    private void pushCont(Tube tube) {
        conts[contsSize++] = tube;

        // expand if needed
        int len = conts.length;
        if (contsSize == len) {
            Tube[] newBuf = new Tube[len * 2];
            System.arraycopy(conts, 0, newBuf, 0, len);
            conts = newBuf;
        }
    }

    private Tube popCont() {
        return conts[--contsSize];
    }

    private Tube peekCont() {
        int index = contsSize - 1;
        if (index >= 0 && index < conts.length) {
          return conts[index];
        } else {
          return null;
        }
    }

    /**
     * Only to be used by Tubes that manipulate the Fiber to create alternate flows
     * @since 2.2.6
     */
    public void resetCont(Tube[] conts, int contsSize) {
        this.conts = conts;
        this.contsSize = contsSize;
    }

    /**
     * Returns true if the fiber needs to block its execution.
     */
    private boolean isBlocking(boolean justSuspended) {
        if (synchronous) {
            while (suspendedCount == 1)
                try {
                    if (isTraceEnabled()) {
                        LOGGER.fine(getName() + " is blocking thread " + Thread.currentThread().getName());
                    }
                    wait(); // the synchronized block is the whole runSync method.
                } catch (InterruptedException e) {
                    // remember that we are interrupted, but don't respond to it
                    // right away. This behavior is in line with what happens
                    // when you are actually running the whole thing synchronously.
                    interrupted = true;
                }
            return false;
        }
        else
            return justSuspended || suspendedCount==1;
    }

    private String getName() {
        return "engine-" + owner.id + "fiber-" + id;
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Gets the current {@link Packet} associated with this fiber.
     * <p/>
     * <p/>
     * This method returns null if no packet has been associated with the fiber yet.
     */
    public
    @Nullable
    Packet getPacket() {
        return packet;
    }

    /**
     * Returns completion callback associated with this Fiber
     * @return Completion callback
     * @since 2.2.6
     */
    public CompletionCallback getCompletionCallback() {
        return completionCallback;
    }

    /**
     * Updates completion callback associated with this Fiber
     * @param completionCallback Completion callback
     * @since 2.2.6
     */
    public void setCompletionCallback(CompletionCallback completionCallback) {
        this.completionCallback = completionCallback;
    }

    /**
     * (ADVANCED) Returns true if the current fiber is being executed synchronously.
     * <p/>
     * <p/>
     * Fiber may run synchronously for various reasons. Perhaps this is
     * on client side and application has invoked a synchronous method call.
     * Perhaps this is on server side and we have deployed on a synchronous
     * transport (like servlet.)
     * <p/>
     * <p/>
     * When a fiber is run synchronously (IOW by {@link #runSync(Tube, Packet)}),
     * further invocations to {@link #runSync(Tube, Packet)} can be done
     * without degrading the performance.
     * <p/>
     * <p/>
     * So this value can be used as a further optimization hint for
     * advanced {@link Tube}s to choose the best strategy to invoke
     * the next {@link Tube}. For example, a tube may want to install
     * a {@link FiberContextSwitchInterceptor} if running async, yet
     * it might find it faster to do {@link #runSync(Tube, Packet)}
     * if it's already running synchronously.
     */
    public static boolean isSynchronous() {
        return current().synchronous;
    }

    /**
     * Returns true if the current Fiber on the current thread was started
     * synchronously. Note, this is not strictly the same as being synchronous
     * because the assumption is that the Fiber will ultimately be dispatched
     * asynchronously, possibly have a completion callback associated with it, etc.
     * Note, the 'startedSync' flag is cleared once the current Fiber is
     * converted to running asynchronously.
     * @since 2.2.6
     */
    public boolean isStartedSync() {
        return startedSync;
    }

    /**
     * Gets the current fiber that's running.
     * <p/>
     * <p/>
     * This works like {@link Thread#currentThread()}.
     * This method only works when invoked from {@link Tube}.
     */
    public static
    @NotNull
    Fiber current() {
        Fiber fiber = CURRENT_FIBER.get();
        if (fiber == null)
            throw new IllegalStateException("Can be only used from fibers");
        return fiber;
    }

    /**
     * Gets the current fiber that's running, if set.
     */
    public static Fiber getCurrentIfSet() {
        return CURRENT_FIBER.get();
    }

    private static final ThreadLocal<Fiber> CURRENT_FIBER = new ThreadLocal<Fiber>();

    /**
     * Used to allocate unique number for each fiber.
     */
    private static final AtomicInteger iotaGen = new AtomicInteger();

    private static boolean isTraceEnabled() {
        return LOGGER.isLoggable(Level.FINE);
    }

    private static final Logger LOGGER = Logger.getLogger(Fiber.class.getName());


    private static final ReentrantLock serializedExecutionLock = new ReentrantLock();

    /**
     * Set this boolean to true to execute fibers sequentially one by one.
     * See class javadoc.
     */
    public static volatile boolean serializeExecution = Boolean.getBoolean(Fiber.class.getName() + ".serialize");

    private final Set<Component> components = new CopyOnWriteArraySet<Component>();

        public <S> S getSPI(Class<S> spiType) {
                for(Component c : components) {
                        S spi = c.getSPI(spiType);
                        if (spi != null)
                                return spi;
                }
                return null;
        }

        public Set<Component> getComponents() {
                return components;
        }
}
