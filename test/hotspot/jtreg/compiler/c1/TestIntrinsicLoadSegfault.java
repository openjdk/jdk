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

/*
 * @test
 * @summary Test that C1 does not wrongly hoist loop independent intrinsic loads above their corresponding checks.
 * @bug 8386708
 * @requires vm.compiler1.enabled
 * @run main/othervm -XX:TieredStopAtLevel=1
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test*
 *                   ${test.main.class}
 * @run main ${test.main.class}
 */

package compiler.c1;

public class TestIntrinsicLoadSegfault {
    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            try {
                testCharAt();
            } catch (StringIndexOutOfBoundsException e) { }
            try {
                testCodePointAt();
            } catch (StringIndexOutOfBoundsException e) { }
            try {
                testCodePointBefore();
            } catch (StringIndexOutOfBoundsException e) { }
            try {
                testBuilderCharAt();
            } catch (StringIndexOutOfBoundsException e) { }
            try {
                testBuilderCodePointAt();
            } catch (StringIndexOutOfBoundsException e) { }
            try {
                testBuilderCodePointBefore();
            } catch (StringIndexOutOfBoundsException e) { }
        }
    }

    static void testCharAt() {
        for (int i = 0; i < 10; i++) {
            // C1 hoists range-check-free LoadIndex out of the loop,
            // before the String.checkIndex guard makes it safe.
            // The index is obviously far out of bounds, which
            // is likely to go to unmapped memory and cause a segfault.
            "\u266B".charAt(1000_000_000);
        }
    }

    static void testCodePointAt() {
        for (int i = 0; i < 10; i++) {
            "\u266B".codePointAt(1000_000_000);
        }
    }

    static void testCodePointBefore() {
        for (int i = -1; i < 10; i++) {
            "\u265B".codePointBefore(1000_000_000);
        }
    }

    static void testBuilderCharAt() {
        for (int i = 0; i < 10; i++) {
            new StringBuilder("\u266B").charAt(1000_000_000);
        }
    }

    static void testBuilderCodePointAt() {
        for (int i = 0; i < 10; i++) {
            new StringBuilder("\u266B").codePointAt(1000_000_000);
        }
    }

    static void testBuilderCodePointBefore() {
        for (int i = -1; i < 10; i++) {
            new StringBuilder("\u265B").codePointBefore(1000_000_000);
        }
    }
}
