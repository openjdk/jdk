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
import com.sun.istack.internal.Nullable;
import com.sun.xml.internal.ws.api.message.Packet;
import com.sun.xml.internal.ws.api.pipe.helper.AbstractFilterTubeImpl;
import com.sun.xml.internal.ws.api.server.Adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * User-level thread&#x2E; Represents the execution of one request/response processing.
 *
 * <p>
 * JAX-WS RI is capable of running a large number of request/response concurrently by
 * using a relatively small number of threads. This is made possible by utilizing
 * a {@link Fiber} &mdash; a user-level thread that gets created for each request/response
 * processing.
 *
 * <p>
 * A fiber remembers where in the pipeline the processing is at, what needs to be
 * executed on the way out (when processing response), and other additional information
 * specific to the execution of a particular request/response.
 *
 * <h2>Suspend/Resume</h2>
 * <p>
 * Fiber can be {@link NextAction#suspend() suspended} by a {@link Tube}.
 * When a fiber is suspended, it will be kept on the side until it is
 * {@link #resume(Packet) resumed}. This allows threads to go execute
 * other runnable fibers, allowing efficient utilization of smaller number of
 * threads.
 *
 * <h2>Context-switch Interception</h2>
 * <p>
 * {@link FiberContextSwitchInterceptor} allows {@link Tube}s and {@link Adapter}s
 * to perform additional processing every time a thread starts running a fiber
 * and stops running it.
 *
 * <h2>Context ClassLoader</h2>
 * <p>
 * Just like thread, a fiber has a context class loader (CCL.) A fiber's CCL
 * becomes the thread's CCL when it's executing the fiber. The original CCL
 * of the thread will be restored when the thread leaves the fiber execution.
 *
 *
 * <h2>Debugging Aid</h2>
 * <p>
 * Because {@link Fiber} doesn't keep much in the call stack, and instead use
 * {@link #conts} to store the continuation, debugging fiber related activities
 * could be harder.
 *
 * <p>
 * Setting the {@link #LOGGER} for FINE would give you basic start/stop/resume/suspend
 * level logging. Using FINER would cause more detailed logging, which includes
 * what tubes are executed in what order and how they behaved.
 *
 * <p>
 * When you debug the server side, consider setting {@link Fiber#serializeExecution}
 * to true, so that execution of fibers are serialized. Debugging a server
 * with more than one running threads is very tricky, and this switch will
 * prevent that. This can be also enabled by setting the system property on.
 * See the source code.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public final class Fiber implements Runnable {
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
     *
     * <p>
     * Logically this is just a boolean, but we need to prepare for the case
     * where the thread is {@link #resume(Packet) resumed} before we get to the {@link #suspend()}.
     * This happens when things happen in the following order:
     *
     * <ol>
     *  <li>Tube decides that the fiber needs to be suspended to wait for the external event.
     *  <li>Tube hooks up fiber with some external mechanism (like NIO channel selector)
     *  <li>Tube returns with {@link NextAction#suspend()}.
     *  <li>"External mechanism" becomes signal state and invokes {@link Fiber#resume(Packet)}
     *      to wake up fiber
     *  <li>{@link Fiber#doRun} invokes {@link Fiber#suspend()}.
     * </ol>
     *
     * <p>
     * Using int, this will work OK because {@link #suspendedCount} becomes -1 when
     * {@link #resume(Packet)} occurs before {@link #suspend()}.
     *
     * <p>
     * Increment and decrement is guarded by 'this' object.
     */
    private volatile int suspendedCount = 0;

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
     *
     * When that happens, we need to first exit the current interceptors
     * and then reenter them, so that the newly added interceptors start
     * taking effect. This flag is used to control that flow.
     */
    private boolean needsToReenter;

    /**
     * Fiber's context {@link ClassLoader}.
     */
    private @Nullable ClassLoader contextClassLoader;

    private @Nullable CompletionCallback completionCallback;

    /**
     * Set to true if this fiber is started asynchronously, to avoid
     * doubly-invoking completion code.
     */
    private boolean started;

    /**
     * Callback to be invoked when a {@link Fiber} finishs execution.
     */
    public interface CompletionCallback {
        /**
         * Indicates that the fiber has finished its execution.
         *
         * <p>
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
        if(isTraceEnabled()) {
            id = iotaGen.incrementAndGet();
            LOGGER.fine(getName()+" created");
        } else {
            id = -1;
        }

        // if this is run from another fiber, then we naturally inherit its context classloader,
        // so this code works for fiber->fiber inheritance just fine.
        contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Starts the execution of this fiber asynchronously.
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
     * @see #runSync(Tube,Packet)
     */
    public void start(@NotNull Tube tubeline, @NotNull Packet request, @Nullable CompletionCallback completionCallback) {
        next = tubeline;
        this.packet = request;
        this.completionCallback = completionCallback;
        this.started = true;
        owner.addRunnable(this);
    }

    /**
     * Wakes up a suspended fiber.
     *
     * <p>
     * If a fiber was suspended without specifying the next {@link Tube},
     * then the execution will be resumed in the response processing direction,
     * by calling the {@link Tube#processResponse(Packet)} method on the next/first
     * {@link Tube} in the {@link Fiber}'s processing stack with the specified resume
     * packet as the parameter.
     *
     * <p>
     * If a fiber was suspended with specifying the next {@link Tube},
     * then the execution will be resumed in the request processing direction,
     * by calling the next tube's {@link Tube#processRequest(Packet)} method with the
     * specified resume packet as the parameter.
     *
     * <p>
     * This method is implemented in a race-free way. Another thread can invoke
     * this method even before this fiber goes into the suspension mode. So the caller
     * need not worry about synchronizing {@link NextAction#suspend()} and this method.
     *
     * @param resumePacket packet used in the resumed processing
     */
    public synchronized void resume(@NotNull Packet resumePacket) {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" resumed");
        packet = resumePacket;
        if( --suspendedCount == 0 ) {
            if(synchronous) {
                notifyAll();
            } else {
                owner.addRunnable(this);
            }
        }
    }


    /**
     * Suspends this fiber's execution until the resume method is invoked.
     *
     * The call returns immediately, and when the fiber is resumed
     * the execution picks up from the last scheduled continuation.
     */
    private synchronized void suspend() {
        if(isTraceEnabled())
            LOGGER.fine(getName()+" suspended");
        suspendedCount++;
    }

    /**
     * Adds a new {@link FiberContextSwitchInterceptor} to this fiber.
     *
     * <p>
     * The newly installed fiber will take effect immediately after the current
     * tube returns from its {@link Tube#processRequest(Packet)} or
     * {@link Tube#processResponse(Packet)}, before the next tube begins processing.
     *
     * <p>
     * So when the tubeline consists of X and Y, and when X installs an interceptor,
     * the order of execution will be as follows:
     *
     * <ol>
     *  <li>X.processRequest()
     *  <li>interceptor gets installed
     *  <li>interceptor.execute() is invoked
     *  <li>Y.processRequest()
     * </ol>
     */
    public void addInterceptor(@NotNull FiberContextSwitchInterceptor interceptor) {
        if(interceptors ==null) {
            interceptors = new ArrayList<FiberContextSwitchInterceptor>();
            interceptorHandler = new InterceptorHandler();
        }
        interceptors.add(interceptor);
        needsToReenter = true;
    }

    /**
     * Removes a {@link FiberContextSwitchInterceptor} from this fiber.
     *
     * <p>
     * The removal of the interceptor takes effect immediately after the current
     * tube returns from its {@link Tube#processRequest(Packet)} or
     * {@link Tube#processResponse(Packet)}, before the next tube begins processing.
     *
     *
     * <p>
     * So when the tubeline consists of X and Y, and when Y uninstalls an interceptor
     * on the way out, then the order of execution will be as follows:
     *
     * <ol>
     *  <li>Y.processResponse() (notice that this happens with interceptor.execute() in the callstack)
     *  <li>interceptor gets uninstalled
     *  <li>interceptor.execute() returns
     *  <li>X.processResponse()
     * </ol>
     *
     * @return
     *      true if the specified interceptor was removed. False if
     *      the specified interceptor was not registered with this fiber to begin with.
     */
    public boolean removeInterceptor(@NotNull FiberContextSwitchInterceptor interceptor) {
        if(interceptors !=null && interceptors.remove(interceptor)) {
            needsToReenter = true;
            return true;
        }
        return false;
    }

    /**
     * Gets the context {@link ClassLoader} of this fiber.
     */
    public @Nullable ClassLoader getContextClassLoader() {
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
        next = doRun(next);
        completionCheck();
    }

    /**
     * Runs a given {@link Tube} (and everything thereafter) synchronously.
     *
     * <p>
     * This method blocks and returns only when all the successive {@link Tube}s
     * complete their request/response processing. This method can be used
     * if a {@link Tube} needs to fallback to synchronous processing.
     *
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
     * @param tubeline
     *      The first tube of the tubeline that will act on the packet.
     * @param request
     *      The request packet to be passed to <tt>startPoint.processRequest()</tt>.
     * @return
     *      The response packet to the <tt>request</tt>.
     *
     * @see #start(Tube, Packet, CompletionCallback)
     */
    public synchronized @NotNull Packet runSync(@NotNull Tube tubeline, @NotNull Packet request) {
        // save the current continuation, so that we return runSync() without executing them.
        final Tube[] oldCont = conts;
        final int oldContSize = contsSize;
        final boolean oldSynchronous = synchronous;

        if(oldContSize>0) {
            conts = new Tube[16];
            contsSize=0;
        }

        try {
            synchronous = true;
            this.packet = request;
            doRun(tubeline);
            if(throwable!=null) {
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
            if(interrupted) {
                Thread.currentThread().interrupt();
                interrupted = false;
            }
            if(!started)
                completionCheck();
        }
    }

    private synchronized void completionCheck() {
        if(contsSize==0) {
            if(isTraceEnabled())
                LOGGER.fine(getName()+" completed");
            completed = true;
            notifyAll();
            if(completionCallback!=null) {
                if(throwable!=null)
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
     * {@link Fiber#__doRun(Tube)}.
     */
    private class InterceptorHandler implements FiberContextSwitchInterceptor.Work<Tube,Tube> {
        /**
         * Index in {@link Fiber#interceptors} to invoke next.
         */
        private int idx;

        /**
         * Initiate the interception, and eventually invokes {@link Fiber#__doRun(Tube)}.
         */
        Tube invoke(Tube next) {
            idx=0;
            return execute(next);
        }

        public Tube execute(Tube next) {
            if(idx==interceptors.size()) {
                return __doRun(next);
            } else {
                FiberContextSwitchInterceptor interceptor = interceptors.get(idx++);
                return interceptor.execute(Fiber.this,next,this);
            }
        }
    }

    /**
     * Executes the fiber as much as possible.
     *
     * @param next
     *      The next tube whose {@link Tube#processRequest(Packet)} is to be invoked. If null,
     *      that means we'll just call {@link Tube#processResponse(Packet)} on the continuation.
     *
     * @return
     *      If non-null, the next time execution resumes, it should resume from calling
     *      the {@link Tube#processRequest(Packet)}. Otherwise it means just finishing up
     *      the continuation.
     */
    @SuppressWarnings({"LoopStatementThatDoesntLoop"}) // IntelliJ reports this bogus error
    private Tube doRun(Tube next) {
        Thread currentThread = Thread.currentThread();

        if(isTraceEnabled())
            LOGGER.fine(getName()+" running by "+currentThread.getName());

        if(serializeExecution) {
            serializedExecutionLock.lock();
            try {
                return _doRun(next);
            } finally {
                serializedExecutionLock.unlock();
            }
        } else {
            return _doRun(next);
        }
    }

    private Tube _doRun(Tube next) {
        Thread currentThread = Thread.currentThread();

        ClassLoader old = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(contextClassLoader);
        try {
            do {
                needsToReenter = false;

                // if interceptors are set, go through the interceptors.
                if(interceptorHandler ==null)
                    next = __doRun(next);
                else
                    next = interceptorHandler.invoke(next);
            } while(needsToReenter);

            return next;
        } finally {
            currentThread.setContextClassLoader(old);
        }
    }

    /**
     * To be invoked from {@link #doRun(Tube)}.
     *
     * @see #doRun(Tube)
     */
    private Tube __doRun(Tube next) {
        final Fiber old = CURRENT_FIBER.get();
        CURRENT_FIBER.set(this);

        // if true, lots of debug messages to show what's being executed
        final boolean traceEnabled = LOGGER.isLoggable(Level.FINER);

        try {
            while(!isBlocking() && !needsToReenter) {
                try {
                    NextAction na;
                    Tube last;
                    if(throwable!=null) {
                        if(contsSize==0) {
                            // nothing else to execute. we are done.
                            return null;
                        }
                        last = popCont();
                        if(traceEnabled)
                            LOGGER.finer(getName()+' '+last+".processException("+throwable+')');
                        na = last.processException(throwable);
                    } else {
                        if(next!=null) {
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+next+".processRequest("+packet+')');
                            na = next.processRequest(packet);
                            last = next;
                        } else {
                            if(contsSize==0) {
                                // nothing else to execute. we are done.
                                return null;
                            }
                            last = popCont();
                            if(traceEnabled)
                                LOGGER.finer(getName()+' '+last+".processResponse("+packet+')');
                            na = last.processResponse(packet);
                        }
                    }

                    if(traceEnabled)
                        LOGGER.finer(getName()+' '+last+" returned with "+na);

                    // If resume is called before suspend, then make sure
                                        // resume(Packet) is not lost
                    if (na.kind != NextAction.SUSPEND) {
                        packet = na.packet;
                        throwable = na.throwable;
                    }

                    switch(na.kind) {
                    case NextAction.INVOKE:
                        pushCont(last);
                        // fall through next
                    case NextAction.INVOKE_AND_FORGET:
                        next = na.next;
                        break;
                    case NextAction.RETURN:
                    case NextAction.THROW:
                        next = null;
                        break;
                    case NextAction.SUSPEND:
                        pushCont(last);
                        next = na.next;
                        suspend();
                        break;
                    default:
                        throw new AssertionError();
                    }
                } catch (RuntimeException t) {
                    if(traceEnabled)
                        LOGGER.log(Level.FINER,getName()+" Caught "+t+". Start stack unwinding",t);
                    throwable = t;
                } catch (Error t) {
                    if(traceEnabled)
                        LOGGER.log(Level.FINER,getName()+" Caught "+t+". Start stack unwinding",t);
                    throwable = t;
                }
            }
            // there's nothing we can execute right away.
            // we'll be back when this fiber is resumed.
            return next;
        } finally {
            CURRENT_FIBER.set(old);
        }
    }

    private void pushCont(Tube tube) {
        conts[contsSize++] = tube;

        // expand if needed
        int len = conts.length;
        if(contsSize==len) {
            Tube[] newBuf = new Tube[len*2];
            System.arraycopy(conts,0,newBuf,0,len);
            conts = newBuf;
        }
    }

    private Tube popCont() {
        return conts[--contsSize];
    }

    /**
     * Returns true if the fiber needs to block its execution.
     */
    // TODO: synchronization on synchronous case is wrong.
    private boolean isBlocking() {
        if(synchronous) {
            while(suspendedCount==1)
                try {
                    if (isTraceEnabled()) {
                        LOGGER.fine(getName()+" is blocking thread "+Thread.currentThread().getName());
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
            return suspendedCount==1;
    }

    private String getName() {
        return "engine-"+owner.id+"fiber-"+id;
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Gets the current {@link Packet} associated with this fiber.
     *
     * <p>
     * This method returns null if no packet has been associated with the fiber yet.
     */
    public @Nullable Packet getPacket() {
        return packet;
    }

    /**
     * Returns true if this fiber is still running or suspended.
     */
    public boolean isAlive() {
        return !completed;
    }

    /**
     * (ADVANCED) Returns true if the current fiber is being executed synchronously.
     *
     * <p>
     * Fiber may run synchronously for various reasons. Perhaps this is
     * on client side and application has invoked a synchronous method call.
     * Perhaps this is on server side and we have deployed on a synchronous
     * transport (like servlet.)
     *
     * <p>
     * When a fiber is run synchronously (IOW by {@link #runSync(Tube, Packet)}),
     * further invocations to {@link #runSync(Tube, Packet)} can be done
     * without degrading the performance.
     *
     * <p>
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
     * Gets the current fiber that's running.
     *
     * <p>
     * This works like {@link Thread#currentThread()}.
     * This method only works when invoked from {@link Tube}.
     */
    public static @NotNull Fiber current() {
        Fiber fiber = CURRENT_FIBER.get();
        if(fiber==null)
            throw new IllegalStateException("Can be only used from fibers");
        return fiber;
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
    public static volatile boolean serializeExecution = Boolean.getBoolean(Fiber.class.getName()+".serialize");
}
