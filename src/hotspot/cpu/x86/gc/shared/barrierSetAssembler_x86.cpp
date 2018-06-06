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
#include "interpreter/interp_masm.hpp"
#include "runtime/jniHandles.hpp"

#define __ masm->

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Register dst, Address src, Register tmp1, Register tmp_thread) {
  bool on_heap = (decorators & IN_HEAP) != 0;
  bool on_root = (decorators & IN_ROOT) != 0;
  bool oop_not_null = (decorators & OOP_NOT_NULL) != 0;
  bool atomic = (decorators & MO_RELAXED) != 0;

  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (on_heap) {
#ifdef _LP64
      if (UseCompressedOops) {
        __ movl(dst, src);
        if (oop_not_null) {
          __ decode_heap_oop_not_null(dst);
        } else {
          __ decode_heap_oop(dst);
        }
      } else
#endif
      {
        __ movptr(dst, src);
      }
    } else {
      assert(on_root, "why else?");
      __ movptr(dst, src);
    }
    break;
  }
  case T_BOOLEAN: __ load_unsigned_byte(dst, src);  break;
  case T_BYTE:    __ load_signed_byte(dst, src);    break;
  case T_CHAR:    __ load_unsigned_short(dst, src); break;
  case T_SHORT:   __ load_signed_short(dst, src);   break;
  case T_INT:     __ movl  (dst, src);              break;
  case T_ADDRESS: __ movptr(dst, src);              break;
  case T_FLOAT:
    assert(dst == noreg, "only to ftos");
    __ load_float(src);
    break;
  case T_DOUBLE:
    assert(dst == noreg, "only to dtos");
    __ load_double(src);
    break;
  case T_LONG:
    assert(dst == noreg, "only to ltos");
#ifdef _LP64
    __ movq(rax, src);
#else
    if (atomic) {
      __ fild_d(src);               // Must load atomically
      __ subptr(rsp,2*wordSize);    // Make space for store
      __ fistp_d(Address(rsp,0));
      __ pop(rax);
      __ pop(rdx);
    } else {
      __ movl(rax, src);
      __ movl(rdx, src.plus_disp(wordSize));
    }
#endif
    break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Address dst, Register val, Register tmp1, Register tmp2) {
  bool on_heap = (decorators & IN_HEAP) != 0;
  bool on_root = (decorators & IN_ROOT) != 0;
  bool oop_not_null = (decorators & OOP_NOT_NULL) != 0;
  bool atomic = (decorators & MO_RELAXED) != 0;

  switch (type) {
  case T_OBJECT:
  case T_ARRAY: {
    if (on_heap) {
      if (val == noreg) {
        assert(!oop_not_null, "inconsistent access");
#ifdef _LP64
        if (UseCompressedOops) {
          __ movl(dst, (int32_t)NULL_WORD);
        } else {
          __ movslq(dst, (int32_t)NULL_WORD);
        }
#else
        __ movl(dst, (int32_t)NULL_WORD);
#endif
      } else {
#ifdef _LP64
        if (UseCompressedOops) {
          assert(!dst.uses(val), "not enough registers");
          if (oop_not_null) {
            __ encode_heap_oop_not_null(val);
          } else {
            __ encode_heap_oop(val);
          }
          __ movl(dst, val);
        } else
#endif
        {
          __ movptr(dst, val);
        }
      }
    } else {
      assert(on_root, "why else?");
      assert(val != noreg, "not supported");
      __ movptr(dst, val);
    }
    break;
  }
  case T_BOOLEAN:
    __ andl(val, 0x1);  // boolean is true if LSB is 1
    __ movb(dst, val);
    break;
  case T_BYTE:
    __ movb(dst, val);
    break;
  case T_SHORT:
    __ movw(dst, val);
    break;
  case T_CHAR:
    __ movw(dst, val);
    break;
  case T_INT:
    __ movl(dst, val);
    break;
  case T_LONG:
    assert(val == noreg, "only tos");
#ifdef _LP64
    __ movq(dst, rax);
#else
    if (atomic) {
      __ push(rdx);
      __ push(rax);                 // Must update atomically with FIST
      __ fild_d(Address(rsp,0));    // So load into FPU register
      __ fistp_d(dst);              // and put into memory atomically
      __ addptr(rsp, 2*wordSize);
    } else {
      __ movptr(dst, rax);
      __ movptr(dst.plus_disp(wordSize), rdx);
    }
#endif
    break;
  case T_FLOAT:
    assert(val == noreg, "only tos");
    __ store_float(dst);
    break;
  case T_DOUBLE:
    assert(val == noreg, "only tos");
    __ store_double(dst);
    break;
  case T_ADDRESS:
    __ movptr(dst, val);
    break;
  default: Unimplemented();
  }
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ clear_jweak_tag(obj);
  __ movptr(obj, Address(obj, 0));
}
