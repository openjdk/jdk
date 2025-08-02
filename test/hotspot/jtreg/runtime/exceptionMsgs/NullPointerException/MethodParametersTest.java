/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the MethodParameters-based NPE messages.
 * @bug 8233268
 * @library /test/lib
 * @clean MethodParametersTest InnerClass
 * @compile -parameters -g:none MethodParametersTest.java
 * @run junit/othervm -XX:+ShowCodeDetailsInExceptionMessages MethodParametersTest
 */

import java.lang.reflect.InvocationTargetException;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MethodParametersTest {

    class InnerClass {}

    @Test
    void testOuterThis() {
        var npe = assertThrows(NullPointerException.class, () -> {
            try {
                InnerClass.class.getDeclaredConstructor(MethodParametersTest.class).newInstance((Object) null);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        });
        assertEquals("\"this$0\" is null", npe.getMessage());
    }

    // Random slot to param index mappings, both raw and requireNonNull NPEs
    // 0, 1, 3, 5
    static void myMethod(String firstArg, long l1, double l2, int[] lastArg) {
        Objects.requireNonNull(firstArg);
        System.out.println(lastArg.length);
        Object a = l1 > 100 ? null : ""; // 6
        a.toString();
    }

    @Test
    void testShuffles() {
        var npe = assertThrows(NullPointerException.class, () -> myMethod(null, 2, 2, new int[0]));
        assertEquals("\"firstArg\" is null", npe.getMessage());
        var msg = assertThrows(NullPointerException.class, () -> myMethod("", 2, 2, null)).getMessage();
        assertTrue(msg.endsWith("because \"lastArg\" is null"), msg);
        msg = assertThrows(NullPointerException.class, () -> myMethod("", 2000, 2, new int[0])).getMessage();
        assertTrue(msg.endsWith("because \"<local6>\" is null"), msg); // No debug info
    }
}