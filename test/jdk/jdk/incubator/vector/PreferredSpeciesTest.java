/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

import jdk.incubator.vector.*;
import jdk.internal.vm.vector.VectorSupport;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test
 * @modules jdk.incubator.vector java.base/jdk.internal.vm.vector
 * @run testng PreferredSpeciesTest
 */

public class PreferredSpeciesTest {
    @DataProvider
    public static Object[][] classesProvider() {
        return new Object[][]{
                {byte.class},
                {short.class},
                {int.class},
                {float.class},
                {long.class},
                {double.class},
        };
    }

    @Test(dataProvider = "classesProvider")
    void testVectorLength(Class<?> c) {
        VectorSpecies<?> species = null;
        if (c == byte.class) {
            species = ByteVector.SPECIES_PREFERRED;
        } else if (c == short.class) {
            species = ShortVector.SPECIES_PREFERRED;
        } else if (c == int.class) {
            species = IntVector.SPECIES_PREFERRED;
        } else if (c == long.class) {
            species = LongVector.SPECIES_PREFERRED;
        } else if (c == float.class) {
            species = FloatVector.SPECIES_PREFERRED;
        } else if (c == double.class) {
            species = DoubleVector.SPECIES_PREFERRED;
        } else {
            throw new IllegalArgumentException("Bad vector element type: " + c.getName());
        }
        VectorShape shape = VectorShape.preferredShape();

        System.out.println("class = "+c+"; preferred shape"+shape+"; preferred species = "+species+"; maxSize="+VectorSupport.getMaxLaneCount(c));
        Assert.assertEquals(species.vectorShape(), shape);
        Assert.assertEquals(species.length(), Math.min(species.length(), VectorSupport.getMaxLaneCount(c)));
    }
}
