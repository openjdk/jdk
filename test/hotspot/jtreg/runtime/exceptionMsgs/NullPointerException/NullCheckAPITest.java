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
 * @summary Test the messages for arbitrary null-check APIs.
 * @bug 8233268
 * @library /test/lib
 * @modules java.base/jdk.internal.access
 * @compile -g NullCheckAPITest.java
 * @run junit/othervm -DnullCheckAPI.nestedThrow=true -XX:+ShowCodeDetailsInExceptionMessages NullCheckAPITest
 * @run junit/othervm -DnullCheckAPI.nestedThrow=false -XX:+ShowCodeDetailsInExceptionMessages NullCheckAPITest
 */

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class NullCheckAPITest {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final boolean NESTED_THROW = Boolean.getBoolean("nullCheckAPI.nestedThrow");

    // An arbitrary null-checking API
    static void nullCheck(Object arg) {
        if (arg == null) {
            if (NESTED_THROW) {
                // 2 offset: nullCheck, throwNpe;
                throwNullPointerException();
            } else {
                // 1 offset: nullCheck
                throw JLA.extendedNullPointerException(1, 0);
            }
        }
    }

    static void throwNullPointerException() {
        throw JLA.extendedNullPointerException(2, 0);
    }

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

    @Test
    void test() {
        checkSimpleMessage(() -> generateVariableNullPointerException(null), "myA");
        checkSimpleMessage(() -> generateVariableNullPointerException(new Dummy()), "myA.field");

        checkInvocationMessage(() -> nullCheck(int.class.getSuperclass()), "java.lang.Class.getSuperclass()");
    }

    static class One extends Dummy {
        One(NullCheckAPITest rnnt) {
            rnnt.super();
        }
    }

    @Test
    @Disabled("Requires javac's API support")
    void testRequireNonNull() {
        checkSimpleMessage(() -> {
            NullCheckAPITest t = null;
            t.new Dummy();
        }, "t");
        checkSimpleMessage(() -> new One(null), "rnnt");

        var npe = assertThrows(NullPointerException.class, () -> {
            try {
                Dummy.class.getDeclaredConstructor(NullCheckAPITest.class).newInstance((Object) null);
            } catch (InvocationTargetException ex) {
                throw ex.getCause();
            }
        });
        assertEquals("\"this$0\" is null", npe.getMessage());

        checkInvocationMessage(() -> {
            switch (int.class.getGenericSuperclass()) {
                case ParameterizedType pt -> {}
                case Class<?> cl -> {}
                default -> {}
            }
        }, "java.lang.Class.getGenericSuperclass()");
    }

    // A method that generate NPE from variables
    static void generateVariableNullPointerException(Dummy myA) {
        nullCheck(myA);
        nullCheck(myA.field);
    }
}