/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @modules jdk.incubator.foreign/jdk.internal.foreign
 *
 * @run testng TestLayoutEquality
 */

import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.ValueLayout;
import jdk.internal.foreign.PlatformLayouts;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static jdk.incubator.foreign.ValueLayout.ADDRESS;
import static jdk.incubator.foreign.ValueLayout.JAVA_BOOLEAN;
import static jdk.incubator.foreign.ValueLayout.JAVA_BYTE;
import static jdk.incubator.foreign.ValueLayout.JAVA_CHAR;
import static jdk.incubator.foreign.ValueLayout.JAVA_DOUBLE;
import static jdk.incubator.foreign.ValueLayout.JAVA_FLOAT;
import static jdk.incubator.foreign.ValueLayout.JAVA_INT;
import static jdk.incubator.foreign.ValueLayout.JAVA_LONG;
import static jdk.incubator.foreign.ValueLayout.JAVA_SHORT;
import static org.testng.Assert.*;

public class TestLayoutEquality {

    @Test(dataProvider = "layoutConstants")
    public void testReconstructedEquality(ValueLayout layout) {
        ValueLayout newLayout = valueLayoutForCarrier(layout.carrier());
        newLayout = newLayout.withBitAlignment(layout.bitAlignment());
        newLayout = newLayout.withOrder(layout.order());

        // properties should be equal
        assertEquals(newLayout.bitSize(), layout.bitSize());
        assertEquals(newLayout.bitAlignment(), layout.bitAlignment());
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

        return testValues.stream().map(e -> new Object[]{ e }).toArray(Object[][]::new);
    }

    private static void addLayoutConstants(List<ValueLayout> testValues, Class<?> cls) throws ReflectiveOperationException {
        for (Field f : cls.getFields()) {
            if (f.getName().startsWith("C_"))
                testValues.add((ValueLayout) f.get(null));
        }
    }

    static ValueLayout valueLayoutForCarrier(Class<?> carrier) {
        if (carrier == boolean.class) {
            return JAVA_BOOLEAN;
        } else if (carrier == char.class) {
            return JAVA_CHAR;
        } else if (carrier == byte.class) {
            return JAVA_BYTE;
        } else if (carrier == short.class) {
            return JAVA_SHORT;
        } else if (carrier == int.class) {
            return JAVA_INT;
        } else if (carrier == long.class) {
            return JAVA_LONG;
        } else if (carrier == float.class) {
            return JAVA_FLOAT;
        } else if (carrier == double.class) {
            return JAVA_DOUBLE;
        } else if (carrier == MemoryAddress.class) {
            return ADDRESS;
        } else {
            throw new UnsupportedOperationException();
        }
    }
}
