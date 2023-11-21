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
#include "opto/vectornode.hpp"

#ifndef PRODUCT
int VPointer::Tracer::_depth = 0;
#endif

VPointer::VPointer(MemNode* mem, const VLoop& vloop,
                   Node_Stack* nstack, bool analyze_only) :
  _mem(mem), _vloop(vloop),
  _base(nullptr), _adr(nullptr), _scale(0), _offset(0), _invar(nullptr),
#ifdef ASSERT
  _debug_invar(nullptr), _debug_negate_invar(false), _debug_invar_scale(nullptr),
#endif
  _nstack(nstack), _analyze_only(analyze_only), _stack_idx(0)
#ifndef PRODUCT
  , _tracer(_vloop)
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

  NOT_PRODUCT(if(_tracer.is_trace_pointer_analysis()) _tracer.store_depth();)
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

  NOT_PRODUCT(if(_tracer.is_trace_pointer_analysis()) _tracer.restore_depth();)
  NOT_PRODUCT(_tracer.ctor_6(mem);)

  _base = base;
  _adr  = adr;
  assert(valid(), "Usable");
}

// Following is used to create a temporary object during
// the pattern match of an address expression.
VPointer::VPointer(VPointer* p) :
  _mem(p->_mem), _vloop(p->_vloop),
  _base(nullptr), _adr(nullptr), _scale(0), _offset(0), _invar(nullptr),
#ifdef ASSERT
  _debug_invar(nullptr), _debug_negate_invar(false), _debug_invar_scale(nullptr),
#endif
  _nstack(p->_nstack), _analyze_only(p->_analyze_only), _stack_idx(p->_stack_idx)
#ifndef PRODUCT
  , _tracer(_vloop)
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
      return phase()->is_dominator(n_c, vloop().pre_loop_head());
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
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print(" %d VPointer::VPointer: start alignment analysis", mem->_idx); mem->dump();
  }
}

void VPointer::Tracer::ctor_2(Node* adr) {
  if (is_trace_pointer_analysis()) {
    //store_depth();
    inc_depth();
    print_depth(); tty->print(" %d (adr) VPointer::VPointer: ", adr->_idx); adr->dump();
    inc_depth();
    print_depth(); tty->print(" %d (base) VPointer::VPointer: ", adr->in(AddPNode::Base)->_idx); adr->in(AddPNode::Base)->dump();
  }
}

void VPointer::Tracer::ctor_3(Node* adr, int i) {
  if (is_trace_pointer_analysis()) {
    inc_depth();
    Node* offset = adr->in(AddPNode::Offset);
    print_depth(); tty->print(" %d (offset) VPointer::VPointer: i = %d: ", offset->_idx, i); offset->dump();
  }
}

void VPointer::Tracer::ctor_4(Node* adr, int i) {
  if (is_trace_pointer_analysis()) {
    inc_depth();
    print_depth(); tty->print(" %d (adr) VPointer::VPointer: i = %d: ", adr->_idx, i); adr->dump();
  }
}

void VPointer::Tracer::ctor_5(Node* adr, Node* base, int i) {
  if (is_trace_pointer_analysis()) {
    inc_depth();
    if (base == adr) {
      print_depth(); tty->print_cr("  \\ %d (adr) == %d (base) VPointer::VPointer: breaking analysis at i = %d", adr->_idx, base->_idx, i);
    } else if (!adr->is_AddP()) {
      print_depth(); tty->print_cr("  \\ %d (adr) is NOT Addp VPointer::VPointer: breaking analysis at i = %d", adr->_idx, i);
    }
  }
}

void VPointer::Tracer::ctor_6(Node* mem) {
  if (is_trace_pointer_analysis()) {
    //restore_depth();
    print_depth(); tty->print_cr(" %d (adr) VPointer::VPointer: stop analysis", mem->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_1(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print(" %d VPointer::scaled_iv_plus_offset testing node: ", n->_idx);
    n->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_2(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: PASSED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_3(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: PASSED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_4(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_AddI PASSED", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is scaled_iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is offset_plus_k: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_5(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_AddI PASSED", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is scaled_iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is offset_plus_k: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_6(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_%s PASSED", n->_idx, n->Name());
    print_depth(); tty->print("  \\  %d VPointer::scaled_iv_plus_offset: in(1) is scaled_iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is offset_plus_k: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_7(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: Op_%s PASSED", n->_idx, n->Name());
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(2) is scaled_iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv_plus_offset: in(1) is offset_plus_k: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_plus_offset_8(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv_plus_offset: FAILED", n->_idx);
  }
}

void VPointer::Tracer::scaled_iv_1(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print(" %d VPointer::scaled_iv: testing node: ", n->_idx); n->dump();
  }
}

void VPointer::Tracer::scaled_iv_2(Node* n, int scale) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: FAILED since another _scale has been detected before", n->_idx);
    print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: _scale (%d) != 0", scale);
  }
}

void VPointer::Tracer::scaled_iv_3(Node* n, int scale) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: is iv, setting _scale = %d", n->_idx, scale);
  }
}

void VPointer::Tracer::scaled_iv_4(Node* n, int scale) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_MulI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_5(Node* n, int scale) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_MulI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is iv: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
  }
}

