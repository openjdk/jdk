/*
 * Copyright (c) 2023 Red Hat, Inc. All rights reserved.
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METABLOCK_HPP
#define SHARE_MEMORY_METASPACE_METABLOCK_HPP

#include "utilities/globalDefinitions.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"

namespace metaspace {

// Tiny structure to be passed by value
class MetaBlock {

  MetaWord* _base;
  size_t _word_size;

public:

  MetaBlock(MetaWord* p, size_t word_size) :
    _base(word_size == 0 ? nullptr : p), _word_size(word_size) {}
  MetaBlock() : MetaBlock(nullptr, 0) {}

  MetaWord* base() const { return _base; }
  const MetaWord* end() const { return _base + _word_size; }
  size_t word_size() const { return _word_size; }
  bool is_empty() const { return _base == nullptr; }
  bool is_nonempty() const { return _base != nullptr; }
  void reset() { _base = nullptr; _word_size = 0; }

  bool operator==(const MetaBlock& rhs) const {
    return base() == rhs.base() &&
           word_size() == rhs.word_size();
  }

  MetaBlock first_part(size_t size) const {
    return is_nonempty() ?
        MetaBlock(base(), MIN2(word_size(), size)) : MetaBlock();
  }

  void split(size_t pivot, MetaBlock& part1, MetaBlock& part2) const {
    if (is_nonempty()) {
      MetaBlock thisblock = *this;
      assert(pivot <= thisblock.word_size(), "invalid split point");
      part1 = MetaBlock(thisblock.base(), pivot);
      part2 = MetaBlock(thisblock.base() + pivot, thisblock.word_size() - pivot);
    } else {
      part1 = part2 = MetaBlock();
    }
  }

  DEBUG_ONLY(void verify() const;)

  // Convenience functions
  bool is_aligned_base(size_t alignment_words) const {
    return is_aligned(_base, alignment_words * BytesPerWord);
  }
  bool is_aligned_size(size_t alignment_words) const {
    return is_aligned(_word_size, alignment_words);
  }
};

#define METABLOCKFORMAT                 "block (@" PTR_FORMAT " word size " SIZE_FORMAT ")"
#define METABLOCKFORMATARGS(__block__)  p2i((__block__).base()), (__block__).word_size()

// some convenience asserts
#define assert_block_base_aligned(block, alignment_words) \
  assert(block.is_aligned_base(alignment_words), "Block wrong base alignment " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

#define assert_block_size_aligned(block, alignment_words) \
  assert(block.is_aligned_size(alignment_words), "Block wrong size alignment " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

#define assert_block_larger_or_equal(block, x) \
  assert(block.word_size() >= x, "Block too small " METABLOCKFORMAT, METABLOCKFORMATARGS(block));

}

#endif // SHARE_MEMORY_METASPACE_METABLOCK_HPP
