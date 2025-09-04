/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/graphInvariants.hpp"
#include "opto/rootnode.hpp"

#ifndef PRODUCT
constexpr int LocalGraphInvariant::OutputStep;

void LocalGraphInvariant::LazyReachableCFGNodes::fill() {
  precond(_live_nodes.size() == 0);

  // We should have at least root, so we are sure it's not filled yet.
  _live_nodes.push(Compile::current()->root());
  for (uint i = 0; i < _live_nodes.size(); ++i) {
    Node* n = _live_nodes.at(i);
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* out = n->fast_out(j);
      if (out->is_CFG()) {
        _live_nodes.push(out);
      }
    }
  }

  postcond(_live_nodes.size() > 0);
}

bool LocalGraphInvariant::LazyReachableCFGNodes::is_node_dead(const Node* n) {
  if (_live_nodes.size() == 0) {
    fill();
  }
  assert(_live_nodes.size() > 0, "filling failed");
  return !_live_nodes.member(n);
}

/* A base class for checks expressed as data. Patterns are supposed to be local, centered around one node
 * and compositional to express complex structures from simple properties.
 * For instance, we have a pattern for saying "the first input of the center match P" where P is another
 * Pattern. We end up with trees of patterns matching the graph.
 */
struct Pattern : ResourceObj {
  virtual bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream&) const = 0;
};

/* This pattern just accepts any node. This is convenient mostly as leaves in a pattern tree.
 * For instance `AtSingleOutputOfType(..., new TruePattern())` will make sure there is
 * indeed a single output of the given type, but won't enforce anything on the said output.
 */
struct TruePattern : Pattern {
  bool check(const Node*, Node_List&, GrowableArray<int>&, stringStream&) const override {
    return true;
  }
};

/* This is semantically equivalent to `TruePattern` but will set the given reference to the node
 * the pattern is matched against. This is useful to perform additional checks that would
 * otherwise be hard or impossible to express as local patterns.
 *
 * For instance, one could write
 * Node* first, second;
 * And::make(
 *   new AtInput(0, new Bind(first)),
 *   new AtInput(1, new Bind(second))
 * );
 * [...] // run the pattern
 * if (first == second) { // checking whether they are the same node
 *
 * Bindings are only honored if the overall pattern succeeds. Otherwise, don't assume anything reasonable
 * has been set. Anyway, you don't need it: you already know it doesn't have the right shape.
 */
struct Bind : Pattern {
  explicit Bind(const Node*& binding) : _binding(binding) {}
  bool check(const Node* center, Node_List&, GrowableArray<int>&, stringStream&) const override {
    _binding = center;
    return true;
  }

private:
  const Node*& _binding;
};

/* Matches multiple patterns at the same node.
 *
 * Evaluation order is guaranteed to be left-to-right. In particular, check a node has enough
 * inputs before checking a property of a given input. This allows better reporting. E.g. if
 * you know a node has 3 inputs and want patterns to be applied to each input, it would look like
 * And::make(
 *    new HasExactlyNInputs(3),
 *    new AtInput(0, P0),
 *    new AtInput(1, P1),
 *    new AtInput(2, P2),
 * )
 * If we relied on `AtInput` to report too few inputs, it would give confusing error messages as
 * the first `AtInput` can only know it expects at least one input, and seeing the message
 * "Found 0 inputs, expected at least 1" is not very helpful, potentially confusing as it doesn't
 * state what is actually expected: 3 inputs.
 * It also is not able to express that a node has exactly a given number of inputs, and it is a
 * significant difference whether we expect AT LEAST 3 inputs, or EXACTLY 3 inputs. Let's make
 * things precise.
 */
