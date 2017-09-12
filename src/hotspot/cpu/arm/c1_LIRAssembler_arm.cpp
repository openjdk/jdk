/*
 * Copyright (c) 2008, 2016, Oracle and/or its affiliates. All rights reserved.
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
#include "c1/c1_Compilation.hpp"
#include "c1/c1_LIRAssembler.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "c1/c1_ValueStack.hpp"
#include "ci/ciArrayKlass.hpp"
#include "ci/ciInstance.hpp"
#include "gc/shared/barrierSet.hpp"
#include "gc/shared/cardTableModRefBS.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "nativeInst_arm.hpp"
#include "oops/objArrayKlass.hpp"
#include "runtime/sharedRuntime.hpp"
#include "vmreg_arm.inline.hpp"

#define __ _masm->

// Note: Rtemp usage is this file should not impact C2 and should be
// correct as long as it is not implicitly used in lower layers (the
// arm [macro]assembler) and used with care in the other C1 specific
// files.

bool LIR_Assembler::is_small_constant(LIR_Opr opr) {
  ShouldNotCallThis(); // Not used on ARM
  return false;
}


LIR_Opr LIR_Assembler::receiverOpr() {
  // The first register in Java calling conventions
  return FrameMap::R0_oop_opr;
}

LIR_Opr LIR_Assembler::osrBufferPointer() {
  return FrameMap::as_pointer_opr(R0);
}

#ifndef PRODUCT
void LIR_Assembler::verify_reserved_argument_area_size(int args_count) {
  assert(args_count * wordSize <= frame_map()->reserved_argument_area_size(), "not enough space for arguments");
}
#endif // !PRODUCT

void LIR_Assembler::store_parameter(jint c, int offset_from_sp_in_words) {
  assert(offset_from_sp_in_words >= 0, "invalid offset from sp");
  int offset_from_sp_in_bytes = offset_from_sp_in_words * BytesPerWord;
  assert(offset_from_sp_in_bytes < frame_map()->reserved_argument_area_size(), "not enough space");
  __ mov_slow(Rtemp, c);
  __ str(Rtemp, Address(SP, offset_from_sp_in_bytes));
}

void LIR_Assembler::store_parameter(Metadata* m, int offset_from_sp_in_words) {
  assert(offset_from_sp_in_words >= 0, "invalid offset from sp");
  int offset_from_sp_in_bytes = offset_from_sp_in_words * BytesPerWord;
  assert(offset_from_sp_in_bytes < frame_map()->reserved_argument_area_size(), "not enough space");
  __ mov_metadata(Rtemp, m);
  __ str(Rtemp, Address(SP, offset_from_sp_in_bytes));
}

//--------------fpu register translations-----------------------


void LIR_Assembler::set_24bit_FPU() {
  ShouldNotReachHere();
}

void LIR_Assembler::reset_FPU() {
  ShouldNotReachHere();
}

void LIR_Assembler::fpop() {
  Unimplemented();
}

void LIR_Assembler::fxch(int i) {
  Unimplemented();
}

void LIR_Assembler::fld(int i) {
  Unimplemented();
}

void LIR_Assembler::ffree(int i) {
  Unimplemented();
}

void LIR_Assembler::breakpoint() {
  __ breakpoint();
}

void LIR_Assembler::push(LIR_Opr opr) {
  Unimplemented();
}

void LIR_Assembler::pop(LIR_Opr opr) {
  Unimplemented();
}

//-------------------------------------------
Address LIR_Assembler::as_Address(LIR_Address* addr) {
  Register base = addr->base()->as_pointer_register();

#ifdef AARCH64
  int align = exact_log2(type2aelembytes(addr->type(), true));
#endif

  if (addr->index()->is_illegal() || addr->index()->is_constant()) {
    int offset = addr->disp();
    if (addr->index()->is_constant()) {
      offset += addr->index()->as_constant_ptr()->as_jint() << addr->scale();
    }

#ifdef AARCH64
    if (!Assembler::is_unsigned_imm_in_range(offset, 12, align) && !Assembler::is_imm_in_range(offset, 9, 0)) {
      BAILOUT_("offset not in range", Address(base));
    }
    assert(UseUnalignedAccesses || (offset & right_n_bits(align)) == 0, "offset should be aligned");
#else
    if ((offset <= -4096) || (offset >= 4096)) {
      BAILOUT_("offset not in range", Address(base));
    }
#endif // AARCH64

    return Address(base, offset);

  } else {
    assert(addr->disp() == 0, "can't have both");
    int scale = addr->scale();

#ifdef AARCH64
    assert((scale == 0) || (scale == align), "scale should be zero or equal to embedded shift");

    bool is_index_extended = (addr->index()->type() == T_INT);
    if (is_index_extended) {
      assert(addr->index()->is_single_cpu(), "should be");
      return Address(base, addr->index()->as_register(), ex_sxtw, scale);
    } else {
      assert(addr->index()->is_double_cpu(), "should be");
      return Address(base, addr->index()->as_register_lo(), ex_lsl, scale);
    }
#else
    assert(addr->index()->is_single_cpu(), "should be");
    return scale >= 0 ? Address(base, addr->index()->as_register(), lsl, scale) :
                        Address(base, addr->index()->as_register(), lsr, -scale);
#endif // AARCH64
  }
}

Address LIR_Assembler::as_Address_hi(LIR_Address* addr) {
#ifdef AARCH64
  ShouldNotCallThis(); // Not used on AArch64
  return Address();
#else
  Address base = as_Address(addr);
  assert(base.index() == noreg, "must be");
  if (base.disp() + BytesPerWord >= 4096) { BAILOUT_("offset not in range", Address(base.base(),0)); }
  return Address(base.base(), base.disp() + BytesPerWord);
#endif // AARCH64
}

Address LIR_Assembler::as_Address_lo(LIR_Address* addr) {
#ifdef AARCH64
  ShouldNotCallThis(); // Not used on AArch64
  return Address();
#else
  return as_Address(addr);
#endif // AARCH64
}


void LIR_Assembler::osr_entry() {
  offsets()->set_value(CodeOffsets::OSR_Entry, code_offset());
  BlockBegin* osr_entry = compilation()->hir()->osr_entry();
  ValueStack* entry_state = osr_entry->end()->state();
  int number_of_locks = entry_state->locks_size();

  __ build_frame(initial_frame_size_in_bytes(), bang_size_in_bytes());
  Register OSR_buf = osrBufferPointer()->as_pointer_register();

  assert(frame::interpreter_frame_monitor_size() == BasicObjectLock::size(), "adjust code below");
  int monitor_offset = (method()->max_locals() + 2 * (number_of_locks - 1)) * BytesPerWord;
  for (int i = 0; i < number_of_locks; i++) {
    int slot_offset = monitor_offset - (i * 2 * BytesPerWord);
    __ ldr(R1, Address(OSR_buf, slot_offset + 0*BytesPerWord));
    __ ldr(R2, Address(OSR_buf, slot_offset + 1*BytesPerWord));
    __ str(R1, frame_map()->address_for_monitor_lock(i));
    __ str(R2, frame_map()->address_for_monitor_object(i));
  }
}


int LIR_Assembler::check_icache() {
  Register receiver = LIR_Assembler::receiverOpr()->as_register();
  int offset = __ offset();
  __ inline_cache_check(receiver, Ricklass);
  return offset;
}


void LIR_Assembler::jobject2reg_with_patching(Register reg, CodeEmitInfo* info) {
  jobject o = (jobject)Universe::non_oop_word();
  int index = __ oop_recorder()->allocate_oop_index(o);

  PatchingStub* patch = new PatchingStub(_masm, patching_id(info), index);

  __ patchable_mov_oop(reg, o, index);
  patching_epilog(patch, lir_patch_normal, reg, info);
}


void LIR_Assembler::klass2reg_with_patching(Register reg, CodeEmitInfo* info) {
  Metadata* o = (Metadata*)Universe::non_oop_word();
  int index = __ oop_recorder()->allocate_metadata_index(o);
  PatchingStub* patch = new PatchingStub(_masm, PatchingStub::load_klass_id, index);

  __ patchable_mov_metadata(reg, o, index);
  patching_epilog(patch, lir_patch_normal, reg, info);
}


int LIR_Assembler::initial_frame_size_in_bytes() const {
  // Subtracts two words to account for return address and link
  return frame_map()->framesize()*VMRegImpl::stack_slot_size - 2*wordSize;
}


int LIR_Assembler::emit_exception_handler() {
  // TODO: ARM
  __ nop(); // See comments in other ports

  address handler_base = __ start_a_stub(exception_handler_size());
  if (handler_base == NULL) {
    bailout("exception handler overflow");
    return -1;
  }

  int offset = code_offset();

  // check that there is really an exception
  __ verify_not_null_oop(Rexception_obj);

  __ call(Runtime1::entry_for(Runtime1::handle_exception_from_callee_id), relocInfo::runtime_call_type);
  __ should_not_reach_here();

  assert(code_offset() - offset <= exception_handler_size(), "overflow");
  __ end_a_stub();

  return offset;
}

// Emit the code to remove the frame from the stack in the exception
// unwind path.
int LIR_Assembler::emit_unwind_handler() {
#ifndef PRODUCT
  if (CommentedAssembly) {
    _masm->block_comment("Unwind handler");
  }
#endif

  int offset = code_offset();

  // Fetch the exception from TLS and clear out exception related thread state
  Register zero = __ zero_register(Rtemp);
  __ ldr(Rexception_obj, Address(Rthread, JavaThread::exception_oop_offset()));
  __ str(zero, Address(Rthread, JavaThread::exception_oop_offset()));
  __ str(zero, Address(Rthread, JavaThread::exception_pc_offset()));

  __ bind(_unwind_handler_entry);
  __ verify_not_null_oop(Rexception_obj);

  // Preform needed unlocking
  MonitorExitStub* stub = NULL;
  if (method()->is_synchronized()) {
    monitor_address(0, FrameMap::R0_opr);
    stub = new MonitorExitStub(FrameMap::R0_opr, true, 0);
    __ unlock_object(R2, R1, R0, Rtemp, *stub->entry());
    __ bind(*stub->continuation());
  }

  // remove the activation and dispatch to the unwind handler
  __ remove_frame(initial_frame_size_in_bytes()); // restores FP and LR
  __ jump(Runtime1::entry_for(Runtime1::unwind_exception_id), relocInfo::runtime_call_type, Rtemp);

  // Emit the slow path assembly
  if (stub != NULL) {
    stub->emit_code(this);
  }

  return offset;
}


int LIR_Assembler::emit_deopt_handler() {
  address handler_base = __ start_a_stub(deopt_handler_size());
  if (handler_base == NULL) {
    bailout("deopt handler overflow");
    return -1;
  }

  int offset = code_offset();

  __ mov_relative_address(LR, __ pc());
#ifdef AARCH64
  __ raw_push(LR, LR);
  __ jump(SharedRuntime::deopt_blob()->unpack(), relocInfo::runtime_call_type, Rtemp);
#else
  __ push(LR); // stub expects LR to be saved
  __ jump(SharedRuntime::deopt_blob()->unpack(), relocInfo::runtime_call_type, noreg);
#endif // AARCH64

  assert(code_offset() - offset <= deopt_handler_size(), "overflow");
  __ end_a_stub();

  return offset;
}


void LIR_Assembler::return_op(LIR_Opr result) {
  // Pop the frame before safepoint polling
  __ remove_frame(initial_frame_size_in_bytes());

  // mov_slow here is usually one or two instruction
  // TODO-AARCH64 3 instructions on AArch64, so try to load polling page by ldr_literal
  __ mov_address(Rtemp, os::get_polling_page(), symbolic_Relocation::polling_page_reference);
  __ relocate(relocInfo::poll_return_type);
  __ ldr(Rtemp, Address(Rtemp));
  __ ret();
}


int LIR_Assembler::safepoint_poll(LIR_Opr tmp, CodeEmitInfo* info) {
  __ mov_address(Rtemp, os::get_polling_page(), symbolic_Relocation::polling_page_reference);
  if (info != NULL) {
    add_debug_info_for_branch(info);
  }
  int offset = __ offset();
  __ relocate(relocInfo::poll_type);
  __ ldr(Rtemp, Address(Rtemp));
  return offset;
}


void LIR_Assembler::move_regs(Register from_reg, Register to_reg) {
  if (from_reg != to_reg) {
    __ mov(to_reg, from_reg);
  }
}

void LIR_Assembler::const2reg(LIR_Opr src, LIR_Opr dest, LIR_PatchCode patch_code, CodeEmitInfo* info) {
  assert(src->is_constant() && dest->is_register(), "must be");
  LIR_Const* c = src->as_constant_ptr();

  switch (c->type()) {
    case T_ADDRESS:
    case T_INT:
      assert(patch_code == lir_patch_none, "no patching handled here");
      __ mov_slow(dest->as_register(), c->as_jint());
      break;

    case T_LONG:
      assert(patch_code == lir_patch_none, "no patching handled here");
#ifdef AARCH64
      __ mov_slow(dest->as_pointer_register(), (intptr_t)c->as_jlong());
#else
      __ mov_slow(dest->as_register_lo(), c->as_jint_lo());
      __ mov_slow(dest->as_register_hi(), c->as_jint_hi());
#endif // AARCH64
      break;

    case T_OBJECT:
      if (patch_code == lir_patch_none) {
        __ mov_oop(dest->as_register(), c->as_jobject());
      } else {
        jobject2reg_with_patching(dest->as_register(), info);
      }
      break;

    case T_METADATA:
      if (patch_code == lir_patch_none) {
        __ mov_metadata(dest->as_register(), c->as_metadata());
      } else {
        klass2reg_with_patching(dest->as_register(), info);
      }
      break;

    case T_FLOAT:
      if (dest->is_single_fpu()) {
        __ mov_float(dest->as_float_reg(), c->as_jfloat());
      } else {
#ifdef AARCH64
        ShouldNotReachHere();
#else
        // Simple getters can return float constant directly into r0
        __ mov_slow(dest->as_register(), c->as_jint_bits());
#endif // AARCH64
      }
      break;

    case T_DOUBLE:
      if (dest->is_double_fpu()) {
        __ mov_double(dest->as_double_reg(), c->as_jdouble());
      } else {
#ifdef AARCH64
        ShouldNotReachHere();
#else
        // Simple getters can return double constant directly into r1r0
        __ mov_slow(dest->as_register_lo(), c->as_jint_lo_bits());
        __ mov_slow(dest->as_register_hi(), c->as_jint_hi_bits());
#endif // AARCH64
      }
      break;

    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::const2stack(LIR_Opr src, LIR_Opr dest) {
  assert(src->is_constant(), "must be");
  assert(dest->is_stack(), "must be");
  LIR_Const* c = src->as_constant_ptr();

  switch (c->type()) {
    case T_INT:  // fall through
    case T_FLOAT:
      __ mov_slow(Rtemp, c->as_jint_bits());
      __ str_32(Rtemp, frame_map()->address_for_slot(dest->single_stack_ix()));
      break;

    case T_ADDRESS:
      __ mov_slow(Rtemp, c->as_jint());
      __ str(Rtemp, frame_map()->address_for_slot(dest->single_stack_ix()));
      break;

    case T_OBJECT:
      __ mov_oop(Rtemp, c->as_jobject());
      __ str(Rtemp, frame_map()->address_for_slot(dest->single_stack_ix()));
      break;

    case T_LONG:  // fall through
    case T_DOUBLE:
#ifdef AARCH64
      __ mov_slow(Rtemp, c->as_jlong_bits());
      __ str(Rtemp, frame_map()->address_for_slot(dest->double_stack_ix()));
#else
      __ mov_slow(Rtemp, c->as_jint_lo_bits());
      __ str(Rtemp, frame_map()->address_for_slot(dest->double_stack_ix(), lo_word_offset_in_bytes));
      if (c->as_jint_hi_bits() != c->as_jint_lo_bits()) {
        __ mov_slow(Rtemp, c->as_jint_hi_bits());
      }
      __ str(Rtemp, frame_map()->address_for_slot(dest->double_stack_ix(), hi_word_offset_in_bytes));
#endif // AARCH64
      break;

    default:
      ShouldNotReachHere();
  }
}

void LIR_Assembler::const2mem(LIR_Opr src, LIR_Opr dest, BasicType type,
                              CodeEmitInfo* info, bool wide) {
#ifdef AARCH64
  assert((src->as_constant_ptr()->type() == T_OBJECT && src->as_constant_ptr()->as_jobject() == NULL) ||
         (src->as_constant_ptr()->type() == T_INT && src->as_constant_ptr()->as_jint() == 0) ||
         (src->as_constant_ptr()->type() == T_LONG && src->as_constant_ptr()->as_jlong() == 0) ||
         (src->as_constant_ptr()->type() == T_FLOAT && src->as_constant_ptr()->as_jint_bits() == 0) ||
         (src->as_constant_ptr()->type() == T_DOUBLE && src->as_constant_ptr()->as_jlong_bits() == 0),
        "cannot handle otherwise");
  assert(dest->as_address_ptr()->type() == type, "should be");

  Address addr = as_Address(dest->as_address_ptr());
  int null_check_offset = code_offset();
  switch (type) {
    case T_OBJECT:  // fall through
    case T_ARRAY:
        if (UseCompressedOops && !wide) {
          __ str_w(ZR, addr);
        } else {
          __ str(ZR, addr);
        }
        break;
    case T_ADDRESS: // fall through
    case T_DOUBLE:  // fall through
    case T_LONG:    __ str(ZR, addr);   break;
    case T_FLOAT:   // fall through
    case T_INT:     __ str_w(ZR, addr); break;
    case T_BOOLEAN: // fall through
    case T_BYTE:    __ strb(ZR, addr);  break;
    case T_CHAR:    // fall through
    case T_SHORT:   __ strh(ZR, addr);  break;
    default: ShouldNotReachHere();
  }
#else
  assert((src->as_constant_ptr()->type() == T_OBJECT && src->as_constant_ptr()->as_jobject() == NULL),"cannot handle otherwise");
  __ mov(Rtemp, 0);

  int null_check_offset = code_offset();
  __ str(Rtemp, as_Address(dest->as_address_ptr()));
#endif // AARCH64

  if (info != NULL) {
#ifndef AARCH64
    assert(false, "arm32 didn't support this before, investigate if bug");
#endif
    add_debug_info_for_null_check(null_check_offset, info);
  }
}

void LIR_Assembler::reg2reg(LIR_Opr src, LIR_Opr dest) {
  assert(src->is_register() && dest->is_register(), "must be");

  if (src->is_single_cpu()) {
    if (dest->is_single_cpu()) {
      move_regs(src->as_register(), dest->as_register());
#ifdef AARCH64
    } else if (dest->is_double_cpu()) {
      assert ((src->type() == T_OBJECT) || (src->type() == T_ARRAY) || (src->type() == T_ADDRESS), "invalid src type");
      move_regs(src->as_register(), dest->as_register_lo());
#else
    } else if (dest->is_single_fpu()) {
      __ fmsr(dest->as_float_reg(), src->as_register());
#endif // AARCH64
    } else {
      ShouldNotReachHere();
    }
  } else if (src->is_double_cpu()) {
#ifdef AARCH64
    move_regs(src->as_register_lo(), dest->as_register_lo());
#else
    if (dest->is_double_cpu()) {
      __ long_move(dest->as_register_lo(), dest->as_register_hi(), src->as_register_lo(), src->as_register_hi());
    } else {
      __ fmdrr(dest->as_double_reg(), src->as_register_lo(), src->as_register_hi());
    }
#endif // AARCH64
  } else if (src->is_single_fpu()) {
    if (dest->is_single_fpu()) {
      __ mov_float(dest->as_float_reg(), src->as_float_reg());
    } else if (dest->is_single_cpu()) {
      __ mov_fpr2gpr_float(dest->as_register(), src->as_float_reg());
    } else {
      ShouldNotReachHere();
    }
  } else if (src->is_double_fpu()) {
    if (dest->is_double_fpu()) {
      __ mov_double(dest->as_double_reg(), src->as_double_reg());
    } else if (dest->is_double_cpu()) {
#ifdef AARCH64
      __ fmov_xd(dest->as_register_lo(), src->as_double_reg());
#else
      __ fmrrd(dest->as_register_lo(), dest->as_register_hi(), src->as_double_reg());
#endif // AARCH64
    } else {
      ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::reg2stack(LIR_Opr src, LIR_Opr dest, BasicType type, bool pop_fpu_stack) {
  assert(src->is_register(), "should not call otherwise");
  assert(dest->is_stack(), "should not call otherwise");

  Address addr = dest->is_single_word() ?
    frame_map()->address_for_slot(dest->single_stack_ix()) :
    frame_map()->address_for_slot(dest->double_stack_ix());

#ifndef AARCH64
  assert(lo_word_offset_in_bytes == 0 && hi_word_offset_in_bytes == 4, "little ending");
  if (src->is_single_fpu() || src->is_double_fpu()) {
    if (addr.disp() >= 1024) { BAILOUT("Too exotic case to handle here"); }
  }
#endif // !AARCH64

  if (src->is_single_cpu()) {
    switch (type) {
      case T_OBJECT:
      case T_ARRAY:    __ verify_oop(src->as_register());   // fall through
      case T_ADDRESS:
      case T_METADATA: __ str(src->as_register(), addr);    break;
      case T_FLOAT:    // used in intBitsToFloat intrinsic implementation, fall through
      case T_INT:      __ str_32(src->as_register(), addr); break;
      default:
        ShouldNotReachHere();
    }
  } else if (src->is_double_cpu()) {
    __ str(src->as_register_lo(), addr);
#ifndef AARCH64
    __ str(src->as_register_hi(), frame_map()->address_for_slot(dest->double_stack_ix(), hi_word_offset_in_bytes));
#endif // !AARCH64
  } else if (src->is_single_fpu()) {
    __ str_float(src->as_float_reg(), addr);
  } else if (src->is_double_fpu()) {
    __ str_double(src->as_double_reg(), addr);
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::reg2mem(LIR_Opr src, LIR_Opr dest, BasicType type,
                            LIR_PatchCode patch_code, CodeEmitInfo* info,
                            bool pop_fpu_stack, bool wide,
                            bool unaligned) {
  LIR_Address* to_addr = dest->as_address_ptr();
  Register base_reg = to_addr->base()->as_pointer_register();
  const bool needs_patching = (patch_code != lir_patch_none);

  PatchingStub* patch = NULL;
  if (needs_patching) {
#ifdef AARCH64
    // Same alignment of reg2mem code and PatchingStub code. Required to make copied bind_literal() code properly aligned.
    __ align(wordSize);
#endif
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
#ifdef AARCH64
    // Extra nop for MT safe patching
    __ nop();
#endif // AARCH64
  }

  int null_check_offset = code_offset();

  switch (type) {
    case T_ARRAY:
    case T_OBJECT:
      if (UseCompressedOops && !wide) {
#ifdef AARCH64
        const Register temp_src = Rtemp;
        assert_different_registers(temp_src, src->as_register());
        __ encode_heap_oop(temp_src, src->as_register());
        null_check_offset = code_offset();
        __ str_32(temp_src, as_Address(to_addr));
#else
        ShouldNotReachHere();
#endif // AARCH64
      } else {
        __ str(src->as_register(), as_Address(to_addr));
      }
      break;

    case T_ADDRESS:
#ifdef AARCH64
    case T_LONG:
#endif // AARCH64
      __ str(src->as_pointer_register(), as_Address(to_addr));
      break;

    case T_BYTE:
    case T_BOOLEAN:
      __ strb(src->as_register(), as_Address(to_addr));
      break;

    case T_CHAR:
    case T_SHORT:
      __ strh(src->as_register(), as_Address(to_addr));
      break;

    case T_INT:
#ifdef __SOFTFP__
    case T_FLOAT:
#endif // __SOFTFP__
      __ str_32(src->as_register(), as_Address(to_addr));
      break;

#ifdef AARCH64

    case T_FLOAT:
      __ str_s(src->as_float_reg(), as_Address(to_addr));
      break;

    case T_DOUBLE:
      __ str_d(src->as_double_reg(), as_Address(to_addr));
      break;

#else // AARCH64

#ifdef __SOFTFP__
    case T_DOUBLE:
#endif // __SOFTFP__
    case T_LONG: {
      Register from_lo = src->as_register_lo();
      Register from_hi = src->as_register_hi();
      if (to_addr->index()->is_register()) {
        assert(to_addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        assert(to_addr->disp() == 0, "Not yet supporting both");
        __ add(Rtemp, base_reg, to_addr->index()->as_register());
        base_reg = Rtemp;
        __ str(from_lo, Address(Rtemp));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ str(from_hi, Address(Rtemp, BytesPerWord));
      } else if (base_reg == from_lo) {
        __ str(from_hi, as_Address_hi(to_addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_high, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_low;
        }
        __ str(from_lo, as_Address_lo(to_addr));
      } else {
        __ str(from_lo, as_Address_lo(to_addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ str(from_hi, as_Address_hi(to_addr));
      }
      break;
    }

#ifndef __SOFTFP__
    case T_FLOAT:
      if (to_addr->index()->is_register()) {
        assert(to_addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        __ add(Rtemp, base_reg, to_addr->index()->as_register());
        if ((to_addr->disp() <= -4096) || (to_addr->disp() >= 4096)) { BAILOUT("offset not in range"); }
        __ fsts(src->as_float_reg(), Address(Rtemp, to_addr->disp()));
      } else {
        __ fsts(src->as_float_reg(), as_Address(to_addr));
      }
      break;

    case T_DOUBLE:
      if (to_addr->index()->is_register()) {
        assert(to_addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        __ add(Rtemp, base_reg, to_addr->index()->as_register());
        if ((to_addr->disp() <= -4096) || (to_addr->disp() >= 4096)) { BAILOUT("offset not in range"); }
        __ fstd(src->as_double_reg(), Address(Rtemp, to_addr->disp()));
      } else {
        __ fstd(src->as_double_reg(), as_Address(to_addr));
      }
      break;
#endif // __SOFTFP__

#endif // AARCH64

    default:
      ShouldNotReachHere();
  }

  if (info != NULL) {
    add_debug_info_for_null_check(null_check_offset, info);
  }

  if (patch != NULL) {
    // Offset embeedded into LDR/STR instruction may appear not enough
    // to address a field. So, provide a space for one more instruction
    // that will deal with larger offsets.
    __ nop();
    patching_epilog(patch, patch_code, base_reg, info);
  }
}


void LIR_Assembler::stack2reg(LIR_Opr src, LIR_Opr dest, BasicType type) {
  assert(src->is_stack(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");

  Address addr = src->is_single_word() ?
    frame_map()->address_for_slot(src->single_stack_ix()) :
    frame_map()->address_for_slot(src->double_stack_ix());

#ifndef AARCH64
  assert(lo_word_offset_in_bytes == 0 && hi_word_offset_in_bytes == 4, "little ending");
  if (dest->is_single_fpu() || dest->is_double_fpu()) {
    if (addr.disp() >= 1024) { BAILOUT("Too exotic case to handle here"); }
  }
#endif // !AARCH64

  if (dest->is_single_cpu()) {
    switch (type) {
      case T_OBJECT:
      case T_ARRAY:
      case T_ADDRESS:
      case T_METADATA: __ ldr(dest->as_register(), addr); break;
      case T_FLOAT:    // used in floatToRawIntBits intrinsic implemenation
      case T_INT:      __ ldr_u32(dest->as_register(), addr); break;
      default:
        ShouldNotReachHere();
    }
    if ((type == T_OBJECT) || (type == T_ARRAY)) {
      __ verify_oop(dest->as_register());
    }
  } else if (dest->is_double_cpu()) {
    __ ldr(dest->as_register_lo(), addr);
#ifndef AARCH64
    __ ldr(dest->as_register_hi(), frame_map()->address_for_slot(src->double_stack_ix(), hi_word_offset_in_bytes));
#endif // !AARCH64
  } else if (dest->is_single_fpu()) {
    __ ldr_float(dest->as_float_reg(), addr);
  } else if (dest->is_double_fpu()) {
    __ ldr_double(dest->as_double_reg(), addr);
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::stack2stack(LIR_Opr src, LIR_Opr dest, BasicType type) {
  if (src->is_single_stack()) {
    switch (src->type()) {
      case T_OBJECT:
      case T_ARRAY:
      case T_ADDRESS:
      case T_METADATA:
        __ ldr(Rtemp, frame_map()->address_for_slot(src->single_stack_ix()));
        __ str(Rtemp, frame_map()->address_for_slot(dest->single_stack_ix()));
        break;

      case T_INT:
      case T_FLOAT:
        __ ldr_u32(Rtemp, frame_map()->address_for_slot(src->single_stack_ix()));
        __ str_32(Rtemp, frame_map()->address_for_slot(dest->single_stack_ix()));
        break;

      default:
        ShouldNotReachHere();
    }
  } else {
    assert(src->is_double_stack(), "must be");
    __ ldr(Rtemp, frame_map()->address_for_slot(src->double_stack_ix(), lo_word_offset_in_bytes));
    __ str(Rtemp, frame_map()->address_for_slot(dest->double_stack_ix(), lo_word_offset_in_bytes));
#ifdef AARCH64
    assert(lo_word_offset_in_bytes == 0, "adjust this code");
#else
    __ ldr(Rtemp, frame_map()->address_for_slot(src->double_stack_ix(), hi_word_offset_in_bytes));
    __ str(Rtemp, frame_map()->address_for_slot(dest->double_stack_ix(), hi_word_offset_in_bytes));
#endif // AARCH64
  }
}


void LIR_Assembler::mem2reg(LIR_Opr src, LIR_Opr dest, BasicType type,
                            LIR_PatchCode patch_code, CodeEmitInfo* info,
                            bool wide, bool unaligned) {
  assert(src->is_address(), "should not call otherwise");
  assert(dest->is_register(), "should not call otherwise");
  LIR_Address* addr = src->as_address_ptr();

  Register base_reg = addr->base()->as_pointer_register();

  PatchingStub* patch = NULL;
  if (patch_code != lir_patch_none) {
    patch = new PatchingStub(_masm, PatchingStub::access_field_id);
#ifdef AARCH64
    // Extra nop for MT safe patching
    __ nop();
#endif // AARCH64
  }
  if (info != NULL) {
    add_debug_info_for_null_check_here(info);
  }

  switch (type) {
    case T_OBJECT:  // fall through
    case T_ARRAY:
      if (UseCompressedOops && !wide) {
        __ ldr_u32(dest->as_register(), as_Address(addr));
      } else {
        __ ldr(dest->as_register(), as_Address(addr));
      }
      break;

    case T_ADDRESS:
      if (UseCompressedClassPointers && addr->disp() == oopDesc::klass_offset_in_bytes()) {
        __ ldr_u32(dest->as_pointer_register(), as_Address(addr));
      } else {
        __ ldr(dest->as_pointer_register(), as_Address(addr));
      }
      break;

#ifdef AARCH64
    case T_LONG:
#else
    case T_INT:
#ifdef __SOFTFP__
    case T_FLOAT:
#endif // __SOFTFP__
#endif // AARCH64
      __ ldr(dest->as_pointer_register(), as_Address(addr));
      break;

    case T_BOOLEAN:
      __ ldrb(dest->as_register(), as_Address(addr));
      break;

    case T_BYTE:
      __ ldrsb(dest->as_register(), as_Address(addr));
      break;

    case T_CHAR:
      __ ldrh(dest->as_register(), as_Address(addr));
      break;

    case T_SHORT:
      __ ldrsh(dest->as_register(), as_Address(addr));
      break;

#ifdef AARCH64

    case T_INT:
      __ ldr_w(dest->as_register(), as_Address(addr));
      break;

    case T_FLOAT:
      __ ldr_s(dest->as_float_reg(), as_Address(addr));
      break;

    case T_DOUBLE:
      __ ldr_d(dest->as_double_reg(), as_Address(addr));
      break;

#else // AARCH64

#ifdef __SOFTFP__
    case T_DOUBLE:
#endif // __SOFTFP__
    case T_LONG: {
      Register to_lo = dest->as_register_lo();
      Register to_hi = dest->as_register_hi();
      if (addr->index()->is_register()) {
        assert(addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        assert(addr->disp() == 0, "Not yet supporting both");
        __ add(Rtemp, base_reg, addr->index()->as_register());
        base_reg = Rtemp;
        __ ldr(to_lo, Address(Rtemp));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ ldr(to_hi, Address(Rtemp, BytesPerWord));
      } else if (base_reg == to_lo) {
        __ ldr(to_hi, as_Address_hi(addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_high, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_low;
        }
        __ ldr(to_lo, as_Address_lo(addr));
      } else {
        __ ldr(to_lo, as_Address_lo(addr));
        if (patch != NULL) {
          patching_epilog(patch, lir_patch_low, base_reg, info);
          patch = new PatchingStub(_masm, PatchingStub::access_field_id);
          patch_code = lir_patch_high;
        }
        __ ldr(to_hi, as_Address_hi(addr));
      }
      break;
    }

#ifndef __SOFTFP__
    case T_FLOAT:
      if (addr->index()->is_register()) {
        assert(addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        __ add(Rtemp, base_reg, addr->index()->as_register());
        if ((addr->disp() <= -4096) || (addr->disp() >= 4096)) { BAILOUT("offset not in range"); }
        __ flds(dest->as_float_reg(), Address(Rtemp, addr->disp()));
      } else {
        __ flds(dest->as_float_reg(), as_Address(addr));
      }
      break;

    case T_DOUBLE:
      if (addr->index()->is_register()) {
        assert(addr->scale() == LIR_Address::times_1,"Unexpected scaled register");
        __ add(Rtemp, base_reg, addr->index()->as_register());
        if ((addr->disp() <= -4096) || (addr->disp() >= 4096)) { BAILOUT("offset not in range"); }
        __ fldd(dest->as_double_reg(), Address(Rtemp, addr->disp()));
      } else {
        __ fldd(dest->as_double_reg(), as_Address(addr));
      }
      break;
#endif // __SOFTFP__

#endif // AARCH64

    default:
      ShouldNotReachHere();
  }

  if (patch != NULL) {
    // Offset embeedded into LDR/STR instruction may appear not enough
    // to address a field. So, provide a space for one more instruction
    // that will deal with larger offsets.
    __ nop();
    patching_epilog(patch, patch_code, base_reg, info);
  }

#ifdef AARCH64
  switch (type) {
    case T_ARRAY:
    case T_OBJECT:
      if (UseCompressedOops && !wide) {
        __ decode_heap_oop(dest->as_register());
      }
      __ verify_oop(dest->as_register());
      break;

    case T_ADDRESS:
      if (UseCompressedClassPointers && addr->disp() == oopDesc::klass_offset_in_bytes()) {
        __ decode_klass_not_null(dest->as_register());
      }
      break;
  }
#endif // AARCH64
}


void LIR_Assembler::emit_op3(LIR_Op3* op) {
  bool is_32 = op->result_opr()->is_single_cpu();

  if (op->code() == lir_idiv && op->in_opr2()->is_constant() && is_32) {
    int c = op->in_opr2()->as_constant_ptr()->as_jint();
    assert(is_power_of_2(c), "non power-of-2 constant should be put in a register");

    Register left = op->in_opr1()->as_register();
    Register dest = op->result_opr()->as_register();
    if (c == 1) {
      __ mov(dest, left);
    } else if (c == 2) {
      __ add_32(dest, left, AsmOperand(left, lsr, 31));
      __ asr_32(dest, dest, 1);
    } else if (c != (int) 0x80000000) {
      int power = log2_intptr(c);
      __ asr_32(Rtemp, left, 31);
      __ add_32(dest, left, AsmOperand(Rtemp, lsr, 32-power)); // dest = left + (left < 0 ? 2^power - 1 : 0);
      __ asr_32(dest, dest, power);                            // dest = dest >>> power;
    } else {
      // x/0x80000000 is a special case, since dividend is a power of two, but is negative.
      // The only possible result values are 0 and 1, with 1 only for dividend == divisor == 0x80000000.
      __ cmp_32(left, c);
#ifdef AARCH64
      __ cset(dest, eq);
#else
      __ mov(dest, 0, ne);
      __ mov(dest, 1, eq);
#endif // AARCH64
    }
  } else {
#ifdef AARCH64
    Register left  = op->in_opr1()->as_pointer_register();
    Register right = op->in_opr2()->as_pointer_register();
    Register dest  = op->result_opr()->as_pointer_register();

    switch (op->code()) {
      case lir_idiv:
        if (is_32) {
          __ sdiv_w(dest, left, right);
        } else {
          __ sdiv(dest, left, right);
        }
        break;
      case lir_irem: {
        Register tmp = op->in_opr3()->as_pointer_register();
        assert_different_registers(left, tmp);
        assert_different_registers(right, tmp);
        if (is_32) {
          __ sdiv_w(tmp, left, right);
          __ msub_w(dest, right, tmp, left);
        } else {
          __ sdiv(tmp, left, right);
          __ msub(dest, right, tmp, left);
        }
        break;
      }
      default:
        ShouldNotReachHere();
    }
#else
    assert(op->code() == lir_idiv || op->code() == lir_irem, "unexpected op3");
    __ call(StubRoutines::Arm::idiv_irem_entry(), relocInfo::runtime_call_type);
    add_debug_info_for_div0_here(op->info());
#endif // AARCH64
  }
}


void LIR_Assembler::emit_opBranch(LIR_OpBranch* op) {
#ifdef ASSERT
  assert(op->block() == NULL || op->block()->label() == op->label(), "wrong label");
  if (op->block() != NULL)  _branch_target_blocks.append(op->block());
  if (op->ublock() != NULL) _branch_target_blocks.append(op->ublock());
  assert(op->info() == NULL, "CodeEmitInfo?");
#endif // ASSERT

#ifdef __SOFTFP__
  assert (op->code() != lir_cond_float_branch, "this should be impossible");
#else
  if (op->code() == lir_cond_float_branch) {
#ifndef AARCH64
    __ fmstat();
#endif // !AARCH64
    __ b(*(op->ublock()->label()), vs);
  }
#endif // __SOFTFP__

  AsmCondition acond = al;
  switch (op->cond()) {
    case lir_cond_equal:        acond = eq; break;
    case lir_cond_notEqual:     acond = ne; break;
    case lir_cond_less:         acond = lt; break;
    case lir_cond_lessEqual:    acond = le; break;
    case lir_cond_greaterEqual: acond = ge; break;
    case lir_cond_greater:      acond = gt; break;
    case lir_cond_aboveEqual:   acond = hs; break;
    case lir_cond_belowEqual:   acond = ls; break;
    default: assert(op->cond() == lir_cond_always, "must be");
  }
  __ b(*(op->label()), acond);
}


void LIR_Assembler::emit_opConvert(LIR_OpConvert* op) {
  LIR_Opr src  = op->in_opr();
  LIR_Opr dest = op->result_opr();

  switch (op->bytecode()) {
    case Bytecodes::_i2l:
#ifdef AARCH64
      __ sign_extend(dest->as_register_lo(), src->as_register(), 32);
#else
      move_regs(src->as_register(), dest->as_register_lo());
      __ mov(dest->as_register_hi(), AsmOperand(src->as_register(), asr, 31));
#endif // AARCH64
      break;
    case Bytecodes::_l2i:
      move_regs(src->as_register_lo(), dest->as_register());
      break;
    case Bytecodes::_i2b:
      __ sign_extend(dest->as_register(), src->as_register(), 8);
      break;
    case Bytecodes::_i2s:
      __ sign_extend(dest->as_register(), src->as_register(), 16);
      break;
    case Bytecodes::_i2c:
      __ zero_extend(dest->as_register(), src->as_register(), 16);
      break;
    case Bytecodes::_f2d:
      __ convert_f2d(dest->as_double_reg(), src->as_float_reg());
      break;
    case Bytecodes::_d2f:
      __ convert_d2f(dest->as_float_reg(), src->as_double_reg());
      break;
    case Bytecodes::_i2f:
#ifdef AARCH64
      __ scvtf_sw(dest->as_float_reg(), src->as_register());
#else
      __ fmsr(Stemp, src->as_register());
      __ fsitos(dest->as_float_reg(), Stemp);
#endif // AARCH64
      break;
    case Bytecodes::_i2d:
#ifdef AARCH64
      __ scvtf_dw(dest->as_double_reg(), src->as_register());
#else
      __ fmsr(Stemp, src->as_register());
      __ fsitod(dest->as_double_reg(), Stemp);
#endif // AARCH64
      break;
    case Bytecodes::_f2i:
#ifdef AARCH64
      __ fcvtzs_ws(dest->as_register(), src->as_float_reg());
#else
      __ ftosizs(Stemp, src->as_float_reg());
      __ fmrs(dest->as_register(), Stemp);
#endif // AARCH64
      break;
    case Bytecodes::_d2i:
#ifdef AARCH64
      __ fcvtzs_wd(dest->as_register(), src->as_double_reg());
#else
      __ ftosizd(Stemp, src->as_double_reg());
      __ fmrs(dest->as_register(), Stemp);
#endif // AARCH64
      break;
#ifdef AARCH64
    case Bytecodes::_l2f:
      __ scvtf_sx(dest->as_float_reg(), src->as_register_lo());
      break;
    case Bytecodes::_l2d:
      __ scvtf_dx(dest->as_double_reg(), src->as_register_lo());
      break;
    case Bytecodes::_f2l:
      __ fcvtzs_xs(dest->as_register_lo(), src->as_float_reg());
      break;
    case Bytecodes::_d2l:
      __ fcvtzs_xd(dest->as_register_lo(), src->as_double_reg());
      break;
#endif // AARCH64
    default:
      ShouldNotReachHere();
  }
}


void LIR_Assembler::emit_alloc_obj(LIR_OpAllocObj* op) {
  if (op->init_check()) {
    Register tmp = op->tmp1()->as_register();
    __ ldrb(tmp, Address(op->klass()->as_register(), InstanceKlass::init_state_offset()));
    add_debug_info_for_null_check_here(op->stub()->info());
    __ cmp(tmp, InstanceKlass::fully_initialized);
    __ b(*op->stub()->entry(), ne);
  }
  __ allocate_object(op->obj()->as_register(),
                     op->tmp1()->as_register(),
                     op->tmp2()->as_register(),
                     op->tmp3()->as_register(),
                     op->header_size(),
                     op->object_size(),
                     op->klass()->as_register(),
                     *op->stub()->entry());
  __ bind(*op->stub()->continuation());
}

void LIR_Assembler::emit_alloc_array(LIR_OpAllocArray* op) {
  if (UseSlowPath ||
      (!UseFastNewObjectArray && (op->type() == T_OBJECT || op->type() == T_ARRAY)) ||
      (!UseFastNewTypeArray   && (op->type() != T_OBJECT && op->type() != T_ARRAY))) {
    __ b(*op->stub()->entry());
  } else {
    __ allocate_array(op->obj()->as_register(),
                      op->len()->as_register(),
                      op->tmp1()->as_register(),
                      op->tmp2()->as_register(),
                      op->tmp3()->as_register(),
                      arrayOopDesc::header_size(op->type()),
                      type2aelembytes(op->type()),
                      op->klass()->as_register(),
                      *op->stub()->entry());
  }
  __ bind(*op->stub()->continuation());
}

void LIR_Assembler::type_profile_helper(Register mdo, int mdo_offset_bias,
                                        ciMethodData *md, ciProfileData *data,
                                        Register recv, Register tmp1, Label* update_done) {
  assert_different_registers(mdo, recv, tmp1);
  uint i;
  for (i = 0; i < VirtualCallData::row_limit(); i++) {
    Label next_test;
    // See if the receiver is receiver[n].
    Address receiver_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_offset(i)) -
                          mdo_offset_bias);
    __ ldr(tmp1, receiver_addr);
    __ verify_klass_ptr(tmp1);
    __ cmp(recv, tmp1);
    __ b(next_test, ne);
    Address data_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_count_offset(i)) -
                      mdo_offset_bias);
    __ ldr(tmp1, data_addr);
    __ add(tmp1, tmp1, DataLayout::counter_increment);
    __ str(tmp1, data_addr);
    __ b(*update_done);
    __ bind(next_test);
  }

  // Didn't find receiver; find next empty slot and fill it in
  for (i = 0; i < VirtualCallData::row_limit(); i++) {
    Label next_test;
    Address recv_addr(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_offset(i)) -
                      mdo_offset_bias);
    __ ldr(tmp1, recv_addr);
    __ cbnz(tmp1, next_test);
    __ str(recv, recv_addr);
    __ mov(tmp1, DataLayout::counter_increment);
    __ str(tmp1, Address(mdo, md->byte_offset_of_slot(data, ReceiverTypeData::receiver_count_offset(i)) -
                         mdo_offset_bias));
    __ b(*update_done);
    __ bind(next_test);
  }
}

void LIR_Assembler::setup_md_access(ciMethod* method, int bci,
                                    ciMethodData*& md, ciProfileData*& data, int& mdo_offset_bias) {
  md = method->method_data_or_null();
  assert(md != NULL, "Sanity");
  data = md->bci_to_data(bci);
  assert(data != NULL,       "need data for checkcast");
  assert(data->is_ReceiverTypeData(), "need ReceiverTypeData for type check");
  if (md->byte_offset_of_slot(data, DataLayout::header_offset()) + data->size_in_bytes() >= 4096) {
    // The offset is large so bias the mdo by the base of the slot so
    // that the ldr can use an immediate offset to reference the slots of the data
    mdo_offset_bias = md->byte_offset_of_slot(data, DataLayout::header_offset());
  }
}

// On 32-bit ARM, code before this helper should test obj for null (ZF should be set if obj is null).
void LIR_Assembler::typecheck_profile_helper1(ciMethod* method, int bci,
                                              ciMethodData*& md, ciProfileData*& data, int& mdo_offset_bias,
                                              Register obj, Register mdo, Register data_val, Label* obj_is_null) {
  assert(method != NULL, "Should have method");
  assert_different_registers(obj, mdo, data_val);
  setup_md_access(method, bci, md, data, mdo_offset_bias);
  Label not_null;
#ifdef AARCH64
  __ cbnz(obj, not_null);
#else
  __ b(not_null, ne);
#endif // AARCH64
  __ mov_metadata(mdo, md->constant_encoding());
  if (mdo_offset_bias > 0) {
    __ mov_slow(data_val, mdo_offset_bias);
    __ add(mdo, mdo, data_val);
  }
  Address flags_addr(mdo, md->byte_offset_of_slot(data, DataLayout::flags_offset()) - mdo_offset_bias);
  __ ldrb(data_val, flags_addr);
  __ orr(data_val, data_val, (uint)BitData::null_seen_byte_constant());
  __ strb(data_val, flags_addr);
  __ b(*obj_is_null);
  __ bind(not_null);
}

void LIR_Assembler::typecheck_profile_helper2(ciMethodData* md, ciProfileData* data, int mdo_offset_bias,
                                              Register mdo, Register recv, Register value, Register tmp1,
                                              Label* profile_cast_success, Label* profile_cast_failure,
                                              Label* success, Label* failure) {
  assert_different_registers(mdo, value, tmp1);
  __ bind(*profile_cast_success);
  __ mov_metadata(mdo, md->constant_encoding());
  if (mdo_offset_bias > 0) {
    __ mov_slow(tmp1, mdo_offset_bias);
    __ add(mdo, mdo, tmp1);
  }
  __ load_klass(recv, value);
  type_profile_helper(mdo, mdo_offset_bias, md, data, recv, tmp1, success);
  __ b(*success);
  // Cast failure case
  __ bind(*profile_cast_failure);
  __ mov_metadata(mdo, md->constant_encoding());
  if (mdo_offset_bias > 0) {
    __ mov_slow(tmp1, mdo_offset_bias);
    __ add(mdo, mdo, tmp1);
  }
  Address data_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()) - mdo_offset_bias);
  __ ldr(tmp1, data_addr);
  __ sub(tmp1, tmp1, DataLayout::counter_increment);
  __ str(tmp1, data_addr);
  __ b(*failure);
}

// Sets `res` to true, if `cond` holds. On AArch64 also sets `res` to false if `cond` does not hold.
static void set_instanceof_result(MacroAssembler* _masm, Register res, AsmCondition cond) {
#ifdef AARCH64
  __ cset(res, cond);
#else
  __ mov(res, 1, cond);
#endif // AARCH64
}


void LIR_Assembler::emit_opTypeCheck(LIR_OpTypeCheck* op) {
  // TODO: ARM - can be more effective with one more register
  switch (op->code()) {
    case lir_store_check: {
      CodeStub* stub = op->stub();
      Register value = op->object()->as_register();
      Register array = op->array()->as_register();
      Register klass_RInfo = op->tmp1()->as_register();
      Register k_RInfo = op->tmp2()->as_register();
      assert_different_registers(klass_RInfo, k_RInfo, Rtemp);
      if (op->should_profile()) {
        assert_different_registers(value, klass_RInfo, k_RInfo, Rtemp);
      }

      // check if it needs to be profiled
      ciMethodData* md;
      ciProfileData* data;
      int mdo_offset_bias = 0;
      Label profile_cast_success, profile_cast_failure, done;
      Label *success_target = op->should_profile() ? &profile_cast_success : &done;
      Label *failure_target = op->should_profile() ? &profile_cast_failure : stub->entry();

      if (op->should_profile()) {
#ifndef AARCH64
        __ cmp(value, 0);
#endif // !AARCH64
        typecheck_profile_helper1(op->profiled_method(), op->profiled_bci(), md, data, mdo_offset_bias, value, k_RInfo, Rtemp, &done);
      } else {
        __ cbz(value, done);
      }
      assert_different_registers(k_RInfo, value);
      add_debug_info_for_null_check_here(op->info_for_exception());
      __ load_klass(k_RInfo, array);
      __ load_klass(klass_RInfo, value);
      __ ldr(k_RInfo, Address(k_RInfo, ObjArrayKlass::element_klass_offset()));
      __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
      // check for immediate positive hit
      __ ldr(Rtemp, Address(klass_RInfo, Rtemp));
      __ cmp(klass_RInfo, k_RInfo);
      __ cond_cmp(Rtemp, k_RInfo, ne);
      __ b(*success_target, eq);
      // check for immediate negative hit
      __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
      __ cmp(Rtemp, in_bytes(Klass::secondary_super_cache_offset()));
      __ b(*failure_target, ne);
      // slow case
      assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
      __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
      __ cbz(R0, *failure_target);
      if (op->should_profile()) {
        Register mdo  = klass_RInfo, recv = k_RInfo, tmp1 = Rtemp;
        if (mdo == value) {
          mdo = k_RInfo;
          recv = klass_RInfo;
        }
        typecheck_profile_helper2(md, data, mdo_offset_bias, mdo, recv, value, tmp1,
                                  &profile_cast_success, &profile_cast_failure,
                                  &done, stub->entry());
      }
      __ bind(done);
      break;
    }

    case lir_checkcast: {
      CodeStub* stub = op->stub();
      Register obj = op->object()->as_register();
      Register res = op->result_opr()->as_register();
      Register klass_RInfo = op->tmp1()->as_register();
      Register k_RInfo = op->tmp2()->as_register();
      ciKlass* k = op->klass();
      assert_different_registers(res, k_RInfo, klass_RInfo, Rtemp);

      if (stub->is_simple_exception_stub()) {
      // TODO: ARM - Late binding is used to prevent confusion of register allocator
      assert(stub->is_exception_throw_stub(), "must be");
      ((SimpleExceptionStub*)stub)->set_obj(op->result_opr());
      }
      ciMethodData* md;
      ciProfileData* data;
      int mdo_offset_bias = 0;

      Label done;

      Label profile_cast_failure, profile_cast_success;
      Label *failure_target = op->should_profile() ? &profile_cast_failure : op->stub()->entry();
      Label *success_target = op->should_profile() ? &profile_cast_success : &done;

#ifdef AARCH64
      move_regs(obj, res);
      if (op->should_profile()) {
        typecheck_profile_helper1(op->profiled_method(), op->profiled_bci(), md, data, mdo_offset_bias, res, klass_RInfo, Rtemp, &done);
      } else {
        __ cbz(obj, done);
      }
      if (k->is_loaded()) {
        __ mov_metadata(k_RInfo, k->constant_encoding());
      } else {
        if (res != obj) {
          op->info_for_patch()->add_register_oop(FrameMap::as_oop_opr(res));
        }
        klass2reg_with_patching(k_RInfo, op->info_for_patch());
      }
      __ load_klass(klass_RInfo, res);

      if (op->fast_check()) {
        __ cmp(klass_RInfo, k_RInfo);
        __ b(*failure_target, ne);
      } else if (k->is_loaded()) {
        __ ldr(Rtemp, Address(klass_RInfo, k->super_check_offset()));
        if (in_bytes(Klass::secondary_super_cache_offset()) != (int) k->super_check_offset()) {
          __ cmp(Rtemp, k_RInfo);
          __ b(*failure_target, ne);
        } else {
          __ cmp(klass_RInfo, k_RInfo);
          __ cond_cmp(Rtemp, k_RInfo, ne);
          __ b(*success_target, eq);
          assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
          __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
          __ cbz(R0, *failure_target);
        }
      } else {
        __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        // check for immediate positive hit
        __ ldr(Rtemp, Address(klass_RInfo, Rtemp));
        __ cmp(klass_RInfo, k_RInfo);
        __ cond_cmp(Rtemp, k_RInfo, ne);
        __ b(*success_target, eq);
        // check for immediate negative hit
        __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        __ cmp(Rtemp, in_bytes(Klass::secondary_super_cache_offset()));
        __ b(*failure_target, ne);
        // slow case
        assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
        __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
        __ cbz(R0, *failure_target);
      }

#else // AARCH64

      __ movs(res, obj);
      if (op->should_profile()) {
        typecheck_profile_helper1(op->profiled_method(), op->profiled_bci(), md, data, mdo_offset_bias, res, klass_RInfo, Rtemp, &done);
      } else {
        __ b(done, eq);
      }
      if (k->is_loaded()) {
        __ mov_metadata(k_RInfo, k->constant_encoding());
      } else if (k_RInfo != obj) {
        klass2reg_with_patching(k_RInfo, op->info_for_patch());
        __ movs(res, obj);
      } else {
        // Patching doesn't update "res" register after GC, so do patching first
        klass2reg_with_patching(Rtemp, op->info_for_patch());
        __ movs(res, obj);
        __ mov(k_RInfo, Rtemp);
      }
      __ load_klass(klass_RInfo, res, ne);

      if (op->fast_check()) {
        __ cmp(klass_RInfo, k_RInfo, ne);
        __ b(*failure_target, ne);
      } else if (k->is_loaded()) {
        __ b(*success_target, eq);
        __ ldr(Rtemp, Address(klass_RInfo, k->super_check_offset()));
        if (in_bytes(Klass::secondary_super_cache_offset()) != (int) k->super_check_offset()) {
          __ cmp(Rtemp, k_RInfo);
          __ b(*failure_target, ne);
        } else {
          __ cmp(klass_RInfo, k_RInfo);
          __ cmp(Rtemp, k_RInfo, ne);
          __ b(*success_target, eq);
          assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
          __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
          __ cbz(R0, *failure_target);
        }
      } else {
        __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        __ b(*success_target, eq);
        // check for immediate positive hit
        __ ldr(Rtemp, Address(klass_RInfo, Rtemp));
        __ cmp(klass_RInfo, k_RInfo);
        __ cmp(Rtemp, k_RInfo, ne);
        __ b(*success_target, eq);
        // check for immediate negative hit
        __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        __ cmp(Rtemp, in_bytes(Klass::secondary_super_cache_offset()));
        __ b(*failure_target, ne);
        // slow case
        assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
        __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
        __ cbz(R0, *failure_target);
      }
#endif // AARCH64

      if (op->should_profile()) {
        Register mdo  = klass_RInfo, recv = k_RInfo, tmp1 = Rtemp;
        typecheck_profile_helper2(md, data, mdo_offset_bias, mdo, recv, res, tmp1,
                                  &profile_cast_success, &profile_cast_failure,
                                  &done, stub->entry());
      }
      __ bind(done);
      break;
    }

    case lir_instanceof: {
      Register obj = op->object()->as_register();
      Register res = op->result_opr()->as_register();
      Register klass_RInfo = op->tmp1()->as_register();
      Register k_RInfo = op->tmp2()->as_register();
      ciKlass* k = op->klass();
      assert_different_registers(res, klass_RInfo, k_RInfo, Rtemp);

      ciMethodData* md;
      ciProfileData* data;
      int mdo_offset_bias = 0;

      Label done;

      Label profile_cast_failure, profile_cast_success;
      Label *failure_target = op->should_profile() ? &profile_cast_failure : &done;
      Label *success_target = op->should_profile() ? &profile_cast_success : &done;

#ifdef AARCH64
      move_regs(obj, res);
#else
      __ movs(res, obj);
#endif // AARCH64

      if (op->should_profile()) {
        typecheck_profile_helper1(op->profiled_method(), op->profiled_bci(), md, data, mdo_offset_bias, res, klass_RInfo, Rtemp, &done);
      } else {
#ifdef AARCH64
        __ cbz(obj, done); // If obj == NULL, res is false
#else
        __ b(done, eq);
#endif // AARCH64
      }

      if (k->is_loaded()) {
        __ mov_metadata(k_RInfo, k->constant_encoding());
      } else {
        op->info_for_patch()->add_register_oop(FrameMap::as_oop_opr(res));
        klass2reg_with_patching(k_RInfo, op->info_for_patch());
      }
      __ load_klass(klass_RInfo, res);

#ifndef AARCH64
      if (!op->should_profile()) {
        __ mov(res, 0);
      }
#endif // !AARCH64

      if (op->fast_check()) {
        __ cmp(klass_RInfo, k_RInfo);
        if (!op->should_profile()) {
          set_instanceof_result(_masm, res, eq);
        } else {
          __ b(profile_cast_failure, ne);
        }
      } else if (k->is_loaded()) {
        __ ldr(Rtemp, Address(klass_RInfo, k->super_check_offset()));
        if (in_bytes(Klass::secondary_super_cache_offset()) != (int) k->super_check_offset()) {
          __ cmp(Rtemp, k_RInfo);
          if (!op->should_profile()) {
            set_instanceof_result(_masm, res, eq);
          } else {
            __ b(profile_cast_failure, ne);
          }
        } else {
          __ cmp(klass_RInfo, k_RInfo);
          __ cond_cmp(Rtemp, k_RInfo, ne);
          if (!op->should_profile()) {
            set_instanceof_result(_masm, res, eq);
          }
          __ b(*success_target, eq);
          assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
          __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
          if (!op->should_profile()) {
            move_regs(R0, res);
          } else {
            __ cbz(R0, *failure_target);
          }
        }
      } else {
        __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        // check for immediate positive hit
        __ cmp(klass_RInfo, k_RInfo);
        if (!op->should_profile()) {
#ifdef AARCH64
          // TODO-AARCH64 check if separate conditional branch is more efficient than ldr+cond_cmp
          __ ldr(res, Address(klass_RInfo, Rtemp));
#else
          __ ldr(res, Address(klass_RInfo, Rtemp), ne);
#endif // AARCH64
          __ cond_cmp(res, k_RInfo, ne);
          set_instanceof_result(_masm, res, eq);
        } else {
#ifdef AARCH64
          // TODO-AARCH64 check if separate conditional branch is more efficient than ldr+cond_cmp
          __ ldr(Rtemp, Address(klass_RInfo, Rtemp));
#else
          __ ldr(Rtemp, Address(klass_RInfo, Rtemp), ne);
#endif // AARCH64
          __ cond_cmp(Rtemp, k_RInfo, ne);
        }
        __ b(*success_target, eq);
        // check for immediate negative hit
        if (op->should_profile()) {
          __ ldr_u32(Rtemp, Address(k_RInfo, Klass::super_check_offset_offset()));
        }
        __ cmp(Rtemp, in_bytes(Klass::secondary_super_cache_offset()));
        if (!op->should_profile()) {
#ifdef AARCH64
          __ mov(res, 0);
#else
          __ mov(res, 0, ne);
#endif // AARCH64
        }
        __ b(*failure_target, ne);
        // slow case
        assert(klass_RInfo == R0 && k_RInfo == R1, "runtime call setup");
        __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type);
        if (!op->should_profile()) {
          move_regs(R0, res);
        }
        if (op->should_profile()) {
          __ cbz(R0, *failure_target);
        }
      }

      if (op->should_profile()) {
        Label done_ok, done_failure;
        Register mdo  = klass_RInfo, recv = k_RInfo, tmp1 = Rtemp;
        typecheck_profile_helper2(md, data, mdo_offset_bias, mdo, recv, res, tmp1,
                                  &profile_cast_success, &profile_cast_failure,
                                  &done_ok, &done_failure);
        __ bind(done_failure);
        __ mov(res, 0);
        __ b(done);
        __ bind(done_ok);
        __ mov(res, 1);
      }
      __ bind(done);
      break;
    }
    default:
      ShouldNotReachHere();
  }
}


void LIR_Assembler::emit_compare_and_swap(LIR_OpCompareAndSwap* op) {
  //   if (*addr == cmpval) {
  //     *addr = newval;
  //     dest = 1;
  //   } else {
  //     dest = 0;
  //   }
#ifdef AARCH64
  Label retry, done;
  Register addr = op->addr()->as_pointer_register();
  Register cmpval = op->cmp_value()->as_pointer_register();
  Register newval = op->new_value()->as_pointer_register();
  Register dest = op->result_opr()->as_pointer_register();
  assert_different_registers(dest, addr, cmpval, newval, Rtemp);

  if (UseCompressedOops && op->code() == lir_cas_obj) {
    Register tmp1 = op->tmp1()->as_pointer_register();
    Register tmp2 = op->tmp2()->as_pointer_register();
    assert_different_registers(dest, addr, cmpval, newval, tmp1, tmp2, Rtemp);
    __ encode_heap_oop(tmp1, cmpval); cmpval = tmp1;
    __ encode_heap_oop(tmp2, newval); newval = tmp2;
  }

  __ mov(dest, ZR);
  __ bind(retry);
  if (((op->code() == lir_cas_obj) && !UseCompressedOops) || op->code() == lir_cas_long) {
    __ ldaxr(Rtemp, addr);
    __ cmp(Rtemp, cmpval);
    __ b(done, ne);
    __ stlxr(Rtemp, newval, addr);
  } else if (((op->code() == lir_cas_obj) && UseCompressedOops) || op->code() == lir_cas_int) {
    __ ldaxr_w(Rtemp, addr);
    __ cmp_w(Rtemp, cmpval);
    __ b(done, ne);
    __ stlxr_w(Rtemp, newval, addr);
  } else {
    ShouldNotReachHere();
  }
  __ cbnz_w(Rtemp, retry);
  __ mov(dest, 1);
  __ bind(done);
#else
  // FIXME: membar_release
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreStore | MacroAssembler::LoadStore), Rtemp);
  if (op->code() == lir_cas_int || op->code() == lir_cas_obj) {
    Register addr = op->addr()->as_register();
    Register cmpval = op->cmp_value()->as_register();
    Register newval = op->new_value()->as_register();
    Register dest = op->result_opr()->as_register();
    assert_different_registers(dest, addr, cmpval, newval, Rtemp);

    __ atomic_cas_bool(cmpval, newval, addr, 0, Rtemp); // Rtemp free by default at C1 LIR layer
    __ mov(dest, 1, eq);
    __ mov(dest, 0, ne);
  } else if (op->code() == lir_cas_long) {
    assert(VM_Version::supports_cx8(), "wrong machine");
    Register addr = op->addr()->as_pointer_register();
    Register cmp_value_lo = op->cmp_value()->as_register_lo();
    Register cmp_value_hi = op->cmp_value()->as_register_hi();
    Register new_value_lo = op->new_value()->as_register_lo();
    Register new_value_hi = op->new_value()->as_register_hi();
    Register dest = op->result_opr()->as_register();
    Register tmp_lo = op->tmp1()->as_register_lo();
    Register tmp_hi = op->tmp1()->as_register_hi();

    assert_different_registers(tmp_lo, tmp_hi, cmp_value_lo, cmp_value_hi, dest, new_value_lo, new_value_hi, addr);
    assert(tmp_hi->encoding() == tmp_lo->encoding() + 1, "non aligned register pair");
    assert(new_value_hi->encoding() == new_value_lo->encoding() + 1, "non aligned register pair");
    assert((tmp_lo->encoding() & 0x1) == 0, "misaligned register pair");
    assert((new_value_lo->encoding() & 0x1) == 0, "misaligned register pair");
    __ atomic_cas64(tmp_lo, tmp_hi, dest, cmp_value_lo, cmp_value_hi,
                    new_value_lo, new_value_hi, addr, 0);
  } else {
    Unimplemented();
  }
#endif // AARCH64
  // FIXME: is full membar really needed instead of just membar_acquire?
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad | MacroAssembler::StoreStore), Rtemp);
}


void LIR_Assembler::cmove(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Opr result, BasicType type) {
  AsmCondition acond = al;
  AsmCondition ncond = nv;
  if (opr1 != opr2) {
    switch (condition) {
      case lir_cond_equal:        acond = eq; ncond = ne; break;
      case lir_cond_notEqual:     acond = ne; ncond = eq; break;
      case lir_cond_less:         acond = lt; ncond = ge; break;
      case lir_cond_lessEqual:    acond = le; ncond = gt; break;
      case lir_cond_greaterEqual: acond = ge; ncond = lt; break;
      case lir_cond_greater:      acond = gt; ncond = le; break;
      case lir_cond_aboveEqual:   acond = hs; ncond = lo; break;
      case lir_cond_belowEqual:   acond = ls; ncond = hi; break;
      default: ShouldNotReachHere();
    }
  }

#ifdef AARCH64

  // TODO-AARCH64 implement it more efficiently

  if (opr1->is_register()) {
    reg2reg(opr1, result);
  } else if (opr1->is_stack()) {
    stack2reg(opr1, result, result->type());
  } else if (opr1->is_constant()) {
    const2reg(opr1, result, lir_patch_none, NULL);
  } else {
    ShouldNotReachHere();
  }

  Label skip;
  __ b(skip, acond);

  if (opr2->is_register()) {
    reg2reg(opr2, result);
  } else if (opr2->is_stack()) {
    stack2reg(opr2, result, result->type());
  } else if (opr2->is_constant()) {
    const2reg(opr2, result, lir_patch_none, NULL);
  } else {
    ShouldNotReachHere();
  }

  __ bind(skip);

#else
  for (;;) {                         // two iterations only
    if (opr1 == result) {
      // do nothing
    } else if (opr1->is_single_cpu()) {
      __ mov(result->as_register(), opr1->as_register(), acond);
    } else if (opr1->is_double_cpu()) {
      __ long_move(result->as_register_lo(), result->as_register_hi(),
                   opr1->as_register_lo(), opr1->as_register_hi(), acond);
    } else if (opr1->is_single_stack()) {
      __ ldr(result->as_register(), frame_map()->address_for_slot(opr1->single_stack_ix()), acond);
    } else if (opr1->is_double_stack()) {
      __ ldr(result->as_register_lo(),
             frame_map()->address_for_slot(opr1->double_stack_ix(), lo_word_offset_in_bytes), acond);
      __ ldr(result->as_register_hi(),
             frame_map()->address_for_slot(opr1->double_stack_ix(), hi_word_offset_in_bytes), acond);
    } else if (opr1->is_illegal()) {
      // do nothing: this part of the cmove has been optimized away in the peephole optimizer
    } else {
      assert(opr1->is_constant(), "must be");
      LIR_Const* c = opr1->as_constant_ptr();

      switch (c->type()) {
        case T_INT:
          __ mov_slow(result->as_register(), c->as_jint(), acond);
          break;
        case T_LONG:
          __ mov_slow(result->as_register_lo(), c->as_jint_lo(), acond);
          __ mov_slow(result->as_register_hi(), c->as_jint_hi(), acond);
          break;
        case T_OBJECT:
          __ mov_oop(result->as_register(), c->as_jobject(), 0, acond);
          break;
        case T_FLOAT:
#ifdef __SOFTFP__
          // not generated now.
          __ mov_slow(result->as_register(), c->as_jint(), acond);
#else
          __ mov_float(result->as_float_reg(), c->as_jfloat(), acond);
#endif // __SOFTFP__
          break;
        case T_DOUBLE:
#ifdef __SOFTFP__
          // not generated now.
          __ mov_slow(result->as_register_lo(), c->as_jint_lo(), acond);
          __ mov_slow(result->as_register_hi(), c->as_jint_hi(), acond);
#else
          __ mov_double(result->as_double_reg(), c->as_jdouble(), acond);
#endif // __SOFTFP__
          break;
        default:
          ShouldNotReachHere();
      }
    }

    // Negate the condition and repeat the algorithm with the second operand
    if (opr1 == opr2) { break; }
    opr1 = opr2;
    acond = ncond;
  }
#endif // AARCH64
}

#if defined(AARCH64) || defined(ASSERT)
static int reg_size(LIR_Opr op) {
  switch (op->type()) {
  case T_FLOAT:
  case T_INT:      return BytesPerInt;
  case T_LONG:
  case T_DOUBLE:   return BytesPerLong;
  case T_OBJECT:
  case T_ARRAY:
  case T_METADATA: return BytesPerWord;
  case T_ADDRESS:
  case T_ILLEGAL:  // fall through
  default: ShouldNotReachHere(); return -1;
  }
}
#endif

void LIR_Assembler::arith_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest, CodeEmitInfo* info, bool pop_fpu_stack) {
  assert(info == NULL, "unused on this code path");
  assert(dest->is_register(), "wrong items state");

  if (right->is_address()) {
    // special case for adding shifted/extended register
    const Register res = dest->as_pointer_register();
    const Register lreg = left->as_pointer_register();
    const LIR_Address* addr = right->as_address_ptr();

    assert(addr->base()->as_pointer_register() == lreg && addr->index()->is_register() && addr->disp() == 0, "must be");

    int scale = addr->scale();
    AsmShift shift = lsl;

#ifdef AARCH64
    bool is_index_extended = reg_size(addr->base()) > reg_size(addr->index());
    if (scale < 0) {
      scale = -scale;
      shift = lsr;
    }
    assert(shift == lsl || !is_index_extended, "could not have extend and right shift in one operand");
    assert(0 <= scale && scale <= 63, "scale is too large");

    if (is_index_extended) {
      assert(scale <= 4, "scale is too large for add with extended register");
      assert(addr->index()->is_single_cpu(), "should be");
      assert(addr->index()->type() == T_INT, "should be");
      assert(dest->is_double_cpu(), "should be");
      assert(code == lir_add, "special case of add with extended register");

      __ add(res, lreg, addr->index()->as_register(), ex_sxtw, scale);
      return;
    } else if (reg_size(dest) == BytesPerInt) {
      assert(reg_size(addr->base()) == reg_size(addr->index()), "should be");
      assert(reg_size(addr->base()) == reg_size(dest), "should be");

      AsmOperand operand(addr->index()->as_pointer_register(), shift, scale);
      switch (code) {
        case lir_add: __ add_32(res, lreg, operand); break;
        case lir_sub: __ sub_32(res, lreg, operand); break;
        default: ShouldNotReachHere();
      }
      return;
    }
#endif // AARCH64

    assert(reg_size(addr->base()) == reg_size(addr->index()), "should be");
    assert(reg_size(addr->base()) == reg_size(dest), "should be");
    assert(reg_size(dest) == wordSize, "should be");

    AsmOperand operand(addr->index()->as_pointer_register(), shift, scale);
    switch (code) {
      case lir_add: __ add(res, lreg, operand); break;
      case lir_sub: __ sub(res, lreg, operand); break;
      default: ShouldNotReachHere();
    }

#ifndef AARCH64
  } else if (left->is_address()) {
    assert(code == lir_sub && right->is_single_cpu(), "special case used by strength_reduce_multiply()");
    const LIR_Address* addr = left->as_address_ptr();
    const Register res = dest->as_register();
    const Register rreg = right->as_register();
    assert(addr->base()->as_register() == rreg && addr->index()->is_register() && addr->disp() == 0, "must be");
    __ rsb(res, rreg, AsmOperand(addr->index()->as_register(), lsl, addr->scale()));
#endif // !AARCH64

  } else if (dest->is_single_cpu()) {
    assert(left->is_single_cpu(), "unexpected left operand");
#ifdef AARCH64
    assert(dest->type() == T_INT, "unexpected dest type");
    assert(left->type() == T_INT, "unexpected left type");
    assert(right->type() == T_INT, "unexpected right type");
#endif // AARCH64

    const Register res = dest->as_register();
    const Register lreg = left->as_register();

    if (right->is_single_cpu()) {
      const Register rreg = right->as_register();
      switch (code) {
        case lir_add: __ add_32(res, lreg, rreg); break;
        case lir_sub: __ sub_32(res, lreg, rreg); break;
        case lir_mul: __ mul_32(res, lreg, rreg); break;
        default: ShouldNotReachHere();
      }
    } else {
      assert(right->is_constant(), "must be");
      const jint c = right->as_constant_ptr()->as_jint();
      if (!Assembler::is_arith_imm_in_range(c)) {
        BAILOUT("illegal arithmetic operand");
      }
      switch (code) {
        case lir_add: __ add_32(res, lreg, c); break;
        case lir_sub: __ sub_32(res, lreg, c); break;
        default: ShouldNotReachHere();
      }
    }

  } else if (dest->is_double_cpu()) {
#ifdef AARCH64
    assert(left->is_double_cpu() ||
           (left->is_single_cpu() && ((left->type() == T_OBJECT) || (left->type() == T_ARRAY) || (left->type() == T_ADDRESS))),
           "unexpected left operand");

    const Register res = dest->as_register_lo();
    const Register lreg = left->as_pointer_register();

    if (right->is_constant()) {
      assert(right->type() == T_LONG, "unexpected right type");
      assert((right->as_constant_ptr()->as_jlong() >> 24) == 0, "out of range");
      jint imm = (jint)right->as_constant_ptr()->as_jlong();
      switch (code) {
        case lir_add: __ add(res, lreg, imm); break;
        case lir_sub: __ sub(res, lreg, imm); break;
        default: ShouldNotReachHere();
      }
    } else {
      assert(right->is_double_cpu() ||
             (right->is_single_cpu() && ((right->type() == T_OBJECT) || (right->type() == T_ARRAY) || (right->type() == T_ADDRESS))),
             "unexpected right operand");
      const Register rreg = right->as_pointer_register();
      switch (code) {
        case lir_add: __ add(res, lreg, rreg); break;
        case lir_sub: __ sub(res, lreg, rreg); break;
        case lir_mul: __ mul(res, lreg, rreg); break;
        default: ShouldNotReachHere();
      }
    }
#else // AARCH64
    Register res_lo = dest->as_register_lo();
    Register res_hi = dest->as_register_hi();
    Register lreg_lo = left->as_register_lo();
    Register lreg_hi = left->as_register_hi();
    if (right->is_double_cpu()) {
      Register rreg_lo = right->as_register_lo();
      Register rreg_hi = right->as_register_hi();
      if (res_lo == lreg_hi || res_lo == rreg_hi) {
        res_lo = Rtemp;
      }
      switch (code) {
        case lir_add:
          __ adds(res_lo, lreg_lo, rreg_lo);
          __ adc(res_hi, lreg_hi, rreg_hi);
          break;
        case lir_sub:
          __ subs(res_lo, lreg_lo, rreg_lo);
          __ sbc(res_hi, lreg_hi, rreg_hi);
          break;
        default:
          ShouldNotReachHere();
      }
    } else {
      assert(right->is_constant(), "must be");
      assert((right->as_constant_ptr()->as_jlong() >> 32) == 0, "out of range");
      const jint c = (jint) right->as_constant_ptr()->as_jlong();
      if (res_lo == lreg_hi) {
        res_lo = Rtemp;
      }
      switch (code) {
        case lir_add:
          __ adds(res_lo, lreg_lo, c);
          __ adc(res_hi, lreg_hi, 0);
          break;
        case lir_sub:
          __ subs(res_lo, lreg_lo, c);
          __ sbc(res_hi, lreg_hi, 0);
          break;
        default:
          ShouldNotReachHere();
      }
    }
    move_regs(res_lo, dest->as_register_lo());
#endif // AARCH64

  } else if (dest->is_single_fpu()) {
    assert(left->is_single_fpu(), "must be");
    assert(right->is_single_fpu(), "must be");
    const FloatRegister res = dest->as_float_reg();
    const FloatRegister lreg = left->as_float_reg();
    const FloatRegister rreg = right->as_float_reg();
    switch (code) {
      case lir_add: __ add_float(res, lreg, rreg); break;
      case lir_sub: __ sub_float(res, lreg, rreg); break;
      case lir_mul_strictfp: // fall through
      case lir_mul: __ mul_float(res, lreg, rreg); break;
      case lir_div_strictfp: // fall through
      case lir_div: __ div_float(res, lreg, rreg); break;
      default: ShouldNotReachHere();
    }
  } else if (dest->is_double_fpu()) {
    assert(left->is_double_fpu(), "must be");
    assert(right->is_double_fpu(), "must be");
    const FloatRegister res = dest->as_double_reg();
    const FloatRegister lreg = left->as_double_reg();
    const FloatRegister rreg = right->as_double_reg();
    switch (code) {
      case lir_add: __ add_double(res, lreg, rreg); break;
      case lir_sub: __ sub_double(res, lreg, rreg); break;
      case lir_mul_strictfp: // fall through
      case lir_mul: __ mul_double(res, lreg, rreg); break;
      case lir_div_strictfp: // fall through
      case lir_div: __ div_double(res, lreg, rreg); break;
      default: ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::intrinsic_op(LIR_Code code, LIR_Opr value, LIR_Opr unused, LIR_Opr dest, LIR_Op* op) {
  switch (code) {
    case lir_abs:
      __ abs_double(dest->as_double_reg(), value->as_double_reg());
      break;
    case lir_sqrt:
      __ sqrt_double(dest->as_double_reg(), value->as_double_reg());
      break;
    default:
      ShouldNotReachHere();
  }
}


void LIR_Assembler::logic_op(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dest) {
  assert(dest->is_register(), "wrong items state");
  assert(left->is_register(), "wrong items state");

  if (dest->is_single_cpu()) {
#ifdef AARCH64
    assert (dest->type() == T_INT, "unexpected result type");
    assert (left->type() == T_INT, "unexpected left type");
    assert (right->type() == T_INT, "unexpected right type");
#endif // AARCH64

    const Register res = dest->as_register();
    const Register lreg = left->as_register();

    if (right->is_single_cpu()) {
      const Register rreg = right->as_register();
      switch (code) {
        case lir_logic_and: __ and_32(res, lreg, rreg); break;
        case lir_logic_or:  __ orr_32(res, lreg, rreg); break;
        case lir_logic_xor: __ eor_32(res, lreg, rreg); break;
        default: ShouldNotReachHere();
      }
    } else {
      assert(right->is_constant(), "must be");
      const uint c = (uint)right->as_constant_ptr()->as_jint();
      switch (code) {
        case lir_logic_and: __ and_32(res, lreg, c); break;
        case lir_logic_or:  __ orr_32(res, lreg, c); break;
        case lir_logic_xor: __ eor_32(res, lreg, c); break;
        default: ShouldNotReachHere();
      }
    }
  } else {
    assert(dest->is_double_cpu(), "should be");
    Register res_lo = dest->as_register_lo();

#ifdef AARCH64
    assert ((left->is_single_cpu() && left->is_oop_register()) || left->is_double_cpu(), "should be");
    const Register lreg_lo = left->as_pointer_register();
#else
    assert (dest->type() == T_LONG, "unexpected result type");
    assert (left->type() == T_LONG, "unexpected left type");
    assert (right->type() == T_LONG, "unexpected right type");

    const Register res_hi = dest->as_register_hi();
    const Register lreg_lo = left->as_register_lo();
    const Register lreg_hi = left->as_register_hi();
#endif // AARCH64

    if (right->is_register()) {
#ifdef AARCH64
      assert ((right->is_single_cpu() && right->is_oop_register()) || right->is_double_cpu(), "should be");
      const Register rreg_lo = right->as_pointer_register();
      switch (code) {
        case lir_logic_and: __ andr(res_lo, lreg_lo, rreg_lo); break;
        case lir_logic_or:  __ orr (res_lo, lreg_lo, rreg_lo); break;
        case lir_logic_xor: __ eor (res_lo, lreg_lo, rreg_lo); break;
        default: ShouldNotReachHere();
      }
#else
      const Register rreg_lo = right->as_register_lo();
      const Register rreg_hi = right->as_register_hi();
      if (res_lo == lreg_hi || res_lo == rreg_hi) {
        res_lo = Rtemp; // Temp register helps to avoid overlap between result and input
      }
      switch (code) {
        case lir_logic_and:
          __ andr(res_lo, lreg_lo, rreg_lo);
          __ andr(res_hi, lreg_hi, rreg_hi);
          break;
        case lir_logic_or:
          __ orr(res_lo, lreg_lo, rreg_lo);
          __ orr(res_hi, lreg_hi, rreg_hi);
          break;
        case lir_logic_xor:
          __ eor(res_lo, lreg_lo, rreg_lo);
          __ eor(res_hi, lreg_hi, rreg_hi);
          break;
        default:
          ShouldNotReachHere();
      }
      move_regs(res_lo, dest->as_register_lo());
#endif // AARCH64
    } else {
      assert(right->is_constant(), "must be");
#ifdef AARCH64
      const julong c = (julong)right->as_constant_ptr()->as_jlong();
      Assembler::LogicalImmediate imm(c, false);
      if (imm.is_encoded()) {
        switch (code) {
          case lir_logic_and: __ andr(res_lo, lreg_lo, imm); break;
          case lir_logic_or:  __ orr (res_lo, lreg_lo, imm); break;
          case lir_logic_xor: __ eor (res_lo, lreg_lo, imm); break;
          default: ShouldNotReachHere();
        }
      } else {
        BAILOUT("64 bit constant cannot be inlined");
      }
#else
      const jint c_lo = (jint) right->as_constant_ptr()->as_jlong();
      const jint c_hi = (jint) (right->as_constant_ptr()->as_jlong() >> 32);
      // Case for logic_or from do_ClassIDIntrinsic()
      if (c_hi == 0 && AsmOperand::is_rotated_imm(c_lo)) {
        switch (code) {
          case lir_logic_and:
            __ andr(res_lo, lreg_lo, c_lo);
            __ mov(res_hi, 0);
            break;
          case lir_logic_or:
            __ orr(res_lo, lreg_lo, c_lo);
            break;
          case lir_logic_xor:
            __ eor(res_lo, lreg_lo, c_lo);
            break;
        default:
          ShouldNotReachHere();
        }
      } else if (code == lir_logic_and &&
                 c_hi == -1 &&
                 (AsmOperand::is_rotated_imm(c_lo) ||
                  AsmOperand::is_rotated_imm(~c_lo))) {
        // Another case which handles logic_and from do_ClassIDIntrinsic()
        if (AsmOperand::is_rotated_imm(c_lo)) {
          __ andr(res_lo, lreg_lo, c_lo);
        } else {
          __ bic(res_lo, lreg_lo, ~c_lo);
        }
        if (res_hi != lreg_hi) {
          __ mov(res_hi, lreg_hi);
        }
      } else {
        BAILOUT("64 bit constant cannot be inlined");
      }
#endif // AARCH64
    }
  }
}


#ifdef AARCH64

void LIR_Assembler::long_compare_helper(LIR_Opr opr1, LIR_Opr opr2) {
  assert(opr1->is_double_cpu(), "should be");
  Register x = opr1->as_register_lo();

  if (opr2->is_double_cpu()) {
    Register y = opr2->as_register_lo();
    __ cmp(x, y);

  } else {
    assert(opr2->is_constant(), "should be");
    assert(opr2->as_constant_ptr()->type() == T_LONG, "long constant expected");
    jlong c = opr2->as_jlong();
    assert(((c >> 31) == 0) || ((c >> 31) == -1), "immediate is out of range");
    if (c >= 0) {
      __ cmp(x, (jint)c);
    } else {
      __ cmn(x, (jint)(-c));
    }
  }
}

#endif // AARCH64

void LIR_Assembler::comp_op(LIR_Condition condition, LIR_Opr opr1, LIR_Opr opr2, LIR_Op2* op) {
  if (opr1->is_single_cpu()) {
    if (opr2->is_constant()) {
      switch (opr2->as_constant_ptr()->type()) {
        case T_INT: {
          const jint c = opr2->as_constant_ptr()->as_jint();
          if (Assembler::is_arith_imm_in_range(c)) {
            __ cmp_32(opr1->as_register(), c);
          } else if (Assembler::is_arith_imm_in_range(-c)) {
            __ cmn_32(opr1->as_register(), -c);
          } else {
            // This can happen when compiling lookupswitch
            __ mov_slow(Rtemp, c);
            __ cmp_32(opr1->as_register(), Rtemp);
          }
          break;
        }
        case T_OBJECT:
          assert(opr2->as_constant_ptr()->as_jobject() == NULL, "cannot handle otherwise");
          __ cmp(opr1->as_register(), 0);
          break;
        default:
          ShouldNotReachHere();
      }
    } else if (opr2->is_single_cpu()) {
      if (opr1->type() == T_OBJECT || opr1->type() == T_ARRAY || opr1->type() == T_METADATA || opr1->type() == T_ADDRESS) {
        assert(opr2->type() == T_OBJECT || opr2->type() == T_ARRAY || opr2->type() == T_METADATA || opr2->type() == T_ADDRESS, "incompatibe type");
        __ cmp(opr1->as_register(), opr2->as_register());
      } else {
        assert(opr2->type() != T_OBJECT && opr2->type() != T_ARRAY && opr2->type() != T_METADATA && opr2->type() != T_ADDRESS, "incompatibe type");
        __ cmp_32(opr1->as_register(), opr2->as_register());
      }
    } else {
      ShouldNotReachHere();
    }
  } else if (opr1->is_double_cpu()) {
#ifdef AARCH64
    long_compare_helper(opr1, opr2);
#else
    Register xlo = opr1->as_register_lo();
    Register xhi = opr1->as_register_hi();
    if (opr2->is_constant() && opr2->as_jlong() == 0) {
      assert(condition == lir_cond_equal || condition == lir_cond_notEqual, "cannot handle otherwise");
      __ orrs(Rtemp, xlo, xhi);
    } else if (opr2->is_register()) {
      Register ylo = opr2->as_register_lo();
      Register yhi = opr2->as_register_hi();
      if (condition == lir_cond_equal || condition == lir_cond_notEqual) {
        __ teq(xhi, yhi);
        __ teq(xlo, ylo, eq);
      } else {
        __ subs(xlo, xlo, ylo);
        __ sbcs(xhi, xhi, yhi);
      }
    } else {
      ShouldNotReachHere();
    }
#endif // AARCH64
  } else if (opr1->is_single_fpu()) {
    if (opr2->is_constant()) {
      assert(opr2->as_jfloat() == 0.0f, "cannot handle otherwise");
      __ cmp_zero_float(opr1->as_float_reg());
    } else {
      __ cmp_float(opr1->as_float_reg(), opr2->as_float_reg());
    }
  } else if (opr1->is_double_fpu()) {
    if (opr2->is_constant()) {
      assert(opr2->as_jdouble() == 0.0, "cannot handle otherwise");
      __ cmp_zero_double(opr1->as_double_reg());
    } else {
      __ cmp_double(opr1->as_double_reg(), opr2->as_double_reg());
    }
  } else {
    ShouldNotReachHere();
  }
}

void LIR_Assembler::comp_fl2i(LIR_Code code, LIR_Opr left, LIR_Opr right, LIR_Opr dst, LIR_Op2* op) {
  const Register res = dst->as_register();
  if (code == lir_cmp_fd2i || code == lir_ucmp_fd2i) {
    comp_op(lir_cond_unknown, left, right, op);
#ifdef AARCH64
    if (code == lir_ucmp_fd2i) {         // unordered is less
      __ cset(res, gt);                  // 1 if '>', else 0
      __ csinv(res, res, ZR, ge);        // previous value if '>=', else -1
    } else {
      __ cset(res, hi);                  // 1 if '>' or unordered, else 0
      __ csinv(res, res, ZR, pl);        // previous value if '>=' or unordered, else -1
    }
#else
    __ fmstat();
    if (code == lir_ucmp_fd2i) {  // unordered is less
      __ mvn(res, 0, lt);
      __ mov(res, 1, ge);
    } else {                      // unordered is greater
      __ mov(res, 1, cs);
      __ mvn(res, 0, cc);
    }
    __ mov(res, 0, eq);
#endif // AARCH64

  } else {
    assert(code == lir_cmp_l2i, "must be");

#ifdef AARCH64
    long_compare_helper(left, right);

    __ cset(res, gt);            // 1 if '>', else 0
    __ csinv(res, res, ZR, ge);  // previous value if '>=', else -1
#else
    Label done;
    const Register xlo = left->as_register_lo();
    const Register xhi = left->as_register_hi();
    const Register ylo = right->as_register_lo();
    const Register yhi = right->as_register_hi();
    __ cmp(xhi, yhi);
    __ mov(res, 1, gt);
    __ mvn(res, 0, lt);
    __ b(done, ne);
    __ subs(res, xlo, ylo);
    __ mov(res, 1, hi);
    __ mvn(res, 0, lo);
    __ bind(done);
#endif // AARCH64
  }
}


void LIR_Assembler::align_call(LIR_Code code) {
  // Not needed
}


void LIR_Assembler::call(LIR_OpJavaCall *op, relocInfo::relocType rtype) {
  int ret_addr_offset = __ patchable_call(op->addr(), rtype);
  assert(ret_addr_offset == __ offset(), "embedded return address not allowed");
  add_call_info_here(op->info());
}


void LIR_Assembler::ic_call(LIR_OpJavaCall *op) {
  bool near_range = __ cache_fully_reachable();
  address oop_address = pc();

  bool use_movw = AARCH64_ONLY(false) NOT_AARCH64(VM_Version::supports_movw());

  // Ricklass may contain something that is not a metadata pointer so
  // mov_metadata can't be used
  InlinedAddress value((address)Universe::non_oop_word());
  InlinedAddress addr(op->addr());
  if (use_movw) {
#ifdef AARCH64
    ShouldNotReachHere();
#else
    __ movw(Ricklass, ((unsigned int)Universe::non_oop_word()) & 0xffff);
    __ movt(Ricklass, ((unsigned int)Universe::non_oop_word()) >> 16);
#endif // AARCH64
  } else {
    // No movw/movt, must be load a pc relative value but no
    // relocation so no metadata table to load from.
    // Use a b instruction rather than a bl, inline constant after the
    // branch, use a PC relative ldr to load the constant, arrange for
    // the call to return after the constant(s).
    __ ldr_literal(Ricklass, value);
  }
  __ relocate(virtual_call_Relocation::spec(oop_address));
  if (near_range && use_movw) {
    __ bl(op->addr());
  } else {
    Label call_return;
    __ adr(LR, call_return);
    if (near_range) {
      __ b(op->addr());
    } else {
      __ indirect_jump(addr, Rtemp);
      __ bind_literal(addr);
    }
    if (!use_movw) {
      __ bind_literal(value);
    }
    __ bind(call_return);
  }
  add_call_info(code_offset(), op->info());
}


/* Currently, vtable-dispatch is only enabled for sparc platforms */
void LIR_Assembler::vtable_call(LIR_OpJavaCall* op) {
  ShouldNotReachHere();
}

