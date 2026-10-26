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

#include "opto/optimizationdelaypolicy.hpp"

uint pick_log_uniform(Stress& stress, uint lo, uint hi) {
  double d_lo = lo;
  double d_hi = hi;
  uint resolution = 1 << 20;
  double u = (stress.random() % (resolution + 1)) / (double)resolution;
  return exp(log(d_lo) + u * log(d_hi / d_lo));
}

OptimizationDelayPolicy::OptimizationDelayPolicy(Stress& stress) :
  _stress(stress),
  _interval_insertion(pick_log_uniform(stress, 1, 100000)),
  _interval_narrow(pick_log_uniform(stress, 1, 1000)),
  _interval_reveal_type(pick_log_uniform(stress, 1, 1000)),
  _interval_reveal_identity(pick_log_uniform(stress, 1, 1000)),
  _insertion_count(0)
{
#ifdef ASSERT
  if (TraceOptimizationDelay) {
    print();
  }
#endif
}

#ifdef ASSERT
void OptimizationDelayPolicy::print() {
  tty->print_cr("[OptimizationDelay] Policy:");
  tty->print_cr("  interval insertion:       %d", _interval_insertion);
  tty->print_cr("  interval narrow:          %d", _interval_narrow);
  tty->print_cr("  interval reveal type:     %d", _interval_reveal_type);
  tty->print_cr("  interval reveal identity: %d", _interval_reveal_identity);
}
#endif

bool OptimizationDelayPolicy::should_insert() {
  if (_insertion_count > MAX_INSERTIONS) { return false; }
  if (_stress.random() % _interval_insertion == 0) {
    _insertion_count++;
    return true;
  } else {
    return false;
  }
}

bool OptimizationDelayPolicy::should_narrow() {
  return _stress.random() % _interval_narrow == 0;
}

bool OptimizationDelayPolicy::should_reveal_type() {
  return _stress.random() % _interval_reveal_type == 0;
}

bool OptimizationDelayPolicy::should_reveal_identity() {
  return _stress.random() % _interval_reveal_identity == 0;
}