struct And : Pattern {
private:
  GrowableArray<Pattern*> _checks;
  template <typename... PP>
  static void make_helper(And* a, Pattern* pattern, PP... others) {
    a->_checks.push(pattern);
    make_helper(a, others...);
  }
  static void make_helper(And*) {}

public:
  template <typename... PP>
  static And* make(PP... patterns) {
    And* andd = new And();
    make_helper(andd, patterns...);
    return andd;
  }

  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    for (int i = 0; i < _checks.length(); ++i) {
      if (!_checks.at(i)->check(center, steps, path, ss)) {
        return false;
      }
    }
    return true;
  }
};

void make_pretty_list_of_inputs(const Node* center, stringStream& ss) {
  for (uint i = 0; i < center->req(); ++i) {
    Node* in = center->in(i);
    ss.print("  %d: ", i);
    if (in == nullptr) {
      ss.print_cr("nullptr");
    } else {
      in->dump("\n", false, &ss);
    }
  }
}

struct HasExactlyNInputs : Pattern {
  explicit HasExactlyNInputs(uint expect_req) : _expect_req(expect_req) {}
  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (center->req() != _expect_req) {
      ss.print_cr("Unexpected number of input. Expected: %d. Found: %d", _expect_req, center->req());
      make_pretty_list_of_inputs(center, ss);
      return false;
    }
    return true;
  }
  const uint _expect_req;
};

struct HasAtLeastNInputs : Pattern {
  explicit HasAtLeastNInputs(uint expect_req) : _expect_req(expect_req) {}
  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (center->req() < _expect_req) {
      ss.print_cr("Too small number of input. Expected: %d. Found: %d", _expect_req, center->req());
      make_pretty_list_of_inputs(center, ss);
      return false;
    }
    return true;
  }
  const uint _expect_req;
};

/* Check that a given pattern applies at the given input of the center.
 *
 * As explained above, it doesn't check (nicely) that inputs are in sufficient numbers.
 * Use HasExactlyNInputs or HasAtLeastNInputs for that.
 */
struct AtInput : Pattern {
  AtInput(uint which_input, const Pattern* pattern) : _which_input(which_input), _pattern(pattern) {}
  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    assert(_which_input < center->req(), "Input number is out of range");
    if (center->in(_which_input) == nullptr) {
      ss.print_cr("Input at index %d is nullptr.", _which_input);
      return false;
    }
    bool result = _pattern->check(center->in(_which_input), steps, path, ss);
    if (!result) {
      steps.push(center->in(_which_input));
      path.push(static_cast<int>(_which_input));
    }
    return result;
  }
  const uint _which_input;
  const Pattern* const _pattern;
};

/* Check a node has the right type (as which C++ class, not as abstract value). Typically used with
 * is_XXXNode methods.
 */
struct NodeClass : Pattern {
  explicit NodeClass(bool (Node::*type_check)() const) : _type_check(type_check) {}
  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!(center->*_type_check)()) {
      ss.print_cr("Unexpected type: %s.", center->Name());
      return false;
    }
    return true;
  }
  bool (Node::*_type_check)() const;
};

struct HasNOutputs : Pattern {
  explicit HasNOutputs(uint expect_outcnt) : _expect_outcnt(expect_outcnt) {}
  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (center->outcnt() != _expect_outcnt) {
      ss.print_cr("Unexpected number of outputs. Expected: %d, found: %d.", _expect_outcnt, center->outcnt());
      for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
        Node* out = center->fast_out(i);
        ss.print("  ");
        out->dump("\n", false, &ss);
      }
      return false;
    }
    return true;
  }
  const uint _expect_outcnt;
};

/* Given a is_XXXNode method pointer and a pattern P, this pattern checks that
 * - only one output has the given type XXX
 * - this one output matches P.
 *
 * Since outputs are not numbered, this is a convenient way to walk on the graph in the Def-Use direction.
 */
struct AtSingleOutputOfType : Pattern {
  AtSingleOutputOfType(bool (Node::*type_check)() const, const Pattern* pattern) : _type_check(type_check), _pattern(pattern) {
  }

