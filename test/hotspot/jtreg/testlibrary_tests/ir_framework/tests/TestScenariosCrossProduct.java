/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.shared.TestFormatException;
import compiler.lib.ir_framework.shared.TestRunException;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8365262 8369232
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test cross product scenarios with the framework.
 * @library /test/lib /testlibrary_tests /
 * @run driver ir_framework.tests.TestScenariosCrossProduct
 */

public class TestScenariosCrossProduct {

    public static void main(String[] args) {
        expectFormatFailure((Set<String>[]) null);
        expectFormatFailure(Set.of("foo", "bar"), null);

        try {
            TestFramework t = new TestFramework();
            t.addCrossProductScenarios(Set.of("blub"), Set.of("foo", null));
            shouldHaveThrown();
        } catch (NullPointerException _) {
            // Expected: Set.of prevents null elements
        }


        try {
            TestFramework t = new TestFramework();
            t.addCrossProductScenarios();
        } catch (TestFormatException e) {
            Asserts.fail("Should not have thrown exception");
        }

        // Single set should test all flags in the set by themselves.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("-XX:TLABRefillWasteFraction=51",
                                           "-XX:TLABRefillWasteFraction=53",
                                           "-XX:TLABRefillWasteFraction=64")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=51"),
                        Set.of("-XX:TLABRefillWasteFraction=53"),
                        Set.of("-XX:TLABRefillWasteFraction=64")
                ))
                .run();

        // The cross product of a set with one element and a set with three elements is three sets.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("-XX:TLABRefillWasteFraction=53"),
                                    Set.of("-XX:+UseNewCode", "-XX:+UseNewCode2", "-XX:+UseNewCode3")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode3")
                ))
                .run();


        // The cross product of two sets with two elements is four sets.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("-XX:TLABRefillWasteFraction=53", "-XX:TLABRefillWasteFraction=64"),
                                    Set.of("-XX:+UseNewCode", "-XX:-UseNewCode")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:-UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=64", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=64", "-XX:-UseNewCode")
                ))
                .run();


        // Test with a pair of flags.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("-XX:TLABRefillWasteFraction=50 -XX:+UseNewCode", "-XX:TLABRefillWasteFraction=40"),
                                    Set.of("-XX:+UseNewCode2")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=50", "-XX:+UseNewCode", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=40", "-XX:+UseNewCode2")
                ))
                .run();

        // Test with an empty string, resulting in 6 scenarios.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("", "-XX:TLABRefillWasteFraction=51", "-XX:TLABRefillWasteFraction=53"),
                                    Set.of("-XX:+UseNewCode", "-XX:+UseNewCode2")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:+UseNewCode"),
                        Set.of("-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode2")
                ))
                .run();

        // Test with 3 input sets which equals to 2x2x2 = 8 scenarios.
        new TestCase()
                .inputFlags(Set.of(
                                    Set.of("-XX:TLABRefillWasteFraction=51",
                                           "-XX:TLABRefillWasteFraction=53"),
                                    Set.of("-XX:+UseNewCode",
                                           "-XX:-UseNewCode"),
                                    Set.of("-XX:+UseNewCode2",
                                           "-XX:-UseNewCode2")
                            )
                )
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:-UseNewCode", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:-UseNewCode", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode", "-XX:-UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode", "-XX:-UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:-UseNewCode", "-XX:-UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:-UseNewCode", "-XX:-UseNewCode2")
                ))
                .run();

        TestFramework testFramework = new TestFramework();
        testFramework.addScenarios(new Scenario(0, "-XX:TLABRefillWasteFraction=50", "-XX:+UseNewCode"));
        testFramework.addCrossProductScenarios(Set.of("-XX:TLABRefillWasteFraction=51", "-XX:TLABRefillWasteFraction=53"),
                                               Set.of("-XX:+UseNewCode", "-XX:+UseNewCode2"));
        try {
            testFramework.addScenarios(new Scenario(4, "-XX:+UseNewCode3")); // fails because index 4 is already used
            shouldHaveThrown();
        } catch (TestFormatException _) {
            // Expected.
        }
        testFramework.addScenarios(new Scenario(5, "-XX:+UseNewCode3"));

        new TestCase()
                .expectedScenariosWithFlags(Set.of(
                        Set.of("-XX:TLABRefillWasteFraction=50", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=51", "-XX:+UseNewCode2"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode"),
                        Set.of("-XX:TLABRefillWasteFraction=53", "-XX:+UseNewCode2"),
                        Set.of("-XX:+UseNewCode3")
                ))
                .runWithPreAddedScenarios(testFramework);

        runEndToEndTest();
    }

    private static void expectFormatFailure(Set<String>... flagSets) {
        TestFramework testFramework = new TestFramework();
        try {
            testFramework.addCrossProductScenarios(flagSets);
            shouldHaveThrown();
        } catch (TestFormatException _) {
            // Expected.
        }
    }

    private static void shouldHaveThrown() {
        Asserts.fail("Should have thrown exception");
    }

    static class TestCase {
        private Set<Set<String>> inputFlags;
        private Set<Set<String>> expectedScenariosWithFlags;

        public TestCase inputFlags(Set<Set<String>> inputFlags) {
            this.inputFlags = inputFlags;
            return this;
        }

        public TestCase expectedScenariosWithFlags(Set<Set<String>> expectedScenariosWithFlags) {
            this.expectedScenariosWithFlags = expectedScenariosWithFlags;
            return this;
        }

        public void run() {
            TestFramework testFramework = new TestFramework();
            testFramework.addCrossProductScenarios(inputFlags.toArray(new Set[0]));
            runWithPreAddedScenarios(testFramework);
        }

        public void runWithPreAddedScenarios(TestFramework testFramework) {
            List<Scenario> scenariosFromCrossProduct = getScenarios(testFramework);
            assertScenarioCount(expectedScenariosWithFlags.size(), scenariosFromCrossProduct);
            assertScenariosWithFlags(scenariosFromCrossProduct, expectedScenariosWithFlags);
            assertSameResultWhenManuallyAdding(scenariosFromCrossProduct, expectedScenariosWithFlags);
        }

        private static void assertScenarioCount(int expectedCount, List<Scenario> scenarios) {
            Asserts.assertEQ(expectedCount, scenarios.size(), "Scenario count is off");
        }

        /**
         * Check that the added scenarios to the IR framework with TestFramework.addCrossProductScenarios()
         * (i.e. 'scenariosFromCrossProduct') match the expected flag combos (i.e. 'expectedScenariosWithFlags').
         */
        private static void assertScenariosWithFlags(List<Scenario> scenariosFromCrossProduct,
                                                     Set<Set<String>> expectedScenariosWithFlags) {
            for (Set<String> expectedScenarioFlags : expectedScenariosWithFlags) {
                if (scenariosFromCrossProduct.stream()
                        .map(Scenario::getFlags)
                        .map(Set::copyOf)
                        .anyMatch(flags -> flags.equals(expectedScenarioFlags))) {
                    continue;
                }
                System.err.println("Scenarios from cross product:");
                for (Scenario s : scenariosFromCrossProduct) {
                    System.err.println(Arrays.toString(s.getFlags().toArray()));
                }
                throw new RuntimeException("Could not find a scenario with the provided flags: " + Arrays.toString(expectedScenarioFlags.toArray()));
            }
        }

        /**
         * Add scenarios for the provided flag sets in 'expectedScenariosWithFlags' by using TestFramework.addScenarios().
         * We should end up with the same scenarios as if we added them with TestFramework.addCrossProductScenarios().
         * This is verified by this method by comparing the flags of the scenarios, ignoring scenario indices.
         */
        private static void assertSameResultWhenManuallyAdding(List<Scenario> scenariosFromCrossProduct,
                                                               Set<Set<String>> expectedScenariosWithFlags) {
            List<Scenario> expectedScenarios = getScenariosWithFlags(expectedScenariosWithFlags);
            List<Scenario> fetchedScenarios = addScenariosAndFetchFromFramework(expectedScenarios);
            assertSameScenarios(scenariosFromCrossProduct, fetchedScenarios);
        }

        private static List<Scenario> getScenariosWithFlags(Set<Set<String>> expectedScenariosWithFlags) {
            List<Scenario> expecedScenarioList = new ArrayList<>();
            int index = -1; // Use some different indices - should not matter what we choose.
            for (Set<String> expectedScenarioFlags : expectedScenariosWithFlags) {
                expecedScenarioList.add(new Scenario(index--, expectedScenarioFlags.toArray(new String[0])));
            }
            return expecedScenarioList;
        }

        private static List<Scenario> addScenariosAndFetchFromFramework(List<Scenario> expecedScenarioList) {
            TestFramework testFramework = new TestFramework();
            testFramework.addScenarios(expecedScenarioList.toArray(new Scenario[0]));
            return getScenarios(testFramework);
        }

        private static void assertSameScenarios(List<Scenario> scenariosFromCrossProduct,
                                                List<Scenario> expectedScenarios) {
            assertScenariosWithFlags(scenariosFromCrossProduct, fetchFlags(expectedScenarios));
        }

        private static Set<Set<String>> fetchFlags(List<Scenario> scenarios) {
            return scenarios.stream()
                    .map(scenario -> new HashSet<>(scenario.getFlags()))
                    .collect(Collectors.toSet());
        }
    }

    private static List<Scenario> getScenarios(TestFramework testFramework) {
        Field field;
        try {
            field = TestFramework.class.getDeclaredField("scenarios");
            field.setAccessible(true);
            return (List<Scenario>)field.get(testFramework);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *  Also run a simple end-to-end test to sanity check the API method. We capture the stderr to fetch the
     *  scenario flags.
     */
    private static void runEndToEndTest() {
        TestFramework testFramework = new TestFramework();

        // Capture stderr
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream);
        System.setErr(printStream);

        try {
            testFramework
                    .addCrossProductScenarios(Set.of("-XX:+UseNewCode", "-XX:-UseNewCode"),
                                              Set.of("-XX:+UseNewCode2", "-XX:-UseNewCode2"))
                    .addFlags()
                    .start();
            shouldHaveThrown();
        } catch (TestRunException e) {
            // Expected.
            System.setErr(originalErr);
            Asserts.assertTrue(e.getMessage().contains("The following scenarios have failed: #0, #1, #2, #3."));
            String stdErr = outputStream.toString();
            Asserts.assertTrue(stdErr.contains("Scenario flags: [-XX:+UseNewCode, -XX:+UseNewCode2]"));
            Asserts.assertTrue(stdErr.contains("Scenario flags: [-XX:-UseNewCode, -XX:-UseNewCode2]"));
            Asserts.assertTrue(stdErr.contains("Scenario flags: [-XX:+UseNewCode, -XX:-UseNewCode2]"));
            Asserts.assertTrue(stdErr.contains("Scenario flags: [-XX:-UseNewCode, -XX:+UseNewCode2]"));
            Asserts.assertEQ(4, scenarioCount(stdErr));
        }
    }

    public static int scenarioCount(String stdErr) {
        Pattern pattern = Pattern.compile("Scenario flags");
        Matcher matcher = pattern.matcher(stdErr);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    public void endToEndTest() {
        throw new RuntimeException("executed test");
    }
}
