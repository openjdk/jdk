/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that C1 respects that static initializers can have memory side effects.
 * @bug 8357782
 * @requires vm.compiler1.enabled
 * @comment Since static initializers only execute in the first execution of the class initializer, we need -Xcomp.
 * @run main/othervm -Xcomp -XX:TieredStopAtLevel=1 -XX:CompileCommand=compileonly,compiler/c1/A$B.test compiler.c1.TestStaticInitializerSideEffect
 */

package compiler.c1;

public class TestStaticInitializerSideEffect {
    public static void main(String[] args) {
        A.B.test();
    }
}

class A {
    static class B {
        static String field;

        static void test() {
            // This unused variable triggers local value numbering to remove
            // the field load in the constructor below if it is not killed
            // before.
            String tmp = field;
            // The class initializer of C should kill the LVN effect of tmp due
            // to the memory side effects of the static initializer.
            new C(field);
        }
    }

    static class C {
        // When executing the class initializer, this has a side effect.
        static {
            B.field = "Hello";
        }

        C(String val) {
            // If C1 does not respect that side effect, we crash here.
            if (val == null) {
                throw new RuntimeException("Should not reach here");
            }
        }
    }
}
