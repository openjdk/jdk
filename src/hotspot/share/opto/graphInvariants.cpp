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

void LocalGraphInvariant::LazyReachableCFGNodes::compute() {
  precond(_reachable_nodes.size() == 0);

  // We should have at least root, so we are sure it's not filled yet.
  _reachable_nodes.push(Compile::current()->root());
  for (uint i = 0; i < _reachable_nodes.size(); ++i) {
    Node* n = _reachable_nodes.at(i);
    for (DUIterator_Fast jmax, j = n->fast_outs(jmax); j < jmax; j++) {
      Node* out = n->fast_out(j);
      if (out->is_CFG()) {
        _reachable_nodes.push(out);
      }
    }
  }

  postcond(_reachable_nodes.size() > 0);
}

bool LocalGraphInvariant::LazyReachableCFGNodes::is_node_dead(const Node* n) {
  if (_reachable_nodes.size() == 0) {
    compute();
  }
  assert(_reachable_nodes.size() > 0, "filling failed");
  return !_reachable_nodes.member(n);
}

/* A base for local invariants that mostly work using a pattern.
 */
struct PatternBasedCheck : LocalGraphInvariant {
  explicit PatternBasedCheck(const char* name, const Pattern* pattern) : _name(name), _pattern(pattern) {}

  const char* name() const override {
    return _name;
  }

  bool run_pattern(const Node* center, PathInGraph& path, stringStream& ss) const {
    return _pattern->match(center, path, ss);
  }

private:
  const char* _name;
  const Pattern* const _pattern;
};

struct CheckHelper {
  static CheckHelper for_reachable_center(const PatternBasedCheck* check, const Node* center, LocalGraphInvariant::LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) {
    return CheckHelper(check, center, &reachable_cfg_nodes, path, ss);
  }
  static CheckHelper for_any_center(const PatternBasedCheck* check, const Node* center, PathInGraph& path, stringStream& ss) {
    return CheckHelper(check, center, nullptr, path, ss);
  }
  CheckHelper& applies_if_center(bool (Node::*type_check)() const) {
    if (!(_center->*type_check)()) {
      _result = CheckHelperResult::NOT_APPLICABLE;
    }
    return *this;
  }
  template <typename F>
  CheckHelper& applies_if_center(F f) {
    if (!f(*_center)) {
      _result = CheckHelperResult::NOT_APPLICABLE;
    }
    return *this;
  }
  CheckHelper& run_pattern() {
    if (_result == CheckHelperResult::UNKNOWN) {
      if (_check.run_pattern(_center, _path, _ss)) {
        _result = CheckHelperResult::VALID;
      } else {
        if (_reachable_cfg_nodes == nullptr) {
          _result = CheckHelperResult::FAILED;
        } else if (_reachable_cfg_nodes->is_node_dead(_center)) {
          _ss.reset();
          _path.clear();
          _result = CheckHelperResult::VALID;
        } else {
          _result = CheckHelperResult::FAILED;
        }
      }
    }
    return *this;
  }
  template <typename Fun>
  CheckHelper& on_success_require(Fun fun) {
    if (_result == CheckHelperResult::VALID) {
      if (!fun()) {
        _result = CheckHelperResult::FAILED;
      }
    }
    return *this;
  }
  LocalGraphInvariant::CheckResult to_result() const {
    switch (_result) {
    case CheckHelperResult::VALID:
      return LocalGraphInvariant::CheckResult::VALID;
    case CheckHelperResult::FAILED:
      return LocalGraphInvariant::CheckResult::FAILED;
    case CheckHelperResult::NOT_APPLICABLE:
      return LocalGraphInvariant::CheckResult::NOT_APPLICABLE;
    case CheckHelperResult::UNKNOWN:
      assert(false, "Should have decided before!");
      return LocalGraphInvariant::CheckResult::FAILED;
    default:
      ShouldNotReachHere();
      return LocalGraphInvariant::CheckResult::FAILED;
    }
  }

private:
  CheckHelper(const PatternBasedCheck* check, const Node* center, LocalGraphInvariant::LazyReachableCFGNodes* reachable_cfg_nodes, PathInGraph& path, stringStream& ss)
      : _check(*check),
        _center(center),
        _reachable_cfg_nodes(reachable_cfg_nodes),
        _path(path),
        _ss(ss) {}

  const PatternBasedCheck& _check;
  const Node* _center;
  LocalGraphInvariant::LazyReachableCFGNodes* _reachable_cfg_nodes;  // Non nullptr iff the check applies only to reachable nodes.
  PathInGraph& _path;
  stringStream& _ss;

  enum class CheckHelperResult {
    VALID,
    FAILED,
    NOT_APPLICABLE,
    UNKNOWN,
  };

  CheckHelperResult _result = CheckHelperResult::UNKNOWN;
};

/* Checks that If Nodes have exactly 2 outputs: IfTrue and IfFalse
 */