void VPointer::Tracer::scaled_iv_6(Node* n, int scale) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_LShiftI PASSED, setting _scale = %d", n->_idx, scale);
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(1) is iv: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::scaled_iv: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
  }
}

void VPointer::Tracer::scaled_iv_7(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: Op_ConvI2L PASSED", n->_idx);
    print_depth(); tty->print_cr("  \\ VPointer::scaled_iv: in(1) %d is scaled_iv_plus_offset: ", n->in(1)->_idx);
    inc_depth(); inc_depth();
    print_depth(); n->in(1)->dump();
    dec_depth(); dec_depth();
  }
}

void VPointer::Tracer::scaled_iv_8(Node* n, VPointer* tmp) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print(" %d VPointer::scaled_iv: Op_LShiftL, creating tmp VPointer: ", n->_idx); tmp->print();
  }
}

void VPointer::Tracer::scaled_iv_9(Node* n, int scale, int offset, Node* invar) {
  if (is_trace_pointer_analysis()) {
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
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::scaled_iv: FAILED", n->_idx);
  }
}

void VPointer::Tracer::offset_plus_k_1(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print(" %d VPointer::offset_plus_k: testing node: ", n->_idx); n->dump();
  }
}

void VPointer::Tracer::offset_plus_k_2(Node* n, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_ConI PASSED, setting _offset = %d", n->_idx, _offset);
  }
}

void VPointer::Tracer::offset_plus_k_3(Node* n, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_ConL PASSED, setting _offset = %d", n->_idx, _offset);
  }
}

void VPointer::Tracer::offset_plus_k_4(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED", n->_idx);
    print_depth(); tty->print_cr("  \\ " JLONG_FORMAT " VPointer::offset_plus_k: Op_ConL FAILED, k is too big", n->get_long());
  }
}

void VPointer::Tracer::offset_plus_k_5(Node* n, Node* _invar) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED since another invariant has been detected before", n->_idx);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: _invar is not null: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_6(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_AddI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_7(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_AddI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_8(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_SubI is PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d",
    n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is Con: ", n->in(2)->_idx); n->in(2)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_9(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: Op_SubI PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d", n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(1) is Con: ", n->in(1)->_idx); n->in(1)->dump();
    print_depth(); tty->print("  \\ %d VPointer::offset_plus_k: in(2) is invariant: ", _invar->_idx); _invar->dump();
  }
}

void VPointer::Tracer::offset_plus_k_10(Node* n, Node* _invar, bool _negate_invar, int _offset) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: PASSED, setting _debug_negate_invar = %d, _invar = %d, _offset = %d", n->_idx, _negate_invar, _invar->_idx, _offset);
    print_depth(); tty->print_cr("  \\ %d VPointer::offset_plus_k: is invariant", n->_idx);
  }
}

void VPointer::Tracer::offset_plus_k_11(Node* n) {
  if (is_trace_pointer_analysis()) {
    print_depth(); tty->print_cr(" %d VPointer::offset_plus_k: FAILED", n->_idx);
  }
}
#endif

bool VLoop::check_preconditions(IdealLoopTree* lpt, bool allow_cfg) {
  reset(lpt, allow_cfg);

#ifndef PRODUCT
  if (is_trace_precondition()) {
    tty->print_cr("\nVLoop::check_precondition");
    lpt->dump_head();
  }
#endif

  const char* return_state = check_preconditions_helper();
  assert(return_state != nullptr, "must have return state");
  if (return_state == VLoop::SUCCESS) {
    return true; // success
  }

#ifndef PRODUCT
  if (is_trace_precondition()) {
    tty->print_cr("VLoop::check_precondition: failed: %s", return_state);
  }
#endif
  return false; // failure
}

