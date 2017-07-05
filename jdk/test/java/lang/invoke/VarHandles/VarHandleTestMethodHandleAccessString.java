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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessString
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

public class VarHandleTestMethodHandleAccessString extends VarHandleBaseTest {
    static final String static_final_v = "foo";

    static String static_v;

    final String final_v = "foo";

    String v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessString.class, "final_v", String.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessString.class, "v", String.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessString.class, "static_final_v", String.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessString.class, "static_v", String.class);

        vhArray = MethodHandles.arrayElementVarHandle(String[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessString::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessString::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessString::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessString::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessString::testArrayIndexOutOfBounds,
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


    static void testInstanceField(VarHandleTestMethodHandleAccessString recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(recv, "foo");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "set String value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(recv, "bar");
            String x = (String) hs.get(TestAccessMode.getVolatile).invokeExact(recv);
            assertEquals(x, "bar", "setVolatile String value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(recv, "foo");
            String x = (String) hs.get(TestAccessMode.getAcquire).invokeExact(recv);
            assertEquals(x, "foo", "setRelease String value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(recv, "bar");
            String x = (String) hs.get(TestAccessMode.getOpaque).invokeExact(recv);
            assertEquals(x, "bar", "setOpaque String value");
        }

        hs.get(TestAccessMode.set).invokeExact(recv, "foo");

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, "foo", "bar");
            assertEquals(r, true, "success compareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "success compareAndSet String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(recv, "foo", "baz");
            assertEquals(r, false, "failing compareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "failing compareAndSet String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeVolatile String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(recv, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, "foo", "bar");
            assertEquals(r, "foo", "success compareAndExchangeAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(recv, "foo", "baz");
            assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "success compareAndExchangeRelease String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(recv, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(recv, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "weakCompareAndSet String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(recv, "bar", "foo");
            assertEquals(r, true, "weakCompareAndSetAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "weakCompareAndSetAcquire String");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(recv, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSetRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "bar", "weakCompareAndSetRelease String");
        }

        // Compare set and get
        {
            String o = (String) hs.get(TestAccessMode.getAndSet).invokeExact(recv, "foo");
            assertEquals(o, "bar", "getAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, "foo", "getAndSet String value");
        }

    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessString recv, Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                String r = (String) hs.get(am).invokeExact(recv, "foo");
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact("foo");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "set String value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact("bar");
            String x = (String) hs.get(TestAccessMode.getVolatile).invokeExact();
            assertEquals(x, "bar", "setVolatile String value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact("foo");
            String x = (String) hs.get(TestAccessMode.getAcquire).invokeExact();
            assertEquals(x, "foo", "setRelease String value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact("bar");
            String x = (String) hs.get(TestAccessMode.getOpaque).invokeExact();
            assertEquals(x, "bar", "setOpaque String value");
        }

        hs.get(TestAccessMode.set).invokeExact("foo");

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact("foo", "bar");
            assertEquals(r, true, "success compareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "success compareAndSet String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact("foo", "baz");
            assertEquals(r, false, "failing compareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "failing compareAndSet String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact("bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeVolatile String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact("bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact("foo", "bar");
            assertEquals(r, "foo", "success compareAndExchangeAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact("foo", "baz");
            assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact("bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "success compareAndExchangeRelease String value");
        }

        {
            String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact("bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact("foo", "bar");
            assertEquals(r, true, "weakCompareAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "weakCompareAndSet String value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact("bar", "foo");
            assertEquals(r, true, "weakCompareAndSetAcquire String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "weakCompareAndSetAcquire String");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact( "foo", "bar");
            assertEquals(r, true, "weakCompareAndSetRelease String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "bar", "weakCompareAndSetRelease String");
        }

        // Compare set and get
        {
            String o = (String) hs.get(TestAccessMode.getAndSet).invokeExact( "foo");
            assertEquals(o, "bar", "getAndSet String");
            String x = (String) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, "foo", "getAndSet String value");
        }

    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                String r = (String) hs.get(am).invokeExact("foo");
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        String[] array = new String[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.set).invokeExact(array, i, "foo");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "get String value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.setVolatile).invokeExact(array, i, "bar");
                String x = (String) hs.get(TestAccessMode.getVolatile).invokeExact(array, i);
                assertEquals(x, "bar", "setVolatile String value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.setRelease).invokeExact(array, i, "foo");
                String x = (String) hs.get(TestAccessMode.getAcquire).invokeExact(array, i);
                assertEquals(x, "foo", "setRelease String value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.setOpaque).invokeExact(array, i, "bar");
                String x = (String) hs.get(TestAccessMode.getOpaque).invokeExact(array, i);
                assertEquals(x, "bar", "setOpaque String value");
            }

            hs.get(TestAccessMode.set).invokeExact(array, i, "foo");

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, "foo", "bar");
                assertEquals(r, true, "success compareAndSet String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "success compareAndSet String value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.compareAndSet).invokeExact(array, i, "foo", "baz");
                assertEquals(r, false, "failing compareAndSet String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "failing compareAndSet String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, "bar", "foo");
                assertEquals(r, "bar", "success compareAndExchangeVolatile String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeVolatile).invokeExact(array, i, "bar", "baz");
                assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, "foo", "bar");
                assertEquals(r, "foo", "success compareAndExchangeAcquire String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeAcquire).invokeExact(array, i, "foo", "baz");
                assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, "bar", "foo");
                assertEquals(r, "bar", "success compareAndExchangeRelease String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "success compareAndExchangeRelease String value");
            }

            {
                String r = (String) hs.get(TestAccessMode.compareAndExchangeRelease).invokeExact(array, i, "bar", "baz");
                assertEquals(r, "foo", "failing compareAndExchangeRelease String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSet).invokeExact(array, i, "foo", "bar");
                assertEquals(r, true, "weakCompareAndSet String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "weakCompareAndSet String value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetAcquire).invokeExact(array, i, "bar", "foo");
                assertEquals(r, true, "weakCompareAndSetAcquire String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "weakCompareAndSetAcquire String");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.weakCompareAndSetRelease).invokeExact(array, i, "foo", "bar");
                assertEquals(r, true, "weakCompareAndSetRelease String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "bar", "weakCompareAndSetRelease String");
            }

            // Compare set and get
            {
                String o = (String) hs.get(TestAccessMode.getAndSet).invokeExact(array, i, "foo");
                assertEquals(o, "bar", "getAndSet String");
                String x = (String) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, "foo", "getAndSet String value");
            }

        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        String[] array = new String[10];

        final int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                String o = (String) hs.get(am).invokeExact(array, i, "foo");
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        String[] array = new String[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
                checkIOOBE(am, () -> {
                    String x = (String) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, "foo");
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, "foo", "bar");
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
                checkIOOBE(am, () -> {
                    String r = (String) hs.get(am).invokeExact(array, ci, "bar", "foo");
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
                checkIOOBE(am, () -> {
                    String o = (String) hs.get(am).invokeExact(array, ci, "foo");
                });
            }

        }
    }
}

