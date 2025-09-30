/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @bug 8320515
 * @summary This test checks that ObjectMonitors with dead objects don't
 *          cause asserts, crashes, or failures when various sub-systems
 *          in the JVM find them.
 * @library /testlibrary /test/lib
 * @modules jdk.management
 */

/*
 * @test id=DetachThread
 * @requires os.family != "windows" & os.family != "aix"
 * @run main/othervm/native MonitorWithDeadObjectTest 0
 */

/*
 * @test id=DumpThreadsBeforeDetach
 * @requires os.family != "windows" & os.family != "aix"
 * @run main/othervm/native MonitorWithDeadObjectTest 1
 */

/*
 * @test id=DumpThreadsAfterDetach
 * @requires os.family != "windows" & os.family != "aix"
 * @run main/othervm/native MonitorWithDeadObjectTest 2
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class MonitorWithDeadObjectTest {
    public static native void createMonitorWithDeadObject();
    public static native void createMonitorWithDeadObjectDumpThreadsBeforeDetach();

    static {
        System.loadLibrary("MonitorWithDeadObjectTest");
    }

    private static void dumpThreadsWithLockedMonitors() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        threadBean.dumpAllThreads(true, false);
    }

    private static void testDetachThread() {
        // Create an ObjectMonitor with a dead object from an attached thread.
        // This used to provoke an assert in DetachCurrentThread.
        createMonitorWithDeadObject();
    }

    private static void testDumpThreadsBeforeDetach() {
        // Create an ObjectMonitor with a dead object from an attached thread
        // and perform a thread dump before detaching the thread.
        createMonitorWithDeadObjectDumpThreadsBeforeDetach();
    }

    private static void testDumpThreadsAfterDetach() {
        createMonitorWithDeadObject();

        // The thread dumping code used to not tolerate monitors with dead
        // objects and the detach code used to not unlock these monitors, so
        // test that we don't end up with a bug where these monitors are not
        // unlocked and then passed to the thread dumping code.
        dumpThreadsWithLockedMonitors();
    }

    public static void main(String[] args) throws Exception {
        int test = Integer.parseInt(args[0]);
        switch (test) {
            case 0: testDetachThread(); break;
            case 1: testDumpThreadsBeforeDetach(); break;
            case 2: testDumpThreadsAfterDetach(); break;
            default: throw new RuntimeException("Unknown test");
        };
    }
}
