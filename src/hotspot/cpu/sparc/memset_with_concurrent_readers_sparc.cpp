/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "asm/macroAssembler.inline.hpp"
#include "gc/shared/memset_with_concurrent_readers.hpp"
#include "runtime/prefetch.inline.hpp"
#include "utilities/align.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"

// An implementation of memset, for use when there may be concurrent
// readers of the region being stored into.
//
// We can't use the standard library memset if it is implemented using
// block initializing stores.  Doing so can result in concurrent readers
// seeing spurious zeros.
//
// We can't use the obvious C/C++ for-loop, because the compiler may
// recognize the idiomatic loop and optimize it into a call to the
// standard library memset; we've seen exactly this happen with, for
// example, Solaris Studio 12.3.  Hence the use of inline assembly
// code, hiding loops from the compiler's optimizer.
//
// We don't attempt to use the standard library memset when it is safe
// to do so.  We could conservatively do so by detecting the presence
// of block initializing stores (VM_Version::has_blk_init()), but the
// implementation provided here should be sufficient.

inline void fill_subword(void* start, void* end, int value) {
  STATIC_ASSERT(BytesPerWord == 8);
  assert(pointer_delta(end, start, 1) < (size_t)BytesPerWord, "precondition");
  // Dispatch on (end - start).
  void* pc;
  __asm__ volatile(
    // offset := (7 - (end - start)) + 3
    //   3 instructions from rdpc to DISPATCH
    " sub %[offset], %[end], %[offset]\n\t" // offset := start - end
    " sllx %[offset], 2, %[offset]\n\t" // scale offset for instruction size of 4
    " add %[offset], 40, %[offset]\n\t" // offset += 10 * instruction size
    " rd %%pc, %[pc]\n\t"               // dispatch on scaled offset
    " jmpl %[pc]+%[offset], %%g0\n\t"
    "  nop\n\t"
    // DISPATCH: no direct reference, but without it the store block may be elided.
    "1:\n\t"
    " stb %[value], [%[end]-7]\n\t" // end[-7] = value
    " stb %[value], [%[end]-6]\n\t"
    " stb %[value], [%[end]-5]\n\t"
    " stb %[value], [%[end]-4]\n\t"
    " stb %[value], [%[end]-3]\n\t"
    " stb %[value], [%[end]-2]\n\t"
    " stb %[value], [%[end]-1]\n\t" // end[-1] = value
    : /* only temporaries/overwritten outputs */
      [pc] "=&r" (pc),               // temp
      [offset] "+&r" (start)
    : [end] "r" (end),
      [value] "r" (value)
    : "memory");
}

void memset_with_concurrent_readers(void* to, int value, size_t size) {
  Prefetch::write(to, 0);
  void* end = static_cast<char*>(to) + size;
  if (size >= (size_t)BytesPerWord) {
    // Fill any partial word prefix.
    uintx* aligned_to = static_cast<uintx*>(align_up(to, BytesPerWord));
    fill_subword(to, aligned_to, value);

    // Compute fill word.
    STATIC_ASSERT(BitsPerByte == 8);
    STATIC_ASSERT(BitsPerWord == 64);
    uintx xvalue = value & 0xff;
    xvalue |= (xvalue << 8);
    xvalue |= (xvalue << 16);
    xvalue |= (xvalue << 32);

    uintx* aligned_end = static_cast<uintx*>(align_down(end, BytesPerWord));
    assert(aligned_to <= aligned_end, "invariant");

    // for ( ; aligned_to < aligned_end; ++aligned_to) {
    //   *aligned_to = xvalue;
    // }
    uintptr_t temp;
    __asm__ volatile(
      // Unroll loop x8.
      " sub %[aend], %[ato], %[temp]\n\t"
      " cmp %[temp], 56\n\t"           // cc := (aligned_end - aligned_to) > 7 words
      " ba %%xcc, 2f\n\t"              // goto TEST always
      "  sub %[aend], 56, %[temp]\n\t" // limit := aligned_end - 7 words
      // LOOP:
      "1:\n\t"                         // unrolled x8 store loop top
      " cmp %[temp], %[ato]\n\t"       // cc := limit > (next) aligned_to
      " stx %[xvalue], [%[ato]-64]\n\t" // store 8 words, aligned_to pre-incremented
      " stx %[xvalue], [%[ato]-56]\n\t"
      " stx %[xvalue], [%[ato]-48]\n\t"
      " stx %[xvalue], [%[ato]-40]\n\t"
      " stx %[xvalue], [%[ato]-32]\n\t"
      " stx %[xvalue], [%[ato]-24]\n\t"
      " stx %[xvalue], [%[ato]-16]\n\t"
      " stx %[xvalue], [%[ato]-8]\n\t"
      // TEST:
      "2:\n\t"
      " bgu,a %%xcc, 1b\n\t"           // goto LOOP if more than 7 words remaining
      "  add %[ato], 64, %[ato]\n\t"   // aligned_to += 8, for next iteration
      // Fill remaining < 8 full words.
      // Dispatch on (aligned_end - aligned_to).
      // offset := (7 - (aligned_end - aligned_to)) + 3
      //   3 instructions from rdpc to DISPATCH
      " sub %[ato], %[aend], %[ato]\n\t" // offset := aligned_to - aligned_end
      " srax %[ato], 1, %[ato]\n\t"      // scale offset for instruction size of 4
      " add %[ato], 40, %[ato]\n\t"      // offset += 10 * instruction size
      " rd %%pc, %[temp]\n\t"            // dispatch on scaled offset
      " jmpl %[temp]+%[ato], %%g0\n\t"
      "  nop\n\t"
      // DISPATCH: no direct reference, but without it the store block may be elided.
      "3:\n\t"
      " stx %[xvalue], [%[aend]-56]\n\t" // aligned_end[-7] = xvalue
      " stx %[xvalue], [%[aend]-48]\n\t"
      " stx %[xvalue], [%[aend]-40]\n\t"
      " stx %[xvalue], [%[aend]-32]\n\t"
      " stx %[xvalue], [%[aend]-24]\n\t"
      " stx %[xvalue], [%[aend]-16]\n\t"
      " stx %[xvalue], [%[aend]-8]\n\t"  // aligned_end[-1] = xvalue
      : /* only temporaries/overwritten outputs */
        [temp] "=&r" (temp),
        [ato] "+&r" (aligned_to)
      : [aend] "r" (aligned_end),
        [xvalue] "r" (xvalue)
      : "cc", "memory");
    to = aligned_end;           // setup for suffix
  }
  // Fill any partial word suffix.  Also the prefix if size < BytesPerWord.
  fill_subword(to, end, value);
}