const char* VLoop::check_preconditions_helper() {

  // Only accept vector width that is power of 2
  int vector_width = Matcher::vector_width_in_bytes(T_BYTE);
  if (vector_width < 2 || !is_power_of_2(vector_width)) {
    return VLoop::FAILURE_VECTOR_WIDTH;
  }

  // Only accept valid counted loops (int)
  if (!_lpt->_head->as_Loop()->is_valid_counted_loop(T_INT)) {
    return VLoop::FAILURE_VALID_COUNTED_LOOP;
  }
  _cl = _lpt->_head->as_CountedLoop();
  _iv = _cl->phi()->as_Phi();

  if (_cl->is_vectorized_loop()) {
    return VLoop::FAILURE_ALREADY_VECTORIZED;
  }

  if (_cl->is_unroll_only()) {
    return VLoop::FAILURE_UNROLL_ONLY;
  }

  // Check for control flow in the body
  _cl_exit = _cl->loopexit();
  bool has_cfg = _cl_exit->in(0) != _cl;
  if (has_cfg && !is_allow_cfg()) {
#ifndef PRODUCT
    if (is_trace_precondition()) {
      tty->print_cr("VLoop::check_preconditions: fails because of control flow.");
      tty->print("  cl_exit %d", _cl_exit->_idx); _cl_exit->dump();
      tty->print("  cl_exit->in(0) %d", _cl_exit->in(0)->_idx); _cl_exit->in(0)->dump();
      tty->print("  lpt->_head %d", _cl->_idx); _cl->dump();
      _lpt->dump_head();
    }
#endif
    return VLoop::FAILURE_CONTROL_FLOW;
  }

  // Make sure the are no extra control users of the loop backedge
  if (_cl->back_control()->outcnt() != 1) {
    return VLoop::FAILURE_BACKEDGE;
  }

  // To align vector memory accesses in the main-loop, we will have to adjust
  // the pre-loop limit.
  if (_cl->is_main_loop()) {
    CountedLoopEndNode* pre_end = _cl->find_pre_loop_end();
    if (pre_end == nullptr) {
      return VLoop::FAILURE_PRE_LOOP_LIMIT;
    }
    Node* pre_opaq1 = pre_end->limit();
    if (pre_opaq1->Opcode() != Op_Opaque1) {
      return VLoop::FAILURE_PRE_LOOP_LIMIT;
    }
    _pre_loop_end = pre_end;
  }

  return VLoop::SUCCESS;
}

bool VLoopAnalyzer::analyze(IdealLoopTree* lpt, bool allow_cfg) {
  bool success = check_preconditions(lpt, allow_cfg);
  if (!success) { return false; }

#ifndef PRODUCT
  if (is_trace_loop_analyze()) {
    tty->print_cr("VLoopAnalyzer::analyze");
    lpt->dump_head();
  }
#endif

  const char* return_state = analyze_helper();
  assert(return_state != nullptr, "must have return state");
  if (return_state == VLoopAnalyzer::SUCCESS) {
    return true; // success
  }

#ifndef PRODUCT
  if (is_trace_loop_analyze()) {
    tty->print_cr("VLoopAnalyze::analyze: failed: %s", return_state);
  }
#endif
  return false; // failure
}

const char* VLoopAnalyzer::analyze_helper() {
  // skip any loop that has not been assigned max unroll by analysis
  if (SuperWordLoopUnrollAnalysis && _cl->slp_max_unroll() == 0) {
    return VLoopAnalyzer::FAILURE_NO_MAX_UNROLL;
  }

  if (SuperWordReductions) {
    _reductions.mark_reductions();
  }

  _memory_slices.analyze();

  // If there is no memory slice detected, that means there is no store.
  // If there is no reduction and no store, then we give up, because
  // vectorization is not possible anyway (given current limitations).
  if (!_reductions.is_marked_reduction_loop() &&
      _memory_slices.heads().is_empty()) {
    return VLoopAnalyzer::FAILURE_NO_REDUCTION_OR_STORE;
  }

  const char* body_failure = _body.construct();
  if (body_failure != nullptr) {
    return body_failure;
  }

  _types.compute_vector_element_type();

  _dependence_graph.build();

  return VLoopAnalyzer::SUCCESS;
}

bool VLoopReductions::is_reduction(const Node* n) {
  if (!is_reduction_operator(n)) {
    return false;
  }
  // Test whether there is a reduction cycle via every edge index
  // (typically indices 1 and 2).
  for (uint input = 1; input < n->req(); input++) {
    if (in_reduction_cycle(n, input)) {
      return true;
    }
  }
  return false;
}

bool VLoopReductions::is_marked_reduction_pair(Node* s1, Node* s2) const {
  if (is_marked_reduction(s1) &&
      is_marked_reduction(s2)) {
    // This is an ordered set, so s1 should define s2
    for (DUIterator_Fast imax, i = s1->fast_outs(imax); i < imax; i++) {
      Node* t1 = s1->fast_out(i);
      if (t1 == s2) {
        // both nodes are reductions and connected
        return true;
      }
    }
  }
  return false;
}

bool VLoopReductions::is_reduction_operator(const Node* n) {
  int opc = n->Opcode();
  return (opc != ReductionNode::opcode(opc, n->bottom_type()->basic_type()));
}

bool VLoopReductions::in_reduction_cycle(const Node* n, uint input) {
  // First find input reduction path to phi node.
  auto has_my_opcode = [&](const Node* m){ return m->Opcode() == n->Opcode(); };
  PathEnd path_to_phi = find_in_path(n, input, LoopMaxUnroll, has_my_opcode,
                                     [&](const Node* m) { return m->is_Phi(); });
  const Node* phi = path_to_phi.first;
  if (phi == nullptr) {
    return false;
  }
  // If there is an input reduction path from the phi's loop-back to n, then n
  // is part of a reduction cycle.
  const Node* first = phi->in(LoopNode::LoopBackControl);
  PathEnd path_from_phi = find_in_path(first, input, LoopMaxUnroll, has_my_opcode,
                                       [&](const Node* m) { return m == n; });
  return path_from_phi.first != nullptr;
}

