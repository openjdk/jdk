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

  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] Value");
    this->dump();
    t_in->dump(); tty->cr();
  }

  // We always have to propagate TOP, at least within one IGVN
  // round, otherwise we risk dead data paths with live CFG.
  if (t_in == Type::TOP) {
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
  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] Identity");
    this->dump();
  }
  if (_stage == OptimizationDelayNode::Stage::RevealIdentity) {
    return in(1);
  }
  return this;
}

void OptimizationDelayNode::reveal_identity() {
  _stage = Stage::RevealIdentity;
  if (TraceOptimizationDelay) {
    tty->print_cr("[OptizationDelay] reveal identity");
    this->dump();
  }
}
