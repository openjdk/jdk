/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.test.TestVM;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/*
 * @test
 * @requires vm.compiler2.enabled & vm.flagless
 * @summary Test basics of the framework. This test runs directly the test VM which normally does not happen.
 * @library /test/lib /
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI
 *                   -Xbatch ir_framework.tests.TestBasics
 */

public class TestBasics {
    private static boolean wasExecuted = false;
    private boolean lastToggleBoolean = true;
    private final static HashMap<String, Integer> executed = HashMap.newHashMap(100);
    private final static int[] executedOnce = new int[5];
    private long[] nonFloatingRandomNumbers = new long[10];
    private double[] floatingRandomNumbers = new double[10];
    private Boolean[] randomBooleans = new Boolean[64];

    public static void main(String[] args) throws Exception {
        // Run on same VM to make this test easier as we are not interested in any output processing.
        Class<?> c = TestFramework.class; // Enable JTreg test to compile TestFramework
        Method runTestsOnSameVM = TestVM.class.getDeclaredMethod("runTestsOnSameVM", Class.class);
        runTestsOnSameVM.setAccessible(true);
        runTestsOnSameVM.invoke(null, new Object[]{ null });

        if (wasExecuted) {
            throw new RuntimeException("Executed non @Test method or a method that was not intended to be run");
        }
        for (Map.Entry<String, Integer> entry : executed.entrySet()) {
            int value = entry.getValue();
            if (value != TestVM.WARMUP_ITERATIONS + 1) {
                // Warmups + 1 C2 compiled invocation
                throw new RuntimeException("Test " + entry.getKey() + "  was executed " + value + " times instead stead of "
                        + (TestVM.WARMUP_ITERATIONS + 1) + " times." );
            }
        }

        for (int value : executedOnce) {
            if (value != 1) {
                throw new RuntimeException("Check function should have been executed exactly once");
            }
        }
    }

    private void clearNonFloatingRandomNumbers() {
        nonFloatingRandomNumbers = new long[10];
    }

    private void clearFloatingRandomNumbers() {
        floatingRandomNumbers = new double[10];
    }

    private void clearRandomBooleans() {
        randomBooleans = new Boolean[64];
    }

    // Not a test
    public void noTest() {
        wasExecuted = true;
    }

    // Not a test
    public void test2() {
        wasExecuted = true;
    }

    // Can overload non- @Test
    public static void test2(int i) {
        wasExecuted = true;
    }

    @Test
    public static void staticTest() {
        executed.merge("staticTest", 1, Integer::sum);
    }

    @Test
    public final void finalTest() {
        executed.merge("finalTest", 1, Integer::sum);
    }

    @Test
    public int returnValueTest() {
        executed.merge("returnValueTest", 1, Integer::sum);
        return 4;
    }

