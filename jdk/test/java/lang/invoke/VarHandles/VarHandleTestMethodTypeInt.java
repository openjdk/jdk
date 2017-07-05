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
 * @run testng/othervm VarHandleTestMethodTypeInt
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false VarHandleTestMethodTypeInt
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

public class VarHandleTestMethodTypeInt extends VarHandleBaseTest {
    static final int static_final_v = 1;

    static int static_v = 1;

    final int final_v = 1;

    int v = 1;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeInt.class, "final_v", int.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeInt.class, "v", int.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeInt.class, "static_final_v", int.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeInt.class, "static_v", int.class);

        vhArray = MethodHandles.arrayElementVarHandle(int[].class);
    }

    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field wrong method type",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field wrong method type",
                                              vhStaticField, VarHandleTestMethodTypeInt::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array wrong method type",
                                              vhArray, VarHandleTestMethodTypeInt::testArrayWrongMethodType,
                                              false));
        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field wrong method type",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field wrong method type",
                                                     vhStaticField, f, VarHandleTestMethodTypeInt::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array wrong method type",
                                                     vhArray, f, VarHandleTestMethodTypeInt::testArrayWrongMethodType,
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


    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeInt recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            int x = (int) vh.get(0);
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
            int x = (int) vh.get();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, 1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            int x = (int) vh.getVolatile(0);
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
            int x = (int) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, 1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            int x = (int) vh.getOpaque(0);
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
            int x = (int) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, 1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            int x = (int) vh.getAcquire(0);
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
            int x = (int) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, 1, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(recv, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(recv, 1, 1, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(recv, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(recv, 1, 1, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(recv, 1, 1, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(recv, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(recv, 1, 1, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeVolatile(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.compareAndExchangeVolatile(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeVolatile(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeVolatile(recv, 1, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.compareAndExchangeVolatile(0, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(recv, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(recv, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeVolatile(recv, 1, 1, Void.class);
        });


        // CompareAndExchangeVolatileAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeAcquire(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.compareAndExchangeAcquire(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeAcquire(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeAcquire(recv, 1, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.compareAndExchangeAcquire(0, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(recv, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(recv, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeAcquire(recv, 1, 1, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeRelease(null, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.compareAndExchangeRelease(Void.class, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeRelease(recv, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeRelease(recv, 1, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.compareAndExchangeRelease(0, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(recv, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(recv, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeRelease(recv, 1, 1, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.getAndSet(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.getAndSet(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndSet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.getAndSet(0, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(recv, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(recv, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndSet(recv, 1, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.getAndAdd(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.getAndAdd(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndAdd(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.getAndAdd(0, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(recv, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(recv, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndAdd(recv, 1, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.addAndGet(null, 1);
        });
        checkCCE(() -> { // receiver reference class
            int x = (int) vh.addAndGet(Void.class, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.addAndGet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            int x = (int) vh.addAndGet(0, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(recv, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(recv, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.addAndGet(recv, 1, Void.class);
        });
    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeInt recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                int x = (int) hs.get(am, methodType(int.class, Void.class)).
                    invoke(null);
            });
            checkCCE(() -> { // receiver reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class)).
                    invoke(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class)).
                    invoke(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class)).
                    invoke(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class)).
                    invoke(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, Class.class)).
                    invoke(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, Void.class, int.class)).
                    invoke(null, 1);
            });
            checkCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, int.class)).
                    invoke(Void.class, 1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeInt.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class)).
                    invoke(0, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeInt.class, int.class, Class.class)).
                    invoke(recv, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Void.class, int.class, int.class)).
                    invoke(null, 1, 1);
            });
            checkCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, int.class)).
                    invoke(Void.class, 1, 1);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class, Class.class, int.class)).
                    invoke(recv, Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class, int.class, Class.class)).
                    invoke(recv, 1, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class , int.class, int.class)).
                    invoke(0, 1, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class, int.class, int.class, Class.class)).
                    invoke(recv, 1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            checkNPE(() -> { // null receiver
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class, int.class)).
                    invoke(null, 1, 1);
            });
            checkCCE(() -> { // receiver reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class, int.class)).
                    invoke(Void.class, 1, 1);
            });
            checkWMTE(() -> { // expected reference class
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, Class.class, int.class)).
                    invoke(recv, Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, int.class, Class.class)).
                    invoke(recv, 1, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class , int.class, int.class)).
                    invoke(0, 1, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeInt.class , int.class, int.class)).
                    invoke(recv, 1, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class , int.class, int.class)).
                    invoke(recv, 1, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, int.class, int.class, Class.class)).
                    invoke(recv, 1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            checkNPE(() -> { // null receiver
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class)).
                    invoke(null, 1);
            });
            checkCCE(() -> { // receiver reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class)).
                    invoke(Void.class, 1);
            });
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class)).
                    invoke(0, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            checkNPE(() -> { // null receiver
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class)).
                    invoke(null, 1);
            });
            checkCCE(() -> { // receiver reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class)).
                    invoke(Void.class, 1);
            });
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class)).
                    invoke(0, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, VarHandleTestMethodTypeInt.class, int.class)).
                    invoke(recv, 1, Void.class);
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
            int x = (int) vh.get(Void.class);
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
            vh.set(1, Void.class);
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
            int x = (int) vh.getVolatile(Void.class);
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
            vh.setVolatile(1, Void.class);
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
            int x = (int) vh.getOpaque(Void.class);
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
            vh.setOpaque(1, Void.class);
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
            int x = (int) vh.getAcquire(Void.class);
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
            vh.setRelease(1, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(1, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(1, 1, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(1, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(1, 1, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(1, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(1, 1, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(1, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(1, 1, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeVolatile(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeVolatile(1, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeVolatile(1, 1, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeAcquire(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeAcquire(1, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeAcquire(1, 1, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeRelease(Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeRelease(1, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeRelease(1, 1, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndSet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndSet(1, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndAdd(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndAdd(1, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            int x = (int) vh.addAndGet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.addAndGet(1, Void.class);
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
                int x = (int) hs.get(am, methodType(Class.class)).
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
                hs.get(am, methodType(void.class, int.class, Class.class)).
                    invoke(1, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class)).
                    invoke(Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, Class.class)).
                    invoke(1, Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, Class.class)).
                    invoke(1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class)).
                    invoke(Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                int x = (int) hs.get(am, methodType(int.class, int.class, Class.class)).
                    invoke(1, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int.class, int.class)).
                    invoke(1, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class)).
                    invoke(1, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class, Class.class)).
                    invoke(1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class)).
                    invoke(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int.class)).
                    invoke(1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int.class)).
                    invoke(1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int.class, Class.class)).
                    invoke(1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class)).
                    invoke(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int.class)).
                    invoke(1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int.class)).
                    invoke(1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int.class, Class.class)).
                    invoke(1, Void.class);
            });
        }
    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        int[] array = new int[10];
        Arrays.fill(array, 1);

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.get(array, Void.class);
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
            int x = (int) vh.get();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, 1, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.getVolatile(array, Void.class);
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
            int x = (int) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, 1, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.getOpaque(array, Void.class);
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
            int x = (int) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, 1, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.getAcquire(array, Void.class);
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
            int x = (int) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, 1, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 0, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.compareAndSet(array, Void.class, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(array, 0, 1, 1, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 0, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSet(array, Void.class, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(array, 0, 1, 1, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 0, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetAcquire(array, Void.class, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(array, 0, 1, 1, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 0, 1, 1);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetRelease(array, Void.class, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(array, 0, 1, 1, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeVolatile(null, 0, 1, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.compareAndExchangeVolatile(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeVolatile(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeVolatile(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.compareAndExchangeVolatile(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.compareAndExchangeVolatile(array, Void.class, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(array, 0, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(array, 0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeVolatile(array, 0, 1, 1, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeAcquire(null, 0, 1, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.compareAndExchangeAcquire(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeAcquire(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeAcquire(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.compareAndExchangeAcquire(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.compareAndExchangeAcquire(array, Void.class, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(array, 0, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(array, 0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeAcquire(array, 0, 1, 1, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            int x = (int) vh.compareAndExchangeRelease(null, 0, 1, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.compareAndExchangeRelease(Void.class, 0, 1, 1);
        });
        checkWMTE(() -> { // expected reference class
            int x = (int) vh.compareAndExchangeRelease(array, 0, Void.class, 1);
        });
        checkWMTE(() -> { // actual reference class
            int x = (int) vh.compareAndExchangeRelease(array, 0, 1, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.compareAndExchangeRelease(0, 0, 1, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.compareAndExchangeRelease(array, Void.class, 1, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(array, 0, 1, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(array, 0, 1, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.compareAndExchangeRelease(array, 0, 1, 1, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.getAndSet(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.getAndSet(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndSet(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            int x = (int) vh.getAndSet(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.getAndSet(array, Void.class, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(array, 0, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(array, 0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndSet(array, 0, 1, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.getAndAdd(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.getAndAdd(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.getAndAdd(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.getAndAdd(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.getAndAdd(array, Void.class, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(array, 0, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(array, 0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.getAndAdd(array, 0, 1, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null array
            int x = (int) vh.addAndGet(null, 0, 1);
        });
        checkCCE(() -> { // array reference class
            int x = (int) vh.addAndGet(Void.class, 0, 1);
        });
        checkWMTE(() -> { // value reference class
            int x = (int) vh.addAndGet(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            int x = (int) vh.addAndGet(0, 0, 1);
        });
        checkWMTE(() -> { // index reference class
            int x = (int) vh.addAndGet(array, Void.class, 1);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(array, 0, 1);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(array, 0, 1);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            int x = (int) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            int x = (int) vh.addAndGet(array, 0, 1, Void.class);
        });
    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        int[] array = new int[10];
        Arrays.fill(array, 1);

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.get)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class)).
                    invoke(null, 0);
            });
            checkCCE(() -> { // array reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class)).
                    invoke(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class)).
                    invoke(0, 0);
            });
            checkWMTE(() -> { // index reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, Class.class)).
                    invoke(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, int[].class, int.class)).
                    invoke(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class)).
                    invoke(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.set)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Void.class, int.class, int.class)).
                    invoke(null, 0, 1);
            });
            checkCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, int.class)).
                    invoke(Void.class, 0, 1);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, int[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, int.class)).
                    invoke(0, 0, 1);
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, int[].class, Class.class, int.class)).
                    invoke(array, Void.class, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, int[].class, int.class, Class.class)).
                    invoke(array, 0, 1, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndSet)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Void.class, int.class, int.class, int.class)).
                    invoke(null, 0, 1, 1);
            });
            checkCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, int.class, int.class)).
                    invoke(Void.class, 0, 1, 1);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, Class.class, int.class)).
                    invoke(array, 0, Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, int.class, int.class)).
                    invoke(0, 0, 1, 1);
            });
            checkWMTE(() -> { // index reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int[].class, Class.class, int.class, int.class)).
                    invoke(array, Void.class, 1, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.compareAndExchange)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class, int.class, int.class)).
                    invoke(null, 0, 1, 1);
            });
            checkCCE(() -> { // array reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class, int.class, int.class)).
                    invoke(Void.class, 0, 1, 1);
            });
            checkWMTE(() -> { // expected reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, Class.class, int.class)).
                    invoke(array, 0, Void.class, 1);
            });
            checkWMTE(() -> { // actual reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class, int.class, int.class)).
                    invoke(0, 0, 1, 1);
            });
            checkWMTE(() -> { // index reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, Class.class, int.class, int.class)).
                    invoke(array, Void.class, 1, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int[].class, int.class, int.class, int.class)).
                    invoke(array, 0, 1, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, int.class, int.class)).
                    invoke(array, 0, 1, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndSet)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class, int.class)).
                    invoke(null, 0, 1);
            });
            checkCCE(() -> { // array reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class, int.class)).
                    invoke(Void.class, 0, 1);
            });
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class, int.class)).
                    invoke(0, 0, 1);
            });
            checkWMTE(() -> { // index reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, Class.class, int.class)).
                    invoke(array, Void.class, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int[].class, int.class, int.class)).
                    invoke(array, 0, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, int.class)).
                    invoke(array, 0, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.getAndAdd)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                int x = (int) hs.get(am, methodType(int.class, Void.class, int.class, int.class)).
                    invoke(null, 0, 1);
            });
            checkCCE(() -> { // array reference class
                int x = (int) hs.get(am, methodType(int.class, Class.class, int.class, int.class)).
                    invoke(Void.class, 0, 1);
            });
            checkWMTE(() -> { // value reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                int x = (int) hs.get(am, methodType(int.class, int.class, int.class, int.class)).
                    invoke(0, 0, 1);
            });
            checkWMTE(() -> { // index reference class
                int x = (int) hs.get(am, methodType(int.class, int[].class, Class.class, int.class)).
                    invoke(array, Void.class, 1);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, int[].class, int.class, int.class)).
                    invoke(array, 0, 1);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, int[].class, int.class, int.class)).
                    invoke(array, 0, 1);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                int x = (int) hs.get(am, methodType(int.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                int x = (int) hs.get(am, methodType(int.class, int[].class, int.class, int.class, Class.class)).
                    invoke(array, 0, 1, Void.class);
            });
        }
    }
}

