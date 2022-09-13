/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

/**
 * Defines static methods to support execution in the context of a virtual thread.
 */
public final class VirtualThreads {
    private static final JavaLangAccess JLA;
    static {
        JLA = SharedSecrets.getJavaLangAccess();
        if (JLA == null) {
            throw new InternalError("JavaLangAccess not setup");
        }
    }
    private VirtualThreads() { }

    /**
     * Parks the current virtual thread until it is unparked or interrupted.
     * If already unparked then the parking permit is consumed and this method
     * completes immediately (meaning it doesn't yield). It also completes
     * immediately if the interrupt status is set.
     * @throws WrongThreadException if the current thread is not a virtual thread
     */
    public static void park() {
        JLA.parkVirtualThread();
    }

    /**
     * Parks the current virtual thread up to the given waiting time or until it
     * is unparked or interrupted. If already unparked then the parking permit is
     * consumed and this method completes immediately (meaning it doesn't yield).
     * It also completes immediately if the interrupt status is set or the waiting
     * time is {@code <= 0}.
     * @param nanos the maximum number of nanoseconds to wait
     * @throws WrongThreadException if the current thread is not a virtual thread
     */
    public static void park(long nanos) {
        JLA.parkVirtualThread(nanos);
    }

    /**
     * Parks the current virtual thread until the given deadline or until is is
     * unparked or interrupted. If already unparked then the parking permit is
     * consumed and this method completes immediately (meaning it doesn't yield).
     * It also completes immediately if the interrupt status is set or the
     * deadline has past.
     * @param deadline absolute time, in milliseconds, from the epoch
     * @throws WrongThreadException if the current thread is not a virtual thread
     */
    public static void parkUntil(long deadline) {
        long millis = deadline - System.currentTimeMillis();
        long nanos = TimeUnit.NANOSECONDS.convert(millis, TimeUnit.MILLISECONDS);
        park(nanos);
    }

    /**
     * Re-enables a virtual thread for scheduling. If the thread was parked then
     * it will be unblocked, otherwise its next attempt to park will not block
     * @param thread the virtual thread to unpark
     * @throws IllegalArgumentException if the thread is not a virtual thread
     * @throws RejectedExecutionException if the scheduler cannot accept a task
     */
    public static void unpark(Thread thread) {
        JLA.unparkVirtualThread(thread);
    }
}
