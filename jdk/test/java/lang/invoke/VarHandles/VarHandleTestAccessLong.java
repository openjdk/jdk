/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessLong
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessLong
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessLong
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessLong
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

public class VarHandleTestAccessLong extends VarHandleBaseTest {
    static final long static_final_v = 1L;

    static long static_v;

    final long final_v = 1L;

    long v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessLong.class, "final_v", long.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessLong.class, "v", long.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessLong.class, "static_final_v", long.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessLong.class, "static_v", long.class);

        vhArray = MethodHandles.arrayElementVarHandle(long[].class);
    }


    @DataProvider
    public Object[][] varHandlesProvider() throws Exception {
        List<VarHandle> vhs = new ArrayList<>();
        vhs.add(vhField);
        vhs.add(vhStaticField);
        vhs.add(vhArray);

        return vhs.stream().map(tc -> new Object[]{tc}).toArray(Object[][]::new);
    }

    @Test(dataProvider = "varHandlesProvider")
    public void testIsAccessModeSupported(VarHandle vh) {
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_OPAQUE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.SET_OPAQUE));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.ADD_AND_GET));
    }


    @DataProvider
    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessLong.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(long[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), long.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessLong.class, "final_v", long.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessLong.class, "v", long.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessLong.class, "static_final_v", long.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessLong.class, "static_v", long.class);
        });
    }


    @DataProvider
    public Object[][] accessTestCaseProvider() throws Exception {
        List<AccessTestCase<?>> cases = new ArrayList<>();

        cases.add(new VarHandleAccessTestCase("Instance final field",
                                              vhFinalField, vh -> testInstanceFinalField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance final field unsupported",
                                              vhFinalField, vh -> testInstanceFinalFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static final field",
                                              vhStaticFinalField, VarHandleTestAccessLong::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessLong::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessLong::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessLong::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessLong::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessLong::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessLong::testArrayIndexOutOfBounds,
                                              false));

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




    static void testInstanceFinalField(VarHandleTestAccessLong recv, VarHandle vh) {
        // Plain
        {
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "get long value");
        }


        // Volatile
        {
            long x = (long) vh.getVolatile(recv);
            assertEquals(x, 1L, "getVolatile long value");
        }

        // Lazy
        {
            long x = (long) vh.getAcquire(recv);
            assertEquals(x, 1L, "getRelease long value");
        }

        // Opaque
        {
            long x = (long) vh.getOpaque(recv);
            assertEquals(x, 1L, "getOpaque long value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessLong recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, 2L);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, 2L);
        });

        checkUOE(() -> {
            vh.setRelease(recv, 2L);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, 2L);
        });


    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            long x = (long) vh.get();
            assertEquals(x, 1L, "get long value");
        }


        // Volatile
        {
            long x = (long) vh.getVolatile();
            assertEquals(x, 1L, "getVolatile long value");
        }

        // Lazy
        {
            long x = (long) vh.getAcquire();
            assertEquals(x, 1L, "getRelease long value");
        }

        // Opaque
        {
            long x = (long) vh.getOpaque();
            assertEquals(x, 1L, "getOpaque long value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(2L);
        });

        checkUOE(() -> {
            vh.setVolatile(2L);
        });

        checkUOE(() -> {
            vh.setRelease(2L);
        });

        checkUOE(() -> {
            vh.setOpaque(2L);
        });


    }


    static void testInstanceField(VarHandleTestAccessLong recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, 1L);
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            vh.setVolatile(recv, 2L);
            long x = (long) vh.getVolatile(recv);
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            vh.setRelease(recv, 1L);
            long x = (long) vh.getAcquire(recv);
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            vh.setOpaque(recv, 2L);
            long x = (long) vh.getOpaque(recv);
            assertEquals(x, 2L, "setOpaque long value");
        }

        vh.set(recv, 1L);

        // Compare
        {
            boolean r = vh.compareAndSet(recv, 1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = vh.compareAndSet(recv, 1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) vh.compareAndExchangeVolatile(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) vh.compareAndExchangeVolatile(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) vh.compareAndExchangeAcquire(recv, 1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) vh.compareAndExchangeAcquire(recv, 1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) vh.compareAndExchangeRelease(recv, 2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) vh.compareAndExchangeRelease(recv, 2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = vh.weakCompareAndSet(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = vh.weakCompareAndSetAcquire(recv, 2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = vh.weakCompareAndSetRelease(recv, 1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) vh.get(recv);
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        // Compare set and get
        {
            long o = (long) vh.getAndSet(recv, 1L);
            assertEquals(o, 2L, "getAndSet long");
            long x = (long) vh.get(recv);
            assertEquals(x, 1L, "getAndSet long value");
        }

        vh.set(recv, 1L);

        // get and add, add and get
        {
            long o = (long) vh.getAndAdd(recv, 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) vh.addAndGet(recv, 3L);
            assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessLong recv, VarHandle vh) {

    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(1L);
            long x = (long) vh.get();
            assertEquals(x, 1L, "set long value");
        }


        // Volatile
        {
            vh.setVolatile(2L);
            long x = (long) vh.getVolatile();
            assertEquals(x, 2L, "setVolatile long value");
        }

        // Lazy
        {
            vh.setRelease(1L);
            long x = (long) vh.getAcquire();
            assertEquals(x, 1L, "setRelease long value");
        }

        // Opaque
        {
            vh.setOpaque(2L);
            long x = (long) vh.getOpaque();
            assertEquals(x, 2L, "setOpaque long value");
        }

        vh.set(1L);

        // Compare
        {
            boolean r = vh.compareAndSet(1L, 2L);
            assertEquals(r, true, "success compareAndSet long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "success compareAndSet long value");
        }

        {
            boolean r = vh.compareAndSet(1L, 3L);
            assertEquals(r, false, "failing compareAndSet long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "failing compareAndSet long value");
        }

        {
            long r = (long) vh.compareAndExchangeVolatile(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeVolatile long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
        }

        {
            long r = (long) vh.compareAndExchangeVolatile(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
        }

        {
            long r = (long) vh.compareAndExchangeAcquire(1L, 2L);
            assertEquals(r, 1L, "success compareAndExchangeAcquire long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
        }

        {
            long r = (long) vh.compareAndExchangeAcquire(1L, 3L);
            assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
        }

        {
            long r = (long) vh.compareAndExchangeRelease(2L, 1L);
            assertEquals(r, 2L, "success compareAndExchangeRelease long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "success compareAndExchangeRelease long value");
        }

        {
            long r = (long) vh.compareAndExchangeRelease(2L, 3L);
            assertEquals(r, 1L, "failing compareAndExchangeRelease long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSet(1L, 2L);
            assertEquals(r, true, "weakCompareAndSet long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "weakCompareAndSet long value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetAcquire(2L, 1L);
            assertEquals(r, true, "weakCompareAndSetAcquire long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "weakCompareAndSetAcquire long");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetRelease( 1L, 2L);
            assertEquals(r, true, "weakCompareAndSetRelease long");
            long x = (long) vh.get();
            assertEquals(x, 2L, "weakCompareAndSetRelease long");
        }

        // Compare set and get
        {
            long o = (long) vh.getAndSet( 1L);
            assertEquals(o, 2L, "getAndSet long");
            long x = (long) vh.get();
            assertEquals(x, 1L, "getAndSet long value");
        }

        vh.set(1L);

        // get and add, add and get
        {
            long o = (long) vh.getAndAdd( 3L);
            assertEquals(o, 1L, "getAndAdd long");
            long c = (long) vh.addAndGet(3L);
            assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
        }
    }

    static void testStaticFieldUnsupported(VarHandle vh) {

    }


    static void testArray(VarHandle vh) {
        long[] array = new long[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, 1L);
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "get long value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, 2L);
                long x = (long) vh.getVolatile(array, i);
                assertEquals(x, 2L, "setVolatile long value");
            }

            // Lazy
            {
                vh.setRelease(array, i, 1L);
                long x = (long) vh.getAcquire(array, i);
                assertEquals(x, 1L, "setRelease long value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, 2L);
                long x = (long) vh.getOpaque(array, i);
                assertEquals(x, 2L, "setOpaque long value");
            }

            vh.set(array, i, 1L);

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, 1L, 2L);
                assertEquals(r, true, "success compareAndSet long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "success compareAndSet long value");
            }

            {
                boolean r = vh.compareAndSet(array, i, 1L, 3L);
                assertEquals(r, false, "failing compareAndSet long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "failing compareAndSet long value");
            }

            {
                long r = (long) vh.compareAndExchangeVolatile(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeVolatile long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "success compareAndExchangeVolatile long value");
            }

            {
                long r = (long) vh.compareAndExchangeVolatile(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeVolatile long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeVolatile long value");
            }

            {
                long r = (long) vh.compareAndExchangeAcquire(array, i, 1L, 2L);
                assertEquals(r, 1L, "success compareAndExchangeAcquire long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "success compareAndExchangeAcquire long value");
            }

            {
                long r = (long) vh.compareAndExchangeAcquire(array, i, 1L, 3L);
                assertEquals(r, 2L, "failing compareAndExchangeAcquire long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "failing compareAndExchangeAcquire long value");
            }

            {
                long r = (long) vh.compareAndExchangeRelease(array, i, 2L, 1L);
                assertEquals(r, 2L, "success compareAndExchangeRelease long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "success compareAndExchangeRelease long value");
            }

            {
                long r = (long) vh.compareAndExchangeRelease(array, i, 2L, 3L);
                assertEquals(r, 1L, "failing compareAndExchangeRelease long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "failing compareAndExchangeRelease long value");
            }

            {
                boolean r = vh.weakCompareAndSet(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSet long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "weakCompareAndSet long value");
            }

            {
                boolean r = vh.weakCompareAndSetAcquire(array, i, 2L, 1L);
                assertEquals(r, true, "weakCompareAndSetAcquire long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "weakCompareAndSetAcquire long");
            }

            {
                boolean r = vh.weakCompareAndSetRelease(array, i, 1L, 2L);
                assertEquals(r, true, "weakCompareAndSetRelease long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 2L, "weakCompareAndSetRelease long");
            }

            // Compare set and get
            {
                long o = (long) vh.getAndSet(array, i, 1L);
                assertEquals(o, 2L, "getAndSet long");
                long x = (long) vh.get(array, i);
                assertEquals(x, 1L, "getAndSet long value");
            }

            vh.set(array, i, 1L);

            // get and add, add and get
            {
                long o = (long) vh.getAndAdd(array, i, 3L);
                assertEquals(o, 1L, "getAndAdd long");
                long c = (long) vh.addAndGet(array, i, 3L);
                assertEquals(c, 1L + 3L + 3L, "getAndAdd long value");
            }
        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        long[] array = new long[10];

        int i = 0;

    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        long[] array = new long[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                long x = (long) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, 1L);
            });

            checkIOOBE(() -> {
                long x = (long) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, 1L);
            });

            checkIOOBE(() -> {
                long x = (long) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, 1L);
            });

            checkIOOBE(() -> {
                long x = (long) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, 1L);
            });

            checkIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, 1L, 2L);
            });

            checkIOOBE(() -> {
                long r = (long) vh.compareAndExchangeVolatile(array, ci, 2L, 1L);
            });

            checkIOOBE(() -> {
                long r = (long) vh.compareAndExchangeAcquire(array, ci, 2L, 1L);
            });

            checkIOOBE(() -> {
                long r = (long) vh.compareAndExchangeRelease(array, ci, 2L, 1L);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, 1L, 2L);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, 1L, 2L);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, 1L, 2L);
            });

            checkIOOBE(() -> {
                long o = (long) vh.getAndSet(array, ci, 1L);
            });

            checkIOOBE(() -> {
                long o = (long) vh.getAndAdd(array, ci, 3L);
            });

            checkIOOBE(() -> {
                long o = (long) vh.addAndGet(array, ci, 3L);
            });
        }
    }
}

