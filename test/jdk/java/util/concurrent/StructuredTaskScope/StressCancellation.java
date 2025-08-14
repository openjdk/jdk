/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Stress test of StructuredTaskScope cancellation with running and starting threads
 * @enablePreview
 * @run junit StressCancellation
 */

import java.time.Duration;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.ThreadFactory;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;

class StressCancellation {

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
     * Test StructuredTaskScope cancellation with running threads and concurrently with
     * threads that are starting. The cancellation should interrupt all running threads,
     * join should wakeup, and close would complete quickly.
     *
     * @param factory the ThreadFactory to use
     * @param beforeCancel the number of subtasks to fork before cancel
     * @param afterCancel the number of subtasks to fork after cancel
     */
    @ParameterizedTest
    @MethodSource("testCases")
    void test(ThreadFactory factory, int beforeCancel, int afterCancel) throws Exception {
        var joiner = new Joiner<Boolean, Void>() {
            @Override
            public boolean onComplete(Subtask<? extends Boolean> subtask) {
                boolean cancel = subtask.get();
                return cancel;
            }
            @Override
            public Void result() {
                return null;
            }
        };

        try (var scope = StructuredTaskScope.open(joiner, cf -> cf.withThreadFactory(factory))) {
            // fork subtasks
            for (int i = 0; i < beforeCancel; i++) {
                scope.fork(() -> {
                    Thread.sleep(Duration.ofDays(1));
                    return false;
                });
            }

            // fork subtask to cancel
            scope.fork(() -> true);

            // fork after forking subtask to cancel
            for (int i = 0; i < afterCancel; i++) {
                scope.fork(() -> {
                    Thread.sleep(Duration.ofDays(1));
                    return false;
                });
            }

            scope.join();
        }
    }
}
