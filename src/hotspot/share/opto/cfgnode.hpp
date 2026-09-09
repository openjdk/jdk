/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_CFGNODE_HPP
#define SHARE_OPTO_CFGNODE_HPP

#include "opto/multnode.hpp"
#include "opto/node.hpp"
#include "opto/opcodes.hpp"
#include "opto/predicates_enums.hpp"
#include "opto/type.hpp"
#include "runtime/arguments.hpp"
#include "utilities/bitMap.hpp"

// Portions of code courtesy of Clifford Click

// Optimization - Graph Style

class Matcher;
class Node;
class   RegionNode;
class   TypeNode;
class     PhiNode;
class   GotoNode;
class   MultiNode;
class     MultiBranchNode;
class       IfNode;
class       PCTableNode;
class         JumpNode;
class         CatchNode;
class       NeverBranchNode;
class     BlackholeNode;
class   ProjNode;
class     CProjNode;
class       IfTrueNode;
class       IfFalseNode;
class       CatchProjNode;
class     JProjNode;
class       JumpProjNode;
class     SCMemProjNode;
class PhaseIdealLoop;
enum class AssertionPredicateType;
enum class PredicateState;

//------------------------------RegionNode-------------------------------------
// The class of RegionNodes, which can be mapped to basic blocks in the
// program.  Their inputs point to Control sources.  PhiNodes (described
// below) have an input point to a RegionNode.  Merged data inputs to PhiNodes
// correspond 1-to-1 with RegionNode inputs.  The zero input of a PhiNode is
// the RegionNode, and the zero input of the RegionNode is itself.
class RegionNode : public Node {
public:
  enum LoopStatus {
    // No guarantee: the region may be an irreducible loop entry, thus we have to
    // be careful when removing entry control to it.
    MaybeIrreducibleEntry,
    // Limited guarantee: this region may be (nested) inside an irreducible loop,
    // but it will never be an irreducible loop entry.
    NeverIrreducibleEntry,
    // Strong guarantee: this region is not (nested) inside an irreducible loop.
    Reducible,
  };

private:
  bool _is_unreachable_region;
  LoopStatus _loop_status;

  bool is_possible_unsafe_loop() const;
  bool is_unreachable_from_root(const PhaseGVN* phase) const;
public:
  // Node layout (parallels PhiNode):
  enum { Region,                // Generally points to self.
         Control                // Control arcs are [1..len)
  };

  RegionNode(uint required)
    : Node(required),
      _is_unreachable_region(false),
      _loop_status(LoopStatus::NeverIrreducibleEntry)
  {
    init_class_id(Class_Region);
    init_req(0, this);
  }

  Node* is_copy() const {
    const Node* r = _in[Region];
    if (r == nullptr)
      return nonnull_req();
    return nullptr;  // not a copy!
  }
  PhiNode* has_phi() const;        // returns an arbitrary phi user, or null
  PhiNode* has_unique_phi() const; // returns the unique phi user, or null
  // Is this region node unreachable from root?
  bool is_unreachable_region(const PhaseGVN* phase);
#ifdef ASSERT
  bool is_in_infinite_subgraph();
  static bool are_all_nodes_in_infinite_subgraph(Unique_Node_List& worklist);
#endif //ASSERT
  LoopStatus loop_status() const { return _loop_status; };
  void set_loop_status(LoopStatus status);
  bool can_be_irreducible_entry() const;

  virtual int Opcode() const;
  virtual uint size_of() const { return sizeof(*this); }
  virtual bool pinned() const { return (const Node*)in(0) == this; }
  virtual bool is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; } // CFG nodes do not hash
  virtual const Type* bottom_type() const { return Type::CONTROL; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);
  void remove_unreachable_subgraph(PhaseIterGVN* igvn);
  virtual const RegMask &out_RegMask() const;
  bool is_diamond() const;
  void try_clean_mem_phis(PhaseIterGVN* phase);
  bool optimize_trichotomy(PhaseIterGVN* igvn);
  NOT_PRODUCT(virtual void dump_spec(outputStream* st) const;)
};

//------------------------------JProjNode--------------------------------------
// jump projection for node that produces multiple control-flow paths
class JProjNode : public ProjNode {
 public:
  JProjNode( Node* ctrl, uint idx ) : ProjNode(ctrl,idx) {}
  virtual int Opcode() const;
  virtual bool  is_CFG() const { return true; }
  virtual uint  hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual const Node* is_block_proj() const { return in(0); }
  virtual const RegMask& out_RegMask() const;
  virtual uint  ideal_reg() const { return 0; }
};

