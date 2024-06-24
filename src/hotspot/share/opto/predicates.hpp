/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_PREDICATES_HPP
#define SHARE_OPTO_PREDICATES_HPP

#include "opto/cfgnode.hpp"
#include "opto/connode.hpp"
#include "opto/opaquenode.hpp"

/*
 * There are different kinds of predicates throughout the code. We differentiate between the following predicates:
 *
 * - Regular Predicate: This term is used to refer to a Runtime Predicate or an Assertion Predicate and can be used to
 *                      distinguish from any Parse Predicate which is not a real predicate but rather a placeholder.
 * - Parse Predicate: Added during parsing to capture the current JVM state. This predicate represents a "placeholder"
 *                    above which Regular Predicates can be created later after parsing.
 *
 *                    There are initially three Parse Predicates for each loop:
 *                    - Loop Parse Predicate:             The Parse Predicate added for Loop Predicates.
 *                    - Profiled Loop Parse Predicate:    The Parse Predicate added for Profiled Loop Predicates.
 *                    - Loop Limit Check Parse Predicate: The Parse Predicate added for a Loop Limit Check Predicate.
 * - Runtime Predicate: This term is used to refer to a Hoisted Check Predicate (either a Loop Predicate or a Profiled
 *                      Loop Predicate) or a Loop Limit Check Predicate. These predicates will be checked at runtime while
 *                      the Parse and Assertion Predicates are always removed before code generation (except for
 *                      Initialized Assertion Predicates which are kept in debug builds while being removed in product
 *                      builds).
 *     - Hoisted Check Predicate: Either a Loop Predicate or a Profiled Loop Predicate that is created during Loop
 *                                Predication to hoist a check out of a loop.
 *         - Loop Predicate:     This predicate is created to hoist a loop-invariant check or a range check of the
 *                               form "a[i*scale + offset]", where scale and offset are loop-invariant, out of a
 *                               counted loop. The hoisted check must be executed in each loop iteration. This predicate
 *                               is created during Loop Predication and is inserted above the Loop Parse Predicate. Each
 *                               predicate for a range check is accompanied by additional Assertion Predicates (see below).
 *         - Profiled Loop:      This predicate is very similar to a Loop Predicate but the check to be hoisted does not
 *           Predicate           need to be executed in each loop iteration. By using profiling information, only checks
 *                               with a high execution frequency are chosen to be replaced by a Profiled Loop Predicate.
 *                               This predicate is created during Loop Predication and is inserted above the Profiled
 *                               Loop Parse Predicate.
 *     - Loop Limit Check:   This predicate is created when transforming a loop to a counted loop to protect against
 *       Predicate           the case when adding the stride to the induction variable would cause an overflow which
 *                           will not satisfy the loop limit exit condition. This overflow is unexpected for further
 *                           counted loop optimizations and could lead to wrong results. Therefore, when this predicate
 *                           fails at runtime, we must trap and recompile the method without turning the loop into a
 *                           counted loop to avoid these overflow problems.
 *                           The predicate does not replace an actual check inside the loop. This predicate can only
 *                           be added once above the Loop Limit Check Parse Predicate for a loop.
 * - Assertion Predicate: An always true predicate which will never fail (its range is already covered by an earlier
 *                        Hoisted Check Predicate or the main-loop entry guard) but is required in order to fold away a
 *                        dead sub loop in which some data could be proven to be dead (by the type system) and replaced
 *                        by top. Without such Assertion Predicates, we could find that type ranges in Cast and ConvX2Y
 *                        data nodes become impossible and are replaced by top. This is an indicator that the sub loop
 *                        is never executed and must be dead. But there is no way for C2 to prove that the sub loop is
 *                        actually dead. Assertion Predicates come to the rescue to fold such seemingly dead sub loops
 *                        away to avoid a broken graph. Assertion Predicates are left in the graph as a sanity checks in
 *                        debug builds (they must never fail at runtime) while they are being removed in product builds.
 *                        We use special Opaque4 nodes to block some optimizations and replace the Assertion Predicates
 *                        later in product builds.
 *
 *                        There are two kinds of Assertion Predicates:
 *                        - Template Assertion Predicate:    A template for an Assertion Predicate that uses OpaqueLoop*
 *                                                           nodes as placeholders for the init and stride value of a loop.
 *                                                           This predicate does not represent an actual check, yet, and
 *                                                           just serves as a template to create an Initialized Assertion
 *                                                           Predicate for a (sub) loop.
 *                        - Initialized Assertion Predicate: An Assertion Predicate that represents an actual check for a
 *                                                           (sub) loop that was initialized by cloning a Template
 *                                                           Assertion Predicate. The check is always true and is covered
 *                                                           by an earlier check (a Hoisted Check Predicate or the
 *                                                           main-loop entry guard).
 *
 *                        Assertion Predicates are required when removing a range check from a loop. These are inserted
 *                        either at Loop Predication or at Range Check Elimination:
 *                        - Loop Predication:        A range check inside a loop is replaced by a Hoisted Check Predicate
 *                                                   before the loop. We add two additional Template Assertion Predicates
 *                                                   from which we can later create Initialized Assertion Predicates. One
 *                                                   would have been enough if the number of array accesses inside a sub
 *                                                   loop does not change. But when unrolling the sub loop, we are
 *                                                   doubling the number of array accesses - we need to cover them all.
 *                                                   To do that, we only need to create an Initialized Assertion Predicate
 *                                                   for the first, initial value and for the last value:
 *                                                   Let a[i] be an array access in the original, not-yet unrolled loop
 *                                                   with stride 1. When unrolling this loop, we double the stride
 *                                                   (i.e. stride 2) and have now two accesses a[i] and a[i+1]. We need
 *                                                   checks for both. When further unrolling this loop, we only need to
 *                                                   keep the checks on the first and last access (e.g. a[i] and a[i+3]
 *                                                   on the next unrolling step as they cover the checks in the middle
 *                                                   for a[i+1] and a[i+2]).
 *                                                   Therefore, we just need to cover:
 *                                                   - Initial value: a[init]
 *                                                   - Last value: a[init + new stride - original stride]
 *                                                   (We could still only use one Template Assertion Predicate to create
 *                                                   both Initialized Assertion Predicates from - might be worth doing
 *                                                   at some point).
 *                                                   When later splitting a loop (pre/main/post, peeling, unrolling),
 *                                                   we create two Initialized Assertion Predicates from the Template
 *                                                   Assertion Predicates by replacing the OpaqueLoop* nodes by actual
 *                                                   values. Initially (before unrolling), both Assertion Predicates are
 *                                                   equal. The Initialized Assertion Predicates are always true because
 *                                                   their range is covered by a corresponding Hoisted Check Predicate.
 *                        - Range Check Elimination: A range check is removed from the main-loop by changing the pre
 *                                                   and main-loop iterations. We add two additional Template Assertion
 *                                                   Predicates (see explanation in section above) and one Initialized
 *                                                   Assertion Predicate for the just removed range check. When later
 *                                                   unrolling the main-loop, we create two Initialized Assertion
 *                                                   Predicates from the Template Assertion Predicates by replacing the
 *                                                   OpaqueLoop* nodes by actual values for the unrolled loop.
 *                                                   The Initialized Assertion Predicates are always true: They are true
 *                                                   when entering the main-loop (because we adjusted the pre-loop exit
 *                                                   condition), when executing the last iteration of the main-loop
 *                                                   (because we adjusted the main-loop exit condition), and during all
 *                                                   other iterations of the main-loop in-between by implication.
 *                                                   Note that Range Check Elimination could remove additional range
 *                                                   checks which were not possible to remove with Loop Predication
 *                                                   before (for example, because no Parse Predicates were available
 *                                                   before the loop to create Hoisted Check Predicates with).
 *
 *
 * In order to group predicates and refer to them throughout the code, we introduce the following additional term:
 * - Predicate Block: A block containing all Runtime Predicates, including the Assertion Predicates for Range Check
 *                    Predicates, and the associated Parse Predicate which all share the same uncommon trap. This block
 *                    could be empty if there were no Runtime Predicates created and the Parse Predicate was already
 *                    removed.
 *                    There are three different Predicate Blocks:
 *                    - Loop Predicate Block: Groups the Loop Predicates (if any), including the Assertion Predicates,
 *                                            and the Loop Parse Predicate (if not removed, yet) together.
 *                    - Profiled Loop         Groups the Profiled Loop Predicates (if any), including the Assertion
 *                      Predicate Block:      Predicates, and the Profiled Loop Parse Predicate (if not removed, yet)
 *                                            together.
 *                    - Loop Limit Check      Groups the Loop Limit Check Predicate (if created) and the Loop Limit
 *                      Predicate Block:      Check Parse Predicate (if not removed, yet) together.
 *
 *
 * Initially, before applying any loop-splitting optimizations, we find the following structure after Loop Predication
 * (predicates inside square brackets [] do not need to exist if there are no checks to hoist):
 *
 *   [Loop Predicate 1 + two Template Assertion Predicates]            \
 *   [Loop Predicate 2 + two Template Assertion Predicates]            |
 *   ...                                                               | Loop Predicate Block
 *   [Loop Predicate n + two Template Assertion Predicates]            |
 * Loop Parse Predicate                                                /
 *
 *   [Profiled Loop Predicate 1 + two Template Assertion Predicates]   \
 *   [Profiled Loop Predicate 2 + two Template Assertion Predicates]   | Profiled Loop
 *   ...                                                               | Predicate Block
 *   [Profiled Loop Predicate m + two Template Assertion Predicates]   |
 * Profiled Loop Parse Predicate                                       /
 *
 *   [Loop Limit Check Predicate] (at most one)                        \ Loop Limit Check
 * Loop Limit Check Parse Predicate                                    / Predicate Block
 * Loop Head
 *
 * As an example, let's look at how the predicate structure looks for the main-loop after creating pre/main/post loops
 * and applying Range Check Elimination (the order is insignificant):
 *
 * Main Loop entry (zero-trip) guard
 *   [For Loop Predicate 1: Two Template + two Initialized Assertion Predicates]
 *   [For Loop Predicate 2: Two Template + two Initialized Assertion Predicates]
 *   ...
 *   [For Loop Predicate n: Two Template + two Initialized Assertion Predicates]
 *
 *   [For Profiled Loop Predicate 1: Two Template + two Initialized Assertion Predicates]
 *   [For Profiled Loop Predicate 2: Two Template + two Initialized Assertion Predicates]
 *   ...
 *   [For Profiled Loop Predicate m: Two Template + two Initialized Assertion Predicates]
 *
 *   (after unrolling, we have two Initialized Assertion Predicates for the Assertion Predicates of Range Check Elimination)
 *   [For Range Check Elimination Check 1: Two Templates + one Initialized Assertion Predicate]
 *   [For Range Check Elimination Check 2: Two Templates + one Initialized Assertion Predicate]
 *   ...
 *   [For Range Check Elimination Check k: Two Templates + one Initialized Assertion Predicate]
 * Main Loop Head
 */


