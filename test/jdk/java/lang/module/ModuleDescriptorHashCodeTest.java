/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.util.Set;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

/**
 * @test
 * @bug 8275509 8290041
 * @summary Tests the ModuleDescriptor.hashCode()
 * @run testng ModuleDescriptorHashCodeTest
 * @run testng/othervm -Xshare:off ModuleDescriptorHashCodeTest
 */
public class ModuleDescriptorHashCodeTest {

    /**
     * Verifies that the ModuleDescriptor.hashCode() returned by a boot layer module is
     * the same as that returned by a ModuleDescriptor constructed from the ModuleDescriptor.Builder
     * for the same module.
     */
    @Test
    public void testBootModuleDescriptor() throws Exception {
        Set<Module> bootModules = ModuleLayer.boot().modules();
        for (Module bootModule : bootModules) {
            System.out.println("Testing module descriptor of boot module " + bootModule);
            ModuleDescriptor bootMD = bootModule.getDescriptor();
            ModuleDescriptor mdFromBuilder = fromModuleInfoClass(bootModule);
            // verify that this object is indeed a different object instance than the boot module descriptor
            // to prevent any artificial passing of the test
            assertNotSame(mdFromBuilder, bootMD, "ModuleDescriptor loaded from boot layer and " +
                    "one created from module-info.class unexpectedly returned the same instance");
            assertEquals(mdFromBuilder.hashCode(), bootMD.hashCode(),
                    "Unexpected ModuleDescriptor.hashCode() for " + mdFromBuilder);
            assertEquals(mdFromBuilder.compareTo(bootMD), 0,
                    "Unexpected ModuleDescriptor.compareTo() for " + mdFromBuilder);
        }
    }

    /**
     * Verifies that two "equal" module descriptors which only differ in the order of
     * {@link ModuleDescriptor.Opens.Modifier opens modifiers}, that were used to construct the
     * descriptors, have the same hashcode.
     */
    @Test
    public void testOpensModifiersOrdering() throws Exception {
        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Opens.Modifier> mods1 = Set.of(
                ModuleDescriptor.Opens.Modifier.SYNTHETIC, ModuleDescriptor.Opens.Modifier.MANDATED);
        final ModuleDescriptor desc1 = createModuleDescriptor(mods1, null, null);

        // create the same module descriptor again and this time just change the order of the
        // "opens" modifiers' Set.

        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Opens.Modifier> mods2 = Set.of(
                ModuleDescriptor.Opens.Modifier.MANDATED, ModuleDescriptor.Opens.Modifier.SYNTHETIC);
        final ModuleDescriptor desc2 = createModuleDescriptor(mods2, null, null);

        // basic verification of the modifiers themselves before we check the module descriptors
        assertEquals(mods1, mods2, "Modifiers were expected to be equal");

        // now verify the module descriptors
        assertEquals(desc1, desc2, "Module descriptors were expected to be equal");
        assertEquals(desc1.compareTo(desc2), 0, "compareTo was expected to return" +
                " 0 for module descriptors that are equal");
        System.out.println(desc1 + " hashcode = " + desc1.hashCode());
        System.out.println(desc2 + " hashcode = " + desc2.hashCode());
        assertEquals(desc1.hashCode(), desc2.hashCode(), "Module descriptor hashcodes" +
                " were expected to be equal");
    }

