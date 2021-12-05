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

package compiler.vectorapi.reshape;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8259610
 * @modules jdk.incubator.vector
 * @summary Test that vector reshape intrinsics work as intended.
 * @library /test/lib /
 * @run driver compiler.vectorapi.reshape.TestVectorReshape
 */
public class TestVectorReshape {
    public static void main(String[] args) {
        var cast = new TestFramework(TestVectorCast.class);
        cast.setDefaultWarmup(1);
        cast.addHelperClasses(VectorReshapeHelper.class);
        cast.addFlags("--add-modules=jdk.incubator.vector", "-XX:UseAVX=1");
        String testMethods = String.join(",", TestMethods.AVX1_CAST_TESTS.split("\n"));
        cast.addFlags("-DTest=" + testMethods);
        cast.start();
    }
}
