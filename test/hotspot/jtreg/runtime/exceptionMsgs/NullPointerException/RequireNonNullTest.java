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
 * @summary Test the messages for 1-arg requireNonNull.
 * @bug 8233268
 * @library /test/lib
 * @compile -g RequireNonNullTest.java
 * @run junit/othervm -XX:+ShowCodeDetailsInExceptionMessages RequireNonNullTest
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RequireNonNullTest {

    /// A simple NPE message for an expression
    static String simpleMessage(String cause) {
        return "\"" + cause + "\" is null";
    }

    /// An NPE message for an invocation result
    static String invocationMessage(String cause) {
        return "The return value of " + simpleMessage(cause);
    }

    static void checkSimpleMessage(Executable action, String cause) {
        var msg = assertThrows(NullPointerException.class, action).getMessage();
        assertEquals(simpleMessage(cause), msg);
    }

    static void checkInvocationMessage(Executable action, String cause) {
        var msg = assertThrows(NullPointerException.class, action).getMessage();
        assertEquals(invocationMessage(cause), msg);
    }

    class Dummy { Object field; }

    static class One extends Dummy {
        One(RequireNonNullTest rnnt) {
            rnnt.super();
        }
    }

    @Test
    void test() {
        checkSimpleMessage(() -> generateVariableNpe(null), "myA");
        checkSimpleMessage(() -> generateVariableNpe(new Dummy()), "myA.field");
        checkSimpleMessage(() -> {
            RequireNonNullTest t = null;
            t.new Dummy();
        }, "t");
        checkSimpleMessage(() -> new One(null), "rnnt");

        var npe = assertThrows(NullPointerException.class, () -> {
            try {
                Dummy.class.getDeclaredConstructor(RequireNonNullTest.class).newInstance((Object) null);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        });
        assertEquals("\"this$0\" is null", npe.getMessage());

        checkInvocationMessage(() -> Objects.requireNonNull(int.class.getSuperclass()), "java.lang.Class.getSuperclass()");
        checkInvocationMessage(() -> {
            switch (int.class.getGenericSuperclass()) {
                case ParameterizedType pt -> {}
                case Class<?> cl -> {}
                default -> {}
            }
        }, "java.lang.Class.getGenericSuperclass()");
    }

    // A method that generate NPE from variables
    static void generateVariableNpe(Dummy myA) {
        Objects.requireNonNull(myA);
        Objects.requireNonNull(myA.field);
    }
}