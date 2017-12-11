/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import jdk.incubator.http.internal.common.Pair;
import jdk.incubator.http.internal.common.SequentialScheduler;
import jdk.incubator.http.internal.common.SequentialScheduler.DeferredCompleter;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class MockReceiver extends Receiver {

    private final Iterator<Pair<CompletionStage<?>, Consumer<MessageStreamConsumer>>> iterator;
    private final MessageStreamConsumer consumer;

    public MockReceiver(MessageStreamConsumer consumer, RawChannel channel,
                        Pair<CompletionStage<?>, Consumer<MessageStreamConsumer>>... pairs) {
        super(consumer, channel);
        this.consumer = consumer;
        iterator = Arrays.asList(pairs).iterator();
    }

    @Override
    protected SequentialScheduler createScheduler() {
        class X { // Class is hack needed to allow the task to refer to the scheduler
            SequentialScheduler scheduler = new SequentialScheduler(task());

            SequentialScheduler.RestartableTask task() {
                return new SequentialScheduler.RestartableTask() {
                    @Override
                    public void run(DeferredCompleter taskCompleter) {
                        if (!scheduler.isStopped() && !demand.isFulfilled()) {
                            if (!iterator.hasNext()) {
                                taskCompleter.complete();
                                return;
                            }
                            Pair<CompletionStage<?>, Consumer<MessageStreamConsumer>> p = iterator.next();
                            CompletableFuture<?> cf = p.first.toCompletableFuture();
                            if (cf.isDone()) { // Forcing synchronous execution
                                p.second.accept(consumer);
                                repeat(taskCompleter);
                            } else {
                                cf.whenCompleteAsync((r, e) -> {
                                    p.second.accept(consumer);
                                    repeat(taskCompleter);
                                });
                            }
                        } else {
                            taskCompleter.complete();
                        }
                    }

                    private void repeat(DeferredCompleter taskCompleter) {
                        taskCompleter.complete();
                        scheduler.runOrSchedule();
                    }
                };
            }
        }
        return new X().scheduler;
    }
}
