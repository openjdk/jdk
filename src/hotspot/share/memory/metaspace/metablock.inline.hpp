/*
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METABLOCK_INLINE_HPP
#define SHARE_MEMORY_METASPACE_METABLOCK_INLINE_HPP

#include "memory/metaspace/metablock.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/align.hpp"
#include "utilities/ostream.hpp"
#include "utilities/debug.hpp"

class outputStream;

namespace metaspace {

inline MetaBlock MetaBlock::split_off_tail(size_t tailsize) {
  if (is_empty() || tailsize == 0) {
    return MetaBlock();
  }
  assert(tailsize <= _word_size, "invalid split point for block "
         METABLOCKFORMAT ": %zu", METABLOCKFORMATARGS(*this), tailsize);
  const size_t new_size = _word_size - tailsize;
  MetaBlock tail(_base + new_size, tailsize);
  _word_size = new_size;
  if (_word_size == 0) {
    _base = nullptr;
  }
  return tail;
}

inline void MetaBlock::print_on(outputStream* st) const {
  st->print(METABLOCKFORMAT, METABLOCKFORMATARGS(*this));
}

// Convenience functions
inline bool MetaBlock::is_aligned_base(size_t alignment_words) const {
  return is_aligned(_base, alignment_words * BytesPerWord);
}

inline bool MetaBlock::is_aligned_size(size_t alignment_words) const {
  return is_aligned(_word_size, alignment_words);
}

// some convenience asserts
#define assert_block_base_aligned(block, alignment_words) \
  assert(block.is_aligned_base(alignment_words), "Block wrong base alignment " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

#define assert_block_size_aligned(block, alignment_words) \
  assert(block.is_aligned_size(alignment_words), "Block wrong size alignment " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

#define assert_block_larger_or_equal(block, x) \
  assert(block.word_size() >= x, "Block too small " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

#ifdef ASSERT
inline void MetaBlock::verify() const {
  assert( (_base == nullptr && _word_size == 0) ||
          (_base != nullptr && _word_size > 0),
          "block invalid " METABLOCKFORMAT, METABLOCKFORMATARGS(*this));
}
#endif

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METABLOCK_INLINE_HPP
