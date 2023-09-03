/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
 */

#ifndef SHARE_OPTO_VECTORIZATION_HPP
#define SHARE_OPTO_VECTORIZATION_HPP

#include "opto/node.hpp"
#include "opto/loopnode.hpp"

// Code in this file and the vectorization.cpp contains shared logics and
// utilities for C2's loop auto-vectorization.

// A vectorization pointer (VPointer) has information about an address for
// dependence checking and vector alignment. It's usually bound to a memory
// operation in a counted loop for vectorizable analysis.
class VPointer : public ArenaObj {
 protected:
  MemNode*        _mem;      // My memory reference node
  PhaseIdealLoop* _phase;    // PhaseIdealLoop handle
  IdealLoopTree*  _lpt;      // Current IdealLoopTree
  PhiNode*        _iv;       // The loop induction variable

  Node* _base;               // null if unsafe nonheap reference
  Node* _adr;                // address pointer
  int   _scale;              // multiplier for iv (in bytes), 0 if no loop iv
  int   _offset;             // constant offset (in bytes)

  Node* _invar;              // invariant offset (in bytes), null if none
#ifdef ASSERT
  Node* _debug_invar;
  bool  _debug_negate_invar; // if true then use: (0 - _invar)
  Node* _debug_invar_scale;  // multiplier for invariant
#endif

  Node_Stack* _nstack;       // stack used to record a vpointer trace of variants
  bool        _analyze_only; // Used in loop unrolling only for vpointer trace
  uint        _stack_idx;    // Used in loop unrolling only for vpointer trace

  PhaseIdealLoop* phase() const { return _phase; }
  IdealLoopTree*  lpt() const   { return _lpt; }
  PhiNode*        iv() const    { return _iv; }

  bool is_loop_member(Node* n) const;
  bool invariant(Node* n) const;

  // Match: k*iv + offset
  bool scaled_iv_plus_offset(Node* n);
  // Match: k*iv where k is a constant that's not zero
  bool scaled_iv(Node* n);
  // Match: offset is (k [+/- invariant])
  bool offset_plus_k(Node* n, bool negate = false);

 public:
  enum CMP {
    Less          = 1,
    Greater       = 2,
    Equal         = 4,
    NotEqual      = (Less | Greater),
    NotComparable = (Less | Greater | Equal)
  };

  VPointer(MemNode* mem, PhaseIdealLoop* phase, IdealLoopTree* lpt,
            Node_Stack* nstack, bool analyze_only);
  // Following is used to create a temporary object during
  // the pattern match of an address expression.
  VPointer(VPointer* p);

  bool valid()  { return _adr != nullptr; }
  bool has_iv() { return _scale != 0; }

  Node* base()             { return _base; }
  Node* adr()              { return _adr; }
  MemNode* mem()           { return _mem; }
  int   scale_in_bytes()   { return _scale; }
  Node* invar()            { return _invar; }
  int   offset_in_bytes()  { return _offset; }
  int   memory_size()      { return _mem->memory_size(); }
  Node_Stack* node_stack() { return _nstack; }

  // Comparable?
  bool invar_equals(VPointer& q) {
    assert(_debug_invar == NodeSentinel || q._debug_invar == NodeSentinel ||
           (_invar == q._invar) == (_debug_invar == q._debug_invar &&
                                    _debug_invar_scale == q._debug_invar_scale &&
                                    _debug_negate_invar == q._debug_negate_invar), "");
    return _invar == q._invar;
  }

  int cmp(VPointer& q) {
    if (valid() && q.valid() &&
        (_adr == q._adr || (_base == _adr && q._base == q._adr)) &&
        _scale == q._scale   && invar_equals(q)) {
      bool overlap = q._offset <   _offset +   memory_size() &&
                       _offset < q._offset + q.memory_size();
      return overlap ? Equal : (_offset < q._offset ? Less : Greater);
    } else {
      return NotComparable;
    }
  }

  bool not_equal(VPointer& q)     { return not_equal(cmp(q)); }
  bool equal(VPointer& q)         { return equal(cmp(q)); }
  bool comparable(VPointer& q)    { return comparable(cmp(q)); }
  static bool not_equal(int cmp)  { return cmp <= NotEqual; }
  static bool equal(int cmp)      { return cmp == Equal; }
  static bool comparable(int cmp) { return cmp < NotComparable; }

