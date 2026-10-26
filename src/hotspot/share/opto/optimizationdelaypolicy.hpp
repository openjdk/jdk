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

// TODO: description
class OptimizationDelayPolicy : public StackObj {
private:
  // Maximum OptimizationDelay nodes inserted in any compilation.
  static constexpr uint MAX_INSERTIONS = 100;

  // Expected intervals between actions.
  // Each action happens with p=1/interval.
  const uint _interval_insertion;
  const uint _interval_narrow;
  const uint _interval_reveal_type;
  const uint _interval_reveal_identity;

public:
  OptimizationDelayPolicy(Stress& stress);

  void print();
};

#endif // SHARE_OPTO_OPTIZATIONDELAYPOLICY_HPP
