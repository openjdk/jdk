/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessLong
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.*;

public class VarHandleTestMethodHandleAccessLong extends VarHandleBaseTest {
    static final long static_final_v = 1L;

    static long static_v;

    final long final_v = 1L;

    long v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessLong.class, "final_v", long.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessLong.class, "v", long.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessLong.class, "static_final_v", long.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessLong.class, "static_v", long.class);

        vhArray = MethodHandles.arrayElementVarHandle(long[].class);
    }


    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceField(this, hs)));
            cases.add(new MethodHandleAccessTestCase("Instance field unsupported",
                                                     vhField, f, hs -> testInstanceFieldUnsupported(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessLong::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessLong::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArrayIndexOutOfBounds,
                                                     false));
        }

        // Work around issue with jtreg summary reporting which truncates
        // the String result of Object.toString to 30 characters, hence
        // the first dummy argument
        return cases.stream().map(tc -> new Object[]{tc.toString(), tc}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "accessTestCaseProvider")
    public <T> void testAccess(String desc, AccessTestCase<T> atc) throws Throwable {
        T t = atc.get();
        int iters = atc.requiresLoop() ? ITERS : 1;
        for (int c = 0; c < iters; c++) {
            atc.testAccess(t);
        }
    }


    static void testInstanceField(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(recv, 1L);
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(recv, 2L);
            long x = (long) hs.get(TestAccessMode.getVolatile).invokeExact(recv);
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(recv, 1L);
            long x = (long) hs.get(TestAccessMode.getAcquire).invokeExact(recv);
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(recv, 2L);
            long x = (long) hs.get(TestAccessMode.getOpaque).invokeExact(recv);
            assertEquals(x, 2L, "setOpaque long value");
        }

        hs.get(TestAccessMode.set).invokeExact(recv, 1L);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, 1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, 1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, 1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(recv, 2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.getAndSet).invokeExact(recv, 1L);
            assertEquals(o, 2L, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1L, "getAndSet long value");
        }

        hs.get(TestAccessMode.set).invokeExact(recv, 1L);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.getAndAdd).invokeExact(recv, 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.addAndGet).invokeExact(recv, 3L);
            assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {

    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(1L);
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(2L);
            long x = (long) hs.get(TestAccessMode.getVolatile).invokeExact();
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(1L);
            long x = (long) hs.get(TestAccessMode.getAcquire).invokeExact();
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(2L);
            long x = (long) hs.get(TestAccessMode.getOpaque).invokeExact();
            assertEquals(x, 2L, "setOpaque long value");
        }

        hs.get(TestAccessMode.set).invokeExact(1L);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact( 1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.getAndSet).invokeExact( 1L);
            assertEquals(o, 2L, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1L, "getAndSet long value");
        }

        hs.get(TestAccessMode.set).invokeExact(1L);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.getAndAdd).invokeExact( 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.addAndGet).invokeExact(3L);
            assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
        }
    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

    }


    static void testArray(Handles hs) throws Throwable {
        long[] array = new long[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.set).invokeExact(array, i, 1L);
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "get long value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.setVolatile).invokeExact(array, i, 2L);
                long x = (long) hs.get(TestAccessMode.getVolatile).invokeExact(array, i);
                assertEquals(x, 2L, "setVolatile long value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.setRelease).invokeExact(array, i, 1L);
                long x = (long) hs.get(TestAccessMode.getAcquire).invokeExact(array, i);
                assertEquals(x, 1L, "setRelease long value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.setOpaque).invokeExact(array, i, 2L);
                long x = (long) hs.get(TestAccessMode.getOpaque).invokeExact(array, i);
                assertEquals(x, 2L, "setOpaque long value");
            }

            hs.get(TestAccessMode.set).invokeExact(array, i, 1L);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "success compareAndSet long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "success compareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, 1L, 3L);
                assertEquals(r, false, "failing compareAndSet long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "failing compareAndSet long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeVolatile long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, 1L, 2L);
                assertEquals(r, 1L, "success compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, 1L, 3L);
                assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "success compareAndExchangeRelease long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSet long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "weakCompareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(array, i, 2L, 1L);
                assertEquals(r, true, "weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "weakCompareAndSetAcquire long");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSetRelease long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2L, "weakCompareAndSetRelease long");
            }

            // Compare set and get
            {
                long o = (long) hs.get(TestAccessMode.getAndSet).invokeExact(array, i, 1L);
                assertEquals(o, 2L, "getAndSet long");
                long x = (long) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1L, "getAndSet long value");
            }

            hs.get(TestAccessMode.set).invokeExact(array, i, 1L);

            // get and add, add and get
            {
                long o = (long) hs.get(TestAccessMode.getAndAdd).invokeExact(array, i, 3L);
                assertEquals(o, 1L, "getAndAdd long");
                long c = (long) hs.get(TestAccessMode.addAndGet).invokeExact(array, i, 3L);
                assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
            }
        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        long[] array = new long[10];

        final int i = 0;

    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        long[] array = new long[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
                checkIOOBE(am, () -> {
                    long x = (long) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 1L, 2L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
                checkIOOBE(am, () -> {
                    long r = (long) hs.get(am).invokeExact(array, ci, 2L, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 3L);
                });
            }
        }
    }
}

