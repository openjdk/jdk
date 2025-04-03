/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

public class OldClassAndRedefineClassApp {

    public static void main(String args[]) throws Throwable {
        ClassLoader appClassLoader = ClassLoader.getSystemClassLoader();

        System.out.println("Main: loading OldSuper");
        // Load an old class (version 49), but not linking it.
        Class.forName("OldSuper", false, appClassLoader);

        // Redefine a class unrelated to the above old class.
        System.out.println("INFO: instrumentation = " + RedefineClassHelper.instrumentation);
        Class<?> c = Class.forName("Hello", false, appClassLoader);
        byte[] bytes = c.getClassLoader().getResourceAsStream(c.getName().replace('.', '/') + ".class").readAllBytes();
        RedefineClassHelper.redefineClass(c, bytes);

        System.out.println("Main: loading ChildOldSuper");
        // Load and link a subclass of the above old class.
        // This will in turn link the old class and initializes its vtable, etc.
        Class.forName("ChildOldSuper");
    }
}
