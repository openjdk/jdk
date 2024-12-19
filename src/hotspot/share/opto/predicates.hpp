/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

class IdealLoopTree;
class InitializedAssertionPredicate;
class ParsePredicate;
class PredicateVisitor;
class RuntimePredicate;
class TemplateAssertionPredicate;

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
 *                        We use special OpaqueTemplateAssertionPredicateNode nodes to block some optimizations and replace
 *                        the Assertion Predicates later in product builds.
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
 * - Regular Predicate Block: A block that only contains the Regular Predicates of a Predicate Block without the
 *                            Parse Predicate.
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

// Assertion Predicates are either emitted to check the initial value of a range check in the first iteration or the last
// value of a range check in the last iteration of a loop.
enum class AssertionPredicateType {
  None, // Not an Assertion Predicate
  InitValue,
  LastValue,
  // Used for the Initialized Assertion Predicate emitted during Range Check Elimination for the final IV value.
  FinalIv
};

// Interface to represent a C2 predicate. A predicate is always represented by two CFG nodes:
// - An If node (head)
// - An IfProj node representing the success projection of the If node (tail).
class Predicate : public StackObj {
 public:
  // Return the unique entry CFG node into the predicate.
  virtual Node* entry() const = 0;

  // Return the head node of the predicate which is either:
  // - A ParsePredicateNode if the predicate is a Parse Predicate
  // - An IfNode or RangeCheckNode, otherwise.
  virtual IfNode* head() const = 0;

  // Return the tail node of the predicate. Runtime Predicates can either have a true of false projection as success
  // projection while Parse Predicates and Assertion Predicates always have a true projection as success projection.
  virtual IfProjNode* tail() const = 0;
};

// Generic predicate visitor that does nothing. Subclass this visitor to add customized actions for each predicate.
// The visit methods of this visitor are called from the predicate iterator classes which walk the predicate chain.
// Use the UnifiedPredicateVisitor if the type of the predicate does not matter.
class PredicateVisitor : StackObj {
 public:
  virtual void visit(const ParsePredicate& parse_predicate) {}
  virtual void visit(const RuntimePredicate& runtime_predicate) {}
  virtual void visit(const TemplateAssertionPredicate& template_assertion_predicate) {}
  virtual void visit(const InitializedAssertionPredicate& initialized_assertion_predicate) {}

  // This method can be overridden to stop the predicate iterators from visiting more predicates further up in the
  // predicate chain.
  virtual bool should_continue() const {
    return true;
  }
};

// Interface to check whether a node is in a loop body or not.
class NodeInLoopBody : public StackObj {
 public:
  virtual bool check(Node* node) const = 0;
};

// Class to represent Assertion Predicates (i.e. either Initialized and/or Template Assertion Predicates).
class AssertionPredicates : public StackObj {
  Node* const _entry;

  static Node* find_entry(Node* start_proj);

 public:
  explicit AssertionPredicates(Node* assertion_predicate_proj) : _entry(find_entry(assertion_predicate_proj)) {}
  NONCOPYABLE(AssertionPredicates);

  // Returns the control input node into the first assertion predicate If. If there are no assertion predicates, it
  // returns the same node initially passed to the constructor.
  Node* entry() const {
    return _entry;
  }
};

// Class to represent a single Assertion Predicate. This could either be:
// - A Template Assertion Predicate.
// - An Initialized Assertion Predicate.
class AssertionPredicate : public StackObj {
  static bool has_assertion_predicate_opaque(const Node* predicate_proj);
  static bool has_halt(const Node* success_proj);
 public:
  static bool is_predicate(const Node* maybe_success_proj);
};

// Utility class representing a Regular Predicate which is either a Runtime Predicate or an Assertion Predicate.
class RegularPredicate : public StackObj {
 public:
  static bool may_be_predicate_if(const Node* node);
};

// Class to represent a Parse Predicate.
class ParsePredicate : public Predicate {
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
  Node* entry() const override {
    return _entry;
  }

