/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit/othervm -Xverify:all TestTableSwitch
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestTableSwitch {

    static final MethodHandle MH_IntConsumer_accept;
    static final MethodHandle MH_check;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_IntConsumer_accept = lookup.findVirtual(IntConsumer.class, "accept",
                    MethodType.methodType(void.class, int.class));
            MH_check = lookup.findStatic(TestTableSwitch.class, "check",
                    MethodType.methodType(void.class, List.class, Object[].class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static MethodHandle simpleTestCase(String value) {
        return simpleTestCase(String.class, value);
    }

    public static MethodHandle simpleTestCase(Class<?> type, Object value) {
        return MethodHandles.dropArguments(MethodHandles.constant(type, value), 0, int.class);
    }

    public static Object testValue(Class<?> type) {
        if (type == String.class) {
            return "X";
        } else if (type == byte.class) {
            return (byte) 42;
        } else if (type == short.class) {
            return (short) 84;
        } else if (type == char.class) {
            return 'Y';
        } else if (type == int.class) {
            return 168;
        } else if (type == long.class) {
            return 336L;
        } else if (type == float.class) {
            return 42F;
        } else if (type == double.class) {
            return 84D;
        } else if (type == boolean.class) {
            return true;
        }
        return null;
    }

    static final Class<?>[] TEST_TYPES = {
        Object.class,
        String.class,
        byte.class,
        short.class,
        char.class,
        int.class,
        long.class,
        float.class,
        double.class,
        boolean.class
    };

    public static Object[] testArguments(int caseNum, List<Object> testValues) {
        Object[] args = new Object[testValues.size() + 1];
        args[0] = caseNum;
        int insertPos = 1;
        for (Object testValue : testValues) {
            args[insertPos++] = testValue;
        }
        return args;
    }

    public static Object[][] nonVoidCases() {
        List<Object[]> tests = new ArrayList<>();

        for (Class<?> returnType : TEST_TYPES) {
            for (int numCases = 1; numCases < 5; numCases++) {
                tests.add(new Object[] { returnType, numCases, List.of() });
                tests.add(new Object[] { returnType, numCases, List.of(TEST_TYPES) });
            }
        }

        return tests.toArray(Object[][]::new);
    }

    private static void check(List<Object> testValues, Object[] collectedValues) {
        assertArrayEquals(testValues.toArray(), collectedValues);
    }

    @ParameterizedTest
    @MethodSource("nonVoidCases")
    public void testNonVoidHandles(Class<?> type, int numCases, List<Class<?>> additionalTypes) throws Throwable {
        MethodHandle collector = MH_check;
        List<Object> testArguments = new ArrayList<>();
        collector = MethodHandles.insertArguments(collector, 0, testArguments);
        collector = collector.asCollector(Object[].class, additionalTypes.size());

        Object defaultReturnValue = testValue(type);
        MethodHandle defaultCase = simpleTestCase(type, defaultReturnValue);
        defaultCase = MethodHandles.collectArguments(defaultCase, 1, collector);
        Object[] returnValues = new Object[numCases];
        MethodHandle[] cases = new MethodHandle[numCases];
        for (int i = 0; i < cases.length; i++) {
            Object returnValue = testValue(type);
            returnValues[i] = returnValue;
            MethodHandle theCase = simpleTestCase(type, returnValue);
            theCase = MethodHandles.collectArguments(theCase, 1, collector);
            cases[i] = theCase;
        }

        MethodHandle mhSwitch = MethodHandles.tableSwitch(
            defaultCase,
            cases
        );

        for (Class<?> additionalType : additionalTypes) {
            testArguments.add(testValue(additionalType));
        }

        assertEquals(defaultReturnValue, mhSwitch.invokeWithArguments(testArguments(-1, testArguments)));

        for (int i = 0; i < numCases; i++) {
            assertEquals(returnValues[i], mhSwitch.invokeWithArguments(testArguments(i, testArguments)));
        }

        assertEquals(defaultReturnValue, mhSwitch.invokeWithArguments(testArguments(numCases, testArguments)));
    }

    @Test
    public void testVoidHandles() throws Throwable {
        IntFunction<MethodHandle> makeTestCase = expectedIndex -> {
            IntConsumer test = actualIndex -> assertEquals(expectedIndex, actualIndex);
            return MH_IntConsumer_accept.bindTo(test);
        };

        MethodHandle mhSwitch = MethodHandles.tableSwitch(
            /* default: */ makeTestCase.apply(-1),
            /* case 0: */  makeTestCase.apply(0),
            /* case 1: */  makeTestCase.apply(1),
            /* case 2: */  makeTestCase.apply(2)
        );

        mhSwitch.invokeExact((int) -1);
        mhSwitch.invokeExact((int) 0);
        mhSwitch.invokeExact((int) 1);
        mhSwitch.invokeExact((int) 2);
    }

    @Test
    public void testNullDefaultHandle() {
        assertThrows(NullPointerException.class, () -> MethodHandles.tableSwitch(null, simpleTestCase("test")));
    }

    @Test
    public void testNullCases() {
        MethodHandle[] cases = null;
        assertThrows(NullPointerException.class, () ->
                MethodHandles.tableSwitch(simpleTestCase("default"), cases));
    }

    @Test
    public void testNullCase() {
        assertThrows(NullPointerException.class, () -> MethodHandles.tableSwitch(simpleTestCase("default"), simpleTestCase("case"), null));
    }

    @Test
    public void testNotEnoughCases() {
        assertThrows(IllegalArgumentException.class, () -> MethodHandles.tableSwitch(simpleTestCase("default")));
    }

    @Test
    public void testNotEnoughParameters() {
        MethodHandle empty = MethodHandles.empty(MethodType.methodType(void.class));
        assertThrows(IllegalArgumentException.class, () ->
                MethodHandles.tableSwitch(empty, empty, empty));
    }

    @Test
    public void testNoLeadingIntParameter() {
        MethodHandle empty = MethodHandles.empty(MethodType.methodType(void.class, double.class));
        assertThrows(IllegalArgumentException.class, () ->
                MethodHandles.tableSwitch(empty, empty, empty));
    }

    @Test
    public void testWrongCaseType() {
        // doesn't return a String
        MethodHandle wrongType = MethodHandles.empty(MethodType.methodType(void.class, int.class));
        assertThrows(IllegalArgumentException.class, () ->
                MethodHandles.tableSwitch(simpleTestCase("default"), simpleTestCase("case"), wrongType));
    }

}
