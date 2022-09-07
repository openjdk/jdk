/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

class DowncallStubGenerator : public StubCodeGenerator {
  BasicType* _signature;
  int _num_args;
  BasicType _ret_bt;
  const ABIDescriptor& _abi;

  const GrowableArray<VMStorage>& _input_registers;
  const GrowableArray<VMStorage>& _output_registers;

  bool _needs_return_buffer;

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
                         bool needs_return_buffer)
   : StubCodeGenerator(buffer, PrintMethodHandleStubs),
     _signature(signature),
     _num_args(num_args),
     _ret_bt(ret_bt),
     _abi(abi),
     _input_registers(input_registers),
     _output_registers(output_registers),
     _needs_return_buffer(needs_return_buffer),
     _frame_complete(0),
     _frame_size_slots(0),
     _oop_maps(NULL) {
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

static const int native_invoker_code_size = 1024;

RuntimeStub* DowncallLinker::make_downcall_stub(BasicType* signature,
                                                int num_args,
                                                BasicType ret_bt,
                                                const ABIDescriptor& abi,
                                                const GrowableArray<VMStorage>& input_registers,
                                                const GrowableArray<VMStorage>& output_registers,
                                                bool needs_return_buffer) {
  int locs_size  = 64;
  CodeBuffer code("nep_invoker_blob", native_invoker_code_size, locs_size);
  DowncallStubGenerator g(&code, signature, num_args, ret_bt, abi, input_registers, output_registers, needs_return_buffer);
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

  VMStorage shuffle_reg = VMS_R19;
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

  int allocated_frame_size = 0;
  if (_needs_return_buffer) {
    allocated_frame_size += 8; // for address spill
  }
  allocated_frame_size += arg_shuffle.out_arg_bytes();
  assert(_abi._shadow_space_bytes == 0, "not expecting shadow space on AArch64");

  int ret_buf_addr_sp_offset = -1;
  if (_needs_return_buffer) {
     // in sync with the above
     ret_buf_addr_sp_offset = allocated_frame_size - 8;
  }

  RegSpiller out_reg_spiller(_output_registers);
  int spill_offset = -1;

  if (!_needs_return_buffer) {
    spill_offset = 0;
    // spill area can be shared with the above, so we take the max of the 2
    allocated_frame_size = out_reg_spiller.spill_size_bytes() > allocated_frame_size
      ? out_reg_spiller.spill_size_bytes()
      : allocated_frame_size;
  }

  _frame_size_slots = align_up(framesize + (allocated_frame_size >> LogBytesPerInt), 4);
  assert(is_even(_frame_size_slots/2), "sp not 16-byte aligned");

  _oop_maps  = new OopMapSet();
  address start = __ pc();

  __ enter();

  // lr and fp are already in place
  __ sub(sp, rfp, ((unsigned)_frame_size_slots-4) << LogBytesPerInt); // prolog

  _frame_complete = __ pc() - start;

  address the_pc = __ pc();
  __ set_last_Java_frame(sp, rfp, the_pc, tmp1);
  OopMap* map = new OopMap(_frame_size_slots, 0);
  _oop_maps->add_gc_map(the_pc - start, map);

  // State transition
  __ mov(tmp1, _thread_in_native);
  __ lea(tmp2, Address(rthread, JavaThread::thread_state_offset()));
  __ stlrw(tmp1, tmp2);

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, shuffle_reg, 0, _abi._shadow_space_bytes);
  if (_needs_return_buffer) {
    assert(ret_buf_addr_sp_offset != -1, "no return buffer addr spill");
    __ str(_abi._ret_buf_addr_reg, Address(sp, ret_buf_addr_sp_offset));
  }
  __ block_comment("} argument shuffle");

  __ blr(_abi._target_addr_reg);
  // this call is assumed not to have killed rthread

  if (_needs_return_buffer) {
    assert(ret_buf_addr_sp_offset != -1, "no return buffer addr spill");
    __ ldr(tmp1, Address(sp, ret_buf_addr_sp_offset));
    int offset = 0;
    for (int i = 0; i < _output_registers.length(); i++) {
      VMStorage reg = _output_registers.at(i);
      if (reg.type() == RegType::INTEGER) {
        __ str(as_Register(reg), Address(tmp1, offset));
        offset += 8;
      } else if(reg.type() == RegType::VECTOR) {
        __ strd(as_FloatRegister(reg), Address(tmp1, offset));
        offset += 16;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  __ mov(tmp1, _thread_in_native_trans);
  __ strw(tmp1, Address(rthread, JavaThread::thread_state_offset()));

  // Force this write out before the read below
  if (!UseSystemMemoryBarrier) {
    __ membar(Assembler::LoadLoad | Assembler::LoadStore |
              Assembler::StoreLoad | Assembler::StoreStore);
  }

  __ verify_sve_vector_length(tmp1);

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;

  __ safepoint_poll(L_safepoint_poll_slow_path, true /* at_return */, true /* acquire */, false /* in_nmethod */, tmp1);

  __ ldrw(tmp1, Address(rthread, JavaThread::suspend_flags_offset()));
  __ cbnzw(tmp1, L_safepoint_poll_slow_path);

  __ bind(L_after_safepoint_poll);

  // change thread state
  __ mov(tmp1, _thread_in_Java);
  __ lea(tmp2, Address(rthread, JavaThread::thread_state_offset()));
  __ stlrw(tmp1, tmp2);

  __ block_comment("reguard stack check");
  Label L_reguard;
  Label L_after_reguard;
  __ ldrb(tmp1, Address(rthread, JavaThread::stack_guard_state_offset()));
  __ cmpw(tmp1, StackOverflow::stack_guard_yellow_reserved_disabled);
  __ br(Assembler::EQ, L_reguard);
  __ bind(L_after_reguard);

  __ reset_last_Java_frame(true);

  __ leave(); // required for proper stackwalking of RuntimeStub frame
  __ ret(lr);

  //////////////////////////////////////////////////////////////////////////////

  __ block_comment("{ L_safepoint_poll_slow_path");
  __ bind(L_safepoint_poll_slow_path);

  if (!_needs_return_buffer) {
    // Need to save the native result registers around any runtime calls.
    out_reg_spiller.generate_spill(_masm, spill_offset);
  }

  __ mov(c_rarg0, rthread);
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
  __ lea(tmp1, RuntimeAddress(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans)));
  __ blr(tmp1);

  if (!_needs_return_buffer) {
    out_reg_spiller.generate_fill(_masm, spill_offset);
  }

  __ b(L_after_safepoint_poll);
  __ block_comment("} L_safepoint_poll_slow_path");

  //////////////////////////////////////////////////////////////////////////////

  __ block_comment("{ L_reguard");
  __ bind(L_reguard);

  if (!_needs_return_buffer) {
    out_reg_spiller.generate_spill(_masm, spill_offset);
  }

  __ rt_call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages), tmp1);

  if (!_needs_return_buffer) {
    out_reg_spiller.generate_fill(_masm, spill_offset);
  }

  __ b(L_after_reguard);

  __ block_comment("} L_reguard");

  //////////////////////////////////////////////////////////////////////////////

  __ flush();
}
