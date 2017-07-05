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
            hs.get(TestAccessMode.SET).invokeExact(recv, 1L);
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, 2L);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, 1L);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, 2L);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(x, 2L, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 1L);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, 2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(recv, 2L, 1L);
            assertEquals(r, true, "weakCompareAndSetVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 1L, "weakCompareAndSetVolatile long value");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, 2L);
            assertEquals(o, 1L, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 2L, "getAndSet long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 1L);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(recv, 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(recv, 3L);
            assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {

    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(1L);
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(2L);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(1L);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(2L);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(x, 2L, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(1L);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(2L, 1L);
            assertEquals(r, true, "weakCompareAndSetVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 1L, "weakCompareAndSetVolatile long value");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(2L);
            assertEquals(o, 1L, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 2L, "getAndSet long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(1L);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact( 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(3L);
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
                hs.get(TestAccessMode.SET).invokeExact(array, i, 1L);
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "get long value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, 2L);
                long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(x, 2L, "setVolatile long value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, 1L);
                long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(x, 1L, "setRelease long value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, 2L);
                long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(x, 2L, "setOpaque long value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 1L);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "success compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "success compareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 1L, 3L);
                assertEquals(r, false, "failing compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "failing compareAndSet long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeVolatile long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_VOLATILE).invokeExact(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 1L, 2L);
                assertEquals(r, 1L, "success compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 1L, 3L);
                assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "success compareAndExchangeRelease long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "weakCompareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 2L, 1L);
                assertEquals(r, true, "weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "weakCompareAndSetAcquire long");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSetRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "weakCompareAndSetRelease long");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(array, i, 2L, 1L);
                assertEquals(r, true, "weakCompareAndSetVolatile long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 1L, "weakCompareAndSetVolatile long value");
            }

            // Compare set and get
            {
                long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, 2L);
                assertEquals(o, 1L, "getAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 2L, "getAndSet long value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 1L);

            // get and add, add and get
            {
                long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(array, i, 3L);
                assertEquals(o, 1L, "getAndAdd long");
                long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(array, i, 3L);
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

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkIOOBE(am, () -> {
                    long x = (long) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 1L, 2L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkIOOBE(am, () -> {
                    long r = (long) hs.get(am).invokeExact(array, ci, 2L, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 1L);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 3L);
                });
            }
        }
    }
}

