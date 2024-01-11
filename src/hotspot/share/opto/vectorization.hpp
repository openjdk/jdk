/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
  const MemNode*  _mem;      // My memory reference node
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

  VPointer(const MemNode* mem,
           PhaseIdealLoop* phase, IdealLoopTree* lpt,
           Node_Stack* nstack, bool analyze_only);
  // Following is used to create a temporary object during
  // the pattern match of an address expression.
  VPointer(VPointer* p);

  bool valid()             const { return _adr != nullptr; }
  bool has_iv()            const { return _scale != 0; }

  Node* base()             const { return _base; }
  Node* adr()              const { return _adr; }
  const MemNode* mem()     const { return _mem; }
  int   scale_in_bytes()   const { return _scale; }
  Node* invar()            const { return _invar; }
  int   offset_in_bytes()  const { return _offset; }
  int   memory_size()      const { return _mem->memory_size(); }
  Node_Stack* node_stack() const { return _nstack; }

  // Biggest detectable factor of the invariant.
  int   invar_factor() const;

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

  bool overlap_possible_with_any_in(Node_List* p) {
    for (uint k = 0; k < p->size(); k++) {
      MemNode* mem = p->at(k)->as_Mem();
      VPointer p_mem(mem, phase(), lpt(), nullptr, false);
      // Only if we know that we have Less or Greater can we
      // be sure that there can never be an overlap between
      // the two memory regions.
      if (!not_equal(p_mem)) {
        return true;
      }
    }
    return false;
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
    void ctor_1(const Node* mem);
    void ctor_2(Node* adr);
    void ctor_3(Node* adr, int i);
    void ctor_4(Node* adr, int i);
    void ctor_5(Node* adr, Node* base,  int i);
    void ctor_6(const Node* mem);

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

// When alignment is required, we must adjust the pre-loop iteration count pre_iter,
// such that the address is aligned for any main_iter >= 0:
//
//   adr = base + offset + invar + scale * init
//                               + scale * pre_stride * pre_iter
//                               + scale * main_stride * main_iter
//
// The AlignmentSolver generates solutions of the following forms:
//   1. Empty:       No pre_iter guarantees alignment.
//   2. Trivial:     Any pre_iter guarantees alignment.
//   3. Constrained: There is a periodic solution, but it is not trivial.
//
// The Constrained solution is of the following form:
//
//   pre_iter = m * q + r                                    (for any integer m)
//                   [- invar / (scale * pre_stride)  ]      (if there is an invariant)
//                   [- init / pre_stride             ]      (if init is variable)
//
// The solution is periodic with periodicity q, which is guaranteed to be a power of 2.
// This periodic solution is "rotated" by three alignment terms: one for constants (r),
// one for the invariant (if present), and one for init (if it is variable).
//
// The "filter" method combines the solutions of two mem_refs, such that the new set of
// values for pre_iter guarantees alignment for both mem_refs.
//
class EmptyAlignmentSolution;
class TrivialAlignmentSolution;
class ConstrainedAlignmentSolution;

class AlignmentSolution : public ResourceObj {
public:
  virtual bool is_empty() const = 0;
  virtual bool is_trivial() const = 0;
  virtual bool is_constrained() const = 0;

  virtual const ConstrainedAlignmentSolution* as_constrained() const {
    assert(is_constrained(), "must be constrained");
    return nullptr;
  }

  // Implemented by each subclass
  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const = 0;
  virtual void print() const = 0;

  // Compute modulo and ensure that we get a positive remainder
  static int mod(int i, int q) {
    assert(q >= 1, "modulo value must be large enough");

    // Modulo operator: Get positive 0 <= r < q  for positive i, but
    //                  get negative 0 >= r > -q for negative i.
    int r = i % q;

    // Make negative r into positive ones:
    r = (r >= 0) ? r : r + q;

    assert(0 <= r && r < q, "remainder must fit in modulo space");
    return r;
  }
};

class EmptyAlignmentSolution : public AlignmentSolution {
private:
  const char* _reason;
public:
  EmptyAlignmentSolution(const char* reason) :  _reason(reason) {}
  virtual bool is_empty() const override final       { return true; }
  virtual bool is_trivial() const override final     { return false; }
  virtual bool is_constrained() const override final { return false; }
  const char* reason() const { return _reason; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    // If "this" cannot be guaranteed to be aligned, then we also cannot guarantee to align
    // "this" and "other" together.
    return new EmptyAlignmentSolution("empty solution input to filter");
  }

  virtual void print() const override final {
    tty->print_cr("empty solution: %s", reason());
  };
};

class TrivialAlignmentSolution : public AlignmentSolution {
public:
  TrivialAlignmentSolution() {}
  virtual bool is_empty() const override final       { return false; }
  virtual bool is_trivial() const override final     { return true; }
  virtual bool is_constrained() const override final { return false; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    if (other->is_empty()) {
      // If "other" cannot be guaranteed to be aligned, then we also cannot guarantee to align
      // "this" and "other".
      return new EmptyAlignmentSolution("empty solution input to filter");
    }
    // Since "this" is trivial (no constraints), the solution of "other" guarantees alignment
    // of both.
    return other;
  }

  virtual void print() const override final {
    tty->print_cr("pre_iter >= 0 (trivial)");
  };
};

class ConstrainedAlignmentSolution : public AlignmentSolution {
private:
  const MemNode* _mem_ref;
  const int _q;
  const int _r;
  const Node* _invar;
  const int _scale;
public:
  ConstrainedAlignmentSolution(const MemNode* mem_ref,
                               const int q,
                               const int r,
                               const Node* invar,
                               int scale) :
      _mem_ref(mem_ref),
      _q(q),
      _r(r),
      _invar(invar),
      _scale(scale) {
    assert(q > 1 && is_power_of_2(q), "q must be power of 2");
    assert(0 <= r && r < q, "r must be in modulo space of q");
    assert(_mem_ref != nullptr, "must have mem_ref");
  }

  virtual bool is_empty() const override final       { return false; }
  virtual bool is_trivial() const override final     { return false; }
  virtual bool is_constrained() const override final { return true; }

  const MemNode* mem_ref() const        { return _mem_ref; }

  virtual const ConstrainedAlignmentSolution* as_constrained() const override final { return this; }

  virtual const AlignmentSolution* filter(const AlignmentSolution* other) const override final {
    if (other->is_empty()) {
      // If "other" cannot be guaranteed to be aligned, then we also cannot guarantee to align
      // "this" and "other" together.
      return new EmptyAlignmentSolution("empty solution input to filter");
    }
    // Since "other" is trivial (no constraints), the solution of "this" guarantees alignment
    // of both.
    if (other->is_trivial()) {
      return this;
    }

    // Both solutions are constrained:
    ConstrainedAlignmentSolution const* s1 = this;
    ConstrainedAlignmentSolution const* s2 = other->as_constrained();

    // Thus, pre_iter is the intersection of two sets, i.e. constrained by these two equations,
    // for any integers m1 and m2:
    //
    //   pre_iter = m1 * q1 + r1
    //                     [- invar1 / (scale1 * pre_stride)  ]
    //                     [- init / pre_stride               ]
    //
    //   pre_iter = m2 * q2 + r2
    //                     [- invar2 / (scale2 * pre_stride)  ]
    //                     [- init / pre_stride               ]
    //
    // Note: pre_stride and init are identical for all mem_refs in the loop.
    //
    // The init alignment term either does not exist for both mem_refs, or exists identically
    // for both. The init alignment term is thus trivially identical.
    //
    // The invar alignment term is identical if either:
    //   - both mem_refs have no invariant.
    //   - both mem_refs have the same invariant and the same scale.
    //
    if (s1->_invar != s2->_invar) {
      return new EmptyAlignmentSolution("invar not identical");
    }
    if (s1->_invar != nullptr && s1->_scale != s2->_scale) {
      return new EmptyAlignmentSolution("has invar with different scale");
    }

    // Now, we have reduced the problem to:
    //
    //   pre_iter = m1 * q1 + r1 [- x]       (S1)
    //   pre_iter = m2 * q2 + r2 [- x]       (S2)
    //

    // Make s2 the bigger modulo space, i.e. has larger periodicity q.
    // This guarantees that S2 is either identical to, a subset of,
    // or disjunct from S1 (but cannot be a strict superset of S1).
    if (s1->_q > s2->_q) {
      swap(s1, s2);
    }
    assert(s1->_q <= s2->_q, "s1 is a smaller modulo space than s2");

    // Is S2 subset of (or equal to) S1?
    //
    // for any m2, there are integers a, b, m1: m2 * q2     + r2          =
    //                                          m2 * a * q1 + b * q1 + r1 =
    //                                          (m2 * a + b) * q1 + r1
    //
    // Since q1 and q2 are both powers of 2, and q1 <= q2, we know there
    // is an integer a: a * q1 = q2. Thus, it remains to check if there
    // is an integer b: b * q1 + r1 = r2. This is equivalent to checking:
    //
    //   r1 = r1 % q1 = r2 % q1
    //
    if (mod(s2->_r, s1->_q) != s1->_r) {
      // Neither is subset of the other -> no intersection
      return new EmptyAlignmentSolution("empty intersection (r and q)");
    }

    // Now we know: "s1 = m1 * q1 + r1" is a superset of "s2 = m2 * q2 + r2"
    // Hence, any solution of S2 guarantees alignment for both mem_refs.
    return s2; // return the subset
  }

  virtual void print() const override final {
    tty->print("m * q(%d) + r(%d)", _q, _r);
    if (_invar != nullptr) {
      tty->print(" - invar[%d] / (scale(%d) * pre_stride)", _invar->_idx, _scale);
    }
    tty->print_cr(" [- init / pre_stride], mem_ref[%d]", mem_ref()->_idx);
  };
};

// When strict alignment is required (e.g. -XX:+AlignVector), then we must ensure
// that all vector memory accesses can be aligned. We achieve this alignment by
// adjusting the pre-loop limit, which adjusts the number of iterations executed
// in the pre-loop.
//
// This is how the pre-loop and unrolled main-loop look like for a memref (adr):
//
// iv = init
// i = 0 // single-iteration counter
//
// pre-loop:
//   iv = init + i * pre_stride
//   adr = base + offset + invar + scale * iv
//   adr = base + offset + invar + scale * (init + i * pre_stride)
//   iv += pre_stride
//   i++
//
// pre_iter = i // number of iterations in the pre-loop
// iv = init + pre_iter * pre_stride
//
// main_iter = 0 // main-loop iteration counter
// main_stride = unroll_factor * pre_stride
//
// main-loop:
//   i = pre_iter + main_iter * unroll_factor
//   iv = init + i * pre_stride = init + pre_iter * pre_stride + main_iter * unroll_factor * pre_stride
//                              = init + pre_iter * pre_stride + main_iter * main_stride
//   adr = base + offset + invar + scale * iv // must be aligned
//   iv += main_stride
//   i  += unroll_factor
//   main_iter++
//
// For each vector memory access, we can find the set of pre_iter (number of pre-loop
// iterations) which would align its address. The AlignmentSolver finds such an
// AlignmentSolution. We can then check which solutions are compatible, and thus
// decide if we have to (partially) reject vectorization if not all vectors have
// a compatible solutions.
class AlignmentSolver {
private:
  const MemNode* _mem_ref;       // first element
  const uint     _vector_length; // number of elements in vector
  const int      _element_size;
  const int      _vector_width;  // in bytes

  // All vector loads and stores need to be memory aligned. The alignment width (aw) in
  // principle is the vector_width. But when vector_width > ObjectAlignmentInBytes this is
  // too strict, since any memory object is only guaranteed to be ObjectAlignmentInBytes
  // aligned. For example, the relative offset between two arrays is only guaranteed to
  // be divisible by ObjectAlignmentInBytes.
  const int      _aw;

  // We analyze the address of mem_ref. The idea is to disassemble it into a linear
  // expression, where we can use the constant factors as the basis for ensuring the
  // alignment of vector memory accesses.
  //
  // The Simple form of the address is disassembled by VPointer into:
  //
  //   adr = base + offset + invar + scale * iv
  //
  // Where the iv can be written as:
  //
  //   iv = init + pre_stride * pre_iter + main_stride * main_iter
  //
  // pre_iter:    number of pre-loop iterations (adjustable via pre-loop limit)
  // main_iter:   number of main-loop iterations (main_iter >= 0)
  //
  const Node*    _base;           // base of address (e.g. Java array object, aw-aligned)
  const int      _offset;
  const Node*    _invar;
  const int      _invar_factor;   // known constant factor of invar
  const int      _scale;
  const Node*    _init_node;      // value of iv before pre-loop
  const int      _pre_stride;     // address increment per pre-loop iteration
  const int      _main_stride;    // address increment per main-loop iteration

  DEBUG_ONLY( const bool _is_trace; );

  static const MemNode* mem_ref_not_null(const MemNode* mem_ref) {
    assert(mem_ref != nullptr, "not nullptr");
    return mem_ref;
  }

public:
  AlignmentSolver(const MemNode* mem_ref,
                  const uint vector_length,
                  const Node* base,
                  const int offset,
                  const Node* invar,
                  const int invar_factor,
                  const int scale,
                  const Node* init_node,
                  const int pre_stride,
                  const int main_stride
                  DEBUG_ONLY( COMMA const bool is_trace)
                  ) :
      _mem_ref(           mem_ref_not_null(mem_ref)),
      _vector_length(     vector_length),
      _element_size(      _mem_ref->memory_size()),
      _vector_width(      _vector_length * _element_size),
      _aw(                MIN2(_vector_width, ObjectAlignmentInBytes)),
      _base(              base),
      _offset(            offset),
      _invar(             invar),
      _invar_factor(      invar_factor),
      _scale(             scale),
      _init_node(         init_node),
      _pre_stride(        pre_stride),
      _main_stride(       main_stride)
      DEBUG_ONLY( COMMA _is_trace(is_trace) )
  {
    assert(_mem_ref != nullptr &&
           (_mem_ref->is_Load() || _mem_ref->is_Store()),
           "only load or store vectors allowed");
  }

  AlignmentSolution* solve() const;

private:
  class EQ4 {
   private:
    const int _C_const;
    const int _C_invar;
    const int _C_init;
    const int _C_pre;
    const int _aw;

   public:
    EQ4(const int C_const, const int C_invar, const int C_init, const int C_pre, const int aw) :
    _C_const(C_const), _C_invar(C_invar), _C_init(C_init), _C_pre(C_pre), _aw(aw) {}

    enum State { TRIVIAL, CONSTRAINED, EMPTY };

    State eq4a_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_const_mod_aw() == 0       ) ? TRIVIAL     : EMPTY)
                                  : ( (C_const_mod_abs_C_pre() == 0) ? CONSTRAINED : EMPTY);
    }

    State eq4b_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_invar_mod_aw() == 0       ) ? TRIVIAL     : EMPTY)
                                  : ( (C_invar_mod_abs_C_pre() == 0) ? CONSTRAINED : EMPTY);
    }

    State eq4c_state() const {
      return (abs(_C_pre) >= _aw) ? ( (C_init_mod_aw() == 0       )  ? TRIVIAL     : EMPTY)
                                  : ( (C_init_mod_abs_C_pre() == 0)  ? CONSTRAINED : EMPTY);
    }

   private:
    int C_const_mod_aw() const        { return AlignmentSolution::mod(_C_const, _aw); }
    int C_invar_mod_aw() const        { return AlignmentSolution::mod(_C_invar, _aw); }
    int C_init_mod_aw() const         { return AlignmentSolution::mod(_C_init,  _aw); }
    int C_const_mod_abs_C_pre() const { return AlignmentSolution::mod(_C_const, abs(_C_pre)); }
    int C_invar_mod_abs_C_pre() const { return AlignmentSolution::mod(_C_invar, abs(_C_pre)); }
    int C_init_mod_abs_C_pre() const  { return AlignmentSolution::mod(_C_init,  abs(_C_pre)); }

#ifdef ASSERT
   public:
    void trace() const;

   private:
    static const char* state_to_str(State s) {
      if (s == TRIVIAL)     { return "trivial"; }
      if (s == CONSTRAINED) { return "constrained"; }
      return "empty";
    }
#endif
  };

#ifdef ASSERT
  bool is_trace() const { return _is_trace; }
  void trace_start_solve() const;
  void trace_reshaped_form(const int C_const,
                           const int C_const_init,
                           const int C_invar,
                           const int C_init,
                           const int C_pre,
                           const int C_main) const;
  void trace_main_iteration_alignment(const int C_const,
                                      const int C_invar,
                                      const int C_init,
                                      const int C_pre,
                                      const int C_main,
                                      const int C_main_mod_aw) const;
  void trace_constrained_solution(const int C_const,
                                  const int C_invar,
                                  const int C_init,
                                  const int C_pre,
                                  const int q,
                                  const int r) const;
#endif
};

#endif // SHARE_OPTO_VECTORIZATION_HPP
