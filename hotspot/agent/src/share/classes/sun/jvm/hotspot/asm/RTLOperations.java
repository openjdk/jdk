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

public interface RTLOperations {

   // arithmetic operations
   public static final int RTLOP_ADD          = 0;
   // with carry
   public static final int RTLOP_ADDC         = 1;
   public static final int RTLOP_SUB          = 2;
   // with carry
   public static final int RTLOP_SUBC         = 3;
   public static final int RTLOP_SMUL         = 4;
   public static final int RTLOP_UMUL         = 5;
   public static final int RTLOP_SDIV         = 6;
   public static final int RTLOP_UDIV         = 7;

   public static final int RTLOP_MAX_ARITHMETIC = RTLOP_UDIV;

   // logical operations
   public static final int RTLOP_AND          = 8;
   public static final int RTLOP_OR           = 9;
   public static final int RTLOP_NOT          = 10;
   public static final int RTLOP_NAND         = 11;
   public static final int RTLOP_NOR          = 12;
   public static final int RTLOP_XOR          = 13;
   public static final int RTLOP_XNOR         = 14;

   public static final int RTLOP_MAX_LOGICAL  = RTLOP_XNOR;

   // shift operations
   public static final int RTLOP_SRL          = 15;
   public static final int RTLOP_SRA          = 16;
   public static final int RTLOP_SLL          = 17;

   public static final int RTLOP_MAX_SHIFT    = RTLOP_SLL;

   public static final int RTLOP_UNKNOWN      = Integer.MAX_VALUE;
}
