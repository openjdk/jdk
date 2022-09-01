/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_SERVICES_MALLOCHEADER_HPP
#define SHARE_SERVICES_MALLOCHEADER_HPP

#include "memory/allocation.hpp"
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

  NOT_LP64(uint32_t _alt_canary);
  const size_t _size;
  const uint32_t _mst_marker;
  const uint8_t _flags;
  const uint8_t _unused;
  uint16_t _canary;

  static const uint16_t _header_canary_life_mark = 0xE99E;
  static const uint16_t _header_canary_dead_mark = 0xD99D;
  static const uint16_t _footer_canary_life_mark = 0xE88E;
  static const uint16_t _footer_canary_dead_mark = 0xD88D;
  NOT_LP64(static const uint32_t _header_alt_canary_life_mark = 0xE99EE99E;)
  NOT_LP64(static const uint32_t _header_alt_canary_dead_mark = 0xD88DD88D;)

  // We discount sizes larger than these
  static const size_t max_reasonable_malloc_size = LP64_ONLY(256 * G) NOT_LP64(3500 * M);

  void print_block_on_error(outputStream* st, address bad_address) const;

  static uint16_t build_footer(uint8_t b1, uint8_t b2) { return ((uint16_t)b1 << 8) | (uint16_t)b2; }

  uint8_t* footer_address() const   { return ((address)this) + sizeof(MallocHeader) + _size; }
  uint16_t get_footer() const       { return build_footer(footer_address()[0], footer_address()[1]); }
  void set_footer(uint16_t v)       { footer_address()[0] = v >> 8; footer_address()[1] = (uint8_t)v; }

 public:

  inline MallocHeader(size_t size, MEMFLAGS flags, const NativeCallStack& stack, uint32_t mst_marker);

  inline size_t   size()  const { return _size; }
  inline MEMFLAGS flags() const { return (MEMFLAGS)_flags; }
  inline uint32_t mst_marker() const { return _mst_marker; }
  bool get_stack(NativeCallStack& stack) const;

  inline void mark_block_as_dead();

  // If block is broken, fill in a short descriptive text in out,
  // an option pointer to the corruption in p_corruption, and return false.
  // Return true if block is fine.
  inline bool check_block_integrity(char* msg, size_t msglen, address* p_corruption) const;

  // If block is broken, print out a report to tty (optionally with
  // hex dump surrounding the broken block), then trigger a fatal error
  inline void assert_block_integrity() const;
};

// This needs to be true on both 64-bit and 32-bit platforms
STATIC_ASSERT(sizeof(MallocHeader) == (sizeof(uint64_t) * 2));


#endif // SHARE_SERVICES_MALLOCHEADER_HPP