Node* VLoopReductions::original_input(const Node* n, uint i) {
  if (n->has_swapped_edges()) {
    assert(n->is_Add() || n->is_Mul(), "n should be commutative");
    if (i == 1) {
      return n->in(2);
    } else if (i == 2) {
      return n->in(1);
    }
  }
  return n->in(i);
}

void VLoopReductions::mark_reductions() {
  assert(_loop_reductions.is_empty(), "must have been reset");
  IdealLoopTree*  lpt = _vloop.lpt();
  CountedLoopNode* cl = _vloop.cl();
  PhiNode*         iv = _vloop.iv();

  // Iterate through all phi nodes associated to the loop and search for
  // reduction cycles in the basic block.
  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    const Node* phi = cl->fast_out(i);
    if (!phi->is_Phi()) {
      continue;
    }
    if (phi->outcnt() == 0) {
      continue;
    }
    if (phi == iv) {
      continue;
    }
    // The phi's loop-back is considered the first node in the reduction cycle.
    const Node* first = phi->in(LoopNode::LoopBackControl);
    if (first == nullptr) {
      continue;
    }
    // Test that the node fits the standard pattern for a reduction operator.
    if (!is_reduction_operator(first)) {
      continue;
    }
    // Test that 'first' is the beginning of a reduction cycle ending in 'phi'.
    // To contain the number of searched paths, assume that all nodes in a
    // reduction cycle are connected via the same edge index, modulo swapped
    // inputs. This assumption is realistic because reduction cycles usually
    // consist of nodes cloned by loop unrolling.
    int reduction_input = -1;
    int path_nodes = -1;
    for (uint input = 1; input < first->req(); input++) {
      // Test whether there is a reduction path in the basic block from 'first'
      // to the phi node following edge index 'input'.
      PathEnd path =
        find_in_path(
          first, input, lpt->_body.size(),
          [&](const Node* n) { return n->Opcode() == first->Opcode() &&
                                      _vloop.in_body(n); },
          [&](const Node* n) { return n == phi; });
      if (path.first != nullptr) {
        reduction_input = input;
        path_nodes = path.second;
        break;
      }
    }
    if (reduction_input == -1) {
      continue;
    }
    // Test that reduction nodes do not have any users in the loop besides their
    // reduction cycle successors.
    const Node* current = first;
    const Node* succ = phi; // current's successor in the reduction cycle.
    bool used_in_loop = false;
    for (int i = 0; i < path_nodes; i++) {
      for (DUIterator_Fast jmax, j = current->fast_outs(jmax); j < jmax; j++) {
        Node* u = current->fast_out(j);
        if (!_vloop.in_body(u)) {
          continue;
        }
        if (u == succ) {
          continue;
        }
        used_in_loop = true;
        break;
      }
      if (used_in_loop) {
        break;
      }
      succ = current;
      current = original_input(current, reduction_input);
    }
    if (used_in_loop) {
      continue;
    }
    // Reduction cycle found. Mark all nodes in the found path as reductions.
    current = first;
    for (int i = 0; i < path_nodes; i++) {
      _loop_reductions.set(current->_idx);
      current = original_input(current, reduction_input);
    }
  }
}

void VLoopMemorySlices::analyze() {
  assert(_heads.is_empty(), "must have been reset");
  assert(_tails.is_empty(), "must have been reset");

  CountedLoopNode* cl = _vloop.cl();

  for (DUIterator_Fast imax, i = cl->fast_outs(imax); i < imax; i++) {
    PhiNode* phi = cl->fast_out(i)->isa_Phi();
    if (phi != nullptr &&
        _vloop.in_body(phi) &&
        phi->is_memory_phi()) {
      Node* phi_tail  = phi->in(LoopNode::LoopBackControl);
      if (phi_tail != phi->in(LoopNode::EntryControl)) {
        _heads.push(phi);
        _tails.push(phi_tail->as_Mem());
      }
    }
  }

#ifndef PRODUCT
  if (_vloop.is_trace_memory_slices()) {
    print();
  }
#endif
}

void VLoopMemorySlices::get_slice(Node* head,
                                  Node* tail,
                                  GrowableArray<Node*> &slice) const {
  slice.clear();
  // Start at tail, and go up through Store nodes.
  // For each Store node, find all Loads below that Store.
  // Terminate once we reach the head.
  Node* n = tail;
  Node* prev = nullptr;
  while (true) {
    assert(_vloop.in_body(n), "must be in block");
    for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
      Node* out = n->fast_out(i);
      if (out->is_Load()) {
        if (_vloop.in_body(out)) {
          slice.push(out);
        }
      } else {
        // Expect other outputs to be the prev (with some exceptions)
        if (out->is_MergeMem() && !_vloop.in_body(out)) {
          // Either unrolling is causing a memory edge not to disappear,
          // or need to run igvn.optimize() again before vectorization
        } else if (out->is_memory_phi() && !_vloop.in_body(out)) {
          // Ditto.  Not sure what else to check further.
        } else if (out->Opcode() == Op_StoreCM && out->in(MemNode::OopStore) == n) {
          // StoreCM has an input edge used as a precedence edge.
          // Maybe an issue when oop stores are vectorized.
        } else {
          assert(out == prev || prev == nullptr, "no branches off of store slice");
        }
      }
    }
    if (n == head) { break; };
    slice.push(n);
    prev = n;
    assert(n->is_Mem(), "unexpected node %s", n->Name());
    n = n->in(MemNode::Memory);
  }

