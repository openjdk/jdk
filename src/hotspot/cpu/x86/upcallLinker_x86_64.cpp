/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "code/codeBlob.hpp"
#include "code/codeBlob.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/disassembler.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "prims/upcallLinker.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/signature.hpp"
#include "runtime/stubRoutines.hpp"
#include "utilities/formatBuffer.hpp"
#include "utilities/globalDefinitions.hpp"

#define __ _masm->

static bool is_valid_XMM(XMMRegister reg) {
  return reg->is_valid() && (UseAVX >= 3 || (reg->encoding() < 16)); // why is this not covered by is_valid()?
}

// for callee saved regs, according to the caller's ABI
static int compute_reg_save_area_size(const ABIDescriptor& abi) {
  int size = 0;
  for (Register reg = as_Register(0); reg->is_valid(); reg = reg->successor()) {
    if (reg == rbp || reg == rsp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      size += 8; // bytes
    }
  }

  for (XMMRegister reg = as_XMMRegister(0); is_valid_XMM(reg); reg = reg->successor()) {
    if (!abi.is_volatile_reg(reg)) {
      if (UseAVX >= 3) {
        size += 64; // bytes
      } else if (UseAVX >= 1) {
        size += 32;
      } else {
        size += 16;
      }
    }
  }

#ifndef _WIN64
  // for mxcsr
  size += 8;
#endif

  return size;
}

constexpr int MXCSR_MASK = 0xFFC0;  // Mask out any pending exceptions

static void preserve_callee_saved_registers(MacroAssembler* _masm, const ABIDescriptor& abi, int reg_save_area_offset) {
  // 1. iterate all registers in the architecture
  //     - check if they are volatile or not for the given abi
  //     - if NOT, we need to save it here
  // 2. save mxcsr on non-windows platforms

  int offset = reg_save_area_offset;

  __ block_comment("{ preserve_callee_saved_regs ");
  for (Register reg = as_Register(0); reg->is_valid(); reg = reg->successor()) {
    if (reg == rbp || reg == rsp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      __ movptr(Address(rsp, offset), reg);
      offset += 8;
    }
  }

  for (XMMRegister reg = as_XMMRegister(0); is_valid_XMM(reg); reg = reg->successor()) {
    if (!abi.is_volatile_reg(reg)) {
      if (UseAVX >= 3) {
        __ evmovdqul(Address(rsp, offset), reg, Assembler::AVX_512bit);
        offset += 64;
      } else if (UseAVX >= 1) {
        __ vmovdqu(Address(rsp, offset), reg);
        offset += 32;
      } else {
        __ movdqu(Address(rsp, offset), reg);
        offset += 16;
      }
    }
  }

#ifndef _WIN64
  {
    const Address mxcsr_save(rsp, offset);
    Label skip_ldmx;
    __ stmxcsr(mxcsr_save);
    __ movl(rax, mxcsr_save);
    __ andl(rax, MXCSR_MASK);    // Only check control and mask bits
    ExternalAddress mxcsr_std(StubRoutines::x86::addr_mxcsr_std());
    __ cmp32(rax, mxcsr_std, rscratch1);
    __ jcc(Assembler::equal, skip_ldmx);
    __ ldmxcsr(mxcsr_std, rscratch1);
    __ bind(skip_ldmx);
  }
#endif

  __ block_comment("} preserve_callee_saved_regs ");
}

static void restore_callee_saved_registers(MacroAssembler* _masm, const ABIDescriptor& abi, int reg_save_area_offset) {
  // 1. iterate all registers in the architecture
  //     - check if they are volatile or not for the given abi
  //     - if NOT, we need to restore it here
  // 2. restore mxcsr on non-windows platforms

  int offset = reg_save_area_offset;

  __ block_comment("{ restore_callee_saved_regs ");
  for (Register reg = as_Register(0); reg->is_valid(); reg = reg->successor()) {
    if (reg == rbp || reg == rsp) continue; // saved/restored by prologue/epilogue
    if (!abi.is_volatile_reg(reg)) {
      __ movptr(reg, Address(rsp, offset));
      offset += 8;
    }
  }

  for (XMMRegister reg = as_XMMRegister(0); is_valid_XMM(reg); reg = reg->successor()) {
    if (!abi.is_volatile_reg(reg)) {
      if (UseAVX >= 3) {
        __ evmovdqul(reg, Address(rsp, offset), Assembler::AVX_512bit);
        offset += 64;
      } else if (UseAVX >= 1) {
        __ vmovdqu(reg, Address(rsp, offset));
        offset += 32;
      } else {
        __ movdqu(reg, Address(rsp, offset));
        offset += 16;
      }
    }
  }

#ifndef _WIN64
  const Address mxcsr_save(rsp, offset);
  __ ldmxcsr(mxcsr_save);
#endif

  __ block_comment("} restore_callee_saved_regs ");
}

static const int upcall_stub_code_base_size = 1024;
static const int upcall_stub_size_per_arg = 16;

