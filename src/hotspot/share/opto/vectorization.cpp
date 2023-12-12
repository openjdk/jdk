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

#include "precompiled.hpp"
#include "opto/addnode.hpp"
#include "opto/connode.hpp"
#include "opto/convertnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mulnode.hpp"
#include "opto/rootnode.hpp"
#include "opto/vectorization.hpp"

#ifndef PRODUCT
int VPointer::Tracer::_depth = 0;
#endif

VPointer::VPointer(const MemNode* mem,
                   PhaseIdealLoop* phase, IdealLoopTree* lpt,
                   Node_Stack* nstack, bool analyze_only) :
  _mem(mem), _phase(phase), _lpt(lpt),
  _iv(lpt->_head->as_CountedLoop()->phi()->as_Phi()),
  _base(nullptr), _adr(nullptr), _scale(0), _offset(0), _invar(nullptr),
#ifdef ASSERT
  _debug_invar(nullptr), _debug_negate_invar(false), _debug_invar_scale(nullptr),
#endif
  _nstack(nstack), _analyze_only(analyze_only), _stack_idx(0)
#ifndef PRODUCT
  , _tracer((phase->C->directive()->VectorizeDebugOption & 2) > 0)
#endif
{
  NOT_PRODUCT(_tracer.ctor_1(mem);)

  Node* adr = mem->in(MemNode::Address);
  if (!adr->is_AddP()) {
    assert(!valid(), "too complex");
    return;
  }
  // Match AddP(base, AddP(ptr, k*iv [+ invariant]), constant)
  Node* base = adr->in(AddPNode::Base);
  // The base address should be loop invariant
  if (is_loop_member(base)) {
    assert(!valid(), "base address is loop variant");
    return;
  }
  // unsafe references require misaligned vector access support
  if (base->is_top() && !Matcher::misaligned_vectors_ok()) {
    assert(!valid(), "unsafe access");
    return;
  }

  NOT_PRODUCT(if(_tracer._is_trace_alignment) _tracer.store_depth();)
  NOT_PRODUCT(_tracer.ctor_2(adr);)

  int i;
  for (i = 0; ; i++) {
    NOT_PRODUCT(_tracer.ctor_3(adr, i);)

    if (!scaled_iv_plus_offset(adr->in(AddPNode::Offset))) {
      assert(!valid(), "too complex");
      return;
    }
    adr = adr->in(AddPNode::Address);
    NOT_PRODUCT(_tracer.ctor_4(adr, i);)

    if (base == adr || !adr->is_AddP()) {
      NOT_PRODUCT(_tracer.ctor_5(adr, base, i);)
      break; // stop looking at addp's
    }
  }
  if (is_loop_member(adr)) {
    assert(!valid(), "adr is loop variant");
    return;
  }

  if (!base->is_top() && adr != base) {
    assert(!valid(), "adr and base differ");
    return;
  }

  NOT_PRODUCT(if(_tracer._is_trace_alignment) _tracer.restore_depth();)
  NOT_PRODUCT(_tracer.ctor_6(mem);)

  _base = base;
  _adr  = adr;
  assert(valid(), "Usable");
}

// Following is used to create a temporary object during
// the pattern match of an address expression.
VPointer::VPointer(VPointer* p) :
  _mem(p->_mem), _phase(p->_phase), _lpt(p->_lpt), _iv(p->_iv),
  _base(nullptr), _adr(nullptr), _scale(0), _offset(0), _invar(nullptr),
#ifdef ASSERT
  _debug_invar(nullptr), _debug_negate_invar(false), _debug_invar_scale(nullptr),
#endif
  _nstack(p->_nstack), _analyze_only(p->_analyze_only), _stack_idx(p->_stack_idx)
#ifndef PRODUCT
  , _tracer(p->_tracer._is_trace_alignment)
#endif
{}

// Biggest detectable factor of the invariant.
int VPointer::invar_factor() {
  Node* n = invar();
  if (n == nullptr) {
    return 0;
  }
  int opc = n->Opcode();
  if (opc == Op_LShiftI && n->in(2)->is_Con()) {
    return 1 << n->in(2)->get_int();
  } else if (opc == Op_LShiftL && n->in(2)->is_Con()) {
    return 1 << n->in(2)->get_int();
  }
  // All our best-effort has failed.
  return 1;
}

bool VPointer::is_loop_member(Node* n) const {
  Node* n_c = phase()->get_ctrl(n);
  return lpt()->is_member(phase()->get_loop(n_c));
}

bool VPointer::invariant(Node* n) const {
  NOT_PRODUCT(Tracer::Depth dd;)
  bool is_not_member = !is_loop_member(n);
  if (is_not_member) {
    CountedLoopNode* cl = lpt()->_head->as_CountedLoop();
    if (cl->is_main_loop()) {
      // Check that n_c dominates the pre loop head node. If it does not, then
      // we cannot use n as invariant for the pre loop CountedLoopEndNode check
      // because n_c is either part of the pre loop or between the pre and the
      // main loop (Illegal invariant happens when n_c is a CastII node that
      // prevents data nodes to flow above the main loop).
      Node* n_c = phase()->get_ctrl(n);
      return phase()->is_dominator(n_c, cl->pre_loop_head());
    }
  }
  return is_not_member;
}