  // This Parse Predicate is valid if the node passed to the constructor is a projection of a ParsePredicateNode and the
  // deopt_reason of the uncommon trap of the ParsePredicateNode matches the passed deopt_reason to the constructor.
  bool is_valid() const {
    return _parse_predicate_node != nullptr;
  }

  ParsePredicateNode* head() const override {
    assert(is_valid(), "must be valid");
    return _parse_predicate_node;
  }

  ParsePredicateSuccessProj* tail() const override {
    assert(is_valid(), "must be valid");
    return _success_proj;
  }
};

// Class to represent a Runtime Predicate which always has an associated UCT on the failing path.
class RuntimePredicate : public Predicate {
  IfProjNode* _success_proj;
  IfNode* _if_node;

 public:
  explicit RuntimePredicate(IfProjNode* success_proj)
      : _success_proj(success_proj),
        _if_node(success_proj->in(0)->as_If()) {
    assert(is_predicate(success_proj), "must be valid");
  }
  NONCOPYABLE(RuntimePredicate);

 private:
  static bool is_predicate(Node* maybe_success_proj);
  static bool has_valid_uncommon_trap(const Node* success_proj);
  static Deoptimization::DeoptReason uncommon_trap_reason(IfProjNode* if_proj);

 public:
  Node* entry() const override {
    return _if_node->in(0);
  }

  IfNode* head() const override {
    return _if_node;
  }

  IfProjNode* tail() const override {
    return _success_proj;
  }

  static bool is_predicate(const Node* node, Deoptimization::DeoptReason deopt_reason);
};

// Class to represent a Template Assertion Predicate.
class TemplateAssertionPredicate : public Predicate {
  IfTrueNode* const _success_proj;
  IfNode* const _if_node;

 public:
  explicit TemplateAssertionPredicate(IfTrueNode* success_proj)
      : _success_proj(success_proj),
        _if_node(success_proj->in(0)->as_If()) {
    assert(is_predicate(success_proj), "must be valid");
  }

  Node* entry() const override {
    return _if_node->in(0);
  }

  OpaqueTemplateAssertionPredicateNode* opaque_node() const {
    return _if_node->in(1)->as_OpaqueTemplateAssertionPredicate();
  }

  IfNode* head() const override {
    return _if_node;
  }

  IfTrueNode* tail() const override {
    return _success_proj;
  }

  bool is_last_value() const {
    return _if_node->assertion_predicate_type() == AssertionPredicateType::LastValue;
  }

  IfTrueNode* clone(Node* new_control, PhaseIdealLoop* phase) const;
  IfTrueNode* clone_and_replace_init(Node* new_control, OpaqueLoopInitNode* new_opaque_init, PhaseIdealLoop* phase) const;
  void replace_opaque_stride_input(Node* new_stride, PhaseIterGVN& igvn) const;
  IfTrueNode* initialize(PhaseIdealLoop* phase, Node* new_control) const;
  void rewire_loop_data_dependencies(IfTrueNode* target_predicate, const NodeInLoopBody& data_in_loop_body,
                                     PhaseIdealLoop* phase) const;
  static bool is_predicate(Node* node);

#ifdef ASSERT
  static void verify(IfTrueNode* template_assertion_predicate_success_proj) {
    TemplateAssertionPredicate template_assertion_predicate(template_assertion_predicate_success_proj);
    template_assertion_predicate.verify();
  }

  void verify() const;
#endif // ASSERT
};

// Class to represent an Initialized Assertion Predicate which always has a halt node on the failing path.
// This predicate should never fail at runtime by design.
class InitializedAssertionPredicate : public Predicate {
  IfTrueNode* const _success_proj;
  IfNode* const _if_node;

 public:
  explicit InitializedAssertionPredicate(IfTrueNode* success_proj)
      : _success_proj(success_proj),
        _if_node(success_proj->in(0)->as_If()) {
    assert(is_predicate(success_proj), "must be valid");
  }

  Node* entry() const override {
    return _if_node->in(0);
  }

  OpaqueInitializedAssertionPredicateNode* opaque_node() const {
    return _if_node->in(1)->as_OpaqueInitializedAssertionPredicate();
  }

