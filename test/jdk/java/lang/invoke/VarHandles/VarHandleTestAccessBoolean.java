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
 * @run junit/othervm -Diters=10   -Xint                                                   VarHandleTestAccessBoolean
 *
 * @comment Set CompileThresholdScaling to 0.1 so that the warmup loop sets to 2000 iterations
 *          to hit compilation thresholds
 *
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:TieredStopAtLevel=1 VarHandleTestAccessBoolean
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1                         VarHandleTestAccessBoolean
 * @run junit/othervm -Diters=2000 -XX:CompileThresholdScaling=0.1 -XX:-TieredCompilation  VarHandleTestAccessBoolean
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
public class VarHandleTestAccessBoolean extends VarHandleBaseTest {
    static final boolean static_final_v = true;

    static boolean static_v;

    final boolean final_v = true;

    boolean v;

    static final boolean static_final_v2 = true;

    static boolean static_v2;

    final boolean final_v2 = true;

    boolean v2;

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
                    VarHandleTestAccessBoolean.class, "final_v" + postfix, boolean.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findVarHandle(
                    VarHandleTestAccessBoolean.class, "v" + postfix, boolean.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessBoolean.class, "static_final_v" + postfix, boolean.class);
            vhs.add(vh);

            vh = MethodHandles.lookup().findStaticVarHandle(
                VarHandleTestAccessBoolean.class, "static_v" + postfix, boolean.class);
            vhs.add(vh);

            if (same) {
                vh = MethodHandles.arrayElementVarHandle(boolean[].class);
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
                VarHandleTestAccessBoolean.class, "final_v", boolean.class);

        vhField = MethodHandles.lookup().findVarHandle(
                VarHandleTestAccessBoolean.class, "v", boolean.class);

