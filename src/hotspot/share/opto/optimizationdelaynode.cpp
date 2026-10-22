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
  if (t_in == Type::TOP) {
    return t_in;
  }
  return _type;
  // TODO: something more fancy for the types where we can manage.
}

Node* OptimizationDelayNode::Identity(PhaseGVN* phase) {
  // TODO:: maybe reveal the node
  // return in(1);
  return this;
}