// Class to represent Assertion Predicates with a HaltNode instead of an UCT (i.e. either an Initialized Assertion
// Predicate or a Template Assertion Predicate created after the initial one at Loop Predication).
class AssertionPredicatesWithHalt : public StackObj {
  Node* _entry;

  static Node* find_entry(Node* start_proj);

 public:
  AssertionPredicatesWithHalt(Node* assertion_predicate_proj) : _entry(find_entry(assertion_predicate_proj)) {}

  // Returns the control input node into the first assertion predicate If. If there are no assertion predicates, it
  // returns the same node initially passed to the constructor.
  Node* entry() const {
    return _entry;
  }
};

// Class to represent a single Assertion Predicate with a HaltNode. This could either be:
// - A Template Assertion Predicate.
// - An Initialized Assertion Predicate.
// Note that all other Regular Predicates have an UCT node.
class AssertionPredicateWithHalt : public StackObj {
  static bool has_assertion_predicate_opaque(const Node* predicate_proj);
  static bool has_halt(const Node* success_proj);
 public:
  static bool is_predicate(const Node* maybe_success_proj);
};

// Class to represent a single Regular Predicate with an UCT. This could either be:
// - A Runtime Predicate
// - A Template Assertion Predicate
// Note that all other Regular Predicates have a Halt node.
class RegularPredicateWithUCT : public StackObj {
  static Deoptimization::DeoptReason uncommon_trap_reason(IfProjNode* if_proj);
  static bool may_be_predicate_if(Node* node);

