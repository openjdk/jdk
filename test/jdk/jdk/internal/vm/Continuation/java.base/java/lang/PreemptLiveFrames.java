/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.Arrays;
import java.util.Objects;

public class PreemptLiveFrames {
    public static void main(String[] args) {
        try {
            PreemptLiveFrames obj = new PreemptLiveFrames();
            obj.test1();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static final ContinuationScope FOO = new ContinuationScope() {};
    CountDownLatch startLatch = new CountDownLatch(1);
    volatile boolean run;
    volatile int x;

    public void test1() throws Exception {
        System.out.println("test1");

        final AtomicInteger result = new AtomicInteger(0);
        final Continuation cont = new Continuation(FOO, ()-> {
                double r = 0;
                int k = 1;
                int x = 3;
                String s = "abc";
                r += foo(k);
                result.set((int)r);
            });

        final Thread t0 = Thread.currentThread();
        Thread t = new Thread(() -> {
            try {
                startLatch.await();

                Continuation.PreemptStatus res;
                int i = 0;
                do {
                    res = cont.tryPreempt(t0);
                    i++;
                } while (i < 100 && res == Continuation.PreemptStatus.TRANSIENT_FAIL_PINNED_NATIVE);
                assertEquals(res, Continuation.PreemptStatus.SUCCESS);
                // assertEquals(res, Continuation.PreemptStatus.SUCCESS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        t.start();

        run = true;

        cont.run();
        assertEquals(cont.isDone(), false);
        assertEquals(cont.isPreempted(), true);

        testStackWalk(LiveStackFrame.getStackWalker(cont));

        t.join();
    }

    double foo(int a) {
        long x = 8;
        String s = "yyy";
        String r = bar(a + 1);
        return Integer.parseInt(r)+1;
    }

    String bar(long b) {
        double x = 9.99;
        String s = "zzz";
        String r = baz(b + 1);
        return "" + r;
    }

    String baz(long b) {
        double x = 9.99;
        String s = "zzz";

        loop();

        long r = b+1;
        return "" + r;
    }

    void loop() {
        while (run) {
            x++;
            if (startLatch.getCount() > 0) {
                startLatch.countDown();
            }
        }
    }

    static void testStackWalk(StackWalker walker) {
        // StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        System.out.println("^&^ start");
        walker.forEach(f -> {
            try {
                System.out.println("^&^ --==--");
                // System.out.println("^&^ " + f);
                LiveStackFrame lf = (LiveStackFrame)f;
                System.out.println("^&^ locals: " + Arrays.toString(lf.getLocals()));
                System.out.println("^&^ stack: " + Arrays.toString(lf.getStack()));
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            }
        });
        System.out.println("^&^ end");
    }

    static void assertEquals(Object actual, Object expected) {
        if (!Objects.equals(actual, expected)) {
            throw new AssertionError("Expected: " + expected + ", actual: " + actual);
        }
    }
}
