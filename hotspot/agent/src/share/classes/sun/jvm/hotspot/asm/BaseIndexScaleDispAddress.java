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

// address is calculated as (base + (index * scale) + displacement)
// optionally index is auto incremented or decremented

public abstract class BaseIndexScaleDispAddress extends IndirectAddress {
   private final Register base, index;
   private final int      scale;
   private final long     disp;
   private boolean  isAutoIncr;
   private boolean  isAutoDecr;

   public BaseIndexScaleDispAddress(Register base, Register index, long disp, int scale) {
      this.base = base;
      this.index = index;
      this.disp = disp;
      this.scale = scale;
   }

   public BaseIndexScaleDispAddress(Register base, Register index, long disp) {
      this(base, index, disp, 1);
   }

   public BaseIndexScaleDispAddress(Register base, Register index) {
      this(base, index, 0L, 1);
   }

   public BaseIndexScaleDispAddress(Register base, long disp) {
      this(base, null, disp, 1);
   }

   public Register getBase() {
      return base;
   }

   public Register getIndex() {
      return index;
   }

   public int      getScale() {
      return scale;
   }

   public long     getDisplacement() {
      return disp;
   }

   // is the index auto decremented or incremented?
   public boolean  isAutoIncrement() {
      return isAutoIncr;
   }

   public void setAutoIncrement(boolean value) {
      isAutoIncr = value;
   }

   public boolean  isAutoDecrement() {
      return isAutoDecr;
   }

   public void setAutoDecrement(boolean value) {
      isAutoDecr = value;
   }
}
