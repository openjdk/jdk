/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8212117
 * @summary Verify JVMTI GetClassStatus returns CLASS_PREPARE after call to Class.forName()
 * @run main/othervm/native -agentlib:ClassStatus ClassStatus
 */


public class ClassStatus {
    static {
        try {
            System.out.println("ClassStatus static block");
            System.loadLibrary("ClassStatus");
        } catch (UnsatisfiedLinkError ule) {
            System.err.println("Could not load ClassStatus library");
            System.err.println("java.library.path: "
                + System.getProperty("java.library.path"));
            throw ule;
        }
    }

    static native int check(Class klass);

    public static void main(String[] args) throws ClassNotFoundException {
        ClassLoader loader = ClassStatus.class.getClassLoader();
        Module module = loader.getUnnamedModule();

        // Load class, but don't initialize it
        Class foo2 = Class.forName(module, "Foo2");
        Class foo3 = Class.forName("Foo3", false, loader);

        System.out.println("Loaded: " + foo2);
        System.out.println("Loaded: " + foo3);

        int status2 = check(foo2);
        int status3 = check(foo3);

        new Foo2().bar();
        new Foo3().bar();

        if (status2 != 0) {
            System.out.println("The agent returned non-zero exit status for Foo2: " + status2);
        }
        if (status3 != 0) {
            System.out.println("The agent returned non-zero exit status for Foo3: " + status3);
        }
        if (status2 != 0 || status3 != 0) {
            throw new RuntimeException("Non-zero status returned from the agent");
        }
    }
}

class Foo2 {
    static {
        System.out.println("Foo2 is initialized");
    }
    void bar() {
        System.out.println("Foo2.bar() is called");
    }
}

class Foo3 {
    static {
        System.out.println("Foo3 is initialized");
    }
    void bar() {
        System.out.println("Foo3.bar() is called");
    }
}
