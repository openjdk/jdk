/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

//
// Help test archived box cache consistency.
//
// args[0]: the expected maximum value expected to be archived
//
public class CheckIntegerCacheApp {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new RuntimeException(
                    "FAILED. Incorrect argument length: " + args.length);
        }

        // Base JLS compliance check
        for (int i = -128; i <= 127; i++) {
            if (Integer.valueOf(i) != Integer.valueOf(i)) {
                throw new RuntimeException(
                        "FAILED. All values in range [-128, 127] should be interned in cache: " + i);
            }
            if (Byte.valueOf((byte)i) != Byte.valueOf((byte)i)) {
                throw new RuntimeException(
                        "FAILED. All Byte values in range [-128, 127] should be interned in cache: " + (byte)i);
            }
            if (Short.valueOf((short)i) != Short.valueOf((short)i)) {
                throw new RuntimeException(
                        "FAILED. All Short values in range [-128, 127] should be interned in cache: " + (byte)i);
            }
            if (Long.valueOf(i) != Long.valueOf(i)) {
                throw new RuntimeException(
                        "FAILED. All Long values in range [-128, 127] should be interned in cache: " + i);
            }

            // Character cache only values 0 through 127
            if (i >= 0) {
                if (Character.valueOf((char)i) != Character.valueOf((char)i)) {
                    throw new RuntimeException(
                            "FAILED. All Character values in range [0, 127] should be interned in cache: " + i);
                }
            }
        }

        // Check that archived integer cache agrees with runtime integer cache.
        for (int i = -128; i <= 127; i++) {
            if (ArchivedIntegerHolder.archivedObjects[i + 128] != Integer.valueOf(i)) {
                throw new RuntimeException(
                        "FAILED. Archived and runtime caches disagree for " + i);
            }
        }

        int high = Integer.parseInt(args[0]);
        if (Integer.valueOf(high) != Integer.valueOf(high)) {
            throw new RuntimeException(
                    "FAILED. Value expected to be retrieved from cache: " + high);
        }

        if (Integer.valueOf(high + 1) == Integer.valueOf(high + 1)) {
            throw new RuntimeException(
                    "FAILED. Value not expected to be retrieved from cache: " + high);
        }
    }
}
