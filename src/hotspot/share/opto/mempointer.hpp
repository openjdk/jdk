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
#include "opto/noOverflowInt.hpp"

#ifndef PRODUCT
class TraceMemPointer : public StackObj {
private:
  const bool _is_trace_pointer;
  const bool _is_trace_aliasing;
  const bool _is_trace_adjacency;

public:
  TraceMemPointer(const bool is_trace_pointer,
                  const bool is_trace_aliasing,
                  const bool is_trace_adjacency) :
    _is_trace_pointer(  is_trace_pointer),
    _is_trace_aliasing( is_trace_aliasing),
    _is_trace_adjacency(is_trace_adjacency)
    {}

  bool is_trace_pointer()   const { return _is_trace_pointer; }
  bool is_trace_aliasing()  const { return _is_trace_aliasing; }
  bool is_trace_adjacency() const { return _is_trace_adjacency; }
};
#endif

// Class to represent aliasing between two MemPointer.
class MemPointerAliasing {
public:
  enum Aliasing {
    Unknown, // Distance unknown.
             //   Example: two "int[]" with different variable index offsets.
             //            e.g. "array[i]  vs  array[j]".
             //            e.g. "array1[i] vs  array2[j]".
    Always}; // Constant distance = p1 - p2.
             //   Example: The same address expression, except for a constant offset
             //            e.g. "array[i]  vs  array[i+1]".
private:
  const Aliasing _aliasing;
  const jint _distance;

  MemPointerAliasing(const Aliasing aliasing, const jint distance) :
    _aliasing(aliasing),
    _distance(distance)
  {
    const jint max_distance = 1 << 30;
    assert(_distance < max_distance && _distance > -max_distance, "safe distance");
  }

public:
  MemPointerAliasing() : MemPointerAliasing(Unknown, 0) {}

  static MemPointerAliasing make_unknown() {
    return MemPointerAliasing();
  }

  static MemPointerAliasing make_always(const jint distance) {
    return MemPointerAliasing(Always, distance);
  }

  // Use case: exact aliasing and adjacency.
  bool is_always_at_distance(const jint distance) const {
    return _aliasing == Always && _distance == distance;
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    switch(_aliasing) {
      case Unknown: st->print("Unknown");               break;
      case Always:  st->print("Always(%d)", _distance); break;
      default: ShouldNotReachHere();
    }
  }
#endif
};

// Summand of a MemPointerSimpleForm.
//
// On 32-bit platforms, we trivially use 32-bit jint values for the address computation:
//
//   s = scaleI * variable                    // 32-bit variable
//   scale = scaleI
//
// On 64-bit platforms, we have a mix of 64-bit jlong and 32-bit jint values for the
// address computation:
//
//   s = scaleL * ConvI2L(scaleI * variable)  // 32-bit variable
//   scale = scaleL * scaleI
//
//   s = scaleL * variable                    // 64-bit variable
//   scale = scaleL
//
// For simplicity, we only allow 32-bit jint scales, wrapped in NoOverflowInt, where:
//
//   abs(scale) < (1 << 30)
//
// This allows very high scales, but allows calculations with scale to
// avoid overflows.
//
// TODO generalization: final product only needs to use scale, not scaleL
class MemPointerSummand : public StackObj {
private:
  Node* _variable;
  NoOverflowInt _scale;
  LP64_ONLY( NoOverflowInt _scaleL; )

public:
  MemPointerSummand() :
      _variable(nullptr),
      _scale(NoOverflowInt::make_NaN())
      LP64_ONLY( COMMA _scaleL(NoOverflowInt::make_NaN()) ) {}
  MemPointerSummand(Node* variable, const NoOverflowInt scale LP64_ONLY( COMMA const NoOverflowInt scaleL )) :
      _variable(variable),
      _scale(scale)
      LP64_ONLY( COMMA _scaleL(scaleL) )
  {
    assert(_variable != nullptr, "must have variable");
    assert(!_scale.is_zero(), "non-zero scale");
    LP64_ONLY( assert(!_scaleL.is_zero(), "non-zero scale") );
  }

  Node* variable() const { return _variable; }
  NoOverflowInt scale() const { return _scale; }
  LP64_ONLY( NoOverflowInt scaleL() const { return _scaleL; } )

  static int cmp_for_sort(MemPointerSummand* p1, MemPointerSummand* p2) {
    if (p1->variable() == nullptr) {
      return (p2->variable() == nullptr) ? 0 : 1;
    } else if (p2->variable() == nullptr) {
      return -1;
    }

    return p1->variable()->_idx - p2->variable()->_idx;
  }

  friend bool operator==(const MemPointerSummand a, const MemPointerSummand b) {
    // Both "null" -> equal.
    if (a.variable() == nullptr && b.variable() == nullptr) { return true; }

    // Same variable and scale?
    if (a.variable() != b.variable()) { return false; }
    return a.scale() == b.scale();
  }

  friend bool operator!=(const MemPointerSummand a, const MemPointerSummand b) {
    return !(a == b);
  }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    st->print("Summand[");
#ifdef _LP64
    st->print("(scaleL = ");
    _scaleL.print_on(st);
    st->print(") ");
#endif
    _scale.print_on(st);
    tty->print(" * [%d %s]]", _variable->_idx, _variable->Name());
  }
