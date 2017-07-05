/*
 * Copyright (c) 2007, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_SERVICES_PSMEMORYPOOL_HPP
#define SHARE_VM_SERVICES_PSMEMORYPOOL_HPP

#ifndef SERIALGC
#include "gc_implementation/parallelScavenge/psOldGen.hpp"
#include "gc_implementation/parallelScavenge/psYoungGen.hpp"
#include "gc_implementation/shared/mutableSpace.hpp"
#include "memory/defNewGeneration.hpp"
#include "memory/heap.hpp"
#include "memory/space.hpp"
#include "services/memoryPool.hpp"
#include "services/memoryUsage.hpp"
#endif

class PSGenerationPool : public CollectedMemoryPool {
private:
  PSOldGen* _gen;

public:
  PSGenerationPool(PSOldGen* pool, const char* name, PoolType type, bool support_usage_threshold);
  PSGenerationPool(PSPermGen* pool, const char* name, PoolType type, bool support_usage_threshold);

  MemoryUsage get_memory_usage();
  size_t used_in_bytes()              { return _gen->used_in_bytes(); }
  size_t max_size() const             { return _gen->reserved().byte_size(); }
};

class EdenMutableSpacePool : public CollectedMemoryPool {
private:
  PSYoungGen*   _gen;
  MutableSpace* _space;

public:
  EdenMutableSpacePool(PSYoungGen* gen,
                       MutableSpace* space,
                       const char* name,
                       PoolType type,
                       bool support_usage_threshold);

  MutableSpace* space()                     { return _space; }
  MemoryUsage get_memory_usage();
  size_t used_in_bytes()                    { return space()->used_in_bytes(); }
  size_t max_size() const {
    // Eden's max_size = max_size of Young Gen - the current committed size of survivor spaces
    return _gen->max_size() - _gen->from_space()->capacity_in_bytes() - _gen->to_space()->capacity_in_bytes();
  }
};

class SurvivorMutableSpacePool : public CollectedMemoryPool {
private:
  PSYoungGen*   _gen;

public:
  SurvivorMutableSpacePool(PSYoungGen* gen,
                           const char* name,
                           PoolType type,
                           bool support_usage_threshold);

  MemoryUsage get_memory_usage();

  size_t used_in_bytes() {
    return _gen->from_space()->used_in_bytes();
  }
  size_t committed_in_bytes() {
    return _gen->from_space()->capacity_in_bytes();
  }
  size_t max_size() const {
    // Return current committed size of the from-space
    return _gen->from_space()->capacity_in_bytes();
  }
};

#endif // SHARE_VM_SERVICES_PSMEMORYPOOL_HPP