  IfNode* head() const override {
    return _if_node;
  }

  IfTrueNode* tail() const override {
    return _success_proj;
  }

  bool is_last_value() const {
    return _if_node->assertion_predicate_type() == AssertionPredicateType::LastValue;
  }

  void kill(PhaseIdealLoop* phase) const;
  static bool is_predicate(Node* node);

#ifdef ASSERT
  static void verify(IfTrueNode* initialized_assertion_predicate_success_proj) {
    InitializedAssertionPredicate initialized_assertion_predicate(initialized_assertion_predicate_success_proj);
    initialized_assertion_predicate.verify();
  }

  void verify() const;
#endif // ASSERT
};

// Interface to transform OpaqueLoopInit and OpaqueLoopStride nodes of a Template Assertion Expression.
class TransformStrategyForOpaqueLoopNodes : public StackObj {
 public:
  virtual Node* transform_opaque_init(OpaqueLoopInitNode* opaque_init) const = 0;
  virtual Node* transform_opaque_stride(OpaqueLoopStrideNode* opaque_stride) const = 0;
};

// A Template Assertion Predicate represents the OpaqueTemplateAssertionPredicateNode for the initial value or the last
// value of a Template Assertion Predicate and all the nodes up to and including the OpaqueLoop* nodes.
class TemplateAssertionExpression : public StackObj {
  OpaqueTemplateAssertionPredicateNode* _opaque_node;

 public:
  explicit TemplateAssertionExpression(OpaqueTemplateAssertionPredicateNode* opaque_node) : _opaque_node(opaque_node) {}

 private:
  OpaqueTemplateAssertionPredicateNode* clone(const TransformStrategyForOpaqueLoopNodes& transform_strategy,
                                              Node* new_ctrl, PhaseIdealLoop* phase);

 public:
  OpaqueTemplateAssertionPredicateNode* clone(Node* new_control, PhaseIdealLoop* phase);
  OpaqueTemplateAssertionPredicateNode* clone_and_replace_init(Node* new_control, Node* new_init,
                                                               PhaseIdealLoop* phase);
  OpaqueTemplateAssertionPredicateNode* clone_and_replace_init_and_stride(Node* new_control, Node* new_init,
                                                                          Node* new_stride, PhaseIdealLoop* phase);
  void replace_opaque_stride_input(Node* new_stride, PhaseIterGVN& igvn);
  OpaqueInitializedAssertionPredicateNode* clone_and_fold_opaque_loop_nodes(Node* new_ctrl, PhaseIdealLoop* phase);
};

// Class to represent a node being part of a Template Assertion Expression. Note that this is not an IR node.
//
// The expression itself can belong to no, one, or two Template Assertion Predicates:
// - None: This node is already dead (i.e. we replaced the Bool condition of the Template Assertion Predicate).
// - Two: A OpaqueLoopInitNode could be part of two Template Assertion Predicates.
// - One: In all other cases.
class TemplateAssertionExpressionNode : public StackObj {
  Node* const _node;

 public:
  explicit TemplateAssertionExpressionNode(Node* node) : _node(node) {
    assert(is_in_expression(node), "must be valid");
  }
  NONCOPYABLE(TemplateAssertionExpressionNode);

 private:
  static bool is_template_assertion_predicate(Node* node);

 public:
  // Check whether the provided node is part of a Template Assertion Expression or not.
  static bool is_in_expression(Node* node);

  // Check if the opcode of node could be found in a Template Assertion Expression.
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
        assert(!next->is_CFG(), "no CFG expected in Template Assertion Expression");
        list.push_outputs_of(next);
      }
    }

    // Each node inside a Template Assertion Expression is in between a Template Assertion Predicate and its OpaqueLoop*
    // nodes (or an OpaqueLoop* node itself). The OpaqueLoop* nodes do not common up. Therefore, each Template Assertion
    // Expression node belongs to a single expression - except for OpaqueLoopInitNodes. An OpaqueLoopInitNode is shared
    // between the init and last value Template Assertion Predicate at creation. Later, when cloning the expressions,
    // they are no longer shared.
    assert(template_counter <= 2, "a node cannot be part of more than two templates");
    assert(template_counter <= 1 || _node->is_OpaqueLoopInit(), "only OpaqueLoopInit nodes can be part of two templates");
  }
};

