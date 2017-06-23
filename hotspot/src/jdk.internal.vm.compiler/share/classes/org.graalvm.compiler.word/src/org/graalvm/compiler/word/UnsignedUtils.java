/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.word;

/**
 * Utility methods on Unsigned values.
 */
public final class UnsignedUtils {

    private UnsignedUtils() {
        // This is a class of static methods, so no need for any instances.
    }

    /**
     * Round an Unsigned down to the nearest smaller multiple.
     *
     * @param that The Unsigned to be rounded down.
     * @param multiple The multiple to which that Unsigned should be decreased.
     * @return That Unsigned, but rounded down.
     */
    public static Unsigned roundDown(Unsigned that, Unsigned multiple) {
        return that.unsignedDivide(multiple).multiply(multiple);
    }

    /**
     * Round an Unsigned up to the nearest larger multiple.
     *
     * @param that The Unsigned to be rounded up.
     * @param multiple The multiple to which that Unsigned should be increased.
     * @return That Unsigned, but rounded up.
     */
    public static Unsigned roundUp(Unsigned that, Unsigned multiple) {
        return UnsignedUtils.roundDown(that.add(multiple.subtract(1)), multiple);
    }

    /**
     * Check that an Unsigned is an even multiple.
     *
     * @param that The Unsigned to be verified as a multiple.
     * @param multiple The multiple against which the Unsigned should be verified.
     * @return true if that Unsigned is a multiple, false otherwise.
     */
    public static boolean isAMultiple(Unsigned that, Unsigned multiple) {
        return that.equal(UnsignedUtils.roundDown(that, multiple));
    }

    /**
     * The minimum of two Unsigneds.
     *
     * @param x An Unsigned.
     * @param y Another Unsigned.
     * @return The whichever Unsigned is smaller.
     */
    public static Unsigned min(Unsigned x, Unsigned y) {
        return (x.belowOrEqual(y)) ? x : y;
    }

    /**
     * The maximum of two Unsigneds.
     *
     * @param x An Unsigned.
     * @param y Another Unsigned.
     * @return The whichever Unsigned is larger.
     */
    public static Unsigned max(Unsigned x, Unsigned y) {
        return (x.aboveOrEqual(y)) ? x : y;
    }
}
