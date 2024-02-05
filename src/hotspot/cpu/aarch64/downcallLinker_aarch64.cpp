/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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
#include "code/codeCache.hpp"
#include "code/vmreg.inline.hpp"
#include "compiler/oopMap.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/downcallLinker.hpp"
#include "runtime/globals.hpp"
#include "runtime/stubCodeGenerator.hpp"

#define __ _masm->

static const int native_invoker_code_base_size = 256;
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

static constexpr int RFP_BIAS = 16; // skip old rbp and return address

void DowncallLinker::StubGenerator::pd_add_offset_to_oop(VMStorage reg_oop, VMStorage reg_offset, VMStorage tmp1, VMStorage tmp2) const {
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
      Address offset_addr(rfp, RFP_BIAS + reg_offset.offset());
      __ ldr (r_tmp1, offset_addr);
      __ add(reg_oop_reg, reg_oop_reg, r_tmp1);
    }
  } else {
    assert(reg_oop.is_stack(), "expected");
    assert(reg_oop.stack_size() == 8, "expected long");
    assert(reg_offset.is_stack(), "expected");
    assert(reg_offset.stack_size() == 8, "expected long");
    Address offset_addr(rfp, RFP_BIAS + reg_offset.offset());
    Address oop_addr(rfp, RFP_BIAS + reg_oop.offset());
    __ ldr(r_tmp1, offset_addr);
    __ ldr(r_tmp2, oop_addr);
    __ add(r_tmp1, r_tmp1, r_tmp2);
    __ str(r_tmp1, oop_addr);
  }
}

