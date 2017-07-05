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

package com.sun.xml.internal.ws.api.pipe;

import com.sun.xml.internal.ws.api.message.Packet;
import java.util.concurrent.Executor;

/**
 * Indicates what shall happen after {@link Tube#processRequest(Packet)} or
 * {@link Tube#processResponse(Packet)} returns.
 *
 * <p>
 * To allow reuse of this object, this class is mutable.
 *
 * @author Kohsuke Kawaguchi
 */
public final class NextAction {
    int kind;
    Tube next;
    Packet packet;
    /**
     * Really either {@link RuntimeException} or {@link Error}.
     */
    Throwable throwable;
    Runnable onExitRunnable;

    // public enum Kind { INVOKE, INVOKE_AND_FORGET, RETURN, SUSPEND }

    static final int INVOKE = 0;
    static final int INVOKE_AND_FORGET = 1;
    static final int RETURN = 2;
    static final int THROW = 3;
    static final int SUSPEND = 4;
    // Used to abort processResponse chain if a fatal exception is encountered
    static final int THROW_ABORT_RESPONSE = 5;
    // Used to abort processResponse chain if a response should be aborted
    static final int ABORT_RESPONSE = 6;
    // Used to switch a tubeline from synchronous to asynchronous execution
    // with respect to the thread that started this tubeline.
    static final int INVOKE_ASYNC = 7;

  private void set(int k, Tube v, Packet p, Throwable t) {
        this.kind = k;
        this.next = v;
        this.packet = p;
        this.throwable = t;
    }

    /**
     * Indicates that the next action should be to
     * invoke the next tube's {@link Tube#processRequest(Packet)},
     * then later invoke the current tube's {@link Tube#processResponse(Packet)}
     * with the response packet.
     */
    public void invoke(Tube next, Packet p) {
        set(INVOKE, next, p, null);
    }

    /**
     * Indicates that the next action should be to
     * invoke the next tube's {@link Tube#processRequest(Packet)},
     * but the current tube doesn't want to receive the response packet to
     * its {@link Tube#processResponse(Packet)}.
     */
    public void invokeAndForget(Tube next, Packet p) {
        set(INVOKE_AND_FORGET, next, p, null);
    }

    /**
     * Indicates that the next action is to flip the processing direction
     * and starts response processing.
     */
    public void returnWith( Packet response ) {
        set(RETURN, null, response, null);
    }

    /**
     * Indicates that the next action is to flip the processing direction
     * and starts exception processing, but with the indicated context.
     *
     * @param t
     *      Either {@link RuntimeException} or {@link Error}, but defined to
     *      take {@link Throwable} because {@link Tube#processException(Throwable)}
     *      takes {@link Throwable}.
     */
    public void throwException( Packet response, Throwable t ) {
        // Use of RETURN is correct -- Fiber processing does not set packet for type of THROW
        set(RETURN, null, response, t);
    }

    /**
     * Indicates that the next action is to flip the processing direction
     * and starts exception processing.
     *
     * @param t
     *      Either {@link RuntimeException} or {@link Error}, but defined to
     *      take {@link Throwable} because {@link Tube#processException(Throwable)}
     *      takes {@link Throwable}.
     */
    public void throwException(Throwable t) {
        assert t instanceof RuntimeException || t instanceof Error;
        set(THROW,null,null,t);
    }

    /**
     * Indicates that the next action is to abort the processResponse chain
     * because of an exception. How that exception is processed is not
     * defined.
     *
     * @param t
     *      Either {@link RuntimeException} or {@link Error}
     */
    public void throwExceptionAbortResponse(Throwable t) {
        set(THROW_ABORT_RESPONSE,null,null,t);
    }

    /**
     * Indicates that the next action is to abort the processResponse chain
     * because of some non-exception condition.
     *
     * @param response The response that is being aborted
     */
    public void abortResponse(Packet response) {
        set(ABORT_RESPONSE,null,response,null);
    }

