/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8311867
 * @summary Stress test of StructuredTaskScope.shutdown with running and starting threads
 * @enablePreview
 * @run junit StressShutdown
 */

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class StressShutdown {

    static final Callable<Void> SLEEP_FOR_A_DAY = () -> {
        Thread.sleep(Duration.ofDays(1));
        return null;
    };

    static Stream<Arguments> testCases() {
        Stream<ThreadFactory> factories = Stream.of(
                Thread.ofPlatform().factory(),
                Thread.ofVirtual().factory()
        );
        // 0..15 forks before shutdown, 0..15 forks after shutdown
        return factories.flatMap(f -> IntStream.range(0, 256)
                .mapToObj(x -> Arguments.of(f, x & 0x0F, (x & 0xF0) >> 4)));
    }

    /**
     * Test StructuredTaskScope.shutdown with running threads and concurrently with
     * threads that are starting. The shutdown should interrupt all threads so that
     * join wakes up.
     *
     * @param factory the ThreadFactory to use
     * @param beforeShutdown the number of subtasks to fork before shutdown
     * @param afterShutdown the number of subtasks to fork after shutdown
     */
    @ParameterizedTest
    @MethodSource("testCases")
    void testShutdown(ThreadFactory factory, int beforeShutdown, int afterShutdown)
        throws InterruptedException
    {
        try (var scope = new StructuredTaskScope<>(null, factory)) {
            // fork subtasks
            for (int i = 0; i < beforeShutdown; i++) {
                scope.fork(SLEEP_FOR_A_DAY);
            }

            // fork subtask to shutdown
            scope.fork(() -> {
                scope.shutdown();
                return null;
            });

            // fork after forking subtask to shutdown
            for (int i = 0; i < afterShutdown; i++) {
                scope.fork(SLEEP_FOR_A_DAY);
            }

            scope.join();
        }
    }
}
