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
 * @summary Test that IsIdentityClass and modifiers return true for arrays that can be flattened.
 * @library /test/lib
 * @enablePreview false
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.value
 * @run junit/othervm IsIdentityClassTest
 * @run junit/othervm --enable-preview IsIdentityClassTest
 */

import org.junit.jupiter.api.Test;

import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.util.Set;

import jdk.internal.misc.PreviewFeatures;

import static jdk.test.lib.Asserts.*;

public class IsIdentityClassTest {

    @Test
    void testIsIdentityClass() {
        assertEquals(!PreviewFeatures.isEnabled(), Integer.class.isIdentity(), "Integer is not an IDENTITY type");
        assertTrue(Integer[].class.isIdentity(), "Arrays of inline types are IDENTITY types");
    }

    @Test
    void testModifiers() {
        // Without --enable-preview (before Valhalla), there was no IDENTITY modifier.
        // With --enable-preview, Integer still should not have the IDENTITY modifier.
        // So only verify this in preview mode.
        if (PreviewFeatures.isEnabled()) {
            int imod = Integer.class.getModifiers();
            assertFalse((imod & ClassFile.ACC_IDENTITY) != 0,
                    "Modifier of Integer " + Integer.toHexString(imod) + " should not have ACC_IDENTITY set");
        }
        int amod = Integer[].class.getModifiers();
        assertEquals(PreviewFeatures.isEnabled(), (amod & ClassFile.ACC_IDENTITY) != 0,
                "Modifier of array " + Integer.toHexString(amod) + " should have ACC_IDENTITY set");
    }

    @Test
    void testAccessFlags() {
        // Without --enable-preview (before Valhalla), there was no IDENTITY accessflag.
        // With --enable-preview, Integer still should not have the IDENTITY accessflag.
        // So only verify this in preview mode.
        if (PreviewFeatures.isEnabled()) {
            Set<AccessFlag> iacc = Integer.class.accessFlags();
            assertFalse(iacc.contains(AccessFlag.IDENTITY), "Access flags should not contain IDENTITY");
        }
        // AccessFlags for arrays set the IDENTITY accessflag.
        Set<AccessFlag> aacc = Integer[].class.accessFlags();
        assertEquals(PreviewFeatures.isEnabled(), aacc.contains(AccessFlag.IDENTITY), "Access flags of array of inline types should contain IDENTITY");
    }
}
