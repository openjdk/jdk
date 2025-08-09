/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_OPTO_RELAXED_MATH_HPP
#define SHARE_OPTO_RELAXED_MATH_HPP

#include "utilities/ostream.hpp"

class RelaxedMathOptimizationMode {
  enum Mode {
    AllowReductionReordering = 1,
    AllowFMA                 = 2,
  };

  const int _mode;

public:
  explicit RelaxedMathOptimizationMode(int mode) : _mode(mode) {}

  // Allow no relaxed math optimizations.
  static RelaxedMathOptimizationMode make_default() {
    return RelaxedMathOptimizationMode(0);
  }

  // Allow reordering in reductions. Can lead to different results
  // due to different rounding. Allows a vector-accumulator that is
  // only folded after the loop.
  bool is_allow_reduction_reordering() const {
    return _mode & AllowReductionReordering;
  }

  // Allow "a * b + c" -> "fma(a, b, c)"
  // First pattern rounds after multiplication and addition separately.
  // Second pattern only rounds after fma computation, which can lead
  // to slightly different rounding results.
  bool is_allow_fma() const {
    return _mode & AllowFMA;
  }

  int mode() const {
    return _mode;
  }

  bool cmp(const RelaxedMathOptimizationMode& other) const {
    return mode() == other.mode();
  }

  void dump_on(outputStream* st) const {
    if (_mode == 0)                      { st->print("no_relaxed_math ");}
    if (is_allow_reduction_reordering()) { st->print("allow_reduction_reordering "); }
    if (is_allow_fma())                  { st->print("allow_fma "); }
  }
};

#endif // SHARE_OPTO_RELAXED_MATH_HPP