// This class is used to create the actual If node with a success path and a fail path with a Halt node.
class AssertionPredicateIfCreator : public StackObj {
  PhaseIdealLoop* const _phase;

 public:
  explicit AssertionPredicateIfCreator(PhaseIdealLoop* const phase) : _phase(phase) {}
  NONCOPYABLE(AssertionPredicateIfCreator);

  IfTrueNode* create_for_initialized(Node* new_control, int if_opcode, Node* assertion_expression,
                                     AssertionPredicateType assertion_predicate_type);
  IfTrueNode* create_for_template(Node* new_control, int if_opcode, Node* assertion_expression,
                                  AssertionPredicateType assertion_predicate_type);
 private:
  IfTrueNode* create(Node* new_control, int if_opcode, Node* assertion_expression, const char* halt_message,
                     AssertionPredicateType assertion_predicate_type);
  IfNode* create_if_node(Node* new_control, int if_opcode, Node* assertion_expression, IdealLoopTree* loop,
                         AssertionPredicateType assertion_predicate_type);
  IfTrueNode* create_success_path(IfNode* if_node, IdealLoopTree* loop);
  void create_fail_path(IfNode* if_node, IdealLoopTree* loop, const char* halt_message);
  void create_halt_node(IfFalseNode* fail_proj, IdealLoopTree* loop, const char* halt_message);
};

// This class is used to create a Template Assertion Predicate either with a Halt Node from scratch.
class TemplateAssertionPredicateCreator : public StackObj {
  CountedLoopNode* const _loop_head;
  const int _scale;
  Node* const _offset;
  Node* const _range;
  PhaseIdealLoop* const _phase;

  OpaqueLoopInitNode* create_opaque_init(Node* new_control);
  OpaqueTemplateAssertionPredicateNode* create_for_init_value(Node* new_control, OpaqueLoopInitNode* opaque_init,
                                                              bool& does_overflow) const;
  OpaqueTemplateAssertionPredicateNode* create_for_last_value(Node* new_control, OpaqueLoopInitNode* opaque_init,
                                                              bool& does_overflow) const;
  Node* create_last_value(Node* new_control, OpaqueLoopInitNode* opaque_init) const;
  IfTrueNode* create_if_node(Node* new_control,
                             OpaqueTemplateAssertionPredicateNode* template_assertion_predicate_expression,
                             bool does_overflow, AssertionPredicateType assertion_predicate_type);

 public:
  TemplateAssertionPredicateCreator(CountedLoopNode* loop_head, int scale, Node* offset, Node* range,
                                    PhaseIdealLoop* phase)
      : _loop_head(loop_head),
        _scale(scale),
        _offset(offset),
        _range(range),
        _phase(phase) {}
  NONCOPYABLE(TemplateAssertionPredicateCreator);

  IfTrueNode* create(Node* new_control);
};

// This class creates a new Initialized Assertion Predicate either from a template or from scratch.
class InitializedAssertionPredicateCreator : public StackObj {
  PhaseIdealLoop* const _phase;

 public:
  explicit InitializedAssertionPredicateCreator(PhaseIdealLoop* phase);
  NONCOPYABLE(InitializedAssertionPredicateCreator);

  IfTrueNode* create_from_template(IfNode* template_assertion_predicate, Node* new_control, Node* new_init,
                                   Node* new_stride);
  IfTrueNode* create_from_template(IfNode* template_assertion_predicate, Node* new_control);
  IfTrueNode* create(Node* operand, Node* new_control, jint stride, int scale, Node* offset, Node* range,
                     AssertionPredicateType assertion_predicate_type);

