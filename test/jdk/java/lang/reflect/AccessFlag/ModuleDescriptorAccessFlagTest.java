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

/*
 * @test
 * @bug 8266670
 * @summary Test expected AccessFlag's on module-related structures.
 */

import java.lang.reflect.AccessFlag;
import java.lang.module.*;
import java.util.Set;

public class ModuleDescriptorAccessFlagTest {
    public static void main(String... args) {
        // Test ModuleDescriptor.Modifier
        var openMod = ModuleDescriptor.newModule("foo",
                                                 Set.of(ModuleDescriptor.Modifier.OPEN,
                                                        ModuleDescriptor.Modifier.SYNTHETIC,
                                                        ModuleDescriptor.Modifier.MANDATED)).build();
        checkAccessFlags(openMod, openMod.accessFlags(), "[OPEN, SYNTHETIC, MANDATED]");
        // AUTOMATIC does not have a corresponding access flag so is
        // *not* tested here.

        // Test ModuleDescriptor.Requires.Modifier
        var requireMod = ModuleDescriptor.newModule("bar")
            .requires(Set.of(ModuleDescriptor.Requires.Modifier.STATIC,
                             ModuleDescriptor.Requires.Modifier.SYNTHETIC,
                             ModuleDescriptor.Requires.Modifier.TRANSITIVE), "baz")
            .build();

        for (ModuleDescriptor.Requires requires : requireMod.requires()) {
            if ("java.base".equals(requires.name())) {
                checkAccessFlags(requires, requires.accessFlags(), "[MANDATED]");
            } else {
                // Note "STATIC_PHASE" rather than "STATIC"
                checkAccessFlags(requires, requires.accessFlags(), "[TRANSITIVE, STATIC_PHASE, SYNTHETIC]");
            }
        }

        // Test ModuleDescriptor.Exports.Modifier
        var exportMod = ModuleDescriptor.newModule("baz")
            .exports(Set.of(ModuleDescriptor.Exports.Modifier.MANDATED,
                            ModuleDescriptor.Exports.Modifier.SYNTHETIC), "quux")
            .build();
        for (ModuleDescriptor.Exports exports : exportMod.exports()) {
            checkAccessFlags(exports, exports.accessFlags(), "[SYNTHETIC, MANDATED]");
        }

        // Test ModuleDescriptor.Opens.Modifier
        var opensMod = ModuleDescriptor.newModule("quux")
            .exports(Set.of(ModuleDescriptor.Exports.Modifier.MANDATED,
                            ModuleDescriptor.Exports.Modifier.SYNTHETIC), "xxyzzy")
            .build();
        for (ModuleDescriptor.Opens opens : exportMod.opens()) {
            checkAccessFlags(opens, opens.accessFlags(), "[SYNTHETIC, MANDATED]");
        }
    }

    private static void checkAccessFlags(Object o, Set<AccessFlag> accessFlags, String expected) {
        String actual = accessFlags.toString();
        if (!expected.equals(actual)) {
            throw new RuntimeException("On " + o.toString() +
                                       " expected " + expected +
                                       " got " + actual);
        }
    }
}
