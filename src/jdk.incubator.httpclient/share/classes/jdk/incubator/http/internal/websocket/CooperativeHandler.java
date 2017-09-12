/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static java.util.Objects.requireNonNull;

/*
 * A synchronization aid that assists a number of parties in running a task
 * in a mutually exclusive fashion.
 *
 * To run the task, a party invokes `handle`. To permanently prevent the task
 * from subsequent runs, the party invokes `stop`.
 *
 * The parties do not have to operate in different threads.
 *
 * The task can be either synchronous or asynchronous.
 *
 * If the task is synchronous, it is represented with `Runnable`.
 * The handler invokes `Runnable.run` to run the task.
 *
 * If the task is asynchronous, it is represented with `Consumer<Runnable>`.
 * The handler invokes `Consumer.accept(end)` to begin the task. The task
 * invokes `end.run()` when it has ended.
 *
 * The next run of the task will not begin until the previous run has finished.
 *
 * The task may invoke `handle()` by itself, it's a normal situation.
 */
public final class CooperativeHandler {

    /*
       Since the task is fixed and known beforehand, no blocking synchronization
       (locks, queues, etc.) is required. The job can be done solely using
       nonblocking primitives.

       The machinery below addresses two problems:

         1. Running the task in a sequential order (no concurrent runs):

                begin, end, begin, end...

         2. Avoiding indefinite recursion:

                begin
                  end
                    begin
                      end
                        ...

       Problem #1 is solved with a finite state machine with 4 states:

           BEGIN, AGAIN, END, and STOP.

       Problem #2 is solved with a "state modifier" OFFLOAD.

       Parties invoke `handle()` to signal the task must run. A party that has
       invoked `handle()` either begins the task or exploits the party that is
       either beginning the task or ending it.

       The party that is trying to end the task either ends it or begins it
       again.

       To avoid indefinite recursion, before re-running the task tryEnd() sets
       OFFLOAD bit, signalling to its "child" tryEnd() that this ("parent")
       tryEnd() is available and the "child" must offload the task on to the
       "parent". Then a race begins. Whichever invocation of tryEnd() manages
       to unset OFFLOAD bit first does not do the work.

       There is at most 1 thread that is beginning the task and at most 2
       threads that are trying to end it: "parent" and "child". In case of a
       synchronous task "parent" and "child" are the same thread.
     */

    private static final int OFFLOAD =  1;
    private static final int AGAIN   =  2;
    private static final int BEGIN   =  4;
    private static final int STOP    =  8;
    private static final int END     = 16;

    private final AtomicInteger state = new AtomicInteger(END);
    private final Consumer<Runnable> begin;

    public CooperativeHandler(Runnable task) {
        this(asyncOf(task));
    }

    public CooperativeHandler(Consumer<Runnable> begin) {
        this.begin = requireNonNull(begin);
    }

    /*
     * Runs the task (though maybe by a different party).
     *
     * The recursion which is possible here will have the maximum depth of 1:
     *
     *     this.handle()
     *         begin.accept()
     *             this.handle()
     */
    public void handle() {
        while (true) {
            int s = state.get();
            if (s == END) {
                if (state.compareAndSet(END, BEGIN)) {
                    break;
                }
            } else if ((s & BEGIN) != 0) {
                // Tries to change the state to AGAIN, preserving OFFLOAD bit
                if (state.compareAndSet(s, AGAIN | (s & OFFLOAD))) {
                    return;
                }
            } else if ((s & AGAIN) != 0 || s == STOP) {
                return;
            } else {
                throw new InternalError(String.valueOf(s));
            }
        }
        begin.accept(this::tryEnd);
    }

    private void tryEnd() {
        while (true) {
            int s;
            while (((s = state.get()) & OFFLOAD) != 0) {
                // Tries to offload ending of the task to the parent
                if (state.compareAndSet(s, s & ~OFFLOAD)) {
                    return;
                }
            }
            while (true) {
                if (s == BEGIN) {
                    if (state.compareAndSet(BEGIN, END)) {
                        return;
                    }
                } else if (s == AGAIN) {
                    if (state.compareAndSet(AGAIN, BEGIN | OFFLOAD)) {
                        break;
                    }
                } else if (s == STOP) {
                    return;
                } else {
                    throw new InternalError(String.valueOf(s));
                }
                s = state.get();
            }
            begin.accept(this::tryEnd);
        }
    }

    /*
     * Checks whether or not this handler has been permanently stopped.
     *
     * Should be used from inside the task to poll the status of the handler,
     * pretty much the same way as it is done for threads:
     *
     *     if (!Thread.currentThread().isInterrupted()) {
     *         ...
     *     }
     */
    public boolean isStopped() {
        return state.get() == STOP;
    }

    /*
     * Signals this handler to ignore subsequent invocations to `handle()`.
     *
     * If the task has already begun, this invocation will not affect it,
     * unless the task itself uses `isStopped()` method to check the state
     * of the handler.
     */
    public void stop() {
        state.set(STOP);
    }

    private static Consumer<Runnable> asyncOf(Runnable task) {
        requireNonNull(task);
        return ender -> {
            task.run();
            ender.run();
        };
    }
}
