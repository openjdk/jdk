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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessInt
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

public class VarHandleTestMethodHandleAccessInt extends VarHandleBaseTest {
    static final int static_final_v = 1;

    static int static_v;

    final int final_v = 1;

    int v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessInt.class, "final_v", int.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessInt.class, "v", int.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessInt.class, "static_final_v", int.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessInt.class, "static_v", int.class);

        vhArray = MethodHandles.arrayElementVarHandle(int[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessInt::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessInt::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArrayIndexOutOfBounds,
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


    static void testInstanceField(VarHandleTestMethodHandleAccessInt recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(recv, 1);
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "set int value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(recv, 2);
            int x = (int) hs.get(TestAccessMode.getVolatile).invokeExact(recv);
            assertEquals(x, 2, "setVolatile int value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(recv, 1);
            int x = (int) hs.get(TestAccessMode.getAcquire).invokeExact(recv);
            assertEquals(x, 1, "setRelease int value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(recv, 2);
            int x = (int) hs.get(TestAccessMode.getOpaque).invokeExact(recv);
            assertEquals(x, 2, "setOpaque int value");
        }

        hs.get(TestAccessMode.set).invokeExact(recv, 1);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, 1, 2);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "success compareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, 1, 3);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "failing compareAndSet int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, 2, 1);
            assertEquals(r, 2, "success compareAndExchangeVolatile int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "success compareAndExchangeVolatile int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, 2, 3);
            assertEquals(r, 1, "failing compareAndExchangeVolatile int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, 1, 2);
            assertEquals(r, 1, "success compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, 1, 3);
            assertEquals(r, 2, "failing compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, 2, 1);
            assertEquals(r, 2, "success compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, 2, 3);
            assertEquals(r, 1, "failing compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "failing compareAndExchangeRelease int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(recv, 1, 2);
            assertEquals(r, true, "weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "weakCompareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(recv, 2, 1);
            assertEquals(r, true, "weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "weakCompareAndSetAcquire int");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(recv, 1, 2);
            assertEquals(r, true, "weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 2, "weakCompareAndSetRelease int");
        }

        // Compare set and get
        {
            int o = (int) hs.get(TestAccessMode.getAndSet).invokeExact(recv, 1);
            assertEquals(o, 2, "getAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1, "getAndSet int value");
        }

        hs.get(TestAccessMode.set).invokeExact(recv, 1);

        // get and add, add and get
        {
            int o = (int) hs.get(TestAccessMode.getAndAdd).invokeExact(recv, 3);
            assertEquals(o, 1, "getAndAdd int");
            int c = (int) hs.get(TestAccessMode.addAndGet).invokeExact(recv, 3);
            assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessInt recv, Handles hs) throws Throwable {

    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(1);
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "set int value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(2);
            int x = (int) hs.get(TestAccessMode.getVolatile).invokeExact();
            assertEquals(x, 2, "setVolatile int value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(1);
            int x = (int) hs.get(TestAccessMode.getAcquire).invokeExact();
            assertEquals(x, 1, "setRelease int value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(2);
            int x = (int) hs.get(TestAccessMode.getOpaque).invokeExact();
            assertEquals(x, 2, "setOpaque int value");
        }

        hs.get(TestAccessMode.set).invokeExact(1);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(1, 2);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "success compareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(1, 3);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "failing compareAndSet int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(2, 1);
            assertEquals(r, 2, "success compareAndExchangeVolatile int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "success compareAndExchangeVolatile int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(2, 3);
            assertEquals(r, 1, "failing compareAndExchangeVolatile int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(1, 2);
            assertEquals(r, 1, "success compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(1, 3);
            assertEquals(r, 2, "failing compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(2, 1);
            assertEquals(r, 2, "success compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(2, 3);
            assertEquals(r, 1, "failing compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "failing compareAndExchangeRelease int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(1, 2);
            assertEquals(r, true, "weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "weakCompareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(2, 1);
            assertEquals(r, true, "weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "weakCompareAndSetAcquire int");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact( 1, 2);
            assertEquals(r, true, "weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 2, "weakCompareAndSetRelease int");
        }

        // Compare set and get
        {
            int o = (int) hs.get(TestAccessMode.getAndSet).invokeExact( 1);
            assertEquals(o, 2, "getAndSet int");
            int x = (int) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1, "getAndSet int value");
        }

        hs.get(TestAccessMode.set).invokeExact(1);

        // get and add, add and get
        {
            int o = (int) hs.get(TestAccessMode.getAndAdd).invokeExact( 3);
            assertEquals(o, 1, "getAndAdd int");
            int c = (int) hs.get(TestAccessMode.addAndGet).invokeExact(3);
            assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
        }
    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

    }


    static void testArray(Handles hs) throws Throwable {
        int[] array = new int[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.set).invokeExact(array, i, 1);
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "get int value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.setVolatile).invokeExact(array, i, 2);
                int x = (int) hs.get(TestAccessMode.getVolatile).invokeExact(array, i);
                assertEquals(x, 2, "setVolatile int value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.setRelease).invokeExact(array, i, 1);
                int x = (int) hs.get(TestAccessMode.getAcquire).invokeExact(array, i);
                assertEquals(x, 1, "setRelease int value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.setOpaque).invokeExact(array, i, 2);
                int x = (int) hs.get(TestAccessMode.getOpaque).invokeExact(array, i);
                assertEquals(x, 2, "setOpaque int value");
            }

            hs.get(TestAccessMode.set).invokeExact(array, i, 1);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, 1, 2);
                assertEquals(r, true, "success compareAndSet int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "success compareAndSet int value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, 1, 3);
                assertEquals(r, false, "failing compareAndSet int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "failing compareAndSet int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, 2, 1);
                assertEquals(r, 2, "success compareAndExchangeVolatile int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "success compareAndExchangeVolatile int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, 2, 3);
                assertEquals(r, 1, "failing compareAndExchangeVolatile int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, 1, 2);
                assertEquals(r, 1, "success compareAndExchangeAcquire int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "success compareAndExchangeAcquire int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, 1, 3);
                assertEquals(r, 2, "failing compareAndExchangeAcquire int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, 2, 1);
                assertEquals(r, 2, "success compareAndExchangeRelease int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "success compareAndExchangeRelease int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, 2, 3);
                assertEquals(r, 1, "failing compareAndExchangeRelease int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "failing compareAndExchangeRelease int value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(array, i, 1, 2);
                assertEquals(r, true, "weakCompareAndSet int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "weakCompareAndSet int value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(array, i, 2, 1);
                assertEquals(r, true, "weakCompareAndSetAcquire int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "weakCompareAndSetAcquire int");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(array, i, 1, 2);
                assertEquals(r, true, "weakCompareAndSetRelease int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 2, "weakCompareAndSetRelease int");
            }

            // Compare set and get
            {
                int o = (int) hs.get(TestAccessMode.getAndSet).invokeExact(array, i, 1);
                assertEquals(o, 2, "getAndSet int");
                int x = (int) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1, "getAndSet int value");
            }

            hs.get(TestAccessMode.set).invokeExact(array, i, 1);

            // get and add, add and get
            {
                int o = (int) hs.get(TestAccessMode.getAndAdd).invokeExact(array, i, 3);
                assertEquals(o, 1, "getAndAdd int");
                int c = (int) hs.get(TestAccessMode.addAndGet).invokeExact(array, i, 3);
                assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
            }
        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        int[] array = new int[10];

        final int i = 0;

    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        int[] array = new int[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
                checkIOOBE(am, () -> {
                    int x = (int) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 1);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 1, 2);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
                checkIOOBE(am, () -> {
                    int r = (int) hs.get(am).invokeExact(array, ci, 2, 1);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
                checkIOOBE(am, () -> {
                    int o = (int) hs.get(am).invokeExact(array, ci, 1);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
                checkIOOBE(am, () -> {
                    int o = (int) hs.get(am).invokeExact(array, ci, 3);
                });
            }
        }
    }
}

