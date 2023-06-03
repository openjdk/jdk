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
    }

    static class D implements I {
        public String toString() {
            return "D";
        }
    }

    public static void main(String... args) throws Throwable {
        C c = new C();
        D d = new D();
        assertEquals(C.test(c), objectToString(c));
        assertEquals(I.test(d), objectToString(d));
    }

    static String objectToString(Object o) {
        return o.getClass().getName() + "@" + Integer.toHexString(o.hashCode());
    }
}
