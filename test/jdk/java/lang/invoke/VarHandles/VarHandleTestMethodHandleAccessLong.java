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
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 VarHandleTestMethodHandleAccessLong
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
public class VarHandleTestMethodHandleAccessLong extends VarHandleBaseTest {
    static final long static_final_v = 0x0123456789ABCDEFL;

    static long static_v;

    final long final_v = 0x0123456789ABCDEFL;

    long v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeAll
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessLong.class, "final_v", long.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestMethodHandleAccessLong.class, "v", long.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessLong.class, "static_final_v", long.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestMethodHandleAccessLong.class, "static_v", long.class);

        vhArray = MethodHandles.arrayElementVarHandle(long[].class);
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
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessLong::testStaticField));
            cases.add(new MethodHandleAccessTestCase("Static field unsupported",
                                                     vhStaticField, f, VarHandleTestMethodHandleAccessLong::testStaticFieldUnsupported,
                                                     false));

            cases.add(new MethodHandleAccessTestCase("Array",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArray));
            cases.add(new MethodHandleAccessTestCase("Array unsupported",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArrayUnsupported,
                                                     false));
            cases.add(new MethodHandleAccessTestCase("Array index out of bounds",
                                                     vhArray, f, VarHandleTestMethodHandleAccessLong::testArrayIndexOutOfBounds,
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

    static void testInstanceField(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, 0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchangeRelease long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetPlain long value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetPlain long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetPlain long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSetAcquire long");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSetAcquire long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetRelease long");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetRelease long value");
        }

        {
            boolean success = false;
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSet long");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSet long value");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSet long value");
        }

        // get and add, add and get
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAdd long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAdd long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAddAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAddRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddRelease long value");
        }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOr long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOr long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrRelease long value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAnd long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAnd long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndRelease long value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXor long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXor long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorRelease long value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {


    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchangeRelease long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetPlain long value");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetPlain long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetPlain long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSetAcquire long");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
            boolean success = (boolean) mh.invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSetAcquire long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetRelease long");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetRelease long value");
        }

        {
            MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) mh.invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSet long");
        }

        {
            boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(success, false, "failing weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSetRe long value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSet long value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSetAcquire long value");
        }

        // Compare set and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSetRelease long value");
        }

        // get and add, add and get
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAdd long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAdd long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAddAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndAddRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddRelease long value");
        }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOr long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOr long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrRelease long value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAnd long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAnd long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndRelease long value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXor long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXor long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorRelease long value");
        }
    }

    static void testStaticFieldUnsupported(Handles hs) throws Throwable {


    }


    static void testArray(Handles hs) throws Throwable {
        long[] array = new long[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "get long value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "setVolatile long value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, 0x0123456789ABCDEFL);
                long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "setRelease long value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "setOpaque long value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                assertEquals(r, true, "success compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, false, "failing compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndSet long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchange long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchange long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "success compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "failing compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "success compareAndExchangeRelease long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "failing compareAndExchangeRelease long value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetPlain long value");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_PLAIN).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(success, false, "failing weakCompareAndSetPlain long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetPlain long value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSetAcquire long");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(success, false, "failing weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSetAcquire long value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "success weakCompareAndSetRelease long");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(success, false, "failing weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "failing weakCompareAndSetAcquire long value");
            }

            {
                MethodHandle mh = hs.get(TestAccessMode.WEAK_COMPARE_AND_SET);
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) mh.invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "success weakCompareAndSet long");
            }

            {
                boolean success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(success, false, "failing weakCompareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0x0123456789ABCDEFL, x, "failing weakCompareAndSet long value");
            }

            // Compare set and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSet long value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_SET_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSetAcquire long value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_SET_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndSetRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(0xCAFEBABECAFEBABEL, x, "getAndSetRelease long value");
            }

            // get and add, add and get
            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndAdd long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAdd long value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_ADD_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndAddAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddAcquire long value");
            }

            {
                hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

                long o = (long) hs.get(TestAccessMode.GET_AND_ADD_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(0x0123456789ABCDEFL, o, "getAndAddRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals((long)(0x0123456789ABCDEFL + 0xCAFEBABECAFEBABEL), x, "getAndAddRelease long value");
            }

        // get and bitwise or
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOr long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOr long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_OR_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseOrRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL | 0xCAFEBABECAFEBABEL), x, "getAndBitwiseOrRelease long value");
        }

        // get and bitwise and
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAnd long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAnd long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_AND_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseAndRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL & 0xCAFEBABECAFEBABEL), x, "getAndBitwiseAndRelease long value");
        }

        // get and bitwise xor
        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXor long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXor long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorAcquire long value");
        }

        {
            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            long o = (long) hs.get(TestAccessMode.GET_AND_BITWISE_XOR_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
            assertEquals(0x0123456789ABCDEFL, o, "getAndBitwiseXorRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
            assertEquals((long)(0x0123456789ABCDEFL ^ 0xCAFEBABECAFEBABEL), x, "getAndBitwiseXorRelease long value");
        }
        }
    }

    static void testArrayUnsupported(Handles hs) throws Throwable {
        long[] array = new long[10];

        final int i = 0;


    }

    static void testArrayIndexOutOfBounds(Handles hs) throws Throwable {
        long[] array = new long[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET)) {
                checkAIOOBE(am, () -> {
                    long x = (long) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkAIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkAIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkAIOOBE(am, () -> {
                    long r = (long) hs.get(am).invokeExact(array, ci, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkAIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
                checkAIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 0xDEADBEEFDEADBEEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_BITWISE)) {
                checkAIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 0xDEADBEEFDEADBEEFL);
                });
            }
        }
    }
}

