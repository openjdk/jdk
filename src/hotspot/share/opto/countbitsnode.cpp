/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
#include <limits>
#include "opto/countbitsnode.hpp"
#include "opto/opcodes.hpp"
#include "opto/phaseX.hpp"
#include "opto/type.hpp"

template <class CT>
const Type* clz_value(const Node* in, PhaseGVN* phase) {
  using U = decltype(CT::_ulo);
  constexpr juint W = sizeof(U) * 8;

  const Type* t = phase->type(in);
  if (t == Type::TOP) {
    return Type::TOP;
  }

  const CT* i = CT::cast(t);
  juint lo = (~i->_zeros) == 0 ? W : count_leading_zeros(~i->_zeros);
  juint hi = i->_ones == 0 ? W : count_leading_zeros(i->_ones);
  return TypeInt::make(lo, hi, i->_widen);
}

const Type* CountLeadingZerosINode::Value(PhaseGVN* phase) const {
  return clz_value<TypeInt>(in(1), phase);
}

const Type* CountLeadingZerosLNode::Value(PhaseGVN* phase) const {
  return clz_value<TypeLong>(in(1), phase);
}

template <class CT>
const Type* ctz_value(const Node* in, PhaseGVN* phase) {
  using U = decltype(CT::_ulo);
  constexpr juint W = sizeof(U) * 8;

  const Type* t = phase->type(in);
  if (t == Type::TOP) {
    return Type::TOP;
  }

  const CT* i = CT::cast(t);
  juint lo = (~i->_zeros) == 0 ? W : count_trailing_zeros(~i->_zeros);
  juint hi = i->_ones == 0 ? W : count_trailing_zeros(i->_ones);
  return TypeInt::make(lo, hi, i->_widen);
}

const Type* CountTrailingZerosINode::Value(PhaseGVN* phase) const {
  return ctz_value<TypeInt>(in(1), phase);
}

const Type* CountTrailingZerosLNode::Value(PhaseGVN* phase) const {
  return ctz_value<TypeLong>(in(1), phase);
}

template <class CT>
const Type* popcnt_value(const Node* in, PhaseGVN* phase) {
  const Type* t = phase->type(in);
  if (t == Type::TOP) {
    return Type::TOP;
  }

  const CT* i = CT::cast(t);
  return TypeInt::make(population_count(i->_ones), population_count(~i->_zeros), i->_widen);
}

const Type* PopCountINode::Value(PhaseGVN* phase) const {
  return popcnt_value<TypeInt>(in(1), phase);
}

const Type* PopCountLNode::Value(PhaseGVN* phase) const {
  return popcnt_value<TypeLong>(in(1), phase);
}
