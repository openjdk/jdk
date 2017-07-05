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
 * @run testng/othervm -Diters=10    -Xint                   VarHandleTestAccessFloat
 * @run testng/othervm -Diters=20000 -XX:TieredStopAtLevel=1 VarHandleTestAccessFloat
 * @run testng/othervm -Diters=20000                         VarHandleTestAccessFloat
 * @run testng/othervm -Diters=20000 -XX:-TieredCompilation  VarHandleTestAccessFloat
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

public class VarHandleTestAccessFloat extends VarHandleBaseTest {
    static final float static_final_v = 1.0f;

    static float static_v;

    final float final_v = 1.0f;

    float v;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    @BeforeClass
    public void setup() throws Exception {
        vhFinalField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessFloat.class, "final_v", float.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessFloat.class, "v", float.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessFloat.class, "static_final_v", float.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessFloat.class, "static_v", float.class);

        vhArray = MethodHandles.arrayElementVarHandle(float[].class);
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
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.COMPARE_AND_EXCHANGE));
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
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessFloat.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(float[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @Test(dataProvider = "typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(vh.varType(), float.class);

        assertEquals(vh.coordinateTypes(), pts);

        testTypes(vh);
    }


    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessFloat.class, "final_v", float.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessFloat.class, "v", float.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessFloat.class, "static_final_v", float.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessFloat.class, "static_v", float.class);
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
                                              vhStaticFinalField, VarHandleTestAccessFloat::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessFloat::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessFloat::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessFloat::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessFloat::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessFloat::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessFloat::testArrayIndexOutOfBounds,
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




    static void testInstanceFinalField(VarHandleTestAccessFloat recv, VarHandle vh) {
        // Plain
        {
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "get float value");
        }


        // Volatile
        {
            float x = (float) vh.getVolatile(recv);
            assertEquals(x, 1.0f, "getVolatile float value");
        }

        // Lazy
        {
            float x = (float) vh.getAcquire(recv);
            assertEquals(x, 1.0f, "getRelease float value");
        }

        // Opaque
        {
            float x = (float) vh.getOpaque(recv);
            assertEquals(x, 1.0f, "getOpaque float value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessFloat recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, 2.0f);
        });

        checkUOE(() -> {
            vh.setVolatile(recv, 2.0f);
        });

        checkUOE(() -> {
            vh.setRelease(recv, 2.0f);
        });

        checkUOE(() -> {
            vh.setOpaque(recv, 2.0f);
        });


    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "get float value");
        }


        // Volatile
        {
            float x = (float) vh.getVolatile();
            assertEquals(x, 1.0f, "getVolatile float value");
        }

        // Lazy
        {
            float x = (float) vh.getAcquire();
            assertEquals(x, 1.0f, "getRelease float value");
        }

        // Opaque
        {
            float x = (float) vh.getOpaque();
            assertEquals(x, 1.0f, "getOpaque float value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(2.0f);
        });

        checkUOE(() -> {
            vh.setVolatile(2.0f);
        });

        checkUOE(() -> {
            vh.setRelease(2.0f);
        });

        checkUOE(() -> {
            vh.setOpaque(2.0f);
        });


    }


    static void testInstanceField(VarHandleTestAccessFloat recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, 1.0f);
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "set float value");
        }


        // Volatile
        {
            vh.setVolatile(recv, 2.0f);
            float x = (float) vh.getVolatile(recv);
            assertEquals(x, 2.0f, "setVolatile float value");
        }

        // Lazy
        {
            vh.setRelease(recv, 1.0f);
            float x = (float) vh.getAcquire(recv);
            assertEquals(x, 1.0f, "setRelease float value");
        }

        // Opaque
        {
            vh.setOpaque(recv, 2.0f);
            float x = (float) vh.getOpaque(recv);
            assertEquals(x, 2.0f, "setOpaque float value");
        }

        vh.set(recv, 1.0f);

        // Compare
        {
            boolean r = vh.compareAndSet(recv, 1.0f, 2.0f);
            assertEquals(r, true, "success compareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "success compareAndSet float value");
        }

        {
            boolean r = vh.compareAndSet(recv, 1.0f, 3.0f);
            assertEquals(r, false, "failing compareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "failing compareAndSet float value");
        }

        {
            float r = (float) vh.compareAndExchange(recv, 2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchange float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "success compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchange(recv, 2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchange float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "failing compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(recv, 1.0f, 2.0f);
            assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "success compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(recv, 1.0f, 3.0f);
            assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "failing compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(recv, 2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "success compareAndExchangeRelease float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(recv, 2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "failing compareAndExchangeRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(recv, 1.0f, 2.0f);
            }
            assertEquals(success, true, "weakCompareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "weakCompareAndSet float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(recv, 2.0f, 1.0f);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "weakCompareAndSetAcquire float");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(recv, 1.0f, 2.0f);
            }
            assertEquals(success, true, "weakCompareAndSetRelease float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "weakCompareAndSetRelease float");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetVolatile(recv, 2.0f, 1.0f);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile float");
            float x = (float) vh.get(recv);
            assertEquals(x, 1.0f, "weakCompareAndSetVolatile float value");
        }

        // Compare set and get
        {
            float o = (float) vh.getAndSet(recv, 2.0f);
            assertEquals(o, 1.0f, "getAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(x, 2.0f, "getAndSet float value");
        }

        vh.set(recv, 1.0f);

        // get and add, add and get
        {
            float o = (float) vh.getAndAdd(recv, 3.0f);
            assertEquals(o, 1.0f, "getAndAdd float");
            float c = (float) vh.addAndGet(recv, 3.0f);
            assertEquals(c, (float)(1.0f + 3.0f + 3.0f), "getAndAdd float value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessFloat recv, VarHandle vh) {

    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(1.0f);
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "set float value");
        }


        // Volatile
        {
            vh.setVolatile(2.0f);
            float x = (float) vh.getVolatile();
            assertEquals(x, 2.0f, "setVolatile float value");
        }

        // Lazy
        {
            vh.setRelease(1.0f);
            float x = (float) vh.getAcquire();
            assertEquals(x, 1.0f, "setRelease float value");
        }

        // Opaque
        {
            vh.setOpaque(2.0f);
            float x = (float) vh.getOpaque();
            assertEquals(x, 2.0f, "setOpaque float value");
        }

        vh.set(1.0f);

        // Compare
        {
            boolean r = vh.compareAndSet(1.0f, 2.0f);
            assertEquals(r, true, "success compareAndSet float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "success compareAndSet float value");
        }

        {
            boolean r = vh.compareAndSet(1.0f, 3.0f);
            assertEquals(r, false, "failing compareAndSet float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "failing compareAndSet float value");
        }

        {
            float r = (float) vh.compareAndExchange(2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchange float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "success compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchange(2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchange float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "failing compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(1.0f, 2.0f);
            assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "success compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(1.0f, 3.0f);
            assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "failing compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "success compareAndExchangeRelease float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "failing compareAndExchangeRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(1.0f, 2.0f);
            }
            assertEquals(success, true, "weakCompareAndSet float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "weakCompareAndSet float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(2.0f, 1.0f);
            }
            assertEquals(success, true, "weakCompareAndSetAcquire float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "weakCompareAndSetAcquire float");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(1.0f, 2.0f);
            }
            assertEquals(success, true, "weakCompareAndSetRelease float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "weakCompareAndSetRelease float");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(2.0f, 1.0f);
            }
            assertEquals(success, true, "weakCompareAndSetVolatile float");
            float x = (float) vh.get();
            assertEquals(x, 1.0f, "weakCompareAndSetVolatile float");
        }

        // Compare set and get
        {
            float o = (float) vh.getAndSet(2.0f);
            assertEquals(o, 1.0f, "getAndSet float");
            float x = (float) vh.get();
            assertEquals(x, 2.0f, "getAndSet float value");
        }

        vh.set(1.0f);

        // get and add, add and get
        {
            float o = (float) vh.getAndAdd( 3.0f);
            assertEquals(o, 1.0f, "getAndAdd float");
            float c = (float) vh.addAndGet(3.0f);
            assertEquals(c, (float)(1.0f + 3.0f + 3.0f), "getAndAdd float value");
        }
    }

    static void testStaticFieldUnsupported(VarHandle vh) {

    }


    static void testArray(VarHandle vh) {
        float[] array = new float[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, 1.0f);
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "get float value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, 2.0f);
                float x = (float) vh.getVolatile(array, i);
                assertEquals(x, 2.0f, "setVolatile float value");
            }

            // Lazy
            {
                vh.setRelease(array, i, 1.0f);
                float x = (float) vh.getAcquire(array, i);
                assertEquals(x, 1.0f, "setRelease float value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, 2.0f);
                float x = (float) vh.getOpaque(array, i);
                assertEquals(x, 2.0f, "setOpaque float value");
            }

            vh.set(array, i, 1.0f);

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, 1.0f, 2.0f);
                assertEquals(r, true, "success compareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "success compareAndSet float value");
            }

            {
                boolean r = vh.compareAndSet(array, i, 1.0f, 3.0f);
                assertEquals(r, false, "failing compareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "failing compareAndSet float value");
            }

            {
                float r = (float) vh.compareAndExchange(array, i, 2.0f, 1.0f);
                assertEquals(r, 2.0f, "success compareAndExchange float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "success compareAndExchange float value");
            }

            {
                float r = (float) vh.compareAndExchange(array, i, 2.0f, 3.0f);
                assertEquals(r, 1.0f, "failing compareAndExchange float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "failing compareAndExchange float value");
            }

            {
                float r = (float) vh.compareAndExchangeAcquire(array, i, 1.0f, 2.0f);
                assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "success compareAndExchangeAcquire float value");
            }

            {
                float r = (float) vh.compareAndExchangeAcquire(array, i, 1.0f, 3.0f);
                assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "failing compareAndExchangeAcquire float value");
            }

            {
                float r = (float) vh.compareAndExchangeRelease(array, i, 2.0f, 1.0f);
                assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "success compareAndExchangeRelease float value");
            }

            {
                float r = (float) vh.compareAndExchangeRelease(array, i, 2.0f, 3.0f);
                assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "failing compareAndExchangeRelease float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSet(array, i, 1.0f, 2.0f);
                }
                assertEquals(success, true, "weakCompareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "weakCompareAndSet float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetAcquire(array, i, 2.0f, 1.0f);
                }
                assertEquals(success, true, "weakCompareAndSetAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "weakCompareAndSetAcquire float");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetRelease(array, i, 1.0f, 2.0f);
                }
                assertEquals(success, true, "weakCompareAndSetRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "weakCompareAndSetRelease float");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetVolatile(array, i, 2.0f, 1.0f);
                }
                assertEquals(success, true, "weakCompareAndSetVolatile float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 1.0f, "weakCompareAndSetVolatile float");
            }

            // Compare set and get
            {
                float o = (float) vh.getAndSet(array, i, 2.0f);
                assertEquals(o, 1.0f, "getAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(x, 2.0f, "getAndSet float value");
            }

            vh.set(array, i, 1.0f);

            // get and add, add and get
            {
                float o = (float) vh.getAndAdd(array, i, 3.0f);
                assertEquals(o, 1.0f, "getAndAdd float");
                float c = (float) vh.addAndGet(array, i, 3.0f);
                assertEquals(c, (float)(1.0f + 3.0f + 3.0f), "getAndAdd float value");
            }
        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        float[] array = new float[10];

        int i = 0;

    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        float[] array = new float[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkIOOBE(() -> {
                float x = (float) vh.get(array, ci);
            });

            checkIOOBE(() -> {
                vh.set(array, ci, 1.0f);
            });

            checkIOOBE(() -> {
                float x = (float) vh.getVolatile(array, ci);
            });

            checkIOOBE(() -> {
                vh.setVolatile(array, ci, 1.0f);
            });

            checkIOOBE(() -> {
                float x = (float) vh.getAcquire(array, ci);
            });

            checkIOOBE(() -> {
                vh.setRelease(array, ci, 1.0f);
            });

            checkIOOBE(() -> {
                float x = (float) vh.getOpaque(array, ci);
            });

            checkIOOBE(() -> {
                vh.setOpaque(array, ci, 1.0f);
            });

            checkIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, 1.0f, 2.0f);
            });

            checkIOOBE(() -> {
                float r = (float) vh.compareAndExchange(array, ci, 2.0f, 1.0f);
            });

            checkIOOBE(() -> {
                float r = (float) vh.compareAndExchangeAcquire(array, ci, 2.0f, 1.0f);
            });

            checkIOOBE(() -> {
                float r = (float) vh.compareAndExchangeRelease(array, ci, 2.0f, 1.0f);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, 1.0f, 2.0f);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetVolatile(array, ci, 1.0f, 2.0f);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, 1.0f, 2.0f);
            });

            checkIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, 1.0f, 2.0f);
            });

            checkIOOBE(() -> {
                float o = (float) vh.getAndSet(array, ci, 1.0f);
            });

            checkIOOBE(() -> {
                float o = (float) vh.getAndAdd(array, ci, 3.0f);
            });

            checkIOOBE(() -> {
                float o = (float) vh.addAndGet(array, ci, 3.0f);
            });
        }
    }
}

