/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_NMT_MALLOCHEADER_HPP
#define SHARE_NMT_MALLOCHEADER_HPP

#include "nmt/memTag.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/nativeCallStack.hpp"

class outputStream;

/*
 * Malloc tracking header.
 *
 * If NMT is active (state >= minimal), we need to track allocations. A simple and cheap way to
 * do this is by using malloc headers.
 *
 * +--------------+-------------  ....  ------------------+
 * |    header    |               user                    |
 * |              |             allocation                |
 * +--------------+-------------  ....  ------------------+
 *     16 bytes              user size
 *
 * Alignment:
 *
 * The start of the user allocation needs to adhere to malloc alignment. We assume 128 bits
 * on both 64-bit/32-bit to be enough for that. So the malloc header is 16 bytes long on both
 * 32-bit and 64-bit.
 *
 * Layout on 64-bit:
 *
 *     0        1        2        3        4        5        6        7
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                            64-bit size                                |  ...
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 *           8        9        10       11       12       13       14       15          16 ++
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *  ...  |   malloc site table marker        |  tags  |         unused           |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *
 * Layout on 32-bit:
 *
 *     0        1        2        3        4        5        6        7
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |                                32-bit size                            |  ...
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 *           8        9        10       11       12       13       14       15          16 ++
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *  ...  |   malloc site table marker        |  tags  |          unused          |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 */

class MallocHeader {
  NONCOPYABLE(MallocHeader);
  NOT_LP64(uint32_t _alt_canary);
  const size_t _size;
  const uint32_t _mst_marker;
  const MemTag _mem_tag;
  const uint8_t _unused[3];

  // We discount sizes larger than these
  static const size_t max_reasonable_malloc_size = LP64_ONLY(256 * G) NOT_LP64(3500 * M);

  void print_block_on_error(outputStream* st, address bad_address) const;

  template<typename InTypeParam, typename OutTypeParam>
  inline static OutTypeParam resolve_checked_impl(InTypeParam memblock);

public:
  // Contains all of the necessary data to to deaccount block with NMT.
  struct FreeInfo {
    const size_t size;
    const MemTag mem_tag;
    const uint32_t mst_marker;
  };

  inline MallocHeader(size_t size, MemTag mem_tag, uint32_t mst_marker);

  inline static size_t malloc_overhead() { return sizeof(MallocHeader); }
  inline size_t size()  const { return _size; }
  inline MemTag mem_tag() const { return _mem_tag; }
  inline uint32_t mst_marker() const { return _mst_marker; }

  // Return the necessary data to deaccount the block with NMT.
  FreeInfo free_info() {
    return FreeInfo{this->size(), this->mem_tag(), this->mst_marker()};
  }

  // If block is broken, fill in a short descriptive text in out,
  // an option pointer to the corruption in p_corruption, and return false.
  // Return true if block is fine.
  inline bool check_block_integrity(char* msg, size_t msglen, address* p_corruption) const;
  // Check correct alignment and placement of pointer, fill in short descriptive text and return false
  // if this is not the case.
  // Returns true if the memblock looks OK.
  inline static bool is_valid_malloced_pointer(const void* payload, char* msg, size_t msglen);

  // If block is broken, print out a report to tty (optionally with
  // hex dump surrounding the broken block), then trigger a fatal error
  inline static const MallocHeader* resolve_checked(const void* memblock);
  inline static MallocHeader* resolve_checked(void* memblock);
};

// This needs to be true on both 64-bit and 32-bit platforms
STATIC_ASSERT(sizeof(MallocHeader) == (sizeof(uint64_t) * 2));


#endif // SHARE_NMT_MALLOCHEADER_HPP
