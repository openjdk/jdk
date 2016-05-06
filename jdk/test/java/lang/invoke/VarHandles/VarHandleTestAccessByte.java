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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessByte
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessByte
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessByte
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessByte
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

public class VarHandleTestAccessByte extends VarHandleBaseTest {
    static final byte static_final_v = (byte)1;

    static byte static_v;

    final byte final_v = (byte)1;

    byte v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessByte.class, "final_v", byte.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessByte.class, "v", byte.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessByte.class, "static_final_v", byte.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessByte.class, "static_v", byte.class);

        vhArray = MethodHandles.arrayElementVarHandle(byte[].class);
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

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_SET));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_VOLATILE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE_RELEASE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_VOLATILE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET));

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.ADD_AND_GET));
    }


    @DataProvider
    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessByte.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(byte[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), byte.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessByte.class, "final_v", byte.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessByte.class, "v", byte.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessByte.class, "static_final_v", byte.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessByte.class, "static_v", byte.class);
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
                                              vhStaticFinalField, VarHandleTestAccessByte::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessByte::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessByte::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessByte::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessByte::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessByte::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessByte::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessByte recv, VarHandle vh) {
        // Plain
        {
            byte x = (byte) vh.get(recv);
            assertEquals(x, (byte)1, "get byte value");
        }


        // Volatile
        {
            byte x = (byte) vh.getVolatile(recv);
            assertEquals(x, (byte)1, "getVolatile byte value");
        }

        // Lazy
        {
            byte x = (byte) vh.getAcquire(recv);
            assertEquals(x, (byte)1, "getRelease byte value");
        }

        // Opaque
        {
            byte x = (byte) vh.getOpaque(recv);
            assertEquals(x, (byte)1, "getOpaque byte value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessByte recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, (byte)2);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, (byte)2);
        });

        checkUOE(() -> {
            vh.setRelease(recv, (byte)2);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeVolatile(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeAcquire(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeRelease(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte o = (byte) vh.getAndAdd(recv, (byte)1);
        });

        checkUOE(() -> {
            byte o = (byte) vh.addAndGet(recv, (byte)1);
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            byte x = (byte) vh.get();
            assertEquals(x, (byte)1, "get byte value");
        }


        // Volatile
        {
            byte x = (byte) vh.getVolatile();
            assertEquals(x, (byte)1, "getVolatile byte value");
        }

        // Lazy
        {
            byte x = (byte) vh.getAcquire();
            assertEquals(x, (byte)1, "getRelease byte value");
        }

        // Opaque
        {
            byte x = (byte) vh.getOpaque();
            assertEquals(x, (byte)1, "getOpaque byte value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set((byte)2);
        });

        checkUOE(() -> {
            vh.setVolatile((byte)2);
        });

        checkUOE(() -> {
            vh.setRelease((byte)2);
        });

        checkUOE(() -> {
            vh.setOpaque((byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeVolatile((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeAcquire((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeRelease((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte o = (byte) vh.getAndAdd((byte)1);
        });

        checkUOE(() -> {
            byte o = (byte) vh.addAndGet((byte)1);
        });
    }


    static void testInstanceField(VarHandleTestAccessByte recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, (byte)1);
            byte x = (byte) vh.get(recv);
            assertEquals(x, (byte)1, "set byte value");
        }


        // Volatile
        {
            vh.setVolatile(recv, (byte)2);
            byte x = (byte) vh.getVolatile(recv);
            assertEquals(x, (byte)2, "setVolatile byte value");
        }

        // Lazy
        {
            vh.setRelease(recv, (byte)1);
            byte x = (byte) vh.getAcquire(recv);
            assertEquals(x, (byte)1, "setRelease byte value");
        }

        // Opaque
        {
            vh.setOpaque(recv, (byte)2);
            byte x = (byte) vh.getOpaque(recv);
            assertEquals(x, (byte)2, "setOpaque byte value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessByte recv, VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeVolatile(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeAcquire(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeRelease(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte o = (byte) vh.getAndAdd(recv, (byte)1);
        });

        checkUOE(() -> {
            byte o = (byte) vh.addAndGet(recv, (byte)1);
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set((byte)1);
            byte x = (byte) vh.get();
            assertEquals(x, (byte)1, "set byte value");
        }


        // Volatile
        {
            vh.setVolatile((byte)2);
            byte x = (byte) vh.getVolatile();
            assertEquals(x, (byte)2, "setVolatile byte value");
        }

        // Lazy
        {
            vh.setRelease((byte)1);
            byte x = (byte) vh.getAcquire();
            assertEquals(x, (byte)1, "setRelease byte value");
        }

        // Opaque
        {
            vh.setOpaque((byte)2);
            byte x = (byte) vh.getOpaque();
            assertEquals(x, (byte)2, "setOpaque byte value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeVolatile((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeAcquire((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeRelease((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire((byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease((byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte o = (byte) vh.getAndAdd((byte)1);
        });

        checkUOE(() -> {
            byte o = (byte) vh.addAndGet((byte)1);
        });
    }


    static void testArray(VarHandle vh) {
        byte[] array = new byte[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, (byte)1);
                byte x = (byte) vh.get(array, i);
                assertEquals(x, (byte)1, "get byte value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, (byte)2);
                byte x = (byte) vh.getVolatile(array, i);
                assertEquals(x, (byte)2, "setVolatile byte value");
            }

            // Lazy
            {
                vh.setRelease(array, i, (byte)1);
                byte x = (byte) vh.getAcquire(array, i);
                assertEquals(x, (byte)1, "setRelease byte value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, (byte)2);
                byte x = (byte) vh.getOpaque(array, i);
                assertEquals(x, (byte)2, "setOpaque byte value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        byte[] array = new byte[10];

        int i = 0;
        checkUOE(() -> {
            boolean r = vh.compareAndSet(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeVolatile(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeAcquire(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte r = (byte) vh.compareAndExchangeRelease(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, i, (byte)1, (byte)2);
        });

        checkUOE(() -> {
            byte o = (byte) vh.getAndAdd(array, i, (byte)1);
        });

        checkUOE(() -> {
            byte o = (byte) vh.addAndGet(array, i, (byte)1);
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        byte[] array = new byte[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                byte x = (byte) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, (byte)1);
            });

            checkIOOBE(() -> {
                byte x = (byte) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, (byte)1);
            });

            checkIOOBE(() -> {
                byte x = (byte) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, (byte)1);
            });

            checkIOOBE(() -> {
                byte x = (byte) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, (byte)1);
            });


        }
    }
}

