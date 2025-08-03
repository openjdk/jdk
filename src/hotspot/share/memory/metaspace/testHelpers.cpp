/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021 SAP SE. All rights reserved.
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

#include "memory/memoryReserver.hpp"
#include "memory/metaspace/chunkManager.hpp"
#include "memory/metaspace/metaspaceArena.hpp"
#include "memory/metaspace/metaspaceArenaGrowthPolicy.hpp"
#include "memory/metaspace/metaspaceContext.hpp"
#include "memory/metaspace/testHelpers.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ostream.hpp"

namespace metaspace {

///// MetaspaceTestArena //////

MetaspaceTestArena::MetaspaceTestArena(Mutex* lock, MetaspaceArena* arena) :
  _lock(lock),
  _arena(arena)
{}

MetaspaceTestArena::~MetaspaceTestArena() {
  {
    MutexLocker fcl(_lock, Mutex::_no_safepoint_check_flag);
    delete _arena;
  }
  delete _lock;
}

MetaWord* MetaspaceTestArena::allocate(size_t word_size) {
  MutexLocker fcl(_lock, Mutex::_no_safepoint_check_flag);
  MetaBlock result, wastage;
  result = _arena->allocate(word_size, wastage);
  if (wastage.is_nonempty()) {
    _arena->deallocate(wastage);
  }
  return result.base();
}

void MetaspaceTestArena::deallocate(MetaWord* p, size_t word_size) {
  MutexLocker fcl(_lock, Mutex::_no_safepoint_check_flag);
  _arena->deallocate(MetaBlock(p, word_size));
}

///// MetaspaceTestArea //////

MetaspaceTestContext::MetaspaceTestContext(const char* name, size_t commit_limit, size_t reserve_limit) :
  _name(name),
  _reserve_limit(reserve_limit),
  _commit_limit(commit_limit),
  _context(nullptr),
  _commit_limiter(commit_limit == 0 ? max_uintx : commit_limit), // commit_limit == 0 -> no limit
  _rs()
{
  assert(is_aligned(reserve_limit, Metaspace::reserve_alignment_words()), "reserve_limit (%zu) "
                    "not aligned to metaspace reserve alignment (%zu)",
                    reserve_limit, Metaspace::reserve_alignment_words());
  if (reserve_limit > 0) {
    // have reserve limit -> non-expandable context
    _rs = MemoryReserver::reserve(reserve_limit * BytesPerWord, Metaspace::reserve_alignment(), os::vm_page_size(), mtTest);
    _context = MetaspaceContext::create_nonexpandable_context(name, _rs, &_commit_limiter);
  } else {
    // no reserve limit -> expandable vslist
    _context = MetaspaceContext::create_expandable_context(name, &_commit_limiter);
  }

}

MetaspaceTestContext::~MetaspaceTestContext() {
  DEBUG_ONLY(verify();)
  MutexLocker fcl(Metaspace_lock, Mutex::_no_safepoint_check_flag);
  delete _context;
  if (_rs.is_reserved()) {
    MemoryReserver::release(_rs);
  }
}

// Create an arena, feeding off this area.
MetaspaceTestArena* MetaspaceTestContext::create_arena(Metaspace::MetaspaceType type) {
  const ArenaGrowthPolicy* growth_policy = ArenaGrowthPolicy::policy_for_space_type(type, false);
  Mutex* lock = new Mutex(Monitor::nosafepoint, "MetaspaceTestArea_lock");
  MetaspaceArena* arena = nullptr;
  {
    MutexLocker ml(lock,  Mutex::_no_safepoint_check_flag);
    arena = new MetaspaceArena(_context, growth_policy, Metaspace::min_allocation_alignment_words, _name);
  }
  return new MetaspaceTestArena(lock, arena);
}

void MetaspaceTestContext::purge_area() {
  _context->cm()->purge();
}

#ifdef ASSERT
void MetaspaceTestContext::verify() const {
  if (_context != nullptr) {
    _context->verify();
  }
}
#endif

void MetaspaceTestContext::print_on(outputStream* st) const {
  _context->print_on(st);
}

size_t MetaspaceTestContext::used_words() const {
  return _context->used_words_counter()->get();
}

size_t MetaspaceTestContext::committed_words() const {
  assert(_commit_limiter.committed_words() == _context->committed_words(), "Sanity");
  return _context->committed_words();
}

size_t MetaspaceTestContext::reserved_words() const {
  return _context->reserved_words();
}

} // namespace metaspace