// Match: k*iv + offset
// where: k is a constant that maybe zero, and
//        offset is (k2 [+/- invariant]) where k2 maybe zero and invariant is optional
bool VPointer::scaled_iv_plus_offset(Node* n) {
  NOT_PRODUCT(Tracer::Depth ddd;)
  NOT_PRODUCT(_tracer.scaled_iv_plus_offset_1(n);)

  if (scaled_iv(n)) {
    NOT_PRODUCT(_tracer.scaled_iv_plus_offset_2(n);)
    return true;
  }

  if (offset_plus_k(n)) {
    NOT_PRODUCT(_tracer.scaled_iv_plus_offset_3(n);)
    return true;
  }

  int opc = n->Opcode();
  if (opc == Op_AddI) {
    if (offset_plus_k(n->in(2)) && scaled_iv_plus_offset(n->in(1))) {
      NOT_PRODUCT(_tracer.scaled_iv_plus_offset_4(n);)
      return true;
    }
    if (offset_plus_k(n->in(1)) && scaled_iv_plus_offset(n->in(2))) {
      NOT_PRODUCT(_tracer.scaled_iv_plus_offset_5(n);)
      return true;
    }
  } else if (opc == Op_SubI || opc == Op_SubL) {
    if (offset_plus_k(n->in(2), true) && scaled_iv_plus_offset(n->in(1))) {
      NOT_PRODUCT(_tracer.scaled_iv_plus_offset_6(n);)
      return true;
    }
    if (offset_plus_k(n->in(1)) && scaled_iv_plus_offset(n->in(2))) {
      _scale *= -1;
      NOT_PRODUCT(_tracer.scaled_iv_plus_offset_7(n);)
      return true;
    }
  }

  NOT_PRODUCT(_tracer.scaled_iv_plus_offset_8(n);)
  return false;
}

// Match: k*iv where k is a constant that's not zero
bool VPointer::scaled_iv(Node* n) {
  NOT_PRODUCT(Tracer::Depth ddd;)
  NOT_PRODUCT(_tracer.scaled_iv_1(n);)

  if (_scale != 0) { // already found a scale
    NOT_PRODUCT(_tracer.scaled_iv_2(n, _scale);)
    return false;
  }

  if (n == iv()) {
    _scale = 1;
    NOT_PRODUCT(_tracer.scaled_iv_3(n, _scale);)
    return true;
  }
  if (_analyze_only && (is_loop_member(n))) {
    _nstack->push(n, _stack_idx++);
  }

  int opc = n->Opcode();
  if (opc == Op_MulI) {
    if (n->in(1) == iv() && n->in(2)->is_Con()) {
      _scale = n->in(2)->get_int();
      NOT_PRODUCT(_tracer.scaled_iv_4(n, _scale);)
      return true;
    } else if (n->in(2) == iv() && n->in(1)->is_Con()) {
      _scale = n->in(1)->get_int();
      NOT_PRODUCT(_tracer.scaled_iv_5(n, _scale);)
      return true;
    }
  } else if (opc == Op_LShiftI) {
    if (n->in(1) == iv() && n->in(2)->is_Con()) {
      _scale = 1 << n->in(2)->get_int();
      NOT_PRODUCT(_tracer.scaled_iv_6(n, _scale);)
      return true;
    }
  } else if (opc == Op_ConvI2L || opc == Op_CastII) {
    if (scaled_iv_plus_offset(n->in(1))) {
      NOT_PRODUCT(_tracer.scaled_iv_7(n);)
      return true;
    }
  } else if (opc == Op_LShiftL && n->in(2)->is_Con()) {
    if (!has_iv()) {
      // Need to preserve the current _offset value, so
      // create a temporary object for this expression subtree.
      // Hacky, so should re-engineer the address pattern match.
      NOT_PRODUCT(Tracer::Depth dddd;)
      VPointer tmp(this);
      NOT_PRODUCT(_tracer.scaled_iv_8(n, &tmp);)

      if (tmp.scaled_iv_plus_offset(n->in(1))) {
        int scale = n->in(2)->get_int();
        _scale   = tmp._scale  << scale;
        _offset += tmp._offset << scale;
        if (tmp._invar != nullptr) {
          BasicType bt = tmp._invar->bottom_type()->basic_type();
          assert(bt == T_INT || bt == T_LONG, "");
          maybe_add_to_invar(register_if_new(LShiftNode::make(tmp._invar, n->in(2), bt)), false);
#ifdef ASSERT
          _debug_invar_scale = n->in(2);
#endif
        }
        NOT_PRODUCT(_tracer.scaled_iv_9(n, _scale, _offset, _invar);)
        return true;
      }
    }
  }
  NOT_PRODUCT(_tracer.scaled_iv_10(n);)
  return false;
}

