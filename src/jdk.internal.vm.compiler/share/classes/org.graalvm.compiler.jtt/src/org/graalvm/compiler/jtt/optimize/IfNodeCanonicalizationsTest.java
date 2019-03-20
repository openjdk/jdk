/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.optimize;

import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.jtt.JTTTest;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InlineInvokePlugin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class IfNodeCanonicalizationsTest extends JTTTest {

    @Override
    protected InlineInvokePlugin.InlineInfo bytecodeParserShouldInlineInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
        if (method.getDeclaringClass().getUnqualifiedName().equals(IfNodeCanonicalizationsTest.class.getSimpleName()) && method.getName().startsWith("compare")) {
            return InlineInvokePlugin.InlineInfo.createStandardInlineInfo(method);
        }
        return super.bytecodeParserShouldInlineInvoke(b, method, args);
    }

    @Parameter(value = 0) public String testName;
    @Parameter(value = 1) public int x;
    @Parameter(value = 2) public int y;

    public static int compare0(int a, int b) {
        return (a < b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare1(int a, int b) {
        return (a < b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare2(int a, int b) {
        return (a < b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare3(int a, int b) {
        return (a < b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare4(int a, int b) {
        return (a < b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare5(int a, int b) {
        return (a < b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare6(int a, int b) {
        return (a < b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare7(int a, int b) {
        return (a < b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare8(int a, int b) {
        return (a < b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare9(int a, int b) {
        return (a < b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare10(int a, int b) {
        return (a < b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare11(int a, int b) {
        return (a < b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare12(int a, int b) {
        return (a > b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare13(int a, int b) {
        return (a > b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare14(int a, int b) {
        return (a > b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare15(int a, int b) {
        return (a > b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare16(int a, int b) {
        return (a > b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare17(int a, int b) {
        return (a > b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare18(int a, int b) {
        return (a > b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare19(int a, int b) {
        return (a > b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare20(int a, int b) {
        return (a > b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare21(int a, int b) {
        return (a > b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare22(int a, int b) {
        return (a > b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare23(int a, int b) {
        return (a > b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare24(int a, int b) {
        return (a <= b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare25(int a, int b) {
        return (a <= b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare26(int a, int b) {
        return (a <= b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare27(int a, int b) {
        return (a <= b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare28(int a, int b) {
        return (a <= b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare29(int a, int b) {
        return (a <= b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare30(int a, int b) {
        return (a <= b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare31(int a, int b) {
        return (a <= b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare32(int a, int b) {
        return (a <= b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare33(int a, int b) {
        return (a <= b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare34(int a, int b) {
        return (a <= b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare35(int a, int b) {
        return (a <= b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare36(int a, int b) {
        return (a >= b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare37(int a, int b) {
        return (a >= b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare38(int a, int b) {
        return (a >= b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare39(int a, int b) {
        return (a >= b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare40(int a, int b) {
        return (a >= b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare41(int a, int b) {
        return (a >= b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare42(int a, int b) {
        return (a >= b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare43(int a, int b) {
        return (a >= b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare44(int a, int b) {
        return (a >= b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare45(int a, int b) {
        return (a >= b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare46(int a, int b) {
        return (a >= b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare47(int a, int b) {
        return (a >= b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare48(int a, int b) {
        return (a == b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare49(int a, int b) {
        return (a == b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare50(int a, int b) {
        return (a == b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare51(int a, int b) {
        return (a == b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare52(int a, int b) {
        return (a == b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare53(int a, int b) {
        return (a == b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare54(int a, int b) {
        return (a == b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare55(int a, int b) {
        return (a == b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare56(int a, int b) {
        return (a == b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare57(int a, int b) {
        return (a == b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare58(int a, int b) {
        return (a == b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare59(int a, int b) {
        return (a == b) ? ((a != b) ? 1 : 2) : 3;
    }

    public static int compare60(int a, int b) {
        return (a != b) ? 1 : ((a < b) ? 2 : 3);
    }

    public static int compare61(int a, int b) {
        return (a != b) ? ((a < b) ? 1 : 2) : 3;
    }

    public static int compare62(int a, int b) {
        return (a != b) ? 1 : ((a > b) ? 2 : 3);
    }

    public static int compare63(int a, int b) {
        return (a != b) ? ((a > b) ? 1 : 2) : 3;
    }

    public static int compare64(int a, int b) {
        return (a != b) ? 1 : ((a <= b) ? 2 : 3);
    }

    public static int compare65(int a, int b) {
        return (a != b) ? ((a <= b) ? 1 : 2) : 3;
    }

    public static int compare66(int a, int b) {
        return (a != b) ? 1 : ((a >= b) ? 2 : 3);
    }

    public static int compare67(int a, int b) {
        return (a != b) ? ((a >= b) ? 1 : 2) : 3;
    }

    public static int compare68(int a, int b) {
        return (a != b) ? 1 : ((a == b) ? 2 : 3);
    }

    public static int compare69(int a, int b) {
        return (a != b) ? ((a == b) ? 1 : 2) : 3;
    }

    public static int compare70(int a, int b) {
        return (a != b) ? 1 : ((a != b) ? 2 : 3);
    }

    public static int compare71(int a, int b) {
        return (a != b) ? ((a != b) ? 1 : 2) : 3;
    }

    @Test
    public void runNamedTest() {
        runTest(testName, x, y);
    }

    @Parameters(name = "{0}(a = {1}, b = {2})")
    public static Collection<Object[]> data() {
        List<Object[]> tests = new ArrayList<>();
        for (Method m : IfNodeCanonicalizationsTest.class.getDeclaredMethods()) {
            if (m.getName().startsWith("compare") && Modifier.isStatic(m.getModifiers())) {
                tests.add(new Object[]{m.getName(), 0, 0});
                tests.add(new Object[]{m.getName(), 0, 1});
                tests.add(new Object[]{m.getName(), 1, 0});
            }
        }
        return tests;
    }
}
