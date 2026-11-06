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

#ifndef SHARE_OPTO_OPTIZATIONDELAYPOLICY_HPP
#define SHARE_OPTO_OPTIZATIONDELAYPOLICY_HPP

#include "compiler/stress.hpp"
#include "memory/allocation.hpp"

// This policy handles the insertion and advancement of the
// OptimizationDelay node. It is a helper object that provides
// the probability that a delay node is inserted kept delayed
// or advanced (type narrowing, type reveal, identity reveal).
class OptimizationDelayPolicy : public StackObj {
private:
  // Maximum OptimizationDelay nodes inserted in any compilation.
  // Compilations with a high probability of insertion will quickly
  // insert a lot of delay nodes at the beginning. Compilations
  // with low probability of insertion may not insert any delay
  // nodes if there are only few IR nodes, but will be able to
  // insert delay nodes more evenly into graphs with a lot of IR
  // nodes.
  static constexpr uint MAX_INSERTIONS = 100;

  // We also have to bound the number of type narrowings, so that we
  // get a bound on how many times we re-run IGVN. We keep this low,
  // since each IGVN round can come with VerifyIterativeGVN which
  // will revisit all nodes in the graph, and that is expensive.
  static constexpr uint MAX_NARROW = 100;

  Stress& _stress;

  // Expected intervals between actions.
  // Each action happens with p=1/interval.
  const uint _interval_insertion;
  const uint _interval_narrow;
  const uint _interval_reveal_type;
  const uint _interval_reveal_identity;

  // Number of inserted OptimizationDelay nodes, not to exceed
  // MAX_INSERTIONS limit. Similarly, cound advancements to
  // keep IGVN re-runs bounded.
  uint _count_insertion;
  uint _count_narrow;

public:
  OptimizationDelayPolicy(Stress& stress);

  DEBUG_ONLY( void print(); )

  bool should_insert();
  bool should_narrow();
  bool should_reveal_type();
  bool should_reveal_identity();
};

#endif // SHARE_OPTO_OPTIZATIONDELAYPOLICY_HPP
