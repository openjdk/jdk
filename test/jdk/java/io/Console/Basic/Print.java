/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Console;
import java.util.function.Consumer;

public class Print {

    private static final Object[] OBJECTS = {
            null,
            false,
            (byte) 1,
            (short) 2,
            'a',
            3,
            4L,
            5f,
            6d,
            new Object(),
            "%s", // to test that print(ln) does not interpret its argument as a format string
            new char[]{'a'},
    };

    public static void main(String[] args) {
        switch (args[0]) {
            case "print" -> {
                print(obj -> System.console().format("%s", obj).flush());
                print(obj -> Console.Basic.print(obj));
            }
            case "println" -> {
                print(obj -> System.console().format("%s%n", obj).flush());
                print(obj -> Console.Basic.println(obj));
            }
            default -> throw new IllegalArgumentException();
        }
    }

    private static void print(Consumer<Object> printer) {
        for (var obj : OBJECTS) {
            printer.accept(obj);
        }
    }
}
