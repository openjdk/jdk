/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.util.regex.Pattern;

import static java.util.Objects.requireNonNull;

//
// A set of testing utility functions
//
public final class TestKit {

    private TestKit() { }

    public static void assertNotThrows(ThrowingProcedure code) {
        requireNonNull(code, "code");
        assertNotThrows(() -> {
            code.run();
            return null;
        });
    }

    public static <V> V assertNotThrows(ThrowingFunction<V> code) {
        requireNonNull(code, "code");
        try {
            return code.run();
        } catch (Throwable t) {
            throw new RuntimeException("Expected to run normally, but threw "
                    + t.getClass().getCanonicalName(), t);
        }
    }

    public static <T extends Throwable> T assertThrows(Class<? extends T> clazz,
                                                       ThrowingProcedure code) {
        requireNonNull(clazz, "clazz");
        requireNonNull(code, "code");
        try {
            code.run();
        } catch (Throwable t) {
            if (clazz.isInstance(t)) {
                return clazz.cast(t);
            }
            throw new RuntimeException("Expected to catch an exception of type "
                    + clazz.getCanonicalName() + ", but caught "
                    + t.getClass().getCanonicalName(), t);

        }
        throw new RuntimeException("Expected to catch an exception of type "
                + clazz.getCanonicalName() + ", but caught nothing");
    }

    public interface ThrowingProcedure {
        void run() throws Throwable;
    }

    public interface ThrowingFunction<V> {
        V run() throws Throwable;
    }

    // The rationale behind asking for a regex is to not pollute variable names
    // space in the scope of assertion: if it's something as simple as checking
    // a message, we can do it inside
    public static <T extends Throwable> T assertThrows(Class<? extends T> clazz,
                                                       String messageRegex,
                                                       ThrowingProcedure code) {
        requireNonNull(messageRegex, "messagePattern");
        T t = assertThrows(clazz, code);
        String m = t.getMessage();
        if (m == null) {
            throw new RuntimeException(String.format(
                    "Expected exception message to match the regex '%s', " +
                            "but the message was null", messageRegex), t);
        }
        if (!Pattern.matches(messageRegex, m)) {
            throw new RuntimeException(String.format(
                    "Expected exception message to match the regex '%s', " +
                            "actual message: %s", messageRegex, m), t);
        }
        return t;
    }
}
