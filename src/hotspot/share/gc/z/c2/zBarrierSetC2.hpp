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

class ZCompareAndSwapPNode : public CompareAndSwapPNode {
public:
    ZCompareAndSwapPNode(Node* c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord) : CompareAndSwapPNode(c, mem, adr, val, ex, mem_ord) { }
    virtual int Opcode() const;
};

class ZWeakCompareAndSwapPNode : public WeakCompareAndSwapPNode {
public:
    ZWeakCompareAndSwapPNode(Node* c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord) : WeakCompareAndSwapPNode(c, mem, adr, val, ex, mem_ord) { }
    virtual int Opcode() const;
};

class ZCompareAndExchangePNode : public CompareAndExchangePNode {
public:
    ZCompareAndExchangePNode(Node* c, Node *mem, Node *adr, Node *val, Node *ex, const TypePtr* at, const Type* t, MemNode::MemOrd mem_ord) : CompareAndExchangePNode(c, mem, adr, val, ex, at, t, mem_ord) { }
    virtual int Opcode() const;
};

class ZGetAndSetPNode : public GetAndSetPNode {
public:
    ZGetAndSetPNode(Node* c, Node *mem, Node *adr, Node *val, const TypePtr* at, const Type* t) : GetAndSetPNode(c, mem, adr, val, at, t) { }
    virtual int Opcode() const;
};

class LoadBarrierNode : public MultiNode {
private:
  bool _weak;               // On strong or weak oop reference
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
                  bool weak);

  virtual int Opcode() const;
  virtual uint size_of() const;
  virtual bool cmp(const Node& n) const;
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
};

class LoadBarrierSlowRegNode : public TypeNode {
private:
  bool _is_weak;
public:
  LoadBarrierSlowRegNode(Node *c,
                         Node *adr,
                         Node *src,
                         const TypePtr* t,
                         bool weak) :
      TypeNode(t, 3), _is_weak(weak) {
    init_req(1, adr);
    init_req(2, src);
    init_class_id(Class_LoadBarrierSlowReg);
  }

  virtual uint size_of() const {
    return sizeof(*this);
  }

  virtual const char * name() {
    return "LoadBarrierSlowRegNode";
  }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    return NULL;
  }

  virtual int Opcode() const;

  bool is_weak() { return _is_weak; }
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
  void expand_loadbarrier_node(PhaseMacroExpand* phase, LoadBarrierNode* barrier) const;

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
  virtual void* create_barrier_state(Arena* comp_arena) const;

  virtual bool has_load_barriers() const { return true; }
  virtual bool is_gc_barrier_node(Node* node) const;
  virtual Node* step_over_gc_barrier(Node* c) const;
  virtual Node* step_over_gc_barrier_ctrl(Node* c) const;

  virtual void register_potential_barrier_node(Node* node) const;
  virtual void unregister_potential_barrier_node(Node* node) const;
  virtual void eliminate_gc_barrier(PhaseMacroExpand* macro, Node* node) const { }
  virtual void enqueue_useful_gc_barrier(PhaseIterGVN* igvn, Node* node) const;
  virtual void eliminate_useless_gc_barriers(Unique_Node_List &useful, Compile* C) const;

  virtual bool array_copy_requires_gc_barriers(bool tightly_coupled_alloc, BasicType type, bool is_clone, ArrayCopyPhase phase) const;

  virtual bool expand_barriers(Compile* C, PhaseIterGVN& igvn) const;
  virtual bool final_graph_reshaping(Compile* compile, Node* n, uint opcode) const;
  virtual bool matcher_find_shared_visit(Matcher* matcher, Matcher::MStack& mstack, Node* n, uint opcode, bool& mem_op, int& mem_addr_idx) const;
  virtual bool matcher_find_shared_post_visit(Matcher* matcher, Node* n, uint opcode) const;
  virtual bool needs_anti_dependence_check(const Node* node) const;

#ifdef ASSERT
  virtual void verify_gc_barriers(Compile* compile, CompilePhase phase) const;
#endif

  // Load barrier insertion and expansion external
  virtual void barrier_insertion_phase(Compile* C, PhaseIterGVN &igvn) const;
  virtual bool optimize_loops(PhaseIdealLoop* phase, LoopOptsMode mode, VectorSet& visited, Node_Stack& nstack, Node_List& worklist) const;
  virtual bool is_gc_specific_loop_opts_pass(LoopOptsMode mode) const { return (mode == LoopOptsZBarrierInsertion); }
  virtual bool strip_mined_loops_expanded(LoopOptsMode mode) const { return mode == LoopOptsZBarrierInsertion; }

private:
  // Load barrier insertion and expansion internal
  void insert_barriers_on_unsafe(PhaseIdealLoop* phase) const;
  void clean_catch_blocks(PhaseIdealLoop* phase) const;
  void insert_load_barriers(PhaseIdealLoop* phase) const;
  LoadNode* insert_one_loadbarrier(PhaseIdealLoop* phase, LoadNode* load, Node* ctrl) const;
  void insert_one_loadbarrier_inner(PhaseIdealLoop* phase, LoadNode* load, Node* ctrl, VectorSet visited) const;
};

#endif // SHARE_GC_Z_C2_ZBARRIERSETC2_HPP
