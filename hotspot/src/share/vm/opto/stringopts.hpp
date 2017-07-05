/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_OPTO_STRINGOPTS_HPP
#define SHARE_VM_OPTO_STRINGOPTS_HPP

#include "opto/node.hpp"
#include "opto/phaseX.hpp"

class StringConcat;

class PhaseStringOpts : public Phase {
  friend class StringConcat;

 private:
  PhaseGVN* _gvn;

  // List of dead nodes to clean up aggressively at the end
  Unique_Node_List dead_worklist;

  // Memory slices needed for code gen
  int char_adr_idx;
  int value_field_idx;
  int count_field_idx;
  int offset_field_idx;

  // Integer.sizeTable - used for int to String conversion
  ciField* size_table_field;

  // A set for use by various stages
  VectorSet _visited;

  // Collect a list of all SB.toString calls
  Node_List collect_toString_calls();

  // Examine the use of the SB alloc to see if it can be replace with
  // a single string construction.
  StringConcat* build_candidate(CallStaticJavaNode* call);

  // Replace all the SB calls in concat with an optimization String allocation
  void replace_string_concat(StringConcat* concat);

  // Load the value of a static field, performing any constant folding.
  Node* fetch_static_field(GraphKit& kit, ciField* field);

  // Compute the number of characters required to represent the int value
  Node* int_stringSize(GraphKit& kit, Node* value);

  // Copy the characters representing value into char_array starting at start
  void int_getChars(GraphKit& kit, Node* value, Node* char_array, Node* start, Node* end);

  // Copy of the contents of the String str into char_array starting at index start.
  Node* copy_string(GraphKit& kit, Node* str, Node* char_array, Node* start);

  // Clean up any leftover nodes
  void record_dead_node(Node* node);
  void remove_dead_nodes();

  PhaseGVN* gvn() { return _gvn; }

  enum {
    // max length of constant string copy unrolling in copy_string
    unroll_string_copy_length = 6
  };

 public:
  PhaseStringOpts(PhaseGVN* gvn, Unique_Node_List* worklist);
};

#endif // SHARE_VM_OPTO_STRINGOPTS_HPP
