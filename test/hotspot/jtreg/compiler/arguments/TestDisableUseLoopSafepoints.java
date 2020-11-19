/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6232281
 * @requires vm.debug == true & vm.compiler2.enabled
 * @summary Tests that C2 does not crash trivially with a "remove_useless_nodes
 *          missed this node" message when UseLoopSafepoints is disabled.
 * @run main/othervm -Xcomp -XX:-TieredCompilation
        -XX:CompileOnly=TestDisableUseLoopSafepoints -XX:-UseLoopSafepoints
 *      compiler.arguments.TestDisableUseLoopSafepoints
 */

package compiler.arguments;

public class TestDisableUseLoopSafepoints {
    public static void main(String[] args) {
        System.out.println("Passed");
    }
}
