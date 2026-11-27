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
 * @bug 8398467
 * @summary GetStackTrace with a negative start_depth on a virtual thread whose
 *          frames are in a stack chunk
 * @requires vm.continuations
 * @library /test/lib
 * @run main/othervm/native -agentlib:GetStackTraceNegativeDepthTest GetStackTraceNegativeDepthTest
 */

import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;

/*
 * GetStackTrace with a negative start_depth walks the stack twice. Test that this
 * works for a virtual thread whose frames are in a stack chunk: while it is unmounted,
 * and after it mounts again until it returns to the frames below its top ones. The
 * result must be the bottom frames of the full stack trace.
 */
public class GetStackTraceNegativeDepthTest {
    private static final int DEPTH = 200;
    private static final int JVMTI_ERROR_ILLEGAL_ARGUMENT = 103;

    // A null thread means the current thread. Each frame is "<class>.<method>@<location>".
    // Returns null and sets lastError if GetStackTrace fails.
    static native String[] stackTrace(Thread thread, int startDepth, int maxCount);
    static native int lastError();

    static volatile boolean parked;
    static volatile boolean done;

    static void recurse(int depth, Runnable atBottom) {
        if (depth > 0) {
            recurse(depth - 1, atBottom);
        } else {
            atBottom.run();
        }
    }

    public static void main(String[] args) throws Exception {
        // An unmounted virtual thread: its frames are in a stack chunk.
        Thread vthread = Thread.ofVirtual().start(() -> recurse(DEPTH, () -> {
            parked = true;
            while (!done) {
                LockSupport.park();
            }
        }));
        while (!parked || vthread.getState() != Thread.State.WAITING) {
            Thread.sleep(10);
        }
        check("unmounted virtual thread", vthread);
        done = true;
        LockSupport.unpark(vthread);
        vthread.join();

        // A mounted virtual thread that resumed after Thread.yield(): it is thawed lazily.
        Throwable[] failure = new Throwable[1];
        Thread.ofVirtual().start(() -> recurse(DEPTH, () -> {
            Thread.yield();
            try {
                check("mounted virtual thread", null);
            } catch (Throwable t) {
                failure[0] = t;
            }
        })).join();
        if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
    }

    // Every negative start_depth must give the bottom frames of the full trace.
    static void check(String what, Thread thread) {
        String[] full = stackTrace(thread, 0, 1024);
        if (full == null || full.length < DEPTH) {
            throw new RuntimeException(what + ": expected at least " + DEPTH + " frames, got "
                                       + (full == null ? "error " + lastError() : full.length));
        }
        int n = full.length;
        for (int k : new int[] { 1, 3, n / 2, n - 1, n }) {
            for (int maxCount : new int[] { 1024, 2 }) {
                String[] tail = stackTrace(thread, -k, maxCount);
                if (tail == null) {
                    throw new RuntimeException(what + ": start_depth " + -k + " failed with " + lastError());
                }
                String[] expected = Arrays.copyOfRange(full, n - k, n - k + Math.min(k, maxCount));
                if (!Arrays.equals(withoutTopLocations(expected, n - k, thread),
                                   withoutTopLocations(tail, n - k, thread))) {
                    throw new RuntimeException(what + ": start_depth " + -k + ", max_count " + maxCount + ": "
                                               + tail.length + " frames, first " + (tail.length > 0 ? tail[0] : "<none>")
                                               + ", expected " + expected.length + ", first " + expected[0]);
                }
            }
        }
        if (stackTrace(thread, -(n + 1), 1024) != null || lastError() != JVMTI_ERROR_ILLEGAL_ARGUMENT) {
            throw new RuntimeException(what + ": start_depth " + -(n + 1) + " did not fail with JVMTI_ERROR_ILLEGAL_ARGUMENT");
        }
        System.out.println(what + ": " + n + " frames");
    }

    // For the current thread, drops the locations at depths 0 and 1: the native method
    // making the call, and check(), which calls it from more than one place.
    static String[] withoutTopLocations(String[] frames, int firstDepth, Thread thread) {
        if (thread != null) {
            return frames;
        }
        String[] copy = frames.clone();
        for (int i = 0; i < copy.length && firstDepth + i < 2; i++) {
            copy[i] = copy[i].substring(0, copy[i].lastIndexOf('@'));
        }
        return copy;
    }
}
