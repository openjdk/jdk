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
 * @bug 8348572
 * @summary Tests where new irreducible loop is introduced by split_if.
 *
 * @run driver compiler.loopopts.TestSplitIfNewIrreducibleLoop
 *
 * @run main/othervm -Xcomp -XX:PerMethodTrapLimit=0
 *                   -XX:CompileCommand=compileonly,compiler.loopopts.TestSplitIfNewIrreducibleLoop::test
 *                   compiler.loopopts.TestSplitIfNewIrreducibleLoop
 */

package compiler.loopopts;

public class TestSplitIfNewIrreducibleLoop {

    static class A {
        public A parent;
    }

    static class B extends A {}

    public static void main(String[] args) {
        // Instantiate one each: classes are loaded.
        A a = new A();
        B b = new B();
        test(b);
    }

    static int test(A parent) {
        do {
            if (parent instanceof B b) { return 1; }
            if (parent != null) { parent = parent.parent; }
            if (parent == null) { return 0; }
        } while (true);
    }

    // Before split_if it looks like this (the instanceof check has already been partial peeled):
    //
    // if (parent instanceof B b) { return 1; }
    // do {
    //     if (parent != null) { parent = parent.parent; }
    //     if (parent == null) { return 0; }
    //     if (parent instanceof B b) { return 1; }
    // } while (true);
    //
    //
    // Now, we want to split_if the first if in the loop body, like this:
    //
    // if (parent instanceof B b) { return 1; }
    // if (parent != null) { goto LOOP2; } else { goto LOOP1; }
    // do {
    //     :LOOP1
    //     parent = parent.parent;
    //     :LOOP2
    //     if (parent == null) { return 0; }
    //     if (parent instanceof B b) { return 1; }
    //     if (parent != null) { goto LOOP2; } else { goto LOOP1; }
    // } while (true);
    //
    // As the comment in ifnode.cpp / split_if says: we know that on the backedge
    // "parent" cannot be null, and so we would be able to split the if through
    // the region, and on the backedge it would constant fold away, and we would
    // only have to check the split if in the loop entry.
    //
    // Problem: we have introduced an irreducible loop!
}