 public:
  static bool is_predicate(Node* maybe_success_proj);
  static bool is_predicate(Node* node, Deoptimization::DeoptReason deopt_reason);
};

// Class to represent a Parse Predicate.
class ParsePredicate : public StackObj {
  ParsePredicateSuccessProj* _success_proj;
  ParsePredicateNode* _parse_predicate_node;
  Node* _entry;

  static IfTrueNode* init_success_proj(const Node* parse_predicate_proj) {
    assert(parse_predicate_proj != nullptr, "must not be null");
    return parse_predicate_proj->isa_IfTrue();
  }

  static ParsePredicateNode* init_parse_predicate(Node* parse_predicate_proj, Deoptimization::DeoptReason deopt_reason);

 public:
  ParsePredicate(Node* parse_predicate_proj, Deoptimization::DeoptReason deopt_reason)
      : _success_proj(init_success_proj(parse_predicate_proj)),
        _parse_predicate_node(init_parse_predicate(parse_predicate_proj, deopt_reason)),
        _entry(_parse_predicate_node != nullptr ? _parse_predicate_node->in(0) : parse_predicate_proj) {}

  // Returns the control input node into this Parse Predicate if it is valid. Otherwise, it returns the passed node
  // into the constructor of this class.
  Node* entry() const {
    return _entry;
  }

