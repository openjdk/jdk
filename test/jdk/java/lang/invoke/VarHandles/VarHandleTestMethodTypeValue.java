/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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

// -- This file was mechanically generated: Do not edit! -- //

/*
 * @test
 * @bug 8156486
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run junit/othervm VarHandleTestMethodTypeValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true VarHandleTestMethodTypeValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false VarHandleTestMethodTypeValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true VarHandleTestMethodTypeValue
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.invoke.MethodType.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestMethodTypeValue extends VarHandleBaseTest {
    static final Value static_final_v = Value.getInstance(10);

    static Value static_v = Value.getInstance(10);

    final Value final_v = Value.getInstance(10);

    Value v = Value.getInstance(10);

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeValue.class, "final_v", Value.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeValue.class, "v", Value.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeValue.class, "static_final_v", Value.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeValue.class, "static_v", Value.class);

        vhArray = MethodHandles.arrayElementVarHandle(Value[].class);
    }

    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestMethodTypeValue::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestMethodTypeValue::testArrayWrongMethodType,
                                              false));

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodTypeValue::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodTypeValue::testArrayWrongMethodType,
                                                     false));
        }
        // Work around issue with jtreg summary reporting which truncates
        // the String result of Object.toString to 30 characters, hence
        // the first dummy argument
        return cases.stream().map(tc -> new Object[]{tc.toString(), tc}).toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("accessTestCaseProvider")
    public <T> void testAccess(String desc, AccessTestCase<T> atc) throws Throwable {
        T t = atc.get();
        int iters = atc.requiresLoop() ? ITERS : 1;
        for (int c = 0; c < iters; c++) {
            atc.testAccess(t);
        }
    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeValue recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            Value x = (Value) vh.get(0);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.get(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.get();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, Value.getInstance(10), Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            Value x = (Value) vh.getVolatile(0);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getVolatile(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, Value.getInstance(10), Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            Value x = (Value) vh.getOpaque(0);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getOpaque(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, Value.getInstance(10), Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            Value x = (Value) vh.getAcquire(0);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getAcquire(recv);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire(recv);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, Value.getInstance(10), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetPlain(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetPlain(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchange(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchange(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchange(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchange(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.compareAndExchange(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(recv, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(recv, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchange(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchangeAcquire(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchangeAcquire(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeAcquire(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.compareAndExchangeAcquire(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchangeRelease(null, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchangeRelease(Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeRelease(recv, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeRelease(recv, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.compareAndExchangeRelease(0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(recv, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(recv, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeRelease(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getAndSet(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSet(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSet(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.getAndSet(0, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(recv, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(recv, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSet(recv, Value.getInstance(10), Void.class);
        });

        // GetAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getAndSetAcquire(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSetAcquire(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetAcquire(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.getAndSetAcquire(0, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(recv, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(recv, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetAcquire(recv, Value.getInstance(10), Void.class);
        });

        // GetAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.getAndSetRelease(null, Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSetRelease(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetRelease(recv, Void.class);
        });
        checkWMTE(() -> { // reciever primitive class
            Value x = (Value) vh.getAndSetRelease(0, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(recv, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(recv, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetRelease(recv, Value.getInstance(10), Void.class);
        });


    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeValue recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class)).
                    invokeExact((VarHandleTestMethodTypeValue) null);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class)).
                    invokeExact(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class)).
                    invokeExact(0);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeValue.class)).
                    invokeExact(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class)).
                    invokeExact(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeValue.class, Value.class)).
                    invokeExact((VarHandleTestMethodTypeValue) null, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, Value.class)).
                    invokeExact(0, Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeValue.class, Value.class, Class.class)).
                    invokeExact(recv, Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class, Value.class, Value.class)).
                    invokeExact((VarHandleTestMethodTypeValue) null, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, Value.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class, Class.class, Value.class)).
                    invokeExact(recv, Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class, Value.class, Class.class)).
                    invokeExact(recv, Value.getInstance(10), Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class , Value.class, Value.class)).
                    invokeExact(0, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class, Value.class, Value.class, Class.class)).
                    invokeExact(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(() -> { // null receiver
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Value.class, Value.class)).
                    invokeExact((VarHandleTestMethodTypeValue) null, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, Value.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Class.class, Value.class)).
                    invokeExact(recv, Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Value.class, Class.class)).
                    invokeExact(recv, Value.getInstance(10), Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class , Value.class, Value.class)).
                    invokeExact(0, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeValue.class , Value.class, Value.class)).
                    invokeExact(recv, Value.getInstance(10), Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class , Value.class, Value.class)).
                    invokeExact(recv, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Value.class, Value.class, Class.class)).
                    invokeExact(recv, Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(() -> { // null receiver
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Value.class)).
                    invokeExact((VarHandleTestMethodTypeValue) null, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // reciever primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class, Value.class)).
                    invokeExact(0, Value.getInstance(10));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeValue.class, Value.class)).
                    invokeExact(recv, Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeValue.class, Value.class)).
                    invokeExact(recv, Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, VarHandleTestMethodTypeValue.class, Value.class)).
                    invokeExact(recv, Value.getInstance(10), Void.class);
            });
        }


    }


    static void testStaticFieldWrongMethodType(VarHandle vh) throws Throwable {
        // Get
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.get();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get();
        });
        // Incorrect arity
        checkWMTE(() -> { // >
            Value x = (Value) vh.get(Void.class);
        });


        // Set
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            vh.set(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(Value.getInstance(10), Void.class);
        });


        // GetVolatile
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getVolatile();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getVolatile(Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            vh.setVolatile(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(Value.getInstance(10), Void.class);
        });


        // GetOpaque
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getOpaque();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getOpaque(Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            vh.setOpaque(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(Value.getInstance(10), Void.class);
        });


        // GetAcquire
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getAcquire();
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAcquire(Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            vh.setRelease(Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(Value.getInstance(10), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(Value.getInstance(10), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(Value.getInstance(10), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(Value.getInstance(10), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(Value.getInstance(10), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(Value.getInstance(10), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchange(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchange(Value.getInstance(10), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchange(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeAcquire(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeAcquire(Value.getInstance(10), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeAcquire(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeRelease(Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeRelease(Value.getInstance(10), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeRelease(Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSet(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSet(Value.getInstance(10), Void.class);
        });


        // GetAndSetAcquire
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetAcquire(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetAcquire(Value.getInstance(10), Void.class);
        });


        // GetAndSetRelease
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetRelease(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetRelease(Value.getInstance(10), Void.class);
        });


    }

    static void testStaticFieldWrongMethodType(Handles hs) throws Throwable {
        int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            // Incorrect arity
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Class.class)).
                    invokeExact(Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            hs.checkWMTEOrCCE(() -> { // value reference class
                hs.get(am, methodType(void.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // expected reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, Value.class)).
                    invokeExact(Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, Value.class, Value.class)).
                    invokeExact(Value.getInstance(10), Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, Value.class, Value.class)).
                    invokeExact(Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, Value.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // value reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, Value.class)).
                    invokeExact(Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, Value.class)).
                    invokeExact(Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, Value.class, Class.class)).
                    invokeExact(Value.getInstance(10), Void.class);
            });
        }


    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        Value[] array = new Value[10];
        Arrays.fill(array, Value.getInstance(10));

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.get(array, Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.get(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.get(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.get();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, Value.getInstance(10), Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getVolatile(array, Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getVolatile(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getVolatile(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, Value.getInstance(10), Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getOpaque(array, Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getOpaque(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getOpaque(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, Value.getInstance(10), Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getAcquire(array, Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void x = (Void) vh.getAcquire(array, 0);
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAcquire(array, 0);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, Value.getInstance(10), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.compareAndSet(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetPlain(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetPlain(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetPlain(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSet(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetAcquire(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetRelease(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchange(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.compareAndExchange(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchange(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchange(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.compareAndExchange(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.compareAndExchange(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchange(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchangeAcquire(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.compareAndExchangeAcquire(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeAcquire(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeAcquire(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.compareAndExchangeAcquire(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.compareAndExchangeAcquire(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeAcquire(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            Value x = (Value) vh.compareAndExchangeRelease(null, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.compareAndExchangeRelease(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkCCE(() -> { // expected reference class
            Value x = (Value) vh.compareAndExchangeRelease(array, 0, Void.class, Value.getInstance(10));
        });
        checkCCE(() -> { // actual reference class
            Value x = (Value) vh.compareAndExchangeRelease(array, 0, Value.getInstance(10), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            Value x = (Value) vh.compareAndExchangeRelease(0, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.compareAndExchangeRelease(array, Void.class, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(array, 0, Value.getInstance(10), Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.compareAndExchangeRelease(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getAndSet(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getAndSet(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSet(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            Value x = (Value) vh.getAndSet(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getAndSet(array, Void.class, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(array, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(array, 0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSet(array, 0, Value.getInstance(10), Void.class);
        });


        // GetAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getAndSetAcquire(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getAndSetAcquire(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetAcquire(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            Value x = (Value) vh.getAndSetAcquire(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getAndSetAcquire(array, Void.class, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(array, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(array, 0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetAcquire(array, 0, Value.getInstance(10), Void.class);
        });


        // GetAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            Value x = (Value) vh.getAndSetRelease(null, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // array reference class
            Value x = (Value) vh.getAndSetRelease(Void.class, 0, Value.getInstance(10));
        });
        checkCCE(() -> { // value reference class
            Value x = (Value) vh.getAndSetRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // reciarrayever primitive class
            Value x = (Value) vh.getAndSetRelease(0, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // index reference class
            Value x = (Value) vh.getAndSetRelease(array, Void.class, Value.getInstance(10));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(array, 0, Value.getInstance(10));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(array, 0, Value.getInstance(10));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            Value x = (Value) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            Value x = (Value) vh.getAndSetRelease(array, 0, Value.getInstance(10), Void.class);
        });


    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        Value[] array = new Value[10];
        Arrays.fill(array, Value.getInstance(10));

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class)).
                    invokeExact((Value[]) null, 0);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, int.class)).
                    invokeExact(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class, int.class)).
                    invokeExact(0, 0);
            });
            checkWMTE(() -> { // index reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, Class.class)).
                    invokeExact(array, Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, Value[].class, int.class)).
                    invokeExact(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class)).
                    invokeExact(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, Value[].class, int.class, Value.class)).
                    invokeExact((Value[]) null, 0, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, Value.class)).
                    invokeExact(Void.class, 0, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                hs.get(am, methodType(void.class, Value[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, Value.class)).
                    invokeExact(0, 0, Value.getInstance(10));
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, Value[].class, Class.class, Value.class)).
                    invokeExact(array, Void.class, Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, Value[].class, int.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Value.class, Value.class)).
                    invokeExact((Value[]) null, 0, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, Value.class, Value.class)).
                    invokeExact(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Class.class, Value.class)).
                    invokeExact(array, 0, Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Value.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, Value.class, Value.class)).
                    invokeExact(0, 0, Value.getInstance(10), Value.getInstance(10));
            });
            checkWMTE(() -> { // index reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value[].class, Class.class, Value.class, Value.class)).
                    invokeExact(array, Void.class, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Value.class, Value.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Value.class, Value.class)).
                    invokeExact((Value[]) null, 0, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, int.class, Value.class, Value.class)).
                    invokeExact(Void.class, 0, Value.getInstance(10), Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Class.class, Value.class)).
                    invokeExact(array, 0, Void.class, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Value.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Void.class);
            });
            checkWMTE(() -> { // array primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class, int.class, Value.class, Value.class)).
                    invokeExact(0, 0, Value.getInstance(10), Value.getInstance(10));
            });
            checkWMTE(() -> { // index reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, Class.class, Value.class, Value.class)).
                    invokeExact(array, Void.class, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, Value[].class, int.class, Value.class, Value.class)).
                    invokeExact(array, 0, Value.getInstance(10), Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Value.class, Value.class)).
                    invokeExact(array, 0, Value.getInstance(10), Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Value.class, Value.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Value.getInstance(10), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Value.class)).
                    invokeExact((Value[]) null, 0, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Class.class, int.class, Value.class)).
                    invokeExact(Void.class, 0, Value.getInstance(10));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                Value x = (Value) hs.get(am, methodType(Value.class, int.class, int.class, Value.class)).
                    invokeExact(0, 0, Value.getInstance(10));
            });
            checkWMTE(() -> { // index reference class
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, Class.class, Value.class)).
                    invokeExact(array, Void.class, Value.getInstance(10));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, Value[].class, int.class, Value.class)).
                    invokeExact(array, 0, Value.getInstance(10));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, Value[].class, int.class, Value.class)).
                    invokeExact(array, 0, Value.getInstance(10));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                Value x = (Value) hs.get(am, methodType(Value.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                Value x = (Value) hs.get(am, methodType(Value.class, Value[].class, int.class, Value.class, Class.class)).
                    invokeExact(array, 0, Value.getInstance(10), Void.class);
            });
        }


    }
}
