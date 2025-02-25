/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_PRIMS_DOWNCALLLINKER_HPP
#define SHARE_VM_PRIMS_DOWNCALLLINKER_HPP

#include "prims/foreignGlobals.hpp"
#include "runtime/stubCodeGenerator.hpp"

class RuntimeStub;

class DowncallLinker: AllStatic {
public:
  static RuntimeStub* make_downcall_stub(BasicType*,
                                         int num_args,
                                         BasicType ret_bt,
                                         const ABIDescriptor& abi,
                                         const GrowableArray<VMStorage>& input_registers,
                                         const GrowableArray<VMStorage>& output_registers,
                                         bool needs_return_buffer,
                                         int captured_state_mask,
                                         bool needs_transition);

  // This is defined as JVM_LEAF which adds the JNICALL modifier.
  static void JNICALL capture_state(int32_t* value_ptr, int captured_state_mask);

  class StubGenerator : public StubCodeGenerator {
    BasicType* _signature;
    int _num_args;
    BasicType _ret_bt;

    const ABIDescriptor& _abi;
    const GrowableArray<VMStorage>& _input_registers;
    const GrowableArray<VMStorage>& _output_registers;

    bool _needs_return_buffer;
    int _captured_state_mask;
    bool _needs_transition;

    int _frame_complete;
    int _frame_size_slots;
    OopMapSet* _oop_maps;
  public:
    StubGenerator(CodeBuffer* buffer,
                  BasicType* signature,
                  int num_args,
                  BasicType ret_bt,
                  const ABIDescriptor& abi,
                  const GrowableArray<VMStorage>& input_registers,
                  const GrowableArray<VMStorage>& output_registers,
                  bool needs_return_buffer,
                  int captured_state_mask,
                  bool needs_transition)
    : StubCodeGenerator(buffer, PrintMethodHandleStubs),
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

    int frame_complete() const { return _frame_complete; }
    // frame size in words
    int framesize() const { return (_frame_size_slots >> (LogBytesPerWord - LogBytesPerInt)); }

    OopMapSet* oop_maps() const { return _oop_maps; }

    void pd_add_offset_to_oop(VMStorage reg_oop, VMStorage reg_offset, VMStorage tmp1, VMStorage tmp2) const;
    void add_offsets_to_oops(GrowableArray<VMStorage>& java_regs, VMStorage tmp1, VMStorage tmp2) const;
  };
};

#endif // SHARE_VM_PRIMS_DOWNCALLLINKER_HPP
