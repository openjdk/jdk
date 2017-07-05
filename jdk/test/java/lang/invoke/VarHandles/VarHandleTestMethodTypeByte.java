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
 * @run testng/othervm VarHandleTestMethodTypeByte
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false VarHandleTestMethodTypeByte
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

public class VarHandleTestMethodTypeByte extends VarHandleBaseTest {
    static final byte static_final_v = (byte)1;

    static byte static_v = (byte)1;

    final byte final_v = (byte)1;

    byte v = (byte)1;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeByte.class, "final_v", byte.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeByte.class, "v", byte.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeByte.class, "static_final_v", byte.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeByte.class, "static_v", byte.class);

        vhArray = MethodHandles.arrayElementVarHandle(byte[].class);
    }

    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field wrong method type",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field wrong method type",
                                              vhStaticField, VarHandleTestMethodTypeByte::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array wrong method type",
                                              vhArray, VarHandleTestMethodTypeByte::testArrayWrongMethodType,
                                              false));
        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field wrong method type",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field wrong method type",
                                                     vhStaticField, f, VarHandleTestMethodTypeByte::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array wrong method type",
                                                     vhArray, f, VarHandleTestMethodTypeByte::testArrayWrongMethodType,
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


    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeByte recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            byte x = (byte) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            byte x = (byte) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            byte x = (byte) vh.get(0);
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
            byte x = (byte) vh.get();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, (byte)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, (byte)1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            byte x = (byte) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            byte x = (byte) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            byte x = (byte) vh.getVolatile(0);
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
            byte x = (byte) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, (byte)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, (byte)1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            byte x = (byte) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            byte x = (byte) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            byte x = (byte) vh.getOpaque(0);
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
            byte x = (byte) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, (byte)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, (byte)1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            byte x = (byte) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            byte x = (byte) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            byte x = (byte) vh.getAcquire(0);
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
            byte x = (byte) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, (byte)1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, (byte)1, Void.class);
        });



    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeByte recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                byte x = (byte) hs.get(am, methodType(byte.class, Void.class)).
                    invoke(null);
            });
            checkCCE(() -> { // receiver reference class
                byte x = (byte) hs.get(am, methodType(byte.class, Class.class)).
                    invoke(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                byte x = (byte) hs.get(am, methodType(byte.class, int.class)).
                    invoke(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(byte.class, VarHandleTestMethodTypeByte.class)).
                    invoke(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeByte.class)).
                    invoke(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                byte x = (byte) hs.get(am, methodType(byte.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                byte x = (byte) hs.get(am, methodType(byte.class, VarHandleTestMethodTypeByte.class, Class.class)).
                    invoke(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, Void.class, byte.class)).
                    invoke(null, (byte)1);
            });
            checkCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, byte.class)).
                    invoke(Void.class, (byte)1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeByte.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, byte.class)).
                    invoke(0, (byte)1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeByte.class, byte.class, Class.class)).
                    invoke(recv, (byte)1, Void.class);
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
            byte x = (byte) vh.get(Void.class);
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
            vh.set((byte)1, Void.class);
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
            byte x = (byte) vh.getVolatile(Void.class);
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
            vh.setVolatile((byte)1, Void.class);
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
            byte x = (byte) vh.getOpaque(Void.class);
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
            vh.setOpaque((byte)1, Void.class);
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
            byte x = (byte) vh.getAcquire(Void.class);
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
            vh.setRelease((byte)1, Void.class);
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
                byte x = (byte) hs.get(am, methodType(Class.class)).
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
                hs.get(am, methodType(void.class, byte.class, Class.class)).
                    invoke((byte)1, Void.class);
            });
        }

    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        byte[] array = new byte[10];
        Arrays.fill(array, (byte)1);

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            byte x = (byte) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            byte x = (byte) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            byte x = (byte) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            byte x = (byte) vh.get(array, Void.class);
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
            byte x = (byte) vh.get();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, (byte)1);
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, (byte)1);
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, (byte)1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            byte x = (byte) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            byte x = (byte) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            byte x = (byte) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            byte x = (byte) vh.getVolatile(array, Void.class);
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
            byte x = (byte) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, (byte)1);
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, (byte)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, (byte)1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            byte x = (byte) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            byte x = (byte) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            byte x = (byte) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            byte x = (byte) vh.getOpaque(array, Void.class);
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
            byte x = (byte) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, (byte)1);
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, (byte)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, (byte)1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            byte x = (byte) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            byte x = (byte) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            byte x = (byte) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            byte x = (byte) vh.getAcquire(array, Void.class);
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
            byte x = (byte) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            byte x = (byte) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, (byte)1);
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, (byte)1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, (byte)1);
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, (byte)1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, (byte)1, Void.class);
        });



    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        byte[] array = new byte[10];
        Arrays.fill(array, (byte)1);

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                byte x = (byte) hs.get(am, methodType(byte.class, Void.class, int.class)).
                    invoke(null, 0);
            });
            checkCCE(() -> { // array reference class
                byte x = (byte) hs.get(am, methodType(byte.class, Class.class, int.class)).
                    invoke(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                byte x = (byte) hs.get(am, methodType(byte.class, int.class, int.class)).
                    invoke(0, 0);
            });
            checkWMTE(() -> { // index reference class
                byte x = (byte) hs.get(am, methodType(byte.class, byte[].class, Class.class)).
                    invoke(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, byte[].class, int.class)).
                    invoke(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, byte[].class, int.class)).
                    invoke(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                byte x = (byte) hs.get(am, methodType(byte.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                byte x = (byte) hs.get(am, methodType(byte.class, byte[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Void.class, int.class, byte.class)).
                    invoke(null, 0, (byte)1);
            });
            checkCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, byte.class)).
                    invoke(Void.class, 0, (byte)1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, byte[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, byte.class)).
                    invoke(0, 0, (byte)1);
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, byte[].class, Class.class, byte.class)).
                    invoke(array, Void.class, (byte)1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, byte[].class, int.class, Class.class)).
                    invoke(array, 0, (byte)1, Void.class);
            });
        }

    }
}