 private:
  OpaqueInitializedAssertionPredicateNode* create_assertion_expression_from_template(IfNode* template_assertion_predicate,
                                                                                     Node* new_control, Node* new_init,
                                                                                     Node* new_stride);
  IfTrueNode* create_control_nodes(Node* new_control, int if_opcode,
                                   OpaqueInitializedAssertionPredicateNode* assertion_expression,
                                   AssertionPredicateType assertion_predicate_type);
};

// This class iterates through all predicates of a Regular Predicate Block and applies the given visitor to each.
class RegularPredicateBlockIterator : public StackObj {
  Node* const _start_node;
  const Deoptimization::DeoptReason _deopt_reason;

 public:
  RegularPredicateBlockIterator(Node* start_node, Deoptimization::DeoptReason deopt_reason)
      : _start_node(start_node),
        _deopt_reason(deopt_reason) {}
  NONCOPYABLE(RegularPredicateBlockIterator);

  // Skip all predicates by just following the inputs. We do not call any user provided visitor.
  Node* skip_all() const {
    PredicateVisitor do_nothing; // No real visits, just do nothing.
    return for_each(do_nothing);
  }

  // Walk over all predicates of this block (if any) and apply the given 'predicate_visitor' to each predicate.
  // Returns the entry to the earliest predicate.
  Node* for_each(PredicateVisitor& predicate_visitor) const {
    Node* current = _start_node;
    while (predicate_visitor.should_continue()) {
      if (TemplateAssertionPredicate::is_predicate(current)) {
        TemplateAssertionPredicate template_assertion_predicate(current->as_IfTrue());
        predicate_visitor.visit(template_assertion_predicate);
        current = template_assertion_predicate.entry();
      } else if (RuntimePredicate::is_predicate(current, _deopt_reason)) {
        RuntimePredicate runtime_predicate(current->as_IfProj());
        predicate_visitor.visit(runtime_predicate);
        current = runtime_predicate.entry();
      } else if (InitializedAssertionPredicate::is_predicate(current)) {
        InitializedAssertionPredicate initialized_assertion_predicate(current->as_IfTrue());
        predicate_visitor.visit(initialized_assertion_predicate);
        current = initialized_assertion_predicate.entry();
      } else {
        // Either a Parse Predicate or not a Regular Predicate. In both cases, the node does not belong to this block.
        break;
      }
    }
    return current;
  }
};

// This class iterates through all predicates of a Predicate Block and applies the given visitor to each.
class PredicateBlockIterator : public StackObj {
  Node* const _start_node;
  const ParsePredicate _parse_predicate; // Could be missing.
  const RegularPredicateBlockIterator _regular_predicate_block_iterator;

 public:
  PredicateBlockIterator(Node* start_node, Deoptimization::DeoptReason deopt_reason)
      : _start_node(start_node),
        _parse_predicate(start_node, deopt_reason),
        _regular_predicate_block_iterator(_parse_predicate.entry(), deopt_reason) {}

  // Walk over all predicates of this block (if any) and apply the given 'predicate_visitor' to each predicate.
  // Returns the entry to the earliest predicate.
  Node* for_each(PredicateVisitor& predicate_visitor) const {
    if (!predicate_visitor.should_continue()) {
      return _start_node;
    }
    if (_parse_predicate.is_valid()) {
      predicate_visitor.visit(_parse_predicate);
    }
    return _regular_predicate_block_iterator.for_each(predicate_visitor);
  }
};

// Class to walk over all predicates starting at a node, which usually is the loop entry node, and following the inputs.
// At each predicate, a PredicateVisitor is applied which the user can implement freely.
class PredicateIterator : public StackObj {
  Node* _start_node;

 public:
  explicit PredicateIterator(Node* start_node)
      : _start_node(start_node) {}
  NONCOPYABLE(PredicateIterator);

