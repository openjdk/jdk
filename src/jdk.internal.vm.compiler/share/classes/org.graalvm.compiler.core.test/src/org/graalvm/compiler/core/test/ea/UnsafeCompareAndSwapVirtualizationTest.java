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


package org.graalvm.compiler.core.test.ea;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import org.graalvm.compiler.nodes.java.LogicCompareAndSwapNode;
import org.junit.Test;

import jdk.vm.ci.meta.JavaConstant;

public class UnsafeCompareAndSwapVirtualizationTest extends EATestBase {

    private static Object obj1 = new Object();
    private static Object obj2 = new Object();
    private static final Object OBJ1 = new Object();

    public static boolean bothVirtualNoMatch() {
        AtomicReference<Object> a = new AtomicReference<>();
        return a.compareAndSet(new Object(), new Object());
    }

    @Test
    public void bothVirtualNoMatchTest() {
        testEscapeAnalysis("bothVirtualNoMatch", JavaConstant.INT_0, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean bothVirtualMatch() {
        Object expect = new Object();
        AtomicReference<Object> a = new AtomicReference<>(expect);
        return a.compareAndSet(expect, new Object());
    }

    @Test
    public void bothVirtualMatchTest() {
        testEscapeAnalysis("bothVirtualMatch", JavaConstant.INT_1, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean expectedVirtualMatch() {
        Object o = new Object();
        AtomicReference<Object> a = new AtomicReference<>(o);
        return a.compareAndSet(o, obj1);
    }

    @Test
    public void expectedVirtualMatchTest() {
        testEscapeAnalysis("expectedVirtualMatch", JavaConstant.INT_1, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean expectedVirtualNoMatch() {
        Object o = new Object();
        AtomicReference<Object> a = new AtomicReference<>();
        return a.compareAndSet(o, obj1);
    }

    @Test
    public void expectedVirtualNoMatchTest() {
        testEscapeAnalysis("expectedVirtualNoMatch", JavaConstant.INT_0, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean bothNonVirtualNoMatch() {
        AtomicReference<Object> a = new AtomicReference<>();
        return a.compareAndSet(OBJ1, obj2);
    }

    @Test
    public void bothNonVirtualNoMatchTest() {
        testEscapeAnalysis("bothNonVirtualNoMatch", JavaConstant.INT_0, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean bothNonVirtualMatch() {
        AtomicReference<Object> a = new AtomicReference<>(OBJ1);
        return a.compareAndSet(OBJ1, obj2);
    }

    @Test
    public void bothNonVirtualMatchTest() {
        testEscapeAnalysis("bothNonVirtualMatch", JavaConstant.INT_1, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean onlyInitialValueVirtualNoMatch() {
        AtomicReference<Object> a = new AtomicReference<>(new Object());
        return a.compareAndSet(obj1, obj2);
    }

    @Test
    public void onlyInitialValueVirtualNoMatchTest() {
        testEscapeAnalysis("onlyInitialValueVirtualNoMatch", JavaConstant.INT_0, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean onlyInitialValueVirtualMatch() {
        Object o = new Object();
        AtomicReference<Object> a = new AtomicReference<>(o);
        return a.compareAndSet(o, obj2);
    }

    @Test
    public void onlyInitialValueVirtualMatchTest() {
        testEscapeAnalysis("onlyInitialValueVirtualMatch", JavaConstant.INT_1, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }

    public static boolean bothVirtualNoMatchArray() {
        AtomicReferenceArray<Object> array = new AtomicReferenceArray<>(1);
        return array.compareAndSet(0, new Object(), new Object());
    }

    @Test
    public void bothVirtualNoMatchArrayTest() {
        testEscapeAnalysis("bothVirtualNoMatchArray", JavaConstant.INT_0, true);
        assertTrue(graph.getNodes(LogicCompareAndSwapNode.TYPE).isEmpty());
    }
}
