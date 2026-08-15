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
 * @summary Test that safepoint debug-info scalarization does not create too many nodes in a single IGVN iteration.
 * @bug 8373598
 * @enablePreview
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+AlwaysIncrementalInline -XX:-UseFieldFlattening
 *                   -XX:-AbortVMOnCompilationFailure
 *                   -Xbatch -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla.inlinetypes;

public class TestSafepointScalarizationNodeGrowth {
    static int counter;
    static RootValue result;

    static value class NestedValue {
        Integer boxed1;
        Integer boxed2;
        Integer boxed3;
        Integer boxed4;
        Integer boxed5;
        Integer boxed6;
        Integer boxed7;

        NestedValue(int i) {
            this.boxed1 = i;
            this.boxed2 = i;
            this.boxed3 = i;
            this.boxed4 = i;
            this.boxed5 = i;
            this.boxed6 = i;
            this.boxed7 = i;
        }

        NestedValue(NestedValue other) {
            this.boxed1 = ((counter++ & 1) == 0) ? other.boxed1 + counter++ : null;
            this.boxed2 = ((counter++ & 1) == 0) ? other.boxed2 + counter++ : null;
            this.boxed3 = ((counter++ & 1) == 0) ? other.boxed3 + counter++ : null;
            this.boxed4 = ((counter++ & 1) == 0) ? other.boxed4 + counter++ : null;
            this.boxed5 = ((counter++ & 1) == 0) ? other.boxed5 + counter++ : null;
            this.boxed6 = ((counter++ & 1) == 0) ? other.boxed6 + counter++ : null;
            this.boxed7 = ((counter++ & 1) == 0) ? other.boxed7 + counter++ : null;
        }
    }

    static value class RootValue {
        NestedValue nested1;
        NestedValue nested2;
        NestedValue nested3;
        NestedValue nested4;
        NestedValue nested5;

        RootValue(int i) {
            this.nested1 = new NestedValue(i);
            this.nested2 = new NestedValue(i);
            this.nested3 = new NestedValue(i);
            this.nested4 = new NestedValue(i);
            this.nested5 = new NestedValue(i);
        }

        RootValue(RootValue other) {
            this.nested1 = ((counter++ & 1) == 0) ? new NestedValue(other.nested1) : null;
            this.nested2 = ((counter++ & 1) == 0) ? new NestedValue(other.nested2) : null;
            this.nested3 = ((counter++ & 1) == 0) ? new NestedValue(other.nested3) : null;
            this.nested4 = ((counter++ & 1) == 0) ? new NestedValue(other.nested4) : null;
            this.nested5 = ((counter++ & 1) == 0) ? new NestedValue(other.nested5) : null;
        }
    }

    static void test(int val) {
        RootValue defaultValue = new RootValue(val);
        result = new RootValue(defaultValue);
        for (int i = 0; i < 20; ++i) {
            result = new RootValue(defaultValue);
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; ++i) {
            test(i);
        }
    }
}

