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

package compiler.c2.igvn;

/*
 * @test
 * @bug 8395951
 * @summary MemNode::Ideal_common optimizes unsafe null access with Halt,
 *          must add root to IGVN worklist because Halt added as req input.
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -XX:-TieredCompilation -Xcomp
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:VerifyIterativeGVN=1110
 *                   -XX:CompileOnly=${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

import jdk.internal.misc.Unsafe;

public class TestRootOnIGVNWorklistAfterHaltForUnsafeNullAccess {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    public static void main(String[] args) {
        test(false);
    }

    static void test(boolean neverTake) {
        // Never enter this loop, it would dereference nullptr with Unsafe.
        // It is basically dead code, but we should still not hit asserts
        // while compiling it.
        if (neverTake) {
            long unsafeAdr = 1000;
            for (int i = 0; i < 1000; i++) {
                unsafeAdr--;
            }
            // At first, unsafeAdr has unknown type.
            // But after a while it will become zero:
            // it will be a TypePtr::NULL_PTR address.
            //
            // MemNode::Idea_common detects such situations
            // and inserts a HaltNode, since such code should
            // never be reached.
            //
            // Unfortunately, we modify the root, but do not
            // add it to the IGVN worklist, so we later hit
            // the assert:
            //   modified node was not processed by IGVN.transform_old()
            UNSAFE.putInt(unsafeAdr, 0);
        }
    }
}
