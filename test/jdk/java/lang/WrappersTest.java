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
 */


import org.testng.annotations.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8252180
 * @summary Test the primitive wrappers constructors are deprecated for removal
 * @run testng WrappersTest
 */

@Test
public class WrappersTest {

    @Test
    void checkForDeprecated() {
        List<Class<?>> classes =
                List.of(Byte.class,
                        Short.class,
                        Integer.class,
                        Long.class,
                        Float.class,
                        Double.class,
                        Character.class,
                        Boolean.class);
        for (Class<?> cl : classes) {
            for (Constructor<?> cons : cl.getConstructors()) {
                Deprecated dep = cons.getAnnotation(Deprecated.class);
                assertNotNull(dep, "Missing @Deprecated annotation");
                System.out.println(cons + ": " + dep);
                assertTrue(dep.forRemoval(), cl.toString() + " deprecated for removal: ");
            }
        }
    }
}