  bool check(const Node* center, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    Node_List outputs_of_correct_type;
    for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
      Node* out = center->fast_out(i);
      if ((out->*_type_check)()) {
        outputs_of_correct_type.push(out);
      }
    }
    if (outputs_of_correct_type.size() != 1) {
      ss.print_cr("Non-unique output of expected type. Found: %d.", outputs_of_correct_type.size());
      for (uint i = 0; i < outputs_of_correct_type.size(); ++i) {
        outputs_of_correct_type.at(i)->dump("\n", false, &ss);
      }
      return false;
    }
    bool result = _pattern->check(outputs_of_correct_type.at(0), steps, path, ss);
    if (!result) {
      steps.push(outputs_of_correct_type.at(0));
      path.push(LocalGraphInvariant::OutputStep);
    }
    return result;
  }
  bool (Node::*_type_check)() const;
  const Pattern* const _pattern;
};

/* A LocalGraphInvariant that mostly use a Pattern for checking.
 *
 * Can still override `check` to do more.
 */
struct PatternBasedCheck : LocalGraphInvariant {
  const Pattern* const _pattern;
  explicit PatternBasedCheck(const Pattern* pattern) : _pattern(pattern) {}
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    return _pattern->check(center, steps, path, ss) ? CheckResult::VALID : CheckResult::FAILED;
  }
};

/* Checks that If Nodes have exactly 2 outputs: IfTrue and IfFalse
 */
struct IfProjections : PatternBasedCheck {
  IfProjections()
      : PatternBasedCheck(
            And::make(
                new HasNOutputs(2),
                new AtSingleOutputOfType(&Node::is_IfTrue, new TruePattern()),
                new AtSingleOutputOfType(&Node::is_IfFalse, new TruePattern()))) {
  }
  const char* name() const override {
    return "IfProjections";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_If()) {
      return CheckResult::NOT_APPLICABLE;
    }
    CheckResult r = PatternBasedCheck::check(center, reachable_cfg_nodes, steps, path, ss);
    if (r == CheckResult::FAILED) {
      if (reachable_cfg_nodes.is_node_dead(center)) {
        // That's ok for dead nodes right now. It might be too expensive to collect for IGVN, but it will be removed in loop opts.
        ss.reset();
        return CheckResult::VALID;
      }
    }
    return r;
  }
};

/* Check that Phi has a Region as first input, and consistent arity
 */
struct PhiArity : PatternBasedCheck {
  const Node* region_node = nullptr;
  PhiArity()
      : PatternBasedCheck(
            And::make(
                new HasAtLeastNInputs(1),
                new AtInput(
                    0,
                    And::make(
                        new NodeClass(&Node::is_Region),
                        new Bind(region_node))))) {
  }
  const char* name() const override {
    return "PhiArity";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_Phi()) {
      return CheckResult::NOT_APPLICABLE;
    }
    CheckResult result = PatternBasedCheck::check(center, reachable_cfg_nodes, steps, path, ss);
    if (result != CheckResult::VALID) {
      return result;
    }
    assert(region_node != nullptr, "sanity");
    if (region_node->req() != center->req()) {
      ss.print_cr("Phi nodes must have the same arity as their Region node. Phi arity: %d; Region arity: %d.", center->req(), region_node->req());
      return CheckResult::FAILED;
    }
    return CheckResult::VALID;
  }
};

/* Make sure each control node has the right amount of control successors: that is 1 for most cases, 2 for If nodes...
 */
struct ControlSuccessor : LocalGraphInvariant {
  const char* name() const override {
    return "ControlSuccessor";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_CFG()) {
      return CheckResult::NOT_APPLICABLE;
    }

