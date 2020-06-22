/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
import jdk.test.lib.compiler.InMemoryJavaCompiler;
import jdk.internal.misc.Unsafe;

public class UnsafeAndLambdaApp {

    static byte klassbuf[] = InMemoryJavaCompiler.compile("LambdaHello",
        "public class LambdaHello { " +
        "    public LambdaHello() { " +
        "        doit(() -> { " +
        "            System.out.println(\"Hello from Lambda\"); " +
        "        }); " +
        "    } " +
        "    static void doit (Runnable r) { " +
        "        r.run(); " +
        "    } " +
        "} ");

    public static void main(String args[]) throws Exception {
        Unsafe unsafe = Unsafe.getUnsafe();

        Class klass = unsafe.defineAnonymousClass(UnsafeAndLambdaApp.class, klassbuf, new Object[0]);
        try {
            Object obj = klass.newInstance();
            // If we come to here, LambdaMetafactory has probably been modified
            // to support vm-anon classes. In that case, we will need more tests for CDS.
            throw new RuntimeException("Unexpected support for lambda classes in VM anonymous classes");
        } catch (java.lang.InternalError expected) {
            System.out.println("Caught expected java.lang.InternalError");
        }
    }
}