  // Apply the 'predicate_visitor' for each predicate found in the predicate chain started at the provided node.
  // Returns the entry to the earliest predicate.
  Node* for_each(PredicateVisitor& predicate_visitor) const {
    Node* current = _start_node;
    PredicateBlockIterator loop_limit_check_predicate_iterator(current, Deoptimization::Reason_loop_limit_check);
    current = loop_limit_check_predicate_iterator.for_each(predicate_visitor);
    if (UseLoopPredicate) {
      if (UseProfiledLoopPredicate) {
        PredicateBlockIterator profiled_loop_predicate_iterator(current, Deoptimization::Reason_profile_predicate);
        current = profiled_loop_predicate_iterator.for_each(predicate_visitor);
      }
      PredicateBlockIterator loop_predicate_iterator(current, Deoptimization::Reason_predicate);
      current = loop_predicate_iterator.for_each(predicate_visitor);
    }
    return current;
  }
};

// Unified PredicateVisitor which only provides a single visit method for a generic Predicate. This visitor can be used
// when it does not matter what kind of predicate is visited. Note that we override all normal visit methods from
// PredicateVisitor by calling the unified method. These visit methods are marked final such that they cannot be
// overridden by implementors of this class.
class UnifiedPredicateVisitor : public PredicateVisitor {
 public:
  virtual void visit(const TemplateAssertionPredicate& template_assertion_predicate) override final {
    visit_predicate(template_assertion_predicate);
  }

  virtual void visit(const ParsePredicate& parse_predicate) override final {
    visit_predicate(parse_predicate);
  }

  virtual void visit(const RuntimePredicate& runtime_predicate) override final {
    visit_predicate(runtime_predicate);
  }

  virtual void visit(const InitializedAssertionPredicate& initialized_assertion_predicate) override final {
    visit_predicate(initialized_assertion_predicate);
  }

  virtual void visit_predicate(const Predicate& predicate) = 0;
};

// A block of Regular Predicates inside a Predicate Block without its Parse Predicate.
class RegularPredicateBlock : public StackObj {
  const Deoptimization::DeoptReason _deopt_reason;
  Node* const _entry;

 public:
  RegularPredicateBlock(Node* tail, Deoptimization::DeoptReason deopt_reason)
      : _deopt_reason(deopt_reason),
        _entry(skip_all(tail)) {
    DEBUG_ONLY(verify_block(tail);)
  }
  NONCOPYABLE(RegularPredicateBlock);

 private:
  // Walk over all Regular Predicates of this block (if any) and return the first node not belonging to the block
  // anymore (i.e. entry to the first Regular Predicate in this block if any or `tail` otherwise).
  Node* skip_all(Node* tail) const {
    RegularPredicateBlockIterator iterator(tail, _deopt_reason);
    return iterator.skip_all();
  }

  DEBUG_ONLY(void verify_block(Node* tail);)

 public:
  Node* entry() const {
    return _entry;
  }
};

#ifndef PRODUCT
// Visitor class to print all the visited predicates. Used by the Predicates class which does the printing starting
// at the loop node and then following the inputs to the earliest predicate.
class PredicatePrinter : public PredicateVisitor {
  const char* _prefix; // Prefix added to each dumped string.

 public:
  explicit PredicatePrinter(const char* prefix) : _prefix(prefix) {}
  NONCOPYABLE(PredicatePrinter);

  void visit(const ParsePredicate& parse_predicate) override {
    print_predicate_node("Parse Predicate", parse_predicate);
  }

  void visit(const RuntimePredicate& runtime_predicate) override {
    print_predicate_node("Runtime Predicate", runtime_predicate);
  }

  void visit(const TemplateAssertionPredicate& template_assertion_predicate) override {
    print_predicate_node("Template Assertion Predicate", template_assertion_predicate);
  }

  void visit(const InitializedAssertionPredicate& initialized_assertion_predicate) override {
    print_predicate_node("Initialized Assertion Predicate", initialized_assertion_predicate);
  }

 private:
  void print_predicate_node(const char* predicate_name, const Predicate& predicate) const {
    tty->print_cr("%s- %s: %d %s", _prefix, predicate_name, predicate.head()->_idx, predicate.head()->Name());
  }
};
#endif // NOT PRODUCT