    Node_List ctrl_succ;
    for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
      Node* out = center->fast_out(i);
      if (out->is_CFG()) {
        ctrl_succ.push(out);
      }
    }

    uint cfg_out = ctrl_succ.size();

    if (center->is_If() || center->is_Start() || center->is_Root() || center->is_Region() || center->is_NeverBranch()) {
      if (cfg_out != 2) {
        if (reachable_cfg_nodes.is_node_dead(center)) {
          // That's ok for dead nodes right now. It might be too expensive to collect for IGVN, but it will be removed in loop opts.
          return CheckResult::VALID;
        }
        ss.print_cr("%s node must have exactly two control successors. Found %d.", center->Name(), cfg_out);
        for (uint i = 0; i < ctrl_succ.size(); ++i) {
          ss.print("  ");
          ctrl_succ.at(i)->dump("\n", false, &ss);
        }
        return CheckResult::FAILED;
      }
    } else if (center->Opcode() == Op_SafePoint) {
      if (cfg_out < 1 || cfg_out > 2) {
        ss.print_cr("%s node must have one or two control successors. Found %d.", center->Name(), cfg_out);
        for (uint i = 0; i < ctrl_succ.size(); ++i) {
          ss.print("  ");
          ctrl_succ.at(i)->dump("\n", false, &ss);
        }
        return CheckResult::FAILED;
      }
      if (cfg_out == 2) {
        if (!ctrl_succ.at(0)->is_Root() && !ctrl_succ.at(1)->is_Root()) {
          ss.print_cr("One of the two control outputs of a %s node must be Root.", center->Name());
          for (uint i = 0; i < ctrl_succ.size(); ++i) {
            ss.print("  ");
            ctrl_succ.at(i)->dump("\n", false, &ss);
          }
          return CheckResult::FAILED;
        }
      }
    } else if (center->is_Catch() || center->is_Jump()) {
      if (cfg_out < 1) {
        ss.print_cr("%s node must have at least one control successors. Found %d.", center->Name(), cfg_out);
        return CheckResult::FAILED;
      }
    } else {
      if (cfg_out != 1) {
        ss.print_cr("Ordinary CFG nodes must have exactly one successor. Found %d.", cfg_out);
        for (uint i = 0; i < ctrl_succ.size(); ++i) {
          ss.print("  ");
          ctrl_succ.at(i)->dump("\n", false, &ss);
        }
        return CheckResult::FAILED;
      }
    }

    return CheckResult::VALID;
  }
};

/* Checks that Region Start and Root nodes' first input is a self loop, except for copy regions, which then must have only one non null input.
 */
struct SelfLoopInvariant : LocalGraphInvariant {
  const char* name() const override {
    return "RegionSelfLoop";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_Region() && !center->is_Start() && !center->is_Root()) {
      return CheckResult::NOT_APPLICABLE;
    }

    if (center->req() == 0) {
      ss.print_cr("%s nodes must have at least one input.", center->Name());
      return CheckResult::FAILED;
    }

    Node* self = center->in(LoopNode::Self);

    if (self != center || (center->is_Region() && self == nullptr)) {
      ss.print_cr("%s nodes' 0-th input must be itself or nullptr (for a copy Region).", center->Name());
      return CheckResult::FAILED;
    }

    if (self == nullptr) {
      // Must be a copy Region
      uint non_null_inputs_count = 0;
      for (uint i = 0; i < center->req(); i++) {
        if (center->in(i) != nullptr) {
          non_null_inputs_count++;
        }
      }
      if (non_null_inputs_count != 1) {
        // Should be a rare case, hence the second (but more expensive) traversal.
        Node_List non_null_inputs;
        for (uint i = 0; i < center->req(); i++) {
          if (center->in(i) != nullptr) {
            non_null_inputs.push(center->in(i));
          }
        }
        if (non_null_inputs.size() != 1) {
          ss.print_cr("%s copy nodes must have exactly one non-null input. Found: %d.", center->Name(), non_null_inputs.size());
          for (uint i = 0; i < non_null_inputs.size(); ++i) {
            non_null_inputs.at(i)->dump("\n", false, &ss);
          }
          return CheckResult::FAILED;
        }
      }
    }

    return CheckResult::VALID;
  }
};

