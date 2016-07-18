/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package java.net.http;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Objects.requireNonNull;

//
// The problem:
// ------------
//   1. For every invocation of 'signal()' there must be at least
//      1 invocation of 'handler.run()' that goes after
//   2. There must be no more than 1 thread running the 'handler.run()'
//      at any given time
//
// For example, imagine each signal increments (+1) some number. Then the
// handler responds (eventually) the way that makes the number 0.
//
// For each signal there's a response. Several signals may be handled by a
// single response.
//
final class WSSignalHandler {

    // In this state the task is neither submitted nor running.
    // No one is handling signals. If a new signal has been received, the task
    // has to be submitted to the executor in order to handle this signal.
    private static final int DONE    = 0;

    // In this state the task is running.
    // * If the signaller has found the task in this state it will try to change
    //   the state to RERUN in order to make the already running task to handle
    //   the new signal before exiting.
    // * If the task has found itself in this state it will exit.
    private static final int RUNNING = 1;

    // A signal to the task, that it must rerun on the spot (without being
    // resubmitted to the executor).
    // If the task has found itself in this state it resets the state to
    // RUNNING and repeats the pass.
    private static final int RERUN   = 2;

    private final AtomicInteger state = new AtomicInteger(DONE);

    private final Executor executor;
    private final Runnable task;

    WSSignalHandler(Executor executor, Runnable handler) {
        this.executor = requireNonNull(executor);
        requireNonNull(handler);

        task = () -> {
            while (!Thread.currentThread().isInterrupted()) {

                try {
                    handler.run();
                } catch (Exception e) {
                    // Sorry, the task won't be automatically retried;
                    // hope next signals (if any) will kick off the handling
                    state.set(DONE);
                    throw e;
                }

                int prev = state.getAndUpdate(s -> {
                    if (s == RUNNING) {
                        return DONE;
                    } else {
                        return RUNNING;
                    }
                });

                // Can't be DONE, since only the task itself may transit state
                // into DONE (with one exception: RejectedExecution in signal();
                // but in that case we couldn't be here at all)
                assert prev == RUNNING || prev == RERUN;

                if (prev == RUNNING) {
                    break;
                }
            }
        };
    }

    // Invoked by outer code to signal
    void signal() {

        int prev = state.getAndUpdate(s -> {
            switch (s) {
                case RUNNING:
                    return RERUN;
                case DONE:
                    return RUNNING;
                case RERUN:
                    return RERUN;
                default:
                    throw new InternalError(String.valueOf(s));
            }
        });

        if (prev != DONE) {
            // Nothing to do! piggybacking on previous signal
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            // Sorry some signal() invocations may have been accepted, but won't
            // be done, since the 'task' couldn't be submitted
            state.set(DONE);
            throw e;
        }
    }
}