// Match: offset is (k [+/- invariant])
// where k maybe zero and invariant is optional, but not both.
bool VPointer::offset_plus_k(Node* n, bool negate) {
  NOT_PRODUCT(Tracer::Depth ddd;)
  NOT_PRODUCT(_tracer.offset_plus_k_1(n);)

  int opc = n->Opcode();
  if (opc == Op_ConI) {
    _offset += negate ? -(n->get_int()) : n->get_int();
    NOT_PRODUCT(_tracer.offset_plus_k_2(n, _offset);)
    return true;
  } else if (opc == Op_ConL) {
    // Okay if value fits into an int
    const TypeLong* t = n->find_long_type();
    if (t->higher_equal(TypeLong::INT)) {
      jlong loff = n->get_long();
      jint  off  = (jint)loff;
      _offset += negate ? -off : loff;
      NOT_PRODUCT(_tracer.offset_plus_k_3(n, _offset);)
      return true;
    }
    NOT_PRODUCT(_tracer.offset_plus_k_4(n);)
    return false;
  }
  assert((_debug_invar == nullptr) == (_invar == nullptr), "");

  if (_analyze_only && is_loop_member(n)) {
    _nstack->push(n, _stack_idx++);
  }
  if (opc == Op_AddI) {
    if (n->in(2)->is_Con() && invariant(n->in(1))) {
      maybe_add_to_invar(n->in(1), negate);
      _offset += negate ? -(n->in(2)->get_int()) : n->in(2)->get_int();
      NOT_PRODUCT(_tracer.offset_plus_k_6(n, _invar, negate, _offset);)
      return true;
    } else if (n->in(1)->is_Con() && invariant(n->in(2))) {
      _offset += negate ? -(n->in(1)->get_int()) : n->in(1)->get_int();
      maybe_add_to_invar(n->in(2), negate);
      NOT_PRODUCT(_tracer.offset_plus_k_7(n, _invar, negate, _offset);)
      return true;
    }
  }
  if (opc == Op_SubI) {
    if (n->in(2)->is_Con() && invariant(n->in(1))) {
      maybe_add_to_invar(n->in(1), negate);
      _offset += !negate ? -(n->in(2)->get_int()) : n->in(2)->get_int();
      NOT_PRODUCT(_tracer.offset_plus_k_8(n, _invar, negate, _offset);)
      return true;
    } else if (n->in(1)->is_Con() && invariant(n->in(2))) {
      _offset += negate ? -(n->in(1)->get_int()) : n->in(1)->get_int();
      maybe_add_to_invar(n->in(2), !negate);
      NOT_PRODUCT(_tracer.offset_plus_k_9(n, _invar, !negate, _offset);)
      return true;
    }
  }

  if (!is_loop_member(n)) {
    // 'n' is loop invariant. Skip ConvI2L and CastII nodes before checking if 'n' is dominating the pre loop.
    if (opc == Op_ConvI2L) {
      n = n->in(1);
    }
    if (n->Opcode() == Op_CastII) {
      // Skip CastII nodes
      assert(!is_loop_member(n), "sanity");
      n = n->in(1);
    }
    // Check if 'n' can really be used as invariant (not in main loop and dominating the pre loop).
    if (invariant(n)) {
      maybe_add_to_invar(n, negate);
      NOT_PRODUCT(_tracer.offset_plus_k_10(n, _invar, negate, _offset);)
      return true;
    }
  }

  NOT_PRODUCT(_tracer.offset_plus_k_11(n);)
  return false;
}

Node* VPointer::maybe_negate_invar(bool negate, Node* invar) {
#ifdef ASSERT
  _debug_negate_invar = negate;
#endif
  if (negate) {
    BasicType bt = invar->bottom_type()->basic_type();
    assert(bt == T_INT || bt == T_LONG, "");
    PhaseIterGVN& igvn = phase()->igvn();
    Node* zero = igvn.zerocon(bt);
    phase()->set_ctrl(zero, phase()->C->root());
    Node* sub = SubNode::make(zero, invar, bt);
    invar = register_if_new(sub);
  }
  return invar;
}

Node* VPointer::register_if_new(Node* n) const {
  PhaseIterGVN& igvn = phase()->igvn();
  Node* prev = igvn.hash_find_insert(n);
  if (prev != nullptr) {
    n->destruct(&igvn);
    n = prev;
  } else {
    Node* c = phase()->get_early_ctrl(n);
    phase()->register_new_node(n, c);
  }
  return n;
}

void VPointer::maybe_add_to_invar(Node* new_invar, bool negate) {
  new_invar = maybe_negate_invar(negate, new_invar);
  if (_invar == nullptr) {
    _invar = new_invar;
#ifdef ASSERT
    _debug_invar = new_invar;
#endif
    return;
  }
#ifdef ASSERT
  _debug_invar = NodeSentinel;
#endif
  BasicType new_invar_bt = new_invar->bottom_type()->basic_type();
  assert(new_invar_bt == T_INT || new_invar_bt == T_LONG, "");
  BasicType invar_bt = _invar->bottom_type()->basic_type();
  assert(invar_bt == T_INT || invar_bt == T_LONG, "");

  BasicType bt = (new_invar_bt == T_LONG || invar_bt == T_LONG) ? T_LONG : T_INT;
  Node* current_invar = _invar;
  if (invar_bt != bt) {
    assert(bt == T_LONG && invar_bt == T_INT, "");
    assert(new_invar_bt == bt, "");
    current_invar = register_if_new(new ConvI2LNode(current_invar));
  } else if (new_invar_bt != bt) {
    assert(bt == T_LONG && new_invar_bt == T_INT, "");
    assert(invar_bt == bt, "");
    new_invar = register_if_new(new ConvI2LNode(new_invar));
  }
  Node* add = AddNode::make(current_invar, new_invar, bt);
  _invar = register_if_new(add);
}

// Function for printing the fields of a VPointer
void VPointer::print() {
#ifndef PRODUCT
  tty->print("base: [%d]  adr: [%d]  scale: %d  offset: %d",
             _base != nullptr ? _base->_idx : 0,
             _adr  != nullptr ? _adr->_idx  : 0,
             _scale, _offset);
  if (_invar != nullptr) {
    tty->print("  invar: [%d]", _invar->_idx);
  }
  tty->cr();
#endif
}

