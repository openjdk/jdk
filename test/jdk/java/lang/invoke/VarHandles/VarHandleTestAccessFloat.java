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
 * @run junit/othervm -Diters=10   -Xint                                                   VarHandleTestAccessFloat
 *
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop sets to 2000 iterations
 *          to hit compilation thresholds
 *
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:TieredStopAtLevel=1 VarHandleTestAccessFloat
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1                         VarHandleTestAccessFloat
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:-TieredCompilation  VarHandleTestAccessFloat
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestAccessFloat extends VarHandleBaseTest {
    static final float static_final_v = 1.0f;

    static float static_v;

    final float final_v = 1.0f;

    float v;

    static final float static_final_v2 = 1.0f;

    static float static_v2;

    final float final_v2 = 1.0f;

    float v2;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;


    VarHandle[] allocate(boolean same) {
        List<VarHandle> vhs = new ArrayList<>();

        String postfix = same ? "" : "2";
        VarHandle vh;
        try {
            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessFloat.class, "final_v" + postfix, float.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessFloat.class, "v" + postfix, float.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessFloat.class, "static_final_v" + postfix, float.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessFloat.class, "static_v" + postfix, float.class);
            vhs.add(vh);

            if (same) {
                vh = MethodHandles.arrayElementVarHandle(float[].class);
            }
            else {
                vh = MethodHandles.arrayElementVarHandle(String[].class);
            }
            vhs.add(vh);
        } catch (Exception e) {
            throw new InternalError(e);
        }
        return vhs.toArray(new VarHandle[0]);
    }

    @BeforeAll
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

    public Object[][] varHandlesProvider() throws Exception {
        List<VarHandle> vhs = new ArrayList<>();
        vhs.add(vhField);
        vhs.add(vhStaticField);
        vhs.add(vhArray);

        return vhs.stream().map(tc -> new Object[]{tc}).toArray(Object[][]::new);
    }

    @Test
    public void testEquals() {
        VarHandle[] vhs1 = allocate(true);
        VarHandle[] vhs2 = allocate(true);

        for (int i = 0; i < vhs1.length; i++) {
            for (int j = 0; j < vhs1.length; j++) {
                if (i != j) {
                    assertNotEquals(vhs1[i], vhs1[j]);
                    assertNotEquals(vhs1[i], vhs2[j]);
                }
            }
        }

        VarHandle[] vhs3 = allocate(false);
        for (int i = 0; i < vhs1.length; i++) {
            assertNotEquals(vhs1[i], vhs3[i]);
        }
    }

    @ParameterizedTest
    @MethodSource("varHandlesProvider")
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
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_PLAIN));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.WEAK_COMPARE_AND_SET_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_SET_RELEASE));

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD_RELEASE));

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR_RELEASE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND_RELEASE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR_RELEASE));
    }

    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessFloat.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(float[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(float.class, vh.varType());

        assertEquals(pts, vh.coordinateTypes());

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

    @ParameterizedTest
    @MethodSource("accessTestCaseProvider")
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
            assertEquals(1.0f, x, "get float value");
        }


        // Volatile
        {
            float x = (float) vh.getVolatile(recv);
            assertEquals(1.0f, x, "getVolatile float value");
        }

        // Lazy
        {
            float x = (float) vh.getAcquire(recv);
            assertEquals(1.0f, x, "getRelease float value");
        }

        // Opaque
        {
            float x = (float) vh.getOpaque(recv);
            assertEquals(1.0f, x, "getOpaque float value");
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



        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOr(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrRelease(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAnd(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndRelease(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXor(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorRelease(recv, 1.0f);
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            float x = (float) vh.get();
            assertEquals(1.0f, x, "get float value");
        }


        // Volatile
        {
            float x = (float) vh.getVolatile();
            assertEquals(1.0f, x, "getVolatile float value");
        }

        // Lazy
        {
            float x = (float) vh.getAcquire();
            assertEquals(1.0f, x, "getRelease float value");
        }

        // Opaque
        {
            float x = (float) vh.getOpaque();
            assertEquals(1.0f, x, "getOpaque float value");
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



        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOr(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrRelease(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAnd(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndRelease(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXor(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorRelease(1.0f);
        });
    }


    static void testInstanceField(VarHandleTestAccessFloat recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, 1.0f);
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "set float value");
        }


        // Volatile
        {
            vh.setVolatile(recv, 2.0f);
            float x = (float) vh.getVolatile(recv);
            assertEquals(2.0f, x, "setVolatile float value");
        }

        // Lazy
        {
            vh.setRelease(recv, 1.0f);
            float x = (float) vh.getAcquire(recv);
            assertEquals(1.0f, x, "setRelease float value");
        }

        // Opaque
        {
            vh.setOpaque(recv, 2.0f);
            float x = (float) vh.getOpaque(recv);
            assertEquals(2.0f, x, "setOpaque float value");
        }

        vh.set(recv, 1.0f);

        // Compare
        {
            boolean r = vh.compareAndSet(recv, 1.0f, 2.0f);
            assertEquals(r, true, "success compareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "success compareAndSet float value");
        }

        {
            boolean r = vh.compareAndSet(recv, 1.0f, 3.0f);
            assertEquals(r, false, "failing compareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "failing compareAndSet float value");
        }

        {
            float r = (float) vh.compareAndExchange(recv, 2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchange float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "success compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchange(recv, 2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchange float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "failing compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(recv, 1.0f, 2.0f);
            assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "success compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(recv, 1.0f, 3.0f);
            assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "failing compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(recv, 2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "success compareAndExchangeRelease float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(recv, 2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "failing compareAndExchangeRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(recv, 1.0f, 2.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "success weakCompareAndSetPlain float value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(recv, 1.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetPlain float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "failing weakCompareAndSetPlain float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(recv, 2.0f, 1.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "success weakCompareAndSetAcquire float");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(recv, 2.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "failing weakCompareAndSetAcquire float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(recv, 1.0f, 2.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "success weakCompareAndSetRelease float");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(recv, 1.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetRelease float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "failing weakCompareAndSetRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(recv, 2.0f, 1.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "success weakCompareAndSet float value");
        }

        {
            boolean success = vh.weakCompareAndSet(recv, 2.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(1.0f, x, "failing weakCompareAndSet float value");
        }

        // Compare set and get
        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndSet(recv, 2.0f);
            assertEquals(1.0f, o, "getAndSet float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "getAndSet float value");
        }

        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndSetAcquire(recv, 2.0f);
            assertEquals(1.0f, o, "getAndSetAcquire float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "getAndSetAcquire float value");
        }

        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndSetRelease(recv, 2.0f);
            assertEquals(1.0f, o, "getAndSetRelease float");
            float x = (float) vh.get(recv);
            assertEquals(2.0f, x, "getAndSetRelease float value");
        }

        // get and add, add and get
        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndAdd(recv, 2.0f);
            assertEquals(1.0f, o, "getAndAdd float");
            float x = (float) vh.get(recv);
            assertEquals((float)(1.0f + 2.0f), x, "getAndAdd float value");
        }

        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndAddAcquire(recv, 2.0f);
            assertEquals(1.0f, o, "getAndAddAcquire float");
            float x = (float) vh.get(recv);
            assertEquals((float)(1.0f + 2.0f), x, "getAndAddAcquire float value");
        }

        {
            vh.set(recv, 1.0f);

            float o = (float) vh.getAndAddRelease(recv, 2.0f);
            assertEquals(1.0f, o, "getAndAddReleasefloat");
            float x = (float) vh.get(recv);
            assertEquals((float)(1.0f + 2.0f), x, "getAndAddRelease float value");
        }

    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessFloat recv, VarHandle vh) {


        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOr(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrRelease(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAnd(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndRelease(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXor(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorAcquire(recv, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorRelease(recv, 1.0f);
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(1.0f);
            float x = (float) vh.get();
            assertEquals(1.0f, x, "set float value");
        }


        // Volatile
        {
            vh.setVolatile(2.0f);
            float x = (float) vh.getVolatile();
            assertEquals(2.0f, x, "setVolatile float value");
        }

        // Lazy
        {
            vh.setRelease(1.0f);
            float x = (float) vh.getAcquire();
            assertEquals(1.0f, x, "setRelease float value");
        }

        // Opaque
        {
            vh.setOpaque(2.0f);
            float x = (float) vh.getOpaque();
            assertEquals(2.0f, x, "setOpaque float value");
        }

        vh.set(1.0f);

        // Compare
        {
            boolean r = vh.compareAndSet(1.0f, 2.0f);
            assertEquals(r, true, "success compareAndSet float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "success compareAndSet float value");
        }

        {
            boolean r = vh.compareAndSet(1.0f, 3.0f);
            assertEquals(r, false, "failing compareAndSet float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "failing compareAndSet float value");
        }

        {
            float r = (float) vh.compareAndExchange(2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchange float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "success compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchange(2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchange float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "failing compareAndExchange float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(1.0f, 2.0f);
            assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "success compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeAcquire(1.0f, 3.0f);
            assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "failing compareAndExchangeAcquire float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(2.0f, 1.0f);
            assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "success compareAndExchangeRelease float value");
        }

        {
            float r = (float) vh.compareAndExchangeRelease(2.0f, 3.0f);
            assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "failing compareAndExchangeRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(1.0f, 2.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "success weakCompareAndSetPlain float value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(1.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetPlain float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "failing weakCompareAndSetPlain float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(2.0f, 1.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "success weakCompareAndSetAcquire float");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(2.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetAcquire float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "failing weakCompareAndSetAcquire float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(1.0f, 2.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "success weakCompareAndSetRelease float");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(1.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSetRelease float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "failing weakCompareAndSetRelease float value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(2.0f, 1.0f);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "success weakCompareAndSet float");
        }

        {
            boolean success = vh.weakCompareAndSet(2.0f, 3.0f);
            assertEquals(success, false, "failing weakCompareAndSet float");
            float x = (float) vh.get();
            assertEquals(1.0f, x, "failing weakCompareAndSet float value");
        }

        // Compare set and get
        {
            vh.set(1.0f);

            float o = (float) vh.getAndSet(2.0f);
            assertEquals(1.0f, o, "getAndSet float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "getAndSet float value");
        }

        {
            vh.set(1.0f);

            float o = (float) vh.getAndSetAcquire(2.0f);
            assertEquals(1.0f, o, "getAndSetAcquire float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "getAndSetAcquire float value");
        }

        {
            vh.set(1.0f);

            float o = (float) vh.getAndSetRelease(2.0f);
            assertEquals(1.0f, o, "getAndSetRelease float");
            float x = (float) vh.get();
            assertEquals(2.0f, x, "getAndSetRelease float value");
        }

        // get and add, add and get
        {
            vh.set(1.0f);

            float o = (float) vh.getAndAdd(2.0f);
            assertEquals(1.0f, o, "getAndAdd float");
            float x = (float) vh.get();
            assertEquals((float)(1.0f + 2.0f), x, "getAndAdd float value");
        }

        {
            vh.set(1.0f);

            float o = (float) vh.getAndAddAcquire(2.0f);
            assertEquals(1.0f, o, "getAndAddAcquire float");
            float x = (float) vh.get();
            assertEquals((float)(1.0f + 2.0f), x, "getAndAddAcquire float value");
        }

        {
            vh.set(1.0f);

            float o = (float) vh.getAndAddRelease(2.0f);
            assertEquals(1.0f, o, "getAndAddReleasefloat");
            float x = (float) vh.get();
            assertEquals((float)(1.0f + 2.0f), x, "getAndAddRelease float value");
        }

    }

    static void testStaticFieldUnsupported(VarHandle vh) {


        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOr(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrRelease(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAnd(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndRelease(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXor(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorAcquire(1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorRelease(1.0f);
        });
    }


    static void testArray(VarHandle vh) {
        float[] array = new float[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, 1.0f);
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "get float value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, 2.0f);
                float x = (float) vh.getVolatile(array, i);
                assertEquals(2.0f, x, "setVolatile float value");
            }

            // Lazy
            {
                vh.setRelease(array, i, 1.0f);
                float x = (float) vh.getAcquire(array, i);
                assertEquals(1.0f, x, "setRelease float value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, 2.0f);
                float x = (float) vh.getOpaque(array, i);
                assertEquals(2.0f, x, "setOpaque float value");
            }

            vh.set(array, i, 1.0f);

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, 1.0f, 2.0f);
                assertEquals(r, true, "success compareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "success compareAndSet float value");
            }

            {
                boolean r = vh.compareAndSet(array, i, 1.0f, 3.0f);
                assertEquals(r, false, "failing compareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "failing compareAndSet float value");
            }

            {
                float r = (float) vh.compareAndExchange(array, i, 2.0f, 1.0f);
                assertEquals(r, 2.0f, "success compareAndExchange float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "success compareAndExchange float value");
            }

            {
                float r = (float) vh.compareAndExchange(array, i, 2.0f, 3.0f);
                assertEquals(r, 1.0f, "failing compareAndExchange float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "failing compareAndExchange float value");
            }

            {
                float r = (float) vh.compareAndExchangeAcquire(array, i, 1.0f, 2.0f);
                assertEquals(r, 1.0f, "success compareAndExchangeAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "success compareAndExchangeAcquire float value");
            }

            {
                float r = (float) vh.compareAndExchangeAcquire(array, i, 1.0f, 3.0f);
                assertEquals(r, 2.0f, "failing compareAndExchangeAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "failing compareAndExchangeAcquire float value");
            }

            {
                float r = (float) vh.compareAndExchangeRelease(array, i, 2.0f, 1.0f);
                assertEquals(r, 2.0f, "success compareAndExchangeRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "success compareAndExchangeRelease float value");
            }

            {
                float r = (float) vh.compareAndExchangeRelease(array, i, 2.0f, 3.0f);
                assertEquals(r, 1.0f, "failing compareAndExchangeRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "failing compareAndExchangeRelease float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetPlain(array, i, 1.0f, 2.0f);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "success weakCompareAndSetPlain float value");
            }

            {
                boolean success = vh.weakCompareAndSetPlain(array, i, 1.0f, 3.0f);
                assertEquals(success, false, "failing weakCompareAndSetPlain float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "failing weakCompareAndSetPlain float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetAcquire(array, i, 2.0f, 1.0f);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "success weakCompareAndSetAcquire float");
            }

            {
                boolean success = vh.weakCompareAndSetAcquire(array, i, 2.0f, 3.0f);
                assertEquals(success, false, "failing weakCompareAndSetAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "failing weakCompareAndSetAcquire float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetRelease(array, i, 1.0f, 2.0f);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "success weakCompareAndSetRelease float");
            }

            {
                boolean success = vh.weakCompareAndSetRelease(array, i, 1.0f, 3.0f);
                assertEquals(success, false, "failing weakCompareAndSetRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "failing weakCompareAndSetRelease float value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSet(array, i, 2.0f, 1.0f);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "success weakCompareAndSet float");
            }

            {
                boolean success = vh.weakCompareAndSet(array, i, 2.0f, 3.0f);
                assertEquals(success, false, "failing weakCompareAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(1.0f, x, "failing weakCompareAndSet float value");
            }

            // Compare set and get
            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndSet(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndSet float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "getAndSet float value");
            }

            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndSetAcquire(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndSetAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "getAndSetAcquire float value");
            }

            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndSetRelease(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndSetRelease float");
                float x = (float) vh.get(array, i);
                assertEquals(2.0f, x, "getAndSetRelease float value");
            }

            // get and add, add and get
            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndAdd(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndAdd float");
                float x = (float) vh.get(array, i);
                assertEquals((float)(1.0f + 2.0f), x, "getAndAdd float value");
            }

            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndAddAcquire(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndAddAcquire float");
                float x = (float) vh.get(array, i);
                assertEquals((float)(1.0f + 2.0f), x, "getAndAddAcquire float value");
            }

            {
                vh.set(array, i, 1.0f);

                float o = (float) vh.getAndAddRelease(array, i, 2.0f);
                assertEquals(1.0f, o, "getAndAddReleasefloat");
                float x = (float) vh.get(array, i);
                assertEquals((float)(1.0f + 2.0f), x, "getAndAddRelease float value");
            }

        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        float[] array = new float[10];

        int i = 0;


        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOr(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrAcquire(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseOrRelease(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAnd(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndAcquire(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseAndRelease(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXor(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorAcquire(array, i, 1.0f);
        });

        checkUOE(() -> {
            float o = (float) vh.getAndBitwiseXorRelease(array, i, 1.0f);
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        float[] array = new float[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkAIOOBE(() -> {
                float x = (float) vh.get(array, ci);
            });

            checkAIOOBE(() -> {
                vh.set(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float x = (float) vh.getVolatile(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setVolatile(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float x = (float) vh.getAcquire(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setRelease(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float x = (float) vh.getOpaque(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setOpaque(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, 1.0f, 2.0f);
            });

            checkAIOOBE(() -> {
                float r = (float) vh.compareAndExchange(array, ci, 2.0f, 1.0f);
            });

            checkAIOOBE(() -> {
                float r = (float) vh.compareAndExchangeAcquire(array, ci, 2.0f, 1.0f);
            });

            checkAIOOBE(() -> {
                float r = (float) vh.compareAndExchangeRelease(array, ci, 2.0f, 1.0f);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetPlain(array, ci, 1.0f, 2.0f);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, 1.0f, 2.0f);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, 1.0f, 2.0f);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, 1.0f, 2.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndSet(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndSetAcquire(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndSetRelease(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndAdd(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndAddAcquire(array, ci, 1.0f);
            });

            checkAIOOBE(() -> {
                float o = (float) vh.getAndAddRelease(array, ci, 1.0f);
            });

        }
    }

}

