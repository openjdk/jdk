/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 *          java.base/jdk.internal.value
 * @run junit/othervm VarHandleTestMethodTypeNullRestrictedValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=true -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true VarHandleTestMethodTypeNullRestrictedValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=false VarHandleTestMethodTypeNullRestrictedValue
 * @run junit/othervm -Djava.lang.invoke.VarHandle.VAR_HANDLE_GUARDS=false -Djava.lang.invoke.VarHandle.VAR_HANDLE_IDENTITY_ADAPT=true VarHandleTestMethodTypeNullRestrictedValue
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.invoke.MethodType.*;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestMethodTypeNullRestrictedValue extends VarHandleBaseTest {
    static final @NullRestricted NullRestrictedValue static_final_v = NullRestrictedValue.of((byte)20,(short)1854);

    static @NullRestricted NullRestrictedValue static_v = NullRestrictedValue.of((byte)20,(short)1854);

    final @NullRestricted NullRestrictedValue final_v;

    @NullRestricted NullRestrictedValue v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    public VarHandleTestMethodTypeNullRestrictedValue() {
        final_v = NullRestrictedValue.of((byte)20,(short)1854);
        v = NullRestrictedValue.of((byte)20,(short)1854);
        super();
    }

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeNullRestrictedValue.class, "final_v", NullRestrictedValue.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodTypeNullRestrictedValue.class, "v", NullRestrictedValue.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeNullRestrictedValue.class, "static_final_v", NullRestrictedValue.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodTypeNullRestrictedValue.class, "static_v", NullRestrictedValue.class);

        vhArray = MethodHandles.arrayElementVarHandle(NullRestrictedValue[].class);
    }

    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceFieldWrongMethodType(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestMethodTypeNullRestrictedValue::testStaticFieldWrongMethodType,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestMethodTypeNullRestrictedValue::testArrayWrongMethodType,
                                              false));

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceFieldWrongMethodType(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodTypeNullRestrictedValue::testStaticFieldWrongMethodType,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodTypeNullRestrictedValue::testArrayWrongMethodType,
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

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeNullRestrictedValue recv, VarHandle vh) throws Throwable {
        // Get
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.get(null);
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.get(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.get(0);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.set(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            vh.set(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.set(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(null);
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(0);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(recv, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setVolatile(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            vh.setVolatile(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setVolatile(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(null);
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(0);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(recv, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setOpaque(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            vh.setOpaque(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setOpaque(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(null);
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(0);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(recv, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            vh.setRelease(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            vh.setRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetPlain(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetPlain(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });

        // GetAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });

        // GetAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(null, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(recv, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


    }

    static void testInstanceFieldWrongMethodType(VarHandleTestMethodTypeNullRestrictedValue recv, Handles hs) throws Throwable {
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class)).
                    invokeExact((VarHandleTestMethodTypeNullRestrictedValue) null);
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class)).
                    invokeExact(Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class)).
                    invokeExact(0);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeNullRestrictedValue.class)).
                    invokeExact(recv);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class)).
                    invokeExact(recv);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((VarHandleTestMethodTypeNullRestrictedValue) null, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                hs.get(am, methodType(void.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeNullRestrictedValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, NullRestrictedValue.class)).
                    invokeExact(0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((VarHandleTestMethodTypeNullRestrictedValue) null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class , NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(() -> { // null receiver
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((VarHandleTestMethodTypeNullRestrictedValue) null, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(recv, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class , NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeNullRestrictedValue.class , NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class , NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(() -> { // null receiver
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((VarHandleTestMethodTypeNullRestrictedValue) null, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, Class.class)).
                    invokeExact(recv, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class, NullRestrictedValue.class)).
                    invokeExact(0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, VarHandleTestMethodTypeNullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.get(Void.class);
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
            vh.set(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(Void.class);
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
            vh.setVolatile(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(Void.class);
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
            vh.setOpaque(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(Void.class);
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
            vh.setRelease(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSetAcquire
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSetRelease
        // Incorrect argument types
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(Void.class);
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
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
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(Class.class)).
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
                hs.get(am, methodType(void.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // expected reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            hs.checkWMTEOrCCE(() -> { // value reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class)).
                    invokeExact(Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, NullRestrictedValue.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }


    }


    static void testArrayWrongMethodType(VarHandle vh) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));
        Arrays.fill(array, NullRestrictedValue.of((byte)20,(short)1854));

        // Get
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.get(null, 0);
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.get(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.get(0, 0);
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.get(array, Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.get(array, 0, Void.class);
        });


        // Set
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.set(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            vh.set(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.set(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.set(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            vh.set(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.set();
        });
        checkWMTE(() -> { // >
            vh.set(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(null, 0);
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(0, 0);
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(array, Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(array, 0, Void.class);
        });


        // SetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setVolatile(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            vh.setVolatile(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setVolatile(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setVolatile(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            vh.setVolatile(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setVolatile();
        });
        checkWMTE(() -> { // >
            vh.setVolatile(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(null, 0);
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(0, 0);
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(array, Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(array, 0, Void.class);
        });


        // SetOpaque
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setOpaque(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            vh.setOpaque(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setOpaque(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setOpaque(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            vh.setOpaque(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setOpaque();
        });
        checkWMTE(() -> { // >
            vh.setOpaque(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(null, 0);
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(Void.class, 0);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(0, 0);
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(array, Void.class);
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
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(array, 0, Void.class);
        });


        // SetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            vh.setRelease(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            vh.setRelease(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            vh.setRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            vh.setRelease(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            vh.setRelease(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            vh.setRelease();
        });
        checkWMTE(() -> { // >
            vh.setRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.compareAndSet(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.compareAndSet(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.compareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.compareAndSet(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.compareAndSet(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.compareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.compareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSet
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetPlain(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetPlain(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetPlain(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetPlain(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetPlain(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetPlain(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetPlain();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetPlain(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetVolatile
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSet(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSet(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSet(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSet(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSet();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetAcquire(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetAcquire(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetAcquire(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetAcquire();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // WeakCompareAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            boolean r = vh.weakCompareAndSetRelease(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // receiver primitive class
            boolean r = vh.weakCompareAndSetRelease(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            boolean r = vh.weakCompareAndSetRelease(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            boolean r = vh.weakCompareAndSetRelease();
        });
        checkWMTE(() -> { // >
            boolean r = vh.weakCompareAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchange
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeAcquire
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // CompareAndExchangeRelease
        // Incorrect argument types
        checkNPE(() -> { // null receiver
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // expected reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // actual reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSet
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSetAcquire
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


        // GetAndSetRelease
        // Incorrect argument types
        checkNPE(() -> { // null array
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(null, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // array reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkCCE(() -> { // value reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(array, 0, Void.class);
        });
        checkWMTE(() -> { // array primitive class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // index reference class
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect return type
        checkCCE(() -> { // reference class
            Void r = (Void) vh.getAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        checkWMTE(() -> { // primitive class
            boolean x = (boolean) vh.getAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
        });
        // Incorrect arity
        checkWMTE(() -> { // 0
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease();
        });
        checkWMTE(() -> { // >
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
        });


    }

    static void testArrayWrongMethodType(Handles hs) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));
        Arrays.fill(array, NullRestrictedValue.of((byte)20,(short)1854));

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class)).
                    invokeExact((NullRestrictedValue[]) null, 0);
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, int.class)).
                    invokeExact(Void.class, 0);
            });
            checkWMTE(() -> { // array primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class, int.class)).
                    invokeExact(0, 0);
            });
            checkWMTE(() -> { // index reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, Class.class)).
                    invokeExact(array, Void.class);
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void x = (Void) hs.get(am, methodType(Void.class, NullRestrictedValue[].class, int.class)).
                    invokeExact(array, 0);
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class)).
                    invokeExact(array, 0);
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                hs.get(am, methodType(void.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class)).
                    invokeExact((NullRestrictedValue[]) null, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                hs.get(am, methodType(void.class, Class.class, int.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                hs.get(am, methodType(void.class, NullRestrictedValue[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                hs.get(am, methodType(void.class, int.class, int.class, NullRestrictedValue.class)).
                    invokeExact(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // index reference class
                hs.get(am, methodType(void.class, NullRestrictedValue[].class, Class.class, NullRestrictedValue.class)).
                    invokeExact(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                hs.get(am, methodType(void.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                hs.get(am, methodType(void.class, NullRestrictedValue[].class, int.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((NullRestrictedValue[]) null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // receiver reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, Class.class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            checkWMTE(() -> { // receiver primitive class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, int.class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // index reference class
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, Class.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                boolean r = (boolean) hs.get(am, methodType(boolean.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                boolean r = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            // Incorrect argument types
            checkNPE(() -> { // null receiver
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact((NullRestrictedValue[]) null, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // expected reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, Class.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // actual reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
            checkWMTE(() -> { // array primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(0, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // index reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, Class.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            // Incorrect argument types
            checkNPE(() -> { // null array
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class)).
                    invokeExact((NullRestrictedValue[]) null, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // array reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, Class.class, int.class, NullRestrictedValue.class)).
                    invokeExact(Void.class, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            hs.checkWMTEOrCCE(() -> { // value reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, Class.class)).
                    invokeExact(array, 0, Void.class);
            });
            checkWMTE(() -> { // array primitive class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, int.class, int.class, NullRestrictedValue.class)).
                    invokeExact(0, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // index reference class
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, Class.class, NullRestrictedValue.class)).
                    invokeExact(array, Void.class, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect return type
            hs.checkWMTEOrCCE(() -> { // reference class
                Void r = (Void) hs.get(am, methodType(Void.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            checkWMTE(() -> { // primitive class
                boolean x = (boolean) hs.get(am, methodType(boolean.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854));
            });
            // Incorrect arity
            checkWMTE(() -> { // 0
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class)).
                    invokeExact();
            });
            checkWMTE(() -> { // >
                NullRestrictedValue x = (NullRestrictedValue) hs.get(am, methodType(NullRestrictedValue.class, NullRestrictedValue[].class, int.class, NullRestrictedValue.class, Class.class)).
                    invokeExact(array, 0, NullRestrictedValue.of((byte)20,(short)1854), Void.class);
            });
        }


    }
}
