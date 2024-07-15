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

// Wrapper around jint, which detects overflow.
// TODO consider moving to separate file, and have GTests?
class NoOverflowInt {
private:
  bool _is_NaN; // overflow, uninitialized, etc.
  jint _value;

public:
  // Default: NaN.
  NoOverflowInt() : _is_NaN(true), _value(0) {}

  // Create from jlong (or jint) -> NaN if overflows jint.
  explicit NoOverflowInt(jlong value) : _is_NaN(true), _value(0) {
    jint trunc = (jint)value;
    if ((jlong)trunc == value) {
      _is_NaN = false;
      _value = trunc;
    }
  }

  static NoOverflowInt make_NaN() { return NoOverflowInt(); }

  bool is_NaN() const { return _is_NaN; }
  jint value() const { assert(!is_NaN(), "NaN not allowed"); return _value; }
  bool is_zero() const { return !is_NaN() && value() == 0; }

  friend NoOverflowInt operator+(const NoOverflowInt a, const NoOverflowInt b) {
    if (a.is_NaN()) { return make_NaN(); }
    if (b.is_NaN()) { return make_NaN(); }
    return NoOverflowInt(java_add((jlong)a.value(), (jlong)b.value()));
  }

  friend NoOverflowInt operator-(const NoOverflowInt a, const NoOverflowInt b) {
    if (a.is_NaN()) { return make_NaN(); }
    if (b.is_NaN()) { return make_NaN(); }
    return NoOverflowInt(java_subtract((jlong)a.value(), (jlong)b.value()));
  }

  friend NoOverflowInt operator*(const NoOverflowInt a, const NoOverflowInt b) {
    if (a.is_NaN()) { return make_NaN(); }
    if (b.is_NaN()) { return make_NaN(); }
    return NoOverflowInt(java_multiply((jlong)a.value(), (jlong)b.value()));
  }

  friend NoOverflowInt operator<<(const NoOverflowInt a, const NoOverflowInt b) {
    if (a.is_NaN()) { return make_NaN(); }
    if (b.is_NaN()) { return make_NaN(); }
    jint shift = b.value();
    if (shift < 0 || shift > 31) { return make_NaN(); }
    return NoOverflowInt(java_shift_left((jlong)a.value(), shift));
  }

  friend bool operator==(const NoOverflowInt a, const NoOverflowInt b) {
    if (a.is_NaN()) { return false; }
    if (b.is_NaN()) { return false; }
    return a.value() == b.value();
  }

  NoOverflowInt truncate_to_30_bits() const {
    if (is_NaN()) { return make_NaN(); }
    const jint max_value = 1 << 30;
    if (value() > max_value || value() < -max_value) { return make_NaN(); }
    return *this;
  }

#ifndef PRODUCT
  void print() const {
    if (is_NaN()) {
      tty->print("NaN");
    } else {
      tty->print("%d", value());
    }
  }
#endif
};

// Class to represent aliasing between two MemPointer.
class MemPointerAliasing {
public:
  enum Aliasing {
    Unknown, // Distance unknown.
             //   Example: two "int[]" with different variable index offsets.
             //            e.g. "array[i] = array[j]".
    Never,   // Can never alias.
             //   Example: "int[]" and "float[]".
             //            e.g. "intArray[i] = floatArray[i]".
    Always,  // Constant distance = p1 - p2.
             //   Example: The same address expression, except for a constant offset
             //            e.g. "array[i] = array[i+1]".
    Maybe};  // Either "Never" (i.e. different memory objects)
             //     or "Always" (at constant distance).
             //   Example: "array1[i] = array2[i]":
             //     If at runtime "array1 != array2": cannot alias.
             //     If at runtime "array1 == array2": constant distance.
             // TODO consider to simplify for MergeStores...?
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

  static MemPointerAliasing make_never() {
    return MemPointerAliasing(Never, 0);
  }

  static MemPointerAliasing make_always(const jint distance) {
    return MemPointerAliasing(Always, distance);
  }

  static MemPointerAliasing make_maybe(const jint distance) {
    return MemPointerAliasing(Maybe, distance);
  }

  Aliasing aliasing() const { return _aliasing; }
  bool has_distance() const { return _aliasing == Always || _aliasing == Maybe; }
  jint distance() const { assert(has_distance(), "must have"); return _distance; }

  // Use case: exact aliasing and adjacency.
  bool is_always_at_distance(const jint distance) const {
    return _aliasing == Always && _distance == distance;
  }

// TODO maybe not yet
//   bool is_never_overlapping(const jint size1, const jint size2) {
//     assert(1 <= size1 && size1 <= 1024, "sane size");
//     assert(1 <= size2 && size2 <= 1024, "sane size");
//
//     if (_aliasing == Unknown) { return false; }
//     if (_aliasing == Never)   { return true; }
//
//     // distance = p2 - p1
//     const jint d = distance();
//     return size1 <=  d || // <==>  size1 <= p2 - p1  <==>  p1 + size1 <= p2
//            size2 <= -d;   // <==>  size2 <= p1 - p2  <==>  p2 + size2 <= p1
//   }

#ifndef PRODUCT
  void print() const {
    switch(_aliasing) {
      case Unknown: tty->print("Unknown");               break;
      case Never:   tty->print("Never");                 break;
      case Always:  tty->print("Always(%d)", _distance); break;
      case Maybe:   tty->print("Maybe(%d)", _distance);  break;
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
  void print() const {
    tty->print("  MemPointerSummand: ");
#ifdef _LP64
    tty->print("(scaleL = ");
    _scaleL.print();
    tty->print(") ");
#endif
    _scale.print();
    tty->print(" * variable: ");
    _variable->dump();
  }
#endif
};

// Simple form of the pointer sub-expression of "pointer".
//
//   pointer = sum(summands) + con
//
class MemPointerSimpleForm : public StackObj {
private:
  static const int SUMMANDS_SIZE = 10; // TODO good?

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
      _summands[i] = summands.at(i);
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

  MemPointerAliasing get_aliasing_with(const MemPointerSimpleForm& other) const;

  const MemPointerSummand summands_at(const uint i) const {
    assert(i < SUMMANDS_SIZE, "in bounds");
    return _summands[i];
  }

  const NoOverflowInt con() const { return _con; }

#ifndef PRODUCT
  void print() const {
    if (_pointer == nullptr) {
      tty->print_cr("MemPointerSimpleForm empty.");
      return;
    }
    tty->print("MemPointerSimpleForm for ");
    _pointer->dump();
    tty->print("  con = ");
    _con.print();
    tty->cr();
    for (int i = 0; i < SUMMANDS_SIZE; i++) {
      const MemPointerSummand& summand = _summands[i];
      if (summand.variable() != nullptr) {
        summand.print();
      }
    }
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
};

// TODO
class MemPointer : public StackObj {
private:
  const MemNode* _mem;
  const MemPointerSimpleForm _simple_form;

public:
  // TODO no need for phase?
  MemPointer(PhaseGVN* phase, const MemNode* mem) :
    _mem(mem),
    _simple_form(init_simple_form(_mem))
  {
    // _simple_form.print(); // TODO tracing???
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
