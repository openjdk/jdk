/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2022 SAP SE. All rights reserved.
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

#ifndef SHARE_NMT_MALLOCHEADER_INLINE_HPP
#define SHARE_NMT_MALLOCHEADER_INLINE_HPP

#include "nmt/mallocHeader.hpp"

#include "jvm_io.h"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/nativeCallStack.hpp"

inline MallocHeader::MallocHeader(size_t size, MEMFLAGS flags, uint32_t mst_marker)
  : _size(size), _mst_marker(mst_marker), _flags(flags),
    _unused(0), _canary(_header_canary_live_mark)
{
  assert(size < max_reasonable_malloc_size, "Too large allocation size?");
  // On 32-bit we have some bits more, use them for a second canary
  // guarding the start of the header.
  NOT_LP64(_alt_canary = _header_alt_canary_live_mark;)
  set_footer(_footer_canary_live_mark); // set after initializing _size
}

inline void MallocHeader::revive() {
  assert(_canary == _header_canary_dead_mark, "must be dead");
  assert(get_footer() == _footer_canary_dead_mark, "must be dead");
  NOT_LP64(assert(_alt_canary == _header_alt_canary_dead_mark, "must be dead"));
  _canary = _header_canary_live_mark;
  NOT_LP64(_alt_canary = _header_alt_canary_live_mark);
  set_footer(_footer_canary_live_mark);
}

// The effects of this method must be reversible with MallocHeader::revive()
inline void MallocHeader::mark_block_as_dead() {
  _canary = _header_canary_dead_mark;
  NOT_LP64(_alt_canary = _header_alt_canary_dead_mark);
  set_footer(_footer_canary_dead_mark);
}

inline bool MallocHeader::is_valid_malloced_pointer(const void* payload, char* msg, size_t msglen) {
  // Handle the pointer as an integral type
  uintptr_t ptr = reinterpret_cast<uintptr_t>(payload);
  // Weed out obviously wrong block addresses of null or very low
  // values. Note that we should not call this for ::free(nullptr),
  // which should be handled by os::free() above us.
  if (ptr < K) {
    jio_snprintf(msg, msglen, "invalid block address");
    return false;
  }

  // From here on we assume the block pointer to be valid. We could
  // use SafeFetch but since this is a hot path we don't. If we are
  // wrong, we will crash when accessing the canary, which hopefully
  // generates distinct crash report.

  // Weed out obviously unaligned addresses. NMT blocks, being the result of
  // malloc calls, should adhere to malloc() alignment. Malloc alignment is
  // specified by the standard by this requirement:
  // "malloc returns a pointer which is suitably aligned for any built-in type"
  // For us it means that it is *at least* 64-bit on all of our 32-bit and
  // 64-bit platforms since we have native 64-bit types. It very probably is
  // larger than that, since there exist scalar types larger than 64bit. Here,
  // we test the smallest alignment we know.
  // Should we ever start using std::max_align_t, this would be one place to
  // fix up.
  if (!is_aligned(ptr, sizeof(uint64_t))) {
    jio_snprintf(msg, msglen, "block address is unaligned");
    return false;
  }
  return true;
}

template<typename InTypeParam, typename OutTypeParam>
inline OutTypeParam MallocHeader::resolve_checked_impl(InTypeParam memblock) {
  char msg[256];
  address corruption = nullptr;
  if (!is_valid_malloced_pointer(memblock, msg, sizeof(msg))) {
    fatal("Not a valid malloc pointer: " PTR_FORMAT ": %s", p2i(memblock), msg);
  }
  OutTypeParam header_pointer = (OutTypeParam)memblock - 1;
  if (!header_pointer->check_block_integrity(msg, sizeof(msg), &corruption)) {
    header_pointer->print_block_on_error(tty, corruption != nullptr ? corruption : (address)header_pointer);
    fatal("NMT corruption: Block at " PTR_FORMAT ": %s", p2i(memblock), msg);
  }
  return header_pointer;
}

inline MallocHeader* MallocHeader::resolve_checked(void* memblock) {
  return MallocHeader::resolve_checked_impl<void*, MallocHeader*>(memblock);
}
inline const MallocHeader* MallocHeader::resolve_checked(const void* memblock) {
  return MallocHeader::resolve_checked_impl<const void*, const MallocHeader*>(memblock);
}


// Used for debugging purposes only. Check header if it could constitute a valid (live or dead) header.
inline bool MallocHeader::looks_valid() const {
  // Note: we define these restrictions loose enough to also catch moderately corrupted blocks.
  // E.g. we don't check footer canary.
  return ( (_canary == _header_canary_live_mark NOT_LP64(&& _alt_canary == _header_alt_canary_live_mark)) ||
           (_canary == _header_canary_dead_mark NOT_LP64(&& _alt_canary == _header_alt_canary_dead_mark)) ) &&
           _size > 0 && _size < max_reasonable_malloc_size;
}

inline bool MallocHeader::check_block_integrity(char* msg, size_t msglen, address* p_corruption) const {
  // Note: if you modify the error messages here, make sure you
  // adapt the associated gtests too.

  // Check header canary
  if (_canary != _header_canary_live_mark) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header canary broken");
    return false;
  }

#ifndef _LP64
  // On 32-bit we have a second canary, check that one too.
  if (_alt_canary != _header_alt_canary_live_mark) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header canary broken");
    return false;
  }
#endif

  // Does block size seems reasonable?
  if (_size >= max_reasonable_malloc_size) {
    *p_corruption = (address)this;
    jio_snprintf(msg, msglen, "header looks invalid (weirdly large block size)");
    return false;
  }

  // Check footer canary
  if (get_footer() != _footer_canary_live_mark) {
    *p_corruption = footer_address();
    jio_snprintf(msg, msglen, "footer canary broken at " PTR_FORMAT " (buffer overflow?)",
                 p2i(footer_address()));
    return false;
  }
  return true;
}

#endif // SHARE_NMT_MALLOCHEADER_INLINE_HPP