  // This Parse Predicate is valid if the node passed to the constructor is a projection of a ParsePredicateNode and the
  // deopt_reason of the uncommon trap of the ParsePredicateNode matches the passed deopt_reason to the constructor.
  bool is_valid() const {
    return _parse_predicate_node != nullptr;
  }

  ParsePredicateNode* node() const {
    assert(is_valid(), "must be valid");
    return _parse_predicate_node;
  }

  ParsePredicateSuccessProj* success_proj() const {
    assert(is_valid(), "must be valid");
    return _success_proj;
  }

  static bool is_predicate(Node* maybe_success_proj);
};

// Utility class for queries on Runtime Predicates.
class RuntimePredicate : public StackObj {
 public:
  static bool is_success_proj(Node* node, Deoptimization::DeoptReason deopt_reason);
};

// Interface to transform OpaqueLoopInit and OpaqueLoopStride nodes of a Template Assertion Predicate Expression.
class TransformStrategyForOpaqueLoopNodes : public StackObj {
 public:
  virtual Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const = 0;
  virtual Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const = 0;
};

// A Template Assertion Predicate Expression represents the Opaque4Node for the initial value or the last value of a
// Template Assertion Predicate and all the nodes up to and including the OpaqueLoop* nodes.
class TemplateAssertionPredicateExpression : public StackObj {
  Opaque4Node* _opaque4_node;

 public:
  explicit TemplateAssertionPredicateExpression(Opaque4Node* opaque4_node) : _opaque4_node(opaque4_node) {}

 private:
  Opaque4Node* clone(const TransformStrategyForOpaqueLoopNodes& transform_strategy, Node* new_ctrl, PhaseIdealLoop* phase);

