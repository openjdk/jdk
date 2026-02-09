/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @build TrySetAccessibleTest
 * @modules java.base/java.lang:open
 *          java.base/jdk.internal.module
 *          java.base/jdk.internal.perf
 *          java.base/jdk.internal.misc:+open
 * @run junit/othervm TrySetAccessibleTest
 * @summary Test AccessibleObject::trySetAccessible method
 */

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import jdk.internal.misc.Unsafe;
import jdk.internal.module.ModulePath;
import jdk.internal.perf.Perf;
import java.security.ProtectionDomain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class TrySetAccessibleTest {
    /**
     * Invoke a private constructor on a public class in an exported package
     */
    @Test
    public void testPrivateConstructorInExportedPackage() throws Exception {
        Constructor<?> ctor = Perf.class.getDeclaredConstructor();

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance());

        assertFalse(ctor.trySetAccessible());
        assertFalse(ctor.canAccess(null));
    }

    /**
     * Invoke a private constructor on a public class in an open package
     */
    @Test
    public void testPrivateConstructorInOpenedPackage() throws Exception {
        Constructor<?> ctor = Unsafe.class.getDeclaredConstructor();

        assertThrows(IllegalAccessException.class, () -> ctor.newInstance());

        assertTrue(ctor.trySetAccessible());
        assertTrue(ctor.canAccess(null));
        Unsafe unsafe = (Unsafe) ctor.newInstance();
    }

    /**
     * Invoke a private method on a public class in an exported package
     */
    @Test
    public void testPrivateMethodInExportedPackage() throws Exception {
        Method m = ModulePath.class.getDeclaredMethod("packageName", String.class);
        assertThrows(IllegalAccessException.class, () -> m.invoke(null));

        assertFalse(m.trySetAccessible());
        assertFalse(m.canAccess(null));
    }


    /**
     * Invoke a private method on a public class in an open package
     */
    @Test
    public void testPrivateMethodInOpenedPackage() throws Exception {
        Method m = Unsafe.class.getDeclaredMethod("throwIllegalAccessError");
        assertFalse(m.canAccess(null));

        assertThrows(IllegalAccessException.class, () -> m.invoke(null));

        assertTrue(m.trySetAccessible());
        assertTrue(m.canAccess(null));

        InvocationTargetException e = assertThrows(InvocationTargetException.class, () ->
                m.invoke(null));
        assertInstanceOf(IllegalAccessError.class, e.getCause());
    }

    /**
     * Invoke a private method on a public class in an exported package
     */
    @Test
    public void testPrivateFieldInExportedPackage() throws Exception {
        Field f = Perf.class.getDeclaredField("instance");
        assertThrows(IllegalAccessException.class, () -> f.get(null));

        assertFalse(f.trySetAccessible());
        assertFalse(f.canAccess(null));
        assertThrows(IllegalAccessException.class, () -> f.get(null));
    }

    /**
     * Access a private field in a public class that is an exported package
     */
    @Test
    public void testPrivateFieldInOpenedPackage() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");

        assertThrows(IllegalAccessException.class, () -> f.get(null));

        assertTrue(f.trySetAccessible());
        assertTrue(f.canAccess(null));
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

        assertFalse(ctor.trySetAccessible());
        assertFalse(ctor.canAccess(null));
        assertFalse(ctor.trySetAccessible());
        assertFalse(ctor.isAccessible()); // should match trySetAccessible
    }


    /**
     * Access a public field in a public class that in a non-exported package
     */
    @Test
    public void testPublicFieldInNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Field f = clazz.getField("SERIALNUMBER_OID");

        assertThrows(IllegalAccessException.class, () -> f.get(null));

        assertFalse(f.trySetAccessible());
        assertFalse(f.canAccess(null));
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

        assertFalse(ctor.trySetAccessible());
        assertFalse(ctor.canAccess(null));
    }

}
