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
 * @bug 8391777
 * @summary Test detection of start node when browsing dominator nodes to narrow IV type
 * @requires vm.compiler2.enabled
 * @run main/othervm -Xcomp -XX:-TieredCompilation -XX:+UnlockDiagnosticVMOptions
 *                   -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.loopopts;

public class TestStartDominator {
    static float getShort() {
        return 42;
    }

    static short test() {
        short value = (short) -getShort();

        while (-1 > ++value) { }
        return value;
    }

    public static void main(String[] args) {
        test();
    }
}

