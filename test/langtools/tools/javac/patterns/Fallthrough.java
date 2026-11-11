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

/**
 * @test
 * @bug 8397040
 * @summary Check permitted switch fallthrough behavior in combination with patterns and guards.
 * @compile Fallthrough.java
 * @run main Fallthrough
 */

void main() {
    Object obj = new Object();
    assertEquals(test(null, null), 111);
    assertEquals(test(null, obj), 111);
    assertEquals(test(null, 1_000), 1_111);
    assertEquals(test("", null), 100);
    assertEquals(test("", obj), 110);
    assertEquals(test("", 1_000), 1_110);
    assertEquals(test(obj, null), 100);
    assertEquals(test(obj, obj), 100);
    assertEquals(test(obj, 1_000), 100);
}

int test(Object o1, Object o2) {
    int result = 0;
    switch(o1) {
        case null:
            result += 1;
        case String _ when o2 != null:
            if (o2 instanceof Integer i) {
                result += i;
            }
            result += 10;
        default:
            result += 100;
    }
    return result;
}

void assertEquals(int expected, int actual) {
    if (expected != actual) {
        throw new AssertionError("expected: " + expected +
                                 "actual: " + actual);
    }
}
