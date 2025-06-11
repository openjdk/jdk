/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "classfile/javaClasses.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"

#define __ _masm->

// for callee saved regs, according to the caller's ABI
static int compute_reg_save_area_size(const ABIDescriptor& abi) {
  int size = 0;
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    // Z_SP saved/restored by prologue/epilogue
    if (reg == Z_SP) continue;
    if (!abi.is_volatile_reg(reg)) {
      size += 8; // bytes
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
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

  __ block_comment("preserve_callee_saved_regs {");
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    // Z_SP saved/restored by prologue/epilogue
    if (reg == Z_SP) continue;
    if (!abi.is_volatile_reg(reg)) {
      __ z_stg(reg, Address(Z_SP, offset));
      offset += 8;
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
      __ z_std(reg, Address(Z_SP, offset));
      offset += 8;
    }
  }

  __ block_comment("} preserve_callee_saved_regs");
}

static void restore_callee_saved_registers(MacroAssembler* _masm, const ABIDescriptor& abi, int reg_save_area_offset) {
  // 1. iterate all registers in the architecture
  //     - check if they are volatile or not for the given abi
  //     - if NOT, we need to restore it here

  int offset = reg_save_area_offset;

  __ block_comment("restore_callee_saved_regs {");
  for (int i = 0; i < Register::number_of_registers; i++) {
    Register reg = as_Register(i);
    // Z_SP saved/restored by prologue/epilogue
    if (reg == Z_SP) continue;
    if (!abi.is_volatile_reg(reg)) {
      __ z_lg(reg, Address(Z_SP, offset));
      offset += 8;
    }
  }

  for (int i = 0; i < FloatRegister::number_of_registers; i++) {
    FloatRegister reg = as_FloatRegister(i);
    if (!abi.is_volatile_reg(reg)) {
      __ z_ld(reg, Address(Z_SP, offset));
      offset += 8;
    }
  }

  __ block_comment("} restore_callee_saved_regs");
}

static const int upcall_stub_code_base_size = 1024;
static const int upcall_stub_size_per_arg = 16; // arg save & restore + move
address UpcallLinker::make_upcall_stub(jobject receiver, Symbol* signature,
                                       BasicType* out_sig_bt, int total_out_args,
                                       BasicType ret_type,
                                       jobject jabi, jobject jconv,
                                       bool needs_return_buffer, int ret_buf_size) {
  ResourceMark rm;
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);
  const CallRegs call_regs = ForeignGlobals::parse_call_regs(jconv);
  int code_size = upcall_stub_code_base_size + (total_out_args * upcall_stub_size_per_arg);
  CodeBuffer buffer("upcall_stub", code_size, /* locs_size = */ 0);
  if (buffer.blob() == nullptr) {
    return nullptr;
  }

  Register call_target_address = Z_R1_scratch;

  GrowableArray<VMStorage> unfiltered_out_regs;
  int out_arg_bytes = ForeignGlobals::java_calling_convention(out_sig_bt, total_out_args, unfiltered_out_regs);

  // The Java call uses the JIT ABI, but we also call C.
  int out_arg_area = MAX2(frame::z_jit_out_preserve_size + out_arg_bytes, (int)frame::z_abi_160_size);

  int reg_save_area_size = compute_reg_save_area_size(abi);
  RegSpiller arg_spiller(call_regs._arg_regs);
  RegSpiller result_spiller(call_regs._ret_regs);

  int res_save_area_offset  = out_arg_area;
  int arg_save_area_offset  = res_save_area_offset  + result_spiller.spill_size_bytes();
  int reg_save_area_offset  = arg_save_area_offset  + arg_spiller.spill_size_bytes();
  int frame_data_offset     = reg_save_area_offset  + reg_save_area_size;
  int frame_bottom_offset   = frame_data_offset     + sizeof(UpcallStub::FrameData);

  StubLocations locs;
  VMStorage shuffle_reg = abi._scratch1;
  GrowableArray<VMStorage> in_regs = ForeignGlobals::replace_place_holders(call_regs._arg_regs, locs);
  GrowableArray<VMStorage> filtered_out_regs = ForeignGlobals::upcall_filter_receiver_reg(unfiltered_out_regs);
  ArgumentShuffle arg_shuffle(in_regs, filtered_out_regs, shuffle_reg);

