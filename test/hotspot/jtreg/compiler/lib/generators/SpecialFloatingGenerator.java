/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.generators;

/**
 * Provides a double distribution picked from a list of special values, including NaN, zero, int, etc.
 */
public final class SpecialFloatingGenerator<T> implements Generator<T> {
    private final T[] values;

    /*
     * We also mix in other values at a certain percentage.
     */
    private final Generator<T> backgroundGenerator;

    /*
     * {@link specialCountDown} detemines in how many iterations we generate the next special value.
     */
    private final int specialMinDistance;
    private final int specialMaxDistance;
    private int specialCountDown;

    /**
     * Creates a new {@link SpecialFloatingGenerator}. It periodically generates a special value (NaN, zero, infinity, etc).
     * The distance between two special values is chosen randomly between {@code specialMinDistance} and
     * {@code specialMaxDistance}. All other values in between are chosen from a {@code backgroundGenerator}.
     *
     * @param backgroundGenerator Provides the random values between the special values.
     * @param specialMinDistance Minimum distance between special values.
     * @param specialMaxDistance Maximum distance between special values.
     */
    public SpecialFloatingGenerator(Generator<T> backgroundGenerator, T[] values, int specialMinDistance, int specialMaxDistance) {
        this.values = values;
        this.backgroundGenerator = backgroundGenerator;
        this.specialMinDistance = specialMinDistance;
        this.specialMaxDistance = specialMaxDistance;
        this.specialCountDown = Generators.RANDOM.nextInt(specialMaxDistance);
    }

    @Override
    public T next() {
        specialCountDown--;
        if (specialCountDown <= 0) {
            specialCountDown = Generators.RANDOM.nextInt(specialMinDistance, specialMaxDistance);
            int r = Generators.RANDOM.nextInt(values.length);
            return values[r];
        } else {
            return backgroundGenerator.next();
        }
    }
}
