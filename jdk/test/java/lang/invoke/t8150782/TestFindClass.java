/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @compile TestFindClass.java TestCls.java
 * @run testng/othervm -ea -esa test.java.lang.invoke.t8150782.TestFindClass
 */
package test.java.lang.invoke.t8150782;

import java.lang.invoke.*;

import static java.lang.invoke.MethodHandles.*;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.*;

public class TestFindClass {

    private static final String PACKAGE_PREFIX = "test.java.lang.invoke.t8150782.";

    private static boolean initializedClass1;

    private static class Class1 {
        static {
            initializedClass1 = true;
        }
    }

    @Test
    public void initializerNotRun() throws IllegalAccessException, ClassNotFoundException {
        lookup().findClass(PACKAGE_PREFIX + "TestFindClass$Class1");
        assertFalse(initializedClass1);
    }

    @Test
    public void returnsRequestedClass() throws IllegalAccessException, ClassNotFoundException {
        Class<?> aClass = lookup().findClass(PACKAGE_PREFIX + "TestFindClass$Class1");
        assertEquals(Class1.class, aClass);
    }

    @Test(expectedExceptions = {ClassNotFoundException.class})
    public void classNotFoundExceptionTest() throws IllegalAccessException, ClassNotFoundException {
        lookup().findClass(PACKAGE_PREFIX + "TestFindClass$NonExistent");
    }

    @DataProvider
    Object[][] illegalAccessFind() {
        return new Object[][] {
                {publicLookup(), PACKAGE_PREFIX + "TestFindClass$Class1"},
                {publicLookup(), PACKAGE_PREFIX + "TestCls$PrivateSIC"}
        };
    }

    /**
     * Assertion: @throws IllegalAccessException if the class is not accessible, using the allowed access modes.
     */
    @Test(dataProvider = "illegalAccessFind", expectedExceptions = {ClassNotFoundException.class})
    public void illegalAccessExceptionTest(Lookup lookup, String className) throws IllegalAccessException, ClassNotFoundException {
        lookup.findClass(className);
    }

    @Test
    public void okAccess() throws IllegalAccessException, ClassNotFoundException {
        lookup().findClass(PACKAGE_PREFIX + "TestCls$PrivateSIC");
    }

}
