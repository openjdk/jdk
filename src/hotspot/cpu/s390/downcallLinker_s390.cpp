/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

class DowncallStubGenerator : public StubCodeGenerator {
  BasicType* _signature;
  int _num_args;
  BasicType _ret_bt;
  const ABIDescriptor& _abi;

  const GrowableArray<VMStorage>&  _input_registers;
  const GrowableArray<VMStorage>&  _output_registers;

  bool _needs_return_buffer;
  int _captured_state_mask;
  bool _needs_transition;

  int _frame_complete;
  int _frame_size_slots;
  OopMapSet* _oop_maps;
  public:
  DowncallStubGenerator(CodeBuffer* buffer,
                         BasicType* signature,
                         int num_args,
                         BasicType ret_bt,
                         const ABIDescriptor& abi,
                         const GrowableArray<VMStorage>& input_registers,
                         const GrowableArray<VMStorage>& output_registers,
                         bool needs_return_buffer,
                         int captured_state_mask,
                         bool needs_transition)
    :StubCodeGenerator(buffer, PrintMethodHandleStubs),
    _signature(signature),
    _num_args(num_args),
    _ret_bt(ret_bt),
    _abi(abi),
    _input_registers(input_registers),
    _output_registers(output_registers),
    _needs_return_buffer(needs_return_buffer),
    _captured_state_mask(captured_state_mask),
    _needs_transition(needs_transition),
    _frame_complete(0),
    _frame_size_slots(0),
    _oop_maps(nullptr) {
    }
  void generate();
  int frame_complete() const {
    return _frame_complete;
  }

  int framesize() const {
    return (_frame_size_slots >> (LogBytesPerWord - LogBytesPerInt));
  }

  OopMapSet* oop_maps() const {
    return _oop_maps;
  }
};

static const int native_invoker_code_base_size = 512;
static const int native_invoker_size_per_args = 8;

