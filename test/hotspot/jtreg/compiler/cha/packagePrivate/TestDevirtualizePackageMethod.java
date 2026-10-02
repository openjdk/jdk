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
package compiler.cha.packagePrivate;

import compiler.cha.packagePrivate.differentPackage.NonOverridingChild;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8390499
 * @summary Verify that C2 correctly devirtualizes package-private methods.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class TestDevirtualizePackageMethod {
    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("-XX:CompileCommand=dontinline,*::call");
        framework.start();
    }

    @Run(test = {"testOverriding1", "testOverriding2"})
    public void runOverriding() {
        var v1 = new OverridingChild.GrandChild1();
        var v2 = new OverridingChild.GrandChild2();
        var v3 = new OverridingChild.GrandChild3();
        Asserts.assertEQ(1, testOverriding1(v1));
        Asserts.assertEQ(1, testOverriding1(v2));
        Asserts.assertEQ(1, testOverriding1(v3));
        Asserts.assertEQ(1, testOverriding2(v1));
        Asserts.assertEQ(1, testOverriding2(v2));
        Asserts.assertEQ(1, testOverriding2(v3));
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "OverridenParent::call", IRNode.DYNAMIC_CALL_OF_METHOD, "OverridingChild::call"})
    @IR(counts = {IRNode.STATIC_CALL_OF_METHOD, "OverridingChild::call", "1"})
    private static int testOverriding1(OverridenParent v) {
        return v.call();
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "OverridenParent::call", IRNode.DYNAMIC_CALL_OF_METHOD, "OverridingChild::call"})
    @IR(counts = {IRNode.STATIC_CALL_OF_METHOD, "OverridingChild::call", "1"})
    private static int testOverriding2(OverridingChild v) {
        return v.call();
    }

    @Run(test = {"testNonOverriding1", "testNonOverriding2"})
    public void runNonOverriding() {
        var v1 = new NonOverridingChild.GrandChild1();
        var v2 = new NonOverridingChild.GrandChild2();
        var v3 = new NonOverridingChild.GrandChild3();
        Asserts.assertEQ(0, testNonOverriding1(v1));
        Asserts.assertEQ(0, testNonOverriding1(v2));
        Asserts.assertEQ(0, testNonOverriding1(v3));
        Asserts.assertEQ(1, testNonOverriding2(v1));
        Asserts.assertEQ(1, testNonOverriding2(v2));
        Asserts.assertEQ(1, testNonOverriding2(v3));
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "NonOverridenParent::call", IRNode.DYNAMIC_CALL_OF_METHOD, "NonOverridingChild::call"})
    @IR(counts = {IRNode.STATIC_CALL_OF_METHOD, "NonOverridenParent::call", "1"})
    private static int testNonOverriding1(NonOverridenParent v) {
        return v.call();
    }

    @Test
    @IR(failOn = {IRNode.DYNAMIC_CALL_OF_METHOD, "NonOverridenParent::call", IRNode.DYNAMIC_CALL_OF_METHOD, "NonOverridingChild::call"})
    @IR(counts = {IRNode.STATIC_CALL_OF_METHOD, "NonOverridingChild::call", "1"})
    private static int testNonOverriding2(NonOverridingChild v) {
        return v.call();
    }
}
