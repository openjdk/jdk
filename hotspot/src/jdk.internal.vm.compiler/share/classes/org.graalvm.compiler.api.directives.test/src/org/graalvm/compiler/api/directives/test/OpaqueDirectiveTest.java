/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.api.directives.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ReturnNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.ConditionalNode;

/**
 * Tests for {@link GraalDirectives#opaque}.
 *
 * There are two snippets for each kind:
 * <ul>
 * <li>opaque&lt;Kind&gt;Snippet verifies that constant folding is prevented by the opaque
 * directive.
 * <li>&lt;kind&gt;Snippet verifies that constant folding does happen if the opaque directive is not
 * there.
 * </ul>
 *
 */
public class OpaqueDirectiveTest extends GraalCompilerTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface OpaqueSnippet {
        Class<?> expectedReturnNode();
    }

    @OpaqueSnippet(expectedReturnNode = ConstantNode.class)
    public static boolean booleanSnippet() {
        return 5 > 3;
    }

    @OpaqueSnippet(expectedReturnNode = ConditionalNode.class)
    public static boolean opaqueBooleanSnippet() {
        return 5 > GraalDirectives.opaque(3);
    }

    @Test
    public void testBoolean() {
        test("booleanSnippet");
        test("opaqueBooleanSnippet");
    }

    @OpaqueSnippet(expectedReturnNode = ConstantNode.class)
    public static int intSnippet() {
        return 5 + 3;
    }

    @OpaqueSnippet(expectedReturnNode = AddNode.class)
    public static int opaqueIntSnippet() {
        return 5 + GraalDirectives.opaque(3);
    }

    @Test
    public void testInt() {
        test("intSnippet");
        test("opaqueIntSnippet");
    }

    @OpaqueSnippet(expectedReturnNode = ConstantNode.class)
    public static double doubleSnippet() {
        return 5. + 3.;
    }

    @OpaqueSnippet(expectedReturnNode = AddNode.class)
    public static double opaqueDoubleSnippet() {
        return 5. + GraalDirectives.opaque(3.);
    }

    @Test
    public void testDouble() {
        test("doubleSnippet");
        test("opaqueDoubleSnippet");
    }

    private static class Dummy {
    }

    @OpaqueSnippet(expectedReturnNode = ConstantNode.class)
    public static boolean objectSnippet() {
        Object obj = new Dummy();
        return obj == null;
    }

    @OpaqueSnippet(expectedReturnNode = ConditionalNode.class)
    public static boolean opaqueObjectSnippet() {
        Object obj = new Dummy();
        return GraalDirectives.opaque(obj) == null;
    }

    @Test
    public void testObject() {
        test("objectSnippet");
        test("opaqueObjectSnippet");
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        OpaqueSnippet snippet = graph.method().getAnnotation(OpaqueSnippet.class);
        for (ReturnNode returnNode : graph.getNodes(ReturnNode.TYPE)) {
            Assert.assertEquals(snippet.expectedReturnNode(), returnNode.result().getClass());
        }
        return true;
    }
}
