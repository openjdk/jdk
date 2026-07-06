/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @build ModuleSetAccessibleTest
 * @modules java.base/java.lang:open
 *          java.base/jdk.internal.misc:+open
 * @run junit/othervm ModuleSetAccessibleTest
 * @summary Test java.lang.reflect.AccessibleObject with modules
 */

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import jdk.internal.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ModuleSetAccessibleTest {

    /**
     * Invoke a private constructor on a public class in an exported package
     */
    @Test
    public void testPrivateConstructorInExportedPackage() throws Exception {
        Constructor<?> ctor = Unsafe.class.getDeclaredConstructor();

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance());

        ctor.setAccessible(true);
        Unsafe unsafe = (Unsafe) ctor.newInstance();
    }


    /**
     * Invoke a private method on a public class in an exported package
     */
    @Test
    public void testPrivateMethodInExportedPackage() throws Exception {
        Method m = Unsafe.class.getDeclaredMethod("throwIllegalAccessError");
        assertThrows(IllegalAccessException.class, () -> m.invoke(null));

        m.setAccessible(true);
        InvocationTargetException e = assertThrows(InvocationTargetException.class, () ->
                m.invoke(null));
        // thrown by throwIllegalAccessError
        assertInstanceOf(IllegalAccessError.class, e.getCause());
    }


    /**
     * Access a private field in a public class that is an exported package
     */
    @Test
    public void testPrivateFieldInExportedPackage() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        assertThrows(IllegalAccessException.class, () -> f.get(null));

        f.setAccessible(true);
        Unsafe unsafe = (Unsafe) f.get(null);
    }


    /**
     * Invoke a public constructor on a public class in a non-exported package
     */
    @Test
    public void testPublicConstructorInNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Constructor<?> ctor = clazz.getConstructor(String.class);

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance("cn=duke"));

        assertThrows(InaccessibleObjectException.class, () -> ctor.setAccessible(true));

        ctor.setAccessible(false); // should succeed
    }


    /**
     * Access a public field in a public class that in a non-exported package
     */
    @Test
    public void testPublicFieldInNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Field f = clazz.getField("SERIALNUMBER_OID");

        assertThrows(IllegalAccessException.class, () -> f.get(null));

        assertThrows(InaccessibleObjectException.class, () -> f.setAccessible(true));

        f.setAccessible(false); // should succeed
    }


    /**
     * Test that the Class constructor cannot be make accessible.
     */
    @Test
    public void testJavaLangClass() throws Exception {

        // non-public constructor
        Constructor<?> ctor
            = Class.class.getDeclaredConstructor(ClassLoader.class, Class.class, char.class,
                                                 ProtectionDomain.class, boolean.class, char.class);
        AccessibleObject[] ctors = { ctor };

        assertThrows(SecurityException.class, () -> ctor.setAccessible(true));
        assertThrows(SecurityException.class, () -> AccessibleObject.setAccessible(ctors, true));

        // should succeed
        ctor.setAccessible(false);
        AccessibleObject.setAccessible(ctors, false);

    }

}