void LIR_Assembler::emit_static_call_stub() {
  address call_pc = __ pc();
  address stub = __ start_a_stub(call_stub_size());
  if (stub == NULL) {
    BAILOUT("static call stub overflow");
  }

  DEBUG_ONLY(int offset = code_offset();)

  InlinedMetadata metadata_literal(NULL);
  __ relocate(static_stub_Relocation::spec(call_pc));
  // If not a single instruction, NativeMovConstReg::next_instruction_address()
  // must jump over the whole following ldr_literal.
  // (See CompiledStaticCall::set_to_interpreted())
#ifdef ASSERT
  address ldr_site = __ pc();
#endif
  __ ldr_literal(Rmethod, metadata_literal);
  assert(nativeMovConstReg_at(ldr_site)->next_instruction_address() == __ pc(), "Fix ldr_literal or its parsing");
  bool near_range = __ cache_fully_reachable();
  InlinedAddress dest((address)-1);
  if (near_range) {
    address branch_site = __ pc();
    __ b(branch_site); // b to self maps to special NativeJump -1 destination
  } else {
    __ indirect_jump(dest, Rtemp);
  }
  __ bind_literal(metadata_literal); // includes spec_for_immediate reloc
  if (!near_range) {
    __ bind_literal(dest); // special NativeJump -1 destination
  }

  assert(code_offset() - offset <= call_stub_size(), "overflow");
  __ end_a_stub();
}

