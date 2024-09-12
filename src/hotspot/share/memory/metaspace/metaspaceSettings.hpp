/*
 * Copyright (c) 2020, 2023 SAP SE. All rights reserved.
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_MEMORY_METASPACE_METASPACESETTINGS_HPP
#define SHARE_MEMORY_METASPACE_METASPACESETTINGS_HPP

#include "memory/allStatic.hpp"
#include "memory/metaspace/chunklevel.hpp"
#include "utilities/globalDefinitions.hpp"

namespace metaspace {

class Settings : public AllStatic {

  // Granularity, in bytes, metaspace is committed with.
  static constexpr size_t _commit_granule_bytes = 64 * K;

  // Granularity, in words, metaspace is committed with.
  static constexpr size_t _commit_granule_words = _commit_granule_bytes / BytesPerWord;

  // The default size of a VirtualSpaceNode, unless created with an explicitly specified size.
  //  Must be a multiple of the root chunk size.
  // This value only affects the process virtual size, and there only the granularity with which it
  //  increases. Matters mostly for 32bit platforms due to limited address space.
  // Note that this only affects the non-class metaspace. Class space ignores this size (it is one
  //  single large mapping).
  static const size_t _virtual_space_node_default_word_size =
      chunklevel::MAX_CHUNK_WORD_SIZE * NOT_LP64(1) LP64_ONLY(4); // 16MB (32-bit) / 64MB (64-bit)

  // Alignment of the base address of a virtual space node
  static const size_t _virtual_space_node_reserve_alignment_words = chunklevel::MAX_CHUNK_WORD_SIZE;

  // When allocating from a chunk, if the remaining area in the chunk is too small to hold
  // the requested size, we attempt to double the chunk size in place...
  static const bool _enlarge_chunks_in_place = true;

public:

  static size_t commit_granule_bytes()                        { return _commit_granule_bytes; }
  static size_t commit_granule_words()                        { return _commit_granule_words; }
  static size_t virtual_space_node_default_word_size()        { return _virtual_space_node_default_word_size; }
  static size_t virtual_space_node_reserve_alignment_words()  { return _virtual_space_node_reserve_alignment_words; }
  static bool enlarge_chunks_in_place()                       { return _enlarge_chunks_in_place; }

  static void ergo_initialize();

  static void print_on(outputStream* st);

};

} // namespace metaspace

#endif // SHARE_MEMORY_METASPACE_METASPACESETTINGS_HPP
