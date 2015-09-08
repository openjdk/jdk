/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

#include <string.h>
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ostream.hpp"

#if INCLUDE_ALL_GCS

// Unit test
#ifdef ASSERT

static unsigned line_byte(const char* line, size_t i) {
  return unsigned(line[i]) & 0xFF;
}

// Verify memset_with_concurrent_readers mimics memset.
// We don't attempt to verify the concurrent reader case.
void test_memset_with_concurrent_readers() {
  const size_t chunk_size = 8 * BytesPerWord;
  const unsigned chunk_count = 4;
  const size_t block_size = (chunk_count + 4) * chunk_size;
  char block[block_size];
  char clear_block[block_size];
  char set_block[block_size];

  // block format:
  // 0: unused leading chunk
  // 1: chunk written from start index to end of chunk
  // ... nchunks fully written chunks
  // N: chunk written from start of chunk to end index
  // N+1: unused trailing chunk

  const int clear_value = 0;
  const int set_value = 0xAC;

  memset(clear_block, clear_value, block_size);
  memset(set_block, set_value, block_size);

  for (unsigned nchunks = 0; nchunks <= chunk_count; ++nchunks) {
    for (size_t start = 1; start <= chunk_size; ++start) {
      for (size_t end = 0; end <= chunk_size; ++end) {
        size_t set_start = chunk_size + start;
        size_t set_end = (2 + nchunks) * chunk_size + end;
        size_t set_size = set_end - set_start;

        memset(block, clear_value, block_size);
        memset_with_concurrent_readers(&block[set_start], set_value, set_size);
        bool head_clear = !memcmp(clear_block, block, set_start);
        bool middle_set = !memcmp(set_block, block + set_start, set_size);
        bool tail_clear = !memcmp(clear_block, block + set_end, block_size - set_end);
        if (!(head_clear && middle_set && tail_clear)) {
          tty->print_cr("*** memset_with_concurrent_readers failed: "
                        "set start " SIZE_FORMAT ", set end " SIZE_FORMAT,
                        set_start, set_end);
          for (unsigned chunk = 0; chunk < (block_size / chunk_size); ++chunk) {
            for (unsigned line = 0; line < (chunk_size / BytesPerWord); ++line) {
              const char* lp = &block[chunk * chunk_size + line * BytesPerWord];
              tty->print_cr("%d,%d: %2x %2x  %2x %2x  %2x %2x  %2x %2x",
                            chunk, line,
                            line_byte(lp, 0), line_byte(lp, 1),
                            line_byte(lp, 2), line_byte(lp, 3),
                            line_byte(lp, 4), line_byte(lp, 5),
                            line_byte(lp, 6), line_byte(lp, 7));
            }
          }
          assert(head_clear, "leading byte not clear");
          assert(middle_set, "memset byte not set");
          assert(tail_clear, "trailing bye not clear");
        }
      }
    }
  }
}

#endif // end unit test

#endif // INCLUDE_ALL_GCS