    // Base test, with arguments, directly invoked.
    // Specify the argument values with @Arguments
    @Test
    @Arguments(values = Argument.DEFAULT)
    public void byteDefaultArgument(byte x) {
        executed.merge("byteDefaultArgument", 1, Integer::sum);
        if (x != 0) {
            throw new RuntimeException("Must be 0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void shortDefaultArgument(short x) {
        executed.merge("shortDefaultArgument", 1, Integer::sum);
        if (x != 0) {
            throw new RuntimeException("Must be 0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void intDefaultArgument(int x) {
        executed.merge("intDefaultArgument", 1, Integer::sum);
        if (x != 0) {
            throw new RuntimeException("Must be 0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void longDefaultArgument(long x) {
        executed.merge("longDefaultArgument", 1, Integer::sum);
        if (x != 0L) {
            throw new RuntimeException("Must be 0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void floatDefaultArgument(float x) {
        executed.merge("floatDefaultArgument", 1, Integer::sum);
        if (x != 0.0f) {
            throw new RuntimeException("Must be 0.0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void doubleDefaultArgument(double x) {
        executed.merge("doubleDefaultArgument", 1, Integer::sum);
        if (x != 0.0f) {
            throw new RuntimeException("Must be 0.0");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void charDefaultArgument(char x) {
        executed.merge("charDefaultArgument", 1, Integer::sum);
        if (x != '\u0000') {
            throw new RuntimeException("Must be \u0000");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void booleanDefaultArgument(boolean x) {
        executed.merge("booleanDefaultArgument", 1, Integer::sum);
        if (x) {
            throw new RuntimeException("Must be false");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void stringObjectDefaultArgument(String x) {
        executed.merge("stringObjectDefaultArgument", 1, Integer::sum);
        if (x == null || x.length() != 0) {
            throw new RuntimeException("Default string object must be non-null and having a length of zero");
        }
    }

    @Test
    @Arguments(values = Argument.DEFAULT)
    public void defaultObjectDefaultArgument(DefaultObject x) {
        executed.merge("defaultObjectDefaultArgument", 1, Integer::sum);
        if (x == null || x.i != 4) {
            throw new RuntimeException("Default object must not be null and its i field must be 4");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void byte42(byte x) {
        executed.merge("byte42", 1, Integer::sum);
        if (x != 42) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void short42(short x) {
        executed.merge("short42", 1, Integer::sum);
        if (x != 42) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void int42(int x) {
        executed.merge("int42", 1, Integer::sum);
        if (x != 42) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void long42(long x) {
        executed.merge("long42", 1, Integer::sum);
        if (x != 42) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void float42(float x) {
        executed.merge("float42", 1, Integer::sum);
        if (x != 42.0) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public void double42(double x) {
        executed.merge("double42", 1, Integer::sum);
        if (x != 42.0) {
            throw new RuntimeException("Must be 42");
        }
    }

    @Test
    @Arguments(values = Argument.FALSE)
    public void booleanFalse(boolean x) {
        executed.merge("booleanFalse", 1, Integer::sum);
        if (x) {
            throw new RuntimeException("Must be false");
        }
    }

    @Test
    @Arguments(values = Argument.TRUE)
    public void booleanTrue(boolean x) {
        executed.merge("booleanTrue", 1, Integer::sum);
        if (!x) {
            throw new RuntimeException("Must be true");
        }
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomByte(byte x) {
        executed.merge("randomByte", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomShort(short x) {
        executed.merge("randomShort", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomInt(int x) {
        executed.merge("randomInt", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomLong(long x) {
        executed.merge("randomLong", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomFloat(float x) {
        executed.merge("randomFloat", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomDouble(double x) {
        executed.merge("randomDouble", 1, Integer::sum);
    }

    // Not executed
    public void randomNotExecutedTest(double x) {
        wasExecuted = true;
    }

    @Test
    @Arguments(values = Argument.RANDOM_ONCE)
    public void randomBoolean(boolean x) {
        executed.merge("randomBoolean", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.BOOLEAN_TOGGLE_FIRST_FALSE)
    public void booleanToggleFirstFalse(boolean x) {
        if (!executed.containsKey("booleanToggleFirstFalse")) {
            // First invocation
            if (x) {
                throw new RuntimeException("BOOLEAN_TOGGLE_FIRST_FALSE must be false on first invocation");
            }
        } else if (x == lastToggleBoolean) {
            throw new RuntimeException("BOOLEAN_TOGGLE_FIRST_FALSE did not toggle");
        }
        lastToggleBoolean = x;
        executed.merge("booleanToggleFirstFalse", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachByte(byte x) {
        checkNonFloatingRandomNumber(x, executed.getOrDefault("randomEachByte", 0));
        executed.merge("randomEachByte", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachShort(short x) {
        checkNonFloatingRandomNumber(x, executed.getOrDefault("randomEachShort", 0));
        executed.merge("randomEachShort", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachInt(int x) {
        checkNonFloatingRandomNumber(x, executed.getOrDefault("randomEachInt", 0));
        executed.merge("randomEachInt", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachLong(long x) {
        checkNonFloatingRandomNumber(x, executed.getOrDefault("randomEachLong", 0));
        executed.merge("randomEachLong", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachChar(char x) {
        checkNonFloatingRandomNumber(x, executed.getOrDefault("randomEachChar", 0));
        executed.merge("randomEachChar", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachFloat(float x) {
        checkFloatingRandomNumber(x, executed.getOrDefault("randomEachFloat", 0));
        executed.merge("randomEachFloat", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachDouble(double x) {
        checkFloatingRandomNumber(x, executed.getOrDefault("randomEachDouble", 0));
        executed.merge("randomEachDouble", 1, Integer::sum);
    }

    @Test
    @Arguments(values = Argument.RANDOM_EACH)
    public void randomEachBoolean(boolean x) {
        checkRandomBoolean(x, executed.getOrDefault("randomEachBoolean", 0));
        executed.merge("randomEachBoolean", 1, Integer::sum);
    }

    private void checkNonFloatingRandomNumber(long x, int invocationCount) {
        int mod10 = invocationCount % 10;
        if (invocationCount > 0 && mod10 == 0) {
            // Not first invocation
            // Check the last 10 numbers and ensure that there are at least 2 different ones.
            // All numbers are equal? Very unlikely nd we should really consider to play the lottery...
            long first = nonFloatingRandomNumbers[0];
            if (Arrays.stream(nonFloatingRandomNumbers).allMatch(n -> n == first)) {
                throw new RuntimeException("RANDOM_EACH does not generate random integer numbers");
            }
            clearNonFloatingRandomNumbers();
        }
        nonFloatingRandomNumbers[mod10] = x;
    }

    private void checkFloatingRandomNumber(double x, int invocationCount) {
        int mod10 = invocationCount % 10;
        if (invocationCount > 0 && mod10 == 0) {
            // Not first invocation
            // Check the last 10 numbers and ensure that there are at least 2 different ones.
            // All numbers are equal? Very unlikely nd we should really consider to play the lottery...
            double first = floatingRandomNumbers[0];
            if (Arrays.stream(floatingRandomNumbers).allMatch(n -> n == first)) {
                throw new RuntimeException("RANDOM_EACH does not generate random floating point numbers");
            }
            clearFloatingRandomNumbers();
        }
        floatingRandomNumbers[mod10] = x;
    }

    private void checkRandomBoolean(boolean x, int invocationCount) {
        int mod64 = invocationCount % 64;
        if (invocationCount > 0 && mod64 == 0) {
            // Not first invocation
            // Check the last 64 booleans and ensure that there are at least one true and one false.
            // All booleans are equal? Very unlikely (chance of 2^64) and we should really consider
            // to play the lottery...
            if (Arrays.stream(randomBooleans).allMatch(b -> b == randomBooleans[0])) {
                throw new RuntimeException("RANDOM_EACH does not generate random booleans");
            }
            clearRandomBooleans();
        }
        randomBooleans[mod64] = x;
    }


    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void byteMinus42(byte x) {
        executed.merge("byteMinus42", 1, Integer::sum);
        if (x != -42) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void shortMinus42(short x) {
        executed.merge("shortMinus42", 1, Integer::sum);
        if (x != -42) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void intMinus42(int x) {
        executed.merge("intMinus42", 1, Integer::sum);
        if (x != -42) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void longMinus42(long x) {
        executed.merge("longMinus42", 1, Integer::sum);
        if (x != -42) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void floatMinus42(float x) {
        executed.merge("floatMinus42", 1, Integer::sum);
        if (x != -42.0) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.NUMBER_MINUS_42)
    public void doubleMinus42(double x) {
        executed.merge("doubleMinus42", 1, Integer::sum);
        if (x != -42.0) {
            throw new RuntimeException("Must be -42");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void byteMin(byte x) {
        executed.merge("byteMin", 1, Integer::sum);
        if (x != Byte.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void charMin(char x) {
        executed.merge("charMin", 1, Integer::sum);
        if (x != Character.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void shortMin(short x) {
        executed.merge("shortMin", 1, Integer::sum);
        if (x != Short.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void intMin(int x) {
        executed.merge("intMin", 1, Integer::sum);
        if (x != Integer.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void longMin(long x) {
        executed.merge("longMin", 1, Integer::sum);
        if (x != Long.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void floatMin(float x) {
        executed.merge("floatMin", 1, Integer::sum);
        if (x != Float.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MIN)
    public void doubleMin(double x) {
        executed.merge("doubleMin", 1, Integer::sum);
        if (x != Double.MIN_VALUE) {
            throw new RuntimeException("Must be MIN_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void byteMax(byte x) {
        executed.merge("byteMax", 1, Integer::sum);
        if (x != Byte.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void charMax(char x) {
        executed.merge("charMax", 1, Integer::sum);
        if (x != Character.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void shortMax(short x) {
        executed.merge("shortMax", 1, Integer::sum);
        if (x != Short.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void intMax(int x) {
        executed.merge("intMax", 1, Integer::sum);
        if (x != Integer.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void longMax(long x) {
        executed.merge("longMax", 1, Integer::sum);
        if (x != Long.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void floatMax(float x) {
        executed.merge("floatMax", 1, Integer::sum);
        if (x != Float.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = Argument.MAX)
    public void doubleMax(double x) {
        executed.merge("doubleMax", 1, Integer::sum);
        if (x != Double.MAX_VALUE) {
            throw new RuntimeException("Must be MAX_VALUE");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault1(byte x, short y) {
        executed.merge("twoArgsDefault1", 1, Integer::sum);
        if (x != 0 || y != 0) {
            throw new RuntimeException("Both must be 0");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault2(int x, short y) {
        executed.merge("twoArgsDefault2", 1, Integer::sum);
        if (x != 0 || y != 0) {
            throw new RuntimeException("Both must be 0");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault3(short x, long y) {
        executed.merge("twoArgsDefault3", 1, Integer::sum);
        if (x != 0 || y != 0) {
            throw new RuntimeException("Both must be 0");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault4(float x, boolean y) {
        executed.merge("twoArgsDefault4", 1, Integer::sum);
        if (x != 0.0 || y) {
            throw new RuntimeException("Must be 0 and false");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault5(boolean x, char y) {
        executed.merge("twoArgsDefault5", 1, Integer::sum);
        if (x || y != '\u0000') {
            throw new RuntimeException("Must be false and \u0000");
        }
    }

    @Test
    @Arguments(values = {Argument.DEFAULT, Argument.DEFAULT})
    public void twoArgsDefault6(char x, byte y) {
        executed.merge("twoArgsDefault6", 1, Integer::sum);
        if (x != '\u0000' || y != 0) {
            throw new RuntimeException("Must be\u0000 and 0");
        }
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE})
    public void twoArgsRandomOnce(char x, byte y) {
        executed.merge("twoArgsRandomOnce", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE})
    public void checkRandomOnceDifferentArgs(int a, int b, int c, int d, int e, int f, int g, int h) {
        if (Stream.of(a, b, c, d, e, f, g, h).allMatch(i -> i == a)) {
            throw new RuntimeException("RANDOM_ONCE does not produce random values for different arguments");
        }
        executed.merge("checkRandomOnceDifferentArgs", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_ONCE, Argument.RANDOM_ONCE})
    public void checkMixedRandoms1(byte a, short b, int c, long d, char e, boolean f, float g, double h) {
        executed.merge("checkMixedRandoms1", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_EACH, Argument.RANDOM_EACH,
                Argument.RANDOM_EACH, Argument.RANDOM_EACH,
                Argument.RANDOM_EACH, Argument.RANDOM_EACH,
                Argument.RANDOM_EACH, Argument.RANDOM_EACH})
    public void checkMixedRandoms2(byte a, short b, int c, long d, char e, boolean f, float g, double h) {
        executed.merge("checkMixedRandoms2", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.RANDOM_ONCE, Argument.RANDOM_ONCE,
                Argument.RANDOM_EACH, Argument.RANDOM_EACH,
                Argument.RANDOM_ONCE, Argument.RANDOM_EACH,
                Argument.RANDOM_EACH, Argument.RANDOM_ONCE})
    public void checkMixedRandoms3(byte a, short b, int c, long d, char e, boolean f, float g, double h) {
        executed.merge("checkMixedRandoms3", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_42, Argument.NUMBER_42,
                Argument.NUMBER_42, Argument.NUMBER_42,
                Argument.NUMBER_42, Argument.NUMBER_42})
    public void check42Mix1(byte a, short b, int c, long d, float e, double f) {
        if (a != 42 || b != 42 || c != 42 || d != 42 || e != 42.0 || f != 42.0) {
            throw new RuntimeException("Must all be 42");
        }
        executed.merge("check42Mix1", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_MINUS_42, Argument.NUMBER_MINUS_42,
                Argument.NUMBER_MINUS_42, Argument.NUMBER_MINUS_42,
                Argument.NUMBER_MINUS_42, Argument.NUMBER_MINUS_42})
    public void check42Mix2(byte a, short b, int c, long d, float e, double f) {
        if (a != -42 || b != -42 || c != -42 || d != -42 || e != -42.0 || f != -42.0) {
            throw new RuntimeException("Must all be -42");
        }
        executed.merge("check42Mix2", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.NUMBER_MINUS_42, Argument.NUMBER_42,
                Argument.NUMBER_MINUS_42, Argument.NUMBER_MINUS_42,
                Argument.NUMBER_42, Argument.NUMBER_MINUS_42})
    public void check42Mix3(byte a, short b, int c, long d, float e, double f) {
        if (a != -42 || b != 42 || c != -42 || d != -42 || e != 42.0 || f != -42.0) {
            throw new RuntimeException("Do not match the right 42 version");
        }
        executed.merge("check42Mix3", 1, Integer::sum);
    }


    @Test
    @Arguments(values = Argument.BOOLEAN_TOGGLE_FIRST_TRUE)
    public void booleanToggleFirstTrue(boolean x) {
        if (executed.getOrDefault("booleanToggleFirstTrue", 0) == 0) {
            // First invocation
            if (!x) {
                throw new RuntimeException("BOOLEAN_TOGGLE_FIRST_FALSE must be false on first invocation");
            }
        } else if (x == lastToggleBoolean) {
            throw new RuntimeException("BOOLEAN_TOGGLE_FIRST_FALSE did not toggle");
        }
        lastToggleBoolean = x;
        executed.merge("booleanToggleFirstTrue", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.BOOLEAN_TOGGLE_FIRST_FALSE, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    public void checkTwoToggles(boolean b1, boolean b2) {
        if (executed.getOrDefault("checkTwoToggles", 0) == 0) {
            // First invocation
            if (b1 || !b2) {
                throw new RuntimeException("BOOLEAN_TOGGLES have wrong initial value");
            }
        } else if (b1 == b2) {
            throw new RuntimeException("Boolean values must be different");
        } else if (b1 == lastToggleBoolean) {
            throw new RuntimeException("Booleans did not toggle");
        }
        lastToggleBoolean = b1;
        executed.merge("checkTwoToggles", 1, Integer::sum);
    }

    @Test
    @Arguments(values = {Argument.BOOLEAN_TOGGLE_FIRST_FALSE, Argument.FALSE,
                Argument.TRUE, Argument.BOOLEAN_TOGGLE_FIRST_TRUE})
    public void booleanMix(boolean b1, boolean b2, boolean b3, boolean b4) {
        if (executed.getOrDefault("booleanMix", 0) == 0) {
            // First invocation
            if (b1 || b2 || !b3 || !b4) {
                throw new RuntimeException("BOOLEAN_TOGGLES have wrong initial value");
            }
        } else if (b1 == b4) {
            throw new RuntimeException("Boolean values must be different");
        } else if (b1 == lastToggleBoolean) {
            throw new RuntimeException("Booleans did not toggle");
        }
        lastToggleBoolean = b1;
        executed.merge("booleanMix", 1, Integer::sum);
    }

    /*
     * Checked tests.
     */

    @Test
    public int testCheck() {
        executed.merge("testCheck", 1, Integer::sum);
        return 1;
    }

    // Checked test. Check invoked after invoking "testCheck". Perform some more things after invocation.
    @Check(test = "testCheck")
    public void checkTestCheck() {
        executed.merge("checkTestCheck", 1, Integer::sum); // Executed on each invocation
    }

    @Test
    public int testCheckReturn() {
        executed.merge("testCheckReturn", 1, Integer::sum);
        return 2;
    }

    // Checked test with return value. Perform checks on it.
    @Check(test = "testCheckReturn")
    public void checkTestCheckReturn(int returnValue) {
        if (returnValue != 2) {
            throw new RuntimeException("Must be 2");
        }
        executed.merge("checkTestCheckReturn", 1, Integer::sum); // Executed on each invocation
    }

    @Test
    @Arguments(values = Argument.NUMBER_42)
    public short testCheckWithArgs(short x) {
        executed.merge("testCheckWithArgs", 1, Integer::sum);
        return x;
    }

    @Check(test = "testCheckWithArgs")
    public void checkTestCheckWithArgs(short returnValue) {
        if (returnValue != 42) {
            throw new RuntimeException("Must be 42");
        }
        executed.merge("checkTestCheckWithArgs", 1, Integer::sum); // Executed on each invocation
    }

    @Test
    public int testCheckTestInfo() {
        executed.merge("testCheckTestInfo", 1, Integer::sum);
        return 3;
    }

    // Checked test with info object about test.
    @Check(test = "testCheckTestInfo")
    public void checkTestCheckTestInfo(TestInfo testInfo) {
        executed.merge("checkTestCheckTestInfo(TestInfo)", 1, Integer::sum); // Executed on each invocation
    }


    @Test
    public int testCheckBoth() {
        executed.merge("testCheckBoth", 1, Integer::sum);
        return 4;
    }

    // Checked test with return value and info object about test.
    @Check(test = "testCheckBoth")
    public void checkTestCheckTestInfo(int returnValue, TestInfo testInfo) {
        if (returnValue != 4) {
            throw new RuntimeException("Must be 4");
        }
        executed.merge("checkTestCheckTestInfo(int, TestInfo)", 1, Integer::sum); // Executed on each invocation
    }

    @Test
    public int testCheckOnce() {
        executed.merge("testCheckOnce", 1, Integer::sum);
        return 1;
    }

    // Check method only invoked once after method is compiled after warm up.
    @Check(test = "testCheckOnce", when = CheckAt.COMPILED)
    public void checkTestCheckOnce() {
        executedOnce[0]++; // Executed once
    }

    @Test
    public int testCheckReturnOnce() {
        executed.merge("testCheckReturnOnce", 1, Integer::sum);
        return 2;
    }

    @Check(test = "testCheckReturnOnce", when = CheckAt.COMPILED)
    public void checkTestCheckReturnOnce(int returnValue) {
        if (returnValue != 2) {
            throw new RuntimeException("Must be 2");
        }
        executedOnce[1]++; // Executed once
    }

    @Test
    public int testCheckTestInfoOnce() {
        executed.merge("testCheckTestInfoOnce", 1, Integer::sum);
        return 3;
    }

    @Check(test = "testCheckTestInfoOnce", when = CheckAt.COMPILED)
    public void checkTestCheckTestInfoOnce(TestInfo testInfo) {
        executedOnce[2]++; // Executed once
    }

    @Test
    public int testCheckBothOnce() {
        executed.merge("testCheckBothOnce", 1, Integer::sum);
        return 4;
    }

    @Check(test = "testCheckBothOnce", when = CheckAt.COMPILED)
    public void checkTestCheckBothOnce(int returnValue, TestInfo testInfo) {
        if (returnValue != 4) {
            throw new RuntimeException("Must be 4");
        }
        executedOnce[3]++; // Executed once
    }

    @Test
    public void testRun() {
        executed.merge("testRun", 1, Integer::sum);
    }

    // Custom run test. This method is invoked each time instead of @Test method. This method responsible for calling
    // the @Test method. @Test method is compiled after warm up. This is similar to the verifiers in the old Valhalla framework.
    @Run(test = "testRun")
    public void runTestRun(RunInfo info) {
        testRun();
    }

    @Test
    public void testRunNoTestInfo(int i) { // Argument allowed when run by @Run
        executed.merge("testRunNoTestInfo", 1, Integer::sum);
    }

    @Run(test = "testRunNoTestInfo")
    public void runTestRunNoTestInfo() {
        testRunNoTestInfo(3);
    }

    @Test
    public void testNotRun() {
        wasExecuted = true;
    }

    @Run(test = "testNotRun")
    public void runTestNotRun() {
        // Do not execute the test. Pointless but need to test that as well.
    }

    @Test
    public void testRunOnce() {
        executedOnce[4]++;
    }

    // Custom run test that is only invoked once. There is no warm up and no compilation. This method is responsible
    // for triggering compilation.
    @Run(test = "testRunOnce", mode = RunMode.STANDALONE)
    public void runTestRunOnce(RunInfo info) {
        testRunOnce();
    }

    @Test
    public void testRunOnce2() {
        executed.merge("testRunOnce2", 1, Integer::sum);
    }

    @Run(test = "testRunOnce2", mode = RunMode.STANDALONE)
    public void runTestRunOnce2(RunInfo info) {
        for (int i = 0; i < TestVM.WARMUP_ITERATIONS + 1; i++) {
            testRunOnce2();
        }
    }

    @Test
    public void testRunMultiple() {
        executed.merge("testRunMultiple", 1, Integer::sum);
    }

    @Test
    public void testRunMultiple2() {
        executed.merge("testRunMultiple2", 1, Integer::sum);
    }

    @Test
    public void testRunMultipleNotExecuted() {
        wasExecuted = true;
    }

    @Run(test = {"testRunMultiple", "testRunMultiple2", "testRunMultipleNotExecuted"})
    public void runTestRunMultiple() {
        testRunMultiple();
        testRunMultiple2();
    }


    @Test
    public void testRunMultiple3() {
        executed.merge("testRunMultiple3", 1, Integer::sum);
    }

    @Test
    public void testRunMultiple4() {
        executed.merge("testRunMultiple4", 1, Integer::sum);
    }

    @Test
    public void testRunMultipleNotExecuted2() {
        wasExecuted = true;
    }

    @Run(test = {"testRunMultiple3", "testRunMultiple4", "testRunMultipleNotExecuted2"}, mode = RunMode.STANDALONE)
    public void runTestRunMultipl2(RunInfo info) {
        for (int i = 0; i < TestVM.WARMUP_ITERATIONS + 1; i++) {
            testRunMultiple3();
            testRunMultiple4();
        }
    }
}

class DefaultObject {
    int i = 4;
}