//------------------------------PhiNode----------------------------------------
// PhiNodes merge values from different Control paths.  Slot 0 points to the
// controlling RegionNode.  Other slots map 1-for-1 with incoming control flow
// paths to the RegionNode.
class PhiNode : public TypeNode {
  friend class PhaseRenumberLive;

  const TypePtr* const _adr_type; // non-null only for Type::MEMORY nodes.
  // The following fields are only used for data PhiNodes to indicate
  // that the PhiNode represents the value of a known instance field.
        int _inst_mem_id; // Instance memory id (node index of the memory Phi)
        int _inst_id;     // Instance id of the memory slice.
  const int _inst_index;  // Alias index of the instance memory slice.
  // Array elements references have the same alias_idx but different offset.
  const int _inst_offset; // Offset of the instance memory slice.

  // Bottom memory Phis are peculiar. Consider a bottom memory Phi bot_phi and an arbitrary alias
  // class mem.
  //
  // 1. Sometimes, bot_phi does not contain the memory state corresponding to mem, and there is
  // another memory Phi mem_phi at the same region representing the memory state corresponding to
  // mem.
  //
  // if (b) {
  //   Call1();
  //   MemProj1(Call1);
  // } else {
  //   Call2();
  //   MemProj2(Call2);
  //   Store1(_, MemProj2, p, v): mem;
  // }
  // bot_phi = Phi(MemProj1, MemProj2);
  // mem_phi = Phi(MemProj1, Store1);
  //
  // In this example, bot_phi cannot contain the memory state corresponding to mem, because
  // MemProj2 does not capture the store into that memory.
  //
  // 2. Sometimes, bot_phi contains the memory state corresponding to mem, even if there is another
  // memory Phi at the same region representing the memory state corresponding to mem.
  //
  // Call1();
  // MemProj1(Call1);
  // Store1(_, MemProj1, p1, v): mem;
  // MergeMem1(MemProj1, Top, Store1);
  // Load1(_, Store1, p2): mem;
  // Load2(_, MergeMem, p3): mem;
  //
  // We somehow want to multiversion some statements:
  //
  // Call1();
  // MemProj1(Call1);
  // if (b) {
  //   Store11(_, MemProj1, p1, v): mem;
  //   MergeMem11(MemProj1, Top, Store11);
  // } else {
  //   Store12(_, MemProj1, p1, v): mem;
  //   MergeMem12(MemProj1, Top, Store12);
  // }
  // bot_phi = Phi(MergeMem11, MergeMem12);
  // mem_phi = Phi(Store11, Store12);
  // Load1(_, mem_phi, p2): mem;
  // Load2(_, bot_phi, p3): mem;
  //
  // In this example, bot_phi must contain the memory state corresponding to mem, because there is
  // a load corresponding to mem from it. This pattern can be eventually simplified after IGVN, but
  // until then, the existence of mem_phi does not mean that bot_phi does not contain the memory
  // state corresponding to mem.
  //
  // 3. Sometimes, bot_phi does not contain the memory state corresponding to mem, even if there is
  // not any memory Phi at the same region representing the memory state corresponding to mem.
  //
  // Call1();
  // MemProj1(Call1);
  // Store1(_, MemProj1, p, v);
  // if (b) {
  //   MergeMem1(MemProj1, Top, Store1);
  // }
  // bot_phi = Phi(MergeMem1, MemProj1);
  //
  // In this case, bot_phi does not contain the memory state corresponding to mem, because in one
  // branch, its input is MemProj1, which does not capture the latest store Store1 of the alias
  // class mem.
  //
  // Those situations can be solved by pushing the MergeMems down through their bottom memory Phi
  // outputs. However, naively doing so can lead to infinite loop, because a Phi can be a
  // transitive input of itself, which means keeping pushing the MergeMem down may eventually
  // results in it being an input of the original Phi again. To tackle that issue, we observe that
  // pushing a MergeMem down through a bottom memory Phi effectively splits that Phi into multiple
  // Phis each of which represents an alias class, and the new bottom memory Phi must not contain
  // the memory states of those alias classes that are split. For example, by transforming:
  //
  //   Proj1       Store
  //      \       /
  //       MergeMem        Proj2
  //             \       /
  //                Phi
  //                 |
  //                ...
  //
  // Into:
  //
  //   Proj1     Proj2    Store     Proj2(this is the same node as the other Proj2)
  //       \     /            \     /
  //         Phi1              Phi2
  //             \            /
  //                MergeMem
  //                   |
  //                  ...
  //
  // While it is uncertain which memory states the original Phi contains, it is certain that Phi1
  // (the new bottom memory Phi) does not contain the memory state corresponding to Phi2, because
  // it misses the latest Store into that alias class.
  //
  // As a result, if we record which alias classes have been split from each bottom memory Phi, it
  // is certain that the transformation will terminate, because if a MergeMem input of a bottom
  // memory Phi has all its non-top inputs being known to be excluded from that Phi, then the Phi
  // does not have to be split anymore (i.e. it can simply skip the MergeMem and is rewired to the
  // MergeMem's base input instead of having the MergeMem pushed through it). Otherwise, the split
  // is performed, more alias classes are split from the Phi, which means some progress is made.
  //
  // This does not need to be exact, it is fine if it misses alias classes that a bottom memory Phi
  // does not include, but it must not contain alias classes that the Phi includes.
  class ExcludedAliasIdx {
  private:
    BitMapView _view;

