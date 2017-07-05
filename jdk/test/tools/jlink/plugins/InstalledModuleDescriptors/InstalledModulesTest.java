/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @modules java.base/jdk.internal.misc
 * @run testng InstalledModulesTest
 * @summary Verify the properties of ModuleDescriptor created
 *          by InstalledModules
 */

public class InstalledModulesTest {
    private static final JavaLangModuleAccess jlma = SharedSecrets.getJavaLangModuleAccess();

    /**
     * Verify ModuleDescriptor contains unmodifiable sets
     */
    @Test
    public void testUnmodifableDescriptors() throws Exception {
        ModuleFinder.ofSystem().findAll()
                    .stream()
                    .map(ModuleReference::descriptor)
                    .forEach(this::testModuleDescriptor);
    }

    private void testModuleDescriptor(ModuleDescriptor md) {
        assertUnmodifiable(md.conceals(), "conceal");
        assertUnmodifiable(md.packages(), "package");
        assertUnmodifiable(md.requires(),
                           jlma.newRequires(EnumSet.allOf(Modifier.class), "require"));
        assertUnmodifiable(md.exports(), jlma.newExports("export"));
        assertUnmodifiable(md.uses(), "use");
        assertUnmodifiable(md.provides(), "provide",
                           jlma.newProvides("provide", Collections.singleton("provide")));

    }

    private <T> void assertUnmodifiable(Set<T> set, T dummy) {
        try {
            set.add(dummy);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // pass
        } catch (Exception e) {
            fail("Should throw UnsupportedOperationException");
        }
    }

    private <T, V> void assertUnmodifiable(Map<T, V> set, T dummyKey, V dummyValue) {
        try {
            set.put(dummyKey, dummyValue);
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // pass
        } catch (Exception e) {
            fail("Should throw UnsupportedOperationException");
        }
    }

}