#ifndef PRODUCT
  if (_vloop.is_trace_memory_slices()) {
    tty->print_cr("\nVLoopMemorySlices::get_slice:");
    head->dump();
    for (int j = slice.length() - 1; j >= 0 ; j--) {
      slice.at(j)->dump();
    }
  }
#endif
}

bool VLoopMemorySlices::same_memory_slice(MemNode* n1, MemNode* n2) const {
  return _vloop.phase()->C->get_alias_index(n1->adr_type()) ==
         _vloop.phase()->C->get_alias_index(n2->adr_type());
}

#ifndef PRODUCT
void VLoopMemorySlices::print() const {
  tty->print_cr("\nVLoopMemorySlices::print: %s",
                _heads.length() > 0 ? "" : "NONE");
  for (int m = 0; m < _heads.length(); m++) {
    tty->print("%6d ", m);  _heads.at(m)->dump();
    tty->print("       ");  _tails.at(m)->dump();
  }
}
#endif

const char* VLoopBody::construct() {
  assert(_body.is_empty(),     "must have been reset");
  assert(_body_idx.is_empty(), "must have been reset");

  IdealLoopTree*  lpt = _vloop.lpt();
  CountedLoopNode* cl = _vloop.cl();

  // First pass over loop body:
  //  (1) Check that there are no unwanted nodes (LoadStore, MergeMem, data Proj).
  //  (2) Count number of nodes, and create a temporary map (_idx -> body_idx).
  //  (3) Verify that all non-ctrl nodes have an input inside the loop.
  int body_count = 0;
  for (uint i = 0; i < lpt->_body.size(); i++) {
    Node* n = lpt->_body.at(i);
    if (!_vloop.in_body(n)) { continue; }

    // Create a temporary map
    set_body_idx(n, i);
    body_count++;

    if (n->is_LoadStore() ||
        n->is_MergeMem() ||
        (n->is_Proj() && !n->as_Proj()->is_CFG())) {
      // Bailout if the loop has LoadStore, MergeMem or data Proj
      // nodes. Superword optimization does not work with them.
#ifndef PRODUCT
      if (_vloop.is_trace_body()) {
        tty->print_cr("VLoopBody::construct: fails because of unhandled node:");
        n->dump();
      }
#endif
      return VLoopBody::FAILURE_NODE_NOT_ALLOWED;
    }
#ifndef PRODUCT
    if (!n->is_CFG()) {
      bool found = false;
      for (uint j = 0; j < n->req(); j++) {
        Node* def = n->in(j);
        if (def != nullptr && _vloop.in_body(def)) {
          found = true;
          break;
        }
      }
      assert(found, "every non-cfg node must have an input that is also inside the loop");
    }
#endif
  }

  // Create reverse-post-order list of nodes in body
  ResourceMark rm;
  GrowableArray<Node*> stack;
  VectorSet visited;
  VectorSet post_visited;

  visited.set(body_idx(cl));
  stack.push(cl);

  // Do a depth first walk over out edges
  int rpo_idx = body_count - 1;
  while (!stack.is_empty()) {
    Node* n = stack.top(); // Leave node on stack
    if (!visited.test_set(body_idx(n))) {
      // forward arc in graph
    } else if (!post_visited.test(body_idx(n))) {
      // cross or back arc
      int old_size = stack.length();
      for (DUIterator_Fast imax, i = n->fast_outs(imax); i < imax; i++) {
        Node* use = n->fast_out(i);
        if (_vloop.in_body(use) &&
            !visited.test(body_idx(use)) &&
            // Don't go around backedge
            (!use->is_Phi() || n == cl)) {
          stack.push(use);
        }
      }
      if (stack.length() == old_size) {
        // There were no additional uses, post visit node now
        stack.pop(); // Remove node from stack
        assert(rpo_idx >= 0, "must still have idx to pass out");
        _body.at_put_grow(rpo_idx, n);
        rpo_idx--;
        post_visited.set(body_idx(n));
        assert(rpo_idx >= 0 || stack.is_empty(), "still have idx left or are finished");
      }
    } else {
      stack.pop(); // Remove post-visited node from stack
    }
  }

  // Create real map of block indices for nodes
  for (int j = 0; j < _body.length(); j++) {
    Node* n = _body.at(j);
    set_body_idx(n, j);
  }

#ifndef PRODUCT
  if (_vloop.is_trace_body()) {
    print();
  }
#endif

  assert(rpo_idx == -1 && body_count == _body.length(), "all block members found");
  return nullptr; // success
}

