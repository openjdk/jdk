/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.lib.hotspot.ir_framework.tests;

import jdk.test.lib.hotspot.ir_framework.Scenario;
import jdk.test.lib.hotspot.ir_framework.Test;
import jdk.test.lib.hotspot.ir_framework.TestFramework;

/*
 * @test
 * @summary Sanity test the different ways to start the test framework.
 * @library /test/lib
 * @run driver jdk.test.lib.hotspot.ir_framework.tests.TestSanity
 */

public class TestSanity {

    public static void main(String[] args) {
        TestFramework.run();
        TestFramework.run(TestSanity.class);
        TestFramework.runWithFlags("-XX:+TieredCompilation");
        TestFramework.runWithFlags(TestSanity.class, "-XX:SuspendRetryCount=51", "-XX:+UseTLAB");
        TestFramework.runWithHelperClasses(TestSanity.class, HelperA.class);
        TestFramework.runWithHelperClasses(TestSanity.class, HelperA.class, HelperB.class);
        Scenario sDefault = new Scenario(0);
        Scenario s1 = new Scenario(1, "-XX:SuspendRetryCount=52", "-XX:+UseTLAB");
        Scenario s2 = new Scenario(2, "-XX:SuspendRetryCount=53", "-XX:+UseTLAB");
        TestFramework.runWithScenarios(s1);
        TestFramework.runWithScenarios(s1, s2);
        TestFramework.runWithScenarios(TestSanity.class, s1, s2);
        TestFramework.runWithScenarios(sDefault, s1);
        TestFramework.runWithScenarios(sDefault, s1, s2);
        TestFramework.runWithScenarios(TestSanity.class, sDefault, s1);
        TestFramework.runWithScenarios(TestSanity.class, sDefault, s1, s2);
        TestFramework testFramework = new TestFramework();
        testFramework.start();
        testFramework.addFlags("-XX:SuspendRetryCount=54").start();
        testFramework = new TestFramework();
        testFramework.addFlags("-XX:SuspendRetryCount=55").addFlags("-XX:+UseTLAB").start();
        testFramework = new TestFramework();
        testFramework.addHelperClasses(HelperA.class, HelperB.class).start();
        testFramework = new TestFramework();
        testFramework.addHelperClasses(HelperA.class, HelperB.class).addHelperClasses(HelperC.class).start();
        testFramework = new TestFramework();
        testFramework.addScenarios(sDefault).addScenarios(s1, s2).start();
        testFramework = new TestFramework();
        testFramework.addHelperClasses(HelperA.class).addScenarios(sDefault).addFlags("-XX:+UseSuperWord").start();
        testFramework = new TestFramework();
        testFramework.addHelperClasses(HelperA.class).addFlags("-XX:+UseSuperWord", "-XX:+UseCompiler").addScenarios(sDefault)
                     .addHelperClasses(HelperB.class, HelperC.class).addScenarios(s1, s2).addFlags("-XX:+TieredCompilation").start();
        testFramework = new TestFramework();
        testFramework.addHelperClasses(HelperA.class).addFlags("-XX:+UseSuperWord", "-XX:+UseCompiler").addScenarios(sDefault)
                     .addHelperClasses(HelperB.class, HelperC.class).addScenarios(s1, s2).setDefaultWarmup(200)
                     .addFlags("-XX:+TieredCompilation").start();
    }

    @Test
    public void test() {}
}

class HelperA { }
class HelperB { }
class HelperC { }
