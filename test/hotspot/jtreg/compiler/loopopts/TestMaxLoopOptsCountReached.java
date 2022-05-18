/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284944
 * @requires vm.compiler2.enabled
 * @summary triggers the loop optimization phase `LoopOptsCount` many times
 * @run main/othervm -Xcomp -XX:-PartialPeelLoop -XX:CompileCommand=compileonly,TestMaxLoopOptsCountReached::test TestMaxLoopOptsCountReached
 */

import java.lang.System.Logger.Level;

public class TestMaxLoopOptsCountReached {

    static Long a = Long.valueOf(42);

    class A {

        static String e(long f, boolean b, String g, Level h, String s,
                        Object... i) {
            return "message" + s + new String() + g;
        }
    }

    public static void main(String[] args) {
        test(null, "", null, null);
        test(null, "", null, null);
    }

    static void test(Integer o, String g, String name, Object obj) {
        for (Level q : Level.values())
            for (Level r : Level.values())
                A.e(a.longValue(), q != Level.OFF, g, null, null);
        for (Level q : Level.values())
            for (Level r : Level.values())
                A.e(a.longValue(), q != Level.OFF, g, null, null);
        for (Level q : Level.values()) {
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
        }
        for (Level q : Level.values()) {
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
        }
        for (Level q : Level.values()) {
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
            for (Level r : Level.values())
                ;
        }
        for (Level q : Level.values()) {
            for (Level r : Level.values())
                ;
            for (Level r : Level.values())
                ;
        }
        for (Level q : Level.values()) {
            for (Level r : Level.values()) {
                String msg = q + "message";
                String val =
                        (q != Level.OFF || name != msg)
                                ? A.e(a.longValue(), q != Level.OFF, g, null, null, "foo")
                                : null;
            }
            for (Level r : Level.values())
                ;
        }
        for (Level q : Level.values()) {
            for (Level r : Level.values())
                ;
            for (Level r : Level.values())
                ;
        }
    }
}
