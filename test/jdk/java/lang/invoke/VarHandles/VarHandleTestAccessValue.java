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

// -- This file was mechanically generated: Do not edit! -- //

/*
 * @test
 * @enablePreview
 * @modules java.base/jdk.internal.vm.annotation
 * @run junit/othervm -Diters=10   -Xint                                                   VarHandleTestAccessValue
 *
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop sets to 2000 iterations
 *          to hit compilation thresholds
 *
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:TieredStopAtLevel=1 VarHandleTestAccessValue
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1                         VarHandleTestAccessValue
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:-TieredCompilation  VarHandleTestAccessValue
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
public class VarHandleTestAccessValue extends VarHandleBaseTest {
    static final Value static_final_v = Value.getInstance(10);

    static Value static_v;

    final Value final_v = Value.getInstance(10);

    Value v;

    static final Value static_final_v2 = Value.getInstance(10);

    static Value static_v2;

    final Value final_v2 = Value.getInstance(10);

    Value v2;

    VarHandle vhFinalField;

    VarHandle vhField;

    VarHandle vhStaticField;

    VarHandle vhStaticFinalField;

    VarHandle vhArray;

    VarHandle vhArrayObject;

    VarHandle[] allocate(boolean same) {
        List<VarHandle> vhs = new ArrayList<>();

        String postfix = same ? "" : "2";
        VarHandle vh;
        try {
            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessValue.class, "final_v" + postfix, Value.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessValue.class, "v" + postfix, Value.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessValue.class, "static_final_v" + postfix, Value.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessValue.class, "static_v" + postfix, Value.class);
            vhs.add(vh);

            if (same) {
                vh = MethodHandles.arrayElementVarHandle(Value[].class);
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
                VarHandleTestAccessValue.class, "final_v", Value.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessValue.class, "v", Value.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessValue.class, "static_final_v", Value.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessValue.class, "static_v", Value.class);

        vhArray = MethodHandles.arrayElementVarHandle(Value[].class);
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
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessValue.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(Value[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(Value.class, vh.varType());

        assertEquals(pts, vh.coordinateTypes());

        testTypes(vh);
    }

    @Test
    public void testLookupInstanceToStatic() {
        checkIAE("Lookup of static final field to instance final field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessValue.class, "final_v", Value.class);
        });

        checkIAE("Lookup of static field to instance field", () -> {
            MethodHandles.lookup().findStaticVarHandle(
                    VarHandleTestAccessValue.class, "v", Value.class);
        });
    }

    @Test
    public void testLookupStaticToInstance() {
        checkIAE("Lookup of instance final field to static final field", () -> {
            MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessValue.class, "static_final_v", Value.class);
        });

        checkIAE("Lookup of instance field to static field", () -> {
            vhStaticField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessValue.class, "static_v", Value.class);
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
                                              vhStaticFinalField, VarHandleTestAccessValue::testStaticFinalField));
        cases.add(new VarHandleAccessTestCase("Static final field unsupported",
                                              vhStaticFinalField, VarHandleTestAccessValue::testStaticFinalFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Instance field",
                                              vhField, vh -> testInstanceField(this, vh)));
        cases.add(new VarHandleAccessTestCase("Instance field unsupported",
                                              vhField, vh -> testInstanceFieldUnsupported(this, vh),
                                              false));

        cases.add(new VarHandleAccessTestCase("Static field",
                                              vhStaticField, VarHandleTestAccessValue::testStaticField));
        cases.add(new VarHandleAccessTestCase("Static field unsupported",
                                              vhStaticField, VarHandleTestAccessValue::testStaticFieldUnsupported,
                                              false));

        cases.add(new VarHandleAccessTestCase("Array",
                                              vhArray, VarHandleTestAccessValue::testArray));
        cases.add(new VarHandleAccessTestCase("Array Object[]",
                                              vhArrayObject, VarHandleTestAccessValue::testArray));
        cases.add(new VarHandleAccessTestCase("Array unsupported",
                                              vhArray, VarHandleTestAccessValue::testArrayUnsupported,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array index out of bounds",
                                              vhArray, VarHandleTestAccessValue::testArrayIndexOutOfBounds,
                                              false));
        cases.add(new VarHandleAccessTestCase("Array store exception",
                                              vhArrayObject, VarHandleTestAccessValue::testArrayStoreException,
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

    static void testInstanceFinalField(VarHandleTestAccessValue recv, VarHandle vh) {
        // Plain
        {
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "get Value value");
        }


        // Volatile
        {
            Value x = (Value) vh.getVolatile(recv);
            assertEquals(Value.getInstance(10), x, "getVolatile Value value");
        }

        // Lazy
        {
            Value x = (Value) vh.getAcquire(recv);
            assertEquals(Value.getInstance(10), x, "getRelease Value value");
        }

        // Opaque
        {
            Value x = (Value) vh.getOpaque(recv);
            assertEquals(Value.getInstance(10), x, "getOpaque Value value");
        }
    }

    static void testInstanceFinalFieldUnsupported(VarHandleTestAccessValue recv, VarHandle vh) {
        checkUOE(() -> {
            vh.set(recv, Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setVolatile(recv, Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setRelease(recv, Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setOpaque(recv, Value.getInstance(20));
        });


        checkUOE(() -> {
            Value o = (Value) vh.getAndAdd(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOr(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAnd(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXor(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorRelease(recv, Value.getInstance(10));
        });
    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "get Value value");
        }


        // Volatile
        {
            Value x = (Value) vh.getVolatile();
            assertEquals(Value.getInstance(10), x, "getVolatile Value value");
        }

        // Lazy
        {
            Value x = (Value) vh.getAcquire();
            assertEquals(Value.getInstance(10), x, "getRelease Value value");
        }

        // Opaque
        {
            Value x = (Value) vh.getOpaque();
            assertEquals(Value.getInstance(10), x, "getOpaque Value value");
        }
    }

    static void testStaticFinalFieldUnsupported(VarHandle vh) {
        checkUOE(() -> {
            vh.set(Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setVolatile(Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setRelease(Value.getInstance(20));
        });

        checkUOE(() -> {
            vh.setOpaque(Value.getInstance(20));
        });


        checkUOE(() -> {
            Value o = (Value) vh.getAndAdd(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOr(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAnd(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXor(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorRelease(Value.getInstance(10));
        });
    }


    static void testInstanceField(VarHandleTestAccessValue recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, Value.getInstance(10));
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "set Value value");
        }


        // Volatile
        {
            vh.setVolatile(recv, Value.getInstance(20));
            Value x = (Value) vh.getVolatile(recv);
            assertEquals(Value.getInstance(20), x, "setVolatile Value value");
        }

        // Lazy
        {
            vh.setRelease(recv, Value.getInstance(10));
            Value x = (Value) vh.getAcquire(recv);
            assertEquals(Value.getInstance(10), x, "setRelease Value value");
        }

        // Opaque
        {
            vh.setOpaque(recv, Value.getInstance(20));
            Value x = (Value) vh.getOpaque(recv);
            assertEquals(Value.getInstance(20), x, "setOpaque Value value");
        }

        vh.set(recv, Value.getInstance(10));

        // Compare
        {
            boolean r = vh.compareAndSet(recv, Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, true, "success compareAndSet Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
        }

        {
            boolean r = vh.compareAndSet(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, false, "failing compareAndSet Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
        }

        {
            Value r = (Value) vh.compareAndExchange(recv, Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
        }

        {
            Value r = (Value) vh.compareAndExchange(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeAcquire(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeRelease(recv, Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeRelease(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(recv, Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetPlain Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(recv, Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(recv, Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(recv, Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetRelease Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetRelease Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(recv, Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value value");
        }

        {
            boolean success = vh.weakCompareAndSet(recv, Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSet Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSet Value value");
        }

        // Compare set and get
        {
            vh.set(recv, Value.getInstance(10));

            Value o = (Value) vh.getAndSet(recv, Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSet Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "getAndSet Value value");
        }

        {
            vh.set(recv, Value.getInstance(10));

            Value o = (Value) vh.getAndSetAcquire(recv, Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetAcquire Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "getAndSetAcquire Value value");
        }

        {
            vh.set(recv, Value.getInstance(10));

            Value o = (Value) vh.getAndSetRelease(recv, Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetRelease Value");
            Value x = (Value) vh.get(recv);
            assertEquals(Value.getInstance(20), x, "getAndSetRelease Value value");
        }


    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessValue recv, VarHandle vh) {

        checkUOE(() -> {
            Value o = (Value) vh.getAndAdd(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOr(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAnd(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndRelease(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXor(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorAcquire(recv, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorRelease(recv, Value.getInstance(10));
        });
    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(Value.getInstance(10));
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "set Value value");
        }


        // Volatile
        {
            vh.setVolatile(Value.getInstance(20));
            Value x = (Value) vh.getVolatile();
            assertEquals(Value.getInstance(20), x, "setVolatile Value value");
        }

        // Lazy
        {
            vh.setRelease(Value.getInstance(10));
            Value x = (Value) vh.getAcquire();
            assertEquals(Value.getInstance(10), x, "setRelease Value value");
        }

        // Opaque
        {
            vh.setOpaque(Value.getInstance(20));
            Value x = (Value) vh.getOpaque();
            assertEquals(Value.getInstance(20), x, "setOpaque Value value");
        }

        vh.set(Value.getInstance(10));

        // Compare
        {
            boolean r = vh.compareAndSet(Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, true, "success compareAndSet Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
        }

        {
            boolean r = vh.compareAndSet(Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, false, "failing compareAndSet Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
        }

        {
            Value r = (Value) vh.compareAndExchange(Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
        }

        {
            Value r = (Value) vh.compareAndExchange(Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeAcquire(Value.getInstance(10), Value.getInstance(20));
            assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeAcquire(Value.getInstance(10), Value.getInstance(30));
            assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeRelease(Value.getInstance(20), Value.getInstance(10));
            assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
        }

        {
            Value r = (Value) vh.compareAndExchangeRelease(Value.getInstance(20), Value.getInstance(30));
            assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetPlain Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(Value.getInstance(10), Value.getInstance(20));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(Value.getInstance(10), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSetRelease Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetRelease Value value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(Value.getInstance(20), Value.getInstance(10));
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value");
        }

        {
            boolean success = vh.weakCompareAndSet(Value.getInstance(20), Value.getInstance(30));
            assertEquals(success, false, "failing weakCompareAndSet Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(10), x, "failing weakCompareAndSet Value value");
        }

        // Compare set and get
        {
            vh.set(Value.getInstance(10));

            Value o = (Value) vh.getAndSet(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSet Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "getAndSet Value value");
        }

        {
            vh.set(Value.getInstance(10));

            Value o = (Value) vh.getAndSetAcquire(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetAcquire Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "getAndSetAcquire Value value");
        }

        {
            vh.set(Value.getInstance(10));

            Value o = (Value) vh.getAndSetRelease(Value.getInstance(20));
            assertEquals(Value.getInstance(10), o, "getAndSetRelease Value");
            Value x = (Value) vh.get();
            assertEquals(Value.getInstance(20), x, "getAndSetRelease Value value");
        }


    }

    static void testStaticFieldUnsupported(VarHandle vh) {

        checkUOE(() -> {
            Value o = (Value) vh.getAndAdd(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOr(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAnd(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndRelease(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXor(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorAcquire(Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorRelease(Value.getInstance(10));
        });
    }


    static void testArray(VarHandle vh) {
        Value[] array = new Value[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, Value.getInstance(10));
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "get Value value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, Value.getInstance(20));
                Value x = (Value) vh.getVolatile(array, i);
                assertEquals(Value.getInstance(20), x, "setVolatile Value value");
            }

            // Lazy
            {
                vh.setRelease(array, i, Value.getInstance(10));
                Value x = (Value) vh.getAcquire(array, i);
                assertEquals(Value.getInstance(10), x, "setRelease Value value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, Value.getInstance(20));
                Value x = (Value) vh.getOpaque(array, i);
                assertEquals(Value.getInstance(20), x, "setOpaque Value value");
            }

            vh.set(array, i, Value.getInstance(10));

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, Value.getInstance(10), Value.getInstance(20));
                assertEquals(r, true, "success compareAndSet Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "success compareAndSet Value value");
            }

            {
                boolean r = vh.compareAndSet(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(r, false, "failing compareAndSet Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "failing compareAndSet Value value");
            }

            {
                Value r = (Value) vh.compareAndExchange(array, i, Value.getInstance(20), Value.getInstance(10));
                assertEquals(r, Value.getInstance(20), "success compareAndExchange Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "success compareAndExchange Value value");
            }

            {
                Value r = (Value) vh.compareAndExchange(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(r, Value.getInstance(10), "failing compareAndExchange Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "failing compareAndExchange Value value");
            }

            {
                Value r = (Value) vh.compareAndExchangeAcquire(array, i, Value.getInstance(10), Value.getInstance(20));
                assertEquals(r, Value.getInstance(10), "success compareAndExchangeAcquire Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "success compareAndExchangeAcquire Value value");
            }

            {
                Value r = (Value) vh.compareAndExchangeAcquire(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(r, Value.getInstance(20), "failing compareAndExchangeAcquire Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "failing compareAndExchangeAcquire Value value");
            }

            {
                Value r = (Value) vh.compareAndExchangeRelease(array, i, Value.getInstance(20), Value.getInstance(10));
                assertEquals(r, Value.getInstance(20), "success compareAndExchangeRelease Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "success compareAndExchangeRelease Value value");
            }

            {
                Value r = (Value) vh.compareAndExchangeRelease(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(r, Value.getInstance(10), "failing compareAndExchangeRelease Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "failing compareAndExchangeRelease Value value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetPlain(array, i, Value.getInstance(10), Value.getInstance(20));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "success weakCompareAndSetPlain Value value");
            }

            {
                boolean success = vh.weakCompareAndSetPlain(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetPlain Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetPlain Value value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetAcquire(array, i, Value.getInstance(20), Value.getInstance(10));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "success weakCompareAndSetAcquire Value");
            }

            {
                boolean success = vh.weakCompareAndSetAcquire(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetAcquire Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "failing weakCompareAndSetAcquire Value value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetRelease(array, i, Value.getInstance(10), Value.getInstance(20));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "success weakCompareAndSetRelease Value");
            }

            {
                boolean success = vh.weakCompareAndSetRelease(array, i, Value.getInstance(10), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSetRelease Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "failing weakCompareAndSetRelease Value value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSet(array, i, Value.getInstance(20), Value.getInstance(10));
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "success weakCompareAndSet Value");
            }

            {
                boolean success = vh.weakCompareAndSet(array, i, Value.getInstance(20), Value.getInstance(30));
                assertEquals(success, false, "failing weakCompareAndSet Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(10), x, "failing weakCompareAndSet Value value");
            }

            // Compare set and get
            {
                vh.set(array, i, Value.getInstance(10));

                Value o = (Value) vh.getAndSet(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSet Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSet Value value");
            }

            {
                vh.set(array, i, Value.getInstance(10));

                Value o = (Value) vh.getAndSetAcquire(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSetAcquire Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSetAcquire Value value");
            }

            {
                vh.set(array, i, Value.getInstance(10));

                Value o = (Value) vh.getAndSetRelease(array, i, Value.getInstance(20));
                assertEquals(Value.getInstance(10), o, "getAndSetRelease Value");
                Value x = (Value) vh.get(array, i);
                assertEquals(Value.getInstance(20), x, "getAndSetRelease Value value");
            }


        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        Value[] array = new Value[10];

        int i = 0;

        checkUOE(() -> {
            Value o = (Value) vh.getAndAdd(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddAcquire(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndAddRelease(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOr(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrAcquire(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseOrRelease(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAnd(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndAcquire(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseAndRelease(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXor(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorAcquire(array, i, Value.getInstance(10));
        });

        checkUOE(() -> {
            Value o = (Value) vh.getAndBitwiseXorRelease(array, i, Value.getInstance(10));
        });
    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        Value[] array = new Value[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkAIOOBE(() -> {
                Value x = (Value) vh.get(array, ci);
            });

            checkAIOOBE(() -> {
                vh.set(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value x = (Value) vh.getVolatile(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setVolatile(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value x = (Value) vh.getAcquire(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setRelease(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value x = (Value) vh.getOpaque(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setOpaque(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, Value.getInstance(10), Value.getInstance(20));
            });

            checkAIOOBE(() -> {
                Value r = (Value) vh.compareAndExchange(array, ci, Value.getInstance(20), Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value r = (Value) vh.compareAndExchangeAcquire(array, ci, Value.getInstance(20), Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value r = (Value) vh.compareAndExchangeRelease(array, ci, Value.getInstance(20), Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetPlain(array, ci, Value.getInstance(10), Value.getInstance(20));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, Value.getInstance(10), Value.getInstance(20));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, Value.getInstance(10), Value.getInstance(20));
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, Value.getInstance(10), Value.getInstance(20));
            });

            checkAIOOBE(() -> {
                Value o = (Value) vh.getAndSet(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value o = (Value) vh.getAndSetAcquire(array, ci, Value.getInstance(10));
            });

            checkAIOOBE(() -> {
                Value o = (Value) vh.getAndSetRelease(array, ci, Value.getInstance(10));
            });


        }
    }

    static void testArrayStoreException(VarHandle vh) throws Throwable {
        Object[] array = new Value[10];
        Arrays.fill(array, Value.getInstance(10));
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
        checkASE(() -> { // receiver reference class
            boolean r = vh.compareAndSet(array, 0, Value.getInstance(10), value);
        });

        // WeakCompareAndSet
        checkASE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetPlain(array, 0, Value.getInstance(10), value);
        });

        // WeakCompareAndSetVolatile
        checkASE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSet(array, 0, Value.getInstance(10), value);
        });

        // WeakCompareAndSetAcquire
        checkASE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetAcquire(array, 0, Value.getInstance(10), value);
        });

        // WeakCompareAndSetRelease
        checkASE(() -> { // receiver reference class
            boolean r = vh.weakCompareAndSetRelease(array, 0, Value.getInstance(10), value);
        });

        // CompareAndExchange
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchange(array, 0, Value.getInstance(10), value);
        });

        // CompareAndExchangeAcquire
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchangeAcquire(array, 0, Value.getInstance(10), value);
        });

        // CompareAndExchangeRelease
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.compareAndExchangeRelease(array, 0, Value.getInstance(10), value);
        });

        // GetAndSet
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSet(array, 0, value);
        });

        // GetAndSetAcquire
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSetAcquire(array, 0, value);
        });

        // GetAndSetRelease
        checkASE(() -> { // receiver reference class
            Value x = (Value) vh.getAndSetRelease(array, 0, value);
        });
    }
}

