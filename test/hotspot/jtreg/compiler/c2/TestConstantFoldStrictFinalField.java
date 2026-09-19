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
package compiler.c2;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.helpers.StrictInit;

/*
 * @test
 * @bug 8392413
 * @summary Test constant fold strict final fields.
 * @library /test/lib /
 * @enablePreview
 * @compile ${test.file}
 * @run driver jdk.test.lib.helpers.StrictProcessor
 *             ${test.main.class}$Int
 * @run driver ${test.main.class}
 */
public class TestConstantFoldStrictFinalField {
    private static class Int {
        @StrictInit
        final int v;

        Int(int v) {
            this.v = v;
            super();
        }
    }

    private static final Int OBJECT = new Int(1);

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("--enable-preview");
        framework.start();
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    private static int testFoldStrictFinal() {
        return OBJECT.v;
    }

    @Run(test = "testFoldStrictFinal")
    public void runFoldStrictFinal() {
        Asserts.assertEQ(OBJECT.v, testFoldStrictFinal());
    }
}
