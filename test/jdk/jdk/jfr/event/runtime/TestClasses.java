/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jfr.event.runtime;

public class TestClasses {

    protected TestClassPrivate testClassPrivate;
    protected TestClassPrivateStatic testClassPrivateStatic;

    public TestClasses() {
        testClassPrivate = new TestClassPrivate();
        testClassPrivateStatic = new TestClassPrivateStatic();
    }

    // Classes TestClassPrivate and TestClassPrivateStatic should be loaded at
    // the same time
    // as the base class TestClasses
    private class TestClassPrivate {
    }

    private static class TestClassPrivateStatic {
    }

    protected class TestClassProtected {
    }

    protected static class TestClassProtectedStatic {
    }

    // When loadClasses() is run, 3 new classes should be loaded.
    public void loadClasses() throws ClassNotFoundException {
        final ClassLoader cl = getClass().getClassLoader();
        cl.loadClass("jdk.jfr.event.runtime.TestClasses$TestClassProtected1");
        cl.loadClass("jdk.jfr.event.runtime.TestClasses$TestClassProtectedStatic1");
    }

    protected class TestClassProtected1 {
    }

    protected static class TestClassProtectedStatic1 {
        protected TestClassProtectedStaticInner testClassProtectedStaticInner = new TestClassProtectedStaticInner();

        protected static class TestClassProtectedStaticInner {
        }
    }

    public static class TestClassPublicStatic {
        public static class TestClassPublicStaticInner {
        }
    }

}

class TestClass {
    static {
        // force creation of anonymous class (for the lambda form)
        Runnable r = () -> System.out.println("Hello");
        r.run();
    }
}
