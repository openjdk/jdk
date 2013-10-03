/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "opto/callnode.hpp"
#include "opto/matcher.hpp"
#include "opto/mathexactnode.hpp"
#include "opto/multnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/regmask.hpp"
#include "opto/type.hpp"

//=============================================================================
//------------------------------MultiNode--------------------------------------
const RegMask &MultiNode::out_RegMask() const {
  return RegMask::Empty;
}

Node *MultiNode::match( const ProjNode *proj, const Matcher *m ) { return proj->clone(); }

//------------------------------proj_out---------------------------------------
// Get a named projection
ProjNode* MultiNode::proj_out(uint which_proj) const {
  assert(Opcode() != Op_If || which_proj == (uint)true || which_proj == (uint)false, "must be 1 or 0");
  assert(Opcode() != Op_If || outcnt() == 2, "bad if #1");
  for( DUIterator_Fast imax, i = fast_outs(imax); i < imax; i++ ) {
    Node *p = fast_out(i);
    if (p->is_Proj()) {
      ProjNode *proj = p->as_Proj();
      if (proj->_con == which_proj) {
        assert(Opcode() != Op_If || proj->Opcode() == (which_proj?Op_IfTrue:Op_IfFalse), "bad if #2");
        return proj;
      }
    } else if (p->is_FlagsProj()) {
      FlagsProjNode *proj = p->as_FlagsProj();
      if (proj->_con == which_proj) {
        return proj;
      }
    } else {
      assert(p == this && this->is_Start(), "else must be proj");
      continue;
    }
  }
  return NULL;
}

//=============================================================================
//------------------------------ProjNode---------------------------------------
uint ProjNode::hash() const {
  // only one input
  return (uintptr_t)in(TypeFunc::Control) + (_con << 1) + (_is_io_use ? 1 : 0);
}
uint ProjNode::cmp( const Node &n ) const { return _con == ((ProjNode&)n)._con && ((ProjNode&)n)._is_io_use == _is_io_use; }
uint ProjNode::size_of() const { return sizeof(ProjNode); }

// Test if we propagate interesting control along this projection
bool ProjNode::is_CFG() const {
  Node *def = in(0);
  return (_con == TypeFunc::Control && def->is_CFG());
}

const Type* ProjNode::proj_type(const Type* t) const {
  if (t == Type::TOP) {
    return Type::TOP;
  }
  if (t == Type::BOTTOM) {
    return Type::BOTTOM;
  }
  t = t->is_tuple()->field_at(_con);
  Node* n = in(0);
  if ((_con == TypeFunc::Parms) &&
      n->is_CallStaticJava() && n->as_CallStaticJava()->is_boxing_method()) {
    // The result of autoboxing is always non-null on normal path.
    t = t->join(TypePtr::NOTNULL);
  }
  return t;
}

const Type *ProjNode::bottom_type() const {
  if (in(0) == NULL) return Type::TOP;
  return proj_type(in(0)->bottom_type());
}

const TypePtr *ProjNode::adr_type() const {
  if (bottom_type() == Type::MEMORY) {
    // in(0) might be a narrow MemBar; otherwise we will report TypePtr::BOTTOM
    const TypePtr* adr_type = in(0)->adr_type();
    #ifdef ASSERT
    if (!is_error_reported() && !Node::in_dump())
      assert(adr_type != NULL, "source must have adr_type");
    #endif
    return adr_type;
  }
  assert(bottom_type()->base() != Type::Memory, "no other memories?");
  return NULL;
}

bool ProjNode::pinned() const { return in(0)->pinned(); }
#ifndef PRODUCT
void ProjNode::dump_spec(outputStream *st) const { st->print("#%d",_con); if(_is_io_use) st->print(" (i_o_use)");}
#endif

//----------------------------check_con----------------------------------------
void ProjNode::check_con() const {
  Node* n = in(0);
  if (n == NULL)       return;  // should be assert, but NodeHash makes bogons
  if (n->is_Mach())    return;  // mach. projs. are not type-safe
  if (n->is_Start())   return;  // alas, starts can have mach. projs. also
  if (_con == SCMemProjNode::SCMEMPROJCON ) return;
  const Type* t = n->bottom_type();
  if (t == Type::TOP)  return;  // multi is dead
  assert(_con < t->is_tuple()->cnt(), "ProjNode::_con must be in range");
}

//------------------------------Value------------------------------------------
const Type *ProjNode::Value( PhaseTransform *phase ) const {
  if (in(0) == NULL) return Type::TOP;
  return proj_type(phase->type(in(0)));
}

//------------------------------out_RegMask------------------------------------
// Pass the buck uphill
const RegMask &ProjNode::out_RegMask() const {
  return RegMask::Empty;
}

//------------------------------ideal_reg--------------------------------------
uint ProjNode::ideal_reg() const {
  return bottom_type()->ideal_reg();
}