address UpcallLinker::make_upcall_stub(jobject receiver, Method* entry,
                                       BasicType* in_sig_bt, int total_in_args,
                                       BasicType* out_sig_bt, int total_out_args,
                                       BasicType ret_type,
                                       jobject jabi, jobject jconv,
                                       bool needs_return_buffer, int ret_buf_size) {
  const ABIDescriptor abi = ForeignGlobals::parse_abi_descriptor(jabi);
  const CallRegs call_regs = ForeignGlobals::parse_call_regs(jconv);
  int code_size = upcall_stub_code_base_size + (total_in_args * upcall_stub_size_per_arg);
  CodeBuffer buffer("upcall_stub", code_size, /* locs_size = */ 1);

  VMStorage shuffle_reg = as_VMStorage(rbx);
  JavaCallingConvention out_conv;
  NativeCallingConvention in_conv(call_regs._arg_regs);
  ArgumentShuffle arg_shuffle(in_sig_bt, total_in_args, out_sig_bt, total_out_args, &in_conv, &out_conv, shuffle_reg);
  int preserved_bytes = SharedRuntime::out_preserve_stack_slots() * VMRegImpl::stack_slot_size;
  int stack_bytes = preserved_bytes + arg_shuffle.out_arg_bytes();
  int out_arg_area = align_up(stack_bytes , StackAlignmentInBytes);

#ifndef PRODUCT
  LogTarget(Trace, foreign, upcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  // out_arg_area (for stack arguments) doubles as shadow space for native calls.
  // make sure it is big enough.
  if (out_arg_area < frame::arg_reg_save_area_bytes) {
    out_arg_area = frame::arg_reg_save_area_bytes;
  }

  int reg_save_area_size = compute_reg_save_area_size(abi);
  RegSpiller arg_spilller(call_regs._arg_regs);
  RegSpiller result_spiller(call_regs._ret_regs);

  int shuffle_area_offset    = 0;
  int res_save_area_offset   = shuffle_area_offset    + out_arg_area;
  int arg_save_area_offset   = res_save_area_offset   + result_spiller.spill_size_bytes();
  int reg_save_area_offset   = arg_save_area_offset   + arg_spilller.spill_size_bytes();
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

  int frame_size = frame_bottom_offset;
  frame_size = align_up(frame_size, StackAlignmentInBytes);

  // Ok The space we have allocated will look like:
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
  if ((abi._stack_alignment_bytes % 16) != 0) {
    // stack alignment of caller is not a multiple of 16
    __ andptr(rsp, -StackAlignmentInBytes); // align stack
  }
  // allocate frame (frame_size is also aligned, so stack is still aligned)
  __ subptr(rsp, frame_size);

  // we have to always spill args since we need to do a call to get the thread
  // (and maybe attach it).
  arg_spilller.generate_spill(_masm, arg_save_area_offset);

  preserve_callee_saved_registers(_masm, abi, reg_save_area_offset);

  __ block_comment("{ on_entry");
  __ vzeroupper();
  __ lea(c_rarg0, Address(rsp, frame_data_offset));
  __ movptr(c_rarg1, (intptr_t)receiver);
  // stack already aligned
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, UpcallLinker::on_entry)));
  __ movptr(r15_thread, rax);
  __ reinit_heapbase();
  __ block_comment("} on_entry");

  __ block_comment("{ argument shuffle");
  arg_spilller.generate_fill(_masm, arg_save_area_offset);
  if (needs_return_buffer) {
    assert(ret_buf_offset != -1, "no return buffer allocated");
    __ lea(as_Register(locs.get(StubLocations::RETURN_BUFFER)), Address(rsp, ret_buf_offset));
  }
  arg_shuffle.generate(_masm, shuffle_reg, abi._shadow_space_bytes, 0, locs);
  __ block_comment("} argument shuffle");

  __ block_comment("{ receiver ");
  __ get_vm_result(j_rarg0, r15_thread);
  __ block_comment("} receiver ");

  __ mov_metadata(rbx, entry);
  __ movptr(Address(r15_thread, JavaThread::callee_target_offset()), rbx); // just in case callee is deoptimized

  __ call(Address(rbx, Method::from_compiled_offset()));

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
        j_expected_result_reg = as_VMStorage(rax);
        break;
        case T_FLOAT:
        case T_DOUBLE:
          j_expected_result_reg = as_VMStorage(xmm0);
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
    __ lea(rscratch1, Address(rsp, ret_buf_offset));
    int offset = 0;
    for (int i = 0; i < call_regs._ret_regs.length(); i++) {
      VMStorage reg = call_regs._ret_regs.at(i);
      if (reg.type() == StorageType::INTEGER) {
        __ movptr(as_Register(reg), Address(rscratch1, offset));
        offset += 8;
      } else if (reg.type() == StorageType::VECTOR) {
        __ movdqu(as_XMMRegister(reg), Address(rscratch1, offset));
        offset += 16;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  result_spiller.generate_spill(_masm, res_save_area_offset);

  __ block_comment("{ on_exit");
  __ vzeroupper();
  __ lea(c_rarg0, Address(rsp, frame_data_offset));
  // stack already aligned
  __ call(RuntimeAddress(CAST_FROM_FN_PTR(address, UpcallLinker::on_exit)));
  __ reinit_heapbase();
  __ block_comment("} on_exit");

  restore_callee_saved_registers(_masm, abi, reg_save_area_offset);

  result_spiller.generate_fill(_masm, res_save_area_offset);

  __ leave();
  __ ret(0);

  //////////////////////////////////////////////////////////////////////////////

  _masm->flush();

#ifndef PRODUCT
  stringStream ss;
  ss.print("upcall_stub_%s", entry->signature()->as_C_string());
  const char* name = _masm->code_string(ss.freeze());
#else // PRODUCT
  const char* name = "upcall_stub";
#endif // PRODUCT

  buffer.log_section_sizes(name);

  UpcallStub* blob
    = UpcallStub::create(name,
                         &buffer,
                         receiver,
                         in_ByteSize(frame_data_offset));

#ifndef PRODUCT
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    blob->print_on(&ls);
  }
#endif

  return blob->code_begin();
}