    ExcludedAliasIdx(BitMap::bm_word_t* payload, BitMap::idx_t bit_size) : _view(payload, bit_size) {}

  public:
    static const ExcludedAliasIdx* make(Compile* C, const BitMap& excluded_idx) {
      Arena* arena = C->node_arena();
      char* ptr = static_cast<char*>(arena->Amalloc(sizeof(ExcludedAliasIdx) + excluded_idx.size_in_bytes()));
      BitMap::bm_word_t* payload = reinterpret_cast<BitMap::bm_word_t*>(ptr + sizeof(ExcludedAliasIdx));
      memset(payload, 0, excluded_idx.size_in_bytes());
      ExcludedAliasIdx* res = ::new(ptr) ExcludedAliasIdx(payload, excluded_idx.size());
      res->_view.set_from(excluded_idx);
      return res;
    }

    bool contains(int alias_idx) const {
      assert(alias_idx >= 0, "invalid idx %d", alias_idx);
      BitMap::idx_t idx = alias_idx;
      return idx < _view.size() && _view.at(idx);
    }

    void copy_into(BitMap& dst) const {
      for (BitMap::Iterator iter(_view); !iter.is_empty(); iter.step()) {
        dst.set_bit(iter.index());
      }
    }
  };

  const ExcludedAliasIdx* _excluded_idx;

  // Size is bigger to hold the _adr_type field.
  virtual uint hash() const;    // Check the type
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const { return sizeof(*this); }

  // Determine if CMoveNode::is_cmove_id can be used at this join point.
  Node* is_cmove_id(PhaseTransform* phase, int true_path);
  bool wait_for_region_igvn(PhaseGVN* phase);
  bool is_data_loop(RegionNode* r, Node* uin, const PhaseGVN* phase);

  static Node* clone_through_phi(Node* root_phi, const Type* t, uint c, PhaseIterGVN* igvn);
  static Node* merge_through_phi(Node* root_phi, PhaseIterGVN* igvn);

  bool must_wait_for_region_in_irreducible_loop(PhaseGVN* phase) const;

  Node* split_through_mergemem(PhaseIterGVN& igvn);

  void verify_type_stability(const PhaseGVN* phase, const Type* union_of_input_types, const Type* new_type) const NOT_DEBUG_RETURN;
  bool wait_for_cast_input_igvn(const PhaseIterGVN* igvn) const;

public:
  // Node layout (parallels RegionNode):
  enum { Region,                // Control input is the Phi's region.
         Input                  // Input values are [1..len)
  };

  PhiNode( Node *r, const Type *t, const TypePtr* at = nullptr,
           const int imid = -1,
           const int iid = TypeOopPtr::InstanceTop,
           const int iidx = Compile::AliasIdxTop,
           const int ioffs = Type::OffsetTop )
    : TypeNode(t,r->req()),
      _adr_type(at),
      _inst_mem_id(imid),
      _inst_id(iid),
      _inst_index(iidx),
      _inst_offset(ioffs),
      _excluded_idx(nullptr)
  {
    init_class_id(Class_Phi);
    init_req(0, r);
    verify_adr_type();
  }
  // create a new phi with in edges matching r and set (initially) to x
  static PhiNode* make( Node* r, Node* x );
  // extra type arguments override the new phi's bottom_type and adr_type
  static PhiNode* make( Node* r, Node* x, const Type *t, const TypePtr* at = nullptr );
  // create a new phi with narrowed memory type
  PhiNode* slice_memory(const TypePtr* adr_type) const;
  PhiNode* split_out_instance(const TypePtr* at, PhaseIterGVN *igvn) const;
  // like make(r, x), but does not initialize the in edges to x
  static PhiNode* make_blank( Node* r, Node* x );

