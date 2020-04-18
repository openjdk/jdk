/*
 * Copyright (c) 2003, 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/parallel/adjoiningGenerations.hpp"
#include "gc/parallel/adjoiningVirtualSpaces.hpp"
#include "gc/parallel/parallelScavengeHeap.hpp"
#include "gc/parallel/parallelArguments.hpp"
#include "gc/shared/genArguments.hpp"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/align.hpp"
#include "utilities/ostream.hpp"

AdjoiningGenerations::AdjoiningGenerations(ReservedSpace old_young_rs) :
  _virtual_spaces(new AdjoiningVirtualSpaces(old_young_rs, MinOldSize,
                                             MinNewSize, GenAlignment)) {
  size_t init_low_byte_size = OldSize;
  size_t min_low_byte_size = MinOldSize;
  size_t max_low_byte_size = MaxOldSize;
  size_t init_high_byte_size = NewSize;
  size_t min_high_byte_size = MinNewSize;
  size_t max_high_byte_size = MaxNewSize;

  assert(min_low_byte_size <= init_low_byte_size &&
         init_low_byte_size <= max_low_byte_size, "Parameter check");
  assert(min_high_byte_size <= init_high_byte_size &&
         init_high_byte_size <= max_high_byte_size, "Parameter check");

  // Layout the reserved space for the generations.
  // If OldGen is allocated on nv-dimm, we need to split the reservation (this is required for windows).
  ReservedSpace old_rs   =
    virtual_spaces()->reserved_space().first_part(max_low_byte_size, ParallelArguments::is_heterogeneous_heap() /* split */);
  ReservedSpace heap_rs  =
    virtual_spaces()->reserved_space().last_part(max_low_byte_size);
  ReservedSpace young_rs = heap_rs.first_part(max_high_byte_size);
  assert(young_rs.size() == heap_rs.size(), "Didn't reserve all of the heap");

  // Create the generations.  Virtual spaces are not passed in.
  _young_gen = new PSYoungGen(init_high_byte_size,
                              min_high_byte_size,
                              max_high_byte_size);
  _old_gen = new PSOldGen(init_low_byte_size,
                          min_low_byte_size,
                          max_low_byte_size,
                          "old", 1);

  // The virtual spaces are created by the initialization of the gens.
  _young_gen->initialize(young_rs, GenAlignment);
  assert(young_gen()->gen_size_limit() == young_rs.size(),
         "Consistency check");
  _old_gen->initialize(old_rs, GenAlignment, "old", 1);
  assert(old_gen()->gen_size_limit() == old_rs.size(), "Consistency check");
}

AdjoiningGenerations::AdjoiningGenerations(): _young_gen(NULL), _old_gen(NULL), _virtual_spaces(NULL) { }

size_t AdjoiningGenerations::reserved_byte_size() {
  return virtual_spaces()->reserved_space().size();
}

AdjoiningGenerations* AdjoiningGenerations::create_adjoining_generations(ReservedSpace old_young_rs) {
  return new AdjoiningGenerations(old_young_rs);
}
