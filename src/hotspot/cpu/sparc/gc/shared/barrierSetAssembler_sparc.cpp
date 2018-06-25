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
#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/barrierSetAssembler.hpp"
#include "interpreter/interp_masm.hpp"
#include "runtime/jniHandles.hpp"

#define __ masm->

void BarrierSetAssembler::store_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                   Register val, Address dst, Register tmp) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (in_heap) {
      if (dst.has_disp() && !Assembler::is_simm13(dst.disp())) {
        assert(!dst.has_index(), "not supported yet");
        __ set(dst.disp(), tmp);
        dst = Address(dst.base(), tmp);
      }
      if (UseCompressedOops) {
        assert(dst.base() != val, "not enough registers");
        if (is_not_null) {
          __ encode_heap_oop_not_null(val);
        } else {
          __ encode_heap_oop(val);
        }
        __ st(val, dst);
      } else {
        __ st_ptr(val, dst);
      }
    } else {
      assert(in_native, "why else?");
      __ st_ptr(val, dst);
    }
    break;
  }
  default: Unimplemented();
  }
}

void BarrierSetAssembler::load_at(MacroAssembler* masm, DecoratorSet decorators, BasicType type,
                                  Address src, Register dst, Register tmp) {
  bool in_heap = (decorators & IN_HEAP) != 0;
  bool in_native = (decorators & IN_NATIVE) != 0;
  bool is_not_null = (decorators & IS_NOT_NULL) != 0;

  switch (type) {
  case T_ARRAY:
  case T_OBJECT: {
    if (in_heap) {
      if (src.has_disp() && !Assembler::is_simm13(src.disp())) {
        assert(!src.has_index(), "not supported yet");
        __ set(src.disp(), tmp);
        src = Address(src.base(), tmp);
      }
      if (UseCompressedOops) {
        __ lduw(src, dst);
        if (is_not_null) {
          __ decode_heap_oop_not_null(dst);
        } else {
          __ decode_heap_oop(dst);
        }
      } else {
        __ ld_ptr(src, dst);
      }
    } else {
      assert(in_native, "why else?");
      __ ld_ptr(src, dst);
    }
    break;
  }
  default: Unimplemented();
  }
}

void BarrierSetAssembler::try_resolve_jobject_in_native(MacroAssembler* masm, Register jni_env,
                                                        Register obj, Register tmp, Label& slowpath) {
  __ andn(obj, JNIHandles::weak_tag_mask, obj);
  __ ld_ptr(obj, 0, obj);
}