#ifndef PRODUCT
void VLoopBody::print() const {
  tty->print_cr("\nVLoopBody::print:");
  for (int i = 0; i < _body.length(); i++) {
    Node* n = _body.at(i);
    if (n != nullptr) {
      n->dump();
    }
  }
}
#endif

void VLoopDependenceGraph::build() {
  assert(_map.length() == 0, "must be freshly reset");
  CountedLoopNode *cl = _vloop.cl();

  // First, assign a dependence node to each memory node
  for (int i = 0; i < _body.body().length(); i++ ) {
    Node* n = _body.body().at(i);
    if (n->is_Mem() || n->is_memory_phi()) {
      make_node(n);
    }
  }

  const GrowableArray<PhiNode*> &mem_slice_head = _memory_slices.heads();
  const GrowableArray<MemNode*> &mem_slice_tail = _memory_slices.tails();

  ResourceMark rm;
  GrowableArray<Node*> slice_nodes;

  // For each memory slice, create the dependences
  for (int i = 0; i < mem_slice_head.length(); i++) {
    Node* head = mem_slice_head.at(i);
    Node* tail = mem_slice_tail.at(i);

    // Get slice in predecessor order (last is first)
    _memory_slices.get_slice(head, tail, slice_nodes);

    // Make the slice dependent on the root
    DependenceNode* slice_head = get_node(head);
    make_edge(root(), slice_head);

    // Create a sink for the slice
    DependenceNode* slice_sink = make_node(nullptr);
    make_edge(slice_sink, sink());

    // Now visit each pair of memory ops, creating the edges
    for (int j = slice_nodes.length() - 1; j >= 0 ; j--) {
      Node* s1 = slice_nodes.at(j);

      // If no dependency yet, use slice_head
      if (get_node(s1)->in_cnt() == 0) {
        make_edge(slice_head, get_node(s1));
      }
      VPointer p1(s1->as_Mem(), _vloop);
      bool sink_dependent = true;
      for (int k = j - 1; k >= 0; k--) {
        Node* s2 = slice_nodes.at(k);
        if (s1->is_Load() && s2->is_Load()) {
          continue;
        }
        VPointer p2(s2->as_Mem(), _vloop);

        int cmp = p1.cmp(p2);
        if (!VPointer::not_equal(cmp)) {
          // Possibly same address
          make_edge(get_node(s1), get_node(s2));
          sink_dependent = false;
        }
      }
      if (sink_dependent) {
        make_edge(get_node(s1), slice_sink);
      }
    }
  }

  compute_max_depth();

#ifndef PRODUCT
  if(_vloop.is_trace_dependence_graph()) {
    print();
  }
#endif
}

void VLoopDependenceGraph::compute_max_depth() {
  assert(_depth.length() == 0, "must be freshly reset");
  // set all depths to zero
  _depth.at_put_grow(_body.body().length()-1, 0);

  int ct = 0;
  bool again;
  do {
    again = false;
    for (int i = 0; i < _body.body().length(); i++) {
      Node* n = _body.body().at(i);
      if (!n->is_Phi()) {
        int d_orig = depth(n);
        int d_in   = 0;
        for (PredsIterator preds(n, *this); !preds.done(); preds.next()) {
          Node* pred = preds.current();
          if (_vloop.in_body(pred)) {
            d_in = MAX2(d_in, depth(pred));
          }
        }
        if (d_in + 1 != d_orig) {
          set_depth(n, d_in + 1);
          again = true;
        }
      }
    }
    ct++;
  } while (again);

#ifndef PRODUCT
  if (_vloop.is_trace_dependence_graph()) {
    tty->print_cr("\nVLoopDependenceGraph::compute_max_depth iterated: %d times", ct);
  }
#endif
}

bool VLoopDependenceGraph::independent(Node* s1, Node* s2) const {
  int d1 = depth(s1);
  int d2 = depth(s2);

  if (d1 == d2) {
    // Same depth:
    //  1) same node       -> dependent
    //  2) different nodes -> same level implies there is no path
    return s1 != s2;
  }

  // Traversal starting at the deeper node to find the shallower one.
  Node* deep    = d1 > d2 ? s1 : s2;
  Node* shallow = d1 > d2 ? s2 : s1;
  int min_d = MIN2(d1, d2); // prune traversal at min_d

  ResourceMark rm;
  Unique_Node_List worklist;
  worklist.push(deep);
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    for (PredsIterator preds(n, *this); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (_vloop.in_body(pred) && depth(pred) >= min_d) {
        if (pred == shallow) {
          return false; // found it -> dependent
        }
        worklist.push(pred);
      }
    }
  }
  return true; // not found -> independent
}

