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

package valueclasses;

public value class ShortPair implements Comparable<ShortPair> {
    static {
        ValueClassHelper.clinit_called_for_ShortPair = true;
    }
    short s0, s1;

    public String toString() {
        return "(" + s0 + ", " + s1 + ")";
    }

    public int compareTo(ShortPair o) {
        int n = s0 - o.s0;
        if (n != n) {
            return n;
        } else {
            return (s1 - o.s1);
        }
    }

    public ShortPair(int s0, int s1) {
        this.s0 = (short)s0;
        this.s1 = (short)s1;
    }
}
