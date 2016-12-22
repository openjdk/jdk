/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaConstant;
import org.junit.Test;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import static org.hamcrest.CoreMatchers.instanceOf;
import org.junit.Assert;

/**
 * Tests constant folding of string equality.
 */
public class StringEqualsConstantTest extends GraalCompilerTest {

    private ValueNode asConstant(StructuredGraph graph, String str) {
        return ConstantNode.forConstant(getSnippetReflection().forObject(str), getMetaAccess(), graph);
    }

    private void testStringEquals(String s0, String s1) {
        ResolvedJavaMethod method = getResolvedJavaMethod("stringEquals");
        StructuredGraph graph = parseForCompile(method);

        graph.getParameter(0).replaceAndDelete(asConstant(graph, s0));
        graph.getParameter(1).replaceAndDelete(asConstant(graph, s1));

        compile(method, graph);

        FixedNode firstFixed = graph.start().next();
        Assert.assertThat(firstFixed, instanceOf(ReturnNode.class));

        ReturnNode ret = (ReturnNode) firstFixed;
        JavaConstant result = ret.result().asJavaConstant();
        if (result == null) {
            Assert.fail("result not constant: " + ret.result());
        } else {
            int expected = s0.equals(s1) ? 1 : 0;
            Assert.assertEquals("result", expected, result.asInt());
        }
    }

    @Test
    public void testSameString() {
        String s = "equal-string";
        testStringEquals(s, s);
    }

    @Test
    public void testEqualString() {
        String s = "equal-string";
        testStringEquals(s, new String(s.toCharArray()));
    }

    @Test
    public void testDifferentString() {
        testStringEquals("some-string", "different-string");
    }

    @Test
    public void testSameLengthString() {
        testStringEquals("string-1", "string-2");
    }

    public static boolean stringEquals(String a, String b) {
        return a.equals(b);
    }
}