// Following are functions for tracing VPointer match
#ifndef PRODUCT
void VPointer::Tracer::print_depth() const {
  for (int ii = 0; ii < _depth; ++ii) {
    tty->print("  ");
  }
}

void VPointer::Tracer::ctor_1(const Node* mem) {
  if (_is_trace_alignment) {
    print_depth(); tty->print(" %d VPointer::VPointer: start alignment analysis", mem->_idx); mem->dump();
  }
}

void VPointer::Tracer::ctor_2(Node* adr) {
  if (_is_trace_alignment) {
    //store_depth();
    inc_depth();
    print_depth(); tty->print(" %d (adr) VPointer::VPointer: ", adr->_idx); adr->dump();
    inc_depth();
    print_depth(); tty->print(" %d (base) VPointer::VPointer: ", adr->in(AddPNode::Base)->_idx); adr->in(AddPNode::Base)->dump();
  }
}

void VPointer::Tracer::ctor_3(Node* adr, int i) {
  if (_is_trace_alignment) {
    inc_depth();
    Node* offset = adr->in(AddPNode::Offset);
    print_depth(); tty->print(" %d (offset) VPointer::VPointer: i = %d: ", offset->_idx, i); offset->dump();
  }
}

void VPointer::Tracer::ctor_4(Node* adr, int i) {
  if (_is_trace_alignment) {
    inc_depth();
    print_depth(); tty->print(" %d (adr) VPointer::VPointer: i = %d: ", adr->_idx, i); adr->dump();
  }
}

void VPointer::Tracer::ctor_5(Node* adr, Node* base, int i) {
  if (_is_trace_alignment) {
    inc_depth();
    if (base == adr) {
      print_depth(); tty->print_cr("  \\ %d (adr) == %d (base) VPointer::VPointer: breaking analysis at i = %d", adr->_idx, base->_idx, i);
    } else if (!adr->is_AddP()) {
      print_depth(); tty->print_cr("  \\ %d (adr) is NOT Addp VPointer::VPointer: breaking analysis at i = %d", adr->_idx, i);
    }
  }
}

void VPointer::Tracer::ctor_6(const Node* mem) {
  if (_is_trace_alignment) {
    //restore_depth();
    print_depth(); tty->print_cr(" %d (adr) VPointer::VPointer: stop analysis", mem->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_1(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print(" %d VPointer::scaled_iv_plus_offset testing node: ", n->_idx);
    n->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_2(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: PASSED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_3(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: PASSED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_4(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_AddI PASSED", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is scaled_iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is offset_plus_k: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_5(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_AddI PASSED", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is scaled_iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is offset_plus_k: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_6(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_%s PASSED", n->_idx, n->Name());
    print_depth(); tty->print("  \\  %d VPointer::scaled_iv_plus_offset: in(1) is scaled_iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is offset_plus_k: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_7(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_%s PASSED", n->_idx, n->Name());
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is scaled_iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is offset_plus_k: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_8(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: FAILED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_1(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print(" %d VPointer::scaled_iv: testing node: ", n->_idx); n->dump();
  }
}

void VPointer::Tracer::scaled_iv_2(Node* n, int scale) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: FAILED since another _scale has been detected before", n->_idx);
    print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: _scale (%d) != 0", scale);
  }
}

void VPointer::Tracer::scaled_iv_3(Node* n, int scale) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: is iv, setting _scale = %d", n->_idx, scale);
  }
}

void VPointer::Tracer::scaled_iv_4(Node* n, int scale) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_MulI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_5(Node* n, int scale) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_MulI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_6(Node* n, int scale) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_LShiftI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_7(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_ConvI2L PASSED", n->_idx);
    print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: in(1) %d is scaled_iv_plus_offset: ", n->in(1)->_idx);
    inc_depth(); inc_depth();
    print_depth(); n->in(1)->dump();
    dec_depth(); dec_depth();
  }
}

void VPointer::Tracer::scaled_iv_8(Node* n, VPointer* tmp) {
  if (_is_trace_alignment) {
    print_depth(); tty->print(" %d VPointer::scaled_iv: Op_LShiftL, creating tmp VPointer: ", n->_idx); tmp->print();
  }
}

void VPointer::Tracer::scaled_iv_9(Node* n, int scale, int offset, Node* invar) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_LShiftL PASSED, setting _scale = %d, _offset = %d", n->_idx, scale, offset);
    print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: in(1) [%d] is scaled_iv_plus_offset, in(2) [%d] used to scale: _scale = %d, _offset = %d",
    n->in(1)->_idx, n->in(2)->_idx, scale, offset);
    if (invar != nullptr) {
      print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: scaled invariant: [%d]", invar->_idx);
    }
    inc_depth(); inc_depth();
    print_depth(); n->in(1)->dump();
    print_depth(); n->in(2)->dump();
    if (invar != nullptr) {
      print_depth(); invar->dump();
    }
    dec_depth(); dec_depth();
  }
}

void VPointer::Tracer::scaled_iv_10(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: FAILED", n->_idx);
  }
}

void VPointer::Tracer::offset_plus_k_1(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print(" %d VPointer::offset_plus_k: testing node: ", n->_idx); n->dump();
  }
}