void LIR_Assembler::throw_op(LIR_Opr exceptionPC, LIR_Opr exceptionOop, CodeEmitInfo* info) {
  assert(exceptionOop->as_register() == Rexception_obj, "must match");
  assert(exceptionPC->as_register()  == Rexception_pc, "must match");
  info->add_register_oop(exceptionOop);

  Runtime1::StubID handle_id = compilation()->has_fpu_code() ?
                               Runtime1::handle_exception_id :
                               Runtime1::handle_exception_nofpu_id;
  Label return_address;
  __ adr(Rexception_pc, return_address);
  __ call(Runtime1::entry_for(handle_id), relocInfo::runtime_call_type);
  __ bind(return_address);
  add_call_info_here(info);  // for exception handler
}

void LIR_Assembler::unwind_op(LIR_Opr exceptionOop) {
  assert(exceptionOop->as_register() == Rexception_obj, "must match");
  __ b(_unwind_handler_entry);
}

void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, LIR_Opr count, LIR_Opr dest, LIR_Opr tmp) {
#ifdef AARCH64
  if (dest->is_single_cpu()) {
    Register res = dest->as_register();
    Register x = left->as_register();
    Register y = count->as_register();
    assert (dest->type() == T_INT, "unexpected result type");
    assert (left->type() == T_INT, "unexpected left type");

    switch (code) {
      case lir_shl:  __ lslv_w(res, x, y); break;
      case lir_shr:  __ asrv_w(res, x, y); break;
      case lir_ushr: __ lsrv_w(res, x, y); break;
      default: ShouldNotReachHere();
    }
  } else if (dest->is_double_cpu()) {
    Register res = dest->as_register_lo();
    Register x = left->as_register_lo();
    Register y = count->as_register();

    switch (code) {
      case lir_shl:  __ lslv(res, x, y); break;
      case lir_shr:  __ asrv(res, x, y); break;
      case lir_ushr: __ lsrv(res, x, y); break;
      default: ShouldNotReachHere();
    }
  } else {
    ShouldNotReachHere();
  }
#else
  AsmShift shift = lsl;
  switch (code) {
    case lir_shl:  shift = lsl; break;
    case lir_shr:  shift = asr; break;
    case lir_ushr: shift = lsr; break;
    default: ShouldNotReachHere();
  }

  if (dest->is_single_cpu()) {
    __ andr(Rtemp, count->as_register(), 31);
    __ mov(dest->as_register(), AsmOperand(left->as_register(), shift, Rtemp));
  } else if (dest->is_double_cpu()) {
    Register dest_lo = dest->as_register_lo();
    Register dest_hi = dest->as_register_hi();
    Register src_lo  = left->as_register_lo();
    Register src_hi  = left->as_register_hi();
    Register Rcount  = count->as_register();
    // Resolve possible register conflicts
    if (shift == lsl && dest_hi == src_lo) {
      dest_hi = Rtemp;
    } else if (shift != lsl && dest_lo == src_hi) {
      dest_lo = Rtemp;
    } else if (dest_lo == src_lo && dest_hi == src_hi) {
      dest_lo = Rtemp;
    } else if (dest_lo == Rcount || dest_hi == Rcount) {
      Rcount = Rtemp;
    }
    __ andr(Rcount, count->as_register(), 63);
    __ long_shift(dest_lo, dest_hi, src_lo, src_hi, shift, Rcount);
    move_regs(dest_lo, dest->as_register_lo());
    move_regs(dest_hi, dest->as_register_hi());
  } else {
    ShouldNotReachHere();
  }
#endif // AARCH64
}