 public:
  Opaque4Node* clone(Node* new_ctrl, PhaseIdealLoop* phase);
  Opaque4Node* clone_and_replace_init(Node* new_init, Node* new_ctrl,PhaseIdealLoop* phase);
  Opaque4Node* clone_and_replace_init_and_stride(Node* new_init, Node* new_stride, Node* new_ctrl, PhaseIdealLoop* phase);
};

// Class to represent a node being part of a Template Assertion Predicate Expression.
//
// The expression itself can belong to no, one, or two Template Assertion Predicates:
// - None: This node is already dead (i.e. we replaced the Bool condition of the Template Assertion Predicate).
// - Two: A OpaqueLoopInitNode could be part of two Template Assertion Predicates.
// - One: In all other cases.
class TemplateAssertionPredicateExpressionNode : public StackObj {
  Node* const _node;

 public:
  explicit TemplateAssertionPredicateExpressionNode(Node* node) : _node(node) {
    assert(is_in_expression(node), "must be valid");
  }
  NONCOPYABLE(TemplateAssertionPredicateExpressionNode);

 private:
  static bool is_template_assertion_predicate(Node* node);

 public:
  // Check whether the provided node is part of a Template Assertion Predicate Expression or not.
  static bool is_in_expression(Node* node);

  // Check if the opcode of node could be found in a Template Assertion Predicate Expression.
  // This also provides a fast check whether a node is unrelated.
  static bool is_maybe_in_expression(const Node* node) {
    const int opcode = node->Opcode();
    return (node->is_OpaqueLoopInit() ||
            node->is_OpaqueLoopStride() ||
            node->is_Bool() ||
            node->is_Cmp() ||
            opcode == Op_AndL ||
            opcode == Op_OrL ||
            opcode == Op_RShiftL ||
            opcode == Op_LShiftL ||
            opcode == Op_LShiftI ||
            opcode == Op_AddL ||
            opcode == Op_AddI ||
            opcode == Op_MulL ||
            opcode == Op_MulI ||
            opcode == Op_SubL ||
            opcode == Op_SubI ||
            opcode == Op_ConvI2L ||
            opcode == Op_CastII);
  }

  // Apply the given function to all Template Assertion Predicates (if any) to which this Template Assertion Predicate
  // Expression Node belongs to.
  template <class Callback>
  void for_each_template_assertion_predicate(Callback callback) {
    ResourceMark rm;
    Unique_Node_List list;
    list.push(_node);
    DEBUG_ONLY(int template_counter = 0;)
    for (uint i = 0; i < list.size(); i++) {
      Node* next = list.at(i);
      if (is_template_assertion_predicate(next)) {
        callback(next->as_If());
        DEBUG_ONLY(template_counter++;)
      } else {
        assert(!next->is_CFG(), "no CFG expected in Template Assertion Predicate Expression");
        list.push_outputs_of(next);
      }
    }

    // Each node inside a Template Assertion Predicate Expression is in between a Template Assertion Predicate and
    // its OpaqueLoop* nodes (or an OpaqueLoop* node itself). The OpaqueLoop* nodes do not common up. Therefore, each
    // Template Assertion Predicate Expression node belongs to a single expression - except for OpaqueLoopInitNodes.
    // An OpaqueLoopInitNode is shared between the init and last value Template Assertion Predicate at creation.
    // Later, when cloning the expressions, they are no longer shared.
    assert(template_counter <= 2, "a node cannot be part of more than two templates");
    assert(template_counter <= 1 || _node->is_OpaqueLoopInit(), "only OpaqueLoopInit nodes can be part of two templates");
  }
};

// This class represents a Predicate Block (i.e. either a Loop Predicate Block, a Profiled Loop Predicate Block,
// or a Loop Limit Check Predicate Block). It contains zero or more Regular Predicates followed by a Parse Predicate
// which, however, does not need to exist (we could already have decided to remove Parse Predicates for this loop).
class PredicateBlock : public StackObj {
  ParsePredicate _parse_predicate; // Could be missing.
  Node* _entry;

