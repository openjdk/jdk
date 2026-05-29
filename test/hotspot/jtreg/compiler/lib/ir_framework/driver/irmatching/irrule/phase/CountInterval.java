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

package compiler.lib.ir_framework.driver.irmatching.irrule.phase;

class CountInterval {
    private int lo;
    private int hi;

    CountInterval() {
        this.lo = 0;
        this.hi = Integer.MAX_VALUE;
    }

    void narrow(String comparator, int value) {
        switch (comparator) {
            case "="  -> { lo = Math.max(lo, value);
                           hi = Math.min(hi, value); }
            case "<"  -> hi = Math.min(hi, value - 1);
            case "<=" -> hi = Math.min(hi, value);
            case ">"  -> lo = Math.max(lo, value + 1);
            case ">=" -> lo = Math.max(lo, value);
            // Unknown comparators will be caught during constraint parsing.
        }
    }

    boolean isEmpty() {
        return lo > hi;
    }

    int lo() {
        return lo;
    }

    int hi() {
        return hi;
    }
}