        vhStaticFinalField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessBoolean.class, "static_final_v", boolean.class);

        vhStaticField = MethodHandles.lookup().findStaticVarHandle(
            VarHandleTestAccessBoolean.class, "static_v", boolean.class);

        vhArray = MethodHandles.arrayElementVarHandle(boolean[].class);
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

        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_OR_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_AND_RELEASE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR_ACQUIRE));
        assertTrue(vh.isAccessModeSupported(VarHandle.AccessMode.GET_AND_BITWISE_XOR_RELEASE));
    }

    public Object[][] typesProvider() throws Exception {
        List<Object[]> types = new ArrayList<>();
        types.add(new Object[] {vhField, Arrays.asList(VarHandleTestAccessBoolean.class)});
        types.add(new Object[] {vhStaticField, Arrays.asList()});
        types.add(new Object[] {vhArray, Arrays.asList(boolean[].class, int.class)});

        return types.stream().toArray(Object[][]::new);
    }

    @ParameterizedTest
    @MethodSource("typesProvider")
    public void testTypes(VarHandle vh, List<Class<?>> pts) {
        assertEquals(boolean.class, vh.varType());

        assertEquals(pts, vh.coordinateTypes());

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

    @ParameterizedTest
    @MethodSource("accessTestCaseProvider")
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
            assertEquals(true, x, "get boolean value");
        }


        // Volatile
        {
            boolean x = (boolean) vh.getVolatile(recv);
            assertEquals(true, x, "getVolatile boolean value");
        }

        // Lazy
        {
            boolean x = (boolean) vh.getAcquire(recv);
            assertEquals(true, x, "getRelease boolean value");
        }

        // Opaque
        {
            boolean x = (boolean) vh.getOpaque(recv);
            assertEquals(true, x, "getOpaque boolean value");
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
            boolean o = (boolean) vh.getAndAdd(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddAcquire(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddRelease(recv, true);
        });

    }


    static void testStaticFinalField(VarHandle vh) {
        // Plain
        {
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "get boolean value");
        }


        // Volatile
        {
            boolean x = (boolean) vh.getVolatile();
            assertEquals(true, x, "getVolatile boolean value");
        }

        // Lazy
        {
            boolean x = (boolean) vh.getAcquire();
            assertEquals(true, x, "getRelease boolean value");
        }

        // Opaque
        {
            boolean x = (boolean) vh.getOpaque();
            assertEquals(true, x, "getOpaque boolean value");
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
            boolean o = (boolean) vh.getAndAdd(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddAcquire(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddRelease(true);
        });

    }


    static void testInstanceField(VarHandleTestAccessBoolean recv, VarHandle vh) {
        // Plain
        {
            vh.set(recv, true);
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "set boolean value");
        }


        // Volatile
        {
            vh.setVolatile(recv, false);
            boolean x = (boolean) vh.getVolatile(recv);
            assertEquals(false, x, "setVolatile boolean value");
        }

        // Lazy
        {
            vh.setRelease(recv, true);
            boolean x = (boolean) vh.getAcquire(recv);
            assertEquals(true, x, "setRelease boolean value");
        }

        // Opaque
        {
            vh.setOpaque(recv, false);
            boolean x = (boolean) vh.getOpaque(recv);
            assertEquals(false, x, "setOpaque boolean value");
        }

        vh.set(recv, true);

        // Compare
        {
            boolean r = vh.compareAndSet(recv, true, false);
            assertEquals(r, true, "success compareAndSet boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "success compareAndSet boolean value");
        }

        {
            boolean r = vh.compareAndSet(recv, true, false);
            assertEquals(r, false, "failing compareAndSet boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "failing compareAndSet boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchange(recv, false, true);
            assertEquals(r, false, "success compareAndExchange boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "success compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchange(recv, false, false);
            assertEquals(r, true, "failing compareAndExchange boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "failing compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeAcquire(recv, true, false);
            assertEquals(r, true, "success compareAndExchangeAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "success compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeAcquire(recv, true, false);
            assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "failing compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeRelease(recv, false, true);
            assertEquals(r, false, "success compareAndExchangeRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "success compareAndExchangeRelease boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeRelease(recv, false, false);
            assertEquals(r, true, "failing compareAndExchangeRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "failing compareAndExchangeRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(recv, true, false);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "success weakCompareAndSetPlain boolean value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(recv, true, false);
            assertEquals(success, false, "failing weakCompareAndSetPlain boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "failing weakCompareAndSetPlain boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(recv, false, true);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "success weakCompareAndSetAcquire boolean");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(recv, false, false);
            assertEquals(success, false, "failing weakCompareAndSetAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "failing weakCompareAndSetAcquire boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(recv, true, false);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "success weakCompareAndSetRelease boolean");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(recv, true, false);
            assertEquals(success, false, "failing weakCompareAndSetRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "failing weakCompareAndSetRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(recv, false, true);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "success weakCompareAndSet boolean value");
        }

        {
            boolean success = vh.weakCompareAndSet(recv, false, false);
            assertEquals(success, false, "failing weakCompareAndSet boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(true, x, "failing weakCompareAndSet boolean value");
        }

        // Compare set and get
        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndSet(recv, false);
            assertEquals(true, o, "getAndSet boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "getAndSet boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndSetAcquire(recv, false);
            assertEquals(true, o, "getAndSetAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "getAndSetAcquire boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndSetRelease(recv, false);
            assertEquals(true, o, "getAndSetRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals(false, x, "getAndSetRelease boolean value");
        }


        // get and bitwise or
        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseOr(recv, false);
            assertEquals(true, o, "getAndBitwiseOr boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true | false), x, "getAndBitwiseOr boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseOrAcquire(recv, false);
            assertEquals(true, o, "getAndBitwiseOrAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true | false), x, "getAndBitwiseOrAcquire boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseOrRelease(recv, false);
            assertEquals(true, o, "getAndBitwiseOrRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true | false), x, "getAndBitwiseOrRelease boolean value");
        }

        // get and bitwise and
        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseAnd(recv, false);
            assertEquals(true, o, "getAndBitwiseAnd boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true & false), x, "getAndBitwiseAnd boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseAndAcquire(recv, false);
            assertEquals(true, o, "getAndBitwiseAndAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true & false), x, "getAndBitwiseAndAcquire boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseAndRelease(recv, false);
            assertEquals(true, o, "getAndBitwiseAndRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true & false), x, "getAndBitwiseAndRelease boolean value");
        }

        // get and bitwise xor
        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseXor(recv, false);
            assertEquals(true, o, "getAndBitwiseXor boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXor boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseXorAcquire(recv, false);
            assertEquals(true, o, "getAndBitwiseXorAcquire boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorAcquire boolean value");
        }

        {
            vh.set(recv, true);

            boolean o = (boolean) vh.getAndBitwiseXorRelease(recv, false);
            assertEquals(true, o, "getAndBitwiseXorRelease boolean");
            boolean x = (boolean) vh.get(recv);
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorRelease boolean value");
        }
    }

    static void testInstanceFieldUnsupported(VarHandleTestAccessBoolean recv, VarHandle vh) {

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddAcquire(recv, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddRelease(recv, true);
        });

    }


    static void testStaticField(VarHandle vh) {
        // Plain
        {
            vh.set(true);
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "set boolean value");
        }


        // Volatile
        {
            vh.setVolatile(false);
            boolean x = (boolean) vh.getVolatile();
            assertEquals(false, x, "setVolatile boolean value");
        }

        // Lazy
        {
            vh.setRelease(true);
            boolean x = (boolean) vh.getAcquire();
            assertEquals(true, x, "setRelease boolean value");
        }

        // Opaque
        {
            vh.setOpaque(false);
            boolean x = (boolean) vh.getOpaque();
            assertEquals(false, x, "setOpaque boolean value");
        }

        vh.set(true);

        // Compare
        {
            boolean r = vh.compareAndSet(true, false);
            assertEquals(r, true, "success compareAndSet boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "success compareAndSet boolean value");
        }

        {
            boolean r = vh.compareAndSet(true, false);
            assertEquals(r, false, "failing compareAndSet boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "failing compareAndSet boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchange(false, true);
            assertEquals(r, false, "success compareAndExchange boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "success compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchange(false, false);
            assertEquals(r, true, "failing compareAndExchange boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "failing compareAndExchange boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeAcquire(true, false);
            assertEquals(r, true, "success compareAndExchangeAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "success compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeAcquire(true, false);
            assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "failing compareAndExchangeAcquire boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeRelease(false, true);
            assertEquals(r, false, "success compareAndExchangeRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "success compareAndExchangeRelease boolean value");
        }

        {
            boolean r = (boolean) vh.compareAndExchangeRelease(false, false);
            assertEquals(r, true, "failing compareAndExchangeRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "failing compareAndExchangeRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetPlain(true, false);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetPlain boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "success weakCompareAndSetPlain boolean value");
        }

        {
            boolean success = vh.weakCompareAndSetPlain(true, false);
            assertEquals(success, false, "failing weakCompareAndSetPlain boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "failing weakCompareAndSetPlain boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetAcquire(false, true);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "success weakCompareAndSetAcquire boolean");
        }

        {
            boolean success = vh.weakCompareAndSetAcquire(false, false);
            assertEquals(success, false, "failing weakCompareAndSetAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "failing weakCompareAndSetAcquire boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSetRelease(true, false);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSetRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "success weakCompareAndSetRelease boolean");
        }

        {
            boolean success = vh.weakCompareAndSetRelease(true, false);
            assertEquals(success, false, "failing weakCompareAndSetRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "failing weakCompareAndSetRelease boolean value");
        }

        {
            boolean success = false;
            for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                success = vh.weakCompareAndSet(false, true);
                if (!success) weakDelay();
            }
            assertEquals(success, true, "success weakCompareAndSet boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "success weakCompareAndSet boolean");
        }

        {
            boolean success = vh.weakCompareAndSet(false, false);
            assertEquals(success, false, "failing weakCompareAndSet boolean");
            boolean x = (boolean) vh.get();
            assertEquals(true, x, "failing weakCompareAndSet boolean value");
        }

        // Compare set and get
        {
            vh.set(true);

            boolean o = (boolean) vh.getAndSet(false);
            assertEquals(true, o, "getAndSet boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "getAndSet boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndSetAcquire(false);
            assertEquals(true, o, "getAndSetAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "getAndSetAcquire boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndSetRelease(false);
            assertEquals(true, o, "getAndSetRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals(false, x, "getAndSetRelease boolean value");
        }


        // get and bitwise or
        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseOr(false);
            assertEquals(true, o, "getAndBitwiseOr boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true | false), x, "getAndBitwiseOr boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseOrAcquire(false);
            assertEquals(true, o, "getAndBitwiseOrAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true | false), x, "getAndBitwiseOrAcquire boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseOrRelease(false);
            assertEquals(true, o, "getAndBitwiseOrRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true | false), x, "getAndBitwiseOrRelease boolean value");
        }

        // get and bitwise and
        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseAnd(false);
            assertEquals(true, o, "getAndBitwiseAnd boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true & false), x, "getAndBitwiseAnd boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseAndAcquire(false);
            assertEquals(true, o, "getAndBitwiseAndAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true & false), x, "getAndBitwiseAndAcquire boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseAndRelease(false);
            assertEquals(true, o, "getAndBitwiseAndRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true & false), x, "getAndBitwiseAndRelease boolean value");
        }

        // get and bitwise xor
        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseXor(false);
            assertEquals(true, o, "getAndBitwiseXor boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXor boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseXorAcquire(false);
            assertEquals(true, o, "getAndBitwiseXorAcquire boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorAcquire boolean value");
        }

        {
            vh.set(true);

            boolean o = (boolean) vh.getAndBitwiseXorRelease(false);
            assertEquals(true, o, "getAndBitwiseXorRelease boolean");
            boolean x = (boolean) vh.get();
            assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorRelease boolean value");
        }
    }

    static void testStaticFieldUnsupported(VarHandle vh) {

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddAcquire(true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddRelease(true);
        });

    }


    static void testArray(VarHandle vh) {
        boolean[] array = new boolean[10];

        for (int i = 0; i < array.length; i++) {
            // Plain
            {
                vh.set(array, i, true);
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "get boolean value");
            }


            // Volatile
            {
                vh.setVolatile(array, i, false);
                boolean x = (boolean) vh.getVolatile(array, i);
                assertEquals(false, x, "setVolatile boolean value");
            }

            // Lazy
            {
                vh.setRelease(array, i, true);
                boolean x = (boolean) vh.getAcquire(array, i);
                assertEquals(true, x, "setRelease boolean value");
            }

            // Opaque
            {
                vh.setOpaque(array, i, false);
                boolean x = (boolean) vh.getOpaque(array, i);
                assertEquals(false, x, "setOpaque boolean value");
            }

            vh.set(array, i, true);

            // Compare
            {
                boolean r = vh.compareAndSet(array, i, true, false);
                assertEquals(r, true, "success compareAndSet boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "success compareAndSet boolean value");
            }

            {
                boolean r = vh.compareAndSet(array, i, true, false);
                assertEquals(r, false, "failing compareAndSet boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "failing compareAndSet boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchange(array, i, false, true);
                assertEquals(r, false, "success compareAndExchange boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "success compareAndExchange boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchange(array, i, false, false);
                assertEquals(r, true, "failing compareAndExchange boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "failing compareAndExchange boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchangeAcquire(array, i, true, false);
                assertEquals(r, true, "success compareAndExchangeAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "success compareAndExchangeAcquire boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchangeAcquire(array, i, true, false);
                assertEquals(r, false, "failing compareAndExchangeAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "failing compareAndExchangeAcquire boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchangeRelease(array, i, false, true);
                assertEquals(r, false, "success compareAndExchangeRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "success compareAndExchangeRelease boolean value");
            }

            {
                boolean r = (boolean) vh.compareAndExchangeRelease(array, i, false, false);
                assertEquals(r, true, "failing compareAndExchangeRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "failing compareAndExchangeRelease boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetPlain(array, i, true, false);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetPlain boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "success weakCompareAndSetPlain boolean value");
            }

            {
                boolean success = vh.weakCompareAndSetPlain(array, i, true, false);
                assertEquals(success, false, "failing weakCompareAndSetPlain boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "failing weakCompareAndSetPlain boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetAcquire(array, i, false, true);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "success weakCompareAndSetAcquire boolean");
            }

            {
                boolean success = vh.weakCompareAndSetAcquire(array, i, false, false);
                assertEquals(success, false, "failing weakCompareAndSetAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "failing weakCompareAndSetAcquire boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSetRelease(array, i, true, false);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSetRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "success weakCompareAndSetRelease boolean");
            }

            {
                boolean success = vh.weakCompareAndSetRelease(array, i, true, false);
                assertEquals(success, false, "failing weakCompareAndSetRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "failing weakCompareAndSetRelease boolean value");
            }

            {
                boolean success = false;
                for (int c = 0; c < WEAK_ATTEMPTS && !success; c++) {
                    success = vh.weakCompareAndSet(array, i, false, true);
                    if (!success) weakDelay();
                }
                assertEquals(success, true, "success weakCompareAndSet boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "success weakCompareAndSet boolean");
            }

            {
                boolean success = vh.weakCompareAndSet(array, i, false, false);
                assertEquals(success, false, "failing weakCompareAndSet boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(true, x, "failing weakCompareAndSet boolean value");
            }

            // Compare set and get
            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndSet(array, i, false);
                assertEquals(true, o, "getAndSet boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "getAndSet boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndSetAcquire(array, i, false);
                assertEquals(true, o, "getAndSetAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "getAndSetAcquire boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndSetRelease(array, i, false);
                assertEquals(true, o, "getAndSetRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals(false, x, "getAndSetRelease boolean value");
            }


            // get and bitwise or
            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseOr(array, i, false);
                assertEquals(true, o, "getAndBitwiseOr boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true | false), x, "getAndBitwiseOr boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseOrAcquire(array, i, false);
                assertEquals(true, o, "getAndBitwiseOrAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true | false), x, "getAndBitwiseOrAcquire boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseOrRelease(array, i, false);
                assertEquals(true, o, "getAndBitwiseOrRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true | false), x, "getAndBitwiseOrRelease boolean value");
            }

            // get and bitwise and
            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseAnd(array, i, false);
                assertEquals(true, o, "getAndBitwiseAnd boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true & false), x, "getAndBitwiseAnd boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseAndAcquire(array, i, false);
                assertEquals(true, o, "getAndBitwiseAndAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true & false), x, "getAndBitwiseAndAcquire boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseAndRelease(array, i, false);
                assertEquals(true, o, "getAndBitwiseAndRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true & false), x, "getAndBitwiseAndRelease boolean value");
            }

            // get and bitwise xor
            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseXor(array, i, false);
                assertEquals(true, o, "getAndBitwiseXor boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true ^ false), x, "getAndBitwiseXor boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseXorAcquire(array, i, false);
                assertEquals(true, o, "getAndBitwiseXorAcquire boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorAcquire boolean value");
            }

            {
                vh.set(array, i, true);

                boolean o = (boolean) vh.getAndBitwiseXorRelease(array, i, false);
                assertEquals(true, o, "getAndBitwiseXorRelease boolean");
                boolean x = (boolean) vh.get(array, i);
                assertEquals((boolean)(true ^ false), x, "getAndBitwiseXorRelease boolean value");
            }
        }
    }

    static void testArrayUnsupported(VarHandle vh) {
        boolean[] array = new boolean[10];

        int i = 0;

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAdd(array, i, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddAcquire(array, i, true);
        });

        checkUOE(() -> {
            boolean o = (boolean) vh.getAndAddRelease(array, i, true);
        });

    }

    static void testArrayIndexOutOfBounds(VarHandle vh) throws Throwable {
        boolean[] array = new boolean[10];

        for (int i : new int[]{-1, Integer.MIN_VALUE, 10, 11, Integer.MAX_VALUE}) {
            final int ci = i;

            checkAIOOBE(() -> {
                boolean x = (boolean) vh.get(array, ci);
            });

            checkAIOOBE(() -> {
                vh.set(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean x = (boolean) vh.getVolatile(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setVolatile(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean x = (boolean) vh.getAcquire(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setRelease(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean x = (boolean) vh.getOpaque(array, ci);
            });

            checkAIOOBE(() -> {
                vh.setOpaque(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean r = vh.compareAndSet(array, ci, true, false);
            });

            checkAIOOBE(() -> {
                boolean r = (boolean) vh.compareAndExchange(array, ci, false, true);
            });

            checkAIOOBE(() -> {
                boolean r = (boolean) vh.compareAndExchangeAcquire(array, ci, false, true);
            });

            checkAIOOBE(() -> {
                boolean r = (boolean) vh.compareAndExchangeRelease(array, ci, false, true);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetPlain(array, ci, true, false);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSet(array, ci, true, false);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetAcquire(array, ci, true, false);
            });

            checkAIOOBE(() -> {
                boolean r = vh.weakCompareAndSetRelease(array, ci, true, false);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndSet(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndSetAcquire(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndSetRelease(array, ci, true);
            });


            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseOr(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseOrAcquire(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseOrRelease(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseAnd(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseAndAcquire(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseAndRelease(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseXor(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseXorAcquire(array, ci, true);
            });

            checkAIOOBE(() -> {
                boolean o = (boolean) vh.getAndBitwiseXorRelease(array, ci, true);
            });
        }
    }

}

