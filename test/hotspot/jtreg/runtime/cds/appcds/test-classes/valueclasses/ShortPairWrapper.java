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

import jdk.internal.vm.annotation.NullRestricted;

public value class ShortPairWrapper implements Comparable<ShortPairWrapper> {
    static {
        ValueClassHelper.clinit_called_for_ShortPairWrapper = true;
    }
    @NullRestricted
    ShortPair sp;

    public String toString() {
        return "ShortPair: " + sp.toString();
    }

    public int compareTo(ShortPairWrapper other) {
        return sp.compareTo(other.sp);
    }

    public ShortPairWrapper(int s0, int s1) {
        sp = new ShortPair((short)s0, (short)s1);
        super();
    }
}
