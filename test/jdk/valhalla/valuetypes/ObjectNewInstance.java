/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Method handle and core reflection to invoke on the constructor of
 *          java.lang.Object (abstract class) should return an Identity instance
 * @enablePreview
 * @run testng/othervm ObjectNewInstance
 */

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class ObjectNewInstance {
    @Test
    public void classNewInstance() throws ReflectiveOperationException {
        Object o = Object.class.newInstance();
        assertTrue(o.getClass() == Object.class);
    }

    @Test
    public void constructorNewInstance() throws ReflectiveOperationException {
        Constructor<Object> ctor = Object.class.getDeclaredConstructor();
        Object o = ctor.newInstance();
        assertTrue(o.getClass() == Object.class);
    }

    @Test
    public void methodHandle() throws Throwable {
        MethodHandle mh = MethodHandles.publicLookup()
                                       .findConstructor(Object.class, MethodType.methodType(void.class));
        Object o = mh.invokeExact();
        assertTrue(o.getClass() == Object.class);
    }
}