// Are all nodes in nodes mutually independent?
// We could query independent(s1, s2) for all pairs, but that results
// in O(size * size) graph traversals. We can do it all in one BFS!
// Start the BFS traversal at all nodes from the nodes list. Traverse
// Preds recursively, for nodes that have at least depth min_d, which
// is the smallest depth of all nodes from the nodes list. Once we have
// traversed all those nodes, and have not found another node from the
// nodes list, we know that all nodes in the nodes list are independent.
bool VLoopDependenceGraph::mutually_independent(Node_List* nodes) const {
  ResourceMark rm;
  Unique_Node_List worklist;
  VectorSet nodes_set;
  int min_d = depth(nodes->at(0));
  for (uint k = 0; k < nodes->size(); k++) {
    Node* n = nodes->at(k);
    min_d = MIN2(min_d, depth(n));
    worklist.push(n); // start traversal at all nodes in nodes list
    nodes_set.set(_body.body_idx(n));
  }
  for (uint i = 0; i < worklist.size(); i++) {
    Node* n = worklist.at(i);
    for (PredsIterator preds(n, *this); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (_vloop.in_body(pred) && depth(pred) >= min_d) {
        if (nodes_set.test(_body.body_idx(pred))) { // in nodes list?
          return false;
        }
        worklist.push(pred);
      }
    }
  }
  return true;
}

#ifndef PRODUCT
void VLoopDependenceGraph::print() const {
  tty->print_cr("\nVLoopDependenceGraph::print:");
  // Memory graph
  tty->print_cr("memory root:");
  root()->print();
  tty->print_cr("memory nodes:");
  for (int i = 0; i < _map.length(); i++) {
    DependenceNode* d = _map.at(i);
    if (d != nullptr) {
      d->print();
    }
  }
  tty->print_cr("memory sink:");
  sink()->print();
  // Combined graph
  tty->print_cr("\nDependencies inside combined graph:");
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    tty->print("d:%2d %5d %-10s (", depth(n), n->_idx, n->Name());
    for (PredsIterator preds(n, *this); !preds.done(); preds.next()) {
      Node* pred = preds.current();
      if (_vloop.in_body(pred)) {
        tty->print("%d ", pred->_idx);
      }
    }
    tty->print_cr(")");
  }
}
#endif

VLoopDependenceGraph::DependenceNode*
VLoopDependenceGraph::make_node(Node* node) {
  DependenceNode* m = new (_vloop.arena()) DependenceNode(node);
  if (node != nullptr) {
    assert(_map.at_grow(node->_idx) == nullptr, "one init only");
    _map.at_put_grow(node->_idx, m);
  }
  return m;
}

VLoopDependenceGraph::DependenceEdge*
VLoopDependenceGraph::make_edge(DependenceNode* dpred, DependenceNode* dsucc) {
  DependenceEdge* e = new (_vloop.arena()) DependenceEdge(dpred,
                                                           dsucc,
                                                           dsucc->in_head(),
                                                           dpred->out_head());
  dpred->set_out_head(e);
  dsucc->set_in_head(e);
  return e;
}

int VLoopDependenceGraph::DependenceNode::in_cnt() {
  int ct = 0;
  for (DependenceEdge* e = _in_head; e != nullptr; e = e->next_in()) {
    ct++;
  };
  return ct;
}

int VLoopDependenceGraph::DependenceNode::out_cnt() {
  int ct = 0;
  for (DependenceEdge* e = _out_head; e != nullptr; e = e->next_out()) {
    ct++;
  }
  return ct;
}


void VLoopDependenceGraph::DependenceNode::print() const {
#ifndef PRODUCT
  if (_node != nullptr) {
    tty->print("  %4d %-6s (", _node->_idx, _node->Name());
  } else {
    tty->print("  sentinel (");
  }
  for (DependenceEdge* p = _in_head; p != nullptr; p = p->next_in()) {
    Node* pred = p->pred()->node();
    tty->print(" %d", pred != nullptr ? pred->_idx : 0);
  }
  tty->print(") [");
  for (DependenceEdge* s = _out_head; s != nullptr; s = s->next_out()) {
    Node* succ = s->succ()->node();
    tty->print(" %d", succ != nullptr ? succ->_idx : 0);
  }
  tty->print_cr(" ]");
#endif
}

VLoopDependenceGraph::PredsIterator::PredsIterator(Node* n,
                                                   const VLoopDependenceGraph &dg) {
  _n = n;
  _done = false;
  if (_n->is_Store() || _n->is_Load()) {
    // Load: only memory dependencies
    // Store: memory dependence and data input
    _next_idx = MemNode::Address;
    _end_idx  = n->req();
    _dep_next = dg.get_node(_n)->in_head();
  } else if (_n->is_Mem()) {
    _next_idx = 0;
    _end_idx  = 0;
    _dep_next = dg.get_node(_n)->in_head();
  } else {
    // Data node: only has its own edges
    _next_idx = 1;
    _end_idx  = _n->req();
    _dep_next = nullptr;
  }
  next();
}

void VLoopDependenceGraph::PredsIterator::next() {
  if (_dep_next != nullptr) {
    // Have memory preds left
    _current  = _dep_next->pred()->node();
    _dep_next = _dep_next->next_in();
  } else if (_next_idx < _end_idx) {
    // Have data preds left
    _current  = _n->in(_next_idx++);
  } else {
    _done = true;
  }
}

