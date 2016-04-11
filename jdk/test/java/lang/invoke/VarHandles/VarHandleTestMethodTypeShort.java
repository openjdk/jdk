/*
 * Copyright (c) 2015, 2016 Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm VarHandleTestMethodTypeShort
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false VarHandleTestMethodTypeShort
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

import static java.lang.invoke.MethodType.*;

public class VarHandleTestMethodTypeShort extends VarHandleBaseTest {
    static final short static_final_v = (short)1;

    static short static_v = (short)1;

    final short final_v = (short)1;

    short v = (short)1;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeShort.class, "final_v", short.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeShort.class, "v", short.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeShort.class, "static_final_v", short.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeShort.class, "static_v", short.class);

        vhArray = MethodHandles.arrayElementVarHandle(short[].class);
    }

    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field wrong method type",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field wrong method type",
                                              vhStaticField, VarHandleTestMethodTypeShort::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array wrong method type",
                                              vhArray, VarHandleTestMethodTypeShort::testArrayWrongMethodType,
                                              false));
        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field wrong method type",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field wrong method type",
                                                     vhStaticField, f, VarHandleTestMethodTypeShort::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array wrong method type",
                                                     vhArray, f, VarHandleTestMethodTypeShort::testArrayWrongMethodType,
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


    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeShort recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            short x = (short) vh.get(0);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.get(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.get();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, (short)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, (short)1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            short x = (short) vh.getVolatile(0);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getVolatile(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, (short)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, (short)1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            short x = (short) vh.getOpaque(0);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getOpaque(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, (short)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, (short)1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            short x = (short) vh.getAcquire(0);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getAcquire(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, (short)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, (short)1, Void.class);
        });



    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeShort recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, Void.class)).
                    invoke(null);
            });
            checkCCE(() -> { // receiver reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class)).
                    invoke(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class)).
                    invoke(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class)).
                    invoke(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class)).
                    invoke(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invoke(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, Void.class, short.class)).
                    invoke(null, (short)1);
            });
            checkCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, short.class)).
                    invoke(Void.class, (short)1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, short.class)).
                    invoke(0, (short)1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeShort.class, short.class, Class.class)).
                    invoke(recv, (short)1, Void.class);
            });
        }


    }


    static void testStaticFieldWrongMethodType(VarHandle vh) throws Throwable {
        // Get
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.get();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get();
        });
        // Incorrect arity
        checkWMTE(() -> { // >
            short x = (short) vh.get(Void.class);
        });


        // Set
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            vh.set(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set((short)1, Void.class);
        });


        // GetVolatile
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getVolatile();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getVolatile(Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            vh.setVolatile(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile((short)1, Void.class);
        });


        // GetOpaque
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getOpaque();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getOpaque(Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            vh.setOpaque(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque((short)1, Void.class);
        });


        // GetAcquire
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getAcquire();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAcquire(Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            vh.setRelease(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease((short)1, Void.class);
        });



    }

    static void testStaticFieldWrongMethodType(Handles hs) throws Throwable {
        int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class)).
                    invoke();
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            // Incorrect arity
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(Class.class)).
                    invoke(Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, Class.class)).
                    invoke(Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, short.class, Class.class)).
                    invoke((short)1, Void.class);
            });
        }

    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        short[] array = new short[10];
        Arrays.fill(array, (short)1);

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.get(array, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.get(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.get();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, (short)1);
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, (short)1);
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, (short)1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.getVolatile(array, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getVolatile(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, (short)1);
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, (short)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, (short)1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.getOpaque(array, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getOpaque(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, (short)1);
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, (short)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, (short)1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.getAcquire(array, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void x = (Void) vh.getAcquire(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, (short)1);
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, (short)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, (short)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, (short)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, (short)1, Void.class);
        });



    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        short[] array = new short[10];
        Arrays.fill(array, (short)1);

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                short x = (short) hs.get(am, methodType(short.class, Void.class, int.class)).
                    invoke(null, 0);
            });
            checkCCE(() -> { // array reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, int.class)).
                    invoke(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, int.class)).
                    invoke(0, 0);
            });
            checkWMTE(() -> { // index reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, Class.class)).
                    invoke(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, short[].class, int.class)).
                    invoke(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class)).
                    invoke(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Void.class, int.class, short.class)).
                    invoke(null, 0, (short)1);
            });
            checkCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, short.class)).
                    invoke(Void.class, 0, (short)1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, short[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, short.class)).
                    invoke(0, 0, (short)1);
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, short[].class, Class.class, short.class)).
                    invoke(array, Void.class, (short)1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, short[].class, int.class, Class.class)).
                    invoke(array, 0, (short)1, Void.class);
            });
        }

    }
}

