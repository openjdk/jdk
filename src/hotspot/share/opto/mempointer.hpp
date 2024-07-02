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
//   if var is a long (varL):
//     s = scaleL * varL
//   else, i.e. if var is a int (varI):
//     s = scaleL * ConvI2L(scaleI * varI)
//
class MemPointerSummand : public StackObj {
public:
  Node* _var;
  jlong _scaleL;
  jlong _scaleI;

public:
  MemPointerSummand() : _var(nullptr), _scaleL(0), _scaleI(0) {}
  MemPointerSummand(Node* var, const jlong scaleL, const jlong scaleI)
    : _var(var), _scaleL(scaleL), _scaleI(scaleI)
  {
    assert(_var != nullptr, "must have variable");
    assert(_scaleL != 0 && _scaleI != 0, "non-zero scale");
  }
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

  static MemPointerSimpleForm make_from_ConIL(Node* n, const jlong con);
  static MemPointerSimpleForm make_from_AddSubILP(Node* n, const MemPointerSimpleForm* a, const MemPointerSimpleForm* b);
  static MemPointerSimpleForm make_from_Mul(Node* n, const MemPointerSimpleForm* a, const jlong scale);
  static MemPointerSimpleForm make_from_ConvI2L(Node* n, const MemPointerSimpleForm* a);
};

// TODO
class MemPointer : public StackObj {
private:
  bool _is_valid;
  const MemNode* _mem;
  MemPointerSimpleForm _simple_form;

public:
  MemPointer(PhaseGVN* phase, const MemNode* mem) :
    _is_valid(false),
    _mem(mem)
  {
    assert(_mem->is_Store(), "only stores are supported");
    Node* pointer = mem->in(MemNode::Address);
    _simple_form = parse_simple_form(pointer);
    assert(false, "TODO");
    // _mem->memory_size();
  }

  static MemPointerSimpleForm parse_simple_form(Node* pointer);

  bool is_adjacent_to_and_before(const MemPointer& other) const;
};

#endif // SHARE_OPTO_MEMPOINTER_HPP