RuntimeStub* DowncallLinker::make_downcall_stub(BasicType* signature,
                                                int num_args,
                                                BasicType ret_bt,
                                                const ABIDescriptor& abi,
                                                const GrowableArray<VMStorage>& input_registers,
                                                const GrowableArray<VMStorage>& output_registers,
                                                bool needs_return_buffer,
                                                int captured_state_mask,
                                                bool needs_transition) {

  int code_size = native_invoker_code_base_size + (num_args * native_invoker_size_per_args);
  int locs_size = 1; //must be non zero
  CodeBuffer code("nep_invoker_blob", code_size, locs_size);

  DowncallStubGenerator g(&code, signature, num_args, ret_bt, abi,
                          input_registers, output_registers,
                          needs_return_buffer, captured_state_mask,
                          needs_transition);
  g.generate();
  code.log_section_sizes("nep_invoker_blob");

  RuntimeStub* stub =
    RuntimeStub::new_runtime_stub("nep_invoker_blob",
                                  &code,
                                  g.frame_complete(),
                                  g.framesize(),
                                  g.oop_maps(), false);

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

void DowncallStubGenerator::generate() {
  Register call_target_address = Z_R1_scratch,
           tmp = Z_R0_scratch;

  VMStorage shuffle_reg = _abi._scratch1;

  JavaCallingConvention in_conv;
  NativeCallingConvention out_conv(_input_registers);
  ArgumentShuffle arg_shuffle(_signature, _num_args, _signature, _num_args, &in_conv, &out_conv, shuffle_reg);

#ifndef PRODUCT
  LogTarget(Trace, foreign, downcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  assert(_abi._shadow_space_bytes == frame::z_abi_160_size, "expected space according to ABI");
  int allocated_frame_size = _abi._shadow_space_bytes;
  allocated_frame_size += arg_shuffle.out_arg_bytes();

  assert(!_needs_return_buffer, "unexpected needs_return_buffer");
  RegSpiller out_reg_spiller(_output_registers);
  int spill_offset = allocated_frame_size;
  allocated_frame_size += BytesPerWord;

  StubLocations locs;
  locs.set(StubLocations::TARGET_ADDRESS, _abi._scratch2);

  if (_captured_state_mask != 0) {
    __ block_comment("{ _captured_state_mask is set");
    locs.set_frame_data(StubLocations::CAPTURED_STATE_BUFFER, allocated_frame_size);
    allocated_frame_size += BytesPerWord;
    __ block_comment("} _captured_state_mask is set");
  }

  allocated_frame_size = align_up(allocated_frame_size, StackAlignmentInBytes);
  _frame_size_slots = allocated_frame_size >> LogBytesPerInt;

  _oop_maps  = _needs_transition ? new OopMapSet() : nullptr;
  address start = __ pc();

  __ save_return_pc();
  __ push_frame(allocated_frame_size, Z_R11); // Create a new frame for the wrapper.

  _frame_complete = __ pc() - start;  // frame build complete.

  if (_needs_transition) {
    __ block_comment("{ thread java2native");
    __ get_PC(Z_R1_scratch);
    address the_pc = __ pc();
    __ set_last_Java_frame(Z_SP, Z_R1_scratch);

    OopMap* map = new OopMap(_frame_size_slots, 0);
    _oop_maps->add_gc_map(the_pc - start, map);

    // State transition
    __ set_thread_state(_thread_in_native);
    __ block_comment("} thread java2native");
  }
  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, shuffle_reg, frame::z_jit_out_preserve_size, _abi._shadow_space_bytes, locs);
  __ block_comment("} argument shuffle");

  __ call(as_Register(locs.get(StubLocations::TARGET_ADDRESS)));

  //////////////////////////////////////////////////////////////////////////////

  if (_captured_state_mask != 0) {
    __ block_comment("{ save thread local");

      out_reg_spiller.generate_spill(_masm, spill_offset);

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, DowncallLinker::capture_state));
    __ z_lg(Z_ARG1, Address(Z_SP, locs.data_offset(StubLocations::CAPTURED_STATE_BUFFER)));
    __ load_const_optimized(Z_ARG2, _captured_state_mask);
    __ call(call_target_address);

      out_reg_spiller.generate_fill(_masm, spill_offset);

    __ block_comment("} save thread local");
  }

  //////////////////////////////////////////////////////////////////////////////

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;
  Label L_reguard;
  Label L_after_reguard;

  if (_needs_transition) {
    __ block_comment("{ thread native2java");
    __ set_thread_state(_thread_in_native_trans);

    if (!UseSystemMemoryBarrier) {
      __ z_fence(); // Order state change wrt. safepoint poll.
    }

    __ safepoint_poll(L_safepoint_poll_slow_path, tmp);

    __ load_and_test_int(tmp, Address(Z_thread, JavaThread::suspend_flags_offset()));
    __ z_brne(L_safepoint_poll_slow_path);

    __ bind(L_after_safepoint_poll);

    // change thread state
    __ set_thread_state(_thread_in_Java);

    __ block_comment("reguard stack check");
    __ z_cli(Address(Z_thread, JavaThread::stack_guard_state_offset() + in_ByteSize(sizeof(StackOverflow::StackGuardState) - 1)),
        StackOverflow::stack_guard_yellow_reserved_disabled);
    __ z_bre(L_reguard);
    __ bind(L_after_reguard);

    __ reset_last_Java_frame();
    __ block_comment("} thread native2java");
  }

  __ pop_frame();
  __ restore_return_pc();             // This is the way back to the caller.
  __ z_br(Z_R14);

  //////////////////////////////////////////////////////////////////////////////

  if (_needs_transition) {
    __ block_comment("{ L_safepoint_poll_slow_path");
    __ bind(L_safepoint_poll_slow_path);

      // Need to save the native result registers around any runtime calls.
      out_reg_spiller.generate_spill(_masm, spill_offset);

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, JavaThread::check_special_condition_for_native_trans));
    __ z_lgr(Z_ARG1, Z_thread);
    __ call(call_target_address);

      out_reg_spiller.generate_fill(_masm, spill_offset);

    __ z_bru(L_after_safepoint_poll);
    __ block_comment("} L_safepoint_poll_slow_path");

    //////////////////////////////////////////////////////////////////////////////
    __ block_comment("{ L_reguard");
    __ bind(L_reguard);

      // Need to save the native result registers around any runtime calls.
      out_reg_spiller.generate_spill(_masm, spill_offset);

    __ load_const_optimized(call_target_address, CAST_FROM_FN_PTR(uint64_t, SharedRuntime::reguard_yellow_pages));
    __ call(call_target_address);

      out_reg_spiller.generate_fill(_masm, spill_offset);

    __ z_bru(L_after_reguard);

    __ block_comment("} L_reguard");
  }

  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}
