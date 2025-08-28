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

#include "shenandoahBarrierSetC2.hpp"
#include "gc/shared/c2/barrierSetC2.hpp"
#include "gc/shenandoah/c2/shenandoahSupport.hpp"
#include "utilities/growableArray.hpp"

static const uint8_t ShenandoahBarrierStrong          = 1 << 0;
static const uint8_t ShenandoahBarrierWeak            = 1 << 1;
static const uint8_t ShenandoahBarrierPhantom         = 1 << 2;
static const uint8_t ShenandoahBarrierNative          = 1 << 3;
static const uint8_t ShenandoahBarrierElided          = 1 << 4;
static const uint8_t ShenandoahBarrierSATB            = 1 << 5;
static const uint8_t ShenandoahBarrierCardMark        = 1 << 6;
static const uint8_t ShenandoahBarrierCardMarkNotNull = 1 << 7;

class ShenandoahBarrierStubC2;

class ShenandoahBarrierSetC2State : public BarrierSetC2State {
  GrowableArray<ShenandoahLoadReferenceBarrierNode*>* _load_reference_barriers;
  GrowableArray<ShenandoahBarrierStubC2*>* _stubs;
  int _stubs_start_offset;

public:
  explicit ShenandoahBarrierSetC2State(Arena* comp_arena);

  bool needs_liveness_data(const MachNode* mach) const override;
  bool needs_livein_data() const override;

  int load_reference_barriers_count() const;
  ShenandoahLoadReferenceBarrierNode* load_reference_barrier(int idx) const;
  void add_load_reference_barrier(ShenandoahLoadReferenceBarrierNode* n);
  void remove_load_reference_barrier(ShenandoahLoadReferenceBarrierNode * n);

  GrowableArray<ShenandoahBarrierStubC2*>* stubs() {
    return _stubs;
  }

  void set_stubs_start_offset(int offset) {
    _stubs_start_offset = offset;
  }

  int stubs_start_offset() {
    return _stubs_start_offset;
  }};

class ShenandoahBarrierSetC2 : public BarrierSetC2 {

  static bool clone_needs_barrier(Node* src, PhaseGVN& gvn);

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

  static bool is_shenandoah_clone_call(Node* call);
  static bool is_shenandoah_lrb_call(Node* call);
  static bool is_shenandoah_state_load(Node* n);

  ShenandoahBarrierSetC2State* state() const;

  static const TypeFunc* clone_barrier_Type();
  static const TypeFunc* load_reference_barrier_Type();
  virtual bool has_load_barrier_nodes() const { return true; }

  // This is the entry-point for the backend to perform accesses through the Access API.
  virtual void clone_at_expansion(PhaseMacroExpand* phase, ArrayCopyNode* ac) const;

  // These are general helper methods used by C2
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, bool is_clone_instance, ArrayCopyPhase phase) const;

  // Support for GC barriers emitted during parsing
  virtual bool is_gc_barrier_node(Node* node) const;
  virtual Node* step_over_gc_barrier(Node* c) const;
  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;
  virtual bool optimize_loops(PhaseIdealLoop* phase, LoopOptsMode mode, VectorSet& visited, Node_Stack& nstack, Node_List& worklist) const;
  virtual bool strip_mined_loops_expanded(LoopOptsMode mode) const { return mode == LoopOptsShenandoahExpand; }
  virtual bool is_gc_specific_loop_opts_pass(LoopOptsMode mode) const { return mode == LoopOptsShenandoahExpand; }

  // Support for macro expanded GC barriers
  virtual void register_potential_barrier_node(Node* node) const;
  virtual void unregister_potential_barrier_node(Node* node) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void eliminate_gc_barrier_data(Node* node) const;
  virtual void eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const;

  // Allow barrier sets to have shared state that is preserved across a compilation unit.
  // This could for example comprise macro nodes to be expanded during macro expansion.
  virtual void* create_barrier_state(Arena* comp_arena) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
#endif

  virtual Node* ideal_node(PhaseGVN* phase, Node* n, bool can_reshape) const;
  virtual bool final_graph_reshaping(Compile* compile, Node* n, uint opcode, Unique_Node_List& dead_nodes) const;

  virtual bool escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const;
  virtual bool escape_add_final_edges(ConnectionGraph* conn_graph, PhaseGVN* gvn, Node* n, uint opcode) const;

  int estimate_stub_size() const /* override */;
  void emit_stubs(CodeBuffer& cb) const /* override */;
  void late_barrier_analysis() const /* override*/ {
    compute_liveness_at_stubs();
  }

};

class ShenandoahBarrierStubC2 : public BarrierStubC2 {
protected:
  explicit ShenandoahBarrierStubC2(const MachNode* node) : BarrierStubC2(node) {}
  void register_stub();
public:
  virtual void emit_code(MacroAssembler& masm) = 0;
};

class ShenandoahSATBBarrierStubC2 : public ShenandoahBarrierStubC2 {
  Register _addr_reg;
  Register _preval;
  ShenandoahSATBBarrierStubC2(const MachNode* node, Register addr, Register preval) :
    ShenandoahBarrierStubC2(node),
    _addr_reg(addr), _preval(preval) {}

public:
  static bool needs_barrier(const MachNode* node) {
    return (node->barrier_data() & ShenandoahBarrierSATB) != 0;
  }
  static ShenandoahSATBBarrierStubC2* create(const MachNode* node, Register addr_reg, Register preval);

  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahCASBarrierSlowStubC2 : public ShenandoahBarrierStubC2 {
  Register _addr_reg;
  Address  _addr;
  Register _expected;
  Register _new_val;
  Register _result;
  Register _tmp1;
  Register _tmp2;
  bool     _cae;
  bool     _acquire;
  bool     _release;
  bool     _weak;

  explicit ShenandoahCASBarrierSlowStubC2(const MachNode* node, Register addr_reg, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae, bool acquire, bool release, bool weak) :
    ShenandoahBarrierStubC2(node),
    _addr_reg(addr_reg), _addr(addr), _expected(expected), _new_val(new_val), _result(result), _tmp1(tmp1), _tmp2(tmp2), _cae(cae), _acquire(acquire), _release(release),  _weak(weak) {}

public:
  static ShenandoahCASBarrierSlowStubC2* create(const MachNode* node, Register addr, Register expected, Register new_val, Register result, Register tmp, bool cae, bool acquire, bool release, bool weak);
  static ShenandoahCASBarrierSlowStubC2* create(const MachNode* node, Address addr, Register expected, Register new_val, Register result, Register tmp1, Register tmp2, bool cae);
  void emit_code(MacroAssembler& masm) override;
};

class ShenandoahCASBarrierMidStubC2 : public ShenandoahBarrierStubC2 {
  ShenandoahCASBarrierSlowStubC2* _slow_stub;
  Register _expected;
  Register _result;
  Register _tmp;
  bool _cae;
  ShenandoahCASBarrierMidStubC2(const MachNode* node, ShenandoahCASBarrierSlowStubC2* slow_stub, Register expected, Register result, Register tmp, bool cae) :
    ShenandoahBarrierStubC2(node), _slow_stub(slow_stub), _expected(expected), _result(result), _tmp(tmp), _cae(cae) {}
public:
  static ShenandoahCASBarrierMidStubC2* create(const MachNode* node, ShenandoahCASBarrierSlowStubC2* slow_stub, Register expected, Register result, Register tmp, bool cae);
  void emit_code(MacroAssembler& masm) override;
};

#endif // SHARE_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
