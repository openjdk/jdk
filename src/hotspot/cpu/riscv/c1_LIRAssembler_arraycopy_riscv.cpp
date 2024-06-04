/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "asm/assembler.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "ci/ciArrayKlass.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/stubRoutines.hpp"

#define __ _masm->


void LIR_Assembler::generic_arraycopy(Register src, Register src_pos, Register length,
                                      Register dst, Register dst_pos, CodeStub *stub) {
  assert(src == x11 && src_pos == x12, "mismatch in calling convention");
  // Save the arguments in case the generic arraycopy fails and we
  // have to fall back to the JNI stub
  arraycopy_store_args(src, src_pos, length, dst, dst_pos);

  address copyfunc_addr = StubRoutines::generic_arraycopy();
  assert(copyfunc_addr != nullptr, "generic arraycopy stub required");

  // The arguments are in java calling convention so we shift them
  // to C convention
  assert_different_registers(c_rarg0, j_rarg1, j_rarg2, j_rarg3, j_rarg4);
  __ mv(c_rarg0, j_rarg0);
  assert_different_registers(c_rarg1, j_rarg2, j_rarg3, j_rarg4);
  __ mv(c_rarg1, j_rarg1);
  assert_different_registers(c_rarg2, j_rarg3, j_rarg4);
  __ mv(c_rarg2, j_rarg2);
  assert_different_registers(c_rarg3, j_rarg4);
  __ mv(c_rarg3, j_rarg3);
  __ mv(c_rarg4, j_rarg4);
#ifndef PRODUCT
  if (PrintC1Statistics) {
    __ incrementw(ExternalAddress((address)&Runtime1::_generic_arraycopystub_cnt));
  }
#endif
  __ far_call(RuntimeAddress(copyfunc_addr));
  __ beqz(x10, *stub->continuation());
  // Reload values from the stack so they are where the stub
  // expects them.
  arraycopy_load_args(src, src_pos, length, dst, dst_pos);

  // x10 is -1^K where K == partial copied count
  __ xori(t0, x10, -1);
  // adjust length down and src/end pos up by partial copied count
  __ subw(length, length, t0);
  __ addw(src_pos, src_pos, t0);
  __ addw(dst_pos, dst_pos, t0);
  __ j(*stub->entry());

  __ bind(*stub->continuation());
}