  static Node* skip_regular_predicates(Node* regular_predicate_proj, Deoptimization::DeoptReason deopt_reason);
  DEBUG_ONLY(void verify_block();)

 public:
  PredicateBlock(Node* predicate_proj, Deoptimization::DeoptReason deopt_reason)
      : _parse_predicate(predicate_proj, deopt_reason),
        _entry(skip_regular_predicates(_parse_predicate.entry(), deopt_reason)) {
    DEBUG_ONLY(verify_block();)
  }

  // Returns the control input node into this Regular Predicate block. This is either:
  // - The control input to the first If node in the block representing a Runtime Predicate if there is at least one
  //   Runtime Predicate.
  // - The control input node into the ParsePredicate node if there is only a Parse Predicate and no Runtime Predicate.
  // - The same node initially passed to the constructor if this Regular Predicate block is empty (i.e. no Parse
  //   Predicate or Runtime Predicate).
  Node* entry() const {
    return _entry;
  }

  bool is_non_empty() const {
    return has_parse_predicate() || has_runtime_predicates();
  }

  bool has_parse_predicate() const {
    return _parse_predicate.is_valid();
  }

  ParsePredicateNode* parse_predicate() const {
    return _parse_predicate.node();
  }

  ParsePredicateSuccessProj* parse_predicate_success_proj() const {
    return _parse_predicate.success_proj();
  }

  bool has_runtime_predicates() const {
    return _parse_predicate.entry() != _entry;
  }

  // Returns either:
  // - The entry to the Parse Predicate if present.
  // - The last Runtime Predicate success projection if Parse Predicate is not present.
  // - The entry to this Regular Predicate Block if the block is empty.
  Node* skip_parse_predicate() const {
    return _parse_predicate.entry();
  }
};

// This class takes a loop entry node and finds all the available predicates for the loop.
class Predicates : public StackObj {
  Node* _loop_entry;
  PredicateBlock _loop_limit_check_predicate_block;
  PredicateBlock _profiled_loop_predicate_block;
  PredicateBlock _loop_predicate_block;
  Node* _entry;

 public:
  Predicates(Node* loop_entry)
      : _loop_entry(loop_entry),
        _loop_limit_check_predicate_block(loop_entry, Deoptimization::Reason_loop_limit_check),
        _profiled_loop_predicate_block(_loop_limit_check_predicate_block.entry(),
                                       Deoptimization::Reason_profile_predicate),
        _loop_predicate_block(_profiled_loop_predicate_block.entry(),
                              Deoptimization::Reason_predicate),
        _entry(_loop_predicate_block.entry()) {}

  // Returns the control input the first predicate if there are any predicates. If there are no predicates, the same
  // node initially passed to the constructor is returned.
  Node* entry() const {
    return _entry;
  }

  const PredicateBlock* loop_predicate_block() const {
    return &_loop_predicate_block;
  }

  const PredicateBlock* profiled_loop_predicate_block() const {
    return &_profiled_loop_predicate_block;
  }

  const PredicateBlock* loop_limit_check_predicate_block() const {
    return &_loop_limit_check_predicate_block;
  }

  bool has_any() const {
    return _entry != _loop_entry;
  }
};

// This class iterates over the Parse Predicates of a loop.
class ParsePredicateIterator : public StackObj {
  GrowableArray<ParsePredicateNode*> _parse_predicates;
  int _current_index;

 public:
  ParsePredicateIterator(const Predicates& predicates);

  bool has_next() const {
    return _current_index < _parse_predicates.length();
  }

  ParsePredicateNode* next();
};

// Special predicate iterator that can be used to walk through predicate entries, regardless of whether the predicate
// belongs to the same loop or not (i.e. leftovers from already folded nodes). The iterator returns the next entry
// to a predicate.
class PredicateEntryIterator : public StackObj {
  Node* _current;

 public:
  explicit PredicateEntryIterator(Node* start) : _current(start) {};

  bool has_next() const;
  Node* next_entry();
};
#endif // SHARE_OPTO_PREDICATES_HPP
