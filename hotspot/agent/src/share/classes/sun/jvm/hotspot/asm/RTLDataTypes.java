/*
 * Copyright (c) 2002, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm;

public interface RTLDataTypes {

   // HALF = 16 bits, WORD = 32 bits, DWORD = 64 bits and QWORD = 128 bits.

   public static final int RTLDT_SIGNED_BYTE    = 0;
   public static final int RTLDT_UNSIGNED_BYTE  = 1;
   public static final int RTLDT_SIGNED_HALF    = 2;
   public static final int RTLDT_UNSIGNED_HALF  = 3;
   public static final int RTLDT_SIGNED_WORD    = 4;
   public static final int RTLDT_UNSIGNED_WORD  = 5;
   public static final int RTLDT_SIGNED_DWORD   = 6;
   public static final int RTLDT_UNSIGNED_DWORD = 7;
   public static final int RTLDT_SIGNED_QWORD   = 8;
   public static final int RTLDT_UNSIGNED_QWORD = 9;

   // float is 4 bytes, double is 8 bytes, extended double is 10 bytes
   // and quad is 16 bytes.

   public static final int RTLDT_FL_SINGLE     = 10;
   public static final int RTLDT_FL_DOUBLE     = 11;
   public static final int RTLDT_FL_EXT_DOUBLE = 12;
   public static final int RTLDT_FL_QUAD       = 13;

   public static final int RTLDT_STRING        = 14;

   public static final int RTLDT_UNKNOWN       = Integer.MAX_VALUE;
}
