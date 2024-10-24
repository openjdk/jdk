/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2022, Arm Limited. All rights reserved.
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

#include "precompiled.hpp"
#include "asm/macroAssembler.hpp"
#include "classfile/javaClasses.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vmreg_aarch64.inline.hpp"

#define __ _masm->

// for callee saved regs, according to the caller's ABI
static int compute_reg_save_area_size(const ABIDescriptor& abi) {
  int size = 0;
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    if (reg == rfp || reg == sp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      size += 8; // bytes
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
      // Only the lower 64 bits of vector registers need to be preserved.
      size += 8; // bytes
    }
  }

  return size;
}

static void preserve_callee_saved_registers(MacroAssembler* _masm, const ABIDescriptor& abi, int reg_save_area_offset) {
  // 1. iterate all registers in the architecture
  //     - check if they are volatile or not for the given abi
  //     - if NOT, we need to save it here

  int offset = reg_save_area_offset;

  __ block_comment("{ preserve_callee_saved_regs ");
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    if (reg == rfp || reg == sp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      __ str(reg, Address(sp, offset));
      offset += 8;
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
      __ strd(reg, Address(sp, offset));
      offset += 8;
    }
  }

  __ block_comment("} preserve_callee_saved_regs ");
}

static void restore_callee_saved_registers(MacroAssembler* _masm, const ABIDescriptor& abi, int reg_save_area_offset) {
  // 1. iterate all registers in the architecture
  //     - check if they are volatile or not for the given abi
  //     - if NOT, we need to restore it here

  int offset = reg_save_area_offset;

  __ block_comment("{ restore_callee_saved_regs ");
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    if (reg == rfp || reg == sp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      __ ldr(reg, Address(sp, offset));
      offset += 8;
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
      __ ldrd(reg, Address(sp, offset));
      offset += 8;
    }
  }

  __ block_comment("} restore_callee_saved_regs ");
}

static const int upcall_stub_code_base_size = 1024;
static const int upcall_stub_size_per_arg = 16;

address UpcallLinker::make_upcall_stub(jobject receiver, Symbol* signature,
                                       BasicType* out_sig_bt, int total_out_args,
                                       BasicType ret_type,
                                       jobject jabi, jobject jconv,
                                       bool needs_return_buffer, int ret_buf_size) {
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);
  const CallRegs call_regs = ForeignGlobals::parse_call_regs(jconv);
  int code_size = upcall_stub_code_base_size + (total_out_args * upcall_stub_size_per_arg);
  CodeBuffer buffer("upcall_stub", code_size, /* locs_size = */ 1);
  if (buffer.blob() == nullptr) {
    return nullptr;
  }

  GrowableArray<VMStorage> unfiltered_out_regs;
  int out_arg_bytes = ForeignGlobals::java_calling_convention(out_sig_bt, total_out_args, unfiltered_out_regs);
  int preserved_bytes = SharedRuntime::out_preserve_stack_slots() * VMRegImpl::stack_slot_size;
  int stack_bytes = preserved_bytes + out_arg_bytes;
  int out_arg_area = align_up(stack_bytes , StackAlignmentInBytes);

  // out_arg_area (for stack arguments) doubles as shadow space for native calls.
  // make sure it is big enough.
  if (out_arg_area < frame::arg_reg_save_area_bytes) {
    out_arg_area = frame::arg_reg_save_area_bytes;
  }

  int reg_save_area_size = compute_reg_save_area_size(abi);
  RegSpiller arg_spiller(call_regs._arg_regs);
  RegSpiller result_spiller(call_regs._ret_regs);

  int shuffle_area_offset    = 0;
  int res_save_area_offset   = shuffle_area_offset    + out_arg_area;
  int arg_save_area_offset   = res_save_area_offset   + result_spiller.spill_size_bytes();
  int reg_save_area_offset   = arg_save_area_offset   + arg_spiller.spill_size_bytes();
  int frame_data_offset      = reg_save_area_offset   + reg_save_area_size;
  int frame_bottom_offset    = frame_data_offset      + sizeof(UpcallStub::FrameData);

  StubLocations locs;
  int ret_buf_offset = -1;
  if (needs_return_buffer) {
    ret_buf_offset = frame_bottom_offset;
    frame_bottom_offset += ret_buf_size;
    // use a free register for shuffling code to pick up return
    // buffer address from
    locs.set(StubLocations::RETURN_BUFFER, abi._scratch1);
  }

  Register shuffle_reg = r19;
  GrowableArray<VMStorage> in_regs = ForeignGlobals::replace_place_holders(call_regs._arg_regs, locs);
  GrowableArray<VMStorage> filtered_out_regs = ForeignGlobals::upcall_filter_receiver_reg(unfiltered_out_regs);
  ArgumentShuffle arg_shuffle(in_regs, filtered_out_regs, as_VMStorage(shuffle_reg));

