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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessBoolean
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessBoolean
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessBoolean
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessBoolean
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

public class VarHandleTestAccessBoolean extends VarHandleBaseTest {
    static final boolean static_final_v = true;

    static boolean static_v;

    final boolean final_v = true;

    boolean v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessBoolean.class, "final_v", boolean.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessBoolean.class, "v", boolean.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessBoolean.class, "static_final_v", boolean.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessBoolean.class, "static_v", boolean.class);

        vhArray = MethodHandles.arrayElementVarHandle(boolean[].class);
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
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessBoolean.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(boolean[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), boolean.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessBoolean.class, "final_v", boolean.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessBoolean.class, "v", boolean.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessBoolean.class, "static_final_v", boolean.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessBoolean.class, "static_v", boolean.class);
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
                                              vhStaticFinalField, VarHandleTestAccessBoolean::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessBoolean::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessBoolean::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessBoolean::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessBoolean::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessBoolean::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessBoolean::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessBoolean recv, VarHandle vh) {
        // Plain
        {
            boolean x = (boolean) vh.get(recv);
            assertEquals(x, true, "get boolean value");
        }


        // Volatile
        {
            boolean x = (boolean) vh.getVolatile(recv);
            assertEquals(x, true, "getVolatile boolean value");
        }

        // Lazy
        {
            boolean x = (boolean) vh.getAcquire(recv);
            assertEquals(x, true, "getRelease boolean value");
        }

        // Opaque
        {
            boolean x = (boolean) vh.getOpaque(recv);
            assertEquals(x, true, "getOpaque boolean value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessBoolean recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, false);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, false);
        });

        checkUOE(() -> {
            vh.setRelease(recv, false);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, false);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeVolatile(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeAcquire(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeRelease(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, true, false);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.addAndGet(recv, true);
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            boolean x = (boolean) vh.get();
            assertEquals(x, true, "get boolean value");
        }


        // Volatile
        {
            boolean x = (boolean) vh.getVolatile();
            assertEquals(x, true, "getVolatile boolean value");
        }

        // Lazy
        {
            boolean x = (boolean) vh.getAcquire();
            assertEquals(x, true, "getRelease boolean value");
        }

        // Opaque
        {
            boolean x = (boolean) vh.getOpaque();
            assertEquals(x, true, "getOpaque boolean value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(false);
        });

        checkUOE(() -> {
            vh.setVolatile(false);
        });

        checkUOE(() -> {
            vh.setRelease(false);
        });

        checkUOE(() -> {
            vh.setOpaque(false);
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeVolatile(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeAcquire(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeRelease(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(true, false);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.addAndGet(true);
        });
    }


    static void testInstanceField(VarHandleTestAccessBoolean recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, true);
            boolean x = (boolean) vh.get(recv);
            assertEquals(x, true, "set boolean value");
        }


        // Volatile
        {
            vh.setVolatile(recv, false);
            boolean x = (boolean) vh.getVolatile(recv);
            assertEquals(x, false, "setVolatile boolean value");
        }

        // Lazy
        {
            vh.setRelease(recv, true);
            boolean x = (boolean) vh.getAcquire(recv);
            assertEquals(x, true, "setRelease boolean value");
        }

        // Opaque
        {
            vh.setOpaque(recv, false);
            boolean x = (boolean) vh.getOpaque(recv);
            assertEquals(x, false, "setOpaque boolean value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessBoolean recv, VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeVolatile(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeAcquire(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeRelease(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, true, false);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.addAndGet(recv, true);
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(true);
            boolean x = (boolean) vh.get();
            assertEquals(x, true, "set boolean value");
        }


        // Volatile
        {
            vh.setVolatile(false);
            boolean x = (boolean) vh.getVolatile();
            assertEquals(x, false, "setVolatile boolean value");
        }

        // Lazy
        {
            vh.setRelease(true);
            boolean x = (boolean) vh.getAcquire();
            assertEquals(x, true, "setRelease boolean value");
        }

        // Opaque
        {
            vh.setOpaque(false);
            boolean x = (boolean) vh.getOpaque();
            assertEquals(x, false, "setOpaque boolean value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeVolatile(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeAcquire(true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeRelease(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(true, false);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.addAndGet(true);
        });
    }


    static void testArray(VarHandle vh) {
        boolean[] array = new boolean[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, true);
                boolean x = (boolean) vh.get(array, i);
                assertEquals(x, true, "get boolean value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, false);
                boolean x = (boolean) vh.getVolatile(array, i);
                assertEquals(x, false, "setVolatile boolean value");
            }

            // Lazy
            {
                vh.setRelease(array, i, true);
                boolean x = (boolean) vh.getAcquire(array, i);
                assertEquals(x, true, "setRelease boolean value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, false);
                boolean x = (boolean) vh.getOpaque(array, i);
                assertEquals(x, false, "setOpaque boolean value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        boolean[] array = new boolean[10];

        int i = 0;
        checkUOE(() -> {
            boolean r = vh.compareAndSet(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeVolatile(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeAcquire(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = (boolean) vh.compareAndExchangeRelease(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, i, true, false);
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, i, true, false);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(array, i, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.addAndGet(array, i, true);
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        boolean[] array = new boolean[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                boolean x = (boolean) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, true);
            });

            checkIOOBE(() -> {
                boolean x = (boolean) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, true);
            });

            checkIOOBE(() -> {
                boolean x = (boolean) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, true);
            });

            checkIOOBE(() -> {
                boolean x = (boolean) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, true);
            });


        }
    }
}

