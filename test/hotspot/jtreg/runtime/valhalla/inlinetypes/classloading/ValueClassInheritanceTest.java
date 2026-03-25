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
 *
 */

 /*
 * @test
 * @summary Ensures a non-abstract value class cannot inherit from an identity class.
 * @enablePreview
 * @clean Parent
 * @clean Child
 * @compile Parent.jcod
 * @compile Child.jcod
 * @run junit runtime.valhalla.inlinetypes.classloading.ValueClassInheritanceTest
 */

package runtime.valhalla.inlinetypes.classloading;

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class ValueClassInheritanceTest {
    private final boolean DEBUG = false;

    @Test
    void test() throws ReflectiveOperationException {
        try {
            // We create a new instance of the child class.
            // This should see that the class hierarchy is illegal and throw an exception.
            Class<?> clazz = Class.forName("Child");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (DEBUG) System.out.println(instance);
        } catch (IncompatibleClassChangeError weWantThis) {
            // The error that we are looking for.
            return;
        }
        // A lack of exception will fail this test.
        throw new IllegalStateException("expected IncompatibleClassChangeError to be thrown");
    }

}
