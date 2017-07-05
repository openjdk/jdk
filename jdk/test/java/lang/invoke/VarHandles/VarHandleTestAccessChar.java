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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessChar
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessChar
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessChar
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessChar
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

public class VarHandleTestAccessChar extends VarHandleBaseTest {
    static final char static_final_v = 'a';

    static char static_v;

    final char final_v = 'a';

    char v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessChar.class, "final_v", char.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessChar.class, "v", char.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessChar.class, "static_final_v", char.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessChar.class, "static_v", char.class);

        vhArray = MethodHandles.arrayElementVarHandle(char[].class);
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
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessChar.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(char[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), char.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessChar.class, "final_v", char.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessChar.class, "v", char.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessChar.class, "static_final_v", char.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessChar.class, "static_v", char.class);
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
                                              vhStaticFinalField, VarHandleTestAccessChar::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessChar::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessChar::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessChar::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessChar::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessChar::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessChar::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessChar recv, VarHandle vh) {
        // Plain
        {
            char x = (char) vh.get(recv);
            assertEquals(x, 'a', "get char value");
        }


        // Volatile
        {
            char x = (char) vh.getVolatile(recv);
            assertEquals(x, 'a', "getVolatile char value");
        }

        // Lazy
        {
            char x = (char) vh.getAcquire(recv);
            assertEquals(x, 'a', "getRelease char value");
        }

        // Opaque
        {
            char x = (char) vh.getOpaque(recv);
            assertEquals(x, 'a', "getOpaque char value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessChar recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, 'b');
        });

        checkUOE(() -> {
            vh.setVolatile(recv, 'b');
        });

        checkUOE(() -> {
            vh.setRelease(recv, 'b');
        });

        checkUOE(() -> {
            vh.setOpaque(recv, 'b');
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeVolatile(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeAcquire(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeRelease(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char o = (char) vh.getAndAdd(recv, 'a');
        });

        checkUOE(() -> {
            char o = (char) vh.addAndGet(recv, 'a');
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            char x = (char) vh.get();
            assertEquals(x, 'a', "get char value");
        }


        // Volatile
        {
            char x = (char) vh.getVolatile();
            assertEquals(x, 'a', "getVolatile char value");
        }

        // Lazy
        {
            char x = (char) vh.getAcquire();
            assertEquals(x, 'a', "getRelease char value");
        }

        // Opaque
        {
            char x = (char) vh.getOpaque();
            assertEquals(x, 'a', "getOpaque char value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set('b');
        });

        checkUOE(() -> {
            vh.setVolatile('b');
        });

        checkUOE(() -> {
            vh.setRelease('b');
        });

        checkUOE(() -> {
            vh.setOpaque('b');
        });

        checkUOE(() -> {
            boolean r = vh.compareAndSet('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeVolatile('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeAcquire('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeRelease('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease('a', 'b');
        });

        checkUOE(() -> {
            char o = (char) vh.getAndAdd('a');
        });

        checkUOE(() -> {
            char o = (char) vh.addAndGet('a');
        });
    }


    static void testInstanceField(VarHandleTestAccessChar recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, 'a');
            char x = (char) vh.get(recv);
            assertEquals(x, 'a', "set char value");
        }


        // Volatile
        {
            vh.setVolatile(recv, 'b');
            char x = (char) vh.getVolatile(recv);
            assertEquals(x, 'b', "setVolatile char value");
        }

        // Lazy
        {
            vh.setRelease(recv, 'a');
            char x = (char) vh.getAcquire(recv);
            assertEquals(x, 'a', "setRelease char value");
        }

        // Opaque
        {
            vh.setOpaque(recv, 'b');
            char x = (char) vh.getOpaque(recv);
            assertEquals(x, 'b', "setOpaque char value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessChar recv, VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeVolatile(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeAcquire(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeRelease(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, 'a', 'b');
        });

        checkUOE(() -> {
            char o = (char) vh.getAndAdd(recv, 'a');
        });

        checkUOE(() -> {
            char o = (char) vh.addAndGet(recv, 'a');
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set('a');
            char x = (char) vh.get();
            assertEquals(x, 'a', "set char value");
        }


        // Volatile
        {
            vh.setVolatile('b');
            char x = (char) vh.getVolatile();
            assertEquals(x, 'b', "setVolatile char value");
        }

        // Lazy
        {
            vh.setRelease('a');
            char x = (char) vh.getAcquire();
            assertEquals(x, 'a', "setRelease char value");
        }

        // Opaque
        {
            vh.setOpaque('b');
            char x = (char) vh.getOpaque();
            assertEquals(x, 'b', "setOpaque char value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            boolean r = vh.compareAndSet('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeVolatile('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeAcquire('a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeRelease('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire('a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease('a', 'b');
        });

        checkUOE(() -> {
            char o = (char) vh.getAndAdd('a');
        });

        checkUOE(() -> {
            char o = (char) vh.addAndGet('a');
        });
    }


    static void testArray(VarHandle vh) {
        char[] array = new char[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, 'a');
                char x = (char) vh.get(array, i);
                assertEquals(x, 'a', "get char value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, 'b');
                char x = (char) vh.getVolatile(array, i);
                assertEquals(x, 'b', "setVolatile char value");
            }

            // Lazy
            {
                vh.setRelease(array, i, 'a');
                char x = (char) vh.getAcquire(array, i);
                assertEquals(x, 'a', "setRelease char value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, 'b');
                char x = (char) vh.getOpaque(array, i);
                assertEquals(x, 'b', "setOpaque char value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        char[] array = new char[10];

        int i = 0;
        checkUOE(() -> {
            boolean r = vh.compareAndSet(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeVolatile(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeAcquire(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            char r = (char) vh.compareAndExchangeRelease(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSet(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetVolatile(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, i, 'a', 'b');
        });

        checkUOE(() -> {
            char o = (char) vh.getAndAdd(array, i, 'a');
        });

        checkUOE(() -> {
            char o = (char) vh.addAndGet(array, i, 'a');
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        char[] array = new char[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                char x = (char) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, 'a');
            });

            checkIOOBE(() -> {
                char x = (char) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, 'a');
            });

            checkIOOBE(() -> {
                char x = (char) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, 'a');
            });

            checkIOOBE(() -> {
                char x = (char) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, 'a');
            });


        }
    }
}

