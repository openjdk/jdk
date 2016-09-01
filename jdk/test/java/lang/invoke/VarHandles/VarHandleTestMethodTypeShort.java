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
 * @bug 8156486
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
    static final short static_final_v = (short)0x0123;

    static short static_v = (short)0x0123;

    final short final_v = (short)0x0123;

    short v = (short)0x0123;

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

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestMethodTypeShort::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestMethodTypeShort::testArrayWrongMethodType,
                                              false));

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodTypeShort::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
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
            vh.set(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, (short)0x0123, Void.class);
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
            vh.setVolatile(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, (short)0x0123, Void.class);
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
            vh.setOpaque(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, (short)0x0123, Void.class);
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
            vh.setRelease(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, (short)0x0123, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetVolatile(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetVolatile(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchange(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.compareAndExchange(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchange(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchange(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.compareAndExchange(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(recv, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(recv, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchange(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchangeAcquire(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.compareAndExchangeAcquire(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeAcquire(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeAcquire(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.compareAndExchangeAcquire(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(recv, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(recv, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeAcquire(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchangeRelease(null, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.compareAndExchangeRelease(Void.class, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeRelease(recv, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeRelease(recv, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.compareAndExchangeRelease(0, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(recv, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(recv, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeRelease(recv, (short)0x0123, (short)0x0123, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.getAndSet(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.getAndSet(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndSet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.getAndSet(0, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(recv, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(recv, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndSet(recv, (short)0x0123, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.getAndAdd(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.getAndAdd(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndAdd(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.getAndAdd(0, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(recv, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(recv, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndAdd(recv, (short)0x0123, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.addAndGet(null, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            short x = (short) vh.addAndGet(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.addAndGet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            short x = (short) vh.addAndGet(0, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(recv, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(recv, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.addAndGet(recv, (short)0x0123, Void.class);
        });
    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeShort recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class)).
                    invokeExact(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class)).
                    invokeExact(0);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeShort.class)).
                    invokeExact(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class)).
                    invokeExact(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, short.class)).
                    invokeExact(Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, short.class)).
                    invokeExact(0, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeShort.class, short.class, Class.class)).
                    invokeExact(recv, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, short.class, short.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null, (short)0x0123, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, short.class, short.class)).
                    invokeExact(Void.class, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, Class.class, short.class)).
                    invokeExact(recv, Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, short.class, Class.class)).
                    invokeExact(recv, (short)0x0123, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class , short.class, short.class)).
                    invokeExact(0, (short)0x0123, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, short.class, short.class, Class.class)).
                    invokeExact(recv, (short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class, short.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null, (short)0x0123, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, short.class, short.class)).
                    invokeExact(Void.class, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // expected reference class
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, Class.class, short.class)).
                    invokeExact(recv, Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class, Class.class)).
                    invokeExact(recv, (short)0x0123, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class , short.class, short.class)).
                    invokeExact(0, (short)0x0123, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeShort.class , short.class, short.class)).
                    invokeExact(recv, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class , short.class, short.class)).
                    invokeExact(recv, (short)0x0123, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class, short.class, Class.class)).
                    invokeExact(recv, (short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, short.class)).
                    invokeExact(Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, short.class)).
                    invokeExact(0, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact((VarHandleTestMethodTypeShort) null, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, short.class)).
                    invokeExact(Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, short.class)).
                    invokeExact(0, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, VarHandleTestMethodTypeShort.class, short.class)).
                    invokeExact(recv, (short)0x0123, Void.class);
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
            vh.set((short)0x0123, Void.class);
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
            vh.setVolatile((short)0x0123, Void.class);
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
            vh.setOpaque((short)0x0123, Void.class);
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
            vh.setRelease((short)0x0123, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet((short)0x0123, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet((short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet((short)0x0123, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet((short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile((short)0x0123, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile((short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire((short)0x0123, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire((short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease((short)0x0123, Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease((short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchange(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchange((short)0x0123, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange((short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange((short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchange((short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeAcquire(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeAcquire((short)0x0123, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire((short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire((short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeAcquire((short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeRelease(Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeRelease((short)0x0123, Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease((short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease((short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeRelease((short)0x0123, (short)0x0123, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndSet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet((short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet((short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndSet((short)0x0123, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndAdd(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd((short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd((short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndAdd((short)0x0123, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkWMTE(() -> { // value reference class
            short x = (short) vh.addAndGet(Void.class);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet((short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet((short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.addAndGet((short)0x0123, Void.class);
        });
    }

    static void testStaticFieldWrongMethodType(Handles hs) throws Throwable {
        int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            // Incorrect arity
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(Class.class)).
                    invokeExact(Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, short.class, Class.class)).
                    invokeExact((short)0x0123, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, short.class)).
                    invokeExact(Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short.class, Class.class)).
                    invokeExact((short)0x0123, Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short.class, short.class, Class.class)).
                    invokeExact((short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkWMTE(() -> { // expected reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, short.class)).
                    invokeExact(Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                short x = (short) hs.get(am, methodType(short.class, short.class, Class.class)).
                    invokeExact((short)0x0123, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short.class, short.class)).
                    invokeExact((short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short.class, short.class)).
                    invokeExact((short)0x0123, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short.class, short.class, Class.class)).
                    invokeExact((short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short.class)).
                    invokeExact((short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short.class)).
                    invokeExact((short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short.class, Class.class)).
                    invokeExact((short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            // Incorrect argument types
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short.class)).
                    invokeExact((short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short.class)).
                    invokeExact((short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short.class, Class.class)).
                    invokeExact((short)0x0123, Void.class);
            });
        }
    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        short[] array = new short[10];
        Arrays.fill(array, (short)0x0123);

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
            vh.set(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, (short)0x0123, Void.class);
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
            vh.setVolatile(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, (short)0x0123, Void.class);
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
            vh.setOpaque(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, (short)0x0123, Void.class);
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
            vh.setRelease(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, (short)0x0123, Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.compareAndSet(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.compareAndSet(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.compareAndSet(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSet(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetVolatile(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetVolatile(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetVolatile(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetVolatile(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetVolatile(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetVolatile(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetVolatile();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetVolatile(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetAcquire(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetRelease(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchange(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.compareAndExchange(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchange(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchange(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.compareAndExchange(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.compareAndExchange(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(array, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(array, 0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchange(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchangeAcquire(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.compareAndExchangeAcquire(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeAcquire(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeAcquire(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.compareAndExchangeAcquire(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.compareAndExchangeAcquire(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(array, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(array, 0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeAcquire(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            short x = (short) vh.compareAndExchangeRelease(null, 0, (short)0x0123, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.compareAndExchangeRelease(Void.class, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // expected reference class
            short x = (short) vh.compareAndExchangeRelease(array, 0, Void.class, (short)0x0123);
        });
        checkWMTE(() -> { // actual reference class
            short x = (short) vh.compareAndExchangeRelease(array, 0, (short)0x0123, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.compareAndExchangeRelease(0, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.compareAndExchangeRelease(array, Void.class, (short)0x0123, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(array, 0, (short)0x0123, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(array, 0, (short)0x0123, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.compareAndExchangeRelease(array, 0, (short)0x0123, (short)0x0123, Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.getAndSet(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.getAndSet(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndSet(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            short x = (short) vh.getAndSet(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.getAndSet(array, Void.class, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndSet(array, 0, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(array, 0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndSet(array, 0, (short)0x0123, Void.class);
        });

        // GetAndAdd
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.getAndAdd(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.getAndAdd(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.getAndAdd(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.getAndAdd(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.getAndAdd(array, Void.class, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.getAndAdd(array, 0, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndAdd(array, 0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.getAndAdd();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.getAndAdd(array, 0, (short)0x0123, Void.class);
        });


        // AddAndGet
        // Incorrect argument types
        checkNPE(() -> { // null array
            short x = (short) vh.addAndGet(null, 0, (short)0x0123);
        });
        checkCCE(() -> { // array reference class
            short x = (short) vh.addAndGet(Void.class, 0, (short)0x0123);
        });
        checkWMTE(() -> { // value reference class
            short x = (short) vh.addAndGet(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            short x = (short) vh.addAndGet(0, 0, (short)0x0123);
        });
        checkWMTE(() -> { // index reference class
            short x = (short) vh.addAndGet(array, Void.class, (short)0x0123);
        });
        // Incorrect return type
        checkWMTE(() -> { // reference class
            Void r = (Void) vh.addAndGet(array, 0, (short)0x0123);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.addAndGet(array, 0, (short)0x0123);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            short x = (short) vh.addAndGet();
        });
        checkWMTE(() -> { // >
            short x = (short) vh.addAndGet(array, 0, (short)0x0123, Void.class);
        });
    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        short[] array = new short[10];
        Arrays.fill(array, (short)0x0123);

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class)).
                    invokeExact((short[]) null, 0);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, int.class)).
                    invokeExact(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, int.class)).
                    invokeExact(0, 0);
            });
            checkWMTE(() -> { // index reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, Class.class)).
                    invokeExact(array, Void.class);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, short[].class, int.class)).
                    invokeExact(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class)).
                    invokeExact(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, short[].class, int.class, short.class)).
                    invokeExact((short[]) null, 0, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, short.class)).
                    invokeExact(Void.class, 0, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                hs.get(am, methodType(void.class, short[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, short.class)).
                    invokeExact(0, 0, (short)0x0123);
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, short[].class, Class.class, short.class)).
                    invokeExact(array, Void.class, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, short[].class, int.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class, short.class)).
                    invokeExact((short[]) null, 0, (short)0x0123, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, short.class, short.class)).
                    invokeExact(Void.class, 0, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, Class.class, short.class)).
                    invokeExact(array, 0, Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, short.class, short.class)).
                    invokeExact(0, 0, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // index reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short[].class, Class.class, short.class, short.class)).
                    invokeExact(array, Void.class, (short)0x0123, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class, short.class)).
                    invokeExact((short[]) null, 0, (short)0x0123, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, int.class, short.class, short.class)).
                    invokeExact(Void.class, 0, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // expected reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, Class.class, short.class)).
                    invokeExact(array, 0, Void.class, (short)0x0123);
            });
            checkWMTE(() -> { // actual reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, int.class, short.class, short.class)).
                    invokeExact(0, 0, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // index reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, Class.class, short.class, short.class)).
                    invokeExact(array, Void.class, (short)0x0123, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short[].class, int.class, short.class, short.class)).
                    invokeExact(array, 0, (short)0x0123, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class, short.class)).
                    invokeExact(array, 0, (short)0x0123, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class)).
                    invokeExact((short[]) null, 0, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, int.class, short.class)).
                    invokeExact(Void.class, 0, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, int.class, short.class)).
                    invokeExact(0, 0, (short)0x0123);
            });
            checkWMTE(() -> { // index reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, Class.class, short.class)).
                    invokeExact(array, Void.class, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short[].class, int.class, short.class)).
                    invokeExact(array, 0, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class)).
                    invokeExact(array, 0, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class)).
                    invokeExact((short[]) null, 0, (short)0x0123);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                short x = (short) hs.get(am, methodType(short.class, Class.class, int.class, short.class)).
                    invokeExact(Void.class, 0, (short)0x0123);
            });
            checkWMTE(() -> { // value reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                short x = (short) hs.get(am, methodType(short.class, int.class, int.class, short.class)).
                    invokeExact(0, 0, (short)0x0123);
            });
            checkWMTE(() -> { // index reference class
                short x = (short) hs.get(am, methodType(short.class, short[].class, Class.class, short.class)).
                    invokeExact(array, Void.class, (short)0x0123);
            });
            // Incorrect return type
            checkWMTE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, short[].class, int.class, short.class)).
                    invokeExact(array, 0, (short)0x0123);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, short[].class, int.class, short.class)).
                    invokeExact(array, 0, (short)0x0123);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                short x = (short) hs.get(am, methodType(short.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                short x = (short) hs.get(am, methodType(short.class, short[].class, int.class, short.class, Class.class)).
                    invokeExact(array, 0, (short)0x0123, Void.class);
            });
        }
    }
}

