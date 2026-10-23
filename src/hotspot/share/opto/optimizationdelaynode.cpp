/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

#include "opto/optimizationdelaynode.hpp"
#include "opto/phaseX.hpp"

const Type* OptimizationDelayNode::Value(PhaseGVN* phase) const {
  const Type* t_in = phase->type(in(1));

  // We always have to propagate TOP, at least within one IGVN
  // round, otherwise we risk dead data paths with live CFG.
  if (t_in == Type::TOP || _stage == OptimizationDelayNode::Stage::RevealType) {
    return t_in;
  }
  // TODO: do we really need the meet for CCP? Why?
  // Ah I see. I think we could also return just the hold_type,
  // since we know it is correct. But if CCP ends up getting a
  // wider input type, because it is not as good as IGVN, then
  // this becomes a type assertion. Not sure we want that?
  // We could also have 2 modes here.
  return _hold_type->meet(t_in);
  // TODO: something more fancy for the types where we can manage.
}

Node* OptimizationDelayNode::Identity(PhaseGVN* phase) {
  if (_stage == OptimizationDelayNode::Stage::RevealIdentity) {
    return in(1);
  }
  return this;
}

template<typename CT>
static const CT* sample_integer_type(const CT* t_wide, const CT* t_narrow, Stress& stress) {
  using S = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;

  assert(t_wide->contains(t_narrow), "ordered endpoints");

  S lo    = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_lo;
  S hi    = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_hi;
  U ulo   = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_lo;
  U uhi   = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_hi;
  U zeros = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_bits._zeros;
  U ones  = (stress.random() % 2 == 0 ? t_wide : t_narrow)->_bits._ones;

  TypeIntPrototype<S, U> prototype{{lo, hi}, {ulo, uhi}, {zeros, ones}};
  const CT* t = CT::make(prototype, MAX2(t_wide->_widen, t_narrow->_widen));
  assert(t_wide->contains(t), "must be between");
  assert(t->contains(t_narrow), "must be between");
  return t;
}

const Type* OptimizationDelayNode::sample_type_between(const Type* t_wide, const Type* t_narrow, Stress& stress) {
  assert(t_narrow->filter(t_wide) == t_narrow, "wide must contain narrow type");
  if (t_wide->isa_int() != nullptr) {
    return sample_integer_type<TypeInt>(t_wide->is_int(), t_narrow->is_int(), stress);
  } else if (t_wide->is_long() != nullptr) {
    return sample_integer_type<TypeLong>(t_wide->is_long(), t_narrow->is_long(), stress);
  } else {
    // For all other types just pick one of the two, i.e.
    // narrow only in 50% of all cases.
    // TODO: impl some more general idea
    return stress.random() % 2 == 0 ? t_wide : t_narrow;
  }
}

void OptimizationDelayNode::narrow_hold_type(const Type* t_narrow, Stress& stress) {
  if (_stage > Stage::HoldType || t_narrow == Type::TOP) { return; }

  // TODO: why need meet here? Links to ::Value
  const Type* t_old = _hold_type->meet(t_narrow);
  _hold_type = sample_type_between(t_old, t_narrow, stress);

  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] narrow hold type between:");
    tty->print("  old hold type: "); t_old->dump(); tty->cr();
    tty->print("  new hold type: "); _hold_type->dump(); tty->cr();
    tty->print("  narrower type: "); t_narrow->dump(); tty->cr();
    this->dump();
  }
  // Now ensure: t_narrow <= _hold_type <= t_old
  assert(t_narrow->filter(_hold_type) == t_narrow, "must be between");
  assert(_hold_type->filter(t_old) == _hold_type, "must be between");
}

void OptimizationDelayNode::reveal_type() {
  if (_stage > Stage::RevealType) { return; }
  _stage = Stage::RevealType;
  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] reveal type");
    this->dump();
  }
}

void OptimizationDelayNode::reveal_identity() {
  assert(_stage < Stage::RevealIdentity, "only reveal identity once");
  _stage = Stage::RevealIdentity;
  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] reveal identity");
    this->dump();
  }
}
