/*
 * Copyright (c) 2002, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2014, Red Hat Inc. All rights reserved.
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

#ifndef CPU_AARCH64_CODEBUFFER_AARCH64_HPP
#define CPU_AARCH64_CODEBUFFER_AARCH64_HPP

/* Finite State Machine for merging instruction */
class InstructionFSM_AArch64 : public ResourceObj {
public:
  enum PendingState {
    NoPending,
    PendingDmbLd   = 0b1001, // Assembler::barrier::ISHLD,
    PendingDmbSt,            // Assembler::barrier::ISHST,
    PendingDmbISH,           // Assembler::barrier::ISH,
    PendingDmbLdSt,
    // It comes from DmbLdSt+DmbISH and will emit dmb.ish + nop,
    // because we need keep same size with PendingDmbLdSt
    PendingDmbISH2,
  };

private:
  PendingState _state;
#ifndef PRODUCT
  int          _merged;
  CodeSection* _cs;
  int          _offset;
#endif

public:
  InstructionFSM_AArch64() {
    _state = NoPending;
#ifndef PRODUCT
    _merged = 0;
    _cs = nullptr;
    _offset = 0;
#endif
  }

  // reset state, emit pending instruction
  void flush_and_reset(Assembler* assem);

  // transition state with current instruction, may emit instructions
  void transition(unsigned int imm, Assembler* assem);

  PendingState state() const { return _state; }

  // report size of pending instructions
  int  pending_size() const {
    if (_state == NoPending) return 0;
    if (_state == PendingDmbLdSt || _state == PendingDmbISH2) return 8;
    return 4;
  }
};

private:
  InstructionFSM_AArch64 _fsm;
  void pd_initialize() { }
  bool pd_finalize_stubs();

public:
  // use finite state machine for merging dmb instructions
  void flush_pending(Assembler* assem) { _fsm.flush_and_reset(assem); }
  void push_dmb(unsigned int imm, Assembler* assem) { _fsm.transition(imm, assem); }

  void flush_bundle(bool start_new_bundle) {}
  static constexpr bool supports_shared_stubs() { return true; }

  void share_trampoline_for(address dest, int caller_offset);

#endif // CPU_AARCH64_CODEBUFFER_AARCH64_HPP
