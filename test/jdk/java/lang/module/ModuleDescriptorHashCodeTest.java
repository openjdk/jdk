/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotSame;

/**
 * @test
 * @bug 8275509
 * @run testng ModuleDescriptorHashCodeTest
 * @run testng/othervm -Xshare:off ModuleDescriptorHashCodeTest
 * @summary Tests the ModuleDescriptor.hashCode() for boot layer modules
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
}