  // Accessors
  RegionNode* region() const { Node* r = in(Region); assert(!r || r->is_Region(), ""); return (RegionNode*)r; }

  bool is_tripcount(BasicType bt) const;

  // Determine a unique non-trivial input, if any.
  // Ignore casts if it helps.  Return null on failure.
  Node* unique_input(PhaseValues* phase, bool uncast);
  Node* unique_input(PhaseValues* phase) {
    Node* uin = unique_input(phase, false);
    if (uin == nullptr) {
      uin = unique_input(phase, true);
    }
    return uin;
  }
  Node* unique_constant_input_recursive(PhaseGVN* phase);

  // Check for a simple dead loop.
  enum LoopSafety { Safe = 0, Unsafe, UnsafeLoop };
  LoopSafety simple_data_loop_check(Node *in) const;
  // Is it unsafe data loop? It becomes a dead loop if this phi node removed.
  bool is_unsafe_data_reference(Node *in) const;
  bool is_dead_phi();
  int is_diamond_phi() const;
  bool try_clean_memory_phi(PhaseIterGVN* igvn);
  virtual int Opcode() const;
  virtual bool pinned() const { return in(0) != nullptr; }
  virtual const TypePtr *adr_type() const { verify_adr_type(true); return _adr_type; }

  void  set_inst_mem_id(int inst_mem_id) { _inst_mem_id = inst_mem_id; }
  int inst_mem_id() const { return _inst_mem_id; }
  int inst_id()     const { return _inst_id; }
  int inst_index()  const { return _inst_index; }
  int inst_offset() const { return _inst_offset; }
  bool is_same_inst_field(const Type* tp, int mem_id, int id, int index, int offset) {
    return type()->basic_type() == tp->basic_type() &&
           inst_mem_id() == mem_id &&
           inst_id()     == id     &&
           inst_index()  == index  &&
           inst_offset() == offset &&
           type()->higher_equal(tp);
  }

  bool can_be_value_type() const {
    return Arguments::is_valhalla_enabled() && _type->isa_instptr() && _type->is_instptr()->can_be_value_type();
  }

  Node* try_push_value_types_down(PhaseGVN* phase, bool can_reshape);
  DEBUG_ONLY(bool can_push_value_types_down(PhaseGVN* phase);)

  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const RegMask &out_RegMask() const;
  virtual const RegMask &in_RegMask(uint) const;
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
#ifdef ASSERT
  void verify_adr_type(VectorSet& visited, const TypePtr* at) const;
  void verify_adr_type(bool recursive = false) const;
#else //ASSERT
  void verify_adr_type(bool recursive = false) const {}
#endif //ASSERT

  const TypeTuple* collect_types(PhaseGVN* phase) const;
  bool can_be_replaced_by(PhaseGVN* phase, const PhiNode* other) const;
};

//------------------------------GotoNode---------------------------------------
// GotoNodes perform direct branches.
class GotoNode : public Node {
public:
  GotoNode( Node *control ) : Node(control) {}
  virtual int Opcode() const;
  virtual bool pinned() const { return true; }
  virtual bool  is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual const Node *is_block_proj() const { return this; }
  virtual const Type *bottom_type() const { return Type::CONTROL; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const RegMask &out_RegMask() const;
};

//------------------------------CProjNode--------------------------------------
// control projection for node that produces multiple control-flow paths
class CProjNode : public ProjNode {
public:
  CProjNode( Node *ctrl, uint idx ) : ProjNode(ctrl,idx) {}
  virtual int Opcode() const;
  virtual bool  is_CFG() const { return true; }
  virtual uint hash() const { return NO_HASH; }  // CFG nodes do not hash
  virtual const Node *is_block_proj() const { return in(0); }
  virtual const RegMask &out_RegMask() const;
  virtual uint ideal_reg() const { return 0; }
};

//---------------------------MultiBranchNode-----------------------------------
// This class defines a MultiBranchNode, a MultiNode which yields multiple
// control values. These are distinguished from other types of MultiNodes
// which yield multiple values, but control is always and only projection #0.
class MultiBranchNode : public MultiNode {
public:
  MultiBranchNode( uint required ) : MultiNode(required) {
    init_class_id(Class_MultiBranch);
  }
  // returns required number of users to be well formed.
  virtual uint required_outcnt() const = 0;
};

//------------------------------IfNode-----------------------------------------
// Output selected Control, based on a boolean test
class IfNode : public MultiBranchNode {
 public:
  float _prob;                           // Probability of true path being taken.
  float _fcnt;                           // Frequency counter

