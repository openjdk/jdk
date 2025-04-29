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
 *
 */

/*
 * @test
 * @bug 8347018
 * @summary Test that stores cloned with clone_up_backedge_goo() are not pinned above Assertion Predicates on which a
 *          load node is pinned at which will later fail in scheduling.
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,*TestLoadPinnedAboveAssertionPredicatesAndUsingStore::test
 *                   compiler.predicates.assertion.TestLoadPinnedAboveAssertionPredicatesAndUsingStore
 */

package compiler.predicates.assertion;

public class TestLoadPinnedAboveAssertionPredicatesAndUsingStore {
    static int iFld;
    static int iArr[] = new int[100];

    static void test() {
        int i = 63;
        do {
            iArr[1] = 34;
            iArr[i] += iFld;
            for (int j = i; j < 1; j++) {
            }
        } while (--i > 0);
    }

    public static void main(String[] strArr) {
        for (int i = 0; i < 10000; i++) {
            test();
        }
    }
}
