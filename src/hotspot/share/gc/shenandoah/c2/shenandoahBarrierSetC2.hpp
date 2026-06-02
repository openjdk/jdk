/*
 * Copyright (c) 2018, 2021, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
#define SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP

#include "gc/shared/c2/barrierSetC2.hpp"

static const uint8_t ShenandoahBitStrong    = 1 << 0; // Barrier: LRB, strong
static const uint8_t ShenandoahBitWeak      = 1 << 1; // Barrier: LRB, weak
static const uint8_t ShenandoahBitPhantom   = 1 << 2; // Barrier: LRB, phantom
static const uint8_t ShenandoahBitKeepAlive = 1 << 3; // Barrier: KeepAlive (SATB for stores, KA for loads)
static const uint8_t ShenandoahBitCardMark  = 1 << 4; // Barrier: CM
static const uint8_t ShenandoahBitNotNull   = 1 << 5; // Metadata: src/dst is definitely not null
static const uint8_t ShenandoahBitNative    = 1 << 6; // Metadata: access is in native, not in heap
static const uint8_t ShenandoahBitElided    = 1 << 7; // Metadata: some part of the barrier is elided

// Barrier data that implies real barriers, not additional metadata.
static const uint8_t ShenandoahBitsReal = ShenandoahBitStrong | ShenandoahBitWeak | ShenandoahBitPhantom |
                                          ShenandoahBitKeepAlive |
                                          ShenandoahBitCardMark;

class MachNode;
class ShenandoahBarrierStubC2;

class ShenandoahBarrierSetC2State : public BarrierSetC2State {
  GrowableArray<ShenandoahBarrierStubC2*>* _stubs;
  int _trampoline_stubs_count;
  int _stubs_start_offset;
  int _stubs_current_total_size;

public:
  explicit ShenandoahBarrierSetC2State(Arena* comp_arena);

  bool needs_liveness_data(const MachNode* mach) const override;
  bool needs_livein_data() const override;

  GrowableArray<ShenandoahBarrierStubC2*>* stubs() {
    return _stubs;
  }

  void inc_trampoline_stubs_count() {
    assert(_trampoline_stubs_count != INT_MAX, "Overflow");
    ++_trampoline_stubs_count;
  }

  int trampoline_stubs_count() {
    return _trampoline_stubs_count;
  }

  void set_stubs_start_offset(int offset) {
    _stubs_start_offset = offset;
  }

  int stubs_start_offset() {
    return _stubs_start_offset;
  }

  int inc_stubs_current_total_size(int size) {
    _stubs_current_total_size += size;
    return _stubs_current_total_size;
  }

  int stubs_current_total_size() {
    return _stubs_current_total_size;
  }
};

class ShenandoahBarrierSetC2 : public BarrierSetC2 {

  static bool clone_needs_barrier(const TypeOopPtr* src_type, bool& is_oop_array);

  static bool can_remove_load_barrier(Node* node);

  static uint8_t refine_load(Node* node, uint8_t bd);
  static uint8_t refine_store(Node* node, uint8_t bd);

  static bool is_Load(int opcode);
  static bool is_Store(int opcode);
  static bool is_LoadStore(int opcode);

protected:
  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* store_at_resolved(C2Access& access, C2AccessValue& val) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                               Node* new_val, const Type* val_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                Node* new_val, const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const;

public:
  static ShenandoahBarrierSetC2* bsc2();

  ShenandoahBarrierSetC2State* state() const;

  // This is the entry-point for the backend to perform accesses through the Access API.
  virtual void clone(GraphKit* kit, Node* src_base, Node* dst_base, Node* size, bool is_array) const;
  virtual void clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const;

  // These are general helper methods used by C2
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone,
      bool is_clone_instance, ArrayCopyPhase phase) const;

  // Support for macro expanded GC barriers
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void eliminate_gc_barrier_data(Node* node) const;

  // Allow barrier sets to have shared state that is preserved across a compilation unit.
  // This could for example comprise macro nodes to be expanded during macro expansion.
  virtual void* create_barrier_state(Arena* comp_arena) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
  static void verify_gc_barrier_assert(bool cond, const char* msg, uint8_t bd, Node* n);
#endif

  virtual int estimate_stub_size() const;
  virtual void emit_stubs(CodeBuffer& cb) const;
  virtual void late_barrier_analysis() const {
    compute_liveness_at_stubs();
    analyze_dominating_barriers();
  }

  virtual void elide_dominated_barrier(MachNode* mach, MachNode* dominator) const;
  virtual void analyze_dominating_barriers() const;
  virtual void final_refinement(Compile* C) const;

  virtual uint estimated_barrier_size(const Node* node) const;

  static void print_barrier_data(outputStream* os, uint8_t data);
};

class ShenandoahBarrierStubC2 : public BarrierStubC2 {
  Register _obj;
  Address const _addr;
  Register const _tmp1;
  Register const _tmp2;
  const bool _do_load;
  const bool _narrow;
  const bool _needs_load_ref_barrier;
  const bool _needs_load_ref_weak_barrier;
  const bool _needs_keep_alive_barrier;
  bool _needs_far_jump;

  static void register_stub(ShenandoahBarrierStubC2* stub);

  int available_gp_registers();
  bool is_live_register(Register reg);
  bool is_special_register(Register reg);
  Register select_temp_register(bool& selected_live, Register skip_reg1 = noreg, Register skip_reg2 = noreg);

  void maybe_far_jump_if_zero(MacroAssembler& masm, Register reg);

  void enter_if_gc_state(MacroAssembler& masm, const char test_state, Register tmp);

  void keepalive(MacroAssembler& masm, Label* L_done);
  void lrb(MacroAssembler& masm);

  static void cardtable(MacroAssembler& masm, Address addr, Register tmp1, Register tmp2);

  address keepalive_runtime_entry_addr();
  address lrb_runtime_entry_addr();

  static ShenandoahBarrierStubC2* create(const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow, bool do_load);
  void post_init();

  ShenandoahBarrierStubC2(const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow, bool do_load) :
    BarrierStubC2(node),
    _obj(obj),
    _addr(addr),
    _tmp1(tmp1),
    _tmp2(tmp2),
    _do_load(do_load),
    _narrow(narrow),
    _needs_load_ref_barrier(needs_load_ref_barrier(node)),
    _needs_load_ref_weak_barrier(needs_load_ref_barrier_weak(node)),
    _needs_keep_alive_barrier(needs_keep_alive_barrier(node)),
    _needs_far_jump() {
    assert(!_narrow || is_heap_access(node), "Only heap accesses can be narrow");
    if (_tmp1 != noreg && _tmp2 != noreg) {
      assert_different_registers(_tmp1, _tmp2, _obj, _addr.base(), _addr.index());
    } else {
      assert(_tmp1 == _tmp2, "should both be noreg");
      assert_different_registers(_obj, _addr.base(), _addr.index());
    }
    post_init();
  }

  static bool is_heap_access(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitNative) == 0;
  }
  static bool needs_load_ref_barrier(const MachNode* node) {
    return (node->barrier_data() & (ShenandoahBitStrong | ShenandoahBitWeak | ShenandoahBitPhantom)) != 0;
  }
  static bool needs_load_ref_barrier_weak(const MachNode* node) {
    return (node->barrier_data() & (ShenandoahBitWeak | ShenandoahBitPhantom)) != 0;
  }
  static bool needs_keep_alive_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitKeepAlive) != 0;
  }
  static bool needs_card_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBitCardMark) != 0;
  }

public:
  static bool needs_slow_barrier(const MachNode* node) {
    return needs_load_ref_barrier(node) || needs_keep_alive_barrier(node);
  }

  static void load_post(MacroAssembler* masm, const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow);
  static void store_pre(MacroAssembler* masm, const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow);
  static void store_post(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2);
  static void load_store_pre(MacroAssembler* masm, const MachNode* node, Register obj, Address addr, Register tmp1, Register tmp2, bool narrow);
  static void load_store_post(MacroAssembler* masm, const MachNode* node, Address addr, Register tmp1, Register tmp2);

  void emit_code(MacroAssembler& masm);
};
#endif // SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
