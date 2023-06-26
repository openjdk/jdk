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

/**
 * @test
 * @summary Test Thread.sleep when emitting the JFR jdk.ThreadSleep event throws OOME
 * @modules java.base/jdk.internal.event
 * @compile/module=java.base jdk/internal/event/ThreadSleepEvent.java
 * @run junit ThreadSleepEventThrows
 */

import jdk.internal.event.ThreadSleepEvent;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThreadSleepEventThrows {

    /**
     * Test Thread.sleep when creating the ThreadSleepEvent fails with OOME.
     */
    @Test
    void testThreadSleepEventCreateThrows() throws Exception {
        ThreadSleepEvent.setCreateThrows(true);
        try {
            testSleep();
        } finally {
            ThreadSleepEvent.setCreateThrows(false);
        }
    }

    /**
     * Test Thread.sleep when ThreadSleepEvent.begin fails with OOME.
     */
    @Test
    void testThreadSleepEventBeginThrows() throws Exception {
        ThreadSleepEvent.setBeginThrows(true);
        try {
            testSleep();
        } finally {
            ThreadSleepEvent.setBeginThrows(false);
        }
    }

    /**
     * Test Thread.sleep when ThreadSleepEvent.commit fails with OOME.
     */
    @Test
    void testThreadSleepEventCommitThrows() throws Exception {
        ThreadSleepEvent.setCommitThrows(true);
        try {
            testSleep();
        } finally {
            ThreadSleepEvent.setCommitThrows(false);
        }
    }

    /**
     * Test Thread.sleep takes a minimum duration and doesn't throw.
     */
    private void testSleep() throws Exception {
        long start = currentTimeMillis();
        Thread.sleep(SLEEP_TIME);
        long duration = currentTimeMillis() - start;
        assertTrue(duration >= MIN_EXPECTED_DURATION,
            "Duration " + duration + "ms, expected >= " + MIN_EXPECTED_DURATION + "ms");
    }

    private static final int SLEEP_TIME = 2000;
    private static final int MIN_EXPECTED_DURATION = 1900;

    private static long currentTimeMillis() {
        return NANOSECONDS.toMillis(System.nanoTime());
    }
}
