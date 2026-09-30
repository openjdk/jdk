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

/*
 * @test
 * @bug 8391658
 * @summary Test cleanup of nested MergeMems after incremental inlining.
 * @key stress randomness
 * @requires vm.compiler2.enabled
 * @enablePreview
 * @run main ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+StressIGVN -XX:+StressIncrementalInlining -XX:StressSeed=3860063970
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla;

public class TestMergeMemCleanup {
    static int count;

    static Integer m1(Object obj) {
        count++;
        return (obj == null) ? null : (Integer)obj;
    }

    static Integer m2(Integer val) {
        return m1(val);
    }

    static void test(Integer val) {
        m1(val);
        m2(val);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; i++) {
            test(null);
            test(i);
        }
    }
}

