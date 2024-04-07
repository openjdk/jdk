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

// for merging dmb/ld/st
class MergeableInst : public StackObj {
private:
  bool _is_dmb;
  bool _is_ld;
  bool _is_st;
  int  _barrier_kind;

public:
  MergeableInst(int kind): _is_dmb(true), _is_ld(false), _is_st(false), _barrier_kind(kind) {}

  bool is_dmb ()      const { return _is_dmb; };
  bool is_ld ()       const { return _is_ld; };
  bool is_st ()       const { return _is_st; };
  int barrier_kind()  const { assert(_is_dmb, "must be"); return _barrier_kind; }
};

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
    PendingLd,
    PendingSt
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

  ~InstructionFSM_AArch64() {}

  // reset state, emit pending instruction
  void flush_and_reset(Assembler* assem);

  // transition state with current instruction, may emit instructions
  void transition(MergeableInst* inst, Assembler* assem);

  PendingState state() const { return _state; }

  // report size of pending instructions
  int  pending_size() const {
    if (_state == NoPending) return 0;
    if (_state == PendingDmbLdSt || _state == PendingDmbISH2) return 8;
    return 4;
  }
};

private:
  InstructionFSM_AArch64* _fsm;
  void pd_initialize() { _fsm = new InstructionFSM_AArch64(); };
  bool pd_finalize_stubs();

public:
  // use finite state machine for merging instructions
  void flush_pending(Assembler* assem) { _fsm->flush_and_reset(assem); }
  InstructionFSM_AArch64* fsm() const  { return _fsm; }

  void flush_bundle(bool start_new_bundle) {}
  static constexpr bool supports_shared_stubs() { return true; }

  void share_trampoline_for(address dest, int caller_offset);

#endif // CPU_AARCH64_CODEBUFFER_AARCH64_HPP