#ifndef PRODUCT
  LogTarget(Trace, foreign, upcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  int frame_size = frame_bottom_offset;
  frame_size = align_up(frame_size, StackAlignmentInBytes);

  // The space we have allocated will look like:
  //
  //
  // FP-> |                     |
  //      |---------------------| = frame_bottom_offset = frame_size
  //      | (optional)          |
  //      | ret_buf             |
  //      |---------------------| = ret_buf_offset
  //      |                     |
  //      | FrameData           |
  //      |---------------------| = frame_data_offset
  //      |                     |
  //      | reg_save_area       |
  //      |---------------------| = reg_save_are_offset
  //      |                     |
  //      | arg_save_area       |
  //      |---------------------| = arg_save_are_offset
  //      |                     |
  //      | res_save_area       |
  //      |---------------------| = res_save_are_offset
  //      |                     |
  // SP-> | out_arg_area        |   needs to be at end for shadow space
  //
  //

  //////////////////////////////////////////////////////////////////////////////

  MacroAssembler* _masm = new MacroAssembler(&buffer);
  address start = __ pc();
  __ enter(); // set up frame
  assert((abi._stack_alignment_bytes % 16) == 0, "must be 16 byte aligned");
  // allocate frame (frame_size is also aligned, so stack is still aligned)
  __ sub(sp, sp, frame_size);

  // we have to always spill args since we need to do a call to get the thread
  // (and maybe attach it).
  arg_spiller.generate_spill(_masm, arg_save_area_offset);
  preserve_callee_saved_registers(_masm, abi, reg_save_area_offset);

  __ block_comment("{ on_entry");
  __ lea(c_rarg0, Address(sp, frame_data_offset));
  __ movptr(rscratch1, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_entry));
  __ blr(rscratch1);
  __ mov(rthread, r0);
  __ reinit_heapbase();
  __ block_comment("} on_entry");

  __ block_comment("{ argument shuffle");
  arg_spiller.generate_fill(_masm, arg_save_area_offset);
  if (needs_return_buffer) {
    assert(ret_buf_offset != -1, "no return buffer allocated");
    __ lea(as_Register(locs.get(StubLocations::RETURN_BUFFER)), Address(sp, ret_buf_offset));
  }
  arg_shuffle.generate(_masm, as_VMStorage(shuffle_reg), abi._shadow_space_bytes, 0);
  __ block_comment("} argument shuffle");

  __ block_comment("{ load target ");
  __ movptr(j_rarg0, (intptr_t)receiver);
  __ far_call(RuntimeAddress(StubRoutines::upcall_stub_load_target()), rscratch1); // puts target Method* in rmethod
  __ block_comment("} load target ");

  __ push_cont_fastpath(rthread);

  __ ldr(rscratch1, Address(rmethod, Method::from_compiled_offset()));
  __ blr(rscratch1);

  __ pop_cont_fastpath(rthread);

    // return value shuffle
  if (!needs_return_buffer) {
#ifdef ASSERT
    if (call_regs._ret_regs.length() == 1) { // 0 or 1
      VMStorage j_expected_result_reg;
      switch (ret_type) {
        case T_BOOLEAN:
        case T_BYTE:
        case T_SHORT:
        case T_CHAR:
        case T_INT:
        case T_LONG:
        j_expected_result_reg = as_VMStorage(r0);
        break;
        case T_FLOAT:
        case T_DOUBLE:
          j_expected_result_reg = as_VMStorage(v0);
          break;
        default:
          fatal("unexpected return type: %s", type2name(ret_type));
      }
      // No need to move for now, since CallArranger can pick a return type
      // that goes in the same reg for both CCs. But, at least assert they are the same
      assert(call_regs._ret_regs.at(0) == j_expected_result_reg, "unexpected result register");
    }
#endif
  } else {
    assert(ret_buf_offset != -1, "no return buffer allocated");
    __ lea(rscratch1, Address(sp, ret_buf_offset));
    int offset = 0;
    for (int i = 0; i < call_regs._ret_regs.length(); i++) {
      VMStorage reg = call_regs._ret_regs.at(i);
      if (reg.type() == StorageType::INTEGER) {
        __ ldr(as_Register(reg), Address(rscratch1, offset));
        offset += 8;
      } else if (reg.type() == StorageType::VECTOR) {
        __ ldrd(as_FloatRegister(reg), Address(rscratch1, offset));
        offset += 16; // needs to match VECTOR_REG_SIZE in AArch64Architecture (Java)
      } else {
        ShouldNotReachHere();
      }
    }
  }

  result_spiller.generate_spill(_masm, res_save_area_offset);

  __ block_comment("{ on_exit");
  __ lea(c_rarg0, Address(sp, frame_data_offset));
  // stack already aligned
  __ movptr(rscratch1, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_exit));
  __ blr(rscratch1);
  __ block_comment("} on_exit");

  restore_callee_saved_registers(_masm, abi, reg_save_area_offset);

  result_spiller.generate_fill(_masm, res_save_area_offset);

  __ leave();
  __ ret(lr);

  //////////////////////////////////////////////////////////////////////////////

  _masm->flush();

#ifndef PRODUCT
  stringStream ss;
  ss.print("upcall_stub_%s", signature->as_C_string());
  const char* name = _masm->code_string(ss.as_string());
#else // PRODUCT
  const char* name = "upcall_stub";
#endif // PRODUCT

  buffer.log_section_sizes(name);

  UpcallStub* blob
    = UpcallStub::create(name,
                         &buffer,
                         receiver,
                         in_ByteSize(frame_data_offset));
  if (blob == nullptr) {
    return nullptr;
  }

#ifndef PRODUCT
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    blob->print_on(&ls);
  }
#endif

  return blob->code_begin();
}