#endif
};

// Simple form of the pointer sub-expression of "pointer".
//
//   pointer = sum(summands) + con
//
class MemPointerSimpleForm : public StackObj {
private:
  // We limit the number of summands to 10. Usually, a pointer contains a base pointer
  // (e.g. array pointer or null for native memory) and a few variables. For example:
  //
  //   array[j]                      ->  array_base + j + con              -> 2 summands
  //   nativeMemorySegment.get(j)    ->  null + address + offset + j + con -> 3 summands
  //
  static const int SUMMANDS_SIZE = 10;

  Node* _pointer; // pointer node associated with this (sub)pointer

  MemPointerSummand _summands[SUMMANDS_SIZE];
  NoOverflowInt _con;

public:
  // Empty
  MemPointerSimpleForm() : _pointer(nullptr), _con(NoOverflowInt::make_NaN()) {}
  // Default: pointer = variable
  MemPointerSimpleForm(Node* variable) : _pointer(variable), _con(NoOverflowInt(0)) {
    const NoOverflowInt one(1);
    _summands[0] = MemPointerSummand(variable, one LP64_ONLY( COMMA one ));
  }

private:
  MemPointerSimpleForm(Node* pointer, const GrowableArray<MemPointerSummand>& summands, const NoOverflowInt con)
    :_pointer(pointer), _con(con) {
    assert(summands.length() <= SUMMANDS_SIZE, "summands must fit");
    for (int i = 0; i < summands.length(); i++) {
      MemPointerSummand s = summands.at(i);
      assert(s.variable() != nullptr, "variable cannot be null");
      _summands[i] = s;
    }
  }

public:
  static MemPointerSimpleForm make(Node* pointer, const GrowableArray<MemPointerSummand>& summands, const NoOverflowInt con) {
    if (summands.length() <= SUMMANDS_SIZE) {
      return MemPointerSimpleForm(pointer, summands, con);
    } else {
      return MemPointerSimpleForm(pointer);
    }
  }

  MemPointerAliasing get_aliasing_with(const MemPointerSimpleForm& other
                                       NOT_PRODUCT( COMMA const TraceMemPointer& trace) ) const;

  const MemPointerSummand summands_at(const uint i) const {
    assert(i < SUMMANDS_SIZE, "in bounds");
    return _summands[i];
  }

  const NoOverflowInt con() const { return _con; }

#ifndef PRODUCT
  void print_on(outputStream* st) const {
    if (_pointer == nullptr) {
      st->print_cr("MemPointerSimpleForm empty.");
      return;
    }
    st->print("MemPointerSimpleForm[%d %s:  con = ", _pointer->_idx, _pointer->Name());
    _con.print_on(st);
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& summand = _summands[i];
      if (summand.variable() != nullptr) {
        st->print(", ");
        summand.print_on(st);
      }
    }
    st->print_cr("]");
  }
#endif
};

class MemPointerSimpleFormParser : public StackObj {
private:
  const MemNode* _mem;

  // Internal data-structures for parsing.
  GrowableArray<MemPointerSummand> _worklist;
  GrowableArray<MemPointerSummand> _summands;
  NoOverflowInt _con;

  // Resulting simple-form.
  MemPointerSimpleForm _simple_form;

public:
  MemPointerSimpleFormParser(const MemNode* mem) : _mem(mem), _con(NoOverflowInt(0)) {
    _simple_form = parse_simple_form();
  }

  const MemPointerSimpleForm simple_form() const { return _simple_form; }

private:
  MemPointerSimpleForm parse_simple_form();
  void parse_sub_expression(const MemPointerSummand summand);

  bool is_safe_from_int_overflow(const int opc LP64_ONLY( COMMA const NoOverflowInt scaleL )) const;
};

// TODO
class MemPointer : public StackObj {
private:
  const MemNode* _mem;
  const MemPointerSimpleForm _simple_form;

  NOT_PRODUCT( const TraceMemPointer& _trace; )

public:
  MemPointer(const MemNode* mem NOT_PRODUCT( COMMA const TraceMemPointer& trace)) :
    _mem(mem),
    _simple_form(init_simple_form(_mem))
    NOT_PRODUCT( COMMA _trace(trace) )
  {
#ifndef PRODUCT
    if (_trace.is_trace_pointer()) {
      tty->print_cr("MemPointer::MemPointer:");
      tty->print("mem: "); mem->dump();
      _mem->in(MemNode::Address)->dump_bfs(5, 0, "d");
      _simple_form.print_on(tty);
    }
#endif
  }

  const MemNode* mem() const { return _mem; }
  const MemPointerSimpleForm simple_form() const { return _simple_form; }
  bool is_adjacent_to_and_before(const MemPointer& other) const;

private:
  static const MemPointerSimpleForm init_simple_form(const MemNode* mem) {
    assert(mem->is_Store(), "only stores are supported");
    ResourceMark rm;
    MemPointerSimpleFormParser parser(mem);
    return parser.simple_form();
  }
};

#endif // SHARE_OPTO_MEMPOINTER_HPP
