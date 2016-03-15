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
 * @compile TestAccessClass.java TestCls.java
 * @run testng/othervm -ea -esa test.java.lang.invoke.t8150782.TestAccessClass
 */
package test.java.lang.invoke.t8150782;

import java.lang.invoke.*;

import static java.lang.invoke.MethodHandles.*;

import static org.testng.AssertJUnit.*;

import org.testng.annotations.*;

public class TestAccessClass {

    private static boolean initializedClass1;

    private static class Class1 {
        static {
            initializedClass1 = true;
        }
    }

    @Test
    public void initializerNotRun() throws IllegalAccessException {
        lookup().accessClass(Class1.class);
        assertFalse(initializedClass1);
    }

    @Test
    public void returnsSameClass() throws IllegalAccessException, ClassNotFoundException {
        Class<?> aClass = lookup().accessClass(Class1.class);
        assertEquals(Class1.class, aClass);
    }

    @DataProvider
    Object[][] illegalAccessAccess() {
        return new Object[][] {
                {publicLookup(), Class1.class},
                {publicLookup(), TestCls.getPrivateSIC()}
        };
    }

    @Test(dataProvider = "illegalAccessAccess", expectedExceptions = {IllegalAccessException.class})
    public void illegalAccessExceptionTest(Lookup lookup, Class<?> klass) throws IllegalAccessException, ClassNotFoundException {
        lookup.accessClass(klass);
    }

    @Test
    public void okAccess() throws IllegalAccessException {
        lookup().accessClass(TestCls.getPrivateSIC());
    }

}
