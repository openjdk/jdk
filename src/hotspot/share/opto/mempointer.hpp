/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_MEMPOINTER_HPP
#define SHARE_OPTO_MEMPOINTER_HPP

#include "opto/memnode.hpp"

// TODO general description

// Summand of a MemPointerSimpleForm.
//   if node is a long (nodeL):
//     s = scaleL * nodeL
//   else, i.e. if node is a int (nodeI):
//     s = scaleL * ConvI2L(scaleI * nodeI)
//
class MemPointerSummand : public StackObj {
private:
  Node* _node;
  jlong _scaleL;
  jlong _scaleI;

public:
  MemPointerSummand() : _node(nullptr), _scaleL(0), _scaleI(0) {}
  MemPointerSummand(Node* node, const jlong scaleL, const jlong scaleI)
    : _node(node), _scaleL(scaleL), _scaleI(scaleI)
  {
    assert(_node != nullptr, "must have node");
    assert(_scaleL != 0 && _scaleI != 0, "non-zero scale");
  }

  Node* node() const { return _node; }
  jlong scaleL() const { return _scaleL; }
  jlong scaleI() const { return _scaleI; }
};

// Simple form of the pointer sub-expression of "pointer".
//
//   pointer = sum(summands) + con
//
class MemPointerSimpleForm : public StackObj {
private:
  static const int SUMMANDS_SIZE = 10; // TODO good?

  bool _is_valid; // the parsing succeeded
  Node* _pointer; // pointer node associated with this (sub)pointer

  MemPointerSummand _summands[SUMMANDS_SIZE];
  jlong _con;

public:
  MemPointerSimpleForm() {}
};

class MemPointerSimpleFormParser : public StackObj {
private:
  const MemNode* _mem;

  // Internal data-structures for parsing.
  GrowableArray<MemPointerSummand> _worklist;
  GrowableArray<MemPointerSummand> _summands;
  jlong _con;

  // Resulting simple-form.
  MemPointerSimpleForm _simple_form;

public:
  MemPointerSimpleFormParser(const MemNode* mem) : _mem(mem), _con(0) {
    _simple_form = parse_simple_form();
  }

  const MemPointerSimpleForm simple_form() const { return _simple_form; }

private:
  MemPointerSimpleForm parse_simple_form();
  void parse_sub_expression(const MemPointerSummand summand);
};

// TODO
class MemPointer : public StackObj {
private:
  bool _is_valid; // TODO needed?
  const MemNode* _mem;
  MemPointerSimpleForm _simple_form;

public:
  MemPointer(PhaseGVN* phase, const MemNode* mem) :
    _is_valid(false),
    _mem(mem)
  {
    assert(_mem->is_Store(), "only stores are supported");
    ResourceMark rm;
    MemPointerSimpleFormParser parser(_mem);
    _simple_form = parser.simple_form();
    assert(false, "TODO");
    // _mem->memory_size();
  }

  bool is_adjacent_to_and_before(const MemPointer& other) const;
};

#endif // SHARE_OPTO_MEMPOINTER_HPP


