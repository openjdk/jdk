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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.invoke.MethodHandles;

public class CommandLineTestHelper {

    /**
     * The argument is a list of names of no-arg static methods in this class to invoke.
     * The names are separated with a '+'.
     */
    public static void main(String[] args) throws Exception {
        String[] methodNames = args.length > 0 ? args[0].split("\\+") : new String[0];
        for (String methodName : methodNames) {
            Method m = CommandLineTestHelper.class.getDeclaredMethod(methodName);
            m.invoke(null);
        }
    }

    static void testFieldSetInt() throws Exception {
        class C {
            final int value;
            C(int value) {
                this.value = value; }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);
        var obj = new C(100);
        f.setInt(obj, 200);
        if (obj.value != 200) {
            throw new RuntimeException("Unexpected value: " + obj.value);
        }
    }

    static void testUnreflectSetter() throws Throwable {
        class C {
            final int value;
            C(int value) {
                this.value = value; }
        }
        Field f = C.class.getDeclaredField("value");
        f.setAccessible(true);
        var obj = new C(100);
        MethodHandles.lookup().unreflectSetter(f).invoke(obj, 200);
        if (obj.value != 200) {
            throw new RuntimeException("Unexpected value: " + obj.value);
        }
    }

    /**
     * Used to test that the property value from startup is used.
     */
    static void setSystemPropertyToAllow() {
        System.setProperty("jdk.module.illegal.final.field.mutation", "allow");
    }
}