/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2022, Huawei Technologies Co., Ltd. All rights reserved.
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
#include "asm/macroAssembler.hpp"
#include "code/codeBlob.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "prims/downcallLinker.hpp"
#include "prims/foreignGlobals.inline.hpp"
#include "runtime/sharedRuntime.hpp"
#include "runtime/stubCodeGenerator.hpp"

#define __ _masm->

class DowncallStubGenerator : public StubCodeGenerator {
    BasicType* _signature;
    int _num_args;
    BasicType _ret_bt;

    const ABIDescriptor& _abi;
    const GrowableArray<VMReg>& _input_registers;
    // output_registers is which carry return value from native function.
    const GrowableArray<VMReg>& _output_registers;

    bool _needs_return_buffer;

    int _frame_complete;
    int _framesize;
    OopMapSet* _oop_maps;
public:
    DowncallStubGenerator(CodeBuffer* buffer,
                          BasicType* signature,
                          int num_args,
                          BasicType ret_bt,
                          const ABIDescriptor& abi,
                          const GrowableArray<VMReg>& input_registers,
                          const GrowableArray<VMReg>& output_registers,
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
              _framesize(0),
              _oop_maps(NULL) {
    }

    void generate();

    int frame_complete() const {
      return _frame_complete;
    }

    int framesize() const {
      return (_framesize >> (LogBytesPerWord - LogBytesPerInt));
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
                                                const GrowableArray<VMReg>& input_registers,
                                                const GrowableArray<VMReg>& output_registers,
                                                bool needs_return_buffer) {
  int locs_size = 64;
  CodeBuffer code("nep_invoker_blob", native_invoker_code_size, locs_size);
  DowncallStubGenerator g(&code, signature, num_args, ret_bt, abi, input_registers, output_registers,
                          needs_return_buffer);
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
  // ra, fp
  // unit = 32bits word
  enum layout {
      ra_off,
      ra_off2,
      fp_off,
      fp_off2,
      framesize_base // inclusive of return address
      // The following are also computed dynamically:
      // shadow space
      // spill area
      // out arg area (e.g. for stack args)
  };

  Register shufffle_reg = t2;
  JavaCallingConvention in_conv;
  NativeCallingConvention out_conv(_input_registers);
  ArgumentShuffle arg_shuffle(_signature, _num_args, _signature, _num_args, &in_conv, &out_conv,
                              shufffle_reg->as_VMReg());

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
    // when perform downcall, only pointer to return buffer be saved in the stack.
    // return buffer size will be determined by total size of vmstorages.
    allocated_frame_size += 8; // store address
  }
  allocated_frame_size += arg_shuffle.out_arg_stack_slots() << LogBytesPerInt;
  allocated_frame_size += _abi._shadow_space_bytes;

  int ret_buf_addr_sp_offset = -1;
  if (_needs_return_buffer) {
    // the above
    ret_buf_addr_sp_offset = allocated_frame_size - 8;
  }

  // when we don't use a return buffer we need to spill the return value around our slowpath calls
  // when we use a return buffer case this SHOULD be unused.
  RegSpiller out_reg_spiller(_output_registers);
  int spill_offset = -1;

  if (!_needs_return_buffer) {
    spill_offset = 0;
    // spill area can be shared with the above, so we take the max of the 2
    allocated_frame_size = out_reg_spiller.spill_size_bytes() > allocated_frame_size
                           ? out_reg_spiller.spill_size_bytes()
                           : allocated_frame_size;
  }
  allocated_frame_size = align_up(allocated_frame_size, 16);
  // _framesize is in 32-bit stack slots:
  _framesize += framesize_base + (allocated_frame_size >> LogBytesPerInt);
  assert(is_even(_framesize / 2), "sp not 16-byte aligned");

  _oop_maps = new OopMapSet();
  address start = __ pc();

  __ enter();

  // ra and fp are already in place
  __ sub(sp, sp, allocated_frame_size); // prolog

  _frame_complete = __ pc() - start; // frame build complete.

  __ block_comment("{ thread java2native");
  address the_pc = __ pc();
  __ set_last_Java_frame(sp, fp, the_pc, t0);
  OopMap* map = new OopMap(_framesize, 0);
  _oop_maps->add_gc_map(the_pc - start, map);

  // State transition
  __ mv(t0, _thread_in_native);
  __ membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);
  __ sw(t0, Address(xthread, JavaThread::thread_state_offset()));
  __ block_comment("} thread java2native");

  __ block_comment("{ argument shuffle");
  arg_shuffle.generate(_masm, shufffle_reg->as_VMReg(), 0, _abi._shadow_space_bytes);
  if (_needs_return_buffer) {
    // spill our return buffer address
    assert(ret_buf_addr_sp_offset != -1, "no return buffer addr spill");
    __ sd(_abi._ret_buf_addr_reg, Address(sp, ret_buf_addr_sp_offset));
  }
  __ block_comment("} argument shuffle");

  // jump to foreign funtion.
  __ jalr(_abi._target_addr_reg);

  if (!_needs_return_buffer) {
    // Unpack native results, convert native results into java value.
    // results still in registers, so when in slow path, it must be spilled.
    __ cast_primitive_type(_ret_bt, c_rarg0);
  } else {
    // when use return buffer, copy content of return registers to return buffer,
    // then operations created in BoxBindingCalculator will be operated.
    assert(ret_buf_addr_sp_offset != -1, "no return buffer addr spill");
    __ ld(t0, Address(sp, ret_buf_addr_sp_offset));
    int offset = 0;
    for (int i = 0; i < _output_registers.length(); i++) {
      VMReg reg = _output_registers.at(i);
      if (reg->is_Register()) {
        __ sd(reg->as_Register(), Address(t0, offset));
        offset += 8;
      } else if (reg->is_FloatRegister()) {
        __ fsd(reg->as_FloatRegister(), Address(t0, offset));
        offset += 8;
      } else {
        ShouldNotReachHere();
      }
    }
  }

  __ block_comment("{ thread native2java");
  __ mv(t0, _thread_in_native_trans);
  __ sw(t0, Address(xthread, JavaThread::thread_state_offset()));

  // Force this write out before the read below
  __ membar(MacroAssembler::AnyAny);

  Label L_after_safepoint_poll;
  Label L_safepoint_poll_slow_path;
  __ safepoint_poll(L_safepoint_poll_slow_path, true /* at_return */, true /* acquire */, false /* in_nmethod */);
  __ lwu(t0, Address(xthread, JavaThread::suspend_flags_offset()));
  __ bnez(t0, L_safepoint_poll_slow_path);

  __ bind(L_after_safepoint_poll);

  __ mv(t0, _thread_in_Java);
  __ membar(MacroAssembler::LoadStore | MacroAssembler::StoreStore);
  __ sw(t0, Address(xthread, JavaThread::thread_state_offset()));

  __ block_comment("reguard stack check");
  Label L_reguard;
  Label L_after_reguard;
  __ lbu(t0, Address(xthread, JavaThread::stack_guard_state_offset()));
  __ mv(t1, StackOverflow::stack_guard_yellow_reserved_disabled);
  __ beq(t0, t1, L_reguard);
  __ bind(L_after_reguard);

  __ reset_last_Java_frame(true);
  __ block_comment("} thread native2java");

  __ leave();
  __ ret();

  __ block_comment("{ L_safepoint_poll_slow_path");
  __ bind(L_safepoint_poll_slow_path);
  if (!_needs_return_buffer) {
    // Need to save the native result registers around any runtime calls.
    out_reg_spiller.generate_spill(_masm, spill_offset);
  }

  __ mv(c_rarg0, xthread);
  assert(frame::arg_reg_save_area_bytes == 0, "not expecting frame reg save area");
  __ rt_call(CAST_FROM_FN_PTR(address, JavaThread::check_special_condition_for_native_trans));

  if (!_needs_return_buffer) {
    out_reg_spiller.generate_fill(_masm, spill_offset);
  }
  __ j(L_after_safepoint_poll);
  __ block_comment("} L_safepoint_poll_slow_path");

  __ block_comment("{ L_reguard");
  __ bind(L_reguard);
  if (!_needs_return_buffer) {
    // Need to save the native result registers around any runtime calls.
    out_reg_spiller.generate_spill(_masm, spill_offset);
  }

  __ rt_call(CAST_FROM_FN_PTR(address, SharedRuntime::reguard_yellow_pages));

  if (!_needs_return_buffer) {
    out_reg_spiller.generate_fill(_masm, spill_offset);
  }

  __ j(L_after_reguard);
  __ block_comment("} L_reguard");

  __ flush();
}
