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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessShort
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessShort
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessShort
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessShort
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

public class VarHandleTestAccessShort extends VarHandleBaseTest {
    static final short static_final_v = (short)1;

    static short static_v;

    final short final_v = (short)1;

    short v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessShort.class, "final_v", short.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessShort.class, "v", short.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessShort.class, "static_final_v", short.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessShort.class, "static_v", short.class);

        vhArray = MethodHandles.arrayElementVarHandle(short[].class);
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
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.get));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.set));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getVolatile));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setVolatile));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getAcquire));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setRelease));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.getOpaque));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.setOpaque));

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndSet));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeVolatile));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeAcquire));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.compareAndExchangeRelease));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSet));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetAcquire));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetRelease));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.weakCompareAndSetRelease));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.getAndSet));

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.getAndAdd));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.addAndGet));
    }


    @DataProvider
    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessShort.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(short[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), short.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessShort.class, "final_v", short.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessShort.class, "v", short.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessShort.class, "static_final_v", short.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessShort.class, "static_v", short.class);
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
                                              vhStaticFinalField, VarHandleTestAccessShort::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessShort::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessShort::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessShort::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessShort::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessShort::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessShort::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessShort recv, VarHandle vh) {
        // Plain
        {
            short x = (short) vh.get(recv);
            assertEquals(x, (short)1, "get short value");
        }


        // Volatile
        {
            short x = (short) vh.getVolatile(recv);
            assertEquals(x, (short)1, "getVolatile short value");
        }

        // Lazy
        {
            short x = (short) vh.getAcquire(recv);
            assertEquals(x, (short)1, "getRelease short value");
        }

        // Opaque
        {
            short x = (short) vh.getOpaque(recv);
            assertEquals(x, (short)1, "getOpaque short value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessShort recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, (short)2);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, (short)2);
        });

        checkUOE(() -> {
            vh.setRelease(recv, (short)2);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeVolatile(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeAcquire(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeRelease(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short o = (short) vh.getAndAdd(recv, (short)1);
        });

        checkUOE(() -> {
            short o = (short) vh.addAndGet(recv, (short)1);
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            short x = (short) vh.get();
            assertEquals(x, (short)1, "get short value");
        }


        // Volatile
        {
            short x = (short) vh.getVolatile();
            assertEquals(x, (short)1, "getVolatile short value");
        }

        // Lazy
        {
            short x = (short) vh.getAcquire();
            assertEquals(x, (short)1, "getRelease short value");
        }

        // Opaque
        {
            short x = (short) vh.getOpaque();
            assertEquals(x, (short)1, "getOpaque short value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set((short)2);
        });

        checkUOE(() -> {
            vh.setVolatile((short)2);
        });

        checkUOE(() -> {
            vh.setRelease((short)2);
        });

        checkUOE(() -> {
            vh.setOpaque((short)2);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeVolatile((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeAcquire((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeRelease((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease((short)1, (short)2);
        });

        checkUOE(() -> {
            short o = (short) vh.getAndAdd((short)1);
        });

        checkUOE(() -> {
            short o = (short) vh.addAndGet((short)1);
        });
    }


    static void testInstanceField(VarHandleTestAccessShort recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, (short)1);
            short x = (short) vh.get(recv);
            assertEquals(x, (short)1, "set short value");
        }


        // Volatile
        {
            vh.setVolatile(recv, (short)2);
            short x = (short) vh.getVolatile(recv);
            assertEquals(x, (short)2, "setVolatile short value");
        }

        // Lazy
        {
            vh.setRelease(recv, (short)1);
            short x = (short) vh.getAcquire(recv);
            assertEquals(x, (short)1, "setRelease short value");
        }

        // Opaque
        {
            vh.setOpaque(recv, (short)2);
            short x = (short) vh.getOpaque(recv);
            assertEquals(x, (short)2, "setOpaque short value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessShort recv, VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeVolatile(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeAcquire(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeRelease(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, (short)1, (short)2);
        });

        checkUOE(() -> {
            short o = (short) vh.getAndAdd(recv, (short)1);
        });

        checkUOE(() -> {
            short o = (short) vh.addAndGet(recv, (short)1);
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set((short)1);
            short x = (short) vh.get();
            assertEquals(x, (short)1, "set short value");
        }


        // Volatile
        {
            vh.setVolatile((short)2);
            short x = (short) vh.getVolatile();
            assertEquals(x, (short)2, "setVolatile short value");
        }

        // Lazy
        {
            vh.setRelease((short)1);
            short x = (short) vh.getAcquire();
            assertEquals(x, (short)1, "setRelease short value");
        }

        // Opaque
        {
            vh.setOpaque((short)2);
            short x = (short) vh.getOpaque();
            assertEquals(x, (short)2, "setOpaque short value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeVolatile((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeAcquire((short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeRelease((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire((short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease((short)1, (short)2);
        });

        checkUOE(() -> {
            short o = (short) vh.getAndAdd((short)1);
        });

        checkUOE(() -> {
            short o = (short) vh.addAndGet((short)1);
        });
    }


    static void testArray(VarHandle vh) {
        short[] array = new short[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, (short)1);
                short x = (short) vh.get(array, i);
                assertEquals(x, (short)1, "get short value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, (short)2);
                short x = (short) vh.getVolatile(array, i);
                assertEquals(x, (short)2, "setVolatile short value");
            }

            // Lazy
            {
                vh.setRelease(array, i, (short)1);
                short x = (short) vh.getAcquire(array, i);
                assertEquals(x, (short)1, "setRelease short value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, (short)2);
                short x = (short) vh.getOpaque(array, i);
                assertEquals(x, (short)2, "setOpaque short value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        short[] array = new short[10];

        int i = 0;
        checkUOE(() -> {
            boolean r = vh.compareAndSet(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeVolatile(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeAcquire(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            short r = (short) vh.compareAndExchangeRelease(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, i, (short)1, (short)2);
        });

        checkUOE(() -> {
            short o = (short) vh.getAndAdd(array, i, (short)1);
        });

        checkUOE(() -> {
            short o = (short) vh.addAndGet(array, i, (short)1);
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        short[] array = new short[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                short x = (short) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, (short)1);
            });

            checkIOOBE(() -> {
                short x = (short) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, (short)1);
            });

            checkIOOBE(() -> {
                short x = (short) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, (short)1);
            });

            checkIOOBE(() -> {
                short x = (short) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, (short)1);
            });


        }
    }
}

