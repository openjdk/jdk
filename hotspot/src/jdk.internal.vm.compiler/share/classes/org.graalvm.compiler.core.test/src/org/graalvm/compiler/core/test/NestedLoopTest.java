/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;

public class NestedLoopTest extends GraalCompilerTest {

    @Test
    public void test1() {
        test("test1Snippet", 1, 2, 2);
    }

    @Test
    public void test2() {
        test("test2Snippet", 1, 2, 2);
    }

    @Test
    public void test3() {
        test("test3Snippet", 1, 2, 2);
    }

    @Test
    public void test4() {
        test("test4Snippet", 1, 3, 2);
    }

    @SuppressWarnings("all")
    public static void test1Snippet(int a) {
        while (a()) { // a() exits root, while() exits root
            m1: while (b()) { // b() exits nested & root, while() exits nested
                while (c()) { // c() exits innermost & nested & root, while() exits innermost
                    if (d()) { // d() exits innermost & nested & root
                        break m1; // break exits innermost & nested
                    }
                }
            }
        }
    }// total : root = 5 exits, nested = 5, innermost = 4

    @SuppressWarnings("all")
    public static void test2Snippet(int a) {
        while (a()) { // a() exits root, while() exits root
            try {
                m1: while (b()) { // b() exits nested, while() exits nested
                    while (c()) { // c() exits innermost & nested, while() exits innermost
                        if (d()) { // d() exits innermost & nested
                            break m1; // break exits innermost & nested
                        }
                    }
                }
            } catch (Throwable t) {
            }
        }
    }// total : root = 2 exits, nested = 5, innermost = 4

    @SuppressWarnings("all")
    public static void test3Snippet(int a) {
        while (a == 0) { // while() exits root
            try {
                m1: while (b()) { // b() exits nested, while() exits nested
                    while (c()) { // c() exits innermost & nested, while() exits innermost
                        if (d()) { // d() exits innermost & nested
                            a(); // a() exits nothing (already outside innermost & nested)
                            break m1; // break exits innermost & nested
                        }
                    }
                }
            } catch (Throwable t) {
            }
        }
    }// total : root = 1 exit, nested = 5, innermost = 4

    public static void test4Snippet(int a) {
        while (a != 0) { // while() exits root
            try {
                m1: while (a != 0) { // while() exits nested
                    b(); // b() exits nested
                    while (c()) { // c() exits innermost & nested, while() exits innermost
                        if (d()) { // d() exits innermost & nested
                            break m1; // break exits innermost & nested
                        }
                    }
                    if (a != 2) {
                        a(); // a() exits nothing (already outside innermost & nested)
                        throw new Exception(); // throw exits nested
                    }
                }
            } catch (Throwable t) {
            }
        }
    } // total : root = 1 exit, nested = 6, innermost = 4

    private static native boolean a();

    private static native boolean b();

    private static native boolean c();

    private static native boolean d();

    private static Invoke getInvoke(String name, StructuredGraph graph) {
        for (MethodCallTargetNode callTarget : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (callTarget.targetMethod().getName().equals(name)) {
                return callTarget.invoke();
            }
        }
        return null;
    }

    private void test(String snippet, int rootExits, int nestedExits, int innerExits) {
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.YES);
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, true);

        Assert.assertEquals(3, cfg.getLoops().size());
        Loop<Block> rootLoop = cfg.getLoops().get(0);
        Loop<Block> nestedLoop = cfg.getLoops().get(1);
        Loop<Block> innerMostLoop = cfg.getLoops().get(2);
        Invoke a = getInvoke("a", graph);
        Invoke b = getInvoke("b", graph);
        Invoke c = getInvoke("c", graph);
        Invoke d = getInvoke("d", graph);
        Assert.assertTrue(containsDirect(rootLoop, a, cfg));
        Assert.assertTrue(containsDirect(nestedLoop, b, cfg));
        Assert.assertTrue(containsDirect(innerMostLoop, c, cfg));
        Assert.assertTrue(containsDirect(innerMostLoop, d, cfg));
        Assert.assertTrue(contains(rootLoop, d, cfg));
        Assert.assertTrue(contains(nestedLoop, d, cfg));
        Assert.assertEquals(rootExits, rootLoop.getExits().size());
        Assert.assertEquals(nestedExits, nestedLoop.getExits().size());
        Assert.assertEquals(innerExits, innerMostLoop.getExits().size());
        Debug.dump(Debug.BASIC_LOG_LEVEL, graph, "Graph");
    }

    private static boolean contains(Loop<Block> loop, Invoke node, ControlFlowGraph cfg) {
        Block block = cfg.blockFor((Node) node);
        Assert.assertNotNull(block);
        return loop.getBlocks().contains(block);
    }

    private static boolean containsDirect(Loop<Block> loop, Invoke node, ControlFlowGraph cfg) {
        for (Loop<Block> child : loop.getChildren()) {
            if (contains(child, node, cfg)) {
                return false;
            }
        }
        return contains(loop, node, cfg);
    }
}
