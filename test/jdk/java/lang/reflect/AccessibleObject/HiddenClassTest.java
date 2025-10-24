/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Test java.lang.reflect.AccessibleObject with modules
 * @run junit/othervm --enable-final-field-mutation=ALL-UNNAMED -DwriteAccess=true HiddenClassTest
 * @run junit/othervm --illegal-final-field-mutation=deny -DwriteAccess=false HiddenClassTest
 */

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class HiddenClassTest {
    static Class<?> hiddenClass;
    static boolean writeAccess;

    @BeforeAll
    static void setup() throws Exception {
        String classes = System.getProperty("test.classes");
        Path cf = Path.of(classes, "Fields.class");
        byte[] bytes = Files.readAllBytes(cf);
        hiddenClass = MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();

        String s = System.getProperty("writeAccess");
        assertNotNull(s);
        writeAccess = Boolean.valueOf(s);
    }

    /*
     * Test Field::set that can write the value of a non-static final field
     * in a normal class
     */
    @Test
    void testFieldsInNormalClass() throws Throwable {
        // despite the name "HiddenClass", this class is loaded by the
        // class loader as non-hidden class
        Class<?> c = Fields.class;
        Fields o = new Fields();
        assertFalse(c.isHidden());
        readOnlyAccessibleObject(c, "STATIC_FINAL", null, true);
        readWriteAccessibleObject(c, "STATIC_NON_FINAL", null, false);
        if (writeAccess) {
            readWriteAccessibleObject(c, "FINAL", o, true);
        } else {
            readOnlyAccessibleObject(c, "FINAL", o, true);
        }
        readWriteAccessibleObject(c, "NON_FINAL", o, false);
    }

    /*
     * Test Field::set that fails to write the value of a non-static final field
     * in a hidden class
     */
    @Test
    void testFieldsInHiddenClass() throws Throwable {
        assertTrue(hiddenClass.isHidden());
        Object o = hiddenClass.newInstance();
        readOnlyAccessibleObject(hiddenClass, "STATIC_FINAL", null, true);
        readWriteAccessibleObject(hiddenClass, "STATIC_NON_FINAL", null, false);
        readOnlyAccessibleObject(hiddenClass, "FINAL", o, true);
        readWriteAccessibleObject(hiddenClass, "NON_FINAL", o, false);
    }

    private static void readOnlyAccessibleObject(Class<?> c, String name, Object o, boolean isFinal) throws Exception {
        Field f = c.getDeclaredField(name);
        int modifier = f.getModifiers();
        if (isFinal) {
            assertTrue(Modifier.isFinal(modifier));
        } else {
            assertFalse(Modifier.isFinal(modifier));
        }
        assertTrue(f.trySetAccessible());
        assertTrue(f.get(o) != null);
        assertThrows(IllegalAccessException.class, () -> f.set(o, null));
    }

    private static void readWriteAccessibleObject(Class<?> c, String name, Object o, boolean isFinal) throws Exception {
        Field f = c.getDeclaredField(name);
        int modifier = f.getModifiers();
        if (isFinal) {
            assertTrue(Modifier.isFinal(modifier));
        } else {
            assertFalse(Modifier.isFinal(modifier));
        }
        assertTrue(f.trySetAccessible());
        assertTrue(f.get(o) != null);
        f.set(o, null);
    }
}