void DowncallLinker::StubGenerator::generate() {
  enum layout {
    rfp_off,
    rfp_off2,
    lr_off,
    lr_off2,
    framesize // inclusive of return address
    // The following are also computed dynamically:
    // spill area for return value
    // out arg area (e.g. for stack args)
  };

  // we can't use rscratch1 because it is r8, and used by the ABI
  Register tmp1 = r9;
  Register tmp2 = r10;

  GrowableArray<VMStorage> java_regs;
  ForeignGlobals::java_calling_convention(_signature, _num_args, java_regs);
  bool has_objects = false;
  GrowableArray<VMStorage> filtered_java_regs = ForeignGlobals::downcall_filter_offset_regs(java_regs, _signature,
                                                                                             _num_args, has_objects);
  assert(!(_needs_transition && has_objects), "can not pass objects when doing transition");

  int allocated_frame_size = 0;
  assert(_abi._shadow_space_bytes == 0, "not expecting shadow space on AArch64");
  allocated_frame_size += ForeignGlobals::compute_out_arg_bytes(_input_registers);

  bool should_save_return_value = !_needs_return_buffer;
  RegSpiller out_reg_spiller(_output_registers);
  int spill_offset = -1;

  if (should_save_return_value) {
    spill_offset = 0;
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
    allocated_frame_size += BytesPerWord; // for address spill
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
  // SP-> | out/stack args      | or | out_reg_spiller area |
  //
  // Note how the last chunk can be shared, since the 3 uses occur at different times.

  VMStorage shuffle_reg = as_VMStorage(r19);
  GrowableArray<VMStorage> out_regs = ForeignGlobals::replace_place_holders(_input_registers, locs);
  ArgumentShuffle arg_shuffle(filtered_java_regs, out_regs, shuffle_reg);

#ifndef PRODUCT
  LogTarget(Trace, foreign, downcall) lt;
  if (lt.is_enabled()) {
    ResourceMark rm;
    LogStream ls(lt);
    arg_shuffle.print_on(&ls);
  }
#endif

  _frame_size_slots = align_up(framesize + (allocated_frame_size >> LogBytesPerInt), 4);
  assert(is_even(_frame_size_slots/2), "sp not 16-byte aligned");

  _oop_maps  = _needs_transition ? new OopMapSet() : nullptr;
  address start = __ pc();

  __ enter();

  // lr and fp are already in place
  __ sub(sp, rfp, ((unsigned)_frame_size_slots-4) << LogBytesPerInt); // prolog

  _frame_complete = __ pc() - start;

  if (_needs_transition) {
    address the_pc = __ pc();
    __ set_last_Java_frame(sp, rfp, the_pc, tmp1);
    OopMap* map = new OopMap(_frame_size_slots, 0);
    _oop_maps->add_gc_map(the_pc - start, map);

    // State transition
    __ mov(tmp1, _thread_in_native);
    __ lea(tmp2, Address(rthread, JavaThread::thread_state_offset()));
    __ stlrw(tmp1, tmp2);
  }

  if (has_objects) {
    add_offsets_to_oops(java_regs, as_VMStorage(tmp1), as_VMStorage(tmp2));
  }

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, shuffle_reg, 0, _abi._shadow_space_bytes);
  __ block_comment("} argument shuffle");

  __ blr(as_Register(locs.get(StubLocations::TARGET_ADDRESS)));
  // this call is assumed not to have killed rthread

  if (_needs_return_buffer) {
    __ ldr(tmp1, Address(sp, locs.data_offset(StubLocations::RETURN_BUFFER)));
    int offset = 0;
    for (int i = 0; i < _output_registers.length(); i++) {
      VMStorage reg = _output_registers.at(i);
      if (reg.type() == StorageType::INTEGER) {
        __ str(as_Register(reg), Address(tmp1, offset));
        offset += 8;
      } else if (reg.type() == StorageType::VECTOR) {
        __ strd(as_FloatRegister(reg), Address(tmp1, offset));
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
      out_reg_spiller.generate_spill(_masm, spill_offset);
    }

    __ ldr(c_rarg0, Address(sp, locs.data_offset(StubLocations::CAPTURED_STATE_BUFFER)));
    __ movw(c_rarg1, _captured_state_mask);
    __ rt_call(CAST_FROM_FN_PTR(address, DowncallLinker::capture_state), tmp1);

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
    // Restore cpu control state after JNI call
    __ restore_cpu_control_state_after_jni(rscratch1, tmp1);

    __ mov(tmp1, _thread_in_native_trans);
    __ strw(tmp1, Address(rthread, JavaThread::thread_state_offset()));

    // Force this write out before the read below
    if (!UseSystemMemoryBarrier) {
      __ membar(Assembler::LoadLoad | Assembler::LoadStore |
                Assembler::StoreLoad | Assembler::StoreStore);
    }

    __ verify_sve_vector_length(tmp1);

    __ safepoint_poll(L_safepoint_poll_slow_path, true /* at_return */, true /* acquire */, false /* in_nmethod */, tmp1);

    __ ldrw(tmp1, Address(rthread, JavaThread::suspend_flags_offset()));
    __ cbnzw(tmp1, L_safepoint_poll_slow_path);

    __ bind(L_after_safepoint_poll);

    // change thread state
    __ mov(tmp1, _thread_in_Java);
    __ lea(tmp2, Address(rthread, JavaThread::thread_state_offset()));
    __ stlrw(tmp1, tmp2);

    __ block_comment("reguard stack check");
    __ ldrb(tmp1, Address(rthread, JavaThread::stack_guard_state_offset()));
    __ cmpw(tmp1, StackOverflow::stack_guard_yellow_reserved_disabled);
    __ br(Assembler::EQ, L_reguard);
    __ bind(L_after_reguard);

    __ reset_last_Java_frame(true);
  }

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(lr);

  //////////////////////////////////////////////////////////////////////////////

  if (_needs_transition) {
    __ block_comment("{ L_safepoint_poll_slow_path");
    __ bind(L_safepoint_poll_slow_path);

    if (should_save_return_value) {
      // Need to save the native result registers around any runtime calls.
      out_reg_spiller.generate_spill(_masm, spill_offset);
    }

    __ mov(c_rarg0, rthread);
    assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
    __ lea(tmp1, RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans)));
    __ blr(tmp1);

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

    __ rt_call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages), tmp1);

    if (should_save_return_value) {
      out_reg_spiller.generate_fill(_masm, spill_offset);
    }

    __ b(L_after_reguard);

    __ block_comment("} L_reguard");
  }

  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}
