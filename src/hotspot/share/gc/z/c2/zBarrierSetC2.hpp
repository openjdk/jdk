/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_Z_C2_ZBARRIERSETC2_HPP
#define SHARE_GC_Z_C2_ZBARRIERSETC2_HPP

#include "gc/shared/c2/barrierSetC2.hpp"
#include "memory/allocation.hpp"
#include "opto/node.hpp"
#include "utilities/growableArray.hpp"

class LoadBarrierNode : public MultiNode {
private:
  bool _weak;               // On strong or weak oop reference
  bool _writeback;          // Controls if the barrier writes the healed oop back to memory
                            // A swap on a memory location must never write back the healed oop
  bool _oop_reload_allowed; // Controls if the barrier are allowed to reload the oop from memory
                            // before healing, otherwise both the oop and the address must be
                            // passed to the barrier from the oop

  static bool is_dominator(PhaseIdealLoop* phase, bool linear_only, Node *d, Node *n);
  void push_dominated_barriers(PhaseIterGVN* igvn) const;

public:
  enum {
    Control,
    Memory,
    Oop,
    Address,
    Number_of_Outputs = Address,
    Similar,
    Number_of_Inputs
  };

  LoadBarrierNode(Compile* C,
                  Node* c,
                  Node* mem,
                  Node* val,
                  Node* adr,
                  bool weak,
                  bool writeback,
                  bool oop_reload_allowed);

  virtual int Opcode() const;
  virtual uint size_of() const;
  virtual uint cmp(const Node& n) const;
  virtual const Type *bottom_type() const;
  virtual const TypePtr* adr_type() const;
  virtual const Type *Value(PhaseGVN *phase) const;
  virtual Node *Identity(PhaseGVN *phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint match_edge(uint idx) const;

  LoadBarrierNode* has_dominating_barrier(PhaseIdealLoop* phase,
                                          bool linear_only,
                                          bool look_for_similar);

  void fix_similar_in_uses(PhaseIterGVN* igvn);

  bool has_true_uses() const;

  bool can_be_eliminated() const {
    return !in(Similar)->is_top();
  }

  bool is_weak() const {
    return _weak;
  }

  bool is_writeback() const {
    return _writeback;
  }

  bool oop_reload_allowed() const {
    return _oop_reload_allowed;
  }
};

class LoadBarrierSlowRegNode : public LoadPNode {
public:
  LoadBarrierSlowRegNode(Node *c,
                         Node *mem,
                         Node *adr,
                         const TypePtr *at,
                         const TypePtr* t,
                         MemOrd mo,
                         ControlDependency control_dependency = DependsOnlyOnTest) :
      LoadPNode(c, mem, adr, at, t, mo, control_dependency) {
    init_class_id(Class_LoadBarrierSlowReg);
  }

  virtual const char * name() {
    return "LoadBarrierSlowRegNode";
  }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    return NULL;
  }

  virtual int Opcode() const;
};

class LoadBarrierWeakSlowRegNode : public LoadPNode {
public:
  LoadBarrierWeakSlowRegNode(Node *c,
                             Node *mem,
                             Node *adr,
                             const TypePtr *at,
                             const TypePtr* t,
                             MemOrd mo,
                             ControlDependency control_dependency = DependsOnlyOnTest) :
      LoadPNode(c, mem, adr, at, t, mo, control_dependency) {
    init_class_id(Class_LoadBarrierWeakSlowReg);
  }

  virtual const char * name() {
    return "LoadBarrierWeakSlowRegNode";
  }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    return NULL;
  }

  virtual int Opcode() const;
};

class ZBarrierSetC2State : public ResourceObj {
private:
  // List of load barrier nodes which need to be expanded before matching
  GrowableArray<LoadBarrierNode*>* _load_barrier_nodes;

public:
  ZBarrierSetC2State(Arena* comp_arena);
  int load_barrier_count() const;
  void add_load_barrier_node(LoadBarrierNode* n);
  void remove_load_barrier_node(LoadBarrierNode* n);
  LoadBarrierNode* load_barrier_node(int idx) const;
};

class ZBarrierSetC2 : public BarrierSetC2 {
private:
  ZBarrierSetC2State* state() const;
  Node* make_cas_loadbarrier(C2AtomicParseAccess& access) const;
  Node* make_cmpx_loadbarrier(C2AtomicParseAccess& access) const;
  void expand_loadbarrier_basic(PhaseMacroExpand* phase, LoadBarrierNode *barrier) const;
  void expand_loadbarrier_node(PhaseMacroExpand* phase, LoadBarrierNode* barrier) const;
  void expand_loadbarrier_optimized(PhaseMacroExpand* phase, LoadBarrierNode *barrier) const;
  const TypeFunc* load_barrier_Type() const;

#ifdef ASSERT
  void verify_gc_barriers(bool post_parse) const;
#endif

protected:
  virtual Node* load_at_resolved(C2Access& access, const Type* val_type) const;
  virtual Node* atomic_cmpxchg_val_at_resolved(C2AtomicParseAccess& access,
                                               Node* expected_val,
                                               Node* new_val,
                                               const Type* val_type) const;
  virtual Node* atomic_cmpxchg_bool_at_resolved(C2AtomicParseAccess& access,
                                                Node* expected_val,
                                                Node* new_val,
                                                const Type* value_type) const;
  virtual Node* atomic_xchg_at_resolved(C2AtomicParseAccess& access,
                                        Node* new_val,
                                        const Type* val_type) const;

public:
  Node* load_barrier(GraphKit* kit,
                     Node* val,
                     Node* adr,
                     bool weak = false,
                     bool writeback = true,
                     bool oop_reload_allowed = true) const;

  virtual void* create_barrier_state(Arena* comp_arena) const;
  virtual bool has_load_barriers() const { return true; }
  virtual bool is_gc_barrier_node(Node* node) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const { }
  virtual void eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const;
  virtual void add_users_to_worklist(Unique_Node_List* worklist) const;
  virtual void enqueue_useful_gc_barrier(PhaseIterGVN* igvn, Node* node) const;
  virtual void register_potential_barrier_node(Node* node) const;
  virtual void unregister_potential_barrier_node(Node* node) const;
  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const;
  virtual Node* step_over_gc_barrier(Node* c) const;
  // If the BarrierSetC2 state has kept barrier nodes in its compilation unit state to be
  // expanded later, then now is the time to do so.
  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;

  static void find_dominating_barriers(PhaseIterGVN& igvn);
  static void loop_optimize_gc_barrier(PhaseIdealLoop* phase, Node* node, bool last_round);

  virtual bool final_graph_reshaping(Compile* compile, Node* n, uint opcode) const;

  virtual bool matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
#endif

  virtual bool escape_add_to_con_graph(ConnectionGraph* conn_graph, PhaseGVN* gvn, Unique_Node_List* delayed_worklist, Node* n, uint opcode) const;
  virtual bool escape_add_final_edges(ConnectionGraph* conn_graph, PhaseGVN* gvn, Node* n, uint opcode) const;
};

#endif // SHARE_GC_Z_C2_ZBARRIERSETC2_HPP
