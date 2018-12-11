/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
#define SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP

#include "gc/shared/c2/barrierSetC2.hpp"
#include "gc/shenandoah/c2/shenandoahSupport.hpp"
#include "utilities/growableArray.hpp"

class ShenandoahBarrierSetC2State : public ResourceObj {
private:
  GrowableArray<ShenandoahWriteBarrierNode*>* _shenandoah_barriers;

public:
  ShenandoahBarrierSetC2State(Arena* comp_arena);
  int shenandoah_barriers_count() const;
  ShenandoahWriteBarrierNode* shenandoah_barrier(int idx) const;
  void add_shenandoah_barrier(ShenandoahWriteBarrierNode * n);
  void remove_shenandoah_barrier(ShenandoahWriteBarrierNode * n);
};

class ShenandoahBarrierSetC2 : public BarrierSetC2 {
private:
  void shenandoah_eliminate_wb_pre(Node* call, PhaseIterGVN* igvn) const;

  bool satb_can_remove_pre_barrier(GraphKit* kit, PhaseTransform* phase, Node* adr,
                                   BasicType bt, uint adr_idx) const;
  void satb_write_barrier_pre(GraphKit* kit, bool do_load,
                              Node* obj,
                              Node* adr,
                              uint alias_idx,
                              Node* val,
                              const TypeOopPtr* val_type,
                              Node* pre_val,
                              BasicType bt) const;

  void shenandoah_write_barrier_pre(GraphKit* kit,
                                    bool do_load,
                                    Node* obj,
                                    Node* adr,
                                    uint alias_idx,
                                    Node* val,
                                    const TypeOopPtr* val_type,
                                    Node* pre_val,
                                    BasicType bt) const;

  Node* shenandoah_enqueue_barrier(GraphKit* kit, Node* val) const;
  Node* shenandoah_read_barrier(GraphKit* kit, Node* obj) const;
  Node* shenandoah_storeval_barrier(GraphKit* kit, Node* obj) const;
  Node* shenandoah_write_barrier(GraphKit* kit, Node* obj) const;
  Node* shenandoah_read_barrier_impl(GraphKit* kit, Node* obj, bool use_ctrl, bool use_mem, bool allow_fromspace) const;
  Node* shenandoah_write_barrier_impl(GraphKit* kit, Node* obj) const;
  Node* shenandoah_write_barrier_helper(GraphKit* kit, Node* obj, const TypePtr* adr_type) const;

  void insert_pre_barrier(GraphKit* kit, Node* base_oop, Node* offset,
                          Node* pre_val, bool need_mem_bar) const;

  static bool clone_needs_postbarrier(ArrayCopyNode *ac, PhaseIterGVN& igvn);

protected:
  virtual void resolve_address(C2Access& access) const;
  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* store_at_resolved(C2Access& access, C2AccessValue& val) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                               Node* new_val, const Type* val_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access, Node* expected_val,
                                                Node* new_val, const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access, Node* new_val, const Type* val_type) const;

public:
  static ShenandoahBarrierSetC2* bsc2();

  static bool is_shenandoah_wb_pre_call(Node* call);
  static bool is_shenandoah_wb_call(Node* call);
  static bool is_shenandoah_marking_if(PhaseTransform *phase, Node* n);
  static bool is_shenandoah_state_load(Node* n);
  static bool has_only_shenandoah_wb_pre_uses(Node* n);

  ShenandoahBarrierSetC2State* state() const;

  static const TypeFunc* write_ref_field_pre_entry_Type();
  static const TypeFunc* shenandoah_clone_barrier_Type();
  static const TypeFunc* shenandoah_write_barrier_Type();

  // This is the entry-point for the backend to perform accesses through the Access API.
  virtual void clone(GraphKit* kit, Node* src, Node* dst, Node* size, bool is_array) const;

  virtual Node* resolve(GraphKit* kit, Node* n, DecoratorSet decorators) const;

  virtual Node* obj_allocate(PhaseMacroExpand* macro, Node* ctrl, Node* mem, Node* toobig_false, Node* size_in_bytes,
                             Node*& i_o, Node*& needgc_ctrl,
                             Node*& fast_oop_ctrl, Node*& fast_oop_rawmem,
                             intx prefetch_lines) const;

  // These are general helper methods used by C2
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const;
  virtual void clone_barrier_at_expansion(ArrayCopyNode* ac, Node* call, PhaseIterGVN& igvn) const;

  // Support for GC barriers emitted during parsing
  virtual bool is_gc_barrier_node(Node* node) const;
  virtual Node* step_over_gc_barrier(Node* c) const;
  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;
  virtual bool optimize_loops(PhaseIdealLoop* phase, LoopOptsMode mode, VectorSet& visited, Node_Stack& nstack, Node_List& worklist) const;
  virtual bool strip_mined_loops_expanded(LoopOptsMode mode) const { return mode == LoopOptsShenandoahExpand || mode == LoopOptsShenandoahPostExpand; }
  virtual bool is_gc_specific_loop_opts_pass(LoopOptsMode mode) const { return mode == LoopOptsShenandoahExpand || mode == LoopOptsShenandoahPostExpand; }

  // Support for macro expanded GC barriers
  virtual void register_potential_barrier_node(Node* node) const;
  virtual void unregister_potential_barrier_node(Node* node) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const;
  virtual void enqueue_useful_gc_barrier(PhaseIterGVN* igvn, Node* node) const;
  virtual void eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const;
  virtual void add_users_to_worklist(Unique_Node_List* worklist) const;

  // Allow barrier sets to have shared state that is preserved across a compilation unit.
  // This could for example comprise macro nodes to be expanded during macro expansion.
  virtual void* create_barrier_state(Arena* comp_arena) const;
  // If the BarrierSetC2 state has kept macro nodes in its compilation unit state to be
  // expanded later, then now is the time to do so.
  virtual bool expand_macro_nodes(PhaseMacroExpand* macro) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
#endif

  virtual bool flatten_gc_alias_type(const TypePtr*& adr_type) const;
#ifdef ASSERT
  virtual bool verify_gc_alias_type(const TypePtr* adr_type, int offset) const;
#endif

  virtual Node* ideal_node(PhaseGVN* phase, Node* n, bool can_reshape) const;
  virtual Node* identity_node(PhaseGVN* phase, Node* n) const;
  virtual bool final_graph_reshaping(Compile* compile, Node* n, uint opcode) const;

  virtual bool escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const;
  virtual bool escape_add_final_edges(ConnectionGraph* conn_graph, PhaseGVN* gvn, Node* n, uint opcode) const;
  virtual bool escape_has_out_with_unsafe_object(Node* n) const;
  virtual bool escape_is_barrier_node(Node* n) const;

  virtual bool matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const;
  virtual bool matcher_find_shared_post_visit(Matcher* matcher, Node* n, uint opcode) const;
  virtual bool matcher_is_store_load_barrier(Node* x, uint xop) const;

  virtual void igvn_add_users_to_worklist(PhaseIterGVN* igvn, Node* use) const;
  virtual void ccp_analyze(PhaseCCP* ccp, Unique_Node_List& worklist, Node* use) const;

  virtual bool has_special_unique_user(const Node* node) const;
  virtual Node* split_if_pre(PhaseIdealLoop* phase, Node* n) const;
  virtual bool build_loop_late_post(PhaseIdealLoop* phase, Node* n) const;
  virtual bool sink_node(PhaseIdealLoop* phase, Node* n, Node* x, Node* x_ctrl, Node* n_ctrl) const;
};

#endif // SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAHBARRIERSETC2_HPP