 private:
  AssertionPredicateType _assertion_predicate_type;

  void init_node(Node* control, Node* bol) {
    init_class_id(Class_If);
    init_req(0, control);
    init_req(1, bol);
  }

  // Size is bigger to hold the probability field.  However, _prob does not
  // change the semantics so it does not appear in the hash & cmp functions.
  virtual uint size_of() const { return sizeof(*this); }

  // Helper methods for fold_compares
  bool cmpi_folds(PhaseIterGVN* igvn, bool fold_ne = false);
  bool is_ctrl_folds(Node* ctrl, PhaseIterGVN* igvn);
  bool has_shared_region(IfProjNode* proj, IfProjNode*& success, IfProjNode*& fail) const;
  bool has_only_uncommon_traps(IfProjNode* proj, IfProjNode*& success, IfProjNode*& fail, PhaseIterGVN* igvn) const;
  Node* merge_uncommon_traps(IfProjNode* proj, IfProjNode* success, IfProjNode* fail, PhaseIterGVN* igvn);
  static void improve_address_types(Node* l, Node* r, ProjNode* fail, PhaseIterGVN* igvn);
  bool is_cmp_with_loadrange(IfProjNode* proj) const;
  bool is_null_check(IfProjNode* proj, PhaseIterGVN* igvn) const;
  bool is_side_effect_free_test(IfProjNode* proj, PhaseIterGVN* igvn) const;
  static void reroute_side_effect_free_unc(IfProjNode* proj, IfProjNode* dom_proj, PhaseIterGVN* igvn);
  bool fold_compares_helper(IfProjNode* proj, IfProjNode* success, IfProjNode* fail, PhaseIterGVN* igvn);
  static bool is_dominator_unc(CallStaticJavaNode* dom_unc, CallStaticJavaNode* unc);

protected:
  IfProjNode* range_check_trap_proj(int& flip, Node*& l, Node*& r) const;
  Node* Ideal_common(PhaseGVN *phase, bool can_reshape);
  Node* search_identical(int dist, PhaseIterGVN* igvn);

  Node* simple_subsuming(PhaseIterGVN* igvn);

public:

  // Degrees of branch prediction probability by order of magnitude:
  // PROB_UNLIKELY_1e(N) is a 1 in 1eN chance.
  // PROB_LIKELY_1e(N) is a 1 - PROB_UNLIKELY_1e(N)
#define PROB_UNLIKELY_MAG(N)    (1e- ## N ## f)
#define PROB_LIKELY_MAG(N)      (1.0f-PROB_UNLIKELY_MAG(N))

  // Maximum and minimum branch prediction probabilties
  // 1 in 1,000,000 (magnitude 6)
  //
  // Although PROB_NEVER == PROB_MIN and PROB_ALWAYS == PROB_MAX
  // they are used to distinguish different situations:
  //
  // The name PROB_MAX (PROB_MIN) is for probabilities which correspond to
  // very likely (unlikely) but with a concrete possibility of a rare
  // contrary case.  These constants would be used for pinning
  // measurements, and as measures for assertions that have high
  // confidence, but some evidence of occasional failure.
  //
  // The name PROB_ALWAYS (PROB_NEVER) is to stand for situations for which
  // there is no evidence at all that the contrary case has ever occurred.

#define PROB_NEVER              PROB_UNLIKELY_MAG(6)
#define PROB_ALWAYS             PROB_LIKELY_MAG(6)

#define PROB_MIN                PROB_UNLIKELY_MAG(6)
#define PROB_MAX                PROB_LIKELY_MAG(6)

  // Static branch prediction probabilities
  // 1 in 10 (magnitude 1)
#define PROB_STATIC_INFREQUENT  PROB_UNLIKELY_MAG(1)
#define PROB_STATIC_FREQUENT    PROB_LIKELY_MAG(1)

  // Fair probability 50/50
#define PROB_FAIR               (0.5f)

  // Unknown probability sentinel
#define PROB_UNKNOWN            (-1.0f)

  // Probability "constructors", to distinguish as a probability any manifest
  // constant without a names
#define PROB_LIKELY(x)          ((float) (x))
#define PROB_UNLIKELY(x)        (1.0f - (float)(x))

