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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessBoolean
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

public class VarHandleTestMethodHandleAccessBoolean extends VarHandleBaseTest {
    static final boolean static_final_v = true;

    static boolean static_v;

    final boolean final_v = true;

    boolean v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessBoolean.class, "final_v", boolean.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessBoolean.class, "v", boolean.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessBoolean.class, "static_final_v", boolean.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessBoolean.class, "static_v", boolean.class);

        vhArray = MethodHandles.arrayElementVarHandle(boolean[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessBoolean::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessBoolean::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessBoolean::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessBoolean::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessBoolean::testArrayIndexOutOfBounds,
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


    static void testInstanceField(VarHandleTestMethodHandleAccessBoolean recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, true);
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "set boolean value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, false);
            boolean x = (boolean) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(x, false, "setVolatile boolean value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, true);
            boolean x = (boolean) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(x, true, "setRelease boolean value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, false);
            boolean x = (boolean) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(x, false, "setOpaque boolean value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, true);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, true, false);
            assertEquals(r, true, "success compareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "success compareAndSet boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, true, false);
            assertEquals(r, false, "failing compareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "failing compareAndSet boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, false, true);
            assertEquals(r, false, "success compareAndExchange boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "success compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, false, false);
            assertEquals(r, true, "failing compareAndExchange boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "failing compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, true, false);
            assertEquals(r, true, "success compareAndExchangeAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "success compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, true, false);
            assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "failing compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, false, true);
            assertEquals(r, false, "success compareAndExchangeRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "success compareAndExchangeRelease boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, false, false);
            assertEquals(r, true, "failing compareAndExchangeRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "failing compareAndExchangeRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, true, false);
            }
            assertEquals(success, true, "weakCompareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "weakCompareAndSet boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, false, true);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "weakCompareAndSetAcquire boolean");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, true, false);
            }
            assertEquals(success, true, "weakCompareAndSetRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "weakCompareAndSetRelease boolean");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(recv, false, true);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, true, "weakCompareAndSetVolatile boolean");
        }

        // Compare set and get
        {
            boolean o = (boolean) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, false);
            assertEquals(o, true, "getAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, false, "getAndSet boolean value");
        }

    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessBoolean recv, Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(recv, true);
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(true);
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "set boolean value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(false);
            boolean x = (boolean) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(x, false, "setVolatile boolean value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(true);
            boolean x = (boolean) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(x, true, "setRelease boolean value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(false);
            boolean x = (boolean) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(x, false, "setOpaque boolean value");
        }

        hs.get(TestAccessMode.SET).invokeExact(true);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(true, false);
            assertEquals(r, true, "success compareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "success compareAndSet boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(true, false);
            assertEquals(r, false, "failing compareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "failing compareAndSet boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(false, true);
            assertEquals(r, false, "success compareAndExchange boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "success compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(false, false);
            assertEquals(r, true, "failing compareAndExchange boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "failing compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(true, false);
            assertEquals(r, true, "success compareAndExchangeAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "success compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(true, false);
            assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "failing compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(false, true);
            assertEquals(r, false, "success compareAndExchangeRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "success compareAndExchangeRelease boolean value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(false, false);
            assertEquals(r, true, "failing compareAndExchangeRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "failing compareAndExchangeRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(true, false);
            }
            assertEquals(success, true, "weakCompareAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "weakCompareAndSet boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(false, true);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "weakCompareAndSetAcquire boolean");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(true, false);
            }
            assertEquals(success, true, "weakCompareAndSetRelease boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "weakCompareAndSetRelease boolean");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(false, true);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, true, "weakCompareAndSetVolatile boolean");
        }

        // Compare set and get
        {
            boolean o = (boolean) hs.get(TestAccessMode.GET_AND_SET).invokeExact( false);
            assertEquals(o, true, "getAndSet boolean");
            boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, false, "getAndSet boolean value");
        }

    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(true);
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        boolean[] array = new boolean[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, true);
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "get boolean value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, false);
                boolean x = (boolean) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(x, false, "setVolatile boolean value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, true);
                boolean x = (boolean) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(x, true, "setRelease boolean value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, false);
                boolean x = (boolean) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(x, false, "setOpaque boolean value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, true);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, true, false);
                assertEquals(r, true, "success compareAndSet boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "success compareAndSet boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, true, false);
                assertEquals(r, false, "failing compareAndSet boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "failing compareAndSet boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, false, true);
                assertEquals(r, false, "success compareAndExchange boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "success compareAndExchange boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, false, false);
                assertEquals(r, true, "failing compareAndExchange boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "failing compareAndExchange boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, true, false);
                assertEquals(r, true, "success compareAndExchangeAcquire boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "success compareAndExchangeAcquire boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, true, false);
                assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "failing compareAndExchangeAcquire boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, false, true);
                assertEquals(r, false, "success compareAndExchangeRelease boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "success compareAndExchangeRelease boolean value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, false, false);
                assertEquals(r, true, "failing compareAndExchangeRelease boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "failing compareAndExchangeRelease boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, true, false);
                }
                assertEquals(success, true, "weakCompareAndSet boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "weakCompareAndSet boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, false, true);
                }
                assertEquals(success, true, "weakCompareAndSetAcquire boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "weakCompareAndSetAcquire boolean");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(array, i, true, false);
                }
                assertEquals(success, true, "weakCompareAndSetRelease boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "weakCompareAndSetRelease boolean");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(array, i, false, true);
                }
                assertEquals(success, true, "weakCompareAndSetVolatile boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, true, "weakCompareAndSetVolatile boolean");
            }

            // Compare set and get
            {
                boolean o = (boolean) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, false);
                assertEquals(o, true, "getAndSet boolean");
                boolean x = (boolean) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, false, "getAndSet boolean value");
            }

        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        boolean[] array = new boolean[10];

        final int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                boolean o = (boolean) hs.get(am).invokeExact(array, i, true);
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        boolean[] array = new boolean[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkIOOBE(am, () -> {
                    boolean x = (boolean) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, true);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, true, false);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, false, true);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkIOOBE(am, () -> {
                    boolean o = (boolean) hs.get(am).invokeExact(array, ci, true);
                });
            }

        }
    }
}

