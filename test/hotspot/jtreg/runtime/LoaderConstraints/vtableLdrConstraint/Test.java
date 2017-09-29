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
    // detected during vtable creation.
    //
    // In this test, during vtable creation for class Task, method "Task.m()LFoo;"
    // overrides "J.m()LFoo;".  But, Task's class Foo and super type J's class Foo
    // are different.  So, a LinkageError exception should be thrown because the
    // loader constraint check will fail.
    public static void main(String args[]) throws Exception {
        Class<?> c = Foo.class; // forces standard class loader to load Foo
        ClassLoader l = new PreemptingClassLoader("Task", "Foo", "I");
        l.loadClass("Foo");
        try {
            l.loadClass("Task").newInstance();
            throw new RuntimeException("Expected LinkageError exception not thrown");
        } catch (LinkageError e) {
            if (!e.getMessage().contains(
                    "loader constraint violation for class Task: when selecting overriding method") ||
                !e.getMessage().contains(
                    "for its super type J have different Class objects for the type Foo")) {
                throw new RuntimeException("Wrong LinkageError exception thrown: " + e.getMessage());
            }
        }
    }

}