void VPointer::Tracer::offset_plus_k_2(Node* n, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_ConI PASSED, setting _offset = %d", n->_idx, _offset);
  }
}

void VPointer::Tracer::offset_plus_k_3(Node* n, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_ConL PASSED, setting _offset = %d", n->_idx, _offset);
  }
}

void VPointer::Tracer::offset_plus_k_4(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED", n->_idx);
    print_depth(); tty->print_cr("  \\ " JLONG_FORMAT " VPointer::offset_plus_k: Op_ConL FAILED, k is too big", n->get_long());
  }
}

void VPointer::Tracer::offset_plus_k_5(Node* n, Node* _invar) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED since another invariant has been detected before", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: _invar is not null: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_6(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_AddI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_7(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_AddI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_8(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_SubI is PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_9(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_SubI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d", n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_10(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d", n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print_cr("  \\ %d VPointer::offset_plus_k: is invariant", n->_idx);
  }
}

void VPointer::Tracer::offset_plus_k_11(Node* n) {
  if (_is_trace_alignment) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED", n->_idx);
  }
}
#endif


AlignmentSolution* AlignmentSolver::solve() const {
  DEBUG_ONLY( trace_start_solve(); )

  // Out of simplicity: non power-of-2 stride not supported.
  if (!is_power_of_2(abs(_pre_stride))) {
    return new AlignmentSolutionEmpty("non power-of-2 stride not supported");
  }
  assert(is_power_of_2(abs(_main_stride)), "main_stride is power of 2");
  assert(_aw > 0 && is_power_of_2(_aw), "aw must be power of 2");

  // Out of simplicity: non power-of-2 scale not supported.
  if (abs(_scale) == 0 || !is_power_of_2(abs(_scale))) {
    return new AlignmentSolutionEmpty("non power-of-2 scale not supported");
  }

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
  // init:        value before pre-loop
  // pre_stride:  increment per pre-loop iteration
  // pre_iter:    number of pre-loop iterations (adjustable via pre-loop limit)
  // main_stride: increment per main-loop iteration (= pre_stride * unroll_factor)
  // main_iter:   number of main-loop iterations (main_iter >= 0)
  //
  // In the following, we restate the simple form of the address expression, by first
  // expanding the iv variable. In a second step, we reshape the expression again, and
  // state it as a linear expression, consisting of 6 terms.
  //
  //          Simple form           Expansion of iv variable                  Reshaped with constants   Comments for terms
  //          -----------           ------------------------                  -----------------------   ------------------
  //   adr =  base               =  base                                   =  base                      (base % aw = 0)
  //        + offset              + offset                                  + C_const                   (sum of constant terms)
  //        + invar               + invar_factor * var_invar                + C_invar * var_invar       (term for invariant)
  //                          /   + scale * init                            + C_init  * var_init        (term for variable init)
  //        + scale * iv   -> |   + scale * pre_stride * pre_iter           + C_pre   * pre_iter        (adjustable pre-loop term)
  //                          \   + scale * main_stride * main_iter         + C_main  * main_iter       (main-loop term)
  //
  // We describe the 6 terms:
  //   1) The "base" of the address is the address of a Java object (e.g. array),
  //      and hence can be assumed to already be aw-aligned (base % aw = 0).
  //   2) The "C_const" term is the sum of all constant terms. This is "offset",
  //      plus "init" if it is constant.
  //   3) The "C_invar * var_invar" is the factorization of "invar" into a constant
  //      and variable term. If there is no invariant, then "C_invar" is zero.
  //   4) The "C_init * var_init" is the factorization of "scale * init" into a
  //      constant and a variable term. If "init" is constant, then "C_init" is
  //      zero, and "C_const" accounts for "init" instead.
  //   5) The "C_pre * pre_iter" term represents how much the iv is incremented
  //      during the "pre_iter" pre-loop iterations. This term can be adjusted
  //      by changing the pre-loop limit, which defines how many pre-loop iterations
  //      are executed. This allows us to adjust the alignment of the main-loop
  //      memory reference.
  //   6) The "C_main * main_iter" term represents how much the iv is increased
  //      during "main_iter" main-loop iterations.

  // Attribute init either to C_const or to C_init term.
  const int C_const_init = _init_node->is_ConI() ? _init_node->as_ConI()->get_int() : 0;
  const int C_init =       _init_node->is_ConI() ? 0                                : _scale;

  // Set C_invar depending on if invar is present
  const int C_invar = (_invar == nullptr) ? 0 : abs(_invar_factor);

  const int C_const = _offset + C_const_init * _scale;
  const int C_pre = _scale * _pre_stride;
  const int C_main = _scale * _main_stride;

  DEBUG_ONLY( trace_reshaped_form(C_const, C_const_init, C_invar, C_init, C_pre, C_main); )

  // We must find a pre_iter, such that adr is aw aligned: adr % aw = 0.
  // Since "base % aw = 0", we only need to ensure alignment of the other 5 terms:
  //
  //   (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter + C_main * main_iter) % aw = 0      (1)
  //
  // Alignment must be maintained over all main-loop iterations, i.e. for any main_iter >= 0, we require:
  //
  //   C_main % aw = 0                                                                                           (2*)
  //
  const int C_main_mod_aw = AlignmentSolution::mod(C_main, _aw);

  DEBUG_ONLY( trace_main_iteration_alignment(C_const, C_invar, C_init, C_pre, C_main, C_main_mod_aw); )

  if (C_main_mod_aw != 0) {
    return new AlignmentSolutionEmpty("EQ(2*) not satisfied (cannot align across main-loop iterations)");
  }

  // In what follows, we need to show that the C_const, init and invar terms can be aligned by
  // adjusting the pre-loop limit (pre_iter). We decompose pre_iter:
  //
  //   pre_iter = pre_iter_C_const + pre_iter_C_invar + pre_iter_C_init
  //
  // where pre_iter_C_const, pre_iter_C_invar, and pre_iter_C_init are defined as the number of
  // pre-loop iterations required to align the C_const, init and invar terms individually.
  // Hence, we can rewrite:
  //
  //     (C_const + C_invar * var_invar + C_init * var_init + C_pre * pre_iter) % aw
  //   = ( C_const             + C_pre * pre_iter_C_const
  //     + C_invar * var_invar + C_pre * pre_iter_C_invar
  //     + C_init  * var_init  + C_pre * pre_iter_C_init ) % aw
  //   = 0                                                                       (3)
  //
  // We strengthen the constraints by splitting the equation into 3 equations, where the C_const,
  // init, and invar term are aligned individually:
  //
  //   (C_init  * var_init  + C_pre * pre_iter_C_init ) % aw = 0                 (4a)
  //   (C_invar * var_invar + C_pre * pre_iter_C_invar) % aw = 0                 (4b)
  //   (C_const             + C_pre * pre_iter_C_const) % aw = 0                 (4c)
  //
  // We can only guarantee solutions to (4a) and (4b) if:
  //
  //   C_init  % abs(C_pre) = 0                                                  (5a*)
  //   C_invar % abs(C_pre) = 0                                                  (5b*)
  //
  // Which means there are X and Y such that:
  //
  //   C_init  = C_pre * X       (X = 0 if C_init  = 0, else X = C_init  / C_pre)
  //   C_invar = C_pre * Y       (Y = 0 if C_invar = 0, else Y = C_invar / C_pre)
  //
  //   (C_init    * var_init  + C_pre * pre_iter_C_init ) % aw =
  //   (C_pre * X * var_init  + C_pre * pre_iter_C_init ) % aw =
  //   (C_pre * (X * var_init  + pre_iter_C_init)       ) % aw = 0
  //
  //   (C_invar   * var_invar + C_pre * pre_iter_C_invar) % aw =
  //   (C_pre * Y * var_invar + C_pre * pre_iter_C_invar) % aw =
  //   (C_pre * (Y * var_invar + pre_iter_C_invar)      ) % aw = 0
  //
  // And hence, we know that there are solutions for pre_iter_C_init and pre_iter_C_invar,
  // based on X, Y, var_init, and var_invar. We call them:
  //
  //   pre_iter_C_init  = alignment_init (X * var_init)
  //   pre_iter_C_invar = alignment_invar(Y * var_invar)
  //
  const int C_init_mod_abs_C_pre  = AlignmentSolution::mod(C_init,  abs(C_pre));
  const int C_invar_mod_abs_C_pre = AlignmentSolution::mod(C_invar, abs(C_pre));

  DEBUG_ONLY( trace_init_and_invar_alignment(C_invar, C_init, C_pre, C_invar_mod_abs_C_pre, C_init_mod_abs_C_pre); )

  if (C_init_mod_abs_C_pre != 0) {
    return new AlignmentSolutionEmpty("EQ(5a*) not satisfied (cannot align init)");
  }
  if (C_invar_mod_abs_C_pre != 0) {
    return new AlignmentSolutionEmpty("EQ(5b*) not satisfied (cannot align invar)");
  }

  // Having solved (4a) and (4b), we now want to find solutions for (4c), i.e. we need
  // to show that the C_const term can be aligned with pre_iter_C_const.
  //
  // We can assume that abs(C_pre) as well as aw are both powers of 2.
  //
  // If abs(C_pre) >= aw, then:
  //
  //   for any pre_iter >= 0:         (C_pre * pre_iter        ) % aw = 0
  //   for any pre_iter_C_const >= 0: (C_pre * pre_iter_C_const) % aw = 0
  //
  // which implies that pre_iter (and pre_iter_C_const) have no effect on the alignment of
  // the C_const term. We thus either have a trivial solution, and any pre_iter aligns
  // the address, or there is no solution. To have the trivial solution, we require:
  //
  //   C_const % aw = 0                                                       (6*)
  //
  assert(abs(C_pre) > 0 && is_power_of_2(abs(C_pre)), "abs(C_pre) must be power of 2");
  const bool abs_C_pre_ge_aw = abs(C_pre) >= _aw;

  DEBUG_ONLY( trace_abs_C_pre_ge_aw(C_pre, abs_C_pre_ge_aw); )

  if (abs_C_pre_ge_aw) {
    const int C_const_mod_aw = AlignmentSolution::mod(C_const, _aw);

    DEBUG_ONLY( trace_C_const_mod_aw(C_const, C_const_mod_aw); )

    // The C_init and C_invar terms are trivially aligned.
    assert(AlignmentSolution::mod(C_init,  _aw) == 0,  "implied by abs(C_pre) >= aw and (5a*)");
    assert(AlignmentSolution::mod(C_invar, _aw) == 0,  "implied by abs(C_pre) >= aw and (5b*)");

    if (C_const_mod_aw != 0) {
      return new AlignmentSolutionEmpty("EQ(6*) not satisfied: C_const not aligned");
    } else {
      // Solution is trivial, holds for any pre-loop limit.
      return new AlignmentSolutionTrivial();
    }
  }

  // Otherwise, if abs(C_pre) < aw, we find all solutions for pre_iter_C_const in (4c).
  // We state pre_iter_C_const in terms of the smallest possible pre_q and pre_r, such
  // that pre_q >= 0 and 0 <= pre_r < pre_q:
  //
  //   pre_iter_C_const = pre_r + pre_q * m  (for any m >= 0)                     (7)
  //
  // We can now restate (4c) with (7):
  //
  //   (C_const + C_pre * pre_r + C_pre * pre_q * m) % aw = 0                     (8)
  //
  // Since this holds for any m >= 0, we require:
  //
  //   (C_pre * pre_q) % aw = 0                                                   (9)
  //   (C_const + C_pre * pre_r) % aw = 0                                         (10*)
  //
  // Given that abs(C_pre) is a powers of 2, and abs(C_pre) < aw:
  //
  const int  pre_q = _aw / abs(C_pre);
  //
  // We brute force the solution for pre_r by enumerating all values 0..pre_q-1 and
  // checking EQ(10*).
  //
  // Assuming we found a solution for (4c), and also for (4a) and (4b), we know that
  // the solution to pre_iter is non-trivial:
  //
  //   pre_iter = pre_iter_C_const  + pre_iter_C_init              + pre_iter_C_invar
  //            = pre_r + pre_q * m + alignment_init(X * var_init) + alignment_invar(Y * var_invar)
  //
  // Hence, the solution depends on:
  //   - Always: pre_r and pre_q
  //   - If a variable init is present (i.e. C_init = scale), then we know that to
  //     satisfy (5a*), we must have abs(pre_stride) = 1, X = 1 and C_pre = scale.
  //     The solution thus depends on var_init = init / scale. We thus have a
  //     dependency on scale. We could also add a dependency for init, but since
  //     it is the same for all mem_refs in the loop this is unnecessary. If init
  //     is constant, then we could add a dependency that there is no variable init.
  //     But since init is the same for all mem_refs, this is unecessary.
  //   - If an invariant is present (i.e. C_invar = abs(invar_factor)), then we know
  //     from (5b*), that Y = abs(invar_factor) / (scale * pre_stride). The solution
  //     depends on Y * var_invar = abs(invar_factor) * var_invar / (scale * pre_stride),
  //     hence we have to add a dependency for invar, and scale (pre_stride is the
  //     same for all mem_refs in the loop). If there is no invariant, then we add
  //     a dependency that there is no invariant.
  //
  // Other mem_refs must have solutions with the same dependencies, otherwise we
  // cannot ensure that they require the same number of pre-loop iterations.

  DEBUG_ONLY( trace_find_pre_q(C_const, C_pre, pre_q); )

  for (int pre_r = 0; pre_r < pre_q; pre_r++) {
    const int eq10_val = AlignmentSolution::mod(C_const + C_pre * pre_r, _aw);

    DEBUG_ONLY( trace_find_pre_r(C_const, C_pre, pre_q, pre_r, eq10_val); )

    if (eq10_val == 0) {
      assert((C_init == 0) == _init_node->is_ConI(), "init consistent");
      assert((C_invar == 0) == (_invar == nullptr), "invar consistent");

      const Node* invar_dependency = _invar;
      const int scale_dependency  = (_invar != nullptr || !_init_node->is_ConI()) ? _scale : 0;
      return new AlignmentSolutionConstrained(pre_r, pre_q, _mem_ref, _aw,
                                              invar_dependency, scale_dependency);
    }
  }
  return new AlignmentSolutionEmpty("EQ(10*) has no solution for pre_r");
}