  void print();

#ifndef PRODUCT
  class Tracer {
    friend class VPointer;
    bool _is_trace_alignment;
    static int _depth;
    int _depth_save;
    void print_depth() const;
    int  depth() const    { return _depth; }
    void set_depth(int d) { _depth = d; }
    void inc_depth()      { _depth++; }
    void dec_depth()      { if (_depth > 0) _depth--; }
    void store_depth()    { _depth_save = _depth; }
    void restore_depth()  { _depth = _depth_save; }

    class Depth {
      friend class VPointer;
      Depth()      { ++_depth; }
      Depth(int x) { _depth = 0; }
      ~Depth()     { if (_depth > 0) --_depth; }
    };
    Tracer(bool is_trace_alignment) : _is_trace_alignment(is_trace_alignment) {}

    // tracing functions
    void ctor_1(Node* mem);
    void ctor_2(Node* adr);
    void ctor_3(Node* adr, int i);
    void ctor_4(Node* adr, int i);
    void ctor_5(Node* adr, Node* base,  int i);
    void ctor_6(Node* mem);

    void scaled_iv_plus_offset_1(Node* n);
    void scaled_iv_plus_offset_2(Node* n);
    void scaled_iv_plus_offset_3(Node* n);
    void scaled_iv_plus_offset_4(Node* n);
    void scaled_iv_plus_offset_5(Node* n);
    void scaled_iv_plus_offset_6(Node* n);
    void scaled_iv_plus_offset_7(Node* n);
    void scaled_iv_plus_offset_8(Node* n);

    void scaled_iv_1(Node* n);
    void scaled_iv_2(Node* n, int scale);
    void scaled_iv_3(Node* n, int scale);
    void scaled_iv_4(Node* n, int scale);
    void scaled_iv_5(Node* n, int scale);
    void scaled_iv_6(Node* n, int scale);
    void scaled_iv_7(Node* n);
    void scaled_iv_8(Node* n, VPointer* tmp);
    void scaled_iv_9(Node* n, int _scale, int _offset, Node* _invar);
    void scaled_iv_10(Node* n);

    void offset_plus_k_1(Node* n);
    void offset_plus_k_2(Node* n, int _offset);
    void offset_plus_k_3(Node* n, int _offset);
    void offset_plus_k_4(Node* n);
    void offset_plus_k_5(Node* n, Node* _invar);
    void offset_plus_k_6(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_7(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_8(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_9(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_10(Node* n, Node* _invar, bool _negate_invar, int _offset);
    void offset_plus_k_11(Node* n);
  } _tracer; // Tracer
#endif

  Node* maybe_negate_invar(bool negate, Node* invar);

  void maybe_add_to_invar(Node* new_invar, bool negate);

  Node* register_if_new(Node* n) const;
};


// Vector element size statistics for loop vectorization with vector masks
class VectorElementSizeStats {
 private:
  static const int NO_SIZE = -1;
  static const int MIXED_SIZE = -2;
  int* _stats;

 public:
  VectorElementSizeStats(Arena* a) : _stats(NEW_ARENA_ARRAY(a, int, 4)) {
    clear();
  }

  void clear() { memset(_stats, 0, sizeof(int) * 4); }

  void record_size(int size) {
    assert(1 <= size && size <= 8 && is_power_of_2(size), "Illegal size");
    _stats[exact_log2(size)]++;
  }

  int count_size(int size) {
    assert(1 <= size && size <= 8 && is_power_of_2(size), "Illegal size");
    return _stats[exact_log2(size)];
  }

  int smallest_size() {
    for (int i = 0; i <= 3; i++) {
      if (_stats[i] > 0) return (1 << i);
    }
    return NO_SIZE;
  }

  int largest_size() {
    for (int i = 3; i >= 0; i--) {
      if (_stats[i] > 0) return (1 << i);
    }
    return NO_SIZE;
  }

  int unique_size() {
    int small = smallest_size();
    int large = largest_size();
    return (small == large) ? small : MIXED_SIZE;
  }
};

#endif // SHARE_OPTO_VECTORIZATION_HPP
