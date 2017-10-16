/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186092
 * @compile ../common/Foo.java
 *          ../common/J.java
 *          I.java
 *          ../common/C.jasm
 *          Task.java
 *          ../common/PreemptingClassLoader.java
 * @run main/othervm Test
 */

public class Test {

    // Test that the error message is correct when a loader constraint error is
    // detected during itable creation.
    //
    // In this test, during itable creation for class C, method "m()LFoo;" for
    // C's super interface I has a different class Foo than the selected method's
    // type super interface J.  The selected method is not an overpass method nor
    // otherwise excluded from loader constraint checking.  So, a LinkageError
    // exception should be thrown because the loader constraint check will fail.
    public static void main(String... args) throws Exception {
        Class<?> c = Foo.class; // forces standard class loader to load Foo
        ClassLoader l = new PreemptingClassLoader("Task", "Foo", "C", "I");
        Runnable r = (Runnable) l.loadClass("Task").newInstance();
        try {
            r.run();
            throw new RuntimeException("Expected LinkageError exception not thrown");
        } catch (LinkageError e) {
            if (!e.getMessage().contains(
                "loader constraint violation in interface itable initialization for class C:")) {
                throw new RuntimeException("Wrong LinkageError exception thrown: " + e.getMessage());
            }
        }
    }
}
