/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 4607272 8364761
 * @summary tests tasks can be submitted to a channel group's thread pool.
 * @run junit AsExecutor
 */

import java.io.IOException;
import java.nio.channels.AsynchronousChannelGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AsExecutor {
    private static ThreadFactory factory;

    @BeforeAll
    public static void createThreadFactory() {
         factory = Executors.defaultThreadFactory();
    }

    private static Stream<Arguments> channelGroups() throws IOException {
        List<Arguments> list = new ArrayList<Arguments>();
        list.add(Arguments.of(AsynchronousChannelGroup
            .withFixedThreadPool(5, factory)));
        list.add(Arguments.of(AsynchronousChannelGroup
            .withCachedThreadPool(Executors.newCachedThreadPool(factory), 0)));
        list.add(Arguments.of(AsynchronousChannelGroup
            .withThreadPool(Executors.newFixedThreadPool(10, factory))));
        return list.stream();
    }

    @ParameterizedTest
    @MethodSource("channelGroups")
    public void simpleTask(AsynchronousChannelGroup group)
        throws InterruptedException
    {
        try {
            Executor executor = (Executor)group;
            final CountDownLatch latch = new CountDownLatch(1);
            executor.execute(new Runnable() {
                    public void run() {
                        latch.countDown();
                    }
                });
            latch.await();
        } finally {
            group.shutdown();
        }
    }

    @ParameterizedTest
    @MethodSource("channelGroups")
    public void nullTask(AsynchronousChannelGroup group) {
        Executor executor = (Executor)group;
        try {
            assertThrows(NullPointerException.class,
                         () -> executor.execute(null));
        } finally {
            group.shutdown();
        }
    }
}