// CountedLoopEnd -> IfTrue -> CountedLoop
struct CountedLoopInvariants : PatternBasedCheck {
  const Node* counted_loop_end = nullptr;
  CountedLoopInvariants()
      : PatternBasedCheck(
            And::make(
                new HasExactlyNInputs(3),
                new AtInput(
                    LoopNode::LoopBackControl,
                    And::make(
                        new NodeClass(&Node::is_IfTrue),
                        new HasExactlyNInputs(1),
                        new AtInput(
                            0,
                            And::make(
                                new NodeClass(&Node::is_BaseCountedLoopEnd),
                                new Bind(counted_loop_end))))))) {}
  const char* name() const override {
    return "CountedLoopInvariants";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_CountedLoop() && !center->is_LongCountedLoop()) {
      return CheckResult::NOT_APPLICABLE;
    }

    CheckResult result = PatternBasedCheck::check(center, reachable_cfg_nodes, steps, path, ss);
    if (result != CheckResult::VALID) {
      return result;
    }
    assert(counted_loop_end != nullptr, "sanity");
    if (center->is_LongCountedLoop()) {
      if (!counted_loop_end->is_LongCountedLoopEnd()) {
        assert(counted_loop_end->is_CountedLoopEnd(), "Update the error message or add cases");
        ss.print_cr("A CountedLoopEnd is the backedge of a LongCountedLoop.");
        return CheckResult::FAILED;
      }
    } else {
      if (!counted_loop_end->is_CountedLoopEnd()) {
        assert(counted_loop_end->is_LongCountedLoopEnd(), "Update the error message or add cases");
        ss.print_cr("A LongCountedLoopEnd is the backedge of a CountedLoop.");
        return CheckResult::FAILED;
      }
    }
    return CheckResult::VALID;
  }
};

// CountedLoopEnd -> IfFalse -> SafePoint -> OuterStripMinedLoopEnd[center] -> IfTrue -> OuterStripMinedLoop -> CountedLoop
struct OuterStripMinedLoopInvariants : PatternBasedCheck {
  OuterStripMinedLoopInvariants()
      : PatternBasedCheck(
            And::make(
                new HasExactlyNInputs(2),
                new AtInput(
                    0,
                    And::make(
                        new NodeClass(&Node::is_SafePoint),
                        new HasAtLeastNInputs(1),
                        new AtInput(
                            0,
                            And::make(
                                new NodeClass(&Node::is_IfFalse),
                                new HasAtLeastNInputs(1),
                                new AtInput(
                                    0,
                                    new NodeClass(&Node::is_CountedLoopEnd)))))),
                new AtSingleOutputOfType(
                    &Node::is_IfTrue,
                    new AtSingleOutputOfType(
                        &Node::is_OuterStripMinedLoop,
                        new AtSingleOutputOfType(
                            &Node::is_CountedLoop,
                            new TruePattern()))))) {}
  const char* name() const override {
    return "OuterStripMinedLoopInvariants";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_OuterStripMinedLoopEnd()) {
      return CheckResult::NOT_APPLICABLE;
    }

    return PatternBasedCheck::check(center, reachable_cfg_nodes, steps, path, ss);
  }
};

struct MultiBranchNodeOut : LocalGraphInvariant {
  const char* name() const override {
    return "MultiBranchNodeOut";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, Node_List& steps, GrowableArray<int>& path, stringStream& ss) const override {
    if (!center->is_MultiBranch()) {
      return CheckResult::NOT_APPLICABLE;
    }

    MultiBranchNode* mb = center->as_MultiBranch();
    if (mb->required_outcnt() < static_cast<int>(mb->outcnt())) {
      ss.print_cr("The required_outcnt of a MultiBranch node must be smaller than or equal to its outcnt. But required_outcnt=%d vs. outcnt=%d", mb->required_outcnt(), mb->outcnt());
      return CheckResult::FAILED;
    }

    return CheckResult::VALID;
  }
};

