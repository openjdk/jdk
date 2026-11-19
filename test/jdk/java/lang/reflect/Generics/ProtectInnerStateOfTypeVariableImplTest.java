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
 */

/*
 * @test
 * @bug 8372258
 * @summary Test if a copy of the internal state is provided
 * @run junit ProtectInnerStateOfTypeVariableImplTest
 */

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.TypeVariable;

import static org.junit.jupiter.api.Assertions.*;

final class ProtectInnerStateOfTypeVariableImplTest {

    static final class Foo {
        public <X> Foo() {
        }

        <X> X x() {
            return null;
        }
    }

    @Test
    void testMethod() throws NoSuchMethodException {
        Method method = Foo.class.getDeclaredMethod("x");
        TypeVariable<Method> tv = method.getTypeParameters()[0];

        Method gd = tv.getGenericDeclaration();
        Method gd2 = tv.getGenericDeclaration();
        assertNotSame(gd, gd2);
    }

    @Test
    void testConstructor() throws NoSuchMethodException {
        Constructor<?> ctor = Foo.class.getConstructor();
        TypeVariable<? extends Constructor<?>> tv = ctor.getTypeParameters()[0];

        Constructor<?> gd = tv.getGenericDeclaration();
        Constructor<?> gd2 = tv.getGenericDeclaration();
        assertNotSame(gd, gd2);
    }

}
