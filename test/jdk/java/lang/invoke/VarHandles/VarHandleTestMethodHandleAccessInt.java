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

/*
 * @test
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop sets to 2000 iterations
 *          to hit compilation thresholds
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 VarHandleTestMethodHandleAccessInt
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
public class VarHandleTestMethodHandleAccessInt extends VarHandleBaseTest {
    static final int static_final_v = 0x01234567;

    static int static_v;

    final int final_v = 0x01234567;

    int v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessInt.class, "final_v", int.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessInt.class, "v", int.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessInt.class, "static_final_v", int.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessInt.class, "static_v", int.class);

        vhArray = MethodHandles.arrayElementVarHandle(int[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessInt::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessInt::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessInt::testArrayIndexOutOfBounds,
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

    static void testInstanceField(VarHandleTestMethodHandleAccessInt recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "set int value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, 0x89ABCDEF);
            int x = (int) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "setVolatile int value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, 0x01234567);
            int x = (int) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(0x01234567, x, "setRelease int value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, 0x89ABCDEF);
            int x = (int) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "setOpaque int value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x01234567, 0x89ABCDEF);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "success compareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x01234567, 0xCAFEBABE);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "failing compareAndSet int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchange int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "success compareAndExchange int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchange int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "failing compareAndExchange int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x01234567, 0x89ABCDEF);
            assertEquals(r, 0x01234567, "success compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x01234567, 0xCAFEBABE);
            assertEquals(r, 0x89ABCDEF, "failing compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "failing compareAndExchangeRelease int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "success weakCompareAndSetPlain int value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(recv, 0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetPlain int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetPlain int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "success weakCompareAndSetAcquire int");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "failing weakCompareAndSetAcquire int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "success weakCompareAndSetRelease int");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, 0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetRelease int value");
        }

        {
            boolean success = false;
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "success weakCompareAndSet int");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, 0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x01234567, x, "failing weakCompareAndSet int value");
        }

        // Compare set and get
        {
            int o = (int) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x89ABCDEF, x, "getAndSet int value");
        }

        // get and add, add and get
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAdd int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAdd int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAddAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAddRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddRelease int value");
        }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOr int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOr int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrRelease int value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAnd int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAnd int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndRelease int value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXor int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXor int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(recv, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorRelease int value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessInt recv, Handles hs) throws Throwable {


    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "set int value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(0x89ABCDEF);
            int x = (int) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(0x89ABCDEF, x, "setVolatile int value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(0x01234567);
            int x = (int) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(0x01234567, x, "setRelease int value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(0x89ABCDEF);
            int x = (int) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(0x89ABCDEF, x, "setOpaque int value");
        }

        hs.get(TestAccessMode.SET).invokeExact(0x01234567);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x01234567, 0x89ABCDEF);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "success compareAndSet int value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x01234567, 0xCAFEBABE);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "failing compareAndSet int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchange int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "success compareAndExchange int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchange int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "failing compareAndExchange int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x01234567, 0x89ABCDEF);
            assertEquals(r, 0x01234567, "success compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x01234567, 0xCAFEBABE);
            assertEquals(r, 0x89ABCDEF, "failing compareAndExchangeAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0x89ABCDEF, 0x01234567);
            assertEquals(r, 0x89ABCDEF, "success compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0x89ABCDEF, 0xCAFEBABE);
            assertEquals(r, 0x01234567, "failing compareAndExchangeRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "failing compareAndExchangeRelease int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "success weakCompareAndSetPlain int value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetPlain int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetPlain int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "success weakCompareAndSetAcquire int");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = (boolean) mh.invokeExact(0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "failing weakCompareAndSetAcquire int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x01234567, 0x89ABCDEF);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "success weakCompareAndSetRelease int");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(0x01234567, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetRelease int value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x89ABCDEF, 0x01234567);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "success weakCompareAndSet int");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(0x89ABCDEF, 0xCAFEBABE);
            assertEquals(success, false, "failing weakCompareAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x01234567, x, "failing weakCompareAndSetRe int value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_SET).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndSet int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "getAndSet int value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndSetAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "getAndSetAcquire int value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndSetRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x89ABCDEF, x, "getAndSetRelease int value");
        }

        // get and add, add and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAdd int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAdd int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAddAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndAddRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddRelease int value");
        }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOr int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOr int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrRelease int value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAnd int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAnd int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndRelease int value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXor int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXor int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorRelease int value");
        }
    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {


    }


    static void testArray(Handles hs) throws Throwable {
        int[] array = new int[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "get int value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, 0x89ABCDEF);
                int x = (int) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "setVolatile int value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, 0x01234567);
                int x = (int) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(0x01234567, x, "setRelease int value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, 0x89ABCDEF);
                int x = (int) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "setOpaque int value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x01234567, 0x89ABCDEF);
                assertEquals(r, true, "success compareAndSet int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "success compareAndSet int value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x01234567, 0xCAFEBABE);
                assertEquals(r, false, "failing compareAndSet int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "failing compareAndSet int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0x89ABCDEF, 0x01234567);
                assertEquals(r, 0x89ABCDEF, "success compareAndExchange int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "success compareAndExchange int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0x89ABCDEF, 0xCAFEBABE);
                assertEquals(r, 0x01234567, "failing compareAndExchange int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "failing compareAndExchange int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x01234567, 0x89ABCDEF);
                assertEquals(r, 0x01234567, "success compareAndExchangeAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "success compareAndExchangeAcquire int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x01234567, 0xCAFEBABE);
                assertEquals(r, 0x89ABCDEF, "failing compareAndExchangeAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "failing compareAndExchangeAcquire int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0x89ABCDEF, 0x01234567);
                assertEquals(r, 0x89ABCDEF, "success compareAndExchangeRelease int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "success compareAndExchangeRelease int value");
            }

            {
                int r = (int) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0x89ABCDEF, 0xCAFEBABE);
                assertEquals(r, 0x01234567, "failing compareAndExchangeRelease int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "failing compareAndExchangeRelease int value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x01234567, 0x89ABCDEF);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "success weakCompareAndSetPlain int value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(array, i, 0x01234567, 0xCAFEBABE);
                assertEquals(success, false, "failing weakCompareAndSetPlain int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetPlain int value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x89ABCDEF, 0x01234567);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "success weakCompareAndSetAcquire int");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 0x89ABCDEF, 0xCAFEBABE);
                assertEquals(success, false, "failing weakCompareAndSetAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "failing weakCompareAndSetAcquire int value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x01234567, 0x89ABCDEF);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "success weakCompareAndSetRelease int");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 0x01234567, 0xCAFEBABE);
                assertEquals(success, false, "failing weakCompareAndSetAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "failing weakCompareAndSetAcquire int value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x89ABCDEF, 0x01234567);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "success weakCompareAndSet int");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, 0x89ABCDEF, 0xCAFEBABE);
                assertEquals(success, false, "failing weakCompareAndSet int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x01234567, x, "failing weakCompareAndSet int value");
            }

            // Compare set and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndSet int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "getAndSet int value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndSetAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "getAndSetAcquire int value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndSetRelease int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x89ABCDEF, x, "getAndSetRelease int value");
            }

            // get and add, add and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndAdd int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAdd int value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndAddAcquire int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddAcquire int value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

                int o = (int) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(array, i, 0x89ABCDEF);
                assertEquals(0x01234567, o, "getAndAddRelease int");
                int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((int)(0x01234567 + 0x89ABCDEF), x, "getAndAddRelease int value");
            }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOr int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOr int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseOrRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 | 0x89ABCDEF), x, "getAndBitwiseOrRelease int value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAnd int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAnd int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseAndRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 & 0x89ABCDEF), x, "getAndBitwiseAndRelease int value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXor int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXor int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorAcquire int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorAcquire int value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x01234567);

            int o = (int) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(array, i, 0x89ABCDEF);
            assertEquals(0x01234567, o, "getAndBitwiseXorRelease int");
            int x = (int) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((int)(0x01234567 ^ 0x89ABCDEF), x, "getAndBitwiseXorRelease int value");
        }
        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        int[] array = new int[10];

        final int i = 0;


    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        int[] array = new int[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkAIOOBE(am, () -> {
                    int x = (int) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkAIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 0x01234567);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkAIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 0x01234567, 0x89ABCDEF);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkAIOOBE(am, () -> {
                    int r = (int) hs.get(am).invokeExact(array, ci, 0x89ABCDEF, 0x01234567);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkAIOOBE(am, () -> {
                    int o = (int) hs.get(am).invokeExact(array, ci, 0x01234567);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
                checkAIOOBE(am, () -> {
                    int o = (int) hs.get(am).invokeExact(array, ci, 0xCAFEBABE);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
                checkAIOOBE(am, () -> {
                    int o = (int) hs.get(am).invokeExact(array, ci, 0xCAFEBABE);
                });
            }
        }
    }
}

