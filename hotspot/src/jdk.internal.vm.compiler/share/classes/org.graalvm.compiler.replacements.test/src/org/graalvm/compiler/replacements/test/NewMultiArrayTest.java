/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.junit.Test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Tests the lowering of the MULTIANEWARRAY instruction.
 */
public class NewMultiArrayTest extends GraalCompilerTest {

    private static int rank(ResolvedJavaType type) {
        String name = type.getName();
        int dims = 0;
        while (dims < name.length() && name.charAt(dims) == '[') {
            dims++;
        }
        return dims;
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph g, boolean ignore, boolean installAsDefault, OptionValues options) {
        StructuredGraph graph = g == null ? parseForCompile(method, options) : g;
        boolean forceCompile = false;
        if (bottomType != null) {
            List<NewMultiArrayNode> snapshot = graph.getNodes().filter(NewMultiArrayNode.class).snapshot();
            assert snapshot != null;
            assert snapshot.size() == 1;

            NewMultiArrayNode node = snapshot.get(0);
            assert rank(arrayType) == dimensions.length;
            int rank = dimensions.length;
            ValueNode[] dimensionNodes = new ValueNode[rank];
            for (int i = 0; i < rank; i++) {
                dimensionNodes[i] = ConstantNode.forInt(dimensions[i], graph);
            }

            NewMultiArrayNode repl = graph.add(new NewMultiArrayNode(arrayType, dimensionNodes));
            graph.replaceFixedWithFixed(node, repl);
            forceCompile = true;
        }
        return super.getCode(method, graph, forceCompile, installAsDefault, options);
    }

    @Override
    protected Object referenceInvoke(ResolvedJavaMethod method, Object receiver, Object... args)
                    throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
        if (bottomType != null) {
            try {
                return Array.newInstance(bottomClass, dimensions);
            } catch (Exception e) {
                throw new InvocationTargetException(e);
            }
        }
        return super.referenceInvoke(method, receiver, args);
    }

    ResolvedJavaType arrayType;
    ResolvedJavaType bottomType;
    Class<?> bottomClass;
    int[] dimensions;

    @Test
    public void test1() {
        for (Class<?> clazz : new Class<?>[]{byte.class, char.class, short.class, int.class, float.class, long.class, double.class, String.class}) {
            bottomClass = clazz;
            bottomType = getMetaAccess().lookupJavaType(clazz);
            arrayType = bottomType;
            for (int rank : new int[]{1, 2, 10, 50, 100, 200, 254, 255}) {
                while (rank(arrayType) != rank) {
                    arrayType = arrayType.getArrayClass();
                }

                dimensions = new int[rank];
                for (int i = 0; i < rank; i++) {
                    dimensions[i] = 1;
                }

                test("newMultiArray");
            }
        }
        bottomType = null;
        arrayType = null;
    }

    public static Object newMultiArray() {
        // This is merely a template - the NewMultiArrayNode is replaced in getCode() above.
        // This also means we need a separate test for correct handling of negative dimensions
        // as deoptimization won't do what we want for a graph modified to be different from the
        // source bytecode.
        return new Object[10][9][8];
    }

    @Test
    public void test2() {
        test("newMultiArrayException");
    }

    public static Object newMultiArrayException() {
        return new Object[10][9][-8];
    }
}
