/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.requireNonNull;

final class CooperativeHandler {

    private static final long CONTINUE = 0;
    private static final long OFF      = 1;
    private static final long ON       = 2;
    private static final long STOP     = 4;

    private final AtomicLong state = new AtomicLong(OFF);

    private final Runnable task;

    CooperativeHandler(Runnable task) {
        this.task = requireNonNull(task);
    }

    /*
     * Causes the task supplied to the constructor to run. The task may be run
     * by this thread as well as by any other that has invoked this method.
     *
     * The recursion which is possible here will have the maximum depth of 1:
     *
     *     task.run()
     *         this.startOrContinue()
     *             task.run()
     */
    void startOrContinue() {
        long s;
        while (true) {
            s = state.get();
            if (s == OFF && state.compareAndSet(OFF, ON)) {
                // No one is running the task, we are going to run it
                break;
            }
            if (s == ON && state.compareAndSet(ON, CONTINUE)) {
                // Some other thread is running the task. We have managed to
                // update the state, it will be surely noticed by that thread.
                return;
            }
            if (s == CONTINUE || s == STOP) {
                return;
            }
        }
        while (true) {
            task.run();
            // State checks are ordered by the probability of expected values
            // (it might be different in different usage patterns, say, when
            // invocations to `startOrContinue()` are concurrent)
            if (state.compareAndSet(ON, OFF)) {
                break; // The state hasn't changed, all done
            }
            if (state.compareAndSet(CONTINUE, ON)) {
                continue;
            }
            // Other threads can change the state from CONTINUE to STOP only
            // So if it's not ON and not CONTINUE, it can only be STOP
            break;
        }
    }

    void stop() {
        state.set(STOP);
    }
}
