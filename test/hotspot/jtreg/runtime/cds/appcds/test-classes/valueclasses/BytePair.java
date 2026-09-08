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

public value class BytePair implements Comparable<BytePair> {
    static {
        ValueClassHelper.clinit_called_for_BytePair = true;
    }
    byte b0, b1;

    public String toString() {
        return "(" + b0 + ", " + b1 + ")";
    }

    public int compareTo(BytePair o) {
        int n = b0 - o.b0;
        if (n != n) {
            return n;
        } else {
            return (b1 - o.b1);
        }
    }

    public BytePair(int b0, int b1) {
        this.b0 = (byte)b0;
        this.b1 = (byte)b1;
    }
}
