/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_G1MEMORYPOOL_HPP
#define SHARE_VM_SERVICES_G1MEMORYPOOL_HPP

#ifndef SERIALGC
#include "services/memoryPool.hpp"
#include "services/memoryUsage.hpp"
#endif

class G1CollectedHeap;

// This file contains the three classes that represent the memory
// pools of the G1 spaces: G1EdenPool, G1SurvivorPool, and
// G1OldGenPool. In G1, unlike our other GCs, we do not have a
// physical space for each of those spaces. Instead, we allocate
// regions for all three spaces out of a single pool of regions (that
// pool basically covers the entire heap). As a result, the eden,
// survivor, and old gen are considered logical spaces in G1, as each
// is a set of non-contiguous regions. This is also reflected in the
// way we map them to memory pools here. The easiest way to have done
// this would have been to map the entire G1 heap to a single memory
// pool. However, it's helpful to show how large the eden and survivor
// get, as this does affect the performance and behavior of G1. Which
// is why we introduce the three memory pools implemented here.
//
// See comments in g1MonitoringSupport.hpp for additional details
// on this model.
//


// This class is shared by the three G1 memory pool classes
// (G1EdenPool, G1SurvivorPool, G1OldGenPool). Given that the way we
// calculate used / committed bytes for these three pools is related
// (see comment above), we put the calculations in this class so that
// we can easily share them among the subclasses.
class G1MemoryPoolSuper : public CollectedMemoryPool {
protected:
  G1CollectedHeap* _g1h;

  // Would only be called from subclasses.
  G1MemoryPoolSuper(G1CollectedHeap* g1h,
                    const char* name,
                    size_t init_size,
                    bool support_usage_threshold);

  // The reason why all the code is in static methods is so that it
  // can be safely called from the constructors of the subclasses.

  static size_t undefined_max() {
    return (size_t) -1;
  }

  static size_t eden_space_committed(G1CollectedHeap* g1h);
  static size_t eden_space_used(G1CollectedHeap* g1h);

  static size_t survivor_space_committed(G1CollectedHeap* g1h);
  static size_t survivor_space_used(G1CollectedHeap* g1h);

  static size_t old_space_committed(G1CollectedHeap* g1h);
  static size_t old_space_used(G1CollectedHeap* g1h);
};

// Memory pool that represents the G1 eden.
class G1EdenPool : public G1MemoryPoolSuper {
public:
  G1EdenPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return eden_space_used(_g1h);
  }
  size_t max_size() const {
    return undefined_max();
  }
  MemoryUsage get_memory_usage();
};

// Memory pool that represents the G1 survivor.
class G1SurvivorPool : public G1MemoryPoolSuper {
public:
  G1SurvivorPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return survivor_space_used(_g1h);
  }
  size_t max_size() const {
    return undefined_max();
  }
  MemoryUsage get_memory_usage();
};

// Memory pool that represents the G1 old gen.
class G1OldGenPool : public G1MemoryPoolSuper {
public:
  G1OldGenPool(G1CollectedHeap* g1h);

  size_t used_in_bytes() {
    return old_space_used(_g1h);
  }
  size_t max_size() const {
    return undefined_max();
  }
  MemoryUsage get_memory_usage();
};

#endif // SHARE_VM_SERVICES_G1MEMORYPOOL_HPP
