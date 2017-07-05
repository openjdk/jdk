/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.asm.x86;

import sun.jvm.hotspot.asm.Address;
import sun.jvm.hotspot.asm.BaseIndexScaleDispAddress;

public class X86RegisterIndirectAddress extends BaseIndexScaleDispAddress {

   final private X86SegmentRegister segReg;

   public X86RegisterIndirectAddress(X86SegmentRegister segReg, X86Register base, X86Register index, long disp, int scale) {
      super(base, index, disp, scale);
      this.segReg = segReg;
   }

   public X86RegisterIndirectAddress(X86SegmentRegister segReg, X86Register base, X86Register index, long disp) {
      super(base, index, disp, -1);
      this.segReg = segReg;
   }

   public String toString() {
      StringBuffer buf = new StringBuffer();
      if(segReg != null) {
         buf.append(segReg.toString());
         buf.append(":");
      }

      long disp = getDisplacement();
      if(disp != 0)
          buf.append(disp);

      sun.jvm.hotspot.asm.Register base = getBase();
      sun.jvm.hotspot.asm.Register index = getIndex();
      int scaleVal = getScale();
      scaleVal = 1 << scaleVal;

      if( (base != null) || (index != null) || (scaleVal > 1) )
         buf.append('[');

      if(base != null) {
         buf.append(base.toString());
         if(index != null) {
            buf.append("+");
            buf.append(index.toString());
         }
      }
      else {
         if(index != null) {
            buf.append(index.toString());
         }
      }

      if (scaleVal > 1) {
         buf.append(" * ");
         buf.append(Integer.toString(scaleVal));
      }

      if( (base != null) || (index != null) || (scaleVal > 1) )
         buf.append(']');

      return buf.toString();
   }
}
