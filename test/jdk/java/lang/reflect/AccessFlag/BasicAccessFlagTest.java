/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266670
 * @summary Basic tests of AccessFlag
 */

import java.lang.reflect.AccessFlag;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class BasicAccessFlagTest {
    public static void main(String... args) throws Exception {
        testSourceModifiers();
        testMaskOrdering();
    }

    private static void testSourceModifiers() throws Exception {
        Class<?> modifierClass = Modifier.class;

        for(AccessFlag accessFlag : AccessFlag.values()) {
            if (accessFlag.sourceModifier()) {
                // Check for consistency
                Field f = modifierClass.getField(accessFlag.name());
                if (accessFlag.mask() != f.getInt(null) ) {
                    throw new RuntimeException("Unexpected mask for " +
                                               accessFlag);
                }
            }
        }
    }

    // The mask values of the enum constants must be non-decreasing;
    // in other words stay the same (for colliding mask values) or go
    // up.
    private static void testMaskOrdering() {
        AccessFlag[] values = AccessFlag.values();
        for (int i = 1; i < values.length; i++) {
            AccessFlag left  = values[i-1];
            AccessFlag right = values[i];
            if (left.mask() > right.mask()) {
                throw new RuntimeException(left
                                           + "has a greater mask than "
                                           + right);
            }
        }
    }
}
