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

package compiler.lib.ir_framework;

/*
 * @test
 * @requires vm.flagless
 * @summary Sanity test the different ways to start the test framework.
 * @library /test/lib /
 * @run driver compiler.lib.ir_framework.TestSanity
 */

public class TestSanity {

    public static void main(String[] args) {
        TestFramework.run();
        TestFramework.run(TestSanity.class);
        TestFramework.runWithFlags("-XX:+TieredCompilation");
        new TestFramework().addFlags("-XX:SuspendRetryCount=51", "-XX:+UseTLAB").start();
        new TestFramework(TestSanity.class).addFlags("-XX:SuspendRetryCount=51", "-XX:+UseTLAB").start();
        new TestFramework().addHelperClasses(HelperA.class).start();
        new TestFramework(TestSanity.class).addHelperClasses(HelperA.class, HelperB.class).start();
        Scenario sDefault = new Scenario(0);
        Scenario s1 = new Scenario(1, "-XX:SuspendRetryCount=52", "-XX:+UseTLAB");
        Scenario s2 = new Scenario(2, "-XX:SuspendRetryCount=53", "-XX:+UseTLAB");
        new TestFramework(TestSanity.class).addScenarios(s1).start();
        new TestFramework().addScenarios(s1, s2).start();
        new TestFramework(TestSanity.class).addScenarios(s1, s2).start();
        new TestFramework().addScenarios(sDefault, s1).start();
        new TestFramework().addScenarios(sDefault, s1, s2).start();
        new TestFramework(TestSanity.class).addScenarios(sDefault, s1).start();
        new TestFramework(TestSanity.class).addScenarios(sDefault, s1, s2).start();
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
