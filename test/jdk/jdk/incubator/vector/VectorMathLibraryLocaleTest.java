/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8382267
 * @summary VectorMathLibrary symbol names must not use locale-sensitive formatting
 * @modules jdk.incubator.vector
 * @run main/othervm -ea -Duser.language=ar -Duser.country=SA VectorMathLibraryLocaleTest
 */

import java.util.Locale;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

public class VectorMathLibraryLocaleTest {
    public static void main(String[] args) {
        assert !String.format("%d", 16).equals("16") : "expected non-ASCII digits for locale " + Locale.getDefault();

        VectorSpecies<Float> species = FloatVector.SPECIES_PREFERRED;
        FloatVector v = FloatVector.broadcast(species, 1.0f);

        // Without the fix, this throws InternalError due to locale-mangled symbol name.
        // The assertion below is just a sanity check on the result.
        FloatVector result = v.lanewise(VectorOperators.EXP);
        float expected = (float) Math.E;
        for (int i = 0; i < species.length(); i++) {
            assert result.lane(i) == expected : "lane " + i + ": expected " + expected + ", got " + result.lane(i);
        }
    }
}
