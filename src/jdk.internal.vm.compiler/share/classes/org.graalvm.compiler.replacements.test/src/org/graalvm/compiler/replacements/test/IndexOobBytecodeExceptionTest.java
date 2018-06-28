/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode.BytecodeExceptionKind;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;

import jdk.vm.ci.meta.ResolvedJavaMethod;

@RunWith(Parameterized.class)
public class IndexOobBytecodeExceptionTest extends BytecodeExceptionTest {

    private static class Exceptions {

        private static Object[] empty = new Object[0];

        public static void throwOutOfBounds(int idx, int length) {
            GraalDirectives.blackhole(empty[idx]);
            GraalDirectives.blackhole(length);
        }
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        invocationPlugins.register(new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode idx, ValueNode length) {
                return throwBytecodeException(b, BytecodeExceptionKind.OUT_OF_BOUNDS, idx, length);
            }
        }, Exceptions.class, "throwOutOfBounds", int.class, int.class);
        super.registerInvocationPlugins(invocationPlugins);
    }

    public static void oobSnippet(int idx, int length) {
        Exceptions.throwOutOfBounds(idx, length);
    }

    @Parameter public int index;

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        int[] values = {Integer.MIN_VALUE, -42, -1, 0, 1, 42, Integer.MAX_VALUE};

        ArrayList<Object[]> ret = new ArrayList<>(values.length);
        for (int i : values) {
            ret.add(new Object[]{i});
        }
        return ret;
    }

    @Test
    public void testOutOfBoundsException() {
        test("oobSnippet", index, Exceptions.empty.length);
    }
}
