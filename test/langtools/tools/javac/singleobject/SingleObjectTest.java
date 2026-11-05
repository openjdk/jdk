/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test contextual keyword 'singleobject' class declaration, inheritance, and singleton instance synthesis
 * @compile SingleObjectTest.java
 * @run main SingleObjectTest
 */

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class SingleObjectTest {

    static class BaseService {
        protected String prefix() {
            return "service";
        }
    }

    public singleobject class AppConfig extends BaseService implements Runnable {
        private int counter = 0;

        public int increment() {
            return ++counter;
        }

        @Override
        public void run() {
            increment();
        }
    }

    public static void main(String[] args) throws Exception {
        // 1. Contextual keyword backward-compatibility: local variable named 'singleobject'
        int singleobject = 42;
        if (singleobject != 42) {
            throw new AssertionError("Contextual keyword broke identifier usage");
        }

        // 2. Verify singleton identity and state across getInstance() calls
        AppConfig c1 = AppConfig.getInstance();
        AppConfig c2 = AppConfig.getInstance();
        if (c1 == null || c1 != c2) {
            throw new AssertionError("Expected identical singleton instance");
        }

        if (c1.increment() != 1 || c2.increment() != 2) {
            throw new AssertionError("Unexpected state across singleton references");
        }

        // 3. Verify superclass inheritance and interface implementation
        if (!"service".equals(c1.prefix())) {
            throw new AssertionError("Superclass method failed");
        }
        Runnable r = AppConfig.getInstance();
        r.run();
        if (c1.increment() != 4) {
            throw new AssertionError("Interface dispatch failed");
        }

        // 4. Verify class is implicitly final and constructor is private
        if (!Modifier.isFinal(AppConfig.class.getModifiers())) {
            throw new AssertionError("singleobject class must be implicitly final");
        }
        Constructor<?>[] constructors = AppConfig.class.getDeclaredConstructors();
        if (constructors.length != 1 || !Modifier.isPrivate(constructors[0].getModifiers())) {
            throw new AssertionError("singleobject class must have a single private constructor");
        }
    }
}