void VLoopTypes::compute_vector_element_type() {
#ifndef PRODUCT
  if (_vloop.is_trace_vector_element_type()) {
    tty->print_cr("\nVLoopTypes::compute_vector_element_type:");
  }
#endif

  assert(_velt_type.length() == 0, "must be freshly reset");
  // reserve space
  _velt_type.at_put_grow(_body.body().length()-1, nullptr);

  // Initial type
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    set_velt_type(n, container_type(n));
  }

  // Propagate integer narrowed type backwards through operations
  // that don't depend on higher order bits
  for (int i = _body.body().length() - 1; i >= 0; i--) {
    Node* n = _body.body().at(i);
    // Only integer types need be examined
    const Type* vtn = velt_type(n);
    if (vtn->basic_type() == T_INT) {
      uint start, end;
      VectorNode::vector_operands(n, &start, &end);

      for (uint j = start; j < end; j++) {
        Node* in  = n->in(j);
        // Don't propagate through a memory
        if (!in->is_Mem() &&
            _vloop.in_body(in) &&
            velt_type(in)->basic_type() == T_INT &&
            data_size(n) < data_size(in)) {
          bool same_type = true;
          for (DUIterator_Fast kmax, k = in->fast_outs(kmax); k < kmax; k++) {
            Node* use = in->fast_out(k);
            if (!_vloop.in_body(use) || !same_velt_type(use, n)) {
              same_type = false;
              break;
            }
          }
          if (same_type) {
            // In any Java arithmetic operation, operands of small integer types
            // (boolean, byte, char & short) should be promoted to int first.
            // During narrowed integer type backward propagation, for some operations
            // like RShiftI, Abs, and ReverseBytesI,
            // the compiler has to know the higher order bits of the 1st operand,
            // which will be lost in the narrowed type. These operations shouldn't
            // be vectorized if the higher order bits info is imprecise.
            const Type* vt = vtn;
            int op = in->Opcode();
            if (VectorNode::is_shift_opcode(op) || op == Op_AbsI || op == Op_ReverseBytesI) {
              Node* load = in->in(1);
              if (load->is_Load() &&
                  _vloop.in_body(load) &&
                  velt_type(load)->basic_type() == T_INT) {
                // Only Load nodes distinguish signed (LoadS/LoadB) and unsigned
                // (LoadUS/LoadUB) values. Store nodes only have one version.
                vt = velt_type(load);
              } else if (op != Op_LShiftI) {
                // Widen type to int to avoid the creation of vector nodes. Note
                // that left shifts work regardless of the signedness.
                vt = TypeInt::INT;
              }
            }
            set_velt_type(in, vt);
          }
        }
      }
    }
  }

  // Look for pattern: Bool -> Cmp -> x.
  // Propagate type down to Cmp and Bool.
  // If this gets vectorized, the bit-mask
  // has the same size as the compared values.
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    Node* nn = n;
    if (nn->is_Bool() && nn->in(0) == nullptr) {
      nn = nn->in(1);
      assert(nn->is_Cmp(), "always have Cmp above Bool");
    }
    if (nn->is_Cmp() && nn->in(0) == nullptr) {
      assert(_vloop.in_body(nn->in(1)) ||
             _vloop.in_body(nn->in(2)),
             "one of the inputs must be in the loop too");
      if (_vloop.in_body(nn->in(1))) {
        set_velt_type(n, velt_type(nn->in(1)));
      } else {
        set_velt_type(n, velt_type(nn->in(2)));
      }
    }
  }

#ifndef PRODUCT
  if (_vloop.is_trace_vector_element_type()) {
    print();
  }
#endif
}

#ifndef PRODUCT
void VLoopTypes::print() const {
  tty->print_cr("\nVLoopTypes::print:");
  for (int i = 0; i < _body.body().length(); i++) {
    Node* n = _body.body().at(i);
    tty->print("  %5d %-10s ", n->_idx, n->Name());
    velt_type(n)->dump();
    tty->cr();
  }
}
#endif

const Type* VLoopTypes::container_type(Node* n) const {
  if (n->is_Mem()) {
    BasicType bt = n->as_Mem()->memory_type();
    if (n->is_Store() && (bt == T_CHAR)) {
      // Use T_SHORT type instead of T_CHAR for stored values because any
      // preceding arithmetic operation extends values to signed Int.
      bt = T_SHORT;
    }
    if (n->Opcode() == Op_LoadUB) {
      // Adjust type for unsigned byte loads, it is important for right shifts.
      // T_BOOLEAN is used because there is no basic type representing type
      // TypeInt::UBYTE. Use of T_BOOLEAN for vectors is fine because only
      // size (one byte) and sign is important.
      bt = T_BOOLEAN;
    }
    return Type::get_const_basic_type(bt);
  }
  const Type* t = _vloop.phase()->igvn().type(n);
  if (t->basic_type() == T_INT) {
    // A narrow type of arithmetic operations will be determined by
    // propagating the type of memory operations.
    return TypeInt::INT;
  }
  return t;
}