void LIR_Assembler::shift_op(LIR_Code code, LIR_Opr left, jint count, LIR_Opr dest) {
#ifdef AARCH64
  if (dest->is_single_cpu()) {
    assert (dest->type() == T_INT, "unexpected result type");
    assert (left->type() == T_INT, "unexpected left type");
    count &= 31;
    if (count != 0) {
      switch (code) {
        case lir_shl:  __ _lsl_w(dest->as_register(), left->as_register(), count); break;
        case lir_shr:  __ _asr_w(dest->as_register(), left->as_register(), count); break;
        case lir_ushr: __ _lsr_w(dest->as_register(), left->as_register(), count); break;
        default: ShouldNotReachHere();
      }
    } else {
      move_regs(left->as_register(), dest->as_register());
    }
  } else if (dest->is_double_cpu()) {
    count &= 63;
    if (count != 0) {
      switch (code) {
        case lir_shl:  __ _lsl(dest->as_register_lo(), left->as_register_lo(), count); break;
        case lir_shr:  __ _asr(dest->as_register_lo(), left->as_register_lo(), count); break;
        case lir_ushr: __ _lsr(dest->as_register_lo(), left->as_register_lo(), count); break;
        default: ShouldNotReachHere();
      }
    } else {
      move_regs(left->as_register_lo(), dest->as_register_lo());
    }
  } else {
    ShouldNotReachHere();
  }

#else
  AsmShift shift = lsl;
  switch (code) {
    case lir_shl:  shift = lsl; break;
    case lir_shr:  shift = asr; break;
    case lir_ushr: shift = lsr; break;
    default: ShouldNotReachHere();
  }

  if (dest->is_single_cpu()) {
    count &= 31;
    if (count != 0) {
      __ mov(dest->as_register(), AsmOperand(left->as_register(), shift, count));
    } else {
      move_regs(left->as_register(), dest->as_register());
    }
  } else if (dest->is_double_cpu()) {
    count &= 63;
    if (count != 0) {
      Register dest_lo = dest->as_register_lo();
      Register dest_hi = dest->as_register_hi();
      Register src_lo  = left->as_register_lo();
      Register src_hi  = left->as_register_hi();
      // Resolve possible register conflicts
      if (shift == lsl && dest_hi == src_lo) {
        dest_hi = Rtemp;
      } else if (shift != lsl && dest_lo == src_hi) {
        dest_lo = Rtemp;
      }
      __ long_shift(dest_lo, dest_hi, src_lo, src_hi, shift, count);
      move_regs(dest_lo, dest->as_register_lo());
      move_regs(dest_hi, dest->as_register_hi());
    } else {
      __ long_move(dest->as_register_lo(), dest->as_register_hi(),
                   left->as_register_lo(), left->as_register_hi());
    }
  } else {
    ShouldNotReachHere();
  }
#endif // AARCH64
}