struct IfProjections : PatternBasedCheck {
  IfProjections()
      : PatternBasedCheck(
            "IfProjections",
            And::make(
                new HasNOutputs(2),
                new AtSingleOutputOfType(&Node::is_IfTrue, new TruePattern()),
                new AtSingleOutputOfType(&Node::is_IfFalse, new TruePattern()))) {
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
    return CheckHelper::for_reachable_center(this, center, reachable_cfg_nodes, path, ss)
        .applies_if_center(&Node::is_If)
        .run_pattern()
        .to_result();
  }
};

/* Check that Phi has a Region as first input, and consistent arity
 */
struct PhiArity : PatternBasedCheck {
private:
  const RegionNode* _region_node = nullptr;

public:
  PhiArity()
      : PatternBasedCheck(
            "PhiArity",
            And::make(
                new HasAtLeastNInputs(2),
                new AtInput(
                    0,
                    NodeClassIsAndBind(Region, _region_node)))) {
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes&, PathInGraph& path, stringStream& ss) const override {
    return CheckHelper::for_any_center(this, center, path, ss)
        .applies_if_center(&Node::is_Phi)
        .run_pattern()
        .on_success_require([&]() -> bool {
          assert(_region_node != nullptr, "sanity");
          if (_region_node->req() != center->req()) {
            ss.print_cr("Phi nodes must have the same arity as their Region node. Phi arity: %d; Region arity: %d.", center->req(), _region_node->req());
            return false;
          }
          return true;
        })
        .to_result();
  }
};

/* Make sure each control node has the right amount of control successors: that is 1 for most cases, 2 for If nodes...
 */
struct ControlSuccessor : LocalGraphInvariant {
  const char* name() const override {
    return "ControlSuccessor";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
    if (!center->is_CFG()) {
      return CheckResult::NOT_APPLICABLE;
    }

    ResourceMark rm;
    Node_List ctrl_succ;
    for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
      Node* out = center->fast_out(i);
      if (out->is_CFG()) {
        ctrl_succ.push(out);
      }
    }

    const uint cfg_out = ctrl_succ.size();

    if (center->is_If() || center->is_Start() || center->is_Root() || center->is_Region() || center->is_NeverBranch()) {
      if (cfg_out != 2) {
        if (reachable_cfg_nodes.is_node_dead(center)) {
          // That's ok for dead nodes right now. It might be too expensive to collect for IGVN, but it will be removed in loop opts.
          return CheckResult::VALID;
        }
        ss.print_cr("%s node must have exactly two control successors. Found %d.", center->Name(), cfg_out);
        print_node_list(ctrl_succ, ss);
        return CheckResult::FAILED;
      }
    } else if (center->Opcode() == Op_SafePoint) {
      if (cfg_out < 1 || cfg_out > 2) {
        ss.print_cr("%s node must have one or two control successors. Found %d.", center->Name(), cfg_out);
        print_node_list(ctrl_succ, ss);
        return CheckResult::FAILED;
      }
      if (cfg_out == 2) {
        if (!ctrl_succ.at(0)->is_Root() && !ctrl_succ.at(1)->is_Root()) {
          ss.print_cr("One of the two control outputs of a %s node must be Root.", center->Name());
          print_node_list(ctrl_succ, ss);
          return CheckResult::FAILED;
        }
      }
    } else if (center->is_PCTable()) {
      if (cfg_out < 1) {
        ss.print_cr("%s node must have at least one control successors. Found %d.", center->Name(), cfg_out);
        return CheckResult::FAILED;
      }
    } else {
      if (cfg_out != 1) {
        ss.print_cr("Ordinary CFG nodes must have exactly one successor. Found %d.", cfg_out);
        print_node_list(ctrl_succ, ss);
        return CheckResult::FAILED;
      }
    }

    return CheckResult::VALID;
  }

private:
  static void print_node_list(const Node_List& ctrl_succ, stringStream& ss) {
    for (uint i = 0; i < ctrl_succ.size(); ++i) {
      ss.print("  ");
      ctrl_succ.at(i)->dump("\n", false, &ss);
    }
  }
};

/* Checks that Region, Start and Root nodes' first input is a self loop, except for copy regions, which then must have only one non null input.
 */
