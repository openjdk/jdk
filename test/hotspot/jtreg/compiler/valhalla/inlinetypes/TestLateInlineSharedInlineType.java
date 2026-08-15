/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8388544
 * @summary Test late inlining of a method handle that returns a shared value object.
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+AlwaysIncrementalInline -Xbatch
 *                   -XX:CompileCommand=dontinline,${test.main.class}::test*
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public value class TestLateInlineSharedInlineType {
    static final MethodHandle MH1;
    static final MethodHandle MH2;
    static volatile Object vSink;
    static Object sink1;
    static Object sink2;

    Integer integer = 42;

    public Integer getInteger() {
        return integer;
    }

    static {
        try {
            MH1 = MethodHandles.lookup().findVirtual(TestLateInlineSharedInlineType.class, "getInteger", MethodType.methodType(Integer.class));
            MH2 = MH1.asType(MethodType.methodType(Object.class, TestLateInlineSharedInlineType.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Below tests all trigger the same bug but with slightly different failure modes:
    // Field 'integer' is represented by a (shared) InlineTypeNode and buffered on the
    // first store to sink. Late MH inlining updates that node's oop input in-place to
    // a newly created, later buffer. InlineTypeNode::Ideal() then removes the first
    // buffer as redundant re-allocation and rewires an earlier use to the later buffer.

    // We assert with "Bad immediate dominator info." when walking the dominator chain
    // from early use towards the non-dominating later definition.
    void test1() throws Throwable {
        vSink = integer;
        vSink = (Integer)MH1.invokeExact(this);
    }

    // We assert with "bad dominance" in loop verification because the definition of the
    // second buffer does not dominate its use at the first vSink store.
    void test2() throws Throwable {
        vSink = integer;
        vSink = MH2.invokeExact(this);
    }

    // The invalid dependency remains hidden in a Phi and escapes normal PhaseCFG::verify().
    // The early sink1 store therefore reads the late buffer's register before it is
    // defined, leading to an "object not in heap" assert in the GC.
    void test3() throws Throwable {
        sink1 = integer;
        sink2 = MH2.invokeExact(this);
    }

    public static void main(String[] args) throws Throwable {
        for (int i = 0; i < 50_000; i++) {
            TestLateInlineSharedInlineType test = new TestLateInlineSharedInlineType();
            test.test1();
            test.test2();
            test.test3();
        }
    }
}