  // Other probabilities in use, but without a unique name, are documented
  // here for lack of a better place:
  //
  // 1 in 1000 probabilities (magnitude 3):
  //     threshold for converting to conditional move
  //     likelihood of null check failure if a null HAS been seen before
  //     likelihood of slow path taken in library calls
  //
  // 1 in 10,000 probabilities (magnitude 4):
  //     threshold for making an uncommon trap probability more extreme
  //     threshold for for making a null check implicit
  //     likelihood of needing a gc if eden top moves during an allocation
  //     likelihood of a predicted call failure
  //
  // 1 in 100,000 probabilities (magnitude 5):
  //     threshold for ignoring counts when estimating path frequency
  //     likelihood of FP clipping failure
  //     likelihood of catching an exception from a try block
  //     likelihood of null check failure if a null has NOT been seen before
  //
  // Magic manifest probabilities such as 0.83, 0.7, ... can be found in
  // gen_subtype_check() and catch_inline_exceptions().

  IfNode(Node* control, Node* bol, float p, float fcnt);
  IfNode(Node* control, Node* bol, float p, float fcnt, AssertionPredicateType assertion_predicate_type);

  static IfNode* make_with_same_profile(IfNode* if_node_profile, Node* ctrl, Node* bol);

  IfTrueNode* true_proj() const {
    return proj_out(true)->as_IfTrue();
  }

  IfTrueNode* true_proj_or_null() const {
    ProjNode* true_proj = proj_out_or_null(true);
    return true_proj == nullptr ? nullptr : true_proj->as_IfTrue();
  }

  IfFalseNode* false_proj() const {
    return proj_out(false)->as_IfFalse();
  }

  IfFalseNode* false_proj_or_null() const {
    ProjNode* false_proj = proj_out_or_null(false);
    return false_proj == nullptr ? nullptr : false_proj->as_IfFalse();
  }

  virtual int Opcode() const;
  virtual bool pinned() const { return true; }
  virtual const Type *bottom_type() const { return TypeTuple::IFBOTH; }
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual uint required_outcnt() const { return 2; }
  virtual const RegMask &out_RegMask() const;
  Node* fold_compares(PhaseIterGVN* phase);
  static Node* up_one_dom(Node* curr, bool linear_only = false);
  bool is_zero_trip_guard() const;
  Node* dominated_by(Node* prev_dom, PhaseIterGVN* igvn, bool prev_dom_not_imply_this);
  ProjNode* uncommon_trap_proj(CallStaticJavaNode*& call, Deoptimization::DeoptReason reason = Deoptimization::Reason_none) const;

  // Takes the type of val and filters it through the test represented
  // by if_proj and returns a more refined type if one is produced.
  // Returns null is it couldn't improve the type.
  static const TypeInt* filtered_int_type(PhaseGVN* phase, Node* val, Node* if_proj);

  bool is_flat_array_check(PhaseTransform* phase, Node** array = nullptr);

  AssertionPredicateType assertion_predicate_type() const {
    return _assertion_predicate_type;
  }

#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif

  bool same_condition(const Node* dom, PhaseIterGVN* igvn) const;
  void mark_projections_unsafe_for_fold_compare() const;
};

class RangeCheckNode : public IfNode {
private:
  int is_range_check(Node*& range, Node*& index, jint& offset);

public:
  RangeCheckNode(Node* control, Node* bol, float p, float fcnt) : IfNode(control, bol, p, fcnt) {
    init_class_id(Class_RangeCheck);
  }

  RangeCheckNode(Node* control, Node* bol, float p, float fcnt, AssertionPredicateType assertion_predicate_type)
      : IfNode(control, bol, p, fcnt, assertion_predicate_type) {
    init_class_id(Class_RangeCheck);
  }

  virtual int Opcode() const;
  virtual Node* Ideal(PhaseGVN *phase, bool can_reshape);
};

// Special node that denotes a Parse Predicate added during parsing. A Parse Predicate serves as placeholder to later
// create Regular Predicates (Runtime Predicates with possible Assertion Predicates) above it. Together they form a
// Predicate Block. The Parse Predicate and Regular Predicates share the same uncommon trap.
// There are three kinds of Parse Predicates:
// Loop Parse Predicate, Profiled Loop Parse Predicate (both used by Loop Predication), and Loop Limit Check Parse
// Predicate (used for integer overflow checks when creating a counted loop).
// More information about predicates can be found in loopPredicate.cpp.
class ParsePredicateNode : public IfNode {
  Deoptimization::DeoptReason _deopt_reason;

  // When a Parse Predicate loses its connection to a loop head, it will be marked useless by
  // EliminateUselessPredicates and cleaned up by Value(). It can also become useless when cloning it to both loops
  // during Loop Multiversioning - we no longer use the old version.
  PredicateState _predicate_state;
 public:
  ParsePredicateNode(Node* control, Deoptimization::DeoptReason deopt_reason, PhaseGVN* gvn);
  virtual int Opcode() const;
  virtual uint size_of() const { return sizeof(*this); }

