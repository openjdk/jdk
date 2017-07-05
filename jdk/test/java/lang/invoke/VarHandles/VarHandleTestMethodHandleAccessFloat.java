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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessFloat
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

public class VarHandleTestMethodHandleAccessFloat extends VarHandleBaseTest {
    static final float static_final_v = 1.0f;

    static float static_v;

    final float final_v = 1.0f;

    float v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessFloat.class, "final_v", float.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessFloat.class, "v", float.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessFloat.class, "static_final_v", float.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessFloat.class, "static_v", float.class);

        vhArray = MethodHandles.arrayElementVarHandle(float[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessFloat::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessFloat::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessFloat::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessFloat::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessFloat::testArrayIndexOutOfBounds,
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


    static void testInstanceField(VarHandleTestMethodHandleAccessFloat recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(recv, 1.0f);
            float x = (float) hs.get(TestAccessMode.get).invokeExact(recv);
            assertEquals(x, 1.0f, "set float value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(recv, 2.0f);
            float x = (float) hs.get(TestAccessMode.getVolatile).invokeExact(recv);
            assertEquals(x, 2.0f, "setVolatile float value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(recv, 1.0f);
            float x = (float) hs.get(TestAccessMode.getAcquire).invokeExact(recv);
            assertEquals(x, 1.0f, "setRelease float value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(recv, 2.0f);
            float x = (float) hs.get(TestAccessMode.getOpaque).invokeExact(recv);
            assertEquals(x, 2.0f, "setOpaque float value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessFloat recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(recv, 1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(recv, 1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(recv, 1.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(recv, 1.0f);
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.set).invokeExact(1.0f);
            float x = (float) hs.get(TestAccessMode.get).invokeExact();
            assertEquals(x, 1.0f, "set float value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.setVolatile).invokeExact(2.0f);
            float x = (float) hs.get(TestAccessMode.getVolatile).invokeExact();
            assertEquals(x, 2.0f, "setVolatile float value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.setRelease).invokeExact(1.0f);
            float x = (float) hs.get(TestAccessMode.getAcquire).invokeExact();
            assertEquals(x, 1.0f, "setRelease float value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.setOpaque).invokeExact(2.0f);
            float x = (float) hs.get(TestAccessMode.getOpaque).invokeExact();
            assertEquals(x, 2.0f, "setOpaque float value");
        }


    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(1.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(1.0f);
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        float[] array = new float[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.set).invokeExact(array, i, 1.0f);
                float x = (float) hs.get(TestAccessMode.get).invokeExact(array, i);
                assertEquals(x, 1.0f, "get float value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.setVolatile).invokeExact(array, i, 2.0f);
                float x = (float) hs.get(TestAccessMode.getVolatile).invokeExact(array, i);
                assertEquals(x, 2.0f, "setVolatile float value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.setRelease).invokeExact(array, i, 1.0f);
                float x = (float) hs.get(TestAccessMode.getAcquire).invokeExact(array, i);
                assertEquals(x, 1.0f, "setRelease float value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.setOpaque).invokeExact(array, i, 2.0f);
                float x = (float) hs.get(TestAccessMode.getOpaque).invokeExact(array, i);
                assertEquals(x, 2.0f, "setOpaque float value");
            }


        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        float[] array = new float[10];

        final int i = 0;
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(array, i, 1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(array, i, 1.0f, 2.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            checkUOE(am, () -> {
                float r = (float) hs.get(am).invokeExact(array, i, 1.0f);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkUOE(am, () -> {
                float o = (float) hs.get(am).invokeExact(array, i, 1.0f);
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        float[] array = new float[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
                checkIOOBE(am, () -> {
                    float x = (float) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 1.0f);
                });
            }


        }
    }
}

