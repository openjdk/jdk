/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5047639 8132785
 * @summary Check that the "java-level" APIs provide a consistent view of
 *          the thread list
 * @comment Must run in othervm mode to avoid interference from other tests.
 * @run main/othervm ThreadLists
 */
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ThreadLists {

    // Thread names permitted to appear during test:
    public static final String [] permittedThreadNames = { "ForkJoinPool", "JVMCI" };

    public static boolean isPermittedNewThread(String name) {
        for (String s : permittedThreadNames) {
            if (name.contains(s)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String args[]) {

        // Bug id : JDK-8151797
        // Use a lambda expression so that call-site cleaner thread is started
        Runnable printLambda = () -> {System.out.println("Starting Test");};
        printLambda.run();

        // get top-level thread group
        ThreadGroup top = Thread.currentThread().getThreadGroup();
        ThreadGroup parent;
        do {
            parent = top.getParent();
            if (parent != null) top = parent;
        } while (parent != null);

        // get the thread count
        int tgActiveCount = top.activeCount();

        // Now enumerate to see if we find any extras yet.
        // Ensure array is big enough for a few extras.
        Thread[] tgThreads = new Thread[tgActiveCount * 2];
        int tgNewCount = top.enumerate(tgThreads);
        Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();

        if (tgNewCount != tgActiveCount) {
            System.out.println("Found different Thread Group thread count after enumeration: tgActiveCount="
                               + tgActiveCount + " enumerated=" + tgNewCount);
        }
        if (tgNewCount != stackTraces.size()) {
            System.out.println("Found difference in counts: thread group new count="
                               + tgNewCount + " stackTraces.size()=" + stackTraces.size());
        }
        System.out.println("Initial set of enumerated threads:");
        for (int i = 0; i < tgNewCount; i++) {
            System.out.println(" - Thread: " + tgThreads[i].getName());
        }

        // Get Threads from MXBean.  Retry to ensure count and id count match.
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCountBean = 0;
        long[] threadIdsBean = null;
        do {
            System.out.println("Gathering Thread info from MXBean...");
            threadCountBean = threadBean.getThreadCount();
            threadIdsBean = threadBean.getAllThreadIds();
        } while (threadCountBean != threadIdsBean.length);

        System.out.println("ThreadGroup:              " + tgActiveCount + " active thread(s)");
        System.out.println("Thread.getAllStackTraces: " + stackTraces.size() + " stack trace(s) returned");
        System.out.println("ThreadMXBean:             " + threadCountBean + " live threads(s)");
        System.out.println("ThreadMXBean:             " + threadIdsBean.length + " thread Id(s)");

        if (threadIdsBean.length > tgActiveCount) {
            // Find the new Threads: some Thead names are permitted to appear: ignore them.
            Set<Long> seenTids = new TreeSet<>();
            for (Thread t : stackTraces.keySet()) {
                if (t != null) {
                    seenTids.add(t.getId());
                }
            }
            for (long tid : threadIdsBean) {
                if (!seenTids.contains(tid)) {
                    // New Thread from MBean, compared to Thread Group:
                    ThreadInfo threadInfo = threadBean.getThreadInfo(tid);
                    if (threadInfo != null && isPermittedNewThread(threadInfo.getThreadName())) {
                        System.out.print("New thread permitted: " + threadInfo);
                        threadCountBean--;
                    }
                }
            }
        }

        // check results are consistent
        boolean failed = false;
        if (tgActiveCount != stackTraces.size()) failed = true;
        if (tgActiveCount != threadCountBean) failed = true;
        // We know threadCountBean == threadIdsBean.length

        if (failed) {
            System.out.println("Failed.");
            System.out.println("Set of Threads from getAllStackTraces:");
            for (Thread t : stackTraces.keySet()) {
                System.out.println(" - Thread: " +
                                   (t != null ? t.getName() : "null!"));
            }
            System.out.println("Set of Thread IDs from MXBean:");
            for (long tid : threadIdsBean) {
                System.out.print(tid + " ");
                ThreadInfo threadInfo = threadBean.getThreadInfo(tid);
                System.out.println(threadInfo != null ? threadInfo.getThreadName() : "");
            }
            throw new RuntimeException("inconsistent results");
        }
    }
}
