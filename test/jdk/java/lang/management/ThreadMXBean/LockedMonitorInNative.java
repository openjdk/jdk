/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test ThreadMXBean.getLockedMonitors returns information about an object
 *    monitor lock entered with a synchronized native method or JNI MonitorEnter
 * @run junit/othervm LockedMonitorInNative
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

public class LockedMonitorInNative {

    @BeforeAll
    static void setup() throws Exception {
        System.loadLibrary("LockedMonitorInNative");
    }

    /**
     * Test ThreadMXBean.getLockedMonitors returns information about an object
     * monitor lock entered with a synchronized native method.
     */
    @Test
    void testSynchronizedNative() {
        Object lock = this;
        runWithSynchronizedNative(() -> {
            assertTrue(holdsLock(lock), "Thread does not hold lock");
        });
    }

    /**
     * Test ThreadMXBean.getLockedMonitors returns information about an object
     * monitor lock entered with JNI MonitorEnter.
     */
    @Test
    void testMonitorEnteredInNative() {
        var lock = new Object();
        runWithMonitorEnteredInNative(lock, () -> {
            assertTrue(holdsLock(lock), "Thread does not hold lock");
        });
    }

    private boolean holdsLock(Object lock) {
        int hc = System.identityHashCode(lock);
        long tid = Thread.currentThread().threadId();
        ThreadInfo ti = ManagementFactory.getPlatformMXBean(ThreadMXBean.class)
                .getThreadInfo(new long[] { tid }, true, true)[0];
        return Arrays.stream(ti.getLockedMonitors())
                .anyMatch(mi -> mi.getIdentityHashCode() == hc);
    }

    /**
     * Invokes the given task's run method while holding the monitor for "this".
     */
    private synchronized native void runWithSynchronizedNative(Runnable task);

    /**
     * Invokes the given task's run method while holding the monitor for the given
     * object. The monitor is entered with JNI MonitorEnter, and exited with JNI MonitorExit.
     */
    private native void runWithMonitorEnteredInNative(Object lock, Runnable task);

    /**
     * Called from native methods to run the given task.
     */
    private void run(Runnable task) {
        task.run();
    }
}
