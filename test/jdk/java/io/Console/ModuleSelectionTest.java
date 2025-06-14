/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8295803 8299689 8351435
 * @summary Tests System.console() returns correct Console (or null) from the expected
 *          module.
 * @modules java.base/java.io:+open
 * @run main/othervm ModuleSelectionTest java.base
 * @run main/othervm -Djdk.console=jdk.internal.le ModuleSelectionTest jdk.internal.le
 * @run main/othervm -Djdk.console=java.base ModuleSelectionTest java.base
 * @run main/othervm --limit-modules java.base ModuleSelectionTest java.base
 */

import java.io.Console;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ModuleSelectionTest {
    public static void main(String... args) throws Throwable {
        var con = System.console();
        var pc = Class.forName("java.io.ProxyingConsole");
        var jdkc = Class.forName("jdk.internal.io.JdkConsole");
        var istty = (boolean)MethodHandles.privateLookupIn(Console.class, MethodHandles.lookup())
                .findStatic(Console.class, "istty", MethodType.methodType(boolean.class))
                .invoke();
        var impl = con != null ? MethodHandles.privateLookupIn(pc, MethodHandles.lookup())
                .findGetter(pc, "delegate", jdkc)
                .invoke(con) : null;

        var expected = switch (args[0]) {
            case "java.base" -> istty ? "java.base" : "null";
            default -> args[0];
        };
        var actual = con == null ? "null" : impl.getClass().getModule().getName();

        if (!actual.equals(expected)) {
            throw new RuntimeException("""
                Console implementation is not the expected one.
                Expected: %s
                Actual: %s
                """.formatted(expected, actual));
        } else {
            System.out.printf("%s is the expected implementation. (tty: %s)\n", impl, istty);
        }
    }
}
