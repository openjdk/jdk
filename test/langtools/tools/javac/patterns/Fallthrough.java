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
    assertEquals(111, test(null, null));
    assertEquals(111, test(null, obj));
    assertEquals(1_111, test(null, 1_000));
    assertEquals(100, test("", null));
    assertEquals(110, test("", obj));
    assertEquals(1_110, test("", 1_000));
    assertEquals(100, test(obj, null));
    assertEquals(100, test(obj, obj));
    assertEquals(100, test(obj, 1_000));
    assertEquals(1_111_111, testRecord(null));
    assertEquals(1_111_110, testRecord(new R(1)));
    assertEquals(1_111_100, testRecord(new R(1f)));
    assertEquals(1_111_000, testRecord(new R("")));
    assertEquals(1_110_000, testRecord(new R(new StringBuilder())));
    assertEquals(1_100_000, testRecord(new R(null)));
    assertEquals(1_000_000, testRecord(""));
    assertEquals(1, testRecordFactorOut(null));
    assertEquals(1, testRecordFactorOut(new R(1)));
    assertEquals(2, testRecordFactorOut(new R(1f)));
    assertEquals(4, testRecordFactorOut(""));
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

int testRecord(Object o) {
    int result = 0;
    switch(o) {
        case null:
            result += 1;
        case R(Integer _):
            result += 10;
        case R(Number _):
            result += 100;
        case R(String _):
            result += 1_000;
        case R(CharSequence _):
            result += 10_000;
        case R(_):
            result += 100_000;
        default:
            result += 1000_000;
    }
    return result;
}

int testRecordFactorOut(Object o) {
    int result = 0;
    switch(o) {
        case null:
        case R(Integer _):
            result += 1;
            break;
        case R(Number _):
            result += 2;
            break;
        default:
            result += 4;
            break;
    }
    return result;
}

void assertEquals(int expected, int actual) {
    if (expected != actual) {
        throw new AssertionError("expected: " + expected +
                                 ", actual: " + actual);
    }
}

record R(Object o) {
}
