/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8206986 8214114 8214529
 * @summary Verify various corner cases with nested switch expressions.
 * @compile --enable-preview -source 12 ExpressionSwitchBugs.java
 * @run main/othervm --enable-preview ExpressionSwitchBugs
 */

public class ExpressionSwitchBugs {
    public static void main(String... args) {
        new ExpressionSwitchBugs().testNested();
        new ExpressionSwitchBugs().testAnonymousClasses();
        new ExpressionSwitchBugs().testFields();
    }

    private void testNested() {
        int i = 0;
        check(42, id(switch (42) {
            default: i++; break 42;
        }));
        i = 0;
        check(43, id(switch (42) {
            case 42: while (i == 0) {
                i++;
            }
            break 42 + i;
            default: i++; break 42;
        }));
        i = 0;
        check(42, id(switch (42) {
            case 42: if (i == 0) {
                break 42;
            }
            default: i++; break 43;
        }));
        i = 0;
        check(42, id(switch (42) {
            case 42: if (i == 0) {
                break 41 + switch (0) {
                    case 0 -> 1;
                    default -> -1;
                };
            }
            default: i++; break 43;
        }));
    }

    private void testAnonymousClasses() {
        for (int i : new int[] {1, 2}) {
            check(3, id((switch (i) {
                case 1: break new I() {
                    public int g() { return 3; }
                };
                default: break (I) () -> { return 3; };
            }).g()));
            check(3, id((switch (i) {
                case 1 -> new I() {
                    public int g() { return 3; }
                };
                default -> (I) () -> { return 3; };
            }).g()));
        }
    }

    private void testFields() {
        check(3, field);
        check(3, ExpressionSwitchBugs.staticField);
    }

    private final int value = 2;
    private final int field = id(switch(value) {
        case 0 -> -1;
        case 2 -> {
            int temp = 0;
            temp += 3;
            break temp;
        }
        default -> throw new IllegalStateException();
    });

    private static final int staticValue = 2;
    private static final int staticField = new ExpressionSwitchBugs().id(switch(staticValue) {
        case 0 -> -1;
        case 2 -> {
            int temp = 0;
            temp += 3;
            break temp;
        }
        default -> throw new IllegalStateException();
    });

    private int id(int i) {
        return i;
    }

    private int id(Object o) {
        return -1;
    }

    private void check(int actual, int expected) {
        if (actual != expected) {
            throw new AssertionError("Unexpected result: " + actual);
        }
    }

    public interface I {
        public int g();
    }
}
