/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318144
 * @summary Verify switches work in presence of enum constants that have bodies
 * @compile SwitchEnumConstants.java
 * @run main SwitchEnumConstants
 */

import java.util.function.ToIntFunction;

public class SwitchEnumConstants {

    public static void main(String... args) throws Exception {
        new SwitchEnumConstants().run();
    }

    void run() throws Exception {
        doRun(this::typeSwitch);
        doRun(this::enumSwitch);
    }

    void doRun(ToIntFunction<Object> c) throws Exception {
        assertEquals(0, c.applyAsInt(E.A));
        assertEquals(1, c.applyAsInt(E.B));
        assertEquals(2, c.applyAsInt(E.C));
        assertEquals(3, c.applyAsInt(""));
    }

    int typeSwitch(Object o) {
        return switch (o) {
            case E.A -> 0;
            case E.B -> 1;
            case E.C -> 2;
            case String s -> 3;
            default -> throw new IllegalStateException();
        };
    }

    int enumSwitch(Object o) {
        if (!(o instanceof E e)) {
            return 3;
        }
        return switch (e) {
            case A -> 0;
            case B -> 1;
            case C -> 2;
        };
    }


    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError("expected: " + expected +
                                     ", actual: " + actual);
        }
    }

    enum E {
        A {},
        B {},
        C {}
    }
}
