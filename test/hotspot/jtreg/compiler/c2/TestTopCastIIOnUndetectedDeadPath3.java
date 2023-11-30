/*
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @bug 8319372
 * @summary CastII because of condition guarding it becomes top
 * @run main/othervm -Xcomp -XX:CompileOnly=TestTopCastIIOnUndetectedDeadPath3::* -XX:-TieredCompilation TestTopCastIIOnUndetectedDeadPath3
 */

public class TestTopCastIIOnUndetectedDeadPath3 {

    static long test() {
        int x = 6, y = 5;
        int[] iArr = new int[200];
        for (int i = 129; i > 5; i -= 2) { // OSR compiled
            try {
                y = iArr[i - 1];
                x = iArr[i + 1];
                x = 1 / i;
            } catch (ArithmeticException a_e) {
            }
        }
        Foo.empty();
        return x + y;
    }

    public static void main(String[] strArr) {
        new TestTopCastIIOnUndetectedDeadPath3();
        for (int i = 0; i < 2000; i++) {
            test();
        }
    }
}

class Foo {
    public static void empty() {}
}
