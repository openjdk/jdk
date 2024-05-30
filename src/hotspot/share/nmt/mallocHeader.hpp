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

#include "nmt/memflags.hpp"
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
 * The user allocation is preceded by a header and is immediately followed by a (possibly unaligned)
 *  footer canary:
 *
 * +--------------+-------------  ....  ------------------+-----+
 * |    header    |               user                    | can |
 * |              |             allocation                | ary |
 * +--------------+-------------  ....  ------------------+-----+
 *     16 bytes              user size                      2 byte
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
 *  ...  |   malloc site table marker        | flags  | unused |     canary      |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *
 * Layout on 32-bit:
 *
 *     0        1        2        3        4        5        6        7
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 * |            alt. canary            |           32-bit size             |  ...
 * +--------+--------+--------+--------+--------+--------+--------+--------+
 *
 *           8        9        10       11       12       13       14       15          16 ++
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *  ...  |   malloc site table marker        | flags  | unused |     canary      |  ... User payload ....
 *       +--------+--------+--------+--------+--------+--------+--------+--------+  ------------------------
 *
 * Notes:
 * - We have a canary in the two bytes directly preceding the user payload. That allows us to
 *   catch negative buffer overflows.
 * - On 32-bit, due to the smaller size_t, we have some bits to spare. So we also have a second
 *   canary at the very start of the malloc header (generously sized 32 bits).
 * - The footer canary consists of two bytes. Since the footer location may be unaligned to 16 bits,
 *   the bytes are stored individually.
 */

class MallocHeader {
  NONCOPYABLE(MallocHeader);
  NOT_LP64(uint32_t _alt_canary);
  const size_t _size;
  const uint32_t _mst_marker;
  const MEMFLAGS _flags;
  const uint8_t _unused;
  uint16_t _canary;

  static const uint16_t _header_canary_live_mark = 0xE99E;
  static const uint16_t _header_canary_dead_mark = 0xD99D;
  static const uint16_t _footer_canary_live_mark = 0xE88E;
  static const uint16_t _footer_canary_dead_mark = 0xD88D;
  NOT_LP64(static const uint32_t _header_alt_canary_live_mark = 0xE99EE99E;)
  NOT_LP64(static const uint32_t _header_alt_canary_dead_mark = 0xD88DD88D;)

  // We discount sizes larger than these
  static const size_t max_reasonable_malloc_size = LP64_ONLY(256 * G) NOT_LP64(3500 * M);

  void print_block_on_error(outputStream* st, address bad_address) const;

  static uint16_t build_footer(uint8_t b1, uint8_t b2) { return (uint16_t)(((uint16_t)b1 << 8) | (uint16_t)b2); }

  uint8_t* footer_address() const   { return ((address)this) + sizeof(MallocHeader) + _size; }
  uint16_t get_footer() const       { return build_footer(footer_address()[0], footer_address()[1]); }
  void set_footer(uint16_t v)       { footer_address()[0] = (uint8_t)(v >> 8); footer_address()[1] = (uint8_t)v; }

  template<typename InTypeParam, typename OutTypeParam>
  inline static OutTypeParam resolve_checked_impl(InTypeParam memblock);

public:
  // Contains all of the necessary data to to deaccount block with NMT.
  struct FreeInfo {
    const size_t size;
    const MEMFLAGS flags;
    const uint32_t mst_marker;
  };

  inline MallocHeader(size_t size, MEMFLAGS flags, uint32_t mst_marker);

  inline size_t   size()  const { return _size; }
  inline MEMFLAGS flags() const { return _flags; }
  inline uint32_t mst_marker() const { return _mst_marker; }
  bool get_stack(NativeCallStack& stack) const;

  // Return the necessary data to deaccount the block with NMT.
  FreeInfo free_info() {
    return FreeInfo{this->size(), this->flags(), this->mst_marker()};
  }
  inline void mark_block_as_dead();
  inline void revive();


  bool is_dead() const { return _canary == _header_canary_dead_mark; }
  bool is_live() const { return _canary == _header_canary_live_mark; }

  // Used for debugging purposes only. Check header if it could constitute a valid (live or dead) header.
  inline bool looks_valid() const;

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
