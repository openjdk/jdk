/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package compiler.arraycopy;

import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8379228
 * @summary Verify that the trailing MemBar after clone expansion is marked as TrailingExpandedArrayCopy.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver ${test.main.class}
 */
public class TestCloneMemBarKind {

    public static void main(String[] args) {
        TestFramework.run();
    }

    // More than 8 (=ArrayCopyLoadStoreMaxElem) fields so the clone is expanded
    // as an arraycopy stub call (is_clonebasic), not as inline loads/stores.
    static class BigObj implements Cloneable {
        int i1, i2, i3, i4, i5, i6, i7, i8, i9;

        public Object clone() throws CloneNotSupportedException {
            return super.clone();
        }
    }

    static BigObj src = new BigObj();

    @Test
    @IR(applyIf = {"ArrayCopyLoadStoreMaxElem", "< 9"},
            phase = CompilePhase.AFTER_MACRO_EXPANSION,
            counts = {"MemBar.*TrailingExpandedArrayCopy", ">= 1"})
    static Object testClone() throws CloneNotSupportedException {
        return src.clone();
    }

    @Run(test = "testClone")
    void runner() throws CloneNotSupportedException {
        src.i1 = 42;
        testClone();
    }
}
