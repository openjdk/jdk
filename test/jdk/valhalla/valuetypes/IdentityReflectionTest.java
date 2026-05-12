/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that isValue and modifiers return correct results for value classes & arrays
 * @library /test/lib
 * @enablePreview false
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 * @run junit/othervm IdentityReflectionTest
 * @run junit/othervm --enable-preview IdentityReflectionTest
 */

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.Set;

import jdk.internal.misc.PreviewFeatures;

import static jdk.test.lib.Asserts.*;

public class IdentityReflectionTest {

    static final boolean PREVIEW = PreviewFeatures.isEnabled();

    @Test
    void testIsValue() {
        checkIsValue(Integer.class, PREVIEW);
        checkIsValue(Number.class, PREVIEW);
        checkIsValue(Thread.class, false);
        checkIsValue(Object.class, false);
        checkIsValue(Runnable.class, false);
        checkIsValue(int.class, false);
        checkIsValue(Integer[].class, false);
        checkIsValue(Thread[].class, false);
    }

    void checkIsValue(Class<?> c, boolean expected) {
        assertEquals(expected, c.isValue(),
                      c + " " + (expected ? "is" : "is not") + " a value class");
    }

    @Test
    void testModifiers() {
        checkIdentityModifier(Integer.class, false);
        checkIdentityModifier(Number.class, false);
        checkIdentityModifier(Thread.class, PREVIEW);
        checkIdentityModifier(Object.class, PREVIEW);
        checkIdentityModifier(Runnable.class, false);
        checkIdentityModifier(int.class, false);
        checkIdentityModifier(Integer[].class, PREVIEW);
        checkIdentityModifier(Thread[].class, PREVIEW);
    }

    void checkIdentityModifier(Class<?> c, boolean expected) {
        int mod = c.getModifiers();
        assertEquals(expected, (mod & ClassFile.ACC_IDENTITY) != 0,
            "Modifier of " + c + " (" + Integer.toHexString(mod) + ") " +
            (expected ? "should" : "should not") + " have ACC_IDENTITY set");
    }

    @Test
    void testAccessFlags() {
        checkIdentityAccessFlag(Integer.class, false);
        checkIdentityAccessFlag(Number.class, false);
        checkIdentityAccessFlag(Thread.class, PREVIEW);
        checkIdentityAccessFlag(Object.class, PREVIEW);
        checkIdentityAccessFlag(Runnable.class, false);
        checkIdentityAccessFlag(int.class, false);
        checkIdentityAccessFlag(Integer[].class, PREVIEW);
        checkIdentityAccessFlag(Thread[].class, PREVIEW);
    }

    void checkIdentityAccessFlag(Class<?> c, boolean expected) {
        Set<AccessFlag> acc = c.accessFlags();
        assertEquals(expected, acc.contains(AccessFlag.IDENTITY),
            "Access flags of " + c + " (" + acc + ") " +
            (expected ? "should" : "should not") + " contain IDENTITY");
    }
}
