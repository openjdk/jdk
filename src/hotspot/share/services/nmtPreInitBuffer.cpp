/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
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

#include "precompiled.hpp"
#include "jvm_io.h"
#include "logging/log.hpp"
#include "services/memTracker.hpp"
#include "services/nmtPreInitBuffer.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// Threadedness note:
//
// The NMTPreInitBuffer is guaranteed to be used only single threaded, since its
// only used during VM initialization. However, that does not mean that its always
// the same thread, since the thread loading the hotspot - which causes the
// dynamic C++ initialization inside the hotspot to run, and allocations to happen
// - may be a different thread from the one invoking CreateJavaVM.

#if INCLUDE_NMT

#include <stdlib.h> // for raw malloc

const static size_t malloc_alignment = NOT_LP64(8) LP64_ONLY(16);

// Statistics
static struct {
  int allocs;
  int reallocs;
  int frees;
  size_t freed_size; // includes realloce'd size
} g_preinit_stats;

// To be able to provide at least a primitive notion of realloc, we
// need to know the block size, hence we need a small header.
struct hdr {
  size_t len;
  size_t next; // for putting into a freelist
};

STATIC_ASSERT(sizeof(hdr) == malloc_alignment);

// Given a user pointer, return its header
static hdr* get_hdr(void* p) {
  return ((hdr*)p) - 1;
}

// Given a header, return the pointer to its user portion
static uint8_t* get_payload(hdr* h) {
  return (uint8_t*)(h + 1);
}

static size_t get_block_size(void* p) {
  return get_hdr(p)->len;
}

// Slab

// Returns NULL on buffer exhaustion
uint8_t* NMTPreInitBuffer::Slab::allocate(size_t s) {
  if ((_used + s) > _capacity) {
    return NULL;
  }
  uint8_t* p = _buffer + _used;
  _used += s;
  return p;
}

void NMTPreInitBuffer::Slab::print_on(outputStream* st) const {
  st->print("base: " PTR_FORMAT ", capacity " SIZE_FORMAT ", used: " SIZE_FORMAT ", free: " SIZE_FORMAT ".",
            p2i(_buffer), _capacity, _used, _capacity - _used);
}

NMTPreInitBuffer::Slab* NMTPreInitBuffer::_primary_buffer = NULL;
NMTPreInitBuffer::Slab* NMTPreInitBuffer::_overflow_buffer = NULL;

NMTPreInitBuffer::Slab* NMTPreInitBuffer::create_slab(size_t capacity) {
  // Notes:
  // - raw malloc!
  // - we never free this
  // - if malloc fails (extremely unlikely - were we that hard pressed for memory
  //   early on, something else would have surely failed) we print and exit.
  //   VM infrastructure may not be here yet, there is not much else we can do.
  const size_t total_size = sizeof(Slab) + capacity;
  void* p = ::malloc(total_size);
  if (p == NULL) {
    ::fprintf(stderr, "NMT pre init: OOM in pre-init phase");
    ::exit(-1);
  }
  DEBUG_ONLY(memset(p, 0xA4, total_size));
  Slab* slab = new(p) Slab(capacity);
  return slab;
}

uint8_t* NMTPreInitBuffer::allocate_block(size_t size, MEMFLAGS flag) {

  // Should only be called before NMT initialization
  assert(MemTracker::is_initialized() == false, "Use only pre-NMT initialization");

  // On first call, make sure the primary buffer is allocated.
  if (_primary_buffer == NULL) {
    _primary_buffer = create_slab(primary_buffer_size);
  }

  // - malloc(0) => malloc(1)
  // - honor malloc alignment
  const size_t inner_size = align_up(MAX2((size_t)1, size), malloc_alignment);
  const size_t outer_size = inner_size + sizeof(hdr);

  uint8_t* p = _primary_buffer->allocate(outer_size);
  if (p == NULL) {
    // If the primary buffer is exhausted (this should be very rare but could happen
    //  with massive command lines), we switch over to the dynamically created
    //  overflow buffer.
    if (_overflow_buffer == NULL) {
      _overflow_buffer = create_slab(overflow_buffer_size);
    }
    p = _overflow_buffer->allocate(outer_size);
    if (p == NULL) {
      // If the overflow buffer is exhausted too, we:
      // - in debug, assert
      // - in release builds, switch over to "normal" os::malloc and disable NMT.
      //   Normal VM operations won't be affected, but NMT will be off.
      // Note that this should really not happen and should be investigated. The
      //  overflow buffer of 2mb should be enough for ~100x the normal pre-init
      //  VM C-heap consumption.
      assert(false, "NMT Preinit buffers exhausted!");
      log_info(nmt)("NMT Preinit buffers exhausted!");
      MemTracker::initialize(NMT_off);
      return (uint8_t*) os::malloc(size, flag);
    }
  }

  hdr* const new_hdr = (hdr*)p;
  new_hdr->len = inner_size;

  uint8_t* const ret = get_payload(new_hdr);

  g_preinit_stats.allocs++;

  assert(is_aligned(ret, malloc_alignment), "Sanity");

  return ret;
}

