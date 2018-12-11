/*
 * Copyright (c) 2015, 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAH_SUPPORT_HPP
#define SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAH_SUPPORT_HPP

#include "gc/shenandoah/shenandoahBrooksPointer.hpp"
#include "memory/allocation.hpp"
#include "opto/addnode.hpp"
#include "opto/graphKit.hpp"
#include "opto/machnode.hpp"
#include "opto/memnode.hpp"
#include "opto/multnode.hpp"
#include "opto/node.hpp"

class PhaseGVN;
class MemoryGraphFixer;

class ShenandoahBarrierNode : public TypeNode {
private:
  bool _allow_fromspace;

#ifdef ASSERT
  enum verify_type {
    ShenandoahLoad,
    ShenandoahStore,
    ShenandoahValue,
    ShenandoahOopStore,
    ShenandoahNone,
  };

  static bool verify_helper(Node* in, Node_Stack& phis, VectorSet& visited, verify_type t, bool trace, Unique_Node_List& barriers_used);
#endif

public:
  enum { Control,
         Memory,
         ValueIn
  };

  ShenandoahBarrierNode(Node* ctrl, Node* mem, Node* obj, bool allow_fromspace)
    : TypeNode(obj->bottom_type()->isa_oopptr() ? obj->bottom_type()->is_oopptr()->cast_to_nonconst() : obj->bottom_type(), 3),
      _allow_fromspace(allow_fromspace) {

    init_req(Control, ctrl);
    init_req(Memory, mem);
    init_req(ValueIn, obj);

    init_class_id(Class_ShenandoahBarrier);
  }

  static Node* skip_through_barrier(Node* n);

  static const TypeOopPtr* brooks_pointer_type(const Type* t) {
    return t->is_oopptr()->cast_to_nonconst()->add_offset(ShenandoahBrooksPointer::byte_offset())->is_oopptr();
  }

  virtual const TypePtr* adr_type() const {
    if (bottom_type() == Type::TOP) {
      return NULL;
    }
    //const TypePtr* adr_type = in(MemNode::Address)->bottom_type()->is_ptr();
    const TypePtr* adr_type = brooks_pointer_type(bottom_type());
    assert(adr_type->offset() == ShenandoahBrooksPointer::byte_offset(), "sane offset");
    assert(Compile::current()->alias_type(adr_type)->is_rewritable(), "brooks ptr must be rewritable");
    return adr_type;
  }

  virtual uint  ideal_reg() const { return Op_RegP; }
  virtual uint match_edge(uint idx) const {
    return idx >= ValueIn;
  }

  Node* Identity_impl(PhaseGVN* phase);

  virtual const Type* Value(PhaseGVN* phase) const;
  virtual bool depends_only_on_test() const {
    return true;
  };

  static bool needs_barrier(PhaseGVN* phase, ShenandoahBarrierNode* orig, Node* n, Node* rb_mem, bool allow_fromspace);

#ifdef ASSERT
  static void report_verify_failure(const char* msg, Node* n1 = NULL, Node* n2 = NULL);
  static void verify(RootNode* root);
  static void verify_raw_mem(RootNode* root);
#endif
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif

  // protected:
  static Node* dom_mem(Node* mem, Node*& mem_ctrl, Node* n, Node* rep_ctrl, int alias, PhaseIdealLoop* phase);
  static Node* dom_mem(Node* mem, Node* ctrl, int alias, Node*& mem_ctrl, PhaseIdealLoop* phase);
  static bool is_dominator(Node *d_c, Node *n_c, Node* d, Node* n, PhaseIdealLoop* phase);
  static bool is_dominator_same_ctrl(Node* c, Node* d, Node* n, PhaseIdealLoop* phase);
  static Node* no_branches(Node* c, Node* dom, bool allow_one_proj, PhaseIdealLoop* phase);
  static bool build_loop_late_post(PhaseIdealLoop* phase, Node* n);
  bool sink_node(PhaseIdealLoop* phase, Node* ctrl, Node* n_ctrl);

protected:
  uint hash() const;
  uint cmp(const Node& n) const;
  uint size_of() const;

private:
  static bool needs_barrier_impl(PhaseGVN* phase, ShenandoahBarrierNode* orig, Node* n, Node* rb_mem, bool allow_fromspace, Unique_Node_List &visited);

  static bool dominates_memory(PhaseGVN* phase, Node* b1, Node* b2, bool linear);
  static bool dominates_memory_impl(PhaseGVN* phase, Node* b1, Node* b2, Node* current, bool linear);
};

class ShenandoahReadBarrierNode : public ShenandoahBarrierNode {
public:
  ShenandoahReadBarrierNode(Node* ctrl, Node* mem, Node* obj)
    : ShenandoahBarrierNode(ctrl, mem, obj, true) {
    assert(UseShenandoahGC && (ShenandoahReadBarrier || ShenandoahStoreValReadBarrier ||
                               ShenandoahWriteBarrier || ShenandoahAcmpBarrier),
           "should be enabled");
  }
  ShenandoahReadBarrierNode(Node* ctrl, Node* mem, Node* obj, bool allow_fromspace)
    : ShenandoahBarrierNode(ctrl, mem, obj, allow_fromspace) {
    assert(UseShenandoahGC && (ShenandoahReadBarrier || ShenandoahStoreValReadBarrier ||
                               ShenandoahWriteBarrier || ShenandoahAcmpBarrier),
           "should be enabled");
  }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual int Opcode() const;

  bool is_independent(Node* mem);

  void try_move(PhaseIdealLoop* phase);

private:
  static bool is_independent(const Type* in_type, const Type* this_type);
  static bool dominates_memory_rb(PhaseGVN* phase, Node* b1, Node* b2, bool linear);
  static bool dominates_memory_rb_impl(PhaseGVN* phase, Node* b1, Node* b2, Node* current, bool linear);
};

class ShenandoahWriteBarrierNode : public ShenandoahBarrierNode {
public:
  ShenandoahWriteBarrierNode(Compile* C, Node* ctrl, Node* mem, Node* obj);

  virtual int Opcode() const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual Node* Identity(PhaseGVN* phase);
  virtual bool depends_only_on_test() const { return false; }

  static bool expand(Compile* C, PhaseIterGVN& igvn);
  static bool is_gc_state_load(Node *n);
  static bool is_heap_state_test(Node* iff, int mask);
  static bool is_heap_stable_test(Node* iff);
  static bool try_common_gc_state_load(Node *n, PhaseIdealLoop *phase);
  static bool has_safepoint_between(Node* start, Node* stop, PhaseIdealLoop *phase);

  static LoopNode* try_move_before_pre_loop(Node* c, Node* val_ctrl, PhaseIdealLoop* phase);
  static Node* move_above_predicates(LoopNode* cl, Node* val_ctrl, PhaseIdealLoop* phase);
#ifdef ASSERT
  static bool memory_dominates_all_paths(Node* mem, Node* rep_ctrl, int alias, PhaseIdealLoop* phase);
  static void memory_dominates_all_paths_helper(Node* c, Node* rep_ctrl, Unique_Node_List& controls, PhaseIdealLoop* phase);
#endif
  void try_move_before_loop(GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, PhaseIdealLoop* phase, bool include_lsm, Unique_Node_List& uses);
  void try_move_before_loop_helper(LoopNode* cl, Node* val_ctrl, GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, PhaseIdealLoop* phase, bool include_lsm, Unique_Node_List& uses);
  static void pin_and_expand(PhaseIdealLoop* phase);
  CallStaticJavaNode* pin_and_expand_null_check(PhaseIterGVN& igvn);
  void pin_and_expand_move_barrier(PhaseIdealLoop* phase, GrowableArray<MemoryGraphFixer*>& memory_graph_fixers, Unique_Node_List& uses);
  void pin_and_expand_helper(PhaseIdealLoop* phase);
  static Node* find_bottom_mem(Node* ctrl, PhaseIdealLoop* phase);
  static void follow_barrier_uses(Node* n, Node* ctrl, Unique_Node_List& uses, PhaseIdealLoop* phase);
  static void test_null(Node*& ctrl, Node* val, Node*& null_ctrl, PhaseIdealLoop* phase);

  static void test_heap_stable(Node*& ctrl, Node* raw_mem, Node*& heap_stable_ctrl,
                               PhaseIdealLoop* phase);
  static void call_wb_stub(Node*& ctrl, Node*& val, Node*& result_mem,
                           Node* raw_mem, Node* wb_mem, int alias,
                           PhaseIdealLoop* phase);
  static Node* clone_null_check(Node*& c, Node* val, Node* unc_ctrl, PhaseIdealLoop* phase);
  static void fix_null_check(Node* unc, Node* unc_ctrl, Node* new_unc_ctrl, Unique_Node_List& uses,
                             PhaseIdealLoop* phase);
  static void in_cset_fast_test(Node*& ctrl, Node*& not_cset_ctrl, Node* val, Node* raw_mem, PhaseIdealLoop* phase);
  static void move_heap_stable_test_out_of_loop(IfNode* iff, PhaseIdealLoop* phase);

  static void optimize_after_expansion(VectorSet &visited, Node_Stack &nstack, Node_List &old_new, PhaseIdealLoop* phase);
  static void merge_back_to_back_tests(Node* n, PhaseIdealLoop* phase);
  static bool identical_backtoback_ifs(Node *n, PhaseIdealLoop* phase);
  static void fix_ctrl(Node* barrier, Node* region, const MemoryGraphFixer& fixer, Unique_Node_List& uses, Unique_Node_List& uses_to_ignore, uint last, PhaseIdealLoop* phase);

  static void optimize_before_expansion(PhaseIdealLoop* phase, GrowableArray<MemoryGraphFixer*> memory_graph_fixers, bool include_lsm);
  Node* would_subsume(ShenandoahBarrierNode* other, PhaseIdealLoop* phase);
  static IfNode* find_unswitching_candidate(const IdealLoopTree *loop, PhaseIdealLoop* phase);

  Node* try_split_thru_phi(PhaseIdealLoop* phase);
};

class ShenandoahWBMemProjNode : public Node {
public:
  enum { Control,
         WriteBarrier };

  ShenandoahWBMemProjNode(Node *src) : Node(NULL, src) {
    assert(UseShenandoahGC && ShenandoahWriteBarrier, "should be enabled");
    assert(src->Opcode() == Op_ShenandoahWriteBarrier || src->is_Mach(), "epxect wb");
  }
  virtual Node* Identity(PhaseGVN* phase);

  virtual int Opcode() const;
  virtual bool      is_CFG() const  { return false; }
  virtual const Type *bottom_type() const {return Type::MEMORY;}
  virtual const TypePtr *adr_type() const {
    Node* wb = in(WriteBarrier);
    if (wb == NULL || wb->is_top())  return NULL; // node is dead
    assert(wb->Opcode() == Op_ShenandoahWriteBarrier || (wb->is_Mach() && wb->as_Mach()->ideal_Opcode() == Op_ShenandoahWriteBarrier) || wb->is_Phi(), "expect wb");
    return ShenandoahBarrierNode::brooks_pointer_type(wb->bottom_type());
  }

  virtual uint ideal_reg() const { return 0;} // memory projections don't have a register
  virtual const Type *Value(PhaseGVN* phase ) const {
    return bottom_type();
  }
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const {};
#endif
};

class ShenandoahEnqueueBarrierNode : public Node {
public:
  ShenandoahEnqueueBarrierNode(Node* val) : Node(NULL, val) {
  }

  const Type *bottom_type() const;
  const Type* Value(PhaseGVN* phase) const;
  Node* Identity(PhaseGVN* phase);

  int Opcode() const;

private:
  enum { Needed, NotNeeded, MaybeNeeded };

  static int needed(Node* n);
  static Node* next(Node* n);
};

class MemoryGraphFixer : public ResourceObj {
private:
  Node_List _memory_nodes;
  int _alias;
  PhaseIdealLoop* _phase;
  bool _include_lsm;

  void collect_memory_nodes();
  Node* get_ctrl(Node* n) const;
  Node* ctrl_or_self(Node* n) const;
  bool mem_is_valid(Node* m, Node* c) const;
  MergeMemNode* allocate_merge_mem(Node* mem, Node* rep_proj, Node* rep_ctrl) const;
  MergeMemNode* clone_merge_mem(Node* u, Node* mem, Node* rep_proj, Node* rep_ctrl, DUIterator& i) const;
  void fix_memory_uses(Node* mem, Node* replacement, Node* rep_proj, Node* rep_ctrl) const;
  bool should_process_phi(Node* phi) const;
  bool has_mem_phi(Node* region) const;

public:
  MemoryGraphFixer(int alias, bool include_lsm, PhaseIdealLoop* phase) :
    _alias(alias), _phase(phase), _include_lsm(include_lsm) {
    assert(_alias != Compile::AliasIdxBot, "unsupported");
    collect_memory_nodes();
  }

  Node* find_mem(Node* ctrl, Node* n) const;
  void fix_mem(Node* ctrl, Node* region, Node* mem, Node* mem_for_ctrl, Node* mem_phi, Unique_Node_List& uses);
  int alias() const { return _alias; }
  void remove(Node* n);
};

class ShenandoahCompareAndSwapPNode : public CompareAndSwapPNode {
public:
  ShenandoahCompareAndSwapPNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord)
    : CompareAndSwapPNode(c, mem, adr, val, ex, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypePtr::NULL_PTR) {
      return new CompareAndSwapPNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

class ShenandoahCompareAndSwapNNode : public CompareAndSwapNNode {
public:
  ShenandoahCompareAndSwapNNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord)
    : CompareAndSwapNNode(c, mem, adr, val, ex, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypeNarrowOop::NULL_PTR) {
      return new CompareAndSwapNNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

class ShenandoahWeakCompareAndSwapPNode : public WeakCompareAndSwapPNode {
public:
  ShenandoahWeakCompareAndSwapPNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord)
    : WeakCompareAndSwapPNode(c, mem, adr, val, ex, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypePtr::NULL_PTR) {
      return new WeakCompareAndSwapPNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

class ShenandoahWeakCompareAndSwapNNode : public WeakCompareAndSwapNNode {
public:
  ShenandoahWeakCompareAndSwapNNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, MemNode::MemOrd mem_ord)
    : WeakCompareAndSwapNNode(c, mem, adr, val, ex, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypeNarrowOop::NULL_PTR) {
      return new WeakCompareAndSwapNNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

class ShenandoahCompareAndExchangePNode : public CompareAndExchangePNode {
public:
  ShenandoahCompareAndExchangePNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, const TypePtr* at, const Type* t, MemNode::MemOrd mem_ord)
    : CompareAndExchangePNode(c, mem, adr, val, ex, at, t, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypePtr::NULL_PTR) {
      return new CompareAndExchangePNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), adr_type(), bottom_type(), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

class ShenandoahCompareAndExchangeNNode : public CompareAndExchangeNNode {
public:
  ShenandoahCompareAndExchangeNNode(Node *c, Node *mem, Node *adr, Node *val, Node *ex, const TypePtr* at, const Type* t, MemNode::MemOrd mem_ord)
    : CompareAndExchangeNNode(c, mem, adr, val, ex, at, t, mem_ord) { }

  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape) {
    if (in(ExpectedIn) != NULL && phase->type(in(ExpectedIn)) == TypeNarrowOop::NULL_PTR) {
      return new CompareAndExchangeNNode(in(MemNode::Control), in(MemNode::Memory), in(MemNode::Address), in(MemNode::ValueIn), in(ExpectedIn), adr_type(), bottom_type(), order());
    }
    return NULL;
  }

  virtual int Opcode() const;
};

#endif // SHARE_VM_GC_SHENANDOAH_C2_SHENANDOAH_SUPPORT_HPP
