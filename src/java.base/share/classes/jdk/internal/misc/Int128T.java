/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.misc;

import jdk.internal.vm.annotation.IntrinsicCandidate;

/**
 * This class serves as API points to verify the correctness of the
 * implementation of corresponding C2 nodes.
 */
public class Int128T {

    @IntrinsicCandidate
    public static long addLo(long lo1, long hi1, long lo2, long hi2) {
        return lo1 + lo2;
    }

    @IntrinsicCandidate
    public static long addHi(long lo1, long hi1, long lo2, long hi2) {
        long sumLo = lo1 + lo2;
        boolean loOverflow = Long.compareUnsigned(sumLo, lo1) < 0;
        return hi1 + hi2 + (loOverflow ? 1 : 0);
    }

    @IntrinsicCandidate
    public static long subLo(long lo1, long hi1, long lo2, long hi2) {
        return lo1 - lo2;
    }

    @IntrinsicCandidate
    public static long subHi(long lo1, long hi1, long lo2, long hi2) {
        boolean loOverflow = Long.compareUnsigned(lo1, lo2) < 0;
        return hi1 - hi2 - (loOverflow ? 1 : 0);
    }
}
