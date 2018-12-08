/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.replacements.aarch64;

import org.graalvm.compiler.api.replacements.ClassSubstitution;
import org.graalvm.compiler.api.replacements.MethodSubstitution;

/**
 * Aarch64 ISA offers a count leading zeros instruction which can be used to implement
 * numberOfLeadingZeros more efficiently than using BitScanReverse.
 */
@ClassSubstitution(Long.class)
public class AArch64LongSubstitutions {

    @MethodSubstitution
    public static int bitCount(long value) {
        // Based on Warren, Hacker's Delight, slightly adapted to profit from Aarch64 add + shift
        // instruction.
        // Assuming the peephole optimizer optimizes all x - y >>> z into a single instruction
        // this takes 11 instructions.
        long x = value;
        x = x - ((x & 0xaaaaaaaaaaaaaaaaL) >>> 1);
        x = (x & 0x3333333333333333L) + ((x & 0xccccccccccccccccL) >>> 2);
        x = (x + (x >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
        x = x + (x >>> 8);
        x = x + (x >>> 16);
        x = x + (x >>> 32);
        return (int) x & 0x7f;
    }

}
