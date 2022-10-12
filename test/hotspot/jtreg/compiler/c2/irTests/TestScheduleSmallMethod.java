/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, Arm Limited. All rights reserved.
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

package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8275847
 * @requires vm.compiler2.enabled
 * @summary Test that small method with runtime calls can be scheduled.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestScheduleSmallMethod
 */
public class TestScheduleSmallMethod {

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        Scenario schedulerOn = new Scenario(0, "-XX:+OptoScheduling");
        Scenario schedulerOff = new Scenario(1, "-XX:-OptoScheduling");
        framework.addScenarios(schedulerOn, schedulerOff).start();
    }

    @Test
    public double testSmallMethodTwoRuntimeCalls(double value) {
        // The two intrinsified Math calls below caused the scheduler to
        // bail out with "too many D-U pinch points". See bug 8275847.
        return Math.log(Math.sin(value));
    }

    @Run(test = "testSmallMethodTwoRuntimeCalls")
    public void checkTestSmallMethodTwoRuntimeCalls() throws Throwable {
        Asserts.assertLessThan(testSmallMethodTwoRuntimeCalls(Math.PI/2), 0.00001);
    }
}
