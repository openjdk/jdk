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
 * @run testng/othervm -Diters=20000 VarHandleTestMethodHandleAccessLong
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

    @BeforeClass
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


    @DataProvider
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

    @Test(dataProvider = "accessTestCaseProvider")
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
            assertEquals(x, 0x0123456789ABCDEFL, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(recv, 0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(recv, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            }
            assertEquals(success, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSet long value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetAcquire long");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(recv, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            }
            assertEquals(success, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSetRelease long");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(recv, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetVolatile long");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(recv, 0xCAFEBABECAFEBABEL);
            assertEquals(o, 0x0123456789ABCDEFL, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact(recv);
            assertEquals(x, 0xCAFEBABECAFEBABEL, "getAndSet long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(recv, 0x0123456789ABCDEFL);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(recv, 0xDEADBEEFDEADBEEFL);
            assertEquals(o, 0x0123456789ABCDEFL, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(recv, 0xDEADBEEFDEADBEEFL);
            assertEquals(c, (long)(0x0123456789ABCDEFL + 0xDEADBEEFDEADBEEFL + 0xDEADBEEFDEADBEEFL), "getAndAdd long value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestMethodHandleAccessLong recv, Handles hs) throws Throwable {

    }


    static void testStaticField(Handles hs) throws Throwable {
        // Plain
        {
            hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "set long value");
        }


        // Volatile
        {
            hs.get(TestAccessMode.SET_VOLATILE).invokeExact(0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "setVolatile long value");
        }

        // Lazy
        {
            hs.get(TestAccessMode.SET_RELEASE).invokeExact(0x0123456789ABCDEFL);
            long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "setRelease long value");
        }

        // Opaque
        {
            hs.get(TestAccessMode.SET_OPAQUE).invokeExact(0xCAFEBABECAFEBABEL);
            long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "setOpaque long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

        // Compare
        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndSet long value");
        }

        {
            boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndSet long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchange long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
            assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            }
            assertEquals(success, true, "weakCompareAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSet long value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetAcquire long");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
            }
            assertEquals(success, true, "weakCompareAndSetRelease long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSetRelease long");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetVolatile long");
        }

        // Compare set and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact( 0xCAFEBABECAFEBABEL);
            assertEquals(o, 0x0123456789ABCDEFL, "getAndSet long");
            long x = (long) hs.get(TestAccessMode.GET).invokeExact();
            assertEquals(x, 0xCAFEBABECAFEBABEL, "getAndSet long value");
        }

        hs.get(TestAccessMode.SET).invokeExact(0x0123456789ABCDEFL);

        // get and add, add and get
        {
            long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact( 0xDEADBEEFDEADBEEFL);
            assertEquals(o, 0x0123456789ABCDEFL, "getAndAdd long");
            long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(0xDEADBEEFDEADBEEFL);
            assertEquals(c, (long)(0x0123456789ABCDEFL + 0xDEADBEEFDEADBEEFL + 0xDEADBEEFDEADBEEFL), "getAndAdd long value");
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
                assertEquals(x, 0x0123456789ABCDEFL, "get long value");
            }


            // Volatile
            {
                hs.get(TestAccessMode.SET_VOLATILE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                long x = (long) hs.get(TestAccessMode.GET_VOLATILE).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "setVolatile long value");
            }

            // Lazy
            {
                hs.get(TestAccessMode.SET_RELEASE).invokeExact(array, i, 0x0123456789ABCDEFL);
                long x = (long) hs.get(TestAccessMode.GET_ACQUIRE).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "setRelease long value");
            }

            // Opaque
            {
                hs.get(TestAccessMode.SET_OPAQUE).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                long x = (long) hs.get(TestAccessMode.GET_OPAQUE).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "setOpaque long value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            // Compare
            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                assertEquals(r, true, "success compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndSet long value");
            }

            {
                boolean r = (boolean) hs.get(TestAccessMode.COMPARE_AND_SET).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, false, "failing compareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndSet long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchange long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchange long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchange long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchange long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                assertEquals(r, 0x0123456789ABCDEFL, "success compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "success compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_ACQUIRE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "failing compareAndExchangeAcquire long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                assertEquals(r, 0xCAFEBABECAFEBABEL, "success compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "success compareAndExchangeRelease long value");
            }

            {
                long r = (long) hs.get(TestAccessMode.COMPARE_AND_EXCHANGE_RELEASE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0xDEADBEEFDEADBEEFL);
                assertEquals(r, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "failing compareAndExchangeRelease long value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                }
                assertEquals(success, true, "weakCompareAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSet long value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_ACQUIRE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                }
                assertEquals(success, true, "weakCompareAndSetAcquire long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetAcquire long");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_RELEASE).invokeExact(array, i, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                }
                assertEquals(success, true, "weakCompareAndSetRelease long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "weakCompareAndSetRelease long");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = (boolean) hs.get(TestAccessMode.WEAK_COMPARE_AND_SET_VOLATILE).invokeExact(array, i, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                }
                assertEquals(success, true, "weakCompareAndSetVolatile long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0x0123456789ABCDEFL, "weakCompareAndSetVolatile long");
            }

            // Compare set and get
            {
                long o = (long) hs.get(TestAccessMode.GET_AND_SET).invokeExact(array, i, 0xCAFEBABECAFEBABEL);
                assertEquals(o, 0x0123456789ABCDEFL, "getAndSet long");
                long x = (long) hs.get(TestAccessMode.GET).invokeExact(array, i);
                assertEquals(x, 0xCAFEBABECAFEBABEL, "getAndSet long value");
            }

            hs.get(TestAccessMode.SET).invokeExact(array, i, 0x0123456789ABCDEFL);

            // get and add, add and get
            {
                long o = (long) hs.get(TestAccessMode.GET_AND_ADD).invokeExact(array, i, 0xDEADBEEFDEADBEEFL);
                assertEquals(o, 0x0123456789ABCDEFL, "getAndAdd long");
                long c = (long) hs.get(TestAccessMode.ADD_AND_GET).invokeExact(array, i, 0xDEADBEEFDEADBEEFL);
                assertEquals(c, (long)(0x0123456789ABCDEFL + 0xDEADBEEFDEADBEEFL + 0xDEADBEEFDEADBEEFL), "getAndAdd long value");
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
                checkIOOBE(am, () -> {
                    long x = (long) hs.get(am).invokeExact(array, ci);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.SET)) {
                checkIOOBE(am, () -> {
                    hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_SET)) {
                checkIOOBE(am, () -> {
                    boolean r = (boolean) hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL, 0xCAFEBABECAFEBABEL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.COMPARE_AND_EXCHANGE)) {
                checkIOOBE(am, () -> {
                    long r = (long) hs.get(am).invokeExact(array, ci, 0xCAFEBABECAFEBABEL, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_SET)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 0x0123456789ABCDEFL);
                });
            }

            for (TestAccessMode am : testAccessModesOfType(TestAccessType.GET_AND_ADD)) {
                checkIOOBE(am, () -> {
                    long o = (long) hs.get(am).invokeExact(array, ci, 0xDEADBEEFDEADBEEFL);
                });
            }
        }
    }
}

