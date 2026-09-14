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
 * @enablePreview
 * @compile LocalProxyVariablesRuntime.java
 * @run main LocalProxyVariablesRuntime
 */
public class LocalProxyVariablesRuntime {
    public static void main(String... args) {
        new Value1();
        new Value1(0);
        new Value2();
        new Value2(0);
        new Value3();
        new Value3(0);
        new Value4();
        new Value4(0);
    }

    private static value class Value1 {
        int i = 0;
        int j = i + 1;

        public Value1() {}

        public Value1(int x) {}
    }

    private static value class Value2 {
        Object f1 = new String("");
        String f2 = f1 instanceof String s ? s : "";

        public Value2() {}

        public Value2(int x) {}
    }

    private static value class Value3 {
        Object f1 = new String("");
        String f2 = !switch (f1) {
            case String s -> true;
            default -> false;
        } ? "a" : "b";

        public Value3() {}

        public Value3(int x) {}
    }

    private static value class Value4 {
        Object f1 = new String("");
        String f2 = switch (0) {
            default -> {
                boolean r;
                switch (f1) {
                    case String s:
                        r = true;
                        break;
                    default:
                        r = false;
                        break;
                }
                IF: if (true) break IF;
                for (int i = 0; i < 10; i++) {
                    if (i < 5) continue;
                    System.err.println(i);
                }
                yield r;
            }
        } ? "a" : "b";

        public Value4() {}

        public Value4(int x) {}
    }
}
