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
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop sets to 2000 iterations
 *          to hit compilation thresholds
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 VarHandleTestMethodHandleAccessValue
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestMethodHandleAccessValue extends VarHandleBaseTest {
    static final Value static_final_v = Value.getInstance(10);

    static Value static_v;

    final Value final_v = Value.getInstance(10);

    Value v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessValue.class, "final_v", Value.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessValue.class, "v", Value.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessValue.class, "static_final_v", Value.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessValue.class, "static_v", Value.class);

        vhArray = MethodHandles.arrayElementVarHandle(Value[].class);
    }

    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        for (VarHandleToMethodHandle f : VarHandleToMethodHandle.values()) {
            cases.add(new MethodHandleAccessTestCase("Instance field",
                                                     vhField, f, hs -> testInstanceField(this, hs)));
            cases.add(new MethodHandleAccessTestCase("Instance field unsupported",
                                                     vhField, f, hs -> testInstanceFieldUnsupported(this, hs),
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Static field",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessValue::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessValue::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessValue::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessValue::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessValue::testArrayIndexOutOfBounds,
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

    static void testInstanceField(VarHandleTestMethodHandleAccessValue recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, Value.getInstance(10));
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "set Value value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, Value.getInstance(20));
            Value x = (Value) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "setVolatile Value value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, Value.getInstance(10));
            Value x = (Value) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "setRelease Value value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, Value.getInstance(20));
            Value x = (Value) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "setOpaque Value value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, Value.getInstance(10));

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, true, "success compareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, false, "failing compareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetPlain Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetRelease Value value");
        }

        {
            boolean success = false;
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSet Value value");
        }

        // Compare set and get
        {
            Value o = (Value) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(Value.getInstance(20), x, "getAndSet Value value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessValue recv, Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                Value r = (Value) hs.get(am).invokeExact(recv, Value.getInstance(10));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                Value r = (Value) hs.get(am).invokeExact(recv, Value.getInstance(10));
            });
        }
    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(Value.getInstance(10));
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "set Value value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(Value.getInstance(20));
            Value x = (Value) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(Value.getInstance(20), x, "setVolatile Value value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(Value.getInstance(10));
            Value x = (Value) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(Value.getInstance(10), x, "setRelease Value value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(Value.getInstance(20));
            Value x = (Value) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(Value.getInstance(20), x, "setOpaque Value value");
        }

        hs.get(TestAccessMode.SET).invokeExact(Value.getInstance(10));

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, true, "success compareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, false, "failing compareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
        }

        {
            Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetPlain Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = (boolean) mh.invokeExact(Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetRelease Value value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetRe Value value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(Value.getInstance(10));

            Value o = (Value) hs.get(TestAccessMode.GET_AND_SET).invokeExact(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSet Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "getAndSet Value value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(Value.getInstance(10));

            Value o = (Value) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetAcquire Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "getAndSetAcquire Value value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(Value.getInstance(10));

            Value o = (Value) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetRelease Value");
            Value x = (Value) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(Value.getInstance(20), x, "getAndSetRelease Value value");
        }


    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                Value r = (Value) hs.get(am).invokeExact(Value.getInstance(10));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                Value r = (Value) hs.get(am).invokeExact(Value.getInstance(10));
            });
        }
    }


    static void testArray(Handles hs) throws Throwable {
        Value[] array = new Value[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, Value.getInstance(10));
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "get Value value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, Value.getInstance(20));
                Value x = (Value) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "setVolatile Value value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, Value.getInstance(10));
                Value x = (Value) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "setRelease Value value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, Value.getInstance(20));
                Value x = (Value) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "setOpaque Value value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, Value.getInstance(10));

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, Value.getInstance(10), Value.getInstance(20));
                assertEquals(r, true, "success compareAndSet Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(r, false, "failing compareAndSet Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, Value.getInstance(20), Value.getInstance(10));
                assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, Value.getInstance(10), Value.getInstance(20));
                assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, Value.getInstance(20), Value.getInstance(10));
                assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
            }

            {
                Value r = (Value) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, Value.getInstance(10), Value.getInstance(20));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetPlain Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, Value.getInstance(20), Value.getInstance(10));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, Value.getInstance(10), Value.getInstance(20));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetAcquire Value value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, Value.getInstance(20), Value.getInstance(10));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSet Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(10), x, "failing weakCompareAndSet Value value");
            }

            // Compare set and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, Value.getInstance(10));

                Value o = (Value) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSet Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSet Value value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, Value.getInstance(10));

                Value o = (Value) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSetAcquire Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSetAcquire Value value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, Value.getInstance(10));

                Value o = (Value) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSetRelease Value");
                Value x = (Value) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSetRelease Value value");
            }


        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        Value[] array = new Value[10];

        final int i = 0;

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
            checkUOE(am, () -> {
                Value o = (Value) hs.get(am).invokeExact(array, i, Value.getInstance(10));
            });
        }

        for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
            checkUOE(am, () -> {
                Value o = (Value) hs.get(am).invokeExact(array, i, Value.getInstance(10));
            });
        }
    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        Value[] array = new Value[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkAIOOBE(am, () -> {
                    Value x = (Value) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkAIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, Value.getInstance(10));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkAIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, Value.getInstance(10), Value.getInstance(20));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkAIOOBE(am, () -> {
                    Value r = (Value) hs.get(am).invokeExact(array, ci, Value.getInstance(20), Value.getInstance(10));
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkAIOOBE(am, () -> {
                    Value o = (Value) hs.get(am).invokeExact(array, ci, Value.getInstance(10));
                });
            }


        }
    }
}

