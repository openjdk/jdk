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
 * @run testng/othervm VarHandleTestMethodTypeChar
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false VarHandleTestMethodTypeChar
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

public class VarHandleTestMethodTypeChar extends VarHandleBaseTest {
    static final char static_final_v = 'a';

    static char static_v = 'a';

    final char final_v = 'a';

    char v = 'a';

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeChar.class, "final_v", char.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeChar.class, "v", char.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeChar.class, "static_final_v", char.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeChar.class, "static_v", char.class);

        vhArray = MethodHandles.arrayElementVarHandle(char[].class);
    }

    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field wrong method type",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field wrong method type",
                                              vhStaticField, VarHandleTestMethodTypeChar::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array wrong method type",
                                              vhArray, VarHandleTestMethodTypeChar::testArrayWrongMethodType,
                                              false));
        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field wrong method type",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field wrong method type",
                                                     vhStaticField, f, VarHandleTestMethodTypeChar::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array wrong method type",
                                                     vhArray, f, VarHandleTestMethodTypeChar::testArrayWrongMethodType,
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


    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeChar recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            char x = (char) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            char x = (char) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            char x = (char) vh.get(0);
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
            char x = (char) vh.get();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, 'a');
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, 'a', Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            char x = (char) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            char x = (char) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            char x = (char) vh.getVolatile(0);
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
            char x = (char) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, 'a');
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, 'a', Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            char x = (char) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            char x = (char) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            char x = (char) vh.getOpaque(0);
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
            char x = (char) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, 'a');
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, 'a', Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            char x = (char) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            char x = (char) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            char x = (char) vh.getAcquire(0);
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
            char x = (char) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, 'a');
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, 'a', Void.class);
        });



    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeChar recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                char x = (char) hs.get(am, methodType(char.class, Void.class)).
                    invoke(null);
            });
            checkCCE(() -> { // receiver reference class
                char x = (char) hs.get(am, methodType(char.class, Class.class)).
                    invoke(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                char x = (char) hs.get(am, methodType(char.class, int.class)).
                    invoke(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(char.class, VarHandleTestMethodTypeChar.class)).
                    invoke(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeChar.class)).
                    invoke(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                char x = (char) hs.get(am, methodType(char.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                char x = (char) hs.get(am, methodType(char.class, VarHandleTestMethodTypeChar.class, Class.class)).
                    invoke(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, Void.class, char.class)).
                    invoke(null, 'a');
            });
            checkCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, char.class)).
                    invoke(Void.class, 'a');
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeChar.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, char.class)).
                    invoke(0, 'a');
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeChar.class, char.class, Class.class)).
                    invoke(recv, 'a', Void.class);
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
            char x = (char) vh.get(Void.class);
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
            vh.set('a', Void.class);
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
            char x = (char) vh.getVolatile(Void.class);
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
            vh.setVolatile('a', Void.class);
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
            char x = (char) vh.getOpaque(Void.class);
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
            vh.setOpaque('a', Void.class);
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
            char x = (char) vh.getAcquire(Void.class);
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
            vh.setRelease('a', Void.class);
        });



    }

    static void testStaticFieldWrongMethodType(Handles hs) throws Throwable {
        int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
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
                char x = (char) hs.get(am, methodType(Class.class)).
                    invoke(Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
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
                hs.get(am, methodType(void.class, char.class, Class.class)).
                    invoke('a', Void.class);
            });
        }

    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        char[] array = new char[10];
        Arrays.fill(array, 'a');

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            char x = (char) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            char x = (char) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            char x = (char) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            char x = (char) vh.get(array, Void.class);
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
            char x = (char) vh.get();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, 'a');
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, 'a');
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, 'a', Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            char x = (char) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            char x = (char) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            char x = (char) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            char x = (char) vh.getVolatile(array, Void.class);
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
            char x = (char) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, 'a');
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, 'a');
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, 'a', Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            char x = (char) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            char x = (char) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            char x = (char) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            char x = (char) vh.getOpaque(array, Void.class);
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
            char x = (char) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, 'a');
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, 'a');
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, 'a', Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            char x = (char) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            char x = (char) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            char x = (char) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            char x = (char) vh.getAcquire(array, Void.class);
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
            char x = (char) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            char x = (char) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, 'a');
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, 'a');
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, 'a');
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, 'a');
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, 'a', Void.class);
        });



    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        char[] array = new char[10];
        Arrays.fill(array, 'a');

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                char x = (char) hs.get(am, methodType(char.class, Void.class, int.class)).
                    invoke(null, 0);
            });
            checkCCE(() -> { // array reference class
                char x = (char) hs.get(am, methodType(char.class, Class.class, int.class)).
                    invoke(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                char x = (char) hs.get(am, methodType(char.class, int.class, int.class)).
                    invoke(0, 0);
            });
            checkWMTE(() -> { // index reference class
                char x = (char) hs.get(am, methodType(char.class, char[].class, Class.class)).
                    invoke(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, char[].class, int.class)).
                    invoke(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, char[].class, int.class)).
                    invoke(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                char x = (char) hs.get(am, methodType(char.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                char x = (char) hs.get(am, methodType(char.class, char[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Void.class, int.class, char.class)).
                    invoke(null, 0, 'a');
            });
            checkCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, char.class)).
                    invoke(Void.class, 0, 'a');
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, char[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, char.class)).
                    invoke(0, 0, 'a');
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, char[].class, Class.class, char.class)).
                    invoke(array, Void.class, 'a');
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, char[].class, int.class, Class.class)).
                    invoke(array, 0, 'a', Void.class);
            });
        }

    }
}

