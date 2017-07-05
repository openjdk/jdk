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
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;

/**
 * Tests for {@link GraalDirectives#blackhole}.
 *
 * There are two snippets for each kind:
 * <ul>
 * <li>blackhole&lt;Kind&gt;Snippet verifies that dead code elimination is prevented by the
 * blackhole directive.
 * <li>&lt;kind&gt;Snippet verifies that dead code elimination does happen if the blackhole
 * directive is not there.
 * </ul>
 *
 */
public class BlackholeDirectiveTest extends GraalCompilerTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    private @interface BlackholeSnippet {
        boolean expectParameterUsage();
    }

    @BlackholeSnippet(expectParameterUsage = false)
    public static int booleanSnippet(int arg) {
        boolean b = arg > 3;
        if (b) {
            return 1;
        } else {
            return 1;
        }
    }

    @BlackholeSnippet(expectParameterUsage = true)
    public static int blackholeBooleanSnippet(int arg) {
        boolean b = arg > 3;
        GraalDirectives.blackhole(b);
        if (b) {
            return 1;
        } else {
            return 1;
        }
    }

    @Test
    public void testBoolean() {
        test("booleanSnippet", 5);
        test("blackholeBooleanSnippet", 5);
    }

    @BlackholeSnippet(expectParameterUsage = false)
    public static int intSnippet(int arg) {
        int x = 42 + arg;
        return x - arg;
    }

    @BlackholeSnippet(expectParameterUsage = true)
    public static int blackholeIntSnippet(int arg) {
        int x = 42 + arg;
        GraalDirectives.blackhole(x);
        return x - arg;
    }

    @Test
    public void testInt() {
        test("intSnippet", 17);
        test("blackholeIntSnippet", 17);
    }

    private static class Dummy {
        private int x = 42;
    }

    @BlackholeSnippet(expectParameterUsage = false)
    public static int objectSnippet(int arg) {
        Dummy obj = new Dummy();
        int ret = obj.x;
        obj.x = arg;
        return ret;
    }

    @BlackholeSnippet(expectParameterUsage = true)
    public static int blackholeObjectSnippet(int arg) {
        Dummy obj = new Dummy();
        int ret = obj.x;
        obj.x = arg;
        GraalDirectives.blackhole(obj);
        return ret;
    }

    @Test
    public void testObject() {
        test("objectSnippet", 37);
        test("blackholeObjectSnippet", 37);
    }

    @Override
    protected boolean checkLowTierGraph(StructuredGraph graph) {
        BlackholeSnippet snippet = graph.method().getAnnotation(BlackholeSnippet.class);
        ParameterNode arg = graph.getParameter(0);
        if (snippet.expectParameterUsage()) {
            Assert.assertNotNull("couldn't find ParameterNode(0)", arg);
            Assert.assertFalse("expected usages of " + arg, arg.hasNoUsages());
        } else {
            Assert.assertTrue("expected no usages of ParameterNode", arg == null || arg.hasNoUsages());
        }
        return true;
    }
}