// This class represents a Predicate Block (i.e. either a Loop Predicate Block, a Profiled Loop Predicate Block,
// or a Loop Limit Check Predicate Block). It contains zero or more Regular Predicates followed by a Parse Predicate
// which, however, does not need to exist (we could already have decided to remove Parse Predicates for this loop).
class PredicateBlock : public StackObj {
  const ParsePredicate _parse_predicate; // Could be missing.
  const RegularPredicateBlock _regular_predicate_block;
  Node* const _entry;
#ifndef PRODUCT
  // Used for dumping.
  Node* const _tail;
  const Deoptimization::DeoptReason _deopt_reason;
#endif // NOT PRODUCT

 public:
  PredicateBlock(Node* tail, Deoptimization::DeoptReason deopt_reason)
      : _parse_predicate(tail, deopt_reason),
        _regular_predicate_block(_parse_predicate.entry(), deopt_reason),
        _entry(_regular_predicate_block.entry())
#ifndef PRODUCT
        , _tail(tail)
        , _deopt_reason(deopt_reason)
#endif // NOT PRODUCT
        {}
  NONCOPYABLE(PredicateBlock);

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
    return _parse_predicate.head();
  }

  ParsePredicateSuccessProj* parse_predicate_success_proj() const {
    return _parse_predicate.tail();
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

#ifndef PRODUCT
  void dump() const;
  void dump(const char* prefix) const;
#endif // NOT PRODUCT
};

// This class takes a loop entry node and finds all the available predicates for the loop.
class Predicates : public StackObj {
  Node* const _tail;
  const PredicateBlock _loop_limit_check_predicate_block;
  const PredicateBlock _profiled_loop_predicate_block;
  const PredicateBlock _loop_predicate_block;
  Node* const _entry;

 public:
  explicit Predicates(Node* loop_entry)
      : _tail(loop_entry),
        _loop_limit_check_predicate_block(loop_entry, Deoptimization::Reason_loop_limit_check),
        _profiled_loop_predicate_block(_loop_limit_check_predicate_block.entry(),
                                       Deoptimization::Reason_profile_predicate),
        _loop_predicate_block(_profiled_loop_predicate_block.entry(),
                              Deoptimization::Reason_predicate),
        _entry(_loop_predicate_block.entry()) {}
  NONCOPYABLE(Predicates);

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
    return _entry != _tail;
  }

#ifndef PRODUCT
  /*
   * Debug printing functions.
   */
  void dump() const;
  static void dump_at(Node* node);
  static void dump_for_loop(LoopNode* loop_node);
#endif // NOT PRODUCT
};

// This class checks whether a node is in the original loop body and not the cloned one.
class NodeInOriginalLoopBody : public NodeInLoopBody {
  const uint _first_node_index_in_cloned_loop_body;
  const Node_List& _old_new;

 public:
  NodeInOriginalLoopBody(const uint first_node_index_in_cloned_loop_body, const Node_List& old_new)
      : _first_node_index_in_cloned_loop_body(first_node_index_in_cloned_loop_body),
        _old_new(old_new) {}
  NONCOPYABLE(NodeInOriginalLoopBody);

  // Check if 'node' is not a cloned node (i.e. "< _first_node_index_in_cloned_loop_body") and if we've created a
  // clone from 'node' (i.e. _old_new entry is non-null). Then we know that 'node' belongs to the original loop body.
  bool check(Node* node) const override {
    if (node->_idx < _first_node_index_in_cloned_loop_body) {
      Node* cloned_node = _old_new[node->_idx];
      return cloned_node != nullptr && cloned_node->_idx >= _first_node_index_in_cloned_loop_body;
    } else {
      return false;
    }
  }
};

// This class checks whether a node is in the cloned loop body and not the original one from which the loop was cloned.
class NodeInClonedLoopBody : public NodeInLoopBody {
  const uint _first_node_index_in_cloned_loop_body;

 public:
  explicit NodeInClonedLoopBody(const uint first_node_index_in_cloned_loop_body)
      : _first_node_index_in_cloned_loop_body(first_node_index_in_cloned_loop_body) {}
  NONCOPYABLE(NodeInClonedLoopBody);