  Deoptimization::DeoptReason deopt_reason() const {
    return _deopt_reason;
  }

  bool is_useless() const {
    return _predicate_state == PredicateState::Useless;
  }

  void mark_useless(PhaseIterGVN& igvn);

  void mark_maybe_useful() {
    _predicate_state = PredicateState::MaybeUseful;
  }

  bool is_useful() const {
    return _predicate_state == PredicateState::Useful;
  }

  void mark_useful() {
    _predicate_state = PredicateState::Useful;
  }

  // Return the uncommon trap If projection of this Parse Predicate.
  ParsePredicateUncommonProj* uncommon_proj() const {
    return false_proj();
  }

  Node* uncommon_trap() const;

  Node* Ideal(PhaseGVN* phase, bool can_reshape) {
    return nullptr; // Don't optimize
  }

  const Type* Value(PhaseGVN* phase) const;
  NOT_PRODUCT(void dump_spec(outputStream* st) const;)
};

class IfProjNode : public CProjNode {
public:
  IfProjNode(IfNode *ifnode, uint idx) : CProjNode(ifnode,idx) {}
  virtual Node* Identity(PhaseGVN* phase);

  // Return the other IfProj node.
  IfProjNode* other_if_proj() const {
    return in(0)->as_If()->proj_out(1 - _con)->as_IfProj();
  }

  void pin_dependent_nodes(PhaseIterGVN* igvn);

protected:
  // Type of If input when this branch is always taken
  virtual bool always_taken(const TypeTuple* t) const = 0;
};

class IfTrueNode : public IfProjNode {
public:
  IfTrueNode( IfNode *ifnode ) : IfProjNode(ifnode,1) {
    init_class_id(Class_IfTrue);
  }
  virtual int Opcode() const;

protected:
  virtual bool always_taken(const TypeTuple* t) const { return t == TypeTuple::IFTRUE; }
};

class IfFalseNode : public IfProjNode {
public:
  IfFalseNode( IfNode *ifnode ) : IfProjNode(ifnode,0) {
    init_class_id(Class_IfFalse);
  }
  virtual int Opcode() const;

protected:
  virtual bool always_taken(const TypeTuple* t) const { return t == TypeTuple::IFFALSE; }
};


//------------------------------PCTableNode------------------------------------
// Build an indirect branch table.  Given a control and a table index,
// control is passed to the Projection matching the table index.  Used to
// implement switch statements and exception-handling capabilities.
// Undefined behavior if passed-in index is not inside the table.
class PCTableNode : public MultiBranchNode {
  virtual uint hash() const;    // Target count; table size
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const { return sizeof(*this); }

public:
  const uint _size;             // Number of targets

  PCTableNode( Node *ctrl, Node *idx, uint size ) : MultiBranchNode(2), _size(size) {
    init_class_id(Class_PCTable);
    init_req(0, ctrl);
    init_req(1, idx);
  }
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual const Type *bottom_type() const;
  virtual bool pinned() const { return true; }
  virtual uint required_outcnt() const { return _size; }
};

//------------------------------JumpNode---------------------------------------
// Indirect branch.  Uses PCTable above to implement a switch statement.
// It emits as a table load and local branch.
class JumpNode : public PCTableNode {
  virtual uint size_of() const { return sizeof(*this); }
public:
  float* _probs; // probability of each projection
  float _fcnt;   // total number of times this Jump was executed
  JumpNode( Node* control, Node* switch_val, uint size, float* probs, float cnt)
    : PCTableNode(control, switch_val, size),
      _probs(probs), _fcnt(cnt) {
    init_class_id(Class_Jump);
  }
  virtual int   Opcode() const;
  virtual const RegMask& out_RegMask() const;
  virtual const Node* is_block_proj() const { return this; }
};

class JumpProjNode : public JProjNode {
  virtual uint hash() const;
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const { return sizeof(*this); }

 private:
  const int  _dest_bci;
  const uint _proj_no;
  const int  _switch_val;
 public:
  JumpProjNode(Node* jumpnode, uint proj_no, int dest_bci, int switch_val)
    : JProjNode(jumpnode, proj_no), _dest_bci(dest_bci), _proj_no(proj_no), _switch_val(switch_val) {
    init_class_id(Class_JumpProj);
  }