void LIR_Assembler::arraycopy_simple_check(Register src, Register src_pos, Register length,
                                           Register dst, Register dst_pos, Register tmp,
                                           CodeStub *stub, int flags) {
  // test for null
  if (flags & LIR_OpArrayCopy::src_null_check) {
    __ beqz(src, *stub->entry(), /* is_far */ true);
  }
  if (flags & LIR_OpArrayCopy::dst_null_check) {
    __ beqz(dst, *stub->entry(), /* is_far */ true);
  }

  // If the compiler was not able to prove that exact type of the source or the destination
  // of the arraycopy is an array type, check at runtime if the source or the destination is
  // an instance type.
  if (flags & LIR_OpArrayCopy::type_check) {
    assert(Klass::_lh_neutral_value == 0, "or replace bgez instructions");
    if (!(flags & LIR_OpArrayCopy::LIR_OpArrayCopy::dst_objarray)) {
      __ load_klass(tmp, dst);
      __ lw(t0, Address(tmp, in_bytes(Klass::layout_helper_offset())));
      __ bgez(t0, *stub->entry(), /* is_far */ true);
    }

    if (!(flags & LIR_OpArrayCopy::LIR_OpArrayCopy::src_objarray)) {
      __ load_klass(tmp, src);
      __ lw(t0, Address(tmp, in_bytes(Klass::layout_helper_offset())));
      __ bgez(t0, *stub->entry(), /* is_far */ true);
    }
  }

  // check if negative
  if (flags & LIR_OpArrayCopy::src_pos_positive_check) {
    __ bltz(src_pos, *stub->entry(), /* is_far */ true);
  }
  if (flags & LIR_OpArrayCopy::dst_pos_positive_check) {
    __ bltz(dst_pos, *stub->entry(), /* is_far */ true);
  }
  if (flags & LIR_OpArrayCopy::length_positive_check) {
    __ bltz(length, *stub->entry(), /* is_far */ true);
  }

  if (flags & LIR_OpArrayCopy::src_range_check) {
    __ addw(tmp, src_pos, length);
    __ lwu(t0, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ bgtu(tmp, t0, *stub->entry(), /* is_far */ true);
  }
  if (flags & LIR_OpArrayCopy::dst_range_check) {
    __ addw(tmp, dst_pos, length);
    __ lwu(t0, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ bgtu(tmp, t0, *stub->entry(), /* is_far */ true);
  }
}

void LIR_Assembler::arraycopy_checkcast(Register src, Register src_pos, Register length,
                                        Register dst, Register dst_pos, Register tmp,
                                        CodeStub *stub, BasicType basic_type,
                                        address copyfunc_addr, int flags) {
  // src is not a sub class of dst so we have to do a
  // per-element check.
  int mask = LIR_OpArrayCopy::src_objarray | LIR_OpArrayCopy::dst_objarray;
  if ((flags & mask) != mask) {
    // Check that at least both of them object arrays.
    assert(flags & mask, "one of the two should be known to be an object array");

    if (!(flags & LIR_OpArrayCopy::src_objarray)) {
      __ load_klass(tmp, src);
    } else if (!(flags & LIR_OpArrayCopy::dst_objarray)) {
      __ load_klass(tmp, dst);
    }
    int lh_offset = in_bytes(Klass::layout_helper_offset());
    Address klass_lh_addr(tmp, lh_offset);
    jint objArray_lh = Klass::array_layout_helper(T_OBJECT);
    __ lw(t0, klass_lh_addr);
    __ mv(t1, objArray_lh);
    __ bne(t0, t1, *stub->entry(), /* is_far */ true);
  }

  // Spill because stubs can use any register they like and it's
  // easier to restore just those that we care about.
  arraycopy_store_args(src, src_pos, length, dst, dst_pos);
  arraycopy_checkcast_prepare_params(src, src_pos, length, dst, dst_pos, basic_type);
  __ far_call(RuntimeAddress(copyfunc_addr));

#ifndef PRODUCT
  if (PrintC1Statistics) {
    Label failed;
    __ bnez(x10, failed);
    __ incrementw(ExternalAddress((address)&Runtime1::_arraycopy_checkcast_cnt));
    __ bind(failed);
  }
#endif

  __ beqz(x10, *stub->continuation());

#ifndef PRODUCT
  if (PrintC1Statistics) {
    __ incrementw(ExternalAddress((address)&Runtime1::_arraycopy_checkcast_attempt_cnt));
  }
#endif
  assert_different_registers(dst, dst_pos, length, src_pos, src, x10, t0);

  // Restore previously spilled arguments
  arraycopy_load_args(src, src_pos, length, dst, dst_pos);

  // return value is -1^K where K is partial copied count
  __ xori(t0, x10, -1);
  // adjust length down and src/end pos up by partial copied count
  __ subw(length, length, t0);
  __ addw(src_pos, src_pos, t0);
  __ addw(dst_pos, dst_pos, t0);
}

void LIR_Assembler::arraycopy_type_check(Register src, Register src_pos, Register length,
                                         Register dst, Register dst_pos, Register tmp,
                                         CodeStub *stub, BasicType basic_type, int flags) {
  // We don't know the array types are compatible
  if (basic_type != T_OBJECT) {
    // Simple test for basic type arrays
    if (UseCompressedClassPointers) {
      __ lwu(tmp, Address(src, oopDesc::klass_offset_in_bytes()));
      __ lwu(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
    } else {
      __ ld(tmp, Address(src, oopDesc::klass_offset_in_bytes()));
      __ ld(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
    }
    __ bne(tmp, t0, *stub->entry(), /* is_far */ true);
  } else {
    // For object arrays, if src is a sub class of dst then we can
    // safely do the copy.
    Label cont, slow;

#define PUSH(r1, r2)                                     \
    __ addi(sp, sp, -2 * wordSize);                      \
    __ sd(r1, Address(sp, 1 * wordSize));                \
    __ sd(r2, Address(sp, 0));

#define POP(r1, r2)                                      \
    __ ld(r1, Address(sp, 1 * wordSize));                \
    __ ld(r2, Address(sp, 0));                           \
    __ addi(sp, sp, 2 * wordSize);

    PUSH(src, dst);
    __ load_klass(src, src);
    __ load_klass(dst, dst);
    __ check_klass_subtype_fast_path(src, dst, tmp, &cont, &slow, nullptr);

    PUSH(src, dst);
    __ far_call(RuntimeAddress(Runtime1::entry_for(Runtime1::slow_subtype_check_id)));
    POP(src, dst);
    __ bnez(dst, cont);

    __ bind(slow);
    POP(src, dst);

    address copyfunc_addr = StubRoutines::checkcast_arraycopy();
    if (copyfunc_addr != nullptr) { // use stub if available
      arraycopy_checkcast(src, src_pos, length, dst, dst_pos, tmp, stub, basic_type, copyfunc_addr, flags);
    }

    __ j(*stub->entry());
    __ bind(cont);
    POP(src, dst);
  }
}

void LIR_Assembler::arraycopy_assert(Register src, Register dst, Register tmp, ciArrayKlass *default_type, int flags) {
  assert(default_type != nullptr, "null default_type!");
  BasicType basic_type = default_type->element_type()->basic_type();

  if (basic_type == T_ARRAY) { basic_type = T_OBJECT; }
  if (basic_type != T_OBJECT || !(flags & LIR_OpArrayCopy::type_check)) {
    // Sanity check the known type with the incoming class.  For the
    // primitive case the types must match exactly with src.klass and
    // dst.klass each exactly matching the default type.  For the
    // object array case, if no type check is needed then either the
    // dst type is exactly the expected type and the src type is a
    // subtype which we can't check or src is the same array as dst
    // but not necessarily exactly of type default_type.
    Label known_ok, halt;
    __ mov_metadata(tmp, default_type->constant_encoding());
    if (UseCompressedClassPointers) {
      __ encode_klass_not_null(tmp);
    }

    if (basic_type != T_OBJECT) {
      if (UseCompressedClassPointers) {
        __ lwu(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
      } else {
        __ ld(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
      }
      __ bne(tmp, t0, halt);
      if (UseCompressedClassPointers) {
        __ lwu(t0, Address(src, oopDesc::klass_offset_in_bytes()));
      } else {
        __ ld(t0, Address(src, oopDesc::klass_offset_in_bytes()));
      }
      __ beq(tmp, t0, known_ok);
    } else {
      if (UseCompressedClassPointers) {
        __ lwu(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
      } else {
        __ ld(t0, Address(dst, oopDesc::klass_offset_in_bytes()));
      }
      __ beq(tmp, t0, known_ok);
      __ beq(src, dst, known_ok);
    }
    __ bind(halt);
    __ stop("incorrect type information in arraycopy");
    __ bind(known_ok);
  }
}

void LIR_Assembler::emit_arraycopy(LIR_OpArrayCopy* op) {
  ciArrayKlass *default_type = op->expected_type();
  Register src = op->src()->as_register();
  Register dst = op->dst()->as_register();
  Register src_pos = op->src_pos()->as_register();
  Register dst_pos = op->dst_pos()->as_register();
  Register length = op->length()->as_register();
  Register tmp = op->tmp()->as_register();

  CodeStub* stub = op->stub();
  int flags = op->flags();
  BasicType basic_type = default_type != nullptr ? default_type->element_type()->basic_type() : T_ILLEGAL;
  if (is_reference_type(basic_type)) { basic_type = T_OBJECT; }

  // if we don't know anything, just go through the generic arraycopy
  if (default_type == nullptr) {
    generic_arraycopy(src, src_pos, length, dst, dst_pos, stub);
    return;
  }

  assert(default_type != nullptr && default_type->is_array_klass() && default_type->is_loaded(),
         "must be true at this point");

  arraycopy_simple_check(src, src_pos, length, dst, dst_pos, tmp, stub, flags);

  if (flags & LIR_OpArrayCopy::type_check) {
    arraycopy_type_check(src, src_pos, length, dst, dst_pos, tmp, stub, basic_type, flags);
  }

#ifdef ASSERT
  arraycopy_assert(src, dst, tmp, default_type, flags);
#endif

#ifndef PRODUCT
  if (PrintC1Statistics) {
    __ incrementw(ExternalAddress(Runtime1::arraycopy_count_address(basic_type)));
  }
#endif
  arraycopy_prepare_params(src, src_pos, length, dst, dst_pos, basic_type);

  bool disjoint = (flags & LIR_OpArrayCopy::overlapping) == 0;
  bool aligned = (flags & LIR_OpArrayCopy::unaligned) == 0;
  const char *name = nullptr;
  address entry = StubRoutines::select_arraycopy_function(basic_type, aligned, disjoint, name, false);

  CodeBlob *cb = CodeCache::find_blob(entry);
  if (cb != nullptr) {
    __ far_call(RuntimeAddress(entry));
  } else {
    const int args_num = 3;
    __ call_VM_leaf(entry, args_num);
  }

  if (stub != nullptr) {
    __ bind(*stub->continuation());
  }
}


void LIR_Assembler::arraycopy_prepare_params(Register src, Register src_pos, Register length,
                                             Register dst, Register dst_pos, BasicType basic_type) {
  int scale = array_element_size(basic_type);
  __ shadd(c_rarg0, src_pos, src, t0, scale);
  __ add(c_rarg0, c_rarg0, arrayOopDesc::base_offset_in_bytes(basic_type));
  assert_different_registers(c_rarg0, dst, dst_pos, length);
  __ shadd(c_rarg1, dst_pos, dst, t0, scale);
  __ add(c_rarg1, c_rarg1, arrayOopDesc::base_offset_in_bytes(basic_type));
  assert_different_registers(c_rarg1, dst, length);
  __ mv(c_rarg2, length);
  assert_different_registers(c_rarg2, dst);
}

void LIR_Assembler::arraycopy_checkcast_prepare_params(Register src, Register src_pos, Register length,
                                                       Register dst, Register dst_pos, BasicType basic_type) {
  arraycopy_prepare_params(src, src_pos, length, dst, dst_pos, basic_type);
  __ load_klass(c_rarg4, dst);
  __ ld(c_rarg4, Address(c_rarg4, ObjArrayKlass::element_klass_offset()));
  __ lwu(c_rarg3, Address(c_rarg4, Klass::super_check_offset_offset()));
}

void LIR_Assembler::arraycopy_store_args(Register src, Register src_pos, Register length,
                                         Register dst, Register dst_pos) {
  __ sd(dst_pos, Address(sp, 0));                // 0: dst_pos sp offset
  __ sd(dst, Address(sp, 1 * BytesPerWord));     // 1: dst sp offset
  __ sd(length, Address(sp, 2 * BytesPerWord));  // 2: length sp offset
  __ sd(src_pos, Address(sp, 3 * BytesPerWord)); // 3: src_pos sp offset
  __ sd(src, Address(sp, 4 * BytesPerWord));     // 4: src sp offset
}

void LIR_Assembler::arraycopy_load_args(Register src, Register src_pos, Register length,
                                        Register dst, Register dst_pos) {
  __ ld(dst_pos, Address(sp, 0));                // 0: dst_pos sp offset
  __ ld(dst, Address(sp, 1 * BytesPerWord));     // 1: dst sp offset
  __ ld(length, Address(sp, 2 * BytesPerWord));  // 2: length sp offset
  __ ld(src_pos, Address(sp, 3 * BytesPerWord)); // 3: src_pos sp offset
  __ ld(src, Address(sp, 4 * BytesPerWord));     // 4: src sp offset
}

#undef __
