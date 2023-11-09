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

VPointer::VPointer(MemNode* mem, PhaseIdealLoop* phase, IdealLoopTree* lpt,
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

void VPointer::Tracer::ctor_1(Node* mem) {
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

void VPointer::Tracer::ctor_6(Node* mem) {
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
