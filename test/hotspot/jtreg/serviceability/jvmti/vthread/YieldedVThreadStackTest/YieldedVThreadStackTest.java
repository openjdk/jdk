/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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
 * @bug 8398335
 * @summary Java and JVMTI stack walking APIs report all frames of a virtual
 *          thread that yielded and continued
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:YieldedVThreadStackTest YieldedVThreadStackTest
 */

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

/*
 * When a virtual thread continues after a yield, only its top frames are copied
 * back to the thread stack. The frames below stay in the stack chunk until they
 * are returned to. Each stack walking API must report them: the Java APIs, and
 * the JVMTI functions on the virtual thread, from the thread itself and from
 * another thread, while it is mounted and while it is unmounted.
 */
public class YieldedVThreadStackTest {
    private static final int DEPTH = 200;
    // recurse(INNER_DEPTH) holds INNER while the virtual thread yields and continues.
    private static final int INNER_DEPTH = 100;
    private static final Object INNER = new Object();
    private static final String RECURSE = "YieldedVThreadStackTest.recurse";

    // A null thread means the current thread. Frames are named "<class>.<method>",
    // with '.' between package names.
    static native String[] stackTrace(Thread thread);
    static native int frameCount(Thread thread);
    static native String frameAt(Thread thread, int depth); // via GetFrameLocation
    static native int monitorDepth(Thread thread, Object monitor); // via GetOwnedMonitorStackDepthInfo, -1 if absent
    static native void suspend(Thread thread);
    static native void resume(Thread thread);

    static final CountDownLatch continued = new CountDownLatch(1);
    static final CountDownLatch checkedMounted = new CountDownLatch(1);
    static volatile boolean parked;
    static volatile boolean checkedUnmounted;

    static void recurse(int depth) {
        if (depth == INNER_DEPTH) {
            synchronized (INNER) {
                recurse(depth - 1);
            }
            return;
        }
        if (depth > 0) {
            recurse(depth - 1);
            return;
        }
        checkAll("before the yield", null);
        Thread.yield();
        // Continued: the frames below this one are still in the stack chunk.
        checkAll("after the yield", null);
        continued.countDown();
        while (checkedMounted.getCount() > 0) {
            Thread.onSpinWait(); // stays mounted, so that the frames stay in the chunk
        }
        parked = true;
        while (!checkedUnmounted) {
            LockSupport.park();
        }
    }

    public static void main(String[] args) throws Exception {
        Throwable[] failure = new Throwable[1];
        Thread vthread = Thread.ofVirtual().start(() -> {
            try {
                recurse(DEPTH);
            } catch (Throwable t) {
                failure[0] = t;
                continued.countDown();
            }
        });
        continued.await();
        try {
            if (failure[0] != null) {
                throw new RuntimeException("virtual thread failed", failure[0]);
            }
            check("mounted, from another thread (Java)", javaFrames(vthread.getStackTrace()));
            // Suspended, so that its top frames cannot change between the JVMTI queries.
            suspend(vthread);
            try {
                checkJvmti("mounted, from another thread (JVMTI)", vthread);
            } finally {
                resume(vthread);
            }
        } finally {
            checkedMounted.countDown();
        }
        while (!parked || vthread.getState() != Thread.State.WAITING) {
            Thread.sleep(10);
        }
        check("unmounted (Java)", javaFrames(vthread.getStackTrace()));
        checkJvmti("unmounted (JVMTI)", vthread);
        checkedUnmounted = true;
        LockSupport.unpark(vthread);
        vthread.join();
        if (failure[0] != null) {
            throw new RuntimeException("virtual thread failed", failure[0]);
        }
    }

    // Checks the virtual thread's own view with all APIs.
    static void checkAll(String when, Thread thread) {
        check(when + " (Thread.getStackTrace)", javaFrames(Thread.currentThread().getStackTrace()));
        check(when + " (Throwable)", javaFrames(new Throwable().getStackTrace()));
        check(when + " (StackWalker)", StackWalker.getInstance().walk(s -> s.map(f -> f.getClassName() + "." + f.getMethodName()).toList()));
        checkJvmti(when + " (JVMTI)", thread);
    }

    // The JVMTI functions must agree with each other, and report every recurse frame.
    // All are called from this frame, so their depths line up; for the current thread,
    // depth 0 is the native method making the call and is not compared.
    static void checkJvmti(String when, Thread thread) {
        String[] trace = stackTrace(thread);
        int count = frameCount(thread);
        int innerDepth = monitorDepth(thread, INNER);
        List<String> frames = List.of(trace);
        check(when, frames);
        if (count != trace.length) {
            throw new RuntimeException(when + ": GetFrameCount " + count + " != GetStackTrace depth " + trace.length);
        }
        for (int depth = thread == null ? 1 : 0; depth < trace.length; depth++) {
            String frame = frameAt(thread, depth);
            if (!trace[depth].equals(frame)) {
                throw new RuntimeException(when + ": GetFrameLocation at depth " + depth + " is " + frame
                                           + ", GetStackTrace has " + trace[depth]);
            }
        }
        int expectedInnerDepth = frames.indexOf(RECURSE) + INNER_DEPTH;
        if (innerDepth != expectedInnerDepth) {
            throw new RuntimeException(when + ": GetOwnedMonitorStackDepthInfo reports INNER at depth " + innerDepth
                                       + ", the frame holding it is at depth " + expectedInnerDepth);
        }
    }

    // There must be one run of exactly DEPTH + 1 recurse frames.
    static void check(String when, List<String> frames) {
        int first = frames.indexOf(RECURSE);
        int last = frames.lastIndexOf(RECURSE);
        int count = (int) frames.stream().filter(RECURSE::equals).count();
        if (first < 0 || count != DEPTH + 1 || last - first != DEPTH) {
            throw new RuntimeException(when + ": " + count + " recurse frames out of " + frames.size()
                                       + ", expected one run of " + (DEPTH + 1));
        }
        System.out.println(when + ": " + frames.size() + " frames");
    }

    static List<String> javaFrames(StackTraceElement[] elements) {
        List<String> frames = new ArrayList<>();
        for (StackTraceElement e : elements) {
            frames.add(e.getClassName() + "." + e.getMethodName());
        }
        return frames;
    }
}