#ifdef ASSERT
void print_icon_or_idx(const Node* n) {
  if (n == nullptr) {
    tty->print("(0)");
  } else if (n->is_ConI()) {
    jint val = n->as_ConI()->get_int();
    tty->print("(%d)", val);
  } else {
    tty->print("[%d]", n->_idx);
  }
}

void AlignmentSolver::trace_start_solve() const {
  if (is_trace()) {
    tty->print(" vector mem_ref:");
    _mem_ref->dump();
    tty->print_cr("  vector_width = vector_length(%d) * element_size(%d) = %d",
                  _vector_length, _element_size, _vector_width);
    tty->print_cr("  aw = alignment_width = min(vector_width(%d), ObjectAlignmentInBytes(%d)) = %d",
                  _vector_width, ObjectAlignmentInBytes, _aw);

    if (!_init_node->is_ConI()) {
      tty->print("  init:");
      _init_node->dump();
    }

    if (_invar != nullptr) {
      tty->print("  invar:");
      _invar->dump();
    }

    tty->print_cr("  invar_factor = %d", _invar_factor);

    // iv = init + pre_iter * pre_stride + main_iter * main_stride
    tty->print("  iv = init");
    print_icon_or_idx(_init_node);
    tty->print_cr(" + pre_iter * pre_stride(%d) + main_iter * main_stride(%d)",
                  _pre_stride, _main_stride);

    // adr = base + offset + invar + scale * iv
    tty->print("  adr = base");
    print_icon_or_idx(_base);
    tty->print(" + offset(%d) + invar", _offset);
    print_icon_or_idx(_invar);
    tty->print_cr(" + scale(%d) * iv", _scale);
  }
}