GraphInvariantChecker* GraphInvariantChecker::make_default() {
  auto* checker = new GraphInvariantChecker();
#define ADD_CHECKER(T) checker->_checks.push(new T())
  ADD_CHECKER(IfProjections);
  ADD_CHECKER(PhiArity);
  ADD_CHECKER(ControlSuccessor);
  ADD_CHECKER(SelfLoopInvariant);
  ADD_CHECKER(CountedLoopInvariants);
  ADD_CHECKER(OuterStripMinedLoopInvariants);
  ADD_CHECKER(MultiBranchNodeOut);
#undef ADD_CHECKER
  return checker;
}

void GraphInvariantChecker::print_path(const Node_List& steps, const GrowableArray<int>& path, stringStream& ss) {
  const int path_len = path.length();
  precond(steps.size() == static_cast<uint>(path_len) + 1);
  if (path.is_empty()) {
    ss.print_cr("At center node");
    steps.at(0)->dump("\n", false, &ss);
    return;
  }
  ss.print("At node\n   ");
  steps.at(0)->dump("\n", false, &ss);
  ss.print_cr("  From path:");
  ss.print("    [center]");
  steps.at(path_len)->dump("\n", false, &ss);
  for (int i = 0; i < path_len; ++i) {
    if (path.at(path_len - i - 1) >= 0) {
      // It's an input
      int input_nb = path.at(path_len - i - 1);
      if (input_nb <= 9) {
        ss.print(" ");
      }
      ss.print("     <-(%d)-", input_nb);

    } else if (path.at(path_len - i - 1) == LocalGraphInvariant::OutputStep) {
      // It's an output
      ss.print("         -->");
    } else {
      ss.print("         ???");
    }
    steps.at(path_len - i - 1)->dump("\n", false, &ss);
  }
}

bool GraphInvariantChecker::run() const {
  if (_checks.is_empty()) {
    return true;
  }

  ResourceMark rm;
  Unique_Node_List worklist;
  worklist.push(Compile::current()->root());
  Node_List steps;
  GrowableArray<int> path;
  stringStream ss;
  stringStream ss2;
  // Sometimes, we get weird structures in dead code that will be cleaned up later. It typically happens
  // when data dies, but control is not cleaned up right away, possibly kept alive by an unreachable loop.
  // Since we don't want to eagerly traverse the whole graph to remove dead code in IGVN, we can accept
  // weird structures in dead code.
  // For CFG-related errors, we will compute the set of reachable CFG nodes and decide whether to keep
  // the issue if the problematic node is reachable. This set of reachable nodes is thus computed lazily
  // (and it seems not to happen often in practice), and shared across checks.
  LocalGraphInvariant::LazyReachableCFGNodes reachable_cfg_nodes;
  bool success = true;

  for (uint i = 0; i < worklist.size(); ++i) {
    Node* center = worklist.at(i);
    for (uint j = 0; j < center->req(); ++j) {
      Node* in = center->in(j);
      if (in != nullptr) {
        worklist.push(in);
      }
    }
    uint failures = 0;
    for (int j = 0; j < _checks.length(); ++j) {
      switch (_checks.at(j)->check(center, reachable_cfg_nodes, steps, path, ss2)) {
      case LocalGraphInvariant::CheckResult::FAILED:
        failures++;
        steps.push(center);
        print_path(steps, path, ss);
        ss.print_cr("# %s:", _checks.at(j)->name());
        ss.print_cr("%s", ss2.base());
        path.clear();
        steps.clear();
        ss2.reset();
        break;
      case LocalGraphInvariant::CheckResult::NOT_APPLICABLE:
      case LocalGraphInvariant::CheckResult::VALID:
        break;
      }
    }
    if (failures > 0) {
      success = false;
      stringStream ss3;
      ss3.print("%d failure%s for node\n", failures, failures == 1 ? "" : "s");
      center->dump("\n", false, &ss3);
      ss3.print_cr("%s", ss.base());
      tty->print("%s", ss3.base());
      ss.reset();
    }
  }

  return success;
}
#endif
