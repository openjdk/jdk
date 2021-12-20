/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)

public class IterativeEA {

    public static int ii = 1;

    static class A {
        int i;

        public A(int i) {
            this.i = i;
        }
    }

    static class B {
        A a;

        public B(A a) {
            this.a = a;
        }
    }

    static class C {
        B b;

        public C(B b) {
            this.b = b;
        }
    }

    @Benchmark
    public int test1() {
        C c = new C(new B(new A(ii)));
        return c.b.a.i;
    }

    static class Point {
        int x;
        int y;
        int ax[];
        int ay[];
    }

    @Benchmark
    public int test2() {
        Point p = new Point();
        p.ax = new int[2];
        p.ay = new int[2];
        int x = 3;
        p.ax[0] = x;
        p.ay[1] = 3 * x + ii;
        return p.ax[0] * p.ay[1];
    }

    public static final Double dbc = Double.valueOf(1.);

    @Benchmark
    public double test3() {
        Double j1 = Double.valueOf(1.);
        Double j2 = Double.valueOf(1.);
        for (int i = 0; i< 1000; i++) {
            j1 = j1 + 1.;
            j2 = j2 + 2.;
        }
        return j1 + j2;
    }
}
