/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACECRITICALALLOCATION_HPP
#define SHARE_MEMORY_METASPACECRITICALALLOCATION_HPP

#include "memory/allocation.hpp"
#include "memory/metaspace.hpp"

class MetadataAllocationRequest;
class ClassLoaderData;

// == Critical allocation support ==
//
// The critical allocation support has the purpose of preventing starvation of failed
// metadata allocations that need a GC, in particular for concurrent GCs.
// A "critical" allocation request is registered, then a concurrent full GC is executed.
// When there is any critical allocation present in the system, allocations compete for
// a global lock, so that allocations can be shut out from the concurrent purge() call,
// which takes the same lock. The reasoning is that we gather all the critical allocations
// that are one more failure away from throwing metaspace OOM, in a queue before the GC,
// then free up metaspace due to class unloading in the purge() operation of that GC,
// and satisfy the registered critical allocations. This allows the critical allocations
// to get precedence over normal metaspace allocations, so that the critical allocations
// that are about to throw, do not get starved by other metaspace allocations that have
// not gone through the same dance.
//
// The solution has an intended accuracy of not one allocation, but one per thread. What
// I mean by that, is that the allocations are allowed to throw if they got starved by
// one metaspace allocation per thread, even though a more complicated dance could have
// survived that situation in theory. The motivation is that we are at this point so close
// to being out of memory, and the VM is not having a good time, so the user really ought
// to increase the amount of available metaspace anyway, instead of GC:ing around more
// to satisfy a very small number of additional allocations. But it does solve pathologial
// unbounded starvation scenarios where OOM can get thrown even though most of metaspace
// is full of dead metadata.
//
// The contract for this to work for a given GC is that GCCause::_metadata_GC_clear_soft_refs
// yields a full synchronous GC that unloads metaspace. And it is only intended to be used
// by GCs with concurrent class unloading.

class MetaspaceCriticalAllocation : public AllStatic {
  friend class MetadataAllocationRequest;

  static volatile bool _has_critical_allocation;
  static MetadataAllocationRequest* _requests_head;
  static MetadataAllocationRequest* _requests_tail;

  static void unlink(MetadataAllocationRequest* curr, MetadataAllocationRequest* prev);

  static void add(MetadataAllocationRequest* request);
  static void remove(MetadataAllocationRequest* request);

  static bool try_allocate_critical(MetadataAllocationRequest* request);
  static void wait_for_purge(MetadataAllocationRequest* request);

public:
  static void block_if_concurrent_purge();
  static void satisfy();
  static MetaWord* allocate(ClassLoaderData* loader_data, size_t word_size, Metaspace::MetadataType type);
};

#endif // SHARE_MEMORY_METASPACECRITICALALLOCATION_HPP