struct SelfLoopInvariant : LocalGraphInvariant {
  const char* name() const override {
    return "RegionSelfLoop";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
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
        ResourceMark rm;
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

// CountedLoopEnd -> IfTrue -> CountedLoop[center]
struct CountedLoopInvariants : PatternBasedCheck {
private:
  const BaseCountedLoopEndNode* _counted_loop_end = nullptr;

public:
  CountedLoopInvariants()
      : PatternBasedCheck(
            "CountedLoopInvariants",
            And::make(
                new HasExactlyNInputs(3),
                new AtInput(
                    LoopNode::LoopBackControl,
                    And::make(
                        new NodeClass(&Node::is_IfTrue),
                        new HasExactlyNInputs(1),
                        new AtInput(
                            0,
                            NodeClassIsAndBind(BaseCountedLoopEnd, _counted_loop_end)))))) {}
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
    return CheckHelper::for_any_center(this, center, path, ss)
        .applies_if_center(&Node::is_BaseCountedLoop)
        .run_pattern()
        .on_success_require([&]() {
          assert(_counted_loop_end != nullptr, "sanity");
          if (center->is_LongCountedLoop()) {
            if (!_counted_loop_end->is_LongCountedLoopEnd()) {
              assert(_counted_loop_end->is_CountedLoopEnd(), "Update the error message or add cases");
              ss.print_cr("A CountedLoopEnd is the backedge of a LongCountedLoop.");
              return false;
            }
          } else {
            if (!_counted_loop_end->is_CountedLoopEnd()) {
              assert(_counted_loop_end->is_LongCountedLoopEnd(), "Update the error message or add cases");
              ss.print_cr("A LongCountedLoopEnd is the backedge of a CountedLoop.");
              return false;
            }
          }
          return true;
        })
        .to_result();
  }
};

// CountedLoopEnd -> IfFalse -> SafePoint -> OuterStripMinedLoopEnd[center] -> IfTrue -> OuterStripMinedLoop -> CountedLoop
//               \-> IfTrue  ->                                                                              /
struct OuterStripMinedLoopInvariants : PatternBasedCheck {
private:
  const CountedLoopNode* _counted_loop_from_outer_strip_mined_loop;
  const CountedLoopNode* _counted_loop_from_backedge;

public:
  OuterStripMinedLoopInvariants()
      : PatternBasedCheck(
            "OuterStripMinedLoopInvariants",
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
                                    And::make(
                                        new NodeClass(&Node::is_CountedLoopEnd),
                                        new AtSingleOutputOfType(
                                            &Node::is_IfTrue,
                                            new AtSingleOutputOfType(
                                                &Node::is_CountedLoop,
                                                new TypedBind<CountedLoopNode>(_counted_loop_from_backedge))))))))),
                new AtSingleOutputOfType(
                    &Node::is_IfTrue,
                    new AtSingleOutputOfType(
                        &Node::is_OuterStripMinedLoop,
                        new AtSingleOutputOfType(
                            &Node::is_CountedLoop,
                            new TypedBind<CountedLoopNode>(_counted_loop_from_outer_strip_mined_loop)))))) {}
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
    return CheckHelper::for_any_center(this, center, path, ss)
        .applies_if_center(&Node::is_OuterStripMinedLoopEnd)
        .run_pattern()
        .on_success_require([&]() {
          assert(_counted_loop_from_backedge != nullptr, "sanity");
          assert(_counted_loop_from_outer_strip_mined_loop != nullptr, "sanity");
          bool same_counted_loop = _counted_loop_from_backedge == _counted_loop_from_outer_strip_mined_loop;
          if (!same_counted_loop) {
            ss.print_cr("Found different counted loop from backedge and from output of OuterStripMinedLoop.");
            ss.print_cr("From backedge:");
            _counted_loop_from_backedge->dump("\n", false, &ss);
            ss.print_cr("From OuterStripMinedLoop:");
            _counted_loop_from_outer_strip_mined_loop->dump("\n", false, &ss);
          }
          return same_counted_loop;
        })
        .to_result();
  }
};

struct MultiBranchNodeOut : LocalGraphInvariant {
  const char* name() const override {
    return "MultiBranchNodeOut";
  }
  CheckResult check(const Node* center, LazyReachableCFGNodes& reachable_cfg_nodes, PathInGraph& path, stringStream& ss) const override {
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

void GraphInvariantChecker::print_path(const PathInGraph& path, stringStream& ss) {
  const GrowableArray<int>& relation_to_previous_node = path.relation_to_previous_node();
  const int path_len = relation_to_previous_node.length();
  const Node_List& nodes = path.nodes();
  precond(nodes.size() == static_cast<uint>(path_len) + 1);
  if (path_len == 0) {
    ss.print_cr("At center node");
    nodes.at(0)->dump("\n", false, &ss);
    return;
  }
  ss.print("At node\n   ");
  nodes.at(0)->dump("\n", false, &ss);
  ss.print_cr("  From path:");
  ss.print("    [center]");
  nodes.at(path_len)->dump("\n", false, &ss);
  for (int i = 0; i < path_len; ++i) {
    if (relation_to_previous_node.at(path_len - i - 1) >= 0) {
      // It's an input
      int input_nb = relation_to_previous_node.at(path_len - i - 1);
      if (input_nb <= 9) {
        ss.print(" ");
      }
      ss.print("     <-(%d)-", input_nb);

    } else if (relation_to_previous_node.at(path_len - i - 1) == PathInGraph::OutputStep) {
      // It's an output
      ss.print("         -->");
    } else {
      ss.print("         ???");
    }
    nodes.at(path_len - i - 1)->dump("\n", false, &ss);
  }
}

bool GraphInvariantChecker::run() const {
  if (_checks.is_empty()) {
    return true;
  }

  ResourceMark rm;
  Unique_Node_List worklist;
  worklist.push(Compile::current()->root());
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
      PathInGraph path;
      switch (_checks.at(j)->check(center, reachable_cfg_nodes, path, ss2)) {
      case LocalGraphInvariant::CheckResult::FAILED:
        failures++;
        path.finalize(center);
        print_path(path, ss);
        ss.print_cr("# %s:", _checks.at(j)->name());
        ss.print_cr("%s", ss2.base());
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