void AlignmentSolver::trace_reshaped_form(const int C_const,
                                          const int C_const_init,
                                          const int C_invar,
                                          const int C_init,
                                          const int C_pre,
                                          const int C_main) const
{
  if (is_trace()) {
    tty->print("      = base[%d] + ", _base->_idx);
    tty->print_cr("C_const(%d) + C_invar(%d) * var_invar + C_init(%d) * var_init + C_pre(%d) * pre_iter + C_main(%d) * main_iter",
                  C_const, C_invar, C_init,  C_pre, C_main);
    if (_init_node->is_ConI()) {
      tty->print_cr("  init is constant:");
      tty->print_cr("    C_const_init = %d", C_const_init);
      tty->print_cr("    C_init = %d", C_init);
    } else {
      tty->print_cr("  init is variable:");
      tty->print_cr("    C_const_init = %d", C_const_init);
      tty->print_cr("    C_init = abs(scale)= %d", C_init);
    }
    if (_invar != nullptr) {
      tty->print_cr("  invariant present:");
      tty->print_cr("    C_invar = abs(invar_factor) = %d", C_invar);
    } else {
      tty->print_cr("  no invariant:");
      tty->print_cr("    C_invar = %d", C_invar);
    }
    tty->print_cr("  C_const = offset(%d) + scale(%d) * C_const_init(%d) = %d",
                  _offset, _scale, C_const_init, C_const);
    tty->print_cr("  C_pre   = scale(%d) * pre_stride(%d) = %d",
                  _scale, _pre_stride, C_pre);
    tty->print_cr("  C_main  = scale(%d) * main_stride(%d) = %d",
                  _scale, _main_stride, C_main);
  }
}