// Reallocate an allocation originally allocated from the preinit buffers within the preinit
// buffers.  Can only be called before NMT initialization.
// On buffer exhaustion, NMT is switched off and C-heap is returned instead (release);
// in debug builds we assert.
uint8_t* NMTPreInitBuffer::reallocate_block(uint8_t* old, size_t size, MEMFLAGS flag) {

  // We only allow this *before* NMT initialization
  assert(MemTracker::is_initialized() == false, "Use only pre-NMT initialization");

  assert(_primary_buffer != NULL, "realloc before malloc?");
  assert(contains_block(old), "sanity");
  assert(is_aligned(old, malloc_alignment), "sanity");

  // Note: To keep complexity down we don't bother with any optimizations here.
  uint8_t* ret = (uint8_t*)allocate_block(size, flag);
  if (size > 0 && old != NULL) {
    size_t size_old = get_block_size(old);
    if (size_old > 0) {
      ::memcpy(ret, old, MIN2(size, size_old));
    }
    free_block(old);
  }

  g_preinit_stats.reallocs++;

  return ret;
}

// Evacuate an allocation in preinit buffers into the regular C-heap. Can only be
//  called *after* NMT initialization.
uint8_t* NMTPreInitBuffer::evacuate_block_to_c_heap(uint8_t* old, size_t size, MEMFLAGS flag) {

  // We only allow this *after* NMT initialization
  assert(MemTracker::is_initialized() == true, "Use only post-NMT initialization");

  assert(_primary_buffer != NULL, "realloc before malloc?");
  assert(contains_block(old), "sanity");
  assert(is_aligned(old, malloc_alignment), "sanity");

  // Please note: we do not modify the content of the preinit buffers anymore in
  //  NMT post-init phase, we just read it.
  uint8_t* ret = NEW_C_HEAP_ARRAY(uint8_t, size, flag);
  if (size > 0 && old != NULL) {
    size_t size_old = get_block_size(old);
    if (size_old > 0) {
      ::memcpy(ret, old, MIN2(size, size_old));
    }
  }

  return ret;
}

void NMTPreInitBuffer::free_block(uint8_t* old) {

  assert(_primary_buffer != NULL, "free before malloc?");

  assert(contains_block(old), "sanity");
  assert(is_aligned(old, malloc_alignment), "sanity");

  if (MemTracker::is_initialized() == true) {
    // Nothing to do post-init. Since we won't use preinit buffers anymore,
    // there is nothing to be gained from doing anything here.
    return;
  } else {
    // For now we do nothing here to keep complexity low. We just count. Should we
    //  notice excessive amounts of free/realloc in pre-init phase, we may do
    //  something smarter, e.g. maintaining free-block lists.
    // Note: the case of alloc-followed-by-free (where one could roll back the allocation
    //  in place) is so rare that its not worth implementing.
    const size_t old_size = get_block_size(old);
    g_preinit_stats.freed_size += old_size;
    g_preinit_stats.frees ++;
  }

}

void NMTPreInitBuffer::print_state(outputStream* st) {

  st->print("primary buffer: ");
  if (_primary_buffer != NULL) {
    _primary_buffer->print_on(st);
    st->cr();
  } else {
    st->print_cr("unused");
  }

  st->print("overflow buffer: ");
  if (_overflow_buffer != NULL) {
    _overflow_buffer->print_on(st);
    st->cr();
  } else {
    st->print_cr("unused");
  }

  st->print_cr("stats: allocs: %d reallocs: %d frees: %d (" SIZE_FORMAT " bytes)",
               g_preinit_stats.allocs, g_preinit_stats.reallocs,
               g_preinit_stats.frees, g_preinit_stats.freed_size);
}


#endif // INCLUDE_NMT