    /**
     * Indicates that the next action is to invoke the next tube in the
     * tubeline async from the thread that started the tubeline. Only fibers
     * that were started using startSync should use this next action kind.
     * @param next The next tube in the tubeline
     * @param p The request to pass to the next tube
     */
    public void invokeAsync(Tube next, Packet p) {
        set(INVOKE_ASYNC,next,p,null);
    }

    /**
     * Indicates that the fiber should be suspended.
     * Once {@link Fiber#resume(Packet) resumed}, return the response processing.
     * @deprecated Use variants that pass {@link Runnable}
     */
    public void suspend() {
        suspend(null, null);
    }

    /**
     * Indicates that the fiber should be suspended.  Once the current {@link Thread}
     * exits the fiber's control loop, the onExitRunnable will be invoked.  This {@link Runnable}
     * may call {@link Fiber#resume(Packet)}; however it is still guaranteed that the current
     * Thread will return control, therefore, further processing will be handled on a {@link Thread}
     * from the {@link Executor}.  For synchronous cases, the Thread invoking this fiber cannot return
     * until fiber processing is complete; therefore, the guarantee is only that the onExitRunnable
     * will be invoked prior to completing the suspension.
     * @since 2.2.7
     */
    public void suspend(Runnable onExitRunnable) {
        suspend(null, onExitRunnable);
    }

    /**
     * Indicates that the fiber should be suspended.
     * Once {@link Fiber#resume(Packet) resumed}, resume with the
     * {@link Tube#processRequest(Packet)} on the given next tube.
     * @deprecated Use variants that pass {@link Runnable}
     */
    public void suspend(Tube next) {
        suspend(next, null);
    }

    /**
     * Indicates that the fiber should be suspended.  Once the current {@link Thread}
     * exits the fiber's control loop, the onExitRunnable will be invoked.  This {@link Runnable}
     * may call {@link Fiber#resume(Packet)}; however it is still guaranteed that the current
     * fiber will return control, therefore, further processing will be handled on a {@link Thread}
     * from the {@link Executor}.  For synchronous cases, the Thread invoking this fiber cannot return
     * until fiber processing is complete; therefore, the guarantee is only that the onExitRunnable
     * will be invoked prior to completing the suspension.
     * <p>
     * Once {@link Fiber#resume(Packet) resumed}, resume with the
     * {@link Tube#processRequest(Packet)} on the given next tube.
     * @since 2.2.7
     */
    public void suspend(Tube next, Runnable onExitRunnable) {
        set(SUSPEND, next, null, null);
        this.onExitRunnable = onExitRunnable;
    }

    /** Returns the next tube
     * @return Next tube
     */
    public Tube getNext() {
        return next;
    }

    /** Sets the next tube
     * @param next Next tube
     */
    public void setNext(Tube next) {
        this.next = next;
    }

    /**
     * Returns the last Packet
     * @return Packet
     */
    public Packet getPacket() {
        return packet;
    }

    /**
     * Returns the Throwable generated by the last Tube
     * @return the Throwable
     */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Dumps the contents to assist debugging.
     */
    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(super.toString()).append(" [");
        buf.append("kind=").append(getKindString()).append(',');
        buf.append("next=").append(next).append(',');
        buf.append("packet=").append(packet != null ? packet.toShortString() : null).append(',');
        buf.append("throwable=").append(throwable).append(']');
        return buf.toString();
    }

    /**
     * Returns {@link #kind} in a human readable string, to assist debugging.
     */
    public String getKindString() {
        switch(kind) {
        case INVOKE:            return "INVOKE";
        case INVOKE_AND_FORGET: return "INVOKE_AND_FORGET";
        case RETURN:            return "RETURN";
        case THROW:             return "THROW";
        case SUSPEND:           return "SUSPEND";
        case THROW_ABORT_RESPONSE: return "THROW_ABORT_RESPONSE";
        case ABORT_RESPONSE:    return "ABORT_RESPONSE";
        case INVOKE_ASYNC:      return "INVOKE_ASYNC";
        default:                throw new AssertionError(kind);
        }
    }
}
