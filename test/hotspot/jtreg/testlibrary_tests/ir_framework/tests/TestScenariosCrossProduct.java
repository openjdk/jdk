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

import java.util.Set;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.shared.TestRunException;
import jdk.test.lib.Asserts;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test cross product scenarios with the framework.
 * @library /test/lib /testlibrary_tests /
 * @run driver ir_framework.tests.TestScenariosCrossProduct
 */

public class TestScenariosCrossProduct {
    static void hasNFailures(String s, int count) {
        if (!s.matches("The following scenarios have failed: (#[0-9](, )?){" + count + "}. Please check stderr for more information.")) {
            throw new RuntimeException("Expected " + count + " failures in \"" + s + "\"");
        }
    }

    public static void main(String[] args) {
        // Single set should test all flags in the set by themselves.
        try {
            TestFramework t1 = new TestFramework();
            t1.addCrossProductScenarios(Set.of("-XX:TLABRefillWasteFraction=51",
                                               "-XX:TLABRefillWasteFraction=53",
                                               "-XX:TLABRefillWasteFraction=64"));
            t1.start();
            Asserts.fail("Should have thrown exception");
        } catch (TestRunException e) {
            hasNFailures(e.getMessage(), 3);
        }

        // The cross product of a set with one element and a set with three elements is three sets.
        try {
            TestFramework t2 = new TestFramework();
            t2.addCrossProductScenarios(Set.of("-XX:TLABRefillWasteFraction=53"),
                                        Set.of("-XX:+UseNewCode", "-XX:+UseNewCode2", "-XX:+UseNewCode3"));
            t2.start();
            Asserts.fail("Should have thrown exception");
        } catch (TestRunException e) {
            hasNFailures(e.getMessage(), 3);
        }

        // The cross product of two sets with two elements is four sets.
        try {
            TestFramework t3 = new TestFramework();
            t3.addCrossProductScenarios(Set.of("-XX:TLABRefillWasteFraction=53", "-XX:TLABRefillWasteFraction=64"),
                                        Set.of("-XX:+UseNewCode", "-XX:-UseNewCode"));
            t3.start();
            Asserts.fail("Should have thrown exception");
        } catch (TestRunException e) {
            hasNFailures(e.getMessage(), 4);
        }

        try {
            TestFramework t4 = new TestFramework();
            t4.addCrossProductScenarios(Set.of("-XX:TLABRefillWasteFraction=50 -XX:+UseNewCode", "-XX:TLABRefillWasteFraction=40"),
                                        Set.of("-XX:+UseNewCode2"));
            t4.start();
            Asserts.fail("Should have thrown exception");
        } catch (TestRunException e) {
            hasNFailures(e.getMessage(), 1);
        }

        // Test with an empty string. All 6 scenarios fail because 64 is the default value for TLABRefillWasteFraction.
        try {
            TestFramework t5 = new TestFramework();
            t5.addCrossProductScenarios(Set.of("", "-XX:TLABRefillWasteFraction=51", "-XX:TLABRefillWasteFraction=53"),
                                        Set.of("-XX:+UseNewCode", "-XX:+UseNewCode2"));
            t5.start();
            Asserts.fail("Should have thrown exception");
        } catch (TestRunException e) {
            hasNFailures(e.getMessage(), 6);
        }
    }

    @Test
    @IR(applyIf = {"TLABRefillWasteFraction", "64"}, counts = {IRNode.CALL, "1"})
    public void failDefault() {
    }

    @Test
    @IR(applyIf = {"TLABRefillWasteFraction", "51"}, counts = {IRNode.CALL, "1"})
    public void fail1() {
    }

    @Test
    @IR(applyIf = {"TLABRefillWasteFraction", "53"}, counts = {IRNode.CALL, "1"})
    public void fail2() {
    }

    @Test
    @IR(applyIfAnd = {"TLABRefillWasteFraction", "50", "UseNewCode", "true"}, counts = {IRNode.CALL, "1"})
    public void failPair() {
    }
}
