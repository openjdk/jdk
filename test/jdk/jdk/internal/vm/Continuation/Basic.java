/*
* Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
* @summary Basic tests for jdk.internal.vm.Continuation
* @requires vm.continuations
* @modules java.base/jdk.internal.vm
* @build java.base/java.lang.StackWalkerHelper
*
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xint Basic
*
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xcomp -XX:TieredStopAtLevel=3 -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* -XX:CompileCommand=exclude,Basic.manyArgsDriver Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* -XX:CompileCommand=exclude,jdk/internal/vm/Continuation.enter Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* -XX:CompileCommand=inline,jdk/internal/vm/Continuation.run Basic
*/

/**
* @test
* @requires vm.continuations
* @requires vm.debug
* @modules java.base/jdk.internal.vm
* @build java.base/java.lang.StackWalkerHelper
*
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+VerifyStack -Xint Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+VerifyStack -Xcomp -XX:TieredStopAtLevel=3 -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* Basic
* @run testng/othervm -XX:+UnlockDiagnosticVMOptions -XX:+ShowHiddenFrames -XX:+VerifyStack -Xcomp -XX:-TieredCompilation -XX:CompileOnly=jdk.internal.vm.Continuation::*,Basic::* Basic
*/

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;
import static org.testng.Assert.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class Basic {
    static final ContinuationScope FOO = new ContinuationScope() {};

    @Test
    public void test1() {
        // Basic freeze and thaw
        final AtomicInteger res = new AtomicInteger(0);
        Continuation cont = new Continuation(FOO, ()-> {
            double r = 0;
            for (int k = 1; k < 20; k++) {
                int x = 3;
                String s = "abc";
                r += foo(k);
            }
            res.set((int)r);
        });

        int i = 0;
        while (!cont.isDone()) {
            cont.run();
            System.gc();

            assertEquals(cont.isPreempted(), false);

            List<String> frames = cont.stackWalker().walk(fs -> fs.map(StackWalker.StackFrame::getMethodName).collect(Collectors.toList()));
            assertEquals(frames, cont.isDone() ? List.of() : Arrays.asList("yield0", "yield", "bar", "foo", "lambda$test1$0", "run", "enter0", "enter"));
        }
        assertEquals(res.get(), 247);
        assertEquals(cont.isPreempted(), false);
    }

    static double foo(int a) {
        long x = 8;
        String s = "yyy";
        String r = bar(a + 1);
        return Integer.parseInt(r)+1;
    }

    static final int DEPTH = 40;
    static String bar(long b) {
        double x = 9.99;
        String s = "zzz";
        boolean res = Continuation.yield(FOO);

        assertEquals(res, true);

        deep(DEPTH);

        long r = b+1;
        return "" + r;
    }

    static String bar2(long b) {
        double x = 9.99;
        String s = "zzz";
        Continuation.yield(FOO);

        long r = b+1;
        return "" + r;
    }

    static void deep(int depth) {
        if (depth > 1) {
            deep(depth-1);
            return;
        }

        StackWalker walker = StackWalker.getInstance();
        List<String> frames = walker.walk(fs -> fs.map(StackWalker.StackFrame::getMethodName).collect(Collectors.toList()));

        List<String> expected0 = new ArrayList<>();
        IntStream.range(0, DEPTH).forEach(i -> { expected0.add("deep"); });
        List<String> baseFrames = List.of("bar", "foo", "lambda$test1$0", "run", "enter0", "enter", "run", "test1");
        expected0.addAll(baseFrames);

        assertEquals(frames.subList(0, DEPTH + baseFrames.size()), expected0);

        walker = StackWalkerHelper.getInstance(FOO);
        frames = walker.walk(fs -> fs.map(StackWalker.StackFrame::getMethodName).collect(Collectors.toList()));


        List<String> expected1 = new ArrayList<>();
        IntStream.range(0, DEPTH).forEach(i -> { expected1.add("deep"); });
        expected1.addAll(List.of("bar", "foo", "lambda$test1$0", "run", "enter0", "enter"));
        assertEquals(frames, expected1);
    }

    static class LoomException extends RuntimeException {
        public LoomException(String message) {
            super(message);
        }
    }

    static double fooThrow(int a) {
        long x = 8;
        String s = "yyy";
        String r = barThrow(a + 1);
        return Integer.parseInt(r)+1;
    }

    static String barThrow(long b) {
        double x = 9.99;
        String s = "zzz";
        Continuation.yield(FOO);

        long r = b+1;

        if (true)
            throw new LoomException("Loom exception!");
        return "" + r;
    }

    @Test
    public void testException1() {
        // Freeze and thaw with exceptions
        Continuation cont = new Continuation(FOO, ()-> {
            double r = 0;
            for (int k = 1; k < 20; k++) {
                int x = 3;
                String s = "abc";
                r += fooThrow(k);
            }
        });

        cont.run();
        try {
            cont.run();
            fail("Exception not thrown.");
        } catch (LoomException e) {
            assertEquals(e.getMessage(), "Loom exception!");
            // e.printStackTrace();
            StackTraceElement[] stes = e.getStackTrace();
            assertEquals(stes[0].getMethodName(), "barThrow");
            int index = -1;
            for (int i=0; i<stes.length; i++) {
                if (stes[i].getClassName().equals(Continuation.class.getName()) && stes[i].getMethodName().equals("enter")) {
                    index = i;
                    break;
                }
            }
            assertTrue(index >= 0);
            StackTraceElement last = stes[index];
        }
    }

    @Test
    public void testManyArgs() {
        // Methods with stack-passed arguments
        final AtomicInteger res = new AtomicInteger(0);
        Continuation cont = new Continuation(FOO, ()-> {
            res.set((int)manyArgsDriver());
        });

        int i = 0;
        while (!cont.isDone()) {
            cont.run();
            System.gc();
        }
        assertEquals(res.get(), 247);
    }

    static double manyArgsDriver() {
        double r = 0;
        for (int k = 1; k < 20; k++) {
            int x = 3;
            String s = "abc";

            r += fooMany(k,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
            1.0, 2.0, 3.0, 4.0, 5.0, 6.0f, 7.0f, 8.0, 9.0, 10.0, 11.0, 12.0, 13.0, 14.0f, 15.0f, 16.0f, 17.0, 18.0, 19.0, 20.0,
            1001, 1002, 1003, 1004, 1005, 1006, 1007, 1008, 1009, 1010, 1011, 1012, 1013, 1014, 1015, 1016, 1017, 1018, 1019, 1020);
        }
        return r;
    }

    static double fooMany(int a,
    int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10, int x11, int x12,
    int x13, int x14, int x15, int x16, int x17, int x18, int x19, int x20,
    double f1, double f2, double f3, double f4, double f5, float f6, float f7, double f8, double f9, double f10,
    double f11, double f12, double f13, float f14, float f15, float f16, double f17, double f18, double f19, double f20,
    Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8, Object o9, Object o10,
    Object o11, Object o12, Object o13, Object o14, Object o15, Object o16, Object o17, Object o18, Object o19, Object o20) {
        long x = 8;
        String s = "yyy";
        String r = barMany(a + 1,
        x1, x2, x3, x4, x5, x6, x7, x8, x9, x10, x11, x12, x13, x14, x15, x16, x17, x18, x19, x20,
        f1, f2, f3, f4, f5, f6, f7, f8, f9, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f20,
        o1, o2, o3, o4, o5, o6, o7, o8, o9, o10, o11, o12, o13, o14, o15, o16, o17, o18, o19, o20);
        return Integer.parseInt(r)+1;
    }

    static String barMany(long b,
    int x1, int x2, int x3, int x4, int x5, int x6, int x7, int x8, int x9, int x10, int x11, int x12,
    int x13, int x14, int x15, int x16, int x17, int x18, int x19, int x20,
    double f1, double f2, double f3, double f4, double f5, float f6, float f7, double f8, double f9, double f10,
    double f11, double f12, double f13, float f14, float f15, float f16, double f17, double f18, double f19, double f20,
    Object o1, Object o2, Object o3, Object o4, Object o5, Object o6, Object o7, Object o8, Object o9, Object o10,
    Object o11, Object o12, Object o13, Object o14, Object o15, Object o16, Object o17, Object o18, Object o19, Object o20) {
        double x = 9.99;
        String s = "zzz";
        for (int i=0; i<2; i++) {
            Continuation.yield(FOO);
        }
        long r = b+1;
        return "" + r;
    }

    @Test
    public void testPinnedMonitor() {
        // Test pinning due to held monitor
        final AtomicReference<Continuation.Pinned> res = new AtomicReference<>();

        Continuation cont = new Continuation(FOO, ()-> {
            syncFoo(1);
        }) {
            @Override
            protected void onPinned(Continuation.Pinned reason) {
                assert Continuation.isPinned(FOO);
                res.set(reason);
            }
        };

        cont.run();
        assertEquals(res.get(), Continuation.Pinned.MONITOR);
        boolean isDone = cont.isDone();
        assertEquals(isDone, true);
    }

    static double syncFoo(int a) {
        long x = 8;
        String s = "yyy";
        String r;
        synchronized(FOO) {
            r = bar2(a + 1);
        }
        return Integer.parseInt(r)+1;
    }

    @Test
    public void testNotPinnedMonitor() {
        final AtomicReference<Continuation.Pinned> res = new AtomicReference<>();

        Continuation cont = new Continuation(FOO, ()-> {
            noSyncFoo(1);
        }) {
            @Override
            protected void onPinned(Continuation.Pinned reason) {
                assert Continuation.isPinned(FOO);
                res.set(reason);
            }
        };

        cont.run();
        boolean isDone = cont.isDone();
        assertEquals(res.get(), null);
        assertEquals(isDone, false);
    }

    static double noSyncFoo(int a) {
        long x = 7;
        synchronized(FOO) {
            x += FOO.getClass().getName().contains("FOO") ? 1 : 0;
        }
        String s = "yyy";
        String r = bar2(a + 1);
        return Integer.parseInt(r)+1;
    }

    @Test
    public void testPinnedCriticalSection() {
        // pinning due to critical section
        final AtomicReference<Continuation.Pinned> res = new AtomicReference<>();

        Continuation cont = new Continuation(FOO, ()-> {
            csFoo(1);
        }) {
            @Override
            protected void onPinned(Continuation.Pinned reason) {
                res.set(reason);
            }
        };

        cont.run();
        assertEquals(res.get(), Continuation.Pinned.CRITICAL_SECTION);
    }

    static double csFoo(int a) {
        long x = 8;
        String s = "yyy";
        String r;
        Continuation.pin();
        try {
            assert Continuation.isPinned(FOO);
            r = bar2(a + 1);
        } finally {
            Continuation.unpin();
        }
        return Integer.parseInt(r)+1;
    }

    @Test
    public void testPinnedNative() {
        // pinning due to native method
        final AtomicReference<Continuation.Pinned> res = new AtomicReference<>();

        Continuation cont = new Continuation(FOO, ()-> {
            nativeFoo(1);
        }) {
            @Override
            protected void onPinned(Continuation.Pinned reason) {
                res.set(reason);
            }
        };

        cont.run();
        assertEquals(res.get(), Continuation.Pinned.NATIVE);
    }

    static double nativeFoo(int a) {
        try {
            int x = 8;
            String s = "yyy";
            return nativeBar(x);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    static int nativeBaz(int b) {
        double x = 9.99;
        String s = "zzz";
        assert Continuation.isPinned(FOO);
        boolean res = Continuation.yield(FOO);
        assert res == false;

        return b+1;
    }

    private static native int nativeBar(int x);

    static {
        System.loadLibrary("BasicJNI");
    }
}
