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
 * @run testng/othervm VarHandleTestMethodTypeLong
 * @run testng/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false VarHandleTestMethodTypeLong
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

public class VarHandleTestMethodTypeLong extends VarHandleBaseTest {
    static final long static_final_v = 1L;

    static long static_v = 1L;

    final long final_v = 1L;

    long v = 1L;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeLong.class, "final_v", long.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeLong.class, "v", long.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeLong.class, "static_final_v", long.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeLong.class, "static_v", long.class);

        vhArray = MethodHandles.arrayElementVarHandle(long[].class);
    }

    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field wrong method type",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field wrong method type",
                                              vhStaticField, VarHandleTestMethodTypeLong::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array wrong method type",
                                              vhArray, VarHandleTestMethodTypeLong::testArrayWrongMethodType,
                                              false));
        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field wrong method type",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field wrong method type",
                                                     vhStaticField, f, VarHandleTestMethodTypeLong::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array wrong method type",
                                                     vhArray, f, VarHandleTestMethodTypeLong::testArrayWrongMethodType,
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


    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeLong recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            long x = (long) vh.get(0);
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
            long x = (long) vh.get();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, 1L, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            long x = (long) vh.getVolatile(0);
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
            long x = (long) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, 1L, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            long x = (long) vh.getOpaque(0);
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
            long x = (long) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, 1L, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            long x = (long) vh.getAcquire(0);
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
            long x = (long) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, 1L, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(recv, 1L, 1L, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(recv, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetVolatile(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetVolatile(0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile(recv, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(recv, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(recv, 1L, 1L, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeVolatile(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.compareAndExchangeVolatile(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeVolatile(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeVolatile(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.compareAndExchangeVolatile(0, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(recv, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(recv, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeVolatile(recv, 1L, 1L, Void.class);
        });


        // CompareAndExchangeVolatileAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeAcquire(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.compareAndExchangeAcquire(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeAcquire(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeAcquire(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.compareAndExchangeAcquire(0, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(recv, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(recv, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeAcquire(recv, 1L, 1L, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeRelease(null, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.compareAndExchangeRelease(Void.class, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeRelease(recv, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeRelease(recv, 1L, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.compareAndExchangeRelease(0, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(recv, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(recv, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeRelease(recv, 1L, 1L, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.getAndSet(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.getAndSet(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndSet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.getAndSet(0, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(recv, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(recv, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndSet(recv, 1L, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.getAndAdd(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.getAndAdd(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndAdd(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.getAndAdd(0, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(recv, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(recv, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndAdd(recv, 1L, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.addAndGet(null, 1L);
        });
        checkCCE(() -> { // receiver reference class
            long x = (long) vh.addAndGet(Void.class, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.addAndGet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            long x = (long) vh.addAndGet(0, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(recv, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(recv, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.addAndGet(recv, 1L, Void.class);
        });
    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeLong recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                long x = (long) hs.get(am, methodType(long.class, Void.class)).
                    invoke(null);
            });
            checkCCE(() -> { // receiver reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class)).
                    invoke(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class)).
                    invoke(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class)).
                    invoke(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class)).
                    invoke(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, Class.class)).
                    invoke(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, Void.class, long.class)).
                    invoke(null, 1L);
            });
            checkCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, long.class)).
                    invoke(Void.class, 1L);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeLong.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, long.class)).
                    invoke(0, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeLong.class, long.class, Class.class)).
                    invoke(recv, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Void.class, long.class, long.class)).
                    invoke(null, 1L, 1L);
            });
            checkCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, long.class, long.class)).
                    invoke(Void.class, 1L, 1L);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class, Class.class, long.class)).
                    invoke(recv, Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class, long.class, Class.class)).
                    invoke(recv, 1L, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class , long.class, long.class)).
                    invoke(0, 1L, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class, long.class, long.class, Class.class)).
                    invoke(recv, 1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(() -> { // null receiver
                long x = (long) hs.get(am, methodType(long.class, Void.class, long.class, long.class)).
                    invoke(null, 1L, 1L);
            });
            checkCCE(() -> { // receiver reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, long.class, long.class)).
                    invoke(Void.class, 1L, 1L);
            });
            checkWMTE(() -> { // expected reference class
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, Class.class, long.class)).
                    invoke(recv, Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, long.class, Class.class)).
                    invoke(recv, 1L, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class , long.class, long.class)).
                    invoke(0, 1L, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeLong.class , long.class, long.class)).
                    invoke(recv, 1L, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class , long.class, long.class)).
                    invoke(recv, 1L, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, long.class, long.class, Class.class)).
                    invoke(recv, 1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(() -> { // null receiver
                long x = (long) hs.get(am, methodType(long.class, Void.class, long.class)).
                    invoke(null, 1L);
            });
            checkCCE(() -> { // receiver reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, long.class)).
                    invoke(Void.class, 1L);
            });
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, long.class)).
                    invoke(0, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkNPE(() -> { // null receiver
                long x = (long) hs.get(am, methodType(long.class, Void.class, long.class)).
                    invoke(null, 1L);
            });
            checkCCE(() -> { // receiver reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, long.class)).
                    invoke(Void.class, 1L);
            });
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, Class.class)).
                    invoke(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, long.class)).
                    invoke(0, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, VarHandleTestMethodTypeLong.class, long.class)).
                    invoke(recv, 1L, Void.class);
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
            long x = (long) vh.get(Void.class);
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
            vh.set(1L, Void.class);
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
            long x = (long) vh.getVolatile(Void.class);
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
            vh.setVolatile(1L, Void.class);
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
            long x = (long) vh.getOpaque(Void.class);
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
            vh.setOpaque(1L, Void.class);
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
            long x = (long) vh.getAcquire(Void.class);
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
            vh.setRelease(1L, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(1L, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(1L, 1L, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(1L, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(1L, 1L, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile(1L, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile(1L, 1L, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(1L, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(1L, 1L, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(1L, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(1L, 1L, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeVolatile(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeVolatile(1L, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeVolatile(1L, 1L, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeAcquire(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeAcquire(1L, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeAcquire(1L, 1L, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeRelease(Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeRelease(1L, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeRelease(1L, 1L, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndSet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndSet(1L, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndAdd(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndAdd(1L, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            long x = (long) vh.addAndGet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.addAndGet(1L, Void.class);
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
                long x = (long) hs.get(am, methodType(Class.class)).
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
                hs.get(am, methodType(void.class, long.class, Class.class)).
                    invoke(1L, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, long.class)).
                    invoke(Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long.class, Class.class)).
                    invoke(1L, Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long.class, long.class, Class.class)).
                    invoke(1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, long.class)).
                    invoke(Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                long x = (long) hs.get(am, methodType(long.class, long.class, Class.class)).
                    invoke(1L, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long.class, long.class)).
                    invoke(1L, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long.class, long.class)).
                    invoke(1L, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long.class, long.class, Class.class)).
                    invoke(1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class)).
                    invoke(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long.class)).
                    invoke(1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long.class)).
                    invoke(1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long.class, Class.class)).
                    invoke(1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class)).
                    invoke(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long.class)).
                    invoke(1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long.class)).
                    invoke(1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long.class, Class.class)).
                    invoke(1L, Void.class);
            });
        }
    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        long[] array = new long[10];
        Arrays.fill(array, 1L);

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.get(array, Void.class);
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
            long x = (long) vh.get();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, 1L, Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.getVolatile(array, Void.class);
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
            long x = (long) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, 1L, Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.getOpaque(array, Void.class);
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
            long x = (long) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, 1L, Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.getAcquire(array, Void.class);
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
            long x = (long) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, 1L, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.compareAndSet(array, Void.class, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(array, 0, 1L, 1L, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSet(array, Void.class, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(array, 0, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetVolatile(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetVolatile(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetVolatile(array, Void.class, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile(array, 0, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetAcquire(array, Void.class, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(array, 0, 1L, 1L, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetRelease(array, Void.class, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(array, 0, 1L, 1L, Void.class);
        });


        // CompareAndExchangeVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeVolatile(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.compareAndExchangeVolatile(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeVolatile(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeVolatile(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.compareAndExchangeVolatile(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.compareAndExchangeVolatile(array, Void.class, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeVolatile(array, 0, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeVolatile(array, 0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeVolatile();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeVolatile(array, 0, 1L, 1L, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeAcquire(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.compareAndExchangeAcquire(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeAcquire(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeAcquire(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.compareAndExchangeAcquire(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.compareAndExchangeAcquire(array, Void.class, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(array, 0, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(array, 0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeAcquire(array, 0, 1L, 1L, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            long x = (long) vh.compareAndExchangeRelease(null, 0, 1L, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.compareAndExchangeRelease(Void.class, 0, 1L, 1L);
        });
        checkWMTE(() -> { // expected reference class
            long x = (long) vh.compareAndExchangeRelease(array, 0, Void.class, 1L);
        });
        checkWMTE(() -> { // actual reference class
            long x = (long) vh.compareAndExchangeRelease(array, 0, 1L, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.compareAndExchangeRelease(0, 0, 1L, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.compareAndExchangeRelease(array, Void.class, 1L, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(array, 0, 1L, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(array, 0, 1L, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.compareAndExchangeRelease(array, 0, 1L, 1L, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.getAndSet(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.getAndSet(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndSet(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            long x = (long) vh.getAndSet(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.getAndSet(array, Void.class, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(array, 0, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(array, 0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndSet(array, 0, 1L, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.getAndAdd(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.getAndAdd(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.getAndAdd(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.getAndAdd(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.getAndAdd(array, Void.class, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(array, 0, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(array, 0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.getAndAdd(array, 0, 1L, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null array
            long x = (long) vh.addAndGet(null, 0, 1L);
        });
        checkCCE(() -> { // array reference class
            long x = (long) vh.addAndGet(Void.class, 0, 1L);
        });
        checkWMTE(() -> { // value reference class
            long x = (long) vh.addAndGet(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            long x = (long) vh.addAndGet(0, 0, 1L);
        });
        checkWMTE(() -> { // index reference class
            long x = (long) vh.addAndGet(array, Void.class, 1L);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(array, 0, 1L);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(array, 0, 1L);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            long x = (long) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            long x = (long) vh.addAndGet(array, 0, 1L, Void.class);
        });
    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        long[] array = new long[10];
        Arrays.fill(array, 1L);

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                long x = (long) hs.get(am, methodType(long.class, Void.class, int.class)).
                    invoke(null, 0);
            });
            checkCCE(() -> { // array reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, int.class)).
                    invoke(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, int.class)).
                    invoke(0, 0);
            });
            checkWMTE(() -> { // index reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, Class.class)).
                    invoke(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, long[].class, int.class)).
                    invoke(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class)).
                    invoke(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Void.class, int.class, long.class)).
                    invoke(null, 0, 1L);
            });
            checkCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, long.class)).
                    invoke(Void.class, 0, 1L);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, long[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, long.class)).
                    invoke(0, 0, 1L);
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, long[].class, Class.class, long.class)).
                    invoke(array, Void.class, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, long[].class, int.class, Class.class)).
                    invoke(array, 0, 1L, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Void.class, int.class, long.class, long.class)).
                    invoke(null, 0, 1L, 1L);
            });
            checkCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, long.class, long.class)).
                    invoke(Void.class, 0, 1L, 1L);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, Class.class, long.class)).
                    invoke(array, 0, Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, long.class, Class.class)).
                    invoke(array, 0, 1L, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, long.class, long.class)).
                    invoke(0, 0, 1L, 1L);
            });
            checkWMTE(() -> { // index reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long[].class, Class.class, long.class, long.class)).
                    invoke(array, Void.class, 1L, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, long.class, long.class, Class.class)).
                    invoke(array, 0, 1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                long x = (long) hs.get(am, methodType(long.class, Void.class, int.class, long.class, long.class)).
                    invoke(null, 0, 1L, 1L);
            });
            checkCCE(() -> { // array reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, int.class, long.class, long.class)).
                    invoke(Void.class, 0, 1L, 1L);
            });
            checkWMTE(() -> { // expected reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, Class.class, long.class)).
                    invoke(array, 0, Void.class, 1L);
            });
            checkWMTE(() -> { // actual reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, long.class, Class.class)).
                    invoke(array, 0, 1L, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, int.class, long.class, long.class)).
                    invoke(0, 0, 1L, 1L);
            });
            checkWMTE(() -> { // index reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, Class.class, long.class, long.class)).
                    invoke(array, Void.class, 1L, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long[].class, int.class, long.class, long.class)).
                    invoke(array, 0, 1L, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, long.class, long.class)).
                    invoke(array, 0, 1L, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, long.class, long.class, Class.class)).
                    invoke(array, 0, 1L, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                long x = (long) hs.get(am, methodType(long.class, Void.class, int.class, long.class)).
                    invoke(null, 0, 1L);
            });
            checkCCE(() -> { // array reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, int.class, long.class)).
                    invoke(Void.class, 0, 1L);
            });
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, int.class, long.class)).
                    invoke(0, 0, 1L);
            });
            checkWMTE(() -> { // index reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, Class.class, long.class)).
                    invoke(array, Void.class, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long[].class, int.class, long.class)).
                    invoke(array, 0, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, long.class)).
                    invoke(array, 0, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, long.class, Class.class)).
                    invoke(array, 0, 1L, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                long x = (long) hs.get(am, methodType(long.class, Void.class, int.class, long.class)).
                    invoke(null, 0, 1L);
            });
            checkCCE(() -> { // array reference class
                long x = (long) hs.get(am, methodType(long.class, Class.class, int.class, long.class)).
                    invoke(Void.class, 0, 1L);
            });
            checkWMTE(() -> { // value reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, Class.class)).
                    invoke(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                long x = (long) hs.get(am, methodType(long.class, int.class, int.class, long.class)).
                    invoke(0, 0, 1L);
            });
            checkWMTE(() -> { // index reference class
                long x = (long) hs.get(am, methodType(long.class, long[].class, Class.class, long.class)).
                    invoke(array, Void.class, 1L);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, long[].class, int.class, long.class)).
                    invoke(array, 0, 1L);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, long[].class, int.class, long.class)).
                    invoke(array, 0, 1L);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                long x = (long) hs.get(am, methodType(long.class)).
                    invoke();
            });
            checkWMTE(() -> { // >
                long x = (long) hs.get(am, methodType(long.class, long[].class, int.class, long.class, Class.class)).
                    invoke(array, 0, 1L, Void.class);
            });
        }
    }
}

