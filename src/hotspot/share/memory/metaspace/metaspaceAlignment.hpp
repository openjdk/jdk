/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 SAP SE. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_ALIGNMENT_HPP
#define SHARE_MEMORY_METASPACE_ALIGNMENT_HPP

#include "memory/metaspace/chunklevel.hpp"
#include "memory/metaspace/freeBlocks.hpp"
#include "utilities/align.hpp"
#include "utilities/powerOfTwo.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

// The minimal alignment: good enough to store structures with 64bit wide members (also on 32-bit).
// Should we ever store longer values, revise.
static const int LogMetaspaceMinimalAlignment = LogBytesPerLong;
static const int MetaspaceMinAlignmentBytes = 1 << LogMetaspaceMinimalAlignment;
static const int MetaspaceMinAlignmentWords = MetaspaceMinAlignmentBytes / BytesPerWord;

// The maximum possible alignment is the smallest chunk size (note that the buddy allocator places
// chunks at chunk-size-aligned boundaries, therefore the start address is guaranteed to be aligned).
// We cannot guarantee allocation alignment beyond this value.
static const int MetaspaceMaxAlignmentWords = chunklevel::MIN_CHUNK_WORD_SIZE;

// Given a net allocation word size and an alignment value, return the raw word size we actually
// allocate internally.
inline size_t get_raw_word_size_for_requested_word_size(size_t net_word_size,
                                                        size_t alignment_words) {

  // The alignment should be between the minimum alignment but cannot be larger than the smallest chunk size
  assert(is_power_of_2(alignment_words), "invalid alignment");
  assert(alignment_words >= MetaspaceMinAlignmentWords &&
         alignment_words <= MetaspaceMaxAlignmentWords,
         "invalid alignment (" SIZE_FORMAT ")", alignment_words);

  // Deallocated metablocks are kept in a binlist which means blocks need to have
  // a minimal size
  size_t raw_word_size = MAX2(net_word_size, FreeBlocks::MinWordSize);

  raw_word_size = align_up(raw_word_size, alignment_words);

  return raw_word_size;
}

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_ALIGNMENT_HPP