  virtual int Opcode() const;
  virtual const Type* bottom_type() const { return Type::CONTROL; }
  int  dest_bci()    const { return _dest_bci; }
  int  switch_val()  const { return _switch_val; }
  uint proj_no()     const { return _proj_no; }
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
  virtual void dump_compact_spec(outputStream *st) const;
#endif
};

//------------------------------CatchNode--------------------------------------
// Helper node to fork exceptions.  "Catch" catches any exceptions thrown by
// a just-prior call.  Looks like a PCTableNode but emits no code - just the
// table.  The table lookup and branch is implemented by RethrowNode.
class CatchNode : public PCTableNode {
public:
  CatchNode( Node *ctrl, Node *idx, uint size ) : PCTableNode(ctrl,idx,size){
    init_class_id(Class_Catch);
  }
  virtual int Opcode() const;
  virtual const Type* Value(PhaseGVN* phase) const;
};

// CatchProjNode controls which exception handler is targeted after a call.
// It is passed in the bci of the target handler, or no_handler_bci in case
// the projection doesn't lead to an exception handler.
class CatchProjNode : public CProjNode {
  virtual uint hash() const;
  virtual bool cmp( const Node &n ) const;
  virtual uint size_of() const { return sizeof(*this); }

private:
  const int _handler_bci;

public:
  enum {
    fall_through_index =  0,      // the fall through projection index
    catch_all_index    =  1,      // the projection index for catch-alls
    no_handler_bci     = -1       // the bci for fall through or catch-all projs
  };

  CatchProjNode(Node* catchnode, uint proj_no, int handler_bci)
    : CProjNode(catchnode, proj_no), _handler_bci(handler_bci) {
    init_class_id(Class_CatchProj);
    assert(proj_no != fall_through_index || handler_bci < 0, "fall through case must have bci < 0");
  }

  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual const Type *bottom_type() const { return Type::CONTROL; }
  int  handler_bci() const        { return _handler_bci; }
  bool is_handler_proj() const    { return _handler_bci >= 0; }
#ifndef PRODUCT
  virtual void dump_spec(outputStream *st) const;
#endif
};


//---------------------------------CreateExNode--------------------------------
// Helper node to create the exception coming back from a call
class CreateExNode : public TypeNode {
public:
  CreateExNode(const Type* t, Node* control, Node* i_o) : TypeNode(t, 2) {
    init_req(0, control);
    init_req(1, i_o);
  }
  virtual int Opcode() const;
  virtual Node* Identity(PhaseGVN* phase);
  virtual bool pinned() const { return true; }
  uint match_edge(uint idx) const { return 0; }
  virtual uint ideal_reg() const { return Op_RegP; }
};

//------------------------------NeverBranchNode-------------------------------
// The never-taken branch.  Used to give the appearance of exiting infinite
// loops to those algorithms that like all paths to be reachable.  Encodes
// empty.
class NeverBranchNode : public MultiBranchNode {
public:
  NeverBranchNode(Node* ctrl) : MultiBranchNode(1) {
    init_req(0, ctrl);
    init_class_id(Class_NeverBranch);
  }
  virtual int Opcode() const;
  virtual bool pinned() const { return true; };
  virtual const Type *bottom_type() const { return TypeTuple::IFBOTH; }
  virtual const Type* Value(PhaseGVN* phase) const;
  virtual Node *Ideal(PhaseGVN *phase, bool can_reshape);
  virtual uint required_outcnt() const { return 2; }
  virtual void emit(C2_MacroAssembler *masm, PhaseRegAlloc *ra_) const { }
  virtual uint size(PhaseRegAlloc *ra_) const { return 0; }
#ifndef PRODUCT
  virtual void format( PhaseRegAlloc *, outputStream *st ) const;
#endif
};

//------------------------------BlackholeNode----------------------------
// Blackhole all arguments. This node would survive through the compiler
// the effects on its arguments, and would be finally matched to nothing.
class BlackholeNode : public MultiNode {
public:
  BlackholeNode(Node* ctrl) : MultiNode(1) {
    init_req(TypeFunc::Control, ctrl);
    init_class_id(Class_Blackhole);
  }
  virtual int   Opcode() const;
  virtual uint ideal_reg() const { return 0; } // not matched in the AD file
  virtual const Type* bottom_type() const { return TypeTuple::MEMBAR; }
  virtual Node* Ideal(PhaseGVN* phase, bool can_reshape);

  const RegMask &in_RegMask(uint idx) const {
    // Fake the incoming arguments mask for blackholes: accept all registers
    // and all stack slots. This would avoid any redundant register moves
    // for blackhole inputs.
    return RegMask::ALL;
  }
#ifndef PRODUCT
  virtual void format(PhaseRegAlloc* ra, outputStream* st) const;
#endif
};


#endif // SHARE_OPTO_CFGNODE_HPP