// Saves 4 given registers in reserved argument area.
void LIR_Assembler::save_in_reserved_area(Register r1, Register r2, Register r3, Register r4) {
  verify_reserved_argument_area_size(4);
#ifdef AARCH64
  __ stp(r1, r2, Address(SP, 0));
  __ stp(r3, r4, Address(SP, 2*wordSize));
#else
  __ stmia(SP, RegisterSet(r1) | RegisterSet(r2) | RegisterSet(r3) | RegisterSet(r4));
#endif // AARCH64
}

// Restores 4 given registers from reserved argument area.
void LIR_Assembler::restore_from_reserved_area(Register r1, Register r2, Register r3, Register r4) {
#ifdef AARCH64
  __ ldp(r1, r2, Address(SP, 0));
  __ ldp(r3, r4, Address(SP, 2*wordSize));
#else
  __ ldmia(SP, RegisterSet(r1) | RegisterSet(r2) | RegisterSet(r3) | RegisterSet(r4), no_writeback);
#endif // AARCH64
}


void LIR_Assembler::emit_arraycopy(LIR_OpArrayCopy* op) {
  ciArrayKlass* default_type = op->expected_type();
  Register src = op->src()->as_register();
  Register src_pos = op->src_pos()->as_register();
  Register dst = op->dst()->as_register();
  Register dst_pos = op->dst_pos()->as_register();
  Register length  = op->length()->as_register();
  Register tmp = op->tmp()->as_register();
  Register tmp2 = Rtemp;

  assert(src == R0 && src_pos == R1 && dst == R2 && dst_pos == R3, "code assumption");
#ifdef AARCH64
  assert(length == R4, "code assumption");
#endif // AARCH64

  CodeStub* stub = op->stub();

  int flags = op->flags();
  BasicType basic_type = default_type != NULL ? default_type->element_type()->basic_type() : T_ILLEGAL;
  if (basic_type == T_ARRAY) basic_type = T_OBJECT;

  // If we don't know anything or it's an object array, just go through the generic arraycopy
  if (default_type == NULL) {

    // save arguments, because they will be killed by a runtime call
    save_in_reserved_area(R0, R1, R2, R3);

#ifdef AARCH64
    // save length argument, will be killed by a runtime call
    __ raw_push(length, ZR);
#else
    // pass length argument on SP[0]
    __ str(length, Address(SP, -2*wordSize, pre_indexed));  // 2 words for a proper stack alignment
#endif // AARCH64

    address copyfunc_addr = StubRoutines::generic_arraycopy();
    if (copyfunc_addr == NULL) { // Use C version if stub was not generated
      __ call(CAST_FROM_FN_PTR(address, Runtime1::arraycopy));
    } else {
#ifndef PRODUCT
      if (PrintC1Statistics) {
        __ inc_counter((address)&Runtime1::_generic_arraycopystub_cnt, tmp, tmp2);
      }
#endif // !PRODUCT
      // the stub is in the code cache so close enough
      __ call(copyfunc_addr, relocInfo::runtime_call_type);
    }

#ifdef AARCH64
    __ raw_pop(length, ZR);
#else
    __ add(SP, SP, 2*wordSize);
#endif // AARCH64

    __ cbz_32(R0, *stub->continuation());

    if (copyfunc_addr != NULL) {
      __ mvn_32(tmp, R0);
      restore_from_reserved_area(R0, R1, R2, R3);  // load saved arguments in slow case only
      __ sub_32(length, length, tmp);
      __ add_32(src_pos, src_pos, tmp);
      __ add_32(dst_pos, dst_pos, tmp);
    } else {
      restore_from_reserved_area(R0, R1, R2, R3);  // load saved arguments in slow case only
    }

    __ b(*stub->entry());

    __ bind(*stub->continuation());
    return;
  }

  assert(default_type != NULL && default_type->is_array_klass() && default_type->is_loaded(),
         "must be true at this point");
  int elem_size = type2aelembytes(basic_type);
  int shift = exact_log2(elem_size);

  // Check for NULL
  if (flags & LIR_OpArrayCopy::src_null_check) {
    if (flags & LIR_OpArrayCopy::dst_null_check) {
      __ cmp(src, 0);
      __ cond_cmp(dst, 0, ne);  // make one instruction shorter if both checks are needed
      __ b(*stub->entry(), eq);
    } else {
      __ cbz(src, *stub->entry());
    }
  } else if (flags & LIR_OpArrayCopy::dst_null_check) {
    __ cbz(dst, *stub->entry());
  }

  // If the compiler was not able to prove that exact type of the source or the destination
  // of the arraycopy is an array type, check at runtime if the source or the destination is
  // an instance type.
  if (flags & LIR_OpArrayCopy::type_check) {
    if (!(flags & LIR_OpArrayCopy::LIR_OpArrayCopy::dst_objarray)) {
      __ load_klass(tmp, dst);
      __ ldr_u32(tmp2, Address(tmp, in_bytes(Klass::layout_helper_offset())));
      __ mov_slow(tmp, Klass::_lh_neutral_value);
      __ cmp_32(tmp2, tmp);
      __ b(*stub->entry(), ge);
    }

    if (!(flags & LIR_OpArrayCopy::LIR_OpArrayCopy::src_objarray)) {
      __ load_klass(tmp, src);
      __ ldr_u32(tmp2, Address(tmp, in_bytes(Klass::layout_helper_offset())));
      __ mov_slow(tmp, Klass::_lh_neutral_value);
      __ cmp_32(tmp2, tmp);
      __ b(*stub->entry(), ge);
    }
  }

  // Check if negative
  const int all_positive_checks = LIR_OpArrayCopy::src_pos_positive_check |
                                  LIR_OpArrayCopy::dst_pos_positive_check |
                                  LIR_OpArrayCopy::length_positive_check;
  switch (flags & all_positive_checks) {
    case LIR_OpArrayCopy::src_pos_positive_check:
      __ branch_if_negative_32(src_pos, *stub->entry());
      break;
    case LIR_OpArrayCopy::dst_pos_positive_check:
      __ branch_if_negative_32(dst_pos, *stub->entry());
      break;
    case LIR_OpArrayCopy::length_positive_check:
      __ branch_if_negative_32(length, *stub->entry());
      break;
    case LIR_OpArrayCopy::src_pos_positive_check | LIR_OpArrayCopy::dst_pos_positive_check:
      __ branch_if_any_negative_32(src_pos, dst_pos, tmp, *stub->entry());
      break;
    case LIR_OpArrayCopy::src_pos_positive_check | LIR_OpArrayCopy::length_positive_check:
      __ branch_if_any_negative_32(src_pos, length, tmp, *stub->entry());
      break;
    case LIR_OpArrayCopy::dst_pos_positive_check | LIR_OpArrayCopy::length_positive_check:
      __ branch_if_any_negative_32(dst_pos, length, tmp, *stub->entry());
      break;
    case all_positive_checks:
      __ branch_if_any_negative_32(src_pos, dst_pos, length, tmp, *stub->entry());
      break;
    default:
      assert((flags & all_positive_checks) == 0, "the last option");
  }

  // Range checks
  if (flags & LIR_OpArrayCopy::src_range_check) {
    __ ldr_s32(tmp2, Address(src, arrayOopDesc::length_offset_in_bytes()));
    __ add_32(tmp, src_pos, length);
    __ cmp_32(tmp, tmp2);
    __ b(*stub->entry(), hi);
  }
  if (flags & LIR_OpArrayCopy::dst_range_check) {
    __ ldr_s32(tmp2, Address(dst, arrayOopDesc::length_offset_in_bytes()));
    __ add_32(tmp, dst_pos, length);
    __ cmp_32(tmp, tmp2);
    __ b(*stub->entry(), hi);
  }

  // Check if src and dst are of the same type
  if (flags & LIR_OpArrayCopy::type_check) {
    // We don't know the array types are compatible
    if (basic_type != T_OBJECT) {
      // Simple test for basic type arrays
      if (UseCompressedClassPointers) {
        // We don't need decode because we just need to compare
        __ ldr_u32(tmp, Address(src, oopDesc::klass_offset_in_bytes()));
        __ ldr_u32(tmp2, Address(dst, oopDesc::klass_offset_in_bytes()));
        __ cmp_32(tmp, tmp2);
      } else {
        __ load_klass(tmp, src);
        __ load_klass(tmp2, dst);
        __ cmp(tmp, tmp2);
      }
      __ b(*stub->entry(), ne);
    } else {
      // For object arrays, if src is a sub class of dst then we can
      // safely do the copy.
      Label cont, slow;

      address copyfunc_addr = StubRoutines::checkcast_arraycopy();

      __ load_klass(tmp, src);
      __ load_klass(tmp2, dst);

      // We are at a call so all live registers are saved before we
      // get here
      assert_different_registers(tmp, tmp2, R6, altFP_7_11);

      __ check_klass_subtype_fast_path(tmp, tmp2, R6, altFP_7_11, &cont, copyfunc_addr == NULL ? stub->entry() : &slow, NULL);

      __ mov(R6, R0);
      __ mov(altFP_7_11, R1);
      __ mov(R0, tmp);
      __ mov(R1, tmp2);
      __ call(Runtime1::entry_for(Runtime1::slow_subtype_check_id), relocInfo::runtime_call_type); // does not blow any registers except R0, LR and Rtemp
      __ cmp_32(R0, 0);
      __ mov(R0, R6);
      __ mov(R1, altFP_7_11);

      if (copyfunc_addr != NULL) { // use stub if available
        // src is not a sub class of dst so we have to do a
        // per-element check.

        __ b(cont, ne);

        __ bind(slow);

        int mask = LIR_OpArrayCopy::src_objarray|LIR_OpArrayCopy::dst_objarray;
        if ((flags & mask) != mask) {
          // Check that at least both of them object arrays.
          assert(flags & mask, "one of the two should be known to be an object array");

          if (!(flags & LIR_OpArrayCopy::src_objarray)) {
            __ load_klass(tmp, src);
          } else if (!(flags & LIR_OpArrayCopy::dst_objarray)) {
            __ load_klass(tmp, dst);
          }
          int lh_offset = in_bytes(Klass::layout_helper_offset());

          __ ldr_u32(tmp2, Address(tmp, lh_offset));

          jint objArray_lh = Klass::array_layout_helper(T_OBJECT);
          __ mov_slow(tmp, objArray_lh);
          __ cmp_32(tmp, tmp2);
          __ b(*stub->entry(), ne);
        }

        save_in_reserved_area(R0, R1, R2, R3);

        Register src_ptr = R0;
        Register dst_ptr = R1;
        Register len     = R2;
        Register chk_off = R3;
        Register super_k = AARCH64_ONLY(R4) NOT_AARCH64(tmp);

        __ add(src_ptr, src, arrayOopDesc::base_offset_in_bytes(basic_type));
        __ add_ptr_scaled_int32(src_ptr, src_ptr, src_pos, shift);

        __ add(dst_ptr, dst, arrayOopDesc::base_offset_in_bytes(basic_type));
        __ add_ptr_scaled_int32(dst_ptr, dst_ptr, dst_pos, shift);
        __ load_klass(tmp, dst);

        int ek_offset = in_bytes(ObjArrayKlass::element_klass_offset());
        int sco_offset = in_bytes(Klass::super_check_offset_offset());

#ifdef AARCH64
        __ raw_push(length, ZR); // Preserve length around *copyfunc_addr call

        __ mov(len, length);
        __ ldr(super_k, Address(tmp, ek_offset)); // super_k == R4 == length, so this load cannot be performed earlier
        // TODO-AARCH64: check whether it is faster to load super klass early by using tmp and additional mov.
        __ ldr_u32(chk_off, Address(super_k, sco_offset));
#else // AARCH64
        __ ldr(super_k, Address(tmp, ek_offset));

        __ mov(len, length);
        __ ldr_u32(chk_off, Address(super_k, sco_offset));
        __ push(super_k);
#endif // AARCH64

        __ call(copyfunc_addr, relocInfo::runtime_call_type);

#ifndef PRODUCT
        if (PrintC1Statistics) {
          Label failed;
          __ cbnz_32(R0, failed);
          __ inc_counter((address)&Runtime1::_arraycopy_checkcast_cnt, tmp, tmp2);
          __ bind(failed);
        }
#endif // PRODUCT

#ifdef AARCH64
        __ raw_pop(length, ZR);
#else
        __ add(SP, SP, wordSize);  // Drop super_k argument
#endif // AARCH64

        __ cbz_32(R0, *stub->continuation());
        __ mvn_32(tmp, R0);

        // load saved arguments in slow case only
        restore_from_reserved_area(R0, R1, R2, R3);

        __ sub_32(length, length, tmp);
        __ add_32(src_pos, src_pos, tmp);
        __ add_32(dst_pos, dst_pos, tmp);

#ifndef PRODUCT
        if (PrintC1Statistics) {
          __ inc_counter((address)&Runtime1::_arraycopy_checkcast_attempt_cnt, tmp, tmp2);
        }
#endif

        __ b(*stub->entry());

        __ bind(cont);
      } else {
        __ b(*stub->entry(), eq);
        __ bind(cont);
      }
    }
  }

#ifndef PRODUCT
  if (PrintC1Statistics) {
    address counter = Runtime1::arraycopy_count_address(basic_type);
    __ inc_counter(counter, tmp, tmp2);
  }
#endif // !PRODUCT

  bool disjoint = (flags & LIR_OpArrayCopy::overlapping) == 0;
  bool aligned = (flags & LIR_OpArrayCopy::unaligned) == 0;
  const char *name;
  address entry = StubRoutines::select_arraycopy_function(basic_type, aligned, disjoint, name, false);

  Register src_ptr = R0;
  Register dst_ptr = R1;
  Register len     = R2;

  __ add(src_ptr, src, arrayOopDesc::base_offset_in_bytes(basic_type));
  __ add_ptr_scaled_int32(src_ptr, src_ptr, src_pos, shift);

  __ add(dst_ptr, dst, arrayOopDesc::base_offset_in_bytes(basic_type));
  __ add_ptr_scaled_int32(dst_ptr, dst_ptr, dst_pos, shift);

  __ mov(len, length);

  __ call(entry, relocInfo::runtime_call_type);

  __ bind(*stub->continuation());
}

