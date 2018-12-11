/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.core.test;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assume;
import org.junit.Test;

public class StableArrayReadFoldingTest extends GraalCompilerTest {

    static final boolean[] STABLE_BOOLEAN_ARRAY = new boolean[16];
    static final int[] STABLE_INT_ARRAY = new int[16];

    static final long BOOLEAN_ARRAY_BASE_OFFSET;
    static final long INT_ARRAY_BASE_OFFSET;

    static {
        BOOLEAN_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(boolean[].class);
        INT_ARRAY_BASE_OFFSET = UNSAFE.arrayBaseOffset(int[].class);
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        StructuredGraph graph = super.parseForCompile(method, compilationId, options);
        // Mimic @Stable array constants.
        for (ConstantNode constantNode : graph.getNodes().filter(ConstantNode.class).snapshot()) {
            if (getConstantReflection().readArrayLength(constantNode.asJavaConstant()) != null) {
                ConstantNode newConstantNode = graph.unique(ConstantNode.forConstant(constantNode.asJavaConstant(), 1, true, getMetaAccess()));
                constantNode.replaceAndDelete(newConstantNode);
            }
        }
        return graph;
    }

    public static boolean killWithSameType() {
        boolean beforeKill = UNSAFE.getBoolean(STABLE_BOOLEAN_ARRAY, BOOLEAN_ARRAY_BASE_OFFSET);
        STABLE_BOOLEAN_ARRAY[0] = true;
        boolean afterKill = UNSAFE.getBoolean(STABLE_BOOLEAN_ARRAY, BOOLEAN_ARRAY_BASE_OFFSET);

        STABLE_BOOLEAN_ARRAY[0] = false;
        return beforeKill == afterKill;
    }

    @Test
    public void testKillWithSameType() {
        ResolvedJavaMethod method = getResolvedJavaMethod("killWithSameType");
        testAgainstExpected(method, new Result(true, null), null);
    }

    public static boolean killWithDifferentType() {
        byte beforeKill = UNSAFE.getByte(STABLE_BOOLEAN_ARRAY, BOOLEAN_ARRAY_BASE_OFFSET);
        STABLE_BOOLEAN_ARRAY[0] = true;
        byte afterKill = UNSAFE.getByte(STABLE_BOOLEAN_ARRAY, BOOLEAN_ARRAY_BASE_OFFSET);

        STABLE_BOOLEAN_ARRAY[0] = false;
        return beforeKill == afterKill;
    }

    @Test
    public void testKillWithDifferentType() {
        ResolvedJavaMethod method = getResolvedJavaMethod("killWithDifferentType");
        testAgainstExpected(method, new Result(true, null), null);
    }

    public static boolean killWithSameTypeUnaligned() {
        int beforeKill = UNSAFE.getInt(STABLE_INT_ARRAY, INT_ARRAY_BASE_OFFSET + 1);
        STABLE_INT_ARRAY[0] = 0x01020304;
        int afterKill = UNSAFE.getInt(STABLE_INT_ARRAY, INT_ARRAY_BASE_OFFSET + 1);

        STABLE_INT_ARRAY[0] = 0;
        return beforeKill == afterKill;
    }

    @Test
    public void testKillWithSameTypeUnaligned() {
        Assume.assumeTrue("Only test unaligned access on AMD64", getTarget().arch instanceof AMD64);
        ResolvedJavaMethod method = getResolvedJavaMethod("killWithSameTypeUnaligned");
        testAgainstExpected(method, new Result(true, null), null);
    }

    public static boolean killWithDifferentTypeUnaligned() {
        byte beforeKill = UNSAFE.getByte(STABLE_INT_ARRAY, INT_ARRAY_BASE_OFFSET + 1);
        STABLE_INT_ARRAY[0] = 0x01020304;
        byte afterKill = UNSAFE.getByte(STABLE_INT_ARRAY, INT_ARRAY_BASE_OFFSET + 1);

        STABLE_INT_ARRAY[0] = 0;
        return beforeKill == afterKill;
    }

    @Test
    public void testKillWithDifferentTypeUnaligned() {
        Assume.assumeTrue("Only test unaligned access on AMD64", getTarget().arch instanceof AMD64);
        ResolvedJavaMethod method = getResolvedJavaMethod("killWithDifferentTypeUnaligned");
        testAgainstExpected(method, new Result(true, null), null);
    }
}
