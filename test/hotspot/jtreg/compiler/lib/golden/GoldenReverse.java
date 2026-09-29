/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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

package compiler.lib.golden;

public class GoldenReverse {
  // The code is copied from java.lang.Integer.reverse(int i)
  public static int golden_reverse_integer(int i) {
    // HD, Figure 7-1
    i = (i & 0x55555555) << 1 | (i >>> 1) & 0x55555555;
    i = (i & 0x33333333) << 2 | (i >>> 2) & 0x33333333;
    i = (i & 0x0f0f0f0f) << 4 | (i >>> 4) & 0x0f0f0f0f;

    return golden_reverseBytes_integer(i);
  }

  // The code is copied from java.lang.Integer.reverseBytes(int i)
  static int golden_reverseBytes_integer(int i) {
    return (i << 24)            |
           ((i & 0xff00) << 8)  |
           ((i >>> 8) & 0xff00) |
           (i >>> 24);
  }

  // The code is copied from java.lang.Long.reverse(long i)
  public static long golden_reverse_long(long i) {
    // HD, Figure 7-1
    i = (i & 0x5555555555555555L) << 1 | (i >>> 1) & 0x5555555555555555L;
    i = (i & 0x3333333333333333L) << 2 | (i >>> 2) & 0x3333333333333333L;
    i = (i & 0x0f0f0f0f0f0f0f0fL) << 4 | (i >>> 4) & 0x0f0f0f0f0f0f0f0fL;

    return golden_reverseBytes_long(i);
  }

  // The code is copied from java.lang.Long.reverseBytes(long i)
  static long golden_reverseBytes_long(long i) {
    i = (i & 0x00ff00ff00ff00ffL) << 8 | (i >>> 8) & 0x00ff00ff00ff00ffL;
    return (i << 48) | ((i & 0xffff0000L) << 16) |
        ((i >>> 16) & 0xffff0000L) | (i >>> 48);
  }
}
