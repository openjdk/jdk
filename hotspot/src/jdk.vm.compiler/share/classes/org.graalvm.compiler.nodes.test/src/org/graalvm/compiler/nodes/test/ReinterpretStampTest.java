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
package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.nodes.calc.ReinterpretNode;

/**
 * Unit tests for the {@link ReinterpretNode#inferStamp} method.
 */
public class ReinterpretStampTest {

    protected static final long[] interestingLongs = {
                    // @formatter:off
                    Long.MIN_VALUE,                                        // -0.0d
                    Long.MIN_VALUE + 1,                                    // largest negative number
                    Double.doubleToLongBits(-42.0),                        // random negative number
                    Double.doubleToLongBits(Double.NEGATIVE_INFINITY) - 1, // smallest negative number
                    Double.doubleToLongBits(Double.NEGATIVE_INFINITY),     // -Inf
                    Double.doubleToLongBits(Double.NEGATIVE_INFINITY) + 1, // smallest negative NaN
                    -42,                                                   // random negative NaN
                    -1,                                                    // largest negative NaN
                    0,                                                     // 0.0d
                    1,                                                     // smallest positive number
                    Double.doubleToLongBits(42.0),                         // random positive number
                    Double.doubleToLongBits(Double.POSITIVE_INFINITY) - 1, // largest positive number
                    Double.doubleToLongBits(Double.POSITIVE_INFINITY),     // +Inf
                    Double.doubleToLongBits(Double.POSITIVE_INFINITY) + 1, // smallest positive NaN
                    Long.MAX_VALUE - 42,                                   // random positive NaN
                    Long.MAX_VALUE,                                        // largest positive NaN
                    // @formatter:on
    };

    protected static final int[] interestingInts = {
                    // @formatter:off
                    Integer.MIN_VALUE,                                 // -0.0f
                    Integer.MIN_VALUE + 1,                             // largest negative number
                    Float.floatToIntBits(-42.0f),                      // random negative number
                    Float.floatToIntBits(Float.NEGATIVE_INFINITY) - 1, // smallest negative number
                    Float.floatToIntBits(Float.NEGATIVE_INFINITY),     // -Inf
                    Float.floatToIntBits(Float.NEGATIVE_INFINITY) + 1, // smallest negative NaN
                    -42,                                               // random negative NaN
                    -1,                                                // largest negative NaN
                    0,                                                 // 0.0f
                    1,                                                 // smallest positive number
                    Float.floatToIntBits(42.0f),                       // random positive number
                    Float.floatToIntBits(Float.POSITIVE_INFINITY) - 1, // largest positive number
                    Float.floatToIntBits(Float.POSITIVE_INFINITY),     // +Inf
                    Float.floatToIntBits(Float.POSITIVE_INFINITY) + 1, // smallest positive NaN
                    Integer.MAX_VALUE - 42,                            // random positive NaN
                    Integer.MAX_VALUE,                                 // largest positive NaN
                    // @formatter:on
    };
}