    /**
     * Verifies that two "equal" module descriptors which only differ in the order of
     * {@link ModuleDescriptor.Exports.Modifier exports modifiers}, that were used to construct the
     * descriptors, have the same hashcode.
     */
    @Test
    public void testExportsModifiersOrdering() throws Exception {
        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Exports.Modifier> mods1 = Set.of(
                ModuleDescriptor.Exports.Modifier.SYNTHETIC, ModuleDescriptor.Exports.Modifier.MANDATED);
        final ModuleDescriptor desc1 = createModuleDescriptor(null, null, mods1);

        // create the same module descriptor again and this time just change the order of the
        // "exports" modifiers' Set.

        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Exports.Modifier> mods2 = Set.of(
                ModuleDescriptor.Exports.Modifier.MANDATED, ModuleDescriptor.Exports.Modifier.SYNTHETIC);
        final ModuleDescriptor desc2 = createModuleDescriptor(null, null, mods2);

        // basic verification of the modifiers themselves before we check the module descriptors
        assertEquals(mods1, mods2, "Modifiers were expected to be equal");

        // now verify the module descriptors
        assertEquals(desc1, desc2, "Module descriptors were expected to be equal");
        assertEquals(desc1.compareTo(desc2), 0, "compareTo was expected to return" +
                " 0 for module descriptors that are equal");
        System.out.println(desc1 + " hashcode = " + desc1.hashCode());
        System.out.println(desc2 + " hashcode = " + desc2.hashCode());
        assertEquals(desc1.hashCode(), desc2.hashCode(), "Module descriptor hashcodes" +
                " were expected to be equal");
    }

    /**
     * Verifies that two "equal" module descriptors which only differ in the order of
     * {@link ModuleDescriptor.Requires.Modifier requires modifiers}, that were used to construct the
     * descriptors, have the same hashcode.
     */
    @Test
    public void testRequiresModifiersOrdering() throws Exception {
        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Requires.Modifier> mods1 = Set.of(
                ModuleDescriptor.Requires.Modifier.SYNTHETIC, ModuleDescriptor.Requires.Modifier.MANDATED);
        final ModuleDescriptor desc1 = createModuleDescriptor(null, mods1, null);

        // create the same module descriptor again and this time just change the order of the
        // "exports" modifiers' Set.

        // important to use Set.of() (i.e. backed by immutable set) to reproduce the issue
        final Set<ModuleDescriptor.Requires.Modifier> mods2 = Set.of(
                ModuleDescriptor.Requires.Modifier.MANDATED, ModuleDescriptor.Requires.Modifier.SYNTHETIC);
        final ModuleDescriptor desc2 = createModuleDescriptor(null, mods2, null);

        // basic verification of the modifiers themselves before we check the module descriptors
        assertEquals(mods1, mods2, "Modifiers were expected to be equal");

        // now verify the module descriptors
        assertEquals(desc1, desc2, "Module descriptors were expected to be equal");
        assertEquals(desc1.compareTo(desc2), 0, "compareTo was expected to return" +
                " 0 for module descriptors that are equal");
        System.out.println(desc1 + " hashcode = " + desc1.hashCode());
        System.out.println(desc2 + " hashcode = " + desc2.hashCode());
        assertEquals(desc1.hashCode(), desc2.hashCode(), "Module descriptor hashcodes" +
                " were expected to be equal");
    }

    // Returns a ModuleDescriptor parsed out of the module-info.class of the passed Module
    private static ModuleDescriptor fromModuleInfoClass(Module module) throws IOException {
        try (InputStream moduleInfo = module.getResourceAsStream("module-info.class")) {
            if (moduleInfo == null) {
                throw new RuntimeException("Could not locate module-info.class in " + module);
            }
            // internally calls ModuleDescriptor.Builder
            return ModuleDescriptor.read(moduleInfo);
        }
    }

    // creates a module descriptor with passed (optional) opens/exports/requires modifiers
    private static ModuleDescriptor createModuleDescriptor(
            Set<ModuleDescriptor.Opens.Modifier> opensModifiers,
            Set<ModuleDescriptor.Requires.Modifier> reqsModifiers,
            Set<ModuleDescriptor.Exports.Modifier> expsModifiers) {

        final var mdb = ModuleDescriptor.newModule("foobar");
        if (opensModifiers != null) {
            mdb.opens(opensModifiers, "a.p1", Set.of("a.m1"));
        }
        if (reqsModifiers != null) {
            mdb.requires(reqsModifiers, "a.m2");
        }
        if (expsModifiers != null) {
            mdb.exports(expsModifiers, "a.b.c", Set.of("a.m3"));
        }
        return mdb.build();
    }
}