#ifndef PRODUCT
  LogTarget(Trace, foreign, upcall) lt;
  if (lt.is_enabled()) {
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  int frame_size = align_up(frame_bottom_offset, StackAlignmentInBytes);

  // The space we have allocated will look like:
  //
  //
  // FP-> |                     |
  //      |---------------------| = frame_bottom_offset = frame_size
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

  __ save_return_pc();
  assert((abi._stack_alignment_bytes % StackAlignmentInBytes) == 0, "must be 8 byte aligned");
  // allocate frame (frame_size is also aligned, so stack is still aligned)
  __ push_frame(frame_size);

  // we have to always spill args since we need to do a call to get the thread
  // (and maybe attach it).
  arg_spiller.generate_spill(_masm, arg_save_area_offset);
  // Java methods won't preserve them, so save them here:
  preserve_callee_saved_registers(_masm, abi, reg_save_area_offset);

  __ block_comment("on_entry {");
  __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_entry));
  __ z_aghik(Z_ARG1, Z_SP, frame_data_offset);
  __ call(call_target_address);
  __ z_lgr(Z_thread, Z_RET);
  __ block_comment("} on_entry");

  arg_spiller.generate_fill(_masm, arg_save_area_offset);
  __ block_comment("argument_shuffle {");
  arg_shuffle.generate(_masm, shuffle_reg, abi._shadow_space_bytes, frame::z_jit_out_preserve_size);
  __ block_comment("} argument_shuffle");

  __ block_comment("load_target {");
  __ load_const_optimized(Z_ARG1, (intptr_t)receiver);
  __ load_const_optimized(call_target_address, StubRoutines::upcall_stub_load_target());
  __ call(call_target_address); // load taget Method* into Z_method
  __ block_comment("} load_target");

  __ z_lg(call_target_address, Address(Z_method, in_bytes(Method::from_compiled_offset())));
  __ call(call_target_address);

  // return value shuffle
  assert(!needs_return_buffer, "unexpected needs_return_buffer");
  // CallArranger can pick a return type that goes in the same reg for both CCs.
  if (call_regs._ret_regs.length() > 0) { // 0 or 1
    VMStorage ret_reg = call_regs._ret_regs.at(0);
    // Check if the return reg is as expected.
    switch (ret_type) {
      case T_BOOLEAN:
      case T_BYTE:
      case T_SHORT:
      case T_CHAR:
      case T_INT:
        __ z_lgfr(Z_RET, Z_RET); // Clear garbage in high half.
                                 // fallthrough
      case T_LONG:
        assert(as_Register(ret_reg) == Z_RET, "unexpected result register");
        break;
      case T_FLOAT:
      case T_DOUBLE:
        assert(as_FloatRegister(ret_reg) == Z_FRET, "unexpected result register");
        break;
      default:
        fatal("unexpected return type: %s", type2name(ret_type));
    }
  }

  result_spiller.generate_spill(_masm, res_save_area_offset);

  __ block_comment("on_exit {");
  __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_exit));
  __ z_aghik(Z_ARG1, Z_SP, frame_data_offset);
  __ call(call_target_address);
  __ block_comment("} on_exit");

  restore_callee_saved_registers(_masm, abi, reg_save_area_offset);

  result_spiller.generate_fill(_masm, res_save_area_offset);

  __ pop_frame();
  __ restore_return_pc();
  __ z_br(Z_R14);

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
    LogStream ls(lt);
    blob->print_on(&ls);
  }
#endif

  return blob->code_begin();
}
