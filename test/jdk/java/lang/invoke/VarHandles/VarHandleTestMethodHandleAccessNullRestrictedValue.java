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
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop set to 2000 iterations
 *          hits compilation thresholds
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 VarHandleTestMethodHandleAccessNullRestrictedValue
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestMethodHandleAccessNullRestrictedValue extends VarHandleBaseTest {
    static final @NullRestricted NullRestrictedValue static_final_v = NullRestrictedValue.of((byte)20,(short)1854);

    static @NullRestricted NullRestrictedValue static_v = NullRestrictedValue.of((byte)20,(short)1854);

    final @NullRestricted NullRestrictedValue final_v;

    @NullRestricted NullRestrictedValue v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    public VarHandleTestMethodHandleAccessNullRestrictedValue() {
        final_v = NullRestrictedValue.of((byte)20,(short)1854);
        v = NullRestrictedValue.of((byte)20,(short)1854);
        super();
    }

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessNullRestrictedValue.class, "final_v", NullRestrictedValue.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessNullRestrictedValue.class, "v", NullRestrictedValue.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessNullRestrictedValue.class, "static_final_v", NullRestrictedValue.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessNullRestrictedValue.class, "static_v", NullRestrictedValue.class);

        vhArray = MethodHandles.arrayElementVarHandle(NullRestrictedValue[].class);
    }

    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceField(this, hs)));
            cases.add(new MethodHandleAccessTestCase("Instance field unsupported",
                                                     vhField, f, hs -> testInstanceFieldUnsupported(this, hs),
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Instance field null pointer exception",
                                                     vhField, f, hs -> testInstanceFieldNullPointerException(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testStaticFieldUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Static field null pointer exception",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testStaticFieldNullPointerException,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testArrayIndexOutOfBounds,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array null pointer exception",
                                                     vhArray, f, VarHandleTestMethodHandleAccessNullRestrictedValue::testArrayNullPointerException,
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

    static void testInstanceField(VarHandleTestMethodHandleAccessNullRestrictedValue recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "set NullRestrictedValue value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, true, "success compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
        }

        {
            boolean success = false;
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessNullRestrictedValue recv, Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854));
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "set NullRestrictedValue value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
        }

        hs.get(TestAccessMode.SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, true, "success compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = (boolean) mh.invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
        }


    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(NullRestrictedValue.of((byte)20,(short)1854));
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "get NullRestrictedValue value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(r, true, "success compareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
            }

            // Compare set and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
            }


        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        final int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                NullRestrictedValue o = (NullRestrictedValue) hs.get(am).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                NullRestrictedValue o = (NullRestrictedValue) hs.get(am).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854));
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkAIOOBE(am, () -> {
                    NullRestrictedValue x = (NullRestrictedValue) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkAIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkAIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkAIOOBE(am, () -> {
                    NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(array, ci, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkAIOOBE(am, () -> {
                    NullRestrictedValue o = (NullRestrictedValue) hs.get(am).invokeExact(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
                });
            }


        }
    }

    static void testInstanceFieldNullPointerException(VarHandleTestMethodHandleAccessNullRestrictedValue recv, Handles hs) throws Throwable {
        NullRestrictedValue value = null;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            checkNPE(am, () -> {
                hs.get(am).invokeExact(recv, value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkNPE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(recv, value);
            });
        }
    }

    static void testStaticFieldNullPointerException(Handles hs) throws Throwable {
        NullRestrictedValue value = null;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            checkNPE(am, () -> {
                hs.get(am).invokeExact(value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkNPE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(value);
            });
        }
    }

    static void testArrayNullPointerException(Handles hs) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));
        NullRestrictedValue value = null;

        final int i = 0;
        for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
            checkNPE(am, () -> {
                hs.get(am).invokeExact(array, i, value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
            checkNPE(am, () -> {
                boolean r = (boolean) hs.get(am).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(array, i, NullRestrictedValue.of((byte)20,(short)1854), value);
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
            checkNPE(am, () -> {
                NullRestrictedValue r = (NullRestrictedValue) hs.get(am).invokeExact(array, i, value);
            });
        }
    }
}

