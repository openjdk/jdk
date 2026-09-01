/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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

// -- This file was mechanically generated: Do not edit! -- //

/*
 * @test
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 *          java.base/jdk.internal.value
 * @run junit/othervm -Diters=10   -Xint                                                   VarHandleTestAccessNullRestrictedValue
 *
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop set to 2000 iterations
 *          hits compilation thresholds
 *
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:TieredStopAtLevel=1 VarHandleTestAccessNullRestrictedValue
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1                         VarHandleTestAccessNullRestrictedValue
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:-TieredCompilation  VarHandleTestAccessNullRestrictedValue
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class VarHandleTestAccessNullRestrictedValue extends VarHandleBaseTest {
    static final @NullRestricted NullRestrictedValue static_final_v = NullRestrictedValue.of((byte)20,(short)1854);

    static @NullRestricted NullRestrictedValue static_v = NullRestrictedValue.of((byte)20,(short)1854);

    final @NullRestricted NullRestrictedValue final_v;

    @NullRestricted NullRestrictedValue v;

    static final @NullRestricted NullRestrictedValue static_final_v2 = NullRestrictedValue.of((byte)20,(short)1854);

    static @NullRestricted NullRestrictedValue static_v2 = NullRestrictedValue.of((byte)20,(short)1854);

    final @NullRestricted NullRestrictedValue final_v2;

    @NullRestricted NullRestrictedValue v2;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    VarHandle vhArrayObject;

    public VarHandleTestAccessNullRestrictedValue() {
        final_v = NullRestrictedValue.of((byte)20,(short)1854);
        v = NullRestrictedValue.of((byte)20,(short)1854);
        final_v2 = NullRestrictedValue.of((byte)20,(short)1854);
        v2 = NullRestrictedValue.of((byte)20,(short)1854);
        super();
    }

    VarHandle[] allocate(boolean same) {
        List<VarHandle> vhs = new ArrayList<>();

        String postfix = same ? "" : "2";
        VarHandle vh;
        try {
            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessNullRestrictedValue.class, "final_v" + postfix, NullRestrictedValue.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessNullRestrictedValue.class, "v" + postfix, NullRestrictedValue.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessNullRestrictedValue.class, "static_final_v" + postfix, NullRestrictedValue.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessNullRestrictedValue.class, "static_v" + postfix, NullRestrictedValue.class);
            vhs.add(vh);

            if (same) {
                vh = MethodHandles.arrayElementVarHandle(NullRestrictedValue[].class);
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
                VarHandleTestAccessNullRestrictedValue.class, "final_v", NullRestrictedValue.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessNullRestrictedValue.class, "v", NullRestrictedValue.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessNullRestrictedValue.class, "static_final_v", NullRestrictedValue.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessNullRestrictedValue.class, "static_v", NullRestrictedValue.class);

        vhArray = MethodHandles.arrayElementVarHandle(NullRestrictedValue[].class);
        vhArrayObject = MethodHandles.arrayElementVarHandle(Object[].class);
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

        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD_ACQUIRE));
        assertFalse(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_ADD_RELEASE));

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
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessNullRestrictedValue.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(NullRestrictedValue[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(NullRestrictedValue.class, vh.varType());

        assertEquals(pts, vh.coordinateTypes());

        testTypes(vh);
    }

    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessNullRestrictedValue.class, "final_v", NullRestrictedValue.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessNullRestrictedValue.class, "v", NullRestrictedValue.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessNullRestrictedValue.class, "static_final_v", NullRestrictedValue.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessNullRestrictedValue.class, "static_v", NullRestrictedValue.class);
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
                                              vhStaticFinalField, VarHandleTestAccessNullRestrictedValue::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessNullRestrictedValue::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));
        cases.add(new VarHandleAccessTestCase("Instance field null pointer exception",
                                              vhField, vh -> testInstanceFieldNullPointerException(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessNullRestrictedValue::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessNullRestrictedValue::testStaticFieldUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Static field null pointer exception",
                                              vhStaticField, VarHandleTestAccessNullRestrictedValue::testStaticFieldNullPointerException,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessNullRestrictedValue::testArray));
        cases.add(new VarHandleAccessTestCase("Array Object[]",
                                              vhArrayObject, VarHandleTestAccessNullRestrictedValue::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessNullRestrictedValue::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessNullRestrictedValue::testArrayIndexOutOfBounds,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array store exception",
                                              vhArrayObject, VarHandleTestAccessNullRestrictedValue::testArrayStoreException,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array null pointer exception",
                                              vhArrayObject, VarHandleTestAccessNullRestrictedValue::testArrayNullPointerException,
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

    static void testInstanceFinalField(VarHandleTestAccessNullRestrictedValue recv, VarHandle vh) {
        // Plain
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "get NullRestrictedValue value");
        }


        // Volatile
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getAcquire NullRestrictedValue value");
        }

        // Opaque
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getOpaque NullRestrictedValue value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessNullRestrictedValue recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setVolatile(recv, NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setRelease(recv, NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setOpaque(recv, NullRestrictedValue.of((byte)-42,(short)1854));
        });


        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAdd(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOr(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAnd(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXor(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "get NullRestrictedValue value");
        }


        // Volatile
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getAcquire NullRestrictedValue value");
        }

        // Opaque
        {
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "getOpaque NullRestrictedValue value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setVolatile(NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setRelease(NullRestrictedValue.of((byte)-42,(short)1854));
        });

        checkUOE(() -> {
            vh.setOpaque(NullRestrictedValue.of((byte)-42,(short)1854));
        });


        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAdd(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOr(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAnd(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXor(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });
    }


    static void testInstanceField(VarHandleTestAccessNullRestrictedValue recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "set NullRestrictedValue value");
        }


        // Volatile
        {
            vh.setVolatile(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            vh.setRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
        }

        // Opaque
        {
            vh.setOpaque(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
        }

        vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854));

        // Compare
        {
            boolean r = vh.compareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, true, "success compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
        }

        {
            boolean r = vh.compareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue value");
        }

        {
            boolean success = vh.weakCompareAndSet(recv, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
        }

        // Compare set and get
        {
            vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSet(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
        }

        {
            vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetAcquire(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
        }

        {
            vh.set(recv, NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetRelease(recv, NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get(recv);
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessNullRestrictedValue recv, VarHandle vh) {

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAdd(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOr(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAnd(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXor(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorRelease(recv, NullRestrictedValue.of((byte)20,(short)1854));
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "set NullRestrictedValue value");
        }


        // Volatile
        {
            vh.setVolatile(NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
        }

        // Lazy
        {
            vh.setRelease(NullRestrictedValue.of((byte)20,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
        }

        // Opaque
        {
            vh.setOpaque(NullRestrictedValue.of((byte)-42,(short)1854));
            NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
        }

        vh.set(NullRestrictedValue.of((byte)20,(short)1854));

        // Compare
        {
            boolean r = vh.compareAndSet(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, true, "success compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
        }

        {
            boolean r = vh.compareAndSet(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue");
        }

        {
            boolean success = vh.weakCompareAndSet(NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
            assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
        }

        // Compare set and get
        {
            vh.set(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSet(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
        }

        {
            vh.set(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetAcquire(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
        }

        {
            vh.set(NullRestrictedValue.of((byte)20,(short)1854));

            NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetRelease(NullRestrictedValue.of((byte)-42,(short)1854));
            assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
            NullRestrictedValue x = (NullRestrictedValue) vh.get();
            assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAdd(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOr(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAnd(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXor(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorAcquire(NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorRelease(NullRestrictedValue.of((byte)20,(short)1854));
        });
    }


    static void testArray(VarHandle vh) {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, NullRestrictedValue.of((byte)20,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "get NullRestrictedValue value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setVolatile NullRestrictedValue value");
            }

            // Lazy
            {
                vh.setRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "setRelease NullRestrictedValue value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "setOpaque NullRestrictedValue value");
            }

            vh.set(array, i, NullRestrictedValue.of((byte)20,(short)1854));

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(r, true, "success compareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndSet NullRestrictedValue value");
            }

            {
                boolean r = vh.compareAndSet(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, false, "failing compareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndSet NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchange NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchange NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchange NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchange NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "success compareAndExchangeAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success compareAndExchangeAcquire NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "failing compareAndExchangeAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing compareAndExchangeAcquire NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                assertEquals(r, NullRestrictedValue.of((byte)-42,(short)1854), "success compareAndExchangeRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success compareAndExchangeRelease NullRestrictedValue value");
            }

            {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(r, NullRestrictedValue.of((byte)20,(short)1854), "failing compareAndExchangeRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing compareAndExchangeRelease NullRestrictedValue value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetPlain(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetPlain NullRestrictedValue value");
            }

            {
                boolean success = vh.weakCompareAndSetPlain(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetPlain NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetPlain NullRestrictedValue value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetAcquire(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSetAcquire NullRestrictedValue");
            }

            {
                boolean success = vh.weakCompareAndSetAcquire(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSetAcquire NullRestrictedValue value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "success weakCompareAndSetRelease NullRestrictedValue");
            }

            {
                boolean success = vh.weakCompareAndSetRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "failing weakCompareAndSetRelease NullRestrictedValue value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSet(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "success weakCompareAndSet NullRestrictedValue");
            }

            {
                boolean success = vh.weakCompareAndSet(array, i, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)-31083));
                assertEquals(success, false, "failing weakCompareAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), x, "failing weakCompareAndSet NullRestrictedValue value");
            }

            // Compare set and get
            {
                vh.set(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSet(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSet NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSet NullRestrictedValue value");
            }

            {
                vh.set(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetAcquire(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetAcquire NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetAcquire NullRestrictedValue value");
            }

            {
                vh.set(array, i, NullRestrictedValue.of((byte)20,(short)1854));

                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetRelease(array, i, NullRestrictedValue.of((byte)-42,(short)1854));
                assertEquals(NullRestrictedValue.of((byte)20,(short)1854), o, "getAndSetRelease NullRestrictedValue");
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, i);
                assertEquals(NullRestrictedValue.of((byte)-42,(short)1854), x, "getAndSetRelease NullRestrictedValue value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        int i = 0;

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAdd(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndAddRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOr(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseOrRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAnd(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseAndRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXor(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorAcquire(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });

        checkUOE(() -> {
            NullRestrictedValue o = (NullRestrictedValue) vh.getAndBitwiseXorRelease(array, i, NullRestrictedValue.of((byte)20,(short)1854));
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkAIOOBE(() -> {
                NullRestrictedValue x = (NullRestrictedValue) vh.get(array, ci);
            });

            checkAIOOBE(() -> {
                vh.set(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue x = (NullRestrictedValue) vh.getVolatile(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setVolatile(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue x = (NullRestrictedValue) vh.getAcquire(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setRelease(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue x = (NullRestrictedValue) vh.getOpaque(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setOpaque(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchange(array, ci, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, ci, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue r = (NullRestrictedValue) vh.compareAndExchangeRelease(array, ci, NullRestrictedValue.of((byte)-42,(short)1854), NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetPlain(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, NullRestrictedValue.of((byte)20,(short)1854), NullRestrictedValue.of((byte)-42,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSet(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetAcquire(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });

            checkAIOOBE(() -> {
                NullRestrictedValue o = (NullRestrictedValue) vh.getAndSetRelease(array, ci, NullRestrictedValue.of((byte)20,(short)1854));
            });


        }
    }

    static void testArrayStoreException(VarHandle vh) throws Throwable {
        Object[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));
        Arrays.fill(array, NullRestrictedValue.of((byte)20,(short)1854));
        Object value = new Object();

        // Set
        checkASE(() -> {
            vh.set(array, 0, value);
        });

        // SetVolatile
        checkASE(() -> {
            vh.setVolatile(array, 0, value);
        });

        // SetOpaque
        checkASE(() -> {
            vh.setOpaque(array, 0, value);
        });

        // SetRelease
        checkASE(() -> {
            vh.setRelease(array, 0, value);
        });

        // CompareAndSet
        checkASE(() -> {
            boolean r = vh.compareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSet
        checkASE(() -> {
            boolean r = vh.weakCompareAndSetPlain(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetVolatile
        checkASE(() -> {
            boolean r = vh.weakCompareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetAcquire
        checkASE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetRelease
        checkASE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchange
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeAcquire
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeRelease
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // GetAndSet
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(array, 0, value);
        });

        // GetAndSetAcquire
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(array, 0, value);
        });

        // GetAndSetRelease
        checkASE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(array, 0, value);
        });
    }

    static void testInstanceFieldNullPointerException(VarHandleTestAccessNullRestrictedValue recv, VarHandle vh) throws Throwable {
        NullRestrictedValue value = null;

        // Set
        checkNPE(() -> {
            vh.set(recv, value);
        });

        // SetVolatile
        checkNPE(() -> {
            vh.setVolatile(recv, value);
        });

        // SetOpaque
        checkNPE(() -> {
            vh.setOpaque(recv, value);
        });

        // SetRelease
        checkNPE(() -> {
            vh.setRelease(recv, value);
        });

        // CompareAndSet
        checkNPE(() -> {
            boolean r = vh.compareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSet
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetPlain(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetVolatile
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSet(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetAcquire
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetRelease
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchange
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(recv, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // GetAndSet
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(recv, value);
        });

        // GetAndSetAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(recv, value);
        });

        // GetAndSetRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(recv, value);
        });
    }

    static void testStaticFieldNullPointerException(VarHandle vh) throws Throwable {
        NullRestrictedValue value = null;

        // Set
        checkNPE(() -> {
            vh.set(value);
        });

        // SetVolatile
        checkNPE(() -> {
            vh.setVolatile(value);
        });

        // SetOpaque
        checkNPE(() -> {
            vh.setOpaque(value);
        });

        // SetRelease
        checkNPE(() -> {
            vh.setRelease(value);
        });

        // CompareAndSet
        checkNPE(() -> {
            boolean r = vh.compareAndSet(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSet
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetPlain(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetVolatile
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSet(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetAcquire
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetRelease
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetRelease(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchange
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // GetAndSet
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(value);
        });

        // GetAndSetAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(value);
        });

        // GetAndSetRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(value);
        });
    }

    static void testArrayNullPointerException(VarHandle vh) throws Throwable {
        NullRestrictedValue[] array = (NullRestrictedValue[]) ValueClass.newNullRestrictedAtomicArray(NullRestrictedValue.class, 10, NullRestrictedValue.of((byte)20,(short)1854));
        NullRestrictedValue value = null;

        // Set
        checkNPE(() -> {
            vh.set(array, 0, value);
        });

        // SetVolatile
        checkNPE(() -> {
            vh.setVolatile(array, 0, value);
        });

        // SetOpaque
        checkNPE(() -> {
            vh.setOpaque(array, 0, value);
        });

        // SetRelease
        checkNPE(() -> {
            vh.setRelease(array, 0, value);
        });

        // CompareAndSet
        checkNPE(() -> {
            boolean r = vh.compareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSet
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetPlain(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetVolatile
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSet(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetAcquire
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // WeakCompareAndSetRelease
        checkNPE(() -> {
            boolean r = vh.weakCompareAndSetRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchange
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchange(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeAcquire(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // CompareAndExchangeRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.compareAndExchangeRelease(array, 0, NullRestrictedValue.of((byte)20,(short)1854), value);
        });

        // GetAndSet
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSet(array, 0, value);
        });

        // GetAndSetAcquire
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetAcquire(array, 0, value);
        });

        // GetAndSetRelease
        checkNPE(() -> {
            NullRestrictedValue x = (NullRestrictedValue) vh.getAndSetRelease(array, 0, value);
        });
    }
}

