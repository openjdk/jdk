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

/**
 * @test
 * @bug 8301721
 * @library /test/lib
 * @run main FindSpecialObjectMethod
 * @summary Test findSpecial on Object methods calling from a class or interface.
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Objects;

import static java.lang.invoke.MethodType.*;
import static jdk.test.lib.Asserts.*;

public class FindSpecialObjectMethod {
    static class C {
        public static Object test(C o) throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle mh = lookup.findSpecial(Object.class, "toString", methodType(String.class), C.class);
            return mh.invoke(o);
        }

        public String toString() {
            return "C";
        }
    }

    interface I {
        static Object test(I o) throws Throwable {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle mh = lookup.findSpecial(Object.class, "toString", methodType(String.class), I.class);
            return mh.invoke(o);
        }

        static void noAccess() throws Throwable {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                MethodHandle mh = lookup.findSpecial(String.class, "hashCode", methodType(int.class), I.class);
                throw new RuntimeException("IllegalAccessException not thrown");
            } catch (IllegalAccessException ex) {}
        }
    }

    public static void main(String... args) throws Throwable {
        // Object.toString can be called from invokespecial from within
        // a special caller class C or interface I
        C c = new C();
        I i = new I() {};
        assertEquals(C.test(c), Objects.toIdentityString(c));
        assertEquals(I.test(i), Objects.toIdentityString(i));

        // I has no access to methods in other class besides Object
        I.noAccess();
    }
}
