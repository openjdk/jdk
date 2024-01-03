/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316653
 * @requires vm.debug
 * @summary Test flag with max value.
 *
 * @run main/othervm -XX:NMethodSizeLimit=1M
 *                   compiler.arguments.TestC1Globals
 */

/**
 * @test
 * @bug 8318817
 * @requires vm.debug
 * @requires os.family == "linux"
 * @summary Test flag with max value combined with transparent huge pages on
 *          Linux.
 *
 * @run main/othervm -XX:NMethodSizeLimit=1M
 *                   -XX:+UseTransparentHugePages
 *                   compiler.arguments.TestC1Globals
 */

/**
 * @test
 * @bug 8320682
 * @requires vm.debug
 * @summary Test flag with max value and specific compilation.
 *
 * @run main/othervm -XX:NMethodSizeLimit=1M
 *                   -XX:CompileOnly=java.util.HashMap::putMapEntries
 *                   -Xcomp
 *                   compiler.arguments.TestC1Globals
 *
 */

package compiler.arguments;

public class TestC1Globals {

    public static void main(String args[]) {
        System.out.println("Passed");
    }
}
