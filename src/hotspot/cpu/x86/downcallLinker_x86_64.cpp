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
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "prims/downcallLinker.hpp"
#include "runtime/globals.hpp"
#include "runtime/sharedRuntime.hpp"
#include "utilities/formatBuffer.hpp"

#define __ _masm->

static const int native_invoker_code_base_size = 512;
static const int native_invoker_size_per_arg = 8;

RuntimeStub* DowncallLinker::make_downcall_stub(BasicType* signature,
                                                int num_args,
                                                BasicType ret_bt,
                                                const ABIDescriptor& abi,
                                                const GrowableArray<VMStorage>& input_registers,
                                                const GrowableArray<VMStorage>& output_registers,
                                                bool needs_return_buffer,
                                                int captured_state_mask,
                                                bool needs_transition) {
  int code_size = native_invoker_code_base_size + (num_args * native_invoker_size_per_arg);
  int locs_size = 1; // can not be zero
  CodeBuffer code("nep_invoker_blob", code_size, locs_size);
  if (code.blob() == nullptr) {
    return nullptr;
  }
  StubGenerator g(&code, signature, num_args, ret_bt, abi,
                  input_registers, output_registers,
                  needs_return_buffer, captured_state_mask,
                  needs_transition);
  g.generate();
  code.log_section_sizes("nep_invoker_blob");

  bool caller_must_gc_arguments = false;
  bool alloc_fail_is_fatal = false;
  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub("nep_invoker_blob",
                                  &code,
                                  g.frame_complete(),
                                  g.framesize(),
                                  g.oop_maps(),
                                  caller_must_gc_arguments,
                                  alloc_fail_is_fatal);
  if (stub == nullptr) {
    return nullptr;
  }

#ifndef PRODUCT
  LogTarget(Trace, foreign, downcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    stub->print_on(&ls);
  }
#endif

  return stub;
}

static constexpr int RBP_BIAS = 16; // skip old rbp and return address

void DowncallLinker::StubGenerator::pd_add_offset_to_oop(VMStorage reg_oop, VMStorage reg_offset,
                                                         VMStorage tmp1, VMStorage tmp2) const {
  if (reg_oop.is_reg()) {
    assert(reg_oop.type() == StorageType::INTEGER, "expected");
    if (reg_offset.is_reg()) {
      assert(reg_offset.type() == StorageType::INTEGER, "expected");
      __ addptr(as_Register(reg_oop), as_Register(reg_offset));
    } else {
      assert(reg_offset.is_stack(), "expected");
      Address offset_addr(rbp, RBP_BIAS + reg_offset.offset());
      __ addptr(as_Register(reg_oop), offset_addr);
    }
  } else {
    assert(reg_oop.is_stack(), "expected");
    assert(reg_offset.is_stack(), "expected");
    Address offset_addr(rbp, RBP_BIAS + reg_offset.offset());
    Address oop_addr(rbp, RBP_BIAS + reg_oop.offset());
    __ movptr(as_Register(tmp1), offset_addr);
    __ addptr(oop_addr, as_Register(tmp1));
  }
}

static void runtime_call(MacroAssembler* _masm, address target) {
  __ vzeroupper();
  __ mov(r12, rsp); // remember sp
  __ subptr(rsp, frame::arg_reg_save_area_bytes); // windows
  __ andptr(rsp, -16); // align stack as required by ABI
  __ call(RuntimeAddress(target));
  __ mov(rsp, r12); // restore sp
  __ reinit_heapbase();
}