  // Check if 'node' is a clone. This can easily be achieved by comparing its node index to the first node index
  // inside the cloned loop body (all of them are clones).
  bool check(Node* node) const override {
    return node->_idx >= _first_node_index_in_cloned_loop_body;
  }
};

// Visitor to create Initialized Assertion Predicates at a target loop from Template Assertion Predicates from a source
// loop. This visitor can be used in combination with a PredicateIterator.
class CreateAssertionPredicatesVisitor : public PredicateVisitor {
  Node* const _init;
  Node* const _stride;
  Node* const _old_target_loop_entry;
  Node* _current_predicate_chain_head;
  PhaseIdealLoop* const _phase;
  bool _has_hoisted_check_parse_predicates;
  const NodeInLoopBody& _node_in_loop_body;
  const bool _clone_template;

  IfTrueNode* clone_template_and_replace_init_input(const TemplateAssertionPredicate& template_assertion_predicate);
  IfTrueNode* initialize_from_template(const TemplateAssertionPredicate& template_assertion_predicate,
                                       Node* new_control) const;
  void rewire_to_old_predicate_chain_head(Node* initialized_assertion_predicate_success_proj) const;

 public:
  CreateAssertionPredicatesVisitor(CountedLoopNode* target_loop_head, PhaseIdealLoop* phase,
                                   const NodeInLoopBody& node_in_loop_body, bool clone_template);
  NONCOPYABLE(CreateAssertionPredicatesVisitor);

  using PredicateVisitor::visit;

  void visit(const ParsePredicate& parse_predicate) override;
  void visit(const TemplateAssertionPredicate& template_assertion_predicate) override;
};

// This visitor collects all Template Assertion Predicates If nodes or the corresponding Opaque nodes, depending on the
// provided 'get_opaque' flag, to the provided list.
class TemplateAssertionPredicateCollector : public PredicateVisitor {
  Unique_Node_List& _list;
  const bool _get_opaque;

 public:
  TemplateAssertionPredicateCollector(Unique_Node_List& list, const bool get_opaque)
      : _list(list),
        _get_opaque(get_opaque) {}

  using PredicateVisitor::visit;

  void visit(const TemplateAssertionPredicate& template_assertion_predicate) override {
    if (_get_opaque) {
      _list.push(template_assertion_predicate.opaque_node());
    } else {
      _list.push(template_assertion_predicate.tail());
    }
  }
};

// This visitor updates the stride for an Assertion Predicate during Loop Unrolling. The inputs to the OpaqueLoopStride
// nodes Template of Template Assertion Predicates are updated and new Initialized Assertion Predicates are created
// from the updated templates. The old Initialized Assertion Predicates are killed.
class UpdateStrideForAssertionPredicates : public PredicateVisitor {
  Node* const _new_stride;
  PhaseIdealLoop* const _phase;

  void replace_opaque_stride_input(const TemplateAssertionPredicate& template_assertion_predicate) const;
  IfTrueNode* initialize_from_updated_template(const TemplateAssertionPredicate& template_assertion_predicate) const;
  void connect_initialized_assertion_predicate(Node* new_control_out, IfTrueNode* initialized_success_proj) const;

 public:
  UpdateStrideForAssertionPredicates(Node* const new_stride, PhaseIdealLoop* phase)
      : _new_stride(new_stride),
        _phase(phase) {}
  NONCOPYABLE(UpdateStrideForAssertionPredicates);

  using PredicateVisitor::visit;

  void visit(const TemplateAssertionPredicate& template_assertion_predicate) override;

  // Kill the old Initialized Assertion Predicates with old strides before unrolling. The new Initialized Assertion
  // Predicates are inserted after the Template Assertion Predicate which ensures that we are not accidentally visiting
  // and killing a newly created Initialized Assertion Predicate here.
  void visit(const InitializedAssertionPredicate& initialized_assertion_predicate) override {
    if (initialized_assertion_predicate.is_last_value()) {
      // Only Last Value Initialized Assertion Predicates need to be killed and updated.
      initialized_assertion_predicate.kill(_phase);
    }
  }
};
#endif // SHARE_OPTO_PREDICATES_HPP
