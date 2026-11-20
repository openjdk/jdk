/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import java.util.Arrays;

/*
 * @test id=testVM
 * @bug 8380670
 * @summary Test that the IR Framework propagates the driver random seed.
 * @requires vm.compiler2.enabled & vm.flagless
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 ir_framework.tests.TestRandomSeed testVM
 */
/*
 * @test id=flagVM
 * @bug 8380670
 * @summary Test seed propagation through the Flag VM as well as the Test VM.
 * @requires vm.debug == true & vm.compiler2.enabled & vm.flagless
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 ir_framework.tests.TestRandomSeed flagVM
 */
/*
 * @test id=overrides
 * @bug 8380670
 * @summary Test that seed propagation preserves explicit seed overrides.
 * @requires vm.compiler2.enabled & vm.flagless
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 ir_framework.tests.TestRandomSeed overrides
 * @run main/othervm -Djdk.test.lib.random.seed=-1 -DPreferCommandLineFlags=true ir_framework.tests.TestRandomSeed overrides
 */
/*
 * @test id=jtregOptsTestVM
 * @bug 8380670
 * @summary Test that a seed supplied only through jtreg options is forwarded to the Test VM.
 * @requires vm.compiler2.enabled & vm.flagless
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 -Dtest.java.opts=-Djdk.test.lib.random.seed=42 ir_framework.tests.TestRandomSeed jtregOpts testVM
 * @run main/othervm -Djdk.test.lib.random.seed=-1 -Dtest.vm.opts=-Djdk.test.lib.random.seed=42 ir_framework.tests.TestRandomSeed jtregOpts testVM
 */
/*
 * @test id=jtregOptsFlagVM
 * @bug 8380670
 * @summary Test that a seed supplied only through jtreg options is forwarded to the Flag VM.
 * @requires vm.debug == true & vm.compiler2.enabled & vm.flagless
 * @library /test/lib /
 * @run main/othervm -Djdk.test.lib.random.seed=-1 -Dtest.java.opts=-Djdk.test.lib.random.seed=42 ir_framework.tests.TestRandomSeed jtregOpts flagVM
 * @run main/othervm -Djdk.test.lib.random.seed=-1 -Dtest.vm.opts=-Djdk.test.lib.random.seed=42 ir_framework.tests.TestRandomSeed jtregOpts flagVM
 */

public class TestRandomSeed {
    private static final String SEED_OPTION = "-D" + Utils.SEED_PROPERTY_NAME + "=";

    public static void main(String[] args) {
        if (args[0].equals("jtregOpts")) {
            Asserts.assertEQ(-1L, Utils.SEED);
            Asserts.assertTrue(Arrays.asList(Utils.getTestJavaOpts()).contains(SEED_OPTION + "42"),
                               "jtreg seed must be visible before starting child VMs");
            Class<?> testClass = args[1].equals("flagVM") ? RandomSeedWithIR.class : RandomSeedWithoutIR.class;
            new TestFramework(testClass).setDefaultWarmup(0)
                    .addFlags("-Dexpected.seed=42").start();
            return;
        }
        if (args[0].equals("overrides")) {
            // Simulate a seed supplied by jtreg, independently of the driver seed.
            System.setProperty("test.java.opts", SEED_OPTION + "42");
            long expected = Boolean.getBoolean("PreferCommandLineFlags") ? 42 : Long.MAX_VALUE;
            new TestFramework(RandomSeedWithoutIR.class).setDefaultWarmup(0)
                    .addFlags(SEED_OPTION + "0", "-Dexpected.seed=" + expected)
                    .addScenarios(new Scenario(0, SEED_OPTION + Long.MAX_VALUE)).start();
            return;
        }

        // @run options are not forwarded as jtreg VM options. Using a negative seed
        // also exposes missing propagation on builds with a version-derived seed.
        Asserts.assertEQ(-1L, Utils.SEED);
        Class<?> testClass = args[0].equals("flagVM") ? RandomSeedWithIR.class : RandomSeedWithoutIR.class;
        new TestFramework(testClass).setDefaultWarmup(0)
                .addFlags("-Dexpected.seed=" + Utils.SEED).start();
        new TestFramework(testClass).setDefaultWarmup(0)
                .addFlags(SEED_OPTION + "0", "-Dexpected.seed=0").start();
    }

    static void checkSeed() {
        long expected = Long.parseLong(System.getProperty("expected.seed"));
        System.out.println("Expected seed=" + expected + ", actual seed=" + Utils.SEED);
        Asserts.assertEQ(expected, Utils.SEED, "Random seed was not propagated");
    }
}

class RandomSeedWithoutIR {
    static {
        TestRandomSeed.checkSeed();
    }

    @Test
    public void test() {}
}

class RandomSeedWithIR {
    static {
        TestRandomSeed.checkSeed();
    }

    @Test
    @IR(failOn = IRNode.LOOP)
    public void test() {}
}
