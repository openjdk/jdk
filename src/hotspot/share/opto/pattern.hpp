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

#ifndef SHARE_OPTO_PATTERN_HPP
#define SHARE_OPTO_PATTERN_HPP

#include "metaprogramming/enableIf.hpp"
#include "memory/allocation.hpp"
#include "opto/node.hpp"

/* A path in the graph from a center, for pretty reporting.
 * Given a list of nodes
 * center = N0 --[r1]--> ... --[rk]-> Nk
 * where the ri are the relation between consecutive nodes: either p-th input, or an output,
 * then:
 * - steps must have length k + 1, and contain Nk ... N0
 * - path must have length k, and contain rk ... r1 where ri is:
 *   - a non-negative integer p for each step such that N{i-1} has Ni as p-th input (we need to follow an input edge)
 *   - the OutputStep value in case N{i-1} has Ni as an output (we need to follow an output edge)
 * The lists are reversed to allow to easily fill them lazily on failure: as we backtrack in the pattern
 * structure, we add the path bottom-up, finishing by the center.
 */
struct PathInGraph {
  /* When an invariant applied at a given node (the center) goes wrong at another
   * node, it is useful to show the path we took between them. OutputStep is used
   * to signify that a node is the output of the previous one in the path.
   * See LocalGraphInvariant::check for more details on paths.
   */
  static constexpr int OutputStep = -1;

  void finalize(Node* center);
  void add_input_step(uint input_index, Node* input);
  void add_output_step(Node* output);

  const Node_List& nodes() const { return _nodes; }
  const GrowableArray<int>& relation_to_previous_node() const { return _relation_to_previous_node; }

private:
  Node_List _nodes;
  GrowableArray<int> _relation_to_previous_node;
};

/* A base class for checks expressed as data. Patterns are supposed to be local, centered around one node
 * and compositional to express complex structures from simple properties.
 * For instance, we have a pattern for saying "match P on the first input of the center" where P is another
 * Pattern. We end up with trees of patterns matching against the graph.
 */
struct Pattern : ResourceObj {
  typedef bool (Node::*TypeCheckMethod)() const;
  /* Check whether the graph and the pattern matches. Returns false in case
   * of failure.
   * center: where to around which node to check whether the pattern matches
   * path: in case of failure, path to the place where the failure happened.
   *   Must be filled from the offending node to the original center, which allows
   *   to compute the path lazily.
   * ss: in case of failure, to fill with error description.
   *
   * In case of success, path and ss must not be changed.
   */
  virtual bool match(const Node* center, PathInGraph& path, stringStream& ss) const = 0;
};

/* This pattern just accepts any node. This is convenient mostly as leaf in a pattern tree.
 * For instance `AtSingleOutputOfType(..., new TruePattern())` will make sure there is
 * indeed a single output of the given type, but won't enforce anything on the said output.
 */
struct TruePattern : Pattern {
  bool match(const Node*, PathInGraph&, stringStream&) const override {
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
  bool match(const Node* center, PathInGraph&, stringStream&) const override;

private:
  const Node*& _binding;
};

/* A more type-safe version of `Bind` mostly to use with NodeClassIsAndBind macro defined later
 */
template <typename N, ENABLE_IF(std::is_base_of<Node, N>::value)>
struct TypedBind : Pattern {
  explicit TypedBind(const N*& binding) : _binding(binding) {}
  bool match(const Node* center, PathInGraph&, stringStream&) const override {
    _binding = static_cast<const N*>(center);
    return true;
  }

private:
  const N*& _binding;
};

/* Check a node has the right type (as which C++ class, not as abstract value). Typically used with
 * is_XXXNode methods.
 */
struct NodeClass : Pattern {
  explicit NodeClass(const TypeCheckMethod type_check) : _type_check(type_check) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const TypeCheckMethod _type_check;
};

/* To check the kind of a node and bind it to a variable of the right type.
 * For instance:
 *   const RegionNode* r;
 *   NodeClassIsAndBind(Region, r)
 */
#define NodeClassIsAndBind(node_type, binding) \
  And::make(                                   \
      new NodeClass(&Node::is_##node_type),    \
      new TypedBind<node_type##Node>(binding))

/* Matches multiple patterns at the same node.
 *
 * Evaluation order is guaranteed to be left-to-right. That is needed, for instance, to check
 * that a node has enough inputs before using `AtInput`, since `AtInput` won't fail gracefully
 * if the number of input is too low. E.g. if you know a node has 3 inputs and want patterns
 * to be applied to each input, it would look like
 * And::make(
 *    new HasExactlyNInputs(3),
 *    new AtInput(0, P0),
 *    new AtInput(1, P1),
 *    new AtInput(2, P2),
 * )
 * If we relied on `AtInput` to report too few inputs, it would give confusing error messages as
 * the first `AtInput` can only know it expects at least one input, and seeing the message
 * "Found 0 inputs, expected at least 1" is not very helpful, potentially confusing since it doesn't
 * state what is actually expected: 3 inputs.
 * It also is not able to express that a node has exactly a given number of inputs, and it is a
 * significant difference whether we expect AT LEAST 3 inputs, or EXACTLY 3 inputs. Let's make
 * things precise.
 * Overall, to get better reporting, `AtInput` is not expected to check the input count, and the
 * user is responsible for it, making the guarantee on the evaluation order of `And` necessary.
 *
 * The evaluation order can also allow you to check easier properties before harder ones: it's
 * nicer if you get a simpler error message, with shorter paths. It's also easier to read as C++
 * expressions when indentation is not going back and forth.
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

  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;
};

struct HasExactlyNInputs : Pattern {
  explicit HasExactlyNInputs(uint expect_req) : _expect_req(expect_req) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const uint _expect_req;
};

struct HasAtLeastNInputs : Pattern {
  explicit HasAtLeastNInputs(uint expect_req) : _expect_req(expect_req) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const uint _expect_req;
};

/* Check that a given pattern applies at the given input of the center.
 *
 * As explained above, it doesn't check (nicely) that inputs are in sufficient numbers.
 * Use HasExactlyNInputs or HasAtLeastNInputs for that.
 */
struct AtInput : Pattern {
  AtInput(uint which_input, const Pattern* pattern) : _which_input(which_input), _pattern(pattern) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const uint _which_input;
  const Pattern* const _pattern;
};

struct HasNOutputs : Pattern {
  explicit HasNOutputs(uint expect_outcnt) : _expect_outcnt(expect_outcnt) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const uint _expect_outcnt;
};

/* Given a is_XXXNode method pointer and a pattern P, this pattern checks that
 * - only one output has the given type XXX
 * - this one output matches P.
 *
 * Since outputs are not numbered, this is a convenient way to walk on the graph in the Def-Use direction.
 */
struct AtSingleOutputOfType : Pattern {
  AtSingleOutputOfType(const TypeCheckMethod type_check, const Pattern* pattern) : _type_check(type_check), _pattern(pattern) {}
  bool match(const Node* center, PathInGraph& path, stringStream& ss) const override;

private:
  const TypeCheckMethod _type_check;
  const Pattern* const _pattern;
};

#endif // SHARE_OPTO_PATTERN_HPP
