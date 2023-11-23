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
 * @test
 * @summary This test checks that ObjectMonitors with dead objects don't
            cause asserts, crashes, or failures when various sub-systems
            in the JVM find them.
 * @requires os.family != "windows"
 * @library /testlibrary /test/lib
 * @modules jdk.management
 * @run main/native MonitorWithDeadObjectTest
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class MonitorWithDeadObjectTest {
    public static native void createMonitorWithDeadObject();
    public static native void createMonitorWithDeadObjectNoJoin();
    public static native void createMonitorWithDeadObjectDumpThreadsBeforeDetach();

    public static native void joinTestThread();

    static {
        System.loadLibrary("MonitorWithDeadObjectTest");
    }

    private static void dumpThreadsWithLockedMonitors() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        threadBean.dumpAllThreads(true, false);
    }

    private static void testDetachThread() {
        // Create an ObjectMonitor with a dead object from an
        // attached thread.
        createMonitorWithDeadObject();
    }

    private static void testDumpThreadsBeforeDetach() {
        // Create an ObjectMonitor with a dead object from an
        // attached thread and perform a thread dump before
        // detaching the thread.
        createMonitorWithDeadObjectDumpThreadsBeforeDetach();
    }

    private static void testDumpThreadsAfterDetachBeforeJoin() {
        createMonitorWithDeadObjectNoJoin();

        // After createMonitorWithDeadObjectNoJoin has been called, there's an
        // "owned" monitor with a dead object. The thread dumping code used to
        // not tolerate such a monitor and would assert. Run a thread dump
        // and make sure that it doesn't crash/assert.
        dumpThreadsWithLockedMonitors();

        joinTestThread();
    }

    private static void testDumpThreadsAfterDetachAfterJoin() {
        createMonitorWithDeadObjectNoJoin();
        joinTestThread();

        // After createMonitorWithDeadObjectNoJoin has been called, there's an
        // "owned" monitor with a dead object. The thread dumping code used to
        // not tolerate such a monitor and would assert. Run a thread dump
        // and make sure that it doesn't crash/assert.
        dumpThreadsWithLockedMonitors();
    }

    public static void main(String[] args) throws Exception {
        testDetachThread();
        testDumpThreadsBeforeDetach();
        testDumpThreadsAfterDetachBeforeJoin();
        testDumpThreadsAfterDetachAfterJoin();
    }
}