void AlignmentSolver::trace_main_iteration_alignment(const int C_const,
                                                     const int C_invar,
                                                     const int C_init,
                                                     const int C_pre,
                                                     const int C_main,
                                                     const int C_main_mod_aw) const
{
  if (is_trace()) {
    tty->print("  EQ(1  ): (C_const(%d) + C_invar(%d) * var_invar + C_init(%d) * var_init",
                  C_const, C_invar, C_init);
    tty->print(" + C_pre(%d) * pre_iter + C_main(%d) * main_iter) %% aw(%d) = 0",
                  C_pre, C_main, _aw);
    tty->print_cr(" (given base aligned -> align rest)");
    tty->print("  EQ(2* ): C_main(%d) %% aw(%d) = %d = 0",
               C_main, _aw, C_main_mod_aw);
    tty->print_cr(" (alignment across iterations)");
  }
}

void AlignmentSolver::trace_init_and_invar_alignment(const int C_invar,
                                                     const int C_init,
                                                     const int C_pre,
                                                     const int C_invar_mod_abs_C_pre,
                                                     const int C_init_mod_abs_C_pre) const
{
  if (is_trace()) {
    tty->print_cr("  EQ(5a*): C_init(%d) %% abs(C_pre(%d)) = %d = 0   (if false: cannot align init)",
                  C_init, C_pre, C_init_mod_abs_C_pre);
    tty->print_cr("  EQ(5b*): C_invar(%d) %% abs(C_pre(%d)) = %d = 0  (if false: cannot align invar)",
                  C_invar, C_pre, C_invar_mod_abs_C_pre);
  }
}

void AlignmentSolver::trace_abs_C_pre_ge_aw(const int C_pre,
                                            const bool abs_C_pre_ge_aw) const
{
  if (is_trace()) {
    tty->print_cr("  abs(C_pre(%d)) >= aw(%d) -> %s", C_pre, _aw,
                  abs_C_pre_ge_aw ? "true (pre-loop limit adjustment makes no difference)" :
                                    "false (pre-loop limit adjustment changes alignment)");
  }
}

void AlignmentSolver::trace_C_const_mod_aw(const int C_const,
                                           const int C_const_mod_aw) const
{
  if (is_trace()) {
      tty->print_cr("  EQ(6* ): C_const(%d) %% aw(%d) = %d = 0 (if true: trivial, else no solution)",
                    C_const, _aw, C_const_mod_aw);
  }
}

void AlignmentSolver::trace_find_pre_q(const int C_const,
                                       const int C_pre,
                                       const int pre_q) const
{
  if (is_trace()) {
    tty->print_cr("  Find alignment for C_const(%d), with:", C_const);
    tty->print_cr("  pre_iter_C_const = pre_r + pre_q * m  (for any m >= 0)");
    tty->print_cr("  (C_const(%d) + C_pre(%d) * pre_r + C_pre(%d) * pre_q * m) %% aw(%d) = 0:",
                  C_const, C_pre, C_pre, _aw);
    tty->print_cr("  pre_q = aw(%d) / abs(C_pre(%d)) = %d",
                  _aw, C_pre, pre_q);
    tty->print_cr("  EQ(10*): brute force pre_r = 0..%d", pre_q - 1);
  }
}

void AlignmentSolver::trace_find_pre_r(const int C_const,
                                       const int C_pre,
                                       const int pre_q,
                                       const int pre_r,
                                       const int eq10_val) const
{
  if (is_trace()) {
      tty->print_cr("   try pre_r = %d: (C_const(%d) + C_pre(%d) * pre_r(%d)) %% aw(%d) = %d = 0",
                    pre_r, C_const, C_pre, pre_r, _aw, eq10_val);
  }
}
#endif
