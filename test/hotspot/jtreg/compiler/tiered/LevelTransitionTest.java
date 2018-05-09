/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test LevelTransitionTest
 * @summary Test the correctness of compilation level transitions for different methods
 * @library /test/lib /
 * @modules java.base/jdk.internal.misc
 *          java.management
 *
 * @build sun.hotspot.WhiteBox
 *        compiler.tiered.LevelTransitionTest
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox
 *                                sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm/timeout=240 -Xmixed -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI -XX:+TieredCompilation -XX:-UseCounterDecay
 *                   -XX:CompileCommand=compileonly,compiler.whitebox.SimpleTestCaseHelper::*
 *                   -XX:CompileCommand=compileonly,compiler.tiered.LevelTransitionTest$ExtendedTestCase$CompileMethodHolder::*
 *                   compiler.tiered.LevelTransitionTest
 */

package compiler.tiered;

import compiler.whitebox.CompilerWhiteBoxTest;
import compiler.whitebox.SimpleTestCase;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.concurrent.Callable;

public class LevelTransitionTest extends TieredLevelsTest {
    /**
     * Shows if method was profiled by being executed on levels 2 or 3
     */
    protected boolean isMethodProfiled;
    private int transitionCount;

    public static void main(String[] args) throws Throwable {
        assert (!CompilerWhiteBoxTest.skipOnTieredCompilation(false));

        CompilerWhiteBoxTest.main(LevelTransitionTest::new, args);
        // run extended test cases
        for (TestCase testCase : ExtendedTestCase.values()) {
            new LevelTransitionTest(testCase).runTest();
        }
    }

    protected LevelTransitionTest(TestCase testCase) {
        super(testCase);
        isMethodProfiled = testCase.isOsr(); // OSR methods were already profiled by warmup
        transitionCount = 0;
    }

    @Override
    protected void test() throws Exception {
        checkTransitions();
        deoptimize();
        printInfo();
        if (testCase.isOsr()) {
            // deoptimization makes the following transitions be unstable
            // methods go to level 3 before 4 because of uncommon_trap and reprofile
            return;
        }
        checkTransitions();
    }

    /**
     * Makes and verifies transitions between compilation levels
     */
    protected void checkTransitions() throws Exception {
        checkNotCompiled();
        boolean finish = false;
        while (!finish) {
            System.out.printf("Level transition #%d%n", ++transitionCount);
            int newLevel;
            int current = getCompLevel();
            int expected = getNextLevel(current);
            if (current == expected) {
                // if we are on expected level, just execute it more
                // to ensure that the level won't change
                System.out.printf("Method %s is already on expected level %d%n", method, expected);
                compile();
                newLevel = getCompLevel();
                finish = true;
            } else {
                newLevel = changeCompLevel();
                finish = false;
            }
            System.out.printf("Method %s is compiled on level %d. Expected level is %d%n", method, newLevel, expected);
            checkLevel(expected, newLevel);
            printInfo();
        }
        ;
    }

    /**
     * Gets next expected level for the test case on each transition.
     *
     * @param currentLevel a level the test case is compiled on
     * @return expected compilation level
     */
    protected int getNextLevel(int currentLevel) {
        int nextLevel = currentLevel;
        switch (currentLevel) {
            case CompilerWhiteBoxTest.COMP_LEVEL_NONE:
                nextLevel = isMethodProfiled ? CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION
                        : CompilerWhiteBoxTest.COMP_LEVEL_FULL_PROFILE;
                break;
            case CompilerWhiteBoxTest.COMP_LEVEL_LIMITED_PROFILE:
            case CompilerWhiteBoxTest.COMP_LEVEL_FULL_PROFILE:
                nextLevel = CompilerWhiteBoxTest.COMP_LEVEL_FULL_OPTIMIZATION;
                isMethodProfiled = true;
                break;
        }
        nextLevel = isTrivial() ? CompilerWhiteBoxTest.COMP_LEVEL_SIMPLE : nextLevel;
        return Math.min(nextLevel, CompilerWhiteBoxTest.TIERED_STOP_AT_LEVEL);
    }

    /**
     * Determines if tested method should be handled as trivial
     *
     * @return {@code true} for trivial methods, {@code false} otherwise
     */
    protected boolean isTrivial() {
        return testCase == ExtendedTestCase.ACCESSOR_TEST
                || testCase == SimpleTestCase.METHOD_TEST
                || testCase == SimpleTestCase.STATIC_TEST
                || (testCase == ExtendedTestCase.TRIVIAL_CODE_TEST && isMethodProfiled);
    }

    /**
     * Invokes {@linkplain #method} until its compilation level is changed.
     * Note that if the level won't change, it will be an endless loop
     *
     * @return compilation level the {@linkplain #method} was compiled on
     */
    protected int changeCompLevel() {
        int currentLevel = getCompLevel();
        int newLevel = currentLevel;
        int result = 0;
        while (currentLevel == newLevel) {
            result = compile(1);
            if (WHITE_BOX.isMethodCompiled(method, testCase.isOsr())) {
                newLevel = getCompLevel();
            }
        }
        return newLevel;
    }

    protected static class Helper {
        /**
         * Gets method from a specified class using its name
         *
         * @param aClass type method belongs to
         * @param name   the name of the method
         * @return {@link Method} that represents corresponding class method
         */
        public static Method getMethod(Class<?> aClass, String name) {
            Method method;
            try {
                method = aClass.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                throw new Error("TESTBUG: Unable to get method " + name, e);
            }
            return method;
        }

        /**
         * Gets {@link Callable} that invokes given method from the given object
         *
         * @param object the object the specified method is invoked from
         * @param name   the name of the method
         */
        public static Callable<Integer> getCallable(Object object, String name) {
            Method method = getMethod(object.getClass(), name);
            return () -> {
                try {
                    return Objects.hashCode(method.invoke(object));
                } catch (ReflectiveOperationException e) {
                    throw new Error("TESTBUG: Invocation failure", e);
                }
            };
        }
    }

    private static enum ExtendedTestCase implements CompilerWhiteBoxTest.TestCase {
        ACCESSOR_TEST("accessor"),
        NONTRIVIAL_METHOD_TEST("nonTrivialMethod"),
        TRIVIAL_CODE_TEST("trivialCode");

        private final Executable executable;
        private final Callable<Integer> callable;

        @Override
        public Executable getExecutable() {
            return executable;
        }

        @Override
        public Callable<Integer> getCallable() {
            return callable;
        }

        @Override
        public boolean isOsr() {
            return false;
        }

        private ExtendedTestCase(String methodName) {
            this.executable = LevelTransitionTest.Helper.getMethod(CompileMethodHolder.class, methodName);
            this.callable = LevelTransitionTest.Helper.getCallable(new CompileMethodHolder(), methodName);
        }

        private static class CompileMethodHolder {
            private final int iter = 10;
            private int field = 42;

            /**
             * Non-trivial method for threshold policy: contains loops
             */
            public int nonTrivialMethod() {
                int acc = 0;
                for (int i = 0; i < iter; i++) {
                    acc += i;
                }
                return acc;
            }

            /**
             * Field accessor method
             */
            public int accessor() {
                return field;
            }

            /**
             * Method considered as trivial by amount of code
             */
            public int trivialCode() {
                int var = 0xBAAD_C0DE;
                var *= field;
                return var;
            }
        }
    }

}

