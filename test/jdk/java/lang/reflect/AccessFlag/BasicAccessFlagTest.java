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
 * @bug 8266670 8293626 8297271
 * @summary Basic tests of AccessFlag
 * @run junit BasicAccessFlagTest
 */

import java.lang.classfile.ClassFile;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import static org.junit.jupiter.api.Assertions.*;

public class BasicAccessFlagTest {

    /*
     * Verify sourceModifier() == true access flags have a
     * corresponding constant in java.lang.reflect.Modifier.
     */
    @Test
    public void testSourceModifiers() throws Exception {
        Class<?> modifierClass = Modifier.class;

        for(AccessFlag accessFlag : AccessFlag.values()) {
            if (accessFlag.sourceModifier()) {
                // Check for consistency
                Field f = modifierClass.getField(accessFlag.name());
                assertEquals(f.getInt(null), accessFlag.mask(), accessFlag + " mask");
            }
        }
    }

    // The mask values of the enum constants must be non-decreasing;
    // in other words stay the same (for colliding mask values) or go
    // up.
    @Test
    public void testMaskOrdering() {
        AccessFlag[] values = AccessFlag.values();
        for (int i = 1; i < values.length; i++) {
            AccessFlag left  = values[i-1];
            AccessFlag right = values[i];
            assertTrue(left.mask() <= right.mask(), () -> left
                    + "has a greater mask than "
                    + right);
        }
    }

    // Test that if access flags have a matching mask, their locations
    // are disjoint.
    @Test
    public void testDisjoint() {
        // First build the mask -> access flags map...
        Map<Integer, Set<AccessFlag>> maskToFlags = new LinkedHashMap<>();

        for (var accessFlag : AccessFlag.values()) {
            Integer mask = accessFlag.mask();
            Set<AccessFlag> flags = maskToFlags.get(mask);

            if (flags == null) {
                flags = new HashSet<>();
                flags.add(accessFlag);
                maskToFlags.put(mask, flags);
            } else {
                flags.add(accessFlag);
            }
        }

        // ...then test for disjointness
        for (var entry : maskToFlags.entrySet()) {
            var value = entry.getValue();
            if (value.size() == 0) {
                throw new AssertionError("Bad flag set " + entry);
            } else if (value.size() == 1) {
                // Need at least two flags to be non-disjointness to
                // be possible
                continue;
            }

            Set<AccessFlag.Location> locations = new HashSet<>();
            for (var accessFlag : value) {
                for (var location : accessFlag.locations()) {
                    boolean added = locations.add(location);
                    if (!added) {
                        reportError(location, accessFlag,
                                    entry.getKey(), value);
                    }
                }
            }
        }
    }

    private static void reportError(AccessFlag.Location location,
                                    AccessFlag accessFlag,
                                    Integer mask, Set<AccessFlag> value) {
        System.err.println("Location " + location +
                           " from " + accessFlag +
                           " already present for 0x" +
                           Integer.toHexString(mask) + ": " + value);
        throw new RuntimeException();
    }

    // For each access flag, make sure it is recognized on every kind
    // of location it can apply to
    @Test
    public void testMaskToAccessFlagsPositive() {
        for (var accessFlag : AccessFlag.values()) {
            Set<AccessFlag> expectedSet = EnumSet.of(accessFlag);
            for (var location : accessFlag.locations()) {
                Set<AccessFlag> computedSet =
                    AccessFlag.maskToAccessFlags(accessFlag.mask(), location);
                if (!expectedSet.equals(computedSet)) {
                    throw new RuntimeException("Bad set computation on " +
                                               accessFlag + ", " + location);
                }
            }
            for (var cffv : ClassFileFormatVersion.values()) {
                for (var location : accessFlag.locations(cffv)) {
                    Set<AccessFlag> computedSet =
                            AccessFlag.maskToAccessFlags(accessFlag.mask(), location, cffv);
                    if (!expectedSet.equals(computedSet)) {
                        throw new RuntimeException("Bad set computation on " +
                                accessFlag + ", " + location);
                    }
                }
            }
        }
        assertEquals(Set.of(AccessFlag.STRICT), AccessFlag.maskToAccessFlags(Modifier.STRICT, AccessFlag.Location.METHOD, ClassFileFormatVersion.RELEASE_8));
    }

    @Test
    public void testMaskToAccessFlagsNegative() {
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(Modifier.STRICT, AccessFlag.Location.METHOD));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(Modifier.STRICT, AccessFlag.Location.METHOD, ClassFileFormatVersion.RELEASE_17));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(Modifier.STRICT, AccessFlag.Location.METHOD, ClassFileFormatVersion.RELEASE_1));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(Modifier.PRIVATE, AccessFlag.Location.CLASS));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_MODULE, AccessFlag.Location.CLASS, ClassFileFormatVersion.RELEASE_8));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_ANNOTATION, AccessFlag.Location.CLASS, ClassFileFormatVersion.RELEASE_4));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_ENUM, AccessFlag.Location.FIELD, ClassFileFormatVersion.RELEASE_4));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_SYNTHETIC, AccessFlag.Location.INNER_CLASS, ClassFileFormatVersion.RELEASE_4));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_PUBLIC, AccessFlag.Location.INNER_CLASS, ClassFileFormatVersion.RELEASE_0));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_MANDATED, AccessFlag.Location.METHOD_PARAMETER, ClassFileFormatVersion.RELEASE_7));
        assertThrows(IllegalArgumentException.class, () -> AccessFlag.maskToAccessFlags(ClassFile.ACC_MANDATED, AccessFlag.Location.MODULE, ClassFileFormatVersion.RELEASE_7));
    }

    @Test
    public void testLocationsNullHandling() {
        for (var flag : AccessFlag.values()) {
            assertThrows(NullPointerException.class, () -> flag.locations(null));
        }

        for (var location : AccessFlag.Location.values()) {
            assertThrows(NullPointerException.class, () -> location.flags(null));
        }

        for (var location : AccessFlag.Location.values()) {
            try {
                location.flags(null);
                throw new RuntimeException("Did not get NPE on " + location + ".flags(null)");
            } catch (NullPointerException npe ) {
                ; // Expected
            }
            try {
                location.flagsMask(null);
                throw new RuntimeException("Did not get NPE on " + location + ".flagsMask(null)");
            } catch (NullPointerException npe ) {
                ; // Expected
            }
        }
    }
}
