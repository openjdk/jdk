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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessInt
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessInt
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessInt
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessInt
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

public class VarHandleTestAccessInt extends VarHandleBaseTest {
    static final int static_final_v = 1;

    static int static_v;

    final int final_v = 1;

    int v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessInt.class, "final_v", int.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessInt.class, "v", int.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessInt.class, "static_final_v", int.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessInt.class, "static_v", int.class);

        vhArray = MethodHandles.arrayElementVarHandle(int[].class);
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
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_VOLATILE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.ADD_AND_GET));
    }


    @DataProvider
    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessInt.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(int[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), int.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessInt.class, "final_v", int.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessInt.class, "v", int.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessInt.class, "static_final_v", int.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessInt.class, "static_v", int.class);
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
                                              vhStaticFinalField, VarHandleTestAccessInt::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessInt::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessInt::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessInt::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessInt::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessInt::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessInt::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessInt recv, VarHandle vh) {
        // Plain
        {
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "get int value");
        }


        // Volatile
        {
            int x = (int) vh.getVolatile(recv);
            assertEquals(x, 1, "getVolatile int value");
        }

        // Lazy
        {
            int x = (int) vh.getAcquire(recv);
            assertEquals(x, 1, "getRelease int value");
        }

        // Opaque
        {
            int x = (int) vh.getOpaque(recv);
            assertEquals(x, 1, "getOpaque int value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessInt recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, 2);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, 2);
        });

        checkUOE(() -> {
            vh.setRelease(recv, 2);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, 2);
        });


    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            int x = (int) vh.get();
            assertEquals(x, 1, "get int value");
        }


        // Volatile
        {
            int x = (int) vh.getVolatile();
            assertEquals(x, 1, "getVolatile int value");
        }

        // Lazy
        {
            int x = (int) vh.getAcquire();
            assertEquals(x, 1, "getRelease int value");
        }

        // Opaque
        {
            int x = (int) vh.getOpaque();
            assertEquals(x, 1, "getOpaque int value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(2);
        });

        checkUOE(() -> {
            vh.setVolatile(2);
        });

        checkUOE(() -> {
            vh.setRelease(2);
        });

        checkUOE(() -> {
            vh.setOpaque(2);
        });


    }


    static void testInstanceField(VarHandleTestAccessInt recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, 1);
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "set int value");
        }


        // Volatile
        {
            vh.setVolatile(recv, 2);
            int x = (int) vh.getVolatile(recv);
            assertEquals(x, 2, "setVolatile int value");
        }

        // Lazy
        {
            vh.setRelease(recv, 1);
            int x = (int) vh.getAcquire(recv);
            assertEquals(x, 1, "setRelease int value");
        }

        // Opaque
        {
            vh.setOpaque(recv, 2);
            int x = (int) vh.getOpaque(recv);
            assertEquals(x, 2, "setOpaque int value");
        }

        vh.set(recv, 1);

        // Compare
        {
            boolean r = vh.compareAndSet(recv, 1, 2);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "success compareAndSet int value");
        }

        {
            boolean r = vh.compareAndSet(recv, 1, 3);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "failing compareAndSet int value");
        }

        {
            int r = (int) vh.compareAndExchangeVolatile(recv, 2, 1);
            assertEquals(r, 2, "success compareAndExchangeVolatile int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "success compareAndExchangeVolatile int value");
        }

        {
            int r = (int) vh.compareAndExchangeVolatile(recv, 2, 3);
            assertEquals(r, 1, "failing compareAndExchangeVolatile int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
        }

        {
            int r = (int) vh.compareAndExchangeAcquire(recv, 1, 2);
            assertEquals(r, 1, "success compareAndExchangeAcquire int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) vh.compareAndExchangeAcquire(recv, 1, 3);
            assertEquals(r, 2, "failing compareAndExchangeAcquire int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) vh.compareAndExchangeRelease(recv, 2, 1);
            assertEquals(r, 2, "success compareAndExchangeRelease int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) vh.compareAndExchangeRelease(recv, 2, 3);
            assertEquals(r, 1, "failing compareAndExchangeRelease int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "failing compareAndExchangeRelease int value");
        }

        {
            boolean r = vh.weakCompareAndSet(recv, 1, 2);
            assertEquals(r, true, "weakCompareAndSet int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "weakCompareAndSet int value");
        }

        {
            boolean r = vh.weakCompareAndSetAcquire(recv, 2, 1);
            assertEquals(r, true, "weakCompareAndSetAcquire int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "weakCompareAndSetAcquire int");
        }

        {
            boolean r = vh.weakCompareAndSetRelease(recv, 1, 2);
            assertEquals(r, true, "weakCompareAndSetRelease int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "weakCompareAndSetRelease int");
        }

        {
            boolean r = vh.weakCompareAndSetVolatile(recv, 2, 1);
            assertEquals(r, true, "weakCompareAndSetVolatile int");
            int x = (int) vh.get(recv);
            assertEquals(x, 1, "weakCompareAndSetVolatile int value");
        }

        // Compare set and get
        {
            int o = (int) vh.getAndSet(recv, 2);
            assertEquals(o, 1, "getAndSet int");
            int x = (int) vh.get(recv);
            assertEquals(x, 2, "getAndSet int value");
        }

        vh.set(recv, 1);

        // get and add, add and get
        {
            int o = (int) vh.getAndAdd(recv, 3);
            assertEquals(o, 1, "getAndAdd int");
            int c = (int) vh.addAndGet(recv, 3);
            assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessInt recv, VarHandle vh) {

    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(1);
            int x = (int) vh.get();
            assertEquals(x, 1, "set int value");
        }


        // Volatile
        {
            vh.setVolatile(2);
            int x = (int) vh.getVolatile();
            assertEquals(x, 2, "setVolatile int value");
        }

        // Lazy
        {
            vh.setRelease(1);
            int x = (int) vh.getAcquire();
            assertEquals(x, 1, "setRelease int value");
        }

        // Opaque
        {
            vh.setOpaque(2);
            int x = (int) vh.getOpaque();
            assertEquals(x, 2, "setOpaque int value");
        }

        vh.set(1);

        // Compare
        {
            boolean r = vh.compareAndSet(1, 2);
            assertEquals(r, true, "success compareAndSet int");
            int x = (int) vh.get();
            assertEquals(x, 2, "success compareAndSet int value");
        }

        {
            boolean r = vh.compareAndSet(1, 3);
            assertEquals(r, false, "failing compareAndSet int");
            int x = (int) vh.get();
            assertEquals(x, 2, "failing compareAndSet int value");
        }

        {
            int r = (int) vh.compareAndExchangeVolatile(2, 1);
            assertEquals(r, 2, "success compareAndExchangeVolatile int");
            int x = (int) vh.get();
            assertEquals(x, 1, "success compareAndExchangeVolatile int value");
        }

        {
            int r = (int) vh.compareAndExchangeVolatile(2, 3);
            assertEquals(r, 1, "failing compareAndExchangeVolatile int");
            int x = (int) vh.get();
            assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
        }

        {
            int r = (int) vh.compareAndExchangeAcquire(1, 2);
            assertEquals(r, 1, "success compareAndExchangeAcquire int");
            int x = (int) vh.get();
            assertEquals(x, 2, "success compareAndExchangeAcquire int value");
        }

        {
            int r = (int) vh.compareAndExchangeAcquire(1, 3);
            assertEquals(r, 2, "failing compareAndExchangeAcquire int");
            int x = (int) vh.get();
            assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
        }

        {
            int r = (int) vh.compareAndExchangeRelease(2, 1);
            assertEquals(r, 2, "success compareAndExchangeRelease int");
            int x = (int) vh.get();
            assertEquals(x, 1, "success compareAndExchangeRelease int value");
        }

        {
            int r = (int) vh.compareAndExchangeRelease(2, 3);
            assertEquals(r, 1, "failing compareAndExchangeRelease int");
            int x = (int) vh.get();
            assertEquals(x, 1, "failing compareAndExchangeRelease int value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSet(1, 2);
            assertEquals(r, true, "weakCompareAndSet int");
            int x = (int) vh.get();
            assertEquals(x, 2, "weakCompareAndSet int value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetAcquire(2, 1);
            assertEquals(r, true, "weakCompareAndSetAcquire int");
            int x = (int) vh.get();
            assertEquals(x, 1, "weakCompareAndSetAcquire int");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetRelease(1, 2);
            assertEquals(r, true, "weakCompareAndSetRelease int");
            int x = (int) vh.get();
            assertEquals(x, 2, "weakCompareAndSetRelease int");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetVolatile(2, 1);
            assertEquals(r, true, "weakCompareAndSetVolatile int");
            int x = (int) vh.get();
            assertEquals(x, 1, "weakCompareAndSetVolatile int value");
        }

        // Compare set and get
        {
            int o = (int) vh.getAndSet( 2);
            assertEquals(o, 1, "getAndSet int");
            int x = (int) vh.get();
            assertEquals(x, 2, "getAndSet int value");
        }

        vh.set(1);

        // get and add, add and get
        {
            int o = (int) vh.getAndAdd( 3);
            assertEquals(o, 1, "getAndAdd int");
            int c = (int) vh.addAndGet(3);
            assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
        }
    }

    static void testStaticFieldUnsupported(VarHandle vh) {

    }


    static void testArray(VarHandle vh) {
        int[] array = new int[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, 1);
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "get int value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, 2);
                int x = (int) vh.getVolatile(array, i);
                assertEquals(x, 2, "setVolatile int value");
            }

            // Lazy
            {
                vh.setRelease(array, i, 1);
                int x = (int) vh.getAcquire(array, i);
                assertEquals(x, 1, "setRelease int value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, 2);
                int x = (int) vh.getOpaque(array, i);
                assertEquals(x, 2, "setOpaque int value");
            }

            vh.set(array, i, 1);

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, 1, 2);
                assertEquals(r, true, "success compareAndSet int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "success compareAndSet int value");
            }

            {
                boolean r = vh.compareAndSet(array, i, 1, 3);
                assertEquals(r, false, "failing compareAndSet int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "failing compareAndSet int value");
            }

            {
                int r = (int) vh.compareAndExchangeVolatile(array, i, 2, 1);
                assertEquals(r, 2, "success compareAndExchangeVolatile int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "success compareAndExchangeVolatile int value");
            }

            {
                int r = (int) vh.compareAndExchangeVolatile(array, i, 2, 3);
                assertEquals(r, 1, "failing compareAndExchangeVolatile int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "failing compareAndExchangeVolatile int value");
            }

            {
                int r = (int) vh.compareAndExchangeAcquire(array, i, 1, 2);
                assertEquals(r, 1, "success compareAndExchangeAcquire int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "success compareAndExchangeAcquire int value");
            }

            {
                int r = (int) vh.compareAndExchangeAcquire(array, i, 1, 3);
                assertEquals(r, 2, "failing compareAndExchangeAcquire int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "failing compareAndExchangeAcquire int value");
            }

            {
                int r = (int) vh.compareAndExchangeRelease(array, i, 2, 1);
                assertEquals(r, 2, "success compareAndExchangeRelease int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "success compareAndExchangeRelease int value");
            }

            {
                int r = (int) vh.compareAndExchangeRelease(array, i, 2, 3);
                assertEquals(r, 1, "failing compareAndExchangeRelease int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "failing compareAndExchangeRelease int value");
            }

            {
                boolean r = vh.weakCompareAndSet(array, i, 1, 2);
                assertEquals(r, true, "weakCompareAndSet int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "weakCompareAndSet int value");
            }

            {
                boolean r = vh.weakCompareAndSetAcquire(array, i, 2, 1);
                assertEquals(r, true, "weakCompareAndSetAcquire int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "weakCompareAndSetAcquire int");
            }

            {
                boolean r = vh.weakCompareAndSetRelease(array, i, 1, 2);
                assertEquals(r, true, "weakCompareAndSetRelease int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "weakCompareAndSetRelease int");
            }

            {
                boolean r = vh.weakCompareAndSetVolatile(array, i, 2, 1);
                assertEquals(r, true, "weakCompareAndSetVolatile int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 1, "weakCompareAndSetVolatile int value");
            }

            // Compare set and get
            {
                int o = (int) vh.getAndSet(array, i, 2);
                assertEquals(o, 1, "getAndSet int");
                int x = (int) vh.get(array, i);
                assertEquals(x, 2, "getAndSet int value");
            }

            vh.set(array, i, 1);

            // get and add, add and get
            {
                int o = (int) vh.getAndAdd(array, i, 3);
                assertEquals(o, 1, "getAndAdd int");
                int c = (int) vh.addAndGet(array, i, 3);
                assertEquals(c, 1 + 3 + 3, "getAndAdd int value");
            }
        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        int[] array = new int[10];

        int i = 0;

    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        int[] array = new int[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                int x = (int) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, 1);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, 1);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, 1);
            });

            checkIOOBE(() -> {
                int x = (int) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, 1);
            });

            checkIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, 1, 2);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeVolatile(array, ci, 2, 1);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeAcquire(array, ci, 2, 1);
            });

            checkIOOBE(() -> {
                int r = (int) vh.compareAndExchangeRelease(array, ci, 2, 1);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, 1, 2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetVolatile(array, ci, 1, 2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, 1, 2);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, 1, 2);
            });

            checkIOOBE(() -> {
                int o = (int) vh.getAndSet(array, ci, 1);
            });

            checkIOOBE(() -> {
                int o = (int) vh.getAndAdd(array, ci, 3);
            });

            checkIOOBE(() -> {
                int o = (int) vh.addAndGet(array, ci, 3);
            });
        }
    }
}

