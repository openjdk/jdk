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

  // The idea is that the _hold_type shrowds the t_in.
  // The meet ensures that we never return a type that
  // is more precise than either t_in or _hold_type.
  // There are cases where t_in is not contained by _hold_type,
  // for example if CCP did not manage to find t_in as
  // precisely as IGVN did. Because of those cases we need to
  // return the meet of t_in and _hold_type, to avoid providing
  // a more precise type than t_in would have delivered.
  return _hold_type->meet(t_in);
}

Node* OptimizationDelayNode::Identity(PhaseGVN* phase) {
  if (_stage == OptimizationDelayNode::Stage::RevealIdentity) {
    return in(1);
  }
  return this;
}

template<typename U>
static U random_bits(Stress& stress) {
  static_assert(std::is_unsigned_v<U>);
  static_assert(sizeof(U) == 4 || sizeof(U) == 8);

  // Out of extra caution, we only trust the lower
  // two bits of random, so we assemble the result
  // from 2-byte chunks.
  U bits = 0;
  for (uint i = 0; i < sizeof(U) / 2; i++) {
    bits = U(stress.random() &  0xffffu) | (bits << 16);
  }
  return bits;
}

template<typename T>
static T pick_inclusive(Stress& stress, T lo, T hi) {
  using U = std::make_unsigned_t<T>;
  assert(lo <= hi, "ordered in T");

  // Computing the distance for signed values works easier
  // using unsigned types, since they can capture all values.
  // We shift all negative values to the lower half of the
  // unsigned range, and all positive values to the upper range.
  // This shift is done by simply flipping the sign bit.
  constexpr U sign_bit = std::is_unsigned_v<T> ? U(0) : U(1) << (sizeof(T) * 8 - 1);
  const U lo_u = U(lo) ^ sign_bit;
  const U hi_u = U(hi) ^ sign_bit;
  assert(lo_u <= hi_u, "ordered in U");
  const U distance = hi_u - lo_u;

  // If the range we pick from fills the whole type range,
  // we just pick random bits. Otherwise, we have to pick
  // a value from: [lo_u, lo_u + distance]
  U val_u = random_bits<U>(stress);
  if (distance != std::numeric_limits<U>::max()) {
    val_u = lo_u + val_u % (distance + U(1));
  }
  assert(lo_u <= val_u, "ordered in U");
  assert(val_u <= hi_u, "ordered in U");

  // Now shift back to the signed range by flipping the bit again.
  T val = static_cast<T>(val_u ^ sign_bit);
  assert(lo <= val, "ordered in T");
  assert(val <= hi, "ordered in T");
  return val;
}

template<typename CT>
static const CT* sample_integer_type(const CT* wide, const CT* narrow, Stress& stress) {
  using S = std::remove_const_t<decltype(CT::_lo)>;
  using U = std::remove_const_t<decltype(CT::_ulo)>;

  assert(wide->contains(narrow), "ordered endpoints");

  // Since each of the below is a constraint, we should bias
  // each choice towards the constraint from the wider type.
  S lo    = (stress.random() % 20 == 0 ? narrow : wide)->_lo;
  S hi    = (stress.random() % 20 == 0 ? narrow : wide)->_hi;
  U ulo   = (stress.random() % 20 == 0 ? narrow : wide)->_ulo;
  U uhi   = (stress.random() % 20 == 0 ? narrow : wide)->_uhi;
  U zeros = (stress.random() % 20 == 0 ? narrow : wide)->_bits._zeros;
  U ones  = (stress.random() % 20 == 0 ? narrow : wide)->_bits._ones;
  lo    = stress.random() % 20 > 0 ? lo  : pick_inclusive(stress, wide->_lo,    narrow->_lo);
  hi    = stress.random() % 20 > 0 ? hi  : pick_inclusive(stress, narrow->_hi,  wide->_hi);
  ulo   = stress.random() % 20 > 0 ? ulo : pick_inclusive(stress, wide->_ulo,   narrow->_ulo);
  uhi   = stress.random() % 20 > 0 ? uhi : pick_inclusive(stress, narrow->_uhi, wide->_uhi);
  zeros = stress.random() % 20 > 0 ? zeros : wide->_bits._zeros | (random_bits<U>(stress) & narrow->_bits._zeros);
  ones  = stress.random() % 20 > 0 ? ones  : wide->_bits._ones  | (random_bits<U>(stress) & narrow->_bits._ones);

  TypeIntPrototype<S, U> prototype{{lo, hi}, {ulo, uhi}, {zeros, ones}};
  const CT* t = CT::make(prototype, MAX2(wide->_widen, narrow->_widen));
  assert(wide->contains(t), "must be between");
  assert(t->contains(narrow), "must be between");
  return t;
}

const Type* OptimizationDelayNode::sample_type_between(const Type* wide, const Type* narrow, Stress& stress) {
  assert(narrow->filter(wide) == narrow, "wide must contain narrow type");
  if (wide->isa_int() != nullptr) {
    return sample_integer_type<TypeInt>(wide->is_int(), narrow->is_int(), stress);
  } else if (wide->is_long() != nullptr) {
    return sample_integer_type<TypeLong>(wide->is_long(), narrow->is_long(), stress);
  } else {
    // For all other types just pick one of the two, i.e.
    // narrow only in 50% of all cases.
    assert(false, "not yet properly implemented");
    return stress.random() % 2 == 0 ? wide : narrow;
  }
}

void OptimizationDelayNode::narrow_hold_type(const Type* t_in, Stress& stress) {
  if (_stage > Stage::HoldType) { return; }

  // If t_in is TOP, ::Value will return TOP anyway, so no point in narrowing.
  if (t_in == Type::TOP) { return; }

  // If the input type is not properly contained in _hold_type,
  // we don't know how to safely narrow. For example, this can
  // happen when CCP clears the cached types from IGVN, produces
  // wider types, and then IGVN starts again with wider types
  // than it had found previously.
  // Note: ::Value will return a conservative result based on
  // the meet of t_in and _hold_type anyway.
  if (t_in->filter(_hold_type) != t_in) { return; }

  const Type* old = _hold_type;
  _hold_type = sample_type_between(old, t_in, stress);

  if (TraceOptimizationDelay && _hold_type != old) {
    tty->print_cr("[OptizationDelay] narrow hold type between:");
    tty->print("  old hold type: "); old->dump(); tty->cr();
    tty->print("  new hold type: "); _hold_type->dump(); tty->cr();
    tty->print("  input type:    "); t_in->dump(); tty->cr();
    this->dump();
  }
  // Now ensure: t_in <= _hold_type <= old
  assert(t_in->filter(_hold_type) == t_in, "must be between");
  assert(_hold_type->filter(old) == _hold_type, "must be between");
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
