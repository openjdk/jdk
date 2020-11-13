/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.junit.Assert;
import org.junit.Test;

// See https://bugs.openjdk.java.net/browse/JDK-8248598
public class VolatileAccessReadEliminationTest extends GraalCompilerTest {
    private static int field;
    private Thread thread;
    private static long[] array = new long[1];

    static {
        arrayBaseOffset = UNSAFE.arrayBaseOffset(long[].class);
    }

    private static int arrayBaseOffset;

    public static int testMethod1() {
        int v = field;
        UNSAFE.putLongVolatile(array, arrayBaseOffset, 1);
        while (UNSAFE.getLongVolatile(array, arrayBaseOffset) != 2) {
            // wait for other thread
        }
        return v + field; // field load shouldn't common with one above.
    }

    @Test
    public void test1() {
        test("testMethod1");
    }

    @Override
    protected void before(ResolvedJavaMethod method) {
        field = 0;
        UNSAFE.putLongVolatile(array, arrayBaseOffset, 0);
        thread = new Thread() {
            @Override
            public void run() {
                while (UNSAFE.getLongVolatile(array, arrayBaseOffset) != 1) {
                    // wait for test thread to start
                }
                field = 0x42;
                UNSAFE.putLongVolatile(array, arrayBaseOffset, 2);
            }
        };
        thread.start();
    }

    @Override
    protected void after() {
        try {
            thread.join();
        } catch (InterruptedException e) {
        }
    }

    public static int testMethod2(int offset) {
        int v = field;
        long v2 = UNSAFE.getLongVolatile(array, offset);
        return v + field + (int) v2; // field load shouldn't common with one above.
    }

    @Test
    public void test2() {
        final StructuredGraph graph = getFinalGraph("testMethod2");
        Assert.assertEquals(2, getFieldReads(graph));
    }

    public static int testMethod3(int offset) {
        int v = field;
        UNSAFE.putLongVolatile(array, offset, 0x42);
        return v + field; // field load shouldn't common with one above.
    }

    @Test
    public void test3() {
        final StructuredGraph graph = getFinalGraph("testMethod3");
        Assert.assertEquals(2, getFieldReads(graph));
    }

    private static int getFieldReads(StructuredGraph graph) {
        return graph.getNodes().filter(n -> n instanceof ReadNode).filter(n -> {
            final Stamp stamp = ((ReadNode) n).stamp(NodeView.DEFAULT);
            return stamp instanceof IntegerStamp && ((IntegerStamp) stamp).getBits() == 32;
        }).count();
    }
}
