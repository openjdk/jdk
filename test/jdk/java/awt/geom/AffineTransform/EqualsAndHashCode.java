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
 * @summary Tests that equals(Object) is consistent with hashCode(),
 *          in particular regarding negative versus positive zeros and
 *          NaN values.
 */

import java.awt.geom.AffineTransform;

public class EqualsAndHashCode {
    private static boolean failed;

    public static void main(String arg[]) {
        checkReflexiveEquals();
        checkZeros();
        checkNotEqual();
        if (failed) {
            throw new RuntimeException("Some tests failed.");
        }
    }

    private static void checkReflexiveEquals() {
        AffineTransform t = new AffineTransform(1, 0, 0, 1, Double.NaN, 0);
        if (!t.equals(t)) {
            System.err.println("Transform should be equal to itself.");
            failed = true;
        }
        if (!t.equals(t.clone())) {
            System.err.println("Transform should be equal to its clone.");
            failed = true;
        }
    }

    private static void checkZeros() {
        AffineTransform positive = new AffineTransform(2, 0, 0, 3, 0, +0.0);
        AffineTransform negative = new AffineTransform(2, 0, 0, 3, 0, -0.0);
        if (!positive.equals(negative)) {
            System.err.println("Transforms should be equal despite the sign difference in zero values.");
            failed = true;
        } else if (positive.hashCode() != negative.hashCode()) {
            System.err.println("Equal transforms should have the same hash code value.");
            failed = true;
        }
    }

    private static void checkNotEqual() {
        AffineTransform t1 = new AffineTransform(2, 0, 0, 3, 2, 0);
        AffineTransform t2 = new AffineTransform(2, 0, 0, 3, 2, 4);
        if (t1.equals(t2)) {
            System.err.println("Expected non-equal transforms.");
            failed = true;
        }
        if (t1.hashCode() == t2.hashCode()) {
            System.err.println("Expected different hash codes.");
            failed = true;
        }
    }
}
