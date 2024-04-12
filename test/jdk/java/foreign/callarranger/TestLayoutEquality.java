/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/*
 * @test
 * @compile platform/PlatformLayouts.java
 * @modules java.base/jdk.internal.foreign.abi
 * @modules java.base/jdk.internal.foreign.layout
 * @run testng TestLayoutEquality
 */

import java.lang.foreign.AddressLayout;
import java.lang.foreign.ValueLayout;

import jdk.internal.foreign.layout.ValueLayouts;
import platform.PlatformLayouts;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.testng.Assert.*;

public class TestLayoutEquality {

    @Test(dataProvider = "layoutConstants")
    public void testReconstructedEquality(ValueLayout layout) {
        ValueLayout newLayout = ValueLayouts.valueLayout(layout.carrier(), layout.order());
        newLayout = newLayout.withByteAlignment(layout.byteAlignment());
        if (layout instanceof AddressLayout addressLayout && addressLayout.targetLayout().isPresent()) {
            newLayout = ((AddressLayout)newLayout).withTargetLayout(addressLayout.targetLayout().get());
        }

        // properties should be equal
        assertEquals(newLayout.byteSize(), layout.byteSize());
        assertEquals(newLayout.byteAlignment(), layout.byteAlignment());
        assertEquals(newLayout.name(), layout.name());

        // layouts should be equals
        assertEquals(newLayout, layout);
    }

    @DataProvider
    public static Object[][] layoutConstants() throws ReflectiveOperationException {
        List<ValueLayout> testValues = new ArrayList<>();

        addLayoutConstants(testValues, PlatformLayouts.SysV.class);
        addLayoutConstants(testValues, PlatformLayouts.Win64.class);
        addLayoutConstants(testValues, PlatformLayouts.AArch64.class);
        addLayoutConstants(testValues, PlatformLayouts.RISCV64.class);

        return testValues.stream().map(e -> new Object[]{ e }).toArray(Object[][]::new);
    }

    private static void addLayoutConstants(List<ValueLayout> testValues, Class<?> cls) throws ReflectiveOperationException {
        for (Field f : cls.getFields()) {
            if (f.getName().startsWith("C_"))
                testValues.add((ValueLayout) f.get(null));
        }
    }
}
