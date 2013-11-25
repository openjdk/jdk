/*
 * Copyright (c) 2001, 2013, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_GENERATIONSIZER_HPP
#define SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_GENERATIONSIZER_HPP

#include "memory/collectorPolicy.hpp"

// There is a nice batch of tested generation sizing code in
// TwoGenerationCollectorPolicy. Lets reuse it!

class GenerationSizer : public TwoGenerationCollectorPolicy {
 public:
  GenerationSizer() {
    // Partial init only!
    initialize_flags();
    initialize_size_info();
  }

  void initialize_flags() {
    // Do basic sizing work
    TwoGenerationCollectorPolicy::initialize_flags();

    assert(UseSerialGC ||
           !FLAG_IS_DEFAULT(ParallelGCThreads) ||
           (ParallelGCThreads > 0),
           "ParallelGCThreads should be set before flag initialization");

    // The survivor ratio's are calculated "raw", unlike the
    // default gc, which adds 2 to the ratio value. We need to
    // make sure the values are valid before using them.
    if (MinSurvivorRatio < 3) {
      MinSurvivorRatio = 3;
    }

    if (InitialSurvivorRatio < 3) {
      InitialSurvivorRatio = 3;
    }
  }

  size_t min_young_gen_size() { return _min_gen0_size; }
  size_t young_gen_size()     { return _initial_gen0_size; }
  size_t max_young_gen_size() { return _max_gen0_size; }

  size_t min_old_gen_size()   { return _min_gen1_size; }
  size_t old_gen_size()       { return _initial_gen1_size; }
  size_t max_old_gen_size()   { return _max_gen1_size; }
};

#endif // SHARE_VM_GC_IMPLEMENTATION_PARALLELSCAVENGE_GENERATIONSIZER_HPP