#ifdef ASSERT
 // emit run-time assertion
void LIR_Assembler::emit_assert(LIR_OpAssert* op) {
  assert(op->code() == lir_assert, "must be");

#ifdef AARCH64
  __ NOT_IMPLEMENTED();
#else
  if (op->in_opr1()->is_valid()) {
    assert(op->in_opr2()->is_valid(), "both operands must be valid");
    comp_op(op->condition(), op->in_opr1(), op->in_opr2(), op);
  } else {
    assert(op->in_opr2()->is_illegal(), "both operands must be illegal");
    assert(op->condition() == lir_cond_always, "no other conditions allowed");
  }

  Label ok;
  if (op->condition() != lir_cond_always) {
    AsmCondition acond;
    switch (op->condition()) {
      case lir_cond_equal:        acond = eq; break;
      case lir_cond_notEqual:     acond = ne; break;
      case lir_cond_less:         acond = lt; break;
      case lir_cond_lessEqual:    acond = le; break;
      case lir_cond_greaterEqual: acond = ge; break;
      case lir_cond_greater:      acond = gt; break;
      case lir_cond_aboveEqual:   acond = hs; break;
      case lir_cond_belowEqual:   acond = ls; break;
      default:                    ShouldNotReachHere();
    }
    __ b(ok, acond);
  }
  if (op->halt()) {
    const char* str = __ code_string(op->msg());
    __ stop(str);
  } else {
    breakpoint();
  }
  __ bind(ok);
#endif // AARCH64
}
#endif // ASSERT

