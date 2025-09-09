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

#include "pattern.hpp"

constexpr int PathInGraph::OutputStep;

void PathInGraph::finalize(Node* center) {
  _nodes.push(center);
}

void PathInGraph::add_input_step(uint input_index, Node* input) {
  _nodes.push(input);
  _relation_to_previous_node.push(static_cast<int>(input_index));
}

void PathInGraph::add_output_step(Node* output) {
  _nodes.push(output);
  _relation_to_previous_node.push(static_cast<int>(OutputStep));
}

bool Bind::match(const Node* center, PathInGraph&, stringStream&) const {
  _binding = center;
  return true;
}

bool And::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  for (int i = 0; i < _checks.length(); ++i) {
    if (!_checks.at(i)->match(center, path, ss)) {
      // We stay on the same center, so no need to update path.
      return false;
    }
  }
  return true;
}

#ifndef PRODUCT
void print_list_of_inputs(const Node* center, stringStream& ss) {
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
#endif

bool HasExactlyNInputs::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  if (center->req() != _expect_req) {
    ss.print_cr("Unexpected number of inputs. Expected exactly: %d. Found: %d", _expect_req, center->req());
#ifndef PRODUCT
    print_list_of_inputs(center, ss);
#endif
    return false;
  }
  return true;
}

bool HasAtLeastNInputs::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  if (center->req() < _expect_req) {
    ss.print_cr("Too few inputs. Expected at least: %d. Found: %d", _expect_req, center->req());
#ifndef PRODUCT
    print_list_of_inputs(center, ss);
#endif
    return false;
  }
  return true;
}

bool AtInput::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  assert(_which_input < center->req(), "Input number is out of range");
  Node* input = center->in(_which_input);
  if (input == nullptr) {
    ss.print_cr("Input at index %d is nullptr.", _which_input);
    return false;
  }
  bool result = _pattern->match(input, path, ss);
  if (!result) {
    path.add_input_step(_which_input, input);
  }
  return result;
}

bool NodeClass::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  if (!(center->*_type_check)()) {
#ifdef PRODUCT
    ss.print_cr("Unexpected type.");
#else
    ss.print_cr("Unexpected type: %s.", center->Name());
#endif
    return false;
  }
  return true;
}

bool HasNOutputs::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  if (center->outcnt() != _expect_outcnt) {
    ss.print_cr("Unexpected number of outputs. Expected: %d, found: %d.", _expect_outcnt, center->outcnt());
#ifndef PRODUCT
    for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
      Node* out = center->fast_out(i);
      ss.print("  ");
      out->dump("\n", false, &ss);
    }
#endif
    return false;
  }
  return true;
}

bool AtSingleOutputOfType::match(const Node* center, PathInGraph& path, stringStream& ss) const {
  Node* single_output_of_right_type = nullptr;
  bool too_many_outputs_of_right_type = false;

  for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
    Node* out = center->fast_out(i);
    if ((out->*_type_check)()) {
      if (single_output_of_right_type == nullptr) {
        single_output_of_right_type = out;
      } else {
        too_many_outputs_of_right_type = true;
      }
    }
  }

  if (single_output_of_right_type == nullptr) {
    ss.print_cr("No output of expected type.");
    return false;
  }
  if (too_many_outputs_of_right_type) {
    ResourceMark rm;
    Node_List outputs_of_correct_type;
    for (DUIterator_Fast imax, i = center->fast_outs(imax); i < imax; i++) {
      Node* out = center->fast_out(i);
      if ((out->*_type_check)()) {
        outputs_of_correct_type.push(out);
      }
    }
    ss.print_cr("Non-unique output of expected type. Found: %d.", outputs_of_correct_type.size());
#ifndef PRODUCT
    for (uint i = 0; i < outputs_of_correct_type.size(); ++i) {
      outputs_of_correct_type.at(i)->dump("\n", false, &ss);
    }
#endif
    return false;
  }

  bool result = _pattern->match(single_output_of_right_type, path, ss);
  if (!result) {
    path.add_output_step(single_output_of_right_type);
  }
  return result;
}