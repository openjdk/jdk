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

#include "compiler/compileLog.hpp"
#include "compiler/compiler_globals.hpp"
#include "compiler/compilerDefinitions.hpp"
#include "compiler/compilerDirectives.hpp"
#include "compiler/stress.hpp"
#include "runtime/globals_extension.hpp"
#include "runtime/os.hpp"
#include "utilities/ticks.hpp"

#ifdef COMPILER2
#include "opto/c2_globals.hpp"
#endif // COMPILER

static bool should_initialize_stress_seed(CompilerType comp) {
  switch (comp) {
#ifdef COMPILER1
    case compiler_c1:
      return false;
#endif // COMPILER1
#ifdef COMPILER2
    case compiler_c2:
      return StressLCM || StressGCM || StressIGVN || StressCCP ||
        StressIncrementalInlining || StressMacroExpansion ||
        StressMacroElimination || StressUnstableIfTraps ||
        StressBailout || StressLoopPeeling || StressCountedLoop ||
        StressEliminateAllocations;
#endif // COMPILER2
    default:
      assert(comp != compiler_none && comp != compiler_number_of_types, "expected valid compiler");
      return false;
  }
}

Stress::Stress(DirectiveSet* directives, CompileLog* log, CompilerType comp) {
  if (!should_initialize_stress_seed(comp)) {
    _stress_seed = 0;
    return;
  }

  if (FLAG_IS_DEFAULT(StressSeed) || (FLAG_IS_ERGO(StressSeed) && directives->RepeatCompilationOption)) {
    _stress_seed = static_cast<uint>(Ticks::now().nanoseconds());
    FLAG_SET_ERGO(StressSeed, _stress_seed);
  } else {
    _stress_seed = StressSeed;
  }
  if (log != nullptr) {
    log->elem("stress_test seed='%u'", _stress_seed);
  }
}

uint Stress::random() {
  _stress_seed = os::next_random(_stress_seed);
  return _stress_seed;
}

const uint RANDOMIZED_DOMAIN_POW  = 29;
const uint RANDOMIZED_DOMAIN      = 1 << RANDOMIZED_DOMAIN_POW;
const uint RANDOMIZED_DOMAIN_MASK = (1 << (RANDOMIZED_DOMAIN_POW + 1)) - 1;

// This method can be called an arbitrary number of times, with the current count
// as the argument. The logic allows for selecting a single candidate from the
// running list of candidates as follows:
//    int count = 0;
//    Cand* selected = null;
//    while(cand = cand->next()) {
//      if (randomized_select(++count)) {
//        selected = cand;
//      }
//    }
//
// Including the count equalizes the chances any candidate is "selected".
// This is useful when we don't have the complete list of candidates to choose
// from uniformly. In this case, we need to adjust the randomicity of the
// selection, or else we will end up biasing the selection towards the latter
// candidates.
//
// A quick back-of-the-envelope calculation shows that for the list of n candidates
// the equal probability for the candidate to persist as "best" as can be
// achieved by replacing it with "next" k-th candidate with the probability
// of 1/k. It can be easily shown that by the end of the run, the
// probability for any candidate has converged to 1/n, thus giving the
// uniform distribution among all the candidates.
//
// We don't care about the domain size as long as (RANDOMIZED_DOMAIN / count) is large.
bool Stress::randomized_select(uint count) {
  return (random() & RANDOMIZED_DOMAIN_MASK) < (RANDOMIZED_DOMAIN / count);
}