void LIR_Assembler::emit_updatecrc32(LIR_OpUpdateCRC32* op) {
  fatal("CRC32 intrinsic is not implemented on this platform");
}

void LIR_Assembler::emit_lock(LIR_OpLock* op) {
  Register obj = op->obj_opr()->as_pointer_register();
  Register hdr = op->hdr_opr()->as_pointer_register();
  Register lock = op->lock_opr()->as_pointer_register();
  Register tmp = op->scratch_opr()->is_illegal() ? noreg :
                 op->scratch_opr()->as_pointer_register();

  if (!UseFastLocking) {
    __ b(*op->stub()->entry());
  } else if (op->code() == lir_lock) {
    assert(BasicLock::displaced_header_offset_in_bytes() == 0, "lock_reg must point to the displaced header");
    int null_check_offset = __ lock_object(hdr, obj, lock, tmp, *op->stub()->entry());
    if (op->info() != NULL) {
      add_debug_info_for_null_check(null_check_offset, op->info());
    }
  } else if (op->code() == lir_unlock) {
    __ unlock_object(hdr, obj, lock, tmp, *op->stub()->entry());
  } else {
    ShouldNotReachHere();
  }
  __ bind(*op->stub()->continuation());
}


void LIR_Assembler::emit_profile_call(LIR_OpProfileCall* op) {
  ciMethod* method = op->profiled_method();
  int bci          = op->profiled_bci();
  ciMethod* callee = op->profiled_callee();

  // Update counter for all call types
  ciMethodData* md = method->method_data_or_null();
  assert(md != NULL, "Sanity");
  ciProfileData* data = md->bci_to_data(bci);
  assert(data->is_CounterData(), "need CounterData for calls");
  assert(op->mdo()->is_single_cpu(),  "mdo must be allocated");
  Register mdo  = op->mdo()->as_register();
  assert(op->tmp1()->is_register(), "tmp1 must be allocated");
  Register tmp1 = op->tmp1()->as_pointer_register();
  assert_different_registers(mdo, tmp1);
  __ mov_metadata(mdo, md->constant_encoding());
  int mdo_offset_bias = 0;
  int max_offset = AARCH64_ONLY(4096 << LogBytesPerWord) NOT_AARCH64(4096);
  if (md->byte_offset_of_slot(data, CounterData::count_offset()) + data->size_in_bytes() >= max_offset) {
    // The offset is large so bias the mdo by the base of the slot so
    // that the ldr can use an immediate offset to reference the slots of the data
    mdo_offset_bias = md->byte_offset_of_slot(data, CounterData::count_offset());
    __ mov_slow(tmp1, mdo_offset_bias);
    __ add(mdo, mdo, tmp1);
  }

  Address counter_addr(mdo, md->byte_offset_of_slot(data, CounterData::count_offset()) - mdo_offset_bias);
  Bytecodes::Code bc = method->java_code_at_bci(bci);
  const bool callee_is_static = callee->is_loaded() && callee->is_static();
  // Perform additional virtual call profiling for invokevirtual and
  // invokeinterface bytecodes
  if ((bc == Bytecodes::_invokevirtual || bc == Bytecodes::_invokeinterface) &&
      !callee_is_static &&  // required for optimized MH invokes
      C1ProfileVirtualCalls) {

    assert(op->recv()->is_single_cpu(), "recv must be allocated");
    Register recv = op->recv()->as_register();
    assert_different_registers(mdo, tmp1, recv);
    assert(data->is_VirtualCallData(), "need VirtualCallData for virtual calls");
    ciKlass* known_klass = op->known_holder();
    if (C1OptimizeVirtualCallProfiling && known_klass != NULL) {
      // We know the type that will be seen at this call site; we can
      // statically update the MethodData* rather than needing to do
      // dynamic tests on the receiver type

      // NOTE: we should probably put a lock around this search to
      // avoid collisions by concurrent compilations
      ciVirtualCallData* vc_data = (ciVirtualCallData*) data;
      uint i;
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (known_klass->equals(receiver)) {
          Address data_addr(mdo, md->byte_offset_of_slot(data,
                                                         VirtualCallData::receiver_count_offset(i)) -
                            mdo_offset_bias);
          __ ldr(tmp1, data_addr);
          __ add(tmp1, tmp1, DataLayout::counter_increment);
          __ str(tmp1, data_addr);
          return;
        }
      }

      // Receiver type not found in profile data; select an empty slot

      // Note that this is less efficient than it should be because it
      // always does a write to the receiver part of the
      // VirtualCallData rather than just the first time
      for (i = 0; i < VirtualCallData::row_limit(); i++) {
        ciKlass* receiver = vc_data->receiver(i);
        if (receiver == NULL) {
          Address recv_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_offset(i)) -
                            mdo_offset_bias);
          __ mov_metadata(tmp1, known_klass->constant_encoding());
          __ str(tmp1, recv_addr);
          Address data_addr(mdo, md->byte_offset_of_slot(data, VirtualCallData::receiver_count_offset(i)) -
                            mdo_offset_bias);
          __ ldr(tmp1, data_addr);
          __ add(tmp1, tmp1, DataLayout::counter_increment);
          __ str(tmp1, data_addr);
          return;
        }
      }
    } else {
      __ load_klass(recv, recv);
      Label update_done;
      type_profile_helper(mdo, mdo_offset_bias, md, data, recv, tmp1, &update_done);
      // Receiver did not match any saved receiver and there is no empty row for it.
      // Increment total counter to indicate polymorphic case.
      __ ldr(tmp1, counter_addr);
      __ add(tmp1, tmp1, DataLayout::counter_increment);
      __ str(tmp1, counter_addr);

      __ bind(update_done);
    }
  } else {
    // Static call
    __ ldr(tmp1, counter_addr);
    __ add(tmp1, tmp1, DataLayout::counter_increment);
    __ str(tmp1, counter_addr);
  }
}

