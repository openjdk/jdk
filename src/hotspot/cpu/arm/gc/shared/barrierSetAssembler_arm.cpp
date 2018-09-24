/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "gc/shared/barrierSetAssembler.hpp"

#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1, Register tmp2, Register tmp3) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
#ifdef AARCH64
      if (UseCompressedOops) {
        __ ldr_w(dst, src);
        __ decode_heap_oop(dst);
      } else
#endif // AARCH64
      {
        __ ldr(dst, src);
      }
    } else {
      assert(in_native, "why else?");
      __ ldr(dst, src);
    }
    break;
  }
  case T_BOOLEAN: __ ldrb      (dst, src); break;
  case T_BYTE:    __ ldrsb     (dst, src); break;
  case T_CHAR:    __ ldrh      (dst, src); break;
  case T_SHORT:   __ ldrsh     (dst, src); break;
  case T_INT:     __ ldr_s32   (dst, src); break;
  case T_ADDRESS: __ ldr       (dst, src); break;
  case T_LONG:
#ifdef AARCH64
    __ ldr                     (dst, src); break;
#else
    assert(dst == noreg, "only to ltos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldmia                   (src.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
#endif // AARCH64
    break;
#ifdef __SOFTFP__
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ ldr                     (R0_tos, src);
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldmia                   (src.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#else
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ add(src.index(), src.index(), src.base());
    __ ldr_float               (S0_tos, src.index());
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ add                     (src.index(), src.index(), src.base());
    __ ldr_double              (D0_tos, src.index());
    break;
#endif
  default: Unimplemented();
  }

}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address obj, Register val, Register tmp1, Register tmp2, Register tmp3, bool is_null) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (in_heap) {
#ifdef AARCH64
      if (UseCompressedOops) {
        assert(!dst.uses(src), "not enough registers");
        if (!is_null) {
          __ encode_heap_oop(src);
        }
        __ str_w(val, obj);
      } else
#endif // AARCH64
      {
      __ str(val, obj);
      }
    } else {
      assert(in_native, "why else?");
      __ str(val, obj);
    }
    break;
  }
  case T_BOOLEAN:
    __ and_32(val, val, 1);
    __ strb(val, obj);
    break;
  case T_BYTE:    __ strb      (val, obj); break;
  case T_CHAR:    __ strh      (val, obj); break;
  case T_SHORT:   __ strh      (val, obj); break;
  case T_INT:     __ str       (val, obj); break;
  case T_ADDRESS: __ str       (val, obj); break;
  case T_LONG:
#ifdef AARCH64
    __ str                     (val, obj); break;
#else // AARCH64
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ stmia                   (obj.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
#endif // AARCH64
    break;
#ifdef __SOFTFP__
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ str (R0_tos,  obj);
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ stmia                   (obj.index(), RegisterSet(R0_tos_lo) | RegisterSet(R1_tos_hi));
    break;
#else
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ str_float               (S0_tos,  obj.index());
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ add                     (obj.index(), obj.index(), obj.base());
    __ str_double              (D0_tos,  obj.index());
    break;
#endif
  default: Unimplemented();
  }
}

void BarrierSetAssembler::obj_equals(MacroAssembler* masm,
                                     Register obj1, Register obj2) {
  __ cmp(obj1, obj2);
}
