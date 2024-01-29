/*
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
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
#include "asm/macroAssembler.inline.hpp"
#include "code/codeBlob.hpp"
#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/oopMap.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/downcallLinker.hpp"
#include "runtime/globals.hpp"
#include "runtime/stubCodeGenerator.hpp"

#define __ _masm->

static const int native_invoker_code_base_size = 384;
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
  int locs_size = 1; // must be non-zero
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

static constexpr int FP_BIAS = frame::jit_out_preserve_size;
static const Register callerSP = R2; // C/C++ uses R2 as TOC, but we can reuse it here

void DowncallLinker::StubGenerator::pd_add_offset_to_oop(VMStorage reg_oop, VMStorage reg_offset,
                                                         VMStorage tmp1, VMStorage tmp2) const {
  Register r_tmp1 = as_Register(tmp1);
  Register r_tmp2 = as_Register(tmp2);
  if (reg_oop.is_reg()) {
    assert(reg_oop.type() == StorageType::INTEGER, "expected");
    Register reg_oop_reg = as_Register(reg_oop);
    if (reg_offset.is_reg()) {
      assert(reg_offset.type() == StorageType::INTEGER, "expected");
      __ add(reg_oop_reg, reg_oop_reg, as_Register(reg_offset));
    } else {
      assert(reg_offset.is_stack(), "expected");
      assert(reg_offset.stack_size() == 8, "expected long");
      __ ld(r_tmp1, reg_offset.offset() + FP_BIAS, callerSP);
      __ add(reg_oop_reg, reg_oop_reg, r_tmp1);
    }
  } else {
    assert(reg_oop.is_stack(), "expected");
    assert(reg_oop.stack_size() == 8, "expected long");
    assert(reg_offset.is_stack(), "expected");
    assert(reg_offset.stack_size() == 8, "expected long");
    __ ld(r_tmp1, reg_offset.offset() + FP_BIAS, callerSP);
    __ ld(r_tmp2, reg_oop.offset() + FP_BIAS, callerSP);
    __ add(r_tmp1, r_tmp2, r_tmp1);
    __ std(r_tmp1, reg_oop.offset() + FP_BIAS, callerSP);
  }
}

void DowncallLinker::StubGenerator::generate() {
  Register tmp                 = R11_scratch1, // same as shuffle_reg
           call_target_address = R12_scratch2; // same as _abi._scratch2 (ABIv2 requires this reg!)

  // Stack frame size computation:
  // We use the number of input VMStorage elements because PPC64 requires slots for all arguments
  // (even if they are passed in registers), at least 8 (exception for ABIv2: see below).
  // This may be a bit more than needed when single precision HFA is used (see CallArranger.java).
  // (native_abi_reg_args is native_abi_minframe plus space for 8 argument register spill slots)
  assert(_abi._shadow_space_bytes == frame::native_abi_minframe_size, "expected space according to ABI");
  // The Parameter Save Area needs to be at least 8 slots for ABIv1.
  // ABIv2 allows omitting it if the callee's prototype indicates that all parameters can be passed in registers.
  // For ABIv2, we typically only need (_input_registers.length() > 8) ? _input_registers.length() : 0,
  // but this may be wrong for VarArgs. So, we currently don't optimize this.
  int parameter_save_area_slots = MAX2(_input_registers.length(), 8);
  int allocated_frame_size = frame::native_abi_minframe_size + parameter_save_area_slots * BytesPerWord;

  bool should_save_return_value = !_needs_return_buffer;
  RegSpiller out_reg_spiller(_output_registers);
  int spill_offset = -1;

  if (should_save_return_value) {
    spill_offset = frame::native_abi_reg_args_size;
    // Spill area can be shared with additional out args (>8),
    // since it is only used after the call.
    int frame_size_including_spill_area = frame::native_abi_reg_args_size + out_reg_spiller.spill_size_bytes();
    if (frame_size_including_spill_area > allocated_frame_size) {
      allocated_frame_size = frame_size_including_spill_area;
    }
  }

  StubLocations locs;
  assert(as_Register(_abi._scratch2) == call_target_address, "required by ABIv2");
  locs.set(StubLocations::TARGET_ADDRESS, _abi._scratch2);
  if (_needs_return_buffer) {
    locs.set_frame_data(StubLocations::RETURN_BUFFER, allocated_frame_size);
    allocated_frame_size += BytesPerWord; // for address spill
  }
  if (_captured_state_mask != 0) {
    locs.set_frame_data(StubLocations::CAPTURED_STATE_BUFFER, allocated_frame_size);
    allocated_frame_size += BytesPerWord;
  }

  GrowableArray<VMStorage> java_regs;
  ForeignGlobals::java_calling_convention(_signature, _num_args, java_regs);
  bool has_objects = false;
  GrowableArray<VMStorage> filtered_java_regs = ForeignGlobals::downcall_filter_offset_regs(java_regs, _signature,
                                                                                             _num_args, has_objects);
  assert(!(_needs_transition && has_objects), "can not pass objects when doing transition");

  GrowableArray<VMStorage> out_regs = ForeignGlobals::replace_place_holders(_input_registers, locs);

  ArgumentShuffle arg_shuffle(filtered_java_regs, out_regs, _abi._scratch1);

#ifndef PRODUCT
  LogTarget(Trace, foreign, downcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  allocated_frame_size = align_up(allocated_frame_size, StackAlignmentInBytes);
  _frame_size_slots = allocated_frame_size >> LogBytesPerInt;

  _oop_maps  = _needs_transition ? new OopMapSet() : nullptr;
  address start = __ pc();

  __ save_LR_CR(tmp); // Save in old frame.
  __ mr(callerSP, R1_SP); // preset (used to access caller frame argument slots)
  __ push_frame(allocated_frame_size, tmp);

  _frame_complete = __ pc() - start;

  if (_needs_transition) {
    address the_pc = __ pc();
    __ calculate_address_from_global_toc(tmp, the_pc, true, true, true, true);
    __ set_last_Java_frame(R1_SP, tmp);
    OopMap* map = new OopMap(_frame_size_slots, 0);
    _oop_maps->add_gc_map(the_pc - start, map);

    // State transition
    __ li(R0, _thread_in_native);
    __ release();
    __ stw(R0, in_bytes(JavaThread::thread_state_offset()), R16_thread);
  }

  if (has_objects) {
    add_offsets_to_oops(java_regs, _abi._scratch1, _abi._scratch2);
  }

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, as_VMStorage(callerSP), frame::jit_out_preserve_size, frame::native_abi_minframe_size);
  __ block_comment("} argument shuffle");

  __ call_c(call_target_address);

  if (_needs_return_buffer) {
    // Store return values as required by BoxBindingCalculator.
    __ ld(tmp, locs.data_offset(StubLocations::RETURN_BUFFER), R1_SP);
    int offset = 0;
    for (int i = 0; i < _output_registers.length(); i++) {
      VMStorage reg = _output_registers.at(i);
      if (reg.type() == StorageType::INTEGER) {
        // Store in matching size (not relevant for little endian).
        if (reg.segment_mask() == REG32_MASK) {
          __ stw(as_Register(reg), offset, tmp);
        } else {
          __ std(as_Register(reg), offset, tmp);
        }
        offset += 8;
      } else if (reg.type() == StorageType::FLOAT) {
        // Java code doesn't perform float-double format conversions. Do it here.
        if (reg.segment_mask() == REG32_MASK) {
          __ stfs(as_FloatRegister(reg), offset, tmp);
        } else {
          __ stfd(as_FloatRegister(reg), offset, tmp);
        }
        offset += 8;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////

  if (_captured_state_mask != 0) {
    __ block_comment("{ save thread local");

    if (should_save_return_value) {
      out_reg_spiller.generate_spill(_masm, spill_offset);
    }

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, DowncallLinker::capture_state), R0);
    __ ld(R3_ARG1, locs.data_offset(StubLocations::CAPTURED_STATE_BUFFER), R1_SP);
    __ load_const_optimized(R4_ARG2, _captured_state_mask, R0);
    __ call_c(call_target_address);

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_offset);
    }

    __ block_comment("} save thread local");
  }

  //////////////////////////////////////////////////////////////////////////////

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;
  Label L_reguard;
  Label L_after_reguard;

  if (_needs_transition) {
    __ li(tmp, _thread_in_native_trans);
    __ release();
    __ stw(tmp, in_bytes(JavaThread::thread_state_offset()), R16_thread);
    if (!UseSystemMemoryBarrier) {
      __ fence(); // Order state change wrt. safepoint poll.
    }

    __ safepoint_poll(L_safepoint_poll_slow_path, tmp, true /* at_return */, false /* in_nmethod */);

    __ lwz(tmp, in_bytes(JavaThread::suspend_flags_offset()), R16_thread);
    __ cmpwi(CCR0, tmp, 0);
    __ bne(CCR0, L_safepoint_poll_slow_path);
    __ bind(L_after_safepoint_poll);

    // change thread state
    __ li(tmp, _thread_in_Java);
    __ lwsync(); // Acquire safepoint and suspend state, release thread state.
    __ stw(tmp, in_bytes(JavaThread::thread_state_offset()), R16_thread);

    __ block_comment("reguard stack check");
    __ lwz(tmp, in_bytes(JavaThread::stack_guard_state_offset()), R16_thread);
    __ cmpwi(CCR0, tmp, StackOverflow::stack_guard_yellow_reserved_disabled);
    __ beq(CCR0, L_reguard);
    __ bind(L_after_reguard);

    __ reset_last_Java_frame();
  }

  __ pop_frame();
  __ restore_LR_CR(tmp);
  __ blr();

  //////////////////////////////////////////////////////////////////////////////

  if (_needs_transition) {
    __ block_comment("{ L_safepoint_poll_slow_path");
    __ bind(L_safepoint_poll_slow_path);

    if (should_save_return_value) {
      // Need to save the native result registers around any runtime calls.
      out_reg_spiller.generate_spill(_masm, spill_offset);
    }

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, JavaThread::check_special_condition_for_native_trans), R0);
    __ mr(R3_ARG1, R16_thread);
    __ call_c(call_target_address);

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_offset);
    }

    __ b(L_after_safepoint_poll);
    __ block_comment("} L_safepoint_poll_slow_path");

    //////////////////////////////////////////////////////////////////////////////

    __ block_comment("{ L_reguard");
    __ bind(L_reguard);

    if (should_save_return_value) {
      out_reg_spiller.generate_spill(_masm, spill_offset);
    }

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, SharedRuntime::reguard_yellow_pages), R0);
    __ call_c(call_target_address);

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_offset);
    }

    __ b(L_after_reguard);

    __ block_comment("} L_reguard");
  }

  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}
