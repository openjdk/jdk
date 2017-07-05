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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessChar
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

public class VarHandleTestMethodHandleAccessChar extends VarHandleBaseTest {
    static final char static_final_v = 'a';

    static char static_v;

    final char final_v = 'a';

    char v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessChar.class, "final_v", char.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessChar.class, "v", char.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessChar.class, "static_final_v", char.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessChar.class, "static_v", char.class);

        vhArray = MethodHandles.arrayElementVarHandle(char[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessChar::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessChar::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessChar::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessChar::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessChar::testArrayIndexOutOfBounds,
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


    static void testInstanceField(VarHandleTestMethodHandleAccessChar recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 'a');
            char x = (char) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 'a', "set char value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, 'b');
            char x = (char) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(x, 'b', "setVolatile char value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, 'a');
            char x = (char) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(x, 'a', "setRelease char value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, 'b');
            char x = (char) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(x, 'b', "setOpaque char value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessChar recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(recv, 'a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact(recv, 'a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact(recv, 'a');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact(recv, 'a');
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact('a');
            char x = (char) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 'a', "set char value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact('b');
            char x = (char) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(x, 'b', "setVolatile char value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact('a');
            char x = (char) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(x, 'a', "setRelease char value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact('b');
            char x = (char) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(x, 'b', "setOpaque char value");
        }


    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact('a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact('a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact('a');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact('a');
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        char[] array = new char[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 'a');
                char x = (char) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 'a', "get char value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, 'b');
                char x = (char) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(x, 'b', "setVolatile char value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, 'a');
                char x = (char) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(x, 'a', "setRelease char value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, 'b');
                char x = (char) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(x, 'b', "setOpaque char value");
            }


        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        char[] array = new char[10];

        final int i = 0;
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkUOE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(array, i, 'a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact(array, i, 'a', 'b');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkUOE(am, () -> {
                char r = (char) hs.get(am).invokeExact(array, i, 'a');
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                char o = (char) hs.get(am).invokeExact(array, i, 'a');
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        char[] array = new char[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkIOOBE(am, () -> {
                    char x = (char) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 'a');
                });
            }


        }
    }
}

