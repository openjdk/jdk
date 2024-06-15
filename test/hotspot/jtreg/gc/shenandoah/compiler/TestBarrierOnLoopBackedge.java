/*
 * Copyright (c) 2024, Red Hat, Inc. All rights reserved.
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
 * @bug 8328107
 * @summary Barrier expanded on backedge break loop verification code
 * @requires vm.gc.Shenandoah
 *
 * @run main/othervm -XX:+UseShenandoahGC -XX:-BackgroundCompilation -XX:CompileCommand=dontinline,TestBarrierOnLoopBackedge::notInlined
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyLoopOptimizations TestBarrierOnLoopBackedge
 * @run main/othervm -XX:+UseShenandoahGC -XX:-BackgroundCompilation -XX:CompileCommand=dontinline,TestBarrierOnLoopBackedge::notInlined
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+VerifyLoopOptimizations -XX:-UseCountedLoopSafepoints TestBarrierOnLoopBackedge
 */

public class TestBarrierOnLoopBackedge {
    private static A field = new A();
    private static final A finalField = new A();
    private static float floatField;

    public static void main(String[] args) {
        A[] array = new A[1];
        array[0] = finalField;
        for (int i = 0; i < 20_000; i++) {
            test1();
            test2();
        }
    }

    private static void test1() {
        floatField = field.f;
        for (int i = 0; i < 1000; i++) {
            notInlined(field); // load barrier split thru phi and ends up on back edge
            if (i % 2 == 0) {
                field = finalField;
            }
        }
    }

    private static void test2() {
        A[] array = new A[1];
        notInlined(array);
        int i = 0;
        A a = array[0];
        for (;;) {
            synchronized (new Object()) {
            }
            notInlined(a);
            i++;
            if (i >= 1000) {
                break;
            }
            a = array[0]; // load barrier pinned on backedge
        }
    }

    private static void notInlined(Object a) {

    }

    private static class A {
        float f;
    }
}