void LIR_Assembler::emit_profile_type(LIR_OpProfileType* op) {
  fatal("Type profiling not implemented on this platform");
}

void LIR_Assembler::emit_delay(LIR_OpDelay*) {
  Unimplemented();
}


void LIR_Assembler::monitor_address(int monitor_no, LIR_Opr dst) {
  Address mon_addr = frame_map()->address_for_monitor_lock(monitor_no);
  __ add_slow(dst->as_pointer_register(), mon_addr.base(), mon_addr.disp());
}


void LIR_Assembler::align_backward_branch_target() {
  // TODO-AARCH64 review it
  // Some ARM processors do better with 8-byte branch target alignment
  __ align(8);
}


void LIR_Assembler::negate(LIR_Opr left, LIR_Opr dest) {

  if (left->is_single_cpu()) {
    assert (dest->type() == T_INT, "unexpected result type");
    assert (left->type() == T_INT, "unexpected left type");
    __ neg_32(dest->as_register(), left->as_register());
  } else if (left->is_double_cpu()) {
#ifdef AARCH64
    __ neg(dest->as_register_lo(), left->as_register_lo());
#else
    Register dest_lo = dest->as_register_lo();
    Register dest_hi = dest->as_register_hi();
    Register src_lo = left->as_register_lo();
    Register src_hi = left->as_register_hi();
    if (dest_lo == src_hi) {
      dest_lo = Rtemp;
    }
    __ rsbs(dest_lo, src_lo, 0);
    __ rsc(dest_hi, src_hi, 0);
    move_regs(dest_lo, dest->as_register_lo());
#endif // AARCH64
  } else if (left->is_single_fpu()) {
    __ neg_float(dest->as_float_reg(), left->as_float_reg());
  } else if (left->is_double_fpu()) {
    __ neg_double(dest->as_double_reg(), left->as_double_reg());
  } else {
    ShouldNotReachHere();
  }
}


void LIR_Assembler::leal(LIR_Opr addr_opr, LIR_Opr dest) {
  LIR_Address* addr = addr_opr->as_address_ptr();
  if (addr->index()->is_illegal()) {
    jint c = addr->disp();
    if (!Assembler::is_arith_imm_in_range(c)) {
      BAILOUT("illegal arithmetic operand");
    }
    __ add(dest->as_pointer_register(), addr->base()->as_pointer_register(), c);
  } else {
    assert(addr->disp() == 0, "cannot handle otherwise");
#ifdef AARCH64
    assert(addr->index()->is_double_cpu(), "should be");
#endif // AARCH64
    __ add(dest->as_pointer_register(), addr->base()->as_pointer_register(),
           AsmOperand(addr->index()->as_pointer_register(), lsl, addr->scale()));
  }
}


void LIR_Assembler::rt_call(LIR_Opr result, address dest, const LIR_OprList* args, LIR_Opr tmp, CodeEmitInfo* info) {
  assert(!tmp->is_valid(), "don't need temporary");
  __ call(dest);
  if (info != NULL) {
    add_call_info_here(info);
  }
}


void LIR_Assembler::volatile_move_op(LIR_Opr src, LIR_Opr dest, BasicType type, CodeEmitInfo* info) {
#ifdef AARCH64
  Unimplemented(); // TODO-AARCH64: Use stlr/ldar instructions for volatile load/store
#else
  assert(src->is_double_cpu() && dest->is_address() ||
         src->is_address() && dest->is_double_cpu(),
         "Simple move_op is called for all other cases");

  int null_check_offset;
  if (dest->is_address()) {
    // Store
    const LIR_Address* addr = dest->as_address_ptr();
    const Register src_lo = src->as_register_lo();
    const Register src_hi = src->as_register_hi();
    assert(addr->index()->is_illegal() && addr->disp() == 0, "The address is simple already");

    if (src_lo < src_hi) {
      null_check_offset = __ offset();
      __ stmia(addr->base()->as_register(), RegisterSet(src_lo) | RegisterSet(src_hi));
    } else {
      assert(src_lo < Rtemp, "Rtemp is higher than any allocatable register");
      __ mov(Rtemp, src_hi);
      null_check_offset = __ offset();
      __ stmia(addr->base()->as_register(), RegisterSet(src_lo) | RegisterSet(Rtemp));
    }
  } else {
    // Load
    const LIR_Address* addr = src->as_address_ptr();
    const Register dest_lo = dest->as_register_lo();
    const Register dest_hi = dest->as_register_hi();
    assert(addr->index()->is_illegal() && addr->disp() == 0, "The address is simple already");

    null_check_offset = __ offset();
    if (dest_lo < dest_hi) {
      __ ldmia(addr->base()->as_register(), RegisterSet(dest_lo) | RegisterSet(dest_hi));
    } else {
      assert(dest_lo < Rtemp, "Rtemp is higher than any allocatable register");
      __ ldmia(addr->base()->as_register(), RegisterSet(dest_lo) | RegisterSet(Rtemp));
      __ mov(dest_hi, Rtemp);
    }
  }

  if (info != NULL) {
    add_debug_info_for_null_check(null_check_offset, info);
  }
#endif // AARCH64
}


void LIR_Assembler::membar() {
  __ membar(MacroAssembler::StoreLoad, Rtemp);
}

void LIR_Assembler::membar_acquire() {
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::LoadLoad | MacroAssembler::LoadStore), Rtemp);
}

void LIR_Assembler::membar_release() {
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreStore | MacroAssembler::LoadStore), Rtemp);
}

void LIR_Assembler::membar_loadload() {
  __ membar(MacroAssembler::LoadLoad, Rtemp);
}

void LIR_Assembler::membar_storestore() {
  __ membar(MacroAssembler::StoreStore, Rtemp);
}

void LIR_Assembler::membar_loadstore() {
  __ membar(MacroAssembler::LoadStore, Rtemp);
}

void LIR_Assembler::membar_storeload() {
  __ membar(MacroAssembler::StoreLoad, Rtemp);
}

void LIR_Assembler::on_spin_wait() {
  Unimplemented();
}

void LIR_Assembler::get_thread(LIR_Opr result_reg) {
  // Not used on ARM
  Unimplemented();
}

void LIR_Assembler::peephole(LIR_List* lir) {
#ifdef AARCH64
  return; // TODO-AARCH64 implement peephole optimizations
#endif
  LIR_OpList* inst = lir->instructions_list();
  const int inst_length = inst->length();
  for (int i = 0; i < inst_length; i++) {
    LIR_Op* op = inst->at(i);
    switch (op->code()) {
      case lir_cmp: {
        // Replace:
        //   cmp rX, y
        //   cmove [EQ] y, z, rX
        // with
        //   cmp rX, y
        //   cmove [EQ] illegalOpr, z, rX
        //
        // or
        //   cmp rX, y
        //   cmove [NE] z, y, rX
        // with
        //   cmp rX, y
        //   cmove [NE] z, illegalOpr, rX
        //
        // moves from illegalOpr should be removed when converting LIR to native assembly

        LIR_Op2* cmp = op->as_Op2();
        assert(cmp != NULL, "cmp LIR instruction is not an op2");

        if (i + 1 < inst_length) {
          LIR_Op2* cmove = inst->at(i + 1)->as_Op2();
          if (cmove != NULL && cmove->code() == lir_cmove) {
            LIR_Opr cmove_res = cmove->result_opr();
            bool res_is_op1 = cmove_res == cmp->in_opr1();
            bool res_is_op2 = cmove_res == cmp->in_opr2();
            LIR_Opr cmp_res, cmp_arg;
            if (res_is_op1) {
              cmp_res = cmp->in_opr1();
              cmp_arg = cmp->in_opr2();
            } else if (res_is_op2) {
              cmp_res = cmp->in_opr2();
              cmp_arg = cmp->in_opr1();
            } else {
              cmp_res = LIR_OprFact::illegalOpr;
              cmp_arg = LIR_OprFact::illegalOpr;
            }

            if (cmp_res != LIR_OprFact::illegalOpr) {
              LIR_Condition cond = cmove->condition();
              if (cond == lir_cond_equal && cmove->in_opr1() == cmp_arg) {
                cmove->set_in_opr1(LIR_OprFact::illegalOpr);
              } else if (cond == lir_cond_notEqual && cmove->in_opr2() == cmp_arg) {
                cmove->set_in_opr2(LIR_OprFact::illegalOpr);
              }
            }
          }
        }
        break;
      }

      default:
        break;
    }
  }
}

void LIR_Assembler::atomic_op(LIR_Code code, LIR_Opr src, LIR_Opr data, LIR_Opr dest, LIR_Opr tmp) {
  Register ptr = src->as_pointer_register();

  if (code == lir_xchg) {
#ifdef AARCH64
    if (UseCompressedOops && data->is_oop()) {
      __ encode_heap_oop(tmp->as_pointer_register(), data->as_register());
    }
#endif // AARCH64
  } else {
    assert (!data->is_oop(), "xadd for oops");
  }

#ifndef AARCH64
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreStore | MacroAssembler::LoadStore), Rtemp);
#endif // !AARCH64

  Label retry;
  __ bind(retry);

  if ((data->type() == T_INT) || (data->is_oop() AARCH64_ONLY(&& UseCompressedOops))) {
    Register dst = dest->as_register();
    Register new_val = noreg;
#ifdef AARCH64
    __ ldaxr_w(dst, ptr);
#else
    __ ldrex(dst, Address(ptr));
#endif
    if (code == lir_xadd) {
      Register tmp_reg = tmp->as_register();
      if (data->is_constant()) {
        assert_different_registers(dst, ptr, tmp_reg);
        __ add_32(tmp_reg, dst, data->as_constant_ptr()->as_jint());
      } else {
        assert_different_registers(dst, ptr, tmp_reg, data->as_register());
        __ add_32(tmp_reg, dst, data->as_register());
      }
      new_val = tmp_reg;
    } else {
      if (UseCompressedOops && data->is_oop()) {
        new_val = tmp->as_pointer_register();
      } else {
        new_val = data->as_register();
      }
      assert_different_registers(dst, ptr, new_val);
    }
#ifdef AARCH64
    __ stlxr_w(Rtemp, new_val, ptr);
#else
    __ strex(Rtemp, new_val, Address(ptr));
#endif // AARCH64

#ifdef AARCH64
  } else if ((data->type() == T_LONG) || (data->is_oop() && !UseCompressedOops)) {
    Register dst = dest->as_pointer_register();
    Register new_val = noreg;
    __ ldaxr(dst, ptr);
    if (code == lir_xadd) {
      Register tmp_reg = tmp->as_pointer_register();
      if (data->is_constant()) {
        assert_different_registers(dst, ptr, tmp_reg);
        jlong c = data->as_constant_ptr()->as_jlong();
        assert((jlong)((jint)c) == c, "overflow");
        __ add(tmp_reg, dst, (jint)c);
      } else {
        assert_different_registers(dst, ptr, tmp_reg, data->as_pointer_register());
        __ add(tmp_reg, dst, data->as_pointer_register());
      }
      new_val = tmp_reg;
    } else {
      new_val = data->as_pointer_register();
      assert_different_registers(dst, ptr, new_val);
    }
    __ stlxr(Rtemp, new_val, ptr);
#else
  } else if (data->type() == T_LONG) {
    Register dst_lo = dest->as_register_lo();
    Register new_val_lo = noreg;
    Register dst_hi = dest->as_register_hi();

    assert(dst_hi->encoding() == dst_lo->encoding() + 1, "non aligned register pair");
    assert((dst_lo->encoding() & 0x1) == 0, "misaligned register pair");

    __ bind(retry);
    __ ldrexd(dst_lo, Address(ptr));
    if (code == lir_xadd) {
      Register tmp_lo = tmp->as_register_lo();
      Register tmp_hi = tmp->as_register_hi();

      assert(tmp_hi->encoding() == tmp_lo->encoding() + 1, "non aligned register pair");
      assert((tmp_lo->encoding() & 0x1) == 0, "misaligned register pair");

      if (data->is_constant()) {
        jlong c = data->as_constant_ptr()->as_jlong();
        assert((jlong)((jint)c) == c, "overflow");
        assert_different_registers(dst_lo, dst_hi, ptr, tmp_lo, tmp_hi);
        __ adds(tmp_lo, dst_lo, (jint)c);
        __ adc(tmp_hi, dst_hi, 0);
      } else {
        Register new_val_lo = data->as_register_lo();
        Register new_val_hi = data->as_register_hi();
        __ adds(tmp_lo, dst_lo, new_val_lo);
        __ adc(tmp_hi, dst_hi, new_val_hi);
        assert_different_registers(dst_lo, dst_hi, ptr, tmp_lo, tmp_hi, new_val_lo, new_val_hi);
      }
      new_val_lo = tmp_lo;
    } else {
      new_val_lo = data->as_register_lo();
      Register new_val_hi = data->as_register_hi();

      assert_different_registers(dst_lo, dst_hi, ptr, new_val_lo, new_val_hi);
      assert(new_val_hi->encoding() == new_val_lo->encoding() + 1, "non aligned register pair");
      assert((new_val_lo->encoding() & 0x1) == 0, "misaligned register pair");
    }
    __ strexd(Rtemp, new_val_lo, Address(ptr));
#endif // AARCH64
  } else {
    ShouldNotReachHere();
  }

  __ cbnz_32(Rtemp, retry);
  __ membar(MacroAssembler::Membar_mask_bits(MacroAssembler::StoreLoad | MacroAssembler::StoreStore), Rtemp);

#ifdef AARCH64
  if (UseCompressedOops && data->is_oop()) {
    __ decode_heap_oop(dest->as_register());
  }
#endif // AARCH64
}

#undef __