void DowncallLinker::StubGenerator::generate() {
  enum layout {
    rbp_off,
    rbp_off2,
    return_off,
    return_off2,
    framesize_base // inclusive of return address
    // The following are also computed dynamically:
    // shadow space
    // spill area
    // out arg area (e.g. for stack args)
  };

  GrowableArray<VMStorage> java_regs;
  ForeignGlobals::java_calling_convention(_signature, _num_args, java_regs);
  bool has_objects = false;
  GrowableArray<VMStorage> filtered_java_regs = ForeignGlobals::downcall_filter_offset_regs(java_regs, _signature,
                                                                                             _num_args, has_objects);
  assert(!(_needs_transition && has_objects), "can not pass objects when doing transition");

  // in bytes
  int allocated_frame_size = 0;
  allocated_frame_size += _abi._shadow_space_bytes;
  allocated_frame_size += ForeignGlobals::compute_out_arg_bytes(_input_registers);

  // when we don't use a return buffer we need to spill the return value around our slow path calls
  bool should_save_return_value = !_needs_return_buffer;
  RegSpiller out_reg_spiller(_output_registers);
  int spill_rsp_offset = -1;

  if (should_save_return_value) {
    spill_rsp_offset = 0;
    // spill area can be shared with shadow space and out args,
    // since they are only used before the call,
    // and spill area is only used after.
    allocated_frame_size = out_reg_spiller.spill_size_bytes() > allocated_frame_size
      ? out_reg_spiller.spill_size_bytes()
      : allocated_frame_size;
  }

  StubLocations locs;
  locs.set(StubLocations::TARGET_ADDRESS, _abi._scratch1);
  if (_needs_return_buffer) {
    locs.set_frame_data(StubLocations::RETURN_BUFFER, allocated_frame_size);
    allocated_frame_size += BytesPerWord;
  }
  if (_captured_state_mask != 0) {
    locs.set_frame_data(StubLocations::CAPTURED_STATE_BUFFER, allocated_frame_size);
    allocated_frame_size += BytesPerWord;
  }

  // The space we have allocated will look like:
  //
  // FP-> |                     |
  //      |---------------------| = frame_bottom_offset = frame_size
  //      | (optional)          |
  //      | capture state buf   |
  //      |---------------------| = StubLocations::CAPTURED_STATE_BUFFER
  //      | (optional)          |
  //      | return buffer       |
  //      |---------------------| = StubLocations::RETURN_BUFFER
  //      | out/stack args      |    |                      |
  //      |---------------------| or | ret. val. spill area |
  // SP-> | shadow space        |    |                      |
  //
  // Note how the last chunk can be shared, since the 3 uses occur at different times.

  GrowableArray<VMStorage> out_regs = ForeignGlobals::replace_place_holders(_input_registers, locs);
  VMStorage shuffle_reg = as_VMStorage(rbx);
  ArgumentShuffle arg_shuffle(filtered_java_regs, out_regs, shuffle_reg);

#ifndef PRODUCT
  LogTarget(Trace, foreign, downcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  allocated_frame_size = align_up(allocated_frame_size, 16);
  _frame_size_slots += framesize_base + (allocated_frame_size >> LogBytesPerInt);
  assert(is_even(_frame_size_slots/2), "sp not 16-byte aligned");

  _oop_maps  = _needs_transition ? new OopMapSet() : nullptr;
  address start = __ pc();

  __ enter();

  // return address and rbp are already in place
  if (allocated_frame_size > 0) {
    __ subptr(rsp, allocated_frame_size); // prolog
  }

  _frame_complete = __ pc() - start;

  if (_needs_transition) {
    __ block_comment("{ thread java2native");
    address the_pc = __ pc();
    __ set_last_Java_frame(rsp, rbp, (address)the_pc, rscratch1);
    OopMap* map = new OopMap(_frame_size_slots, 0);
    _oop_maps->add_gc_map(the_pc - start, map);

    // State transition
    __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native);
    __ block_comment("} thread java2native");
  }

  if (has_objects) {
    add_offsets_to_oops(java_regs, as_VMStorage(rscratch1), VMStorage::invalid());
  }

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, shuffle_reg, 0, _abi._shadow_space_bytes);
  __ block_comment("} argument shuffle");

  __ call(as_Register(locs.get(StubLocations::TARGET_ADDRESS)));
  assert(!_abi.is_volatile_reg(r15_thread), "Call assumed not to kill r15");

  if (_needs_return_buffer) {
    __ movptr(rscratch1, Address(rsp, locs.data_offset(StubLocations::RETURN_BUFFER)));
    int offset = 0;
    for (int i = 0; i < _output_registers.length(); i++) {
      VMStorage reg = _output_registers.at(i);
      if (reg.type() == StorageType::INTEGER) {
        __ movptr(Address(rscratch1, offset), as_Register(reg));
        offset += 8;
      } else if (reg.type() == StorageType::VECTOR) {
        __ movdqu(Address(rscratch1, offset), as_XMMRegister(reg));
        offset += 16;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  if (_captured_state_mask != 0) {
    __ block_comment("{ save thread local");

    if (should_save_return_value) {
      out_reg_spiller.generate_spill(_masm, spill_rsp_offset);
    }

    __ movptr(c_rarg0, Address(rsp, locs.data_offset(StubLocations::CAPTURED_STATE_BUFFER)));
    __ movl(c_rarg1, _captured_state_mask);
    runtime_call(_masm, CAST_FROM_FN_PTR(address, DowncallLinker::capture_state));

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_rsp_offset);
    }

    __ block_comment("} save thread local");
  }

  //////////////////////////////////////////////////////////////////////////////

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;
  Label L_reguard;
  Label L_after_reguard;
  if (_needs_transition) {
    __ block_comment("{ thread native2java");
    __ restore_cpu_control_state_after_jni(rscratch1);

    __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_native_trans);

    // Force this write out before the read below
    if (!UseSystemMemoryBarrier) {
      __ membar(Assembler::Membar_mask_bits(
              Assembler::LoadLoad | Assembler::LoadStore |
              Assembler::StoreLoad | Assembler::StoreStore));
    }

    __ safepoint_poll(L_safepoint_poll_slow_path, r15_thread, true /* at_return */, false /* in_nmethod */);
    __ cmpl(Address(r15_thread, JavaThread::suspend_flags_offset()), 0);
    __ jcc(Assembler::notEqual, L_safepoint_poll_slow_path);

    __ bind(L_after_safepoint_poll);

    // change thread state
    __ movl(Address(r15_thread, JavaThread::thread_state_offset()), _thread_in_Java);

    __ block_comment("reguard stack check");
    __ cmpl(Address(r15_thread, JavaThread::stack_guard_state_offset()), StackOverflow::stack_guard_yellow_reserved_disabled);
    __ jcc(Assembler::equal, L_reguard);
    __ bind(L_after_reguard);

    __ reset_last_Java_frame(r15_thread, true);
    __ block_comment("} thread native2java");
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(0);

  //////////////////////////////////////////////////////////////////////////////

  if (_needs_transition) {
    __ block_comment("{ L_safepoint_poll_slow_path");
    __ bind(L_safepoint_poll_slow_path);

    if (should_save_return_value) {
      out_reg_spiller.generate_spill(_masm, spill_rsp_offset);
    }

    __ mov(c_rarg0, r15_thread);
    runtime_call(_masm, CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans));

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_rsp_offset);
    }

    __ jmp(L_after_safepoint_poll);
    __ block_comment("} L_safepoint_poll_slow_path");

  //////////////////////////////////////////////////////////////////////////////

    __ block_comment("{ L_reguard");
    __ bind(L_reguard);

    if (should_save_return_value) {
      out_reg_spiller.generate_spill(_masm, spill_rsp_offset);
    }

    runtime_call(_masm, CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages));

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_rsp_offset);
    }

    __ jmp(L_after_reguard);

    __ block_comment("} L_reguard");
  }
  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}
