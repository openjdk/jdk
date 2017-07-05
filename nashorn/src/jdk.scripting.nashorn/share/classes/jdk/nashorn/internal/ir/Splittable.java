/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.nashorn.internal.ir;

import java.io.Serializable;
import java.util.List;
import jdk.nashorn.internal.codegen.CompileUnit;

/**
 * An interface for splittable expressions.
 */
public interface Splittable {

    /**
     * Get a list of split ranges for this splittable expression, or null
     * if the expression should not be split.
     *
     * @return a list of split ranges
     */
    List<SplitRange> getSplitRanges();

    /**
     * A SplitRange is a range in a splittable expression. It defines the
     * boundaries of the split range and provides a compile unit for code generation.
     */
    final class SplitRange implements CompileUnitHolder, Serializable {
        private static final long serialVersionUID = 1L;

        /** Compile unit associated with the postsets range. */
        private final CompileUnit compileUnit;

        /** postsets range associated with the unit (hi not inclusive). */
        private final int low, high;

        /**
         * Constructor
         * @param compileUnit compile unit
         * @param low lowest array index in unit
         * @param high highest array index in unit + 1
         */
        public SplitRange(final CompileUnit compileUnit, final int low, final int high) {
            this.compileUnit = compileUnit;
            this.low   = low;
            this.high   = high;
        }

        /**
         * Get the high index position of the ArrayUnit (exclusive)
         * @return high index position
         */
        public int getHigh() {
            return high;
        }

        /**
         * Get the low index position of the ArrayUnit (inclusive)
         * @return low index position
         */
        public int getLow() {
            return low;
        }

        /**
         * The array compile unit
         * @return array compile unit
         */
        @Override
        public CompileUnit getCompileUnit() {
            return compileUnit;
        }
    }
}
