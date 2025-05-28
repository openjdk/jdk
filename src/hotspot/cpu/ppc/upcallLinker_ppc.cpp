/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2025 SAP SE. All rights reserved.
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
  CodeBuffer buffer("upcall_stub", code_size, /* locs_size = */ 1);
  if (buffer.blob() == nullptr) {
    return nullptr;
  }

  Register callerSP            = R2, // C/C++ uses R2 as TOC, but we can reuse it here
           tmp                 = R11_scratch1, // same as shuffle_reg
           call_target_address = R12_scratch2; // same as _abi._scratch2
  GrowableArray<VMStorage> unfiltered_out_regs;
  int out_arg_bytes = ForeignGlobals::java_calling_convention(out_sig_bt, total_out_args, unfiltered_out_regs);
  // The Java call uses the JIT ABI, but we also call C.
  int out_arg_area = MAX2(frame::jit_out_preserve_size + out_arg_bytes, (int)frame::native_abi_reg_args_size);

  MacroAssembler* _masm = new MacroAssembler(&buffer);
  int reg_save_area_size = __ save_nonvolatile_registers_size(true, SuperwordUseVSX);
  RegSpiller arg_spiller(call_regs._arg_regs);
  RegSpiller result_spiller(call_regs._ret_regs);

  int res_save_area_offset   = out_arg_area;
  int arg_save_area_offset   = res_save_area_offset   + result_spiller.spill_size_bytes();
  int reg_save_area_offset   = arg_save_area_offset   + arg_spiller.spill_size_bytes();
  if (SuperwordUseVSX) { // VectorRegisters want alignment
    reg_save_area_offset = align_up(reg_save_area_offset, StackAlignmentInBytes);
  }
  int frame_data_offset      = reg_save_area_offset   + reg_save_area_size;
  int frame_bottom_offset    = frame_data_offset      + sizeof(UpcallStub::FrameData);

  StubLocations locs;
  int ret_buf_offset = -1;
  if (needs_return_buffer) {
    ret_buf_offset = frame_bottom_offset;
    frame_bottom_offset += ret_buf_size;
    // use a free register for shuffling code to pick up return
    // buffer address from
    locs.set(StubLocations::RETURN_BUFFER, abi._scratch2);
  }

  GrowableArray<VMStorage> in_regs = ForeignGlobals::replace_place_holders(call_regs._arg_regs, locs);
  GrowableArray<VMStorage> filtered_out_regs = ForeignGlobals::upcall_filter_receiver_reg(unfiltered_out_regs);
  ArgumentShuffle arg_shuffle(in_regs, filtered_out_regs, abi._scratch1);

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

  address start = __ function_entry(); // called by C
  __ save_LR_CR(R0);
  assert((abi._stack_alignment_bytes % 16) == 0, "must be 16 byte aligned");
  // allocate frame (frame_size is also aligned, so stack is still aligned)
  __ push_frame(frame_size, tmp);

  // we have to always spill args since we need to do a call to get the thread
  // (and maybe attach it).
  arg_spiller.generate_spill(_masm, arg_save_area_offset);
  // Java methods won't preserve them, so save them here:
  __ save_nonvolatile_registers(R1_SP, reg_save_area_offset, true, SuperwordUseVSX);

  // Java code uses TOC (pointer to code cache).
  __ load_const_optimized(R29_TOC, MacroAssembler::global_toc(), R0); // reinit

  __ block_comment("{ on_entry");
  __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_entry), R0);
  __ addi(R3_ARG1, R1_SP, frame_data_offset);
  __ call_c(call_target_address);
  __ mr(R16_thread, R3_RET);
  __ block_comment("} on_entry");

  __ block_comment("{ argument shuffle");
  arg_spiller.generate_fill(_masm, arg_save_area_offset);
  if (needs_return_buffer) {
    assert(ret_buf_offset != -1, "no return buffer allocated");
    __ addi(as_Register(locs.get(StubLocations::RETURN_BUFFER)), R1_SP, ret_buf_offset);
  }
  __ ld(callerSP, _abi0(callers_sp), R1_SP); // preset (used to access caller frame argument slots)
  arg_shuffle.generate(_masm, as_VMStorage(callerSP), frame::native_abi_minframe_size, frame::jit_out_preserve_size);
  __ block_comment("} argument shuffle");

  __ block_comment("{ load target ");
  __ load_const_optimized(call_target_address, StubRoutines::upcall_stub_load_target(), R0);
  __ load_const_optimized(R3_ARG1, (intptr_t)receiver, R0);
  __ mtctr(call_target_address);
  __ bctrl(); // loads target Method* into R19_method
  __ block_comment("} load target ");

  __ push_cont_fastpath();

  __ ld(call_target_address, in_bytes(Method::from_compiled_offset()), R19_method);
  __ mtctr(call_target_address);
  __ bctrl();

  __ pop_cont_fastpath();

  // return value shuffle
  if (!needs_return_buffer) {
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
          __ extsw(R3_RET, R3_RET); // Clear garbage in high half.
          // fallthrough
        case T_LONG:
          assert(as_Register(ret_reg) == R3_RET, "unexpected result register");
          break;
        case T_FLOAT:
        case T_DOUBLE:
          assert(as_FloatRegister(ret_reg) == F1_RET, "unexpected result register");
          break;
        default:
          fatal("unexpected return type: %s", type2name(ret_type));
      }
    }
  } else {
    // Load return values as required by UnboxBindingCalculator.
    assert(ret_buf_offset != -1, "no return buffer allocated");
    int offset = ret_buf_offset;
    for (int i = 0; i < call_regs._ret_regs.length(); i++) {
      VMStorage reg = call_regs._ret_regs.at(i);
      if (reg.type() == StorageType::INTEGER) {
        // Load in matching size (not relevant for little endian).
        if (reg.segment_mask() == REG32_MASK) {
          __ lwa(as_Register(reg), offset, R1_SP);
        } else {
          __ ld(as_Register(reg), offset, R1_SP);
        }
        offset += 8;
      } else if (reg.type() == StorageType::FLOAT) {
        // Java code doesn't perform float-double format conversions. Do it here.
        if (reg.segment_mask() == REG32_MASK) {
          __ lfs(as_FloatRegister(reg), offset, R1_SP);
        } else {
          __ lfd(as_FloatRegister(reg), offset, R1_SP);
        }
        offset += 8;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  result_spiller.generate_spill(_masm, res_save_area_offset);

  __ block_comment("{ on_exit");
  __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, UpcallLinker::on_exit), R0);
  __ addi(R3_ARG1, R1_SP, frame_data_offset);
  __ call_c(call_target_address);
  __ block_comment("} on_exit");

  __ restore_nonvolatile_registers(R1_SP, reg_save_area_offset, true, SuperwordUseVSX);

  result_spiller.generate_fill(_masm, res_save_area_offset);

  __ pop_frame();
  __ restore_LR_CR(R0);
  __ blr();

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

#ifndef ABI_ELFv2
  // Need to patch the FunctionDescriptor after relocating.
  address fd_addr = blob->code_begin();
  FunctionDescriptor* fd = (FunctionDescriptor*)fd_addr;
  fd->set_entry(fd_addr + sizeof(FunctionDescriptor));
#endif

#ifndef PRODUCT
  if (lt.is_enabled()) {
    LogStream ls(lt);
    blob->print_on(&ls);
  }
#endif

  return blob->code_begin();
}
