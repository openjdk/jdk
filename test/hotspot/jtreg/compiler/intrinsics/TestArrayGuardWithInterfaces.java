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

import java.lang.reflect.Array;
import jdk.test.lib.Asserts;

/**
 * @test
 * @bug 8348631
 * @summary Test folding of array guards used by intrinsics.
 * @library /test/lib
 * @run main/othervm -Xcomp -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,TestArrayGuardWithInterfaces::test*
 *                   TestArrayGuardWithInterfaces
 */
public class TestArrayGuardWithInterfaces {

    public static interface MyInterface { }

    public static int test1(Object obj) {
        // Should be folded, arrays can never imlement 'MyInterface'
        return Array.getLength((MyInterface)obj);
    }

    public static int test2(Object obj) {
        // Should not be folded, arrays implement 'Cloneable'
        return Array.getLength((Cloneable)obj);
    }

    public static void main(String[] args) {
        // Warmup
        Class c = MyInterface.class;
        Array.getLength(args);

        try {
            test1(null);
            throw new RuntimeException("No exception thrown");
        } catch (Exception e) {
            // Expected
        }
        Asserts.assertEQ(test2(new int[1]), 1);
    }
}
