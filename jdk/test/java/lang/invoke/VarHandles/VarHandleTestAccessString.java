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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessString
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessString
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessString
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessString
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

public class VarHandleTestAccessString extends VarHandleBaseTest {
    static final String static_final_v = "foo";

    static String static_v;

    final String final_v = "foo";

    String v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessString.class, "final_v", String.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessString.class, "v", String.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessString.class, "static_final_v", String.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessString.class, "static_v", String.class);

        vhArray = MethodHandles.arrayElementVarHandle(String[].class);
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

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.ADD_AND_GET));
    }


    @DataProvider
    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessString.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(String[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), String.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessString.class, "final_v", String.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessString.class, "v", String.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessString.class, "static_final_v", String.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessString.class, "static_v", String.class);
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
                                              vhStaticFinalField, VarHandleTestAccessString::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessString::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessString::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessString::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessString::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessString::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessString::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessString recv, VarHandle vh) {
        // Plain
        {
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "get String value");
        }


        // Volatile
        {
            String x = (String) vh.getVolatile(recv);
            assertEquals(x, "foo", "getVolatile String value");
        }

        // Lazy
        {
            String x = (String) vh.getAcquire(recv);
            assertEquals(x, "foo", "getRelease String value");
        }

        // Opaque
        {
            String x = (String) vh.getOpaque(recv);
            assertEquals(x, "foo", "getOpaque String value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessString recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, "bar");
        });

        checkUOE(() -> {
            vh.setVolatile(recv, "bar");
        });

        checkUOE(() -> {
            vh.setRelease(recv, "bar");
        });

        checkUOE(() -> {
            vh.setOpaque(recv, "bar");
        });


        checkUOE(() -> {
            String o = (String) vh.getAndAdd(recv, "foo");
        });

        checkUOE(() -> {
            String o = (String) vh.addAndGet(recv, "foo");
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            String x = (String) vh.get();
            assertEquals(x, "foo", "get String value");
        }


        // Volatile
        {
            String x = (String) vh.getVolatile();
            assertEquals(x, "foo", "getVolatile String value");
        }

        // Lazy
        {
            String x = (String) vh.getAcquire();
            assertEquals(x, "foo", "getRelease String value");
        }

        // Opaque
        {
            String x = (String) vh.getOpaque();
            assertEquals(x, "foo", "getOpaque String value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set("bar");
        });

        checkUOE(() -> {
            vh.setVolatile("bar");
        });

        checkUOE(() -> {
            vh.setRelease("bar");
        });

        checkUOE(() -> {
            vh.setOpaque("bar");
        });


        checkUOE(() -> {
            String o = (String) vh.getAndAdd("foo");
        });

        checkUOE(() -> {
            String o = (String) vh.addAndGet("foo");
        });
    }


    static void testInstanceField(VarHandleTestAccessString recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, "foo");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "set String value");
        }


        // Volatile
        {
            vh.setVolatile(recv, "bar");
            String x = (String) vh.getVolatile(recv);
            assertEquals(x, "bar", "setVolatile String value");
        }

        // Lazy
        {
            vh.setRelease(recv, "foo");
            String x = (String) vh.getAcquire(recv);
            assertEquals(x, "foo", "setRelease String value");
        }

        // Opaque
        {
            vh.setOpaque(recv, "bar");
            String x = (String) vh.getOpaque(recv);
            assertEquals(x, "bar", "setOpaque String value");
        }

        vh.set(recv, "foo");

        // Compare
        {
            boolean r = vh.compareAndSet(recv, "foo", "bar");
            assertEquals(r, true, "success compareAndSet String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "success compareAndSet String value");
        }

        {
            boolean r = vh.compareAndSet(recv, "foo", "baz");
            assertEquals(r, false, "failing compareAndSet String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "failing compareAndSet String value");
        }

        {
            String r = (String) vh.compareAndExchangeVolatile(recv, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeVolatile String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
        }

        {
            String r = (String) vh.compareAndExchangeVolatile(recv, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
        }

        {
            String r = (String) vh.compareAndExchangeAcquire(recv, "foo", "bar");
            assertEquals(r, "foo", "success compareAndExchangeAcquire String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
        }

        {
            String r = (String) vh.compareAndExchangeAcquire(recv, "foo", "baz");
            assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
        }

        {
            String r = (String) vh.compareAndExchangeRelease(recv, "bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeRelease String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "success compareAndExchangeRelease String value");
        }

        {
            String r = (String) vh.compareAndExchangeRelease(recv, "bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeRelease String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
        }

        {
            boolean r = vh.weakCompareAndSet(recv, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSet String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "weakCompareAndSet String value");
        }

        {
            boolean r = vh.weakCompareAndSetAcquire(recv, "bar", "foo");
            assertEquals(r, true, "weakCompareAndSetAcquire String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "weakCompareAndSetAcquire String");
        }

        {
            boolean r = vh.weakCompareAndSetRelease(recv, "foo", "bar");
            assertEquals(r, true, "weakCompareAndSetRelease String");
            String x = (String) vh.get(recv);
            assertEquals(x, "bar", "weakCompareAndSetRelease String");
        }

        // Compare set and get
        {
            String o = (String) vh.getAndSet(recv, "foo");
            assertEquals(o, "bar", "getAndSet String");
            String x = (String) vh.get(recv);
            assertEquals(x, "foo", "getAndSet String value");
        }

    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessString recv, VarHandle vh) {

        checkUOE(() -> {
            String o = (String) vh.getAndAdd(recv, "foo");
        });

        checkUOE(() -> {
            String o = (String) vh.addAndGet(recv, "foo");
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set("foo");
            String x = (String) vh.get();
            assertEquals(x, "foo", "set String value");
        }


        // Volatile
        {
            vh.setVolatile("bar");
            String x = (String) vh.getVolatile();
            assertEquals(x, "bar", "setVolatile String value");
        }

        // Lazy
        {
            vh.setRelease("foo");
            String x = (String) vh.getAcquire();
            assertEquals(x, "foo", "setRelease String value");
        }

        // Opaque
        {
            vh.setOpaque("bar");
            String x = (String) vh.getOpaque();
            assertEquals(x, "bar", "setOpaque String value");
        }

        vh.set("foo");

        // Compare
        {
            boolean r = vh.compareAndSet("foo", "bar");
            assertEquals(r, true, "success compareAndSet String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "success compareAndSet String value");
        }

        {
            boolean r = vh.compareAndSet("foo", "baz");
            assertEquals(r, false, "failing compareAndSet String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "failing compareAndSet String value");
        }

        {
            String r = (String) vh.compareAndExchangeVolatile("bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeVolatile String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
        }

        {
            String r = (String) vh.compareAndExchangeVolatile("bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
        }

        {
            String r = (String) vh.compareAndExchangeAcquire("foo", "bar");
            assertEquals(r, "foo", "success compareAndExchangeAcquire String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
        }

        {
            String r = (String) vh.compareAndExchangeAcquire("foo", "baz");
            assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
        }

        {
            String r = (String) vh.compareAndExchangeRelease("bar", "foo");
            assertEquals(r, "bar", "success compareAndExchangeRelease String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "success compareAndExchangeRelease String value");
        }

        {
            String r = (String) vh.compareAndExchangeRelease("bar", "baz");
            assertEquals(r, "foo", "failing compareAndExchangeRelease String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSet("foo", "bar");
            assertEquals(r, true, "weakCompareAndSet String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "weakCompareAndSet String value");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetAcquire("bar", "foo");
            assertEquals(r, true, "weakCompareAndSetAcquire String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "weakCompareAndSetAcquire String");
        }

        {
            boolean r = (boolean) vh.weakCompareAndSetRelease( "foo", "bar");
            assertEquals(r, true, "weakCompareAndSetRelease String");
            String x = (String) vh.get();
            assertEquals(x, "bar", "weakCompareAndSetRelease String");
        }

        // Compare set and get
        {
            String o = (String) vh.getAndSet( "foo");
            assertEquals(o, "bar", "getAndSet String");
            String x = (String) vh.get();
            assertEquals(x, "foo", "getAndSet String value");
        }

    }

    static void testStaticFieldUnsupported(VarHandle vh) {

        checkUOE(() -> {
            String o = (String) vh.getAndAdd("foo");
        });

        checkUOE(() -> {
            String o = (String) vh.addAndGet("foo");
        });
    }


    static void testArray(VarHandle vh) {
        String[] array = new String[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, "foo");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "get String value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, "bar");
                String x = (String) vh.getVolatile(array, i);
                assertEquals(x, "bar", "setVolatile String value");
            }

            // Lazy
            {
                vh.setRelease(array, i, "foo");
                String x = (String) vh.getAcquire(array, i);
                assertEquals(x, "foo", "setRelease String value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, "bar");
                String x = (String) vh.getOpaque(array, i);
                assertEquals(x, "bar", "setOpaque String value");
            }

            vh.set(array, i, "foo");

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, "foo", "bar");
                assertEquals(r, true, "success compareAndSet String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "success compareAndSet String value");
            }

            {
                boolean r = vh.compareAndSet(array, i, "foo", "baz");
                assertEquals(r, false, "failing compareAndSet String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "failing compareAndSet String value");
            }

            {
                String r = (String) vh.compareAndExchangeVolatile(array, i, "bar", "foo");
                assertEquals(r, "bar", "success compareAndExchangeVolatile String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "success compareAndExchangeVolatile String value");
            }

            {
                String r = (String) vh.compareAndExchangeVolatile(array, i, "bar", "baz");
                assertEquals(r, "foo", "failing compareAndExchangeVolatile String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "failing compareAndExchangeVolatile String value");
            }

            {
                String r = (String) vh.compareAndExchangeAcquire(array, i, "foo", "bar");
                assertEquals(r, "foo", "success compareAndExchangeAcquire String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "success compareAndExchangeAcquire String value");
            }

            {
                String r = (String) vh.compareAndExchangeAcquire(array, i, "foo", "baz");
                assertEquals(r, "bar", "failing compareAndExchangeAcquire String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "failing compareAndExchangeAcquire String value");
            }

            {
                String r = (String) vh.compareAndExchangeRelease(array, i, "bar", "foo");
                assertEquals(r, "bar", "success compareAndExchangeRelease String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "success compareAndExchangeRelease String value");
            }

            {
                String r = (String) vh.compareAndExchangeRelease(array, i, "bar", "baz");
                assertEquals(r, "foo", "failing compareAndExchangeRelease String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "failing compareAndExchangeRelease String value");
            }

            {
                boolean r = vh.weakCompareAndSet(array, i, "foo", "bar");
                assertEquals(r, true, "weakCompareAndSet String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "weakCompareAndSet String value");
            }

            {
                boolean r = vh.weakCompareAndSetAcquire(array, i, "bar", "foo");
                assertEquals(r, true, "weakCompareAndSetAcquire String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "weakCompareAndSetAcquire String");
            }

            {
                boolean r = vh.weakCompareAndSetRelease(array, i, "foo", "bar");
                assertEquals(r, true, "weakCompareAndSetRelease String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "bar", "weakCompareAndSetRelease String");
            }

            // Compare set and get
            {
                String o = (String) vh.getAndSet(array, i, "foo");
                assertEquals(o, "bar", "getAndSet String");
                String x = (String) vh.get(array, i);
                assertEquals(x, "foo", "getAndSet String value");
            }

        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        String[] array = new String[10];

        int i = 0;

        checkUOE(() -> {
            String o = (String) vh.getAndAdd(array, i, "foo");
        });

        checkUOE(() -> {
            String o = (String) vh.addAndGet(array, i, "foo");
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        String[] array = new String[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                String x = (String) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, "foo");
            });

            checkIOOBE(() -> {
                String x = (String) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, "foo");
            });

            checkIOOBE(() -> {
                String x = (String) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, "foo");
            });

            checkIOOBE(() -> {
                String x = (String) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, "foo");
            });

            checkIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, "foo", "bar");
            });

            checkIOOBE(() -> {
                String r = (String) vh.compareAndExchangeVolatile(array, ci, "bar", "foo");
            });

            checkIOOBE(() -> {
                String r = (String) vh.compareAndExchangeAcquire(array, ci, "bar", "foo");
            });

            checkIOOBE(() -> {
                String r = (String) vh.compareAndExchangeRelease(array, ci, "bar", "foo");
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, "foo", "bar");
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, "foo", "bar");
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, "foo", "bar");
            });

            checkIOOBE(() -> {
                String o = (String) vh.getAndSet(array, ci, "foo");
            });

        }
    }
}

