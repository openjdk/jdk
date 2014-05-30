/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_SPARC_VM_COPY_SPARC_HPP
#define CPU_SPARC_VM_COPY_SPARC_HPP

// Inline functions for memory copy and fill.

static void pd_conjoint_words(HeapWord* from, HeapWord* to, size_t count) {
  (void)memmove(to, from, count * HeapWordSize);
}

static void pd_disjoint_words(HeapWord* from, HeapWord* to, size_t count) {
  switch (count) {
  case 8:  to[7] = from[7];
  case 7:  to[6] = from[6];
  case 6:  to[5] = from[5];
  case 5:  to[4] = from[4];
  case 4:  to[3] = from[3];
  case 3:  to[2] = from[2];
  case 2:  to[1] = from[1];
  case 1:  to[0] = from[0];
  case 0:  break;
  default: (void)memcpy(to, from, count * HeapWordSize);
           break;
  }
}

static void pd_disjoint_words_atomic(HeapWord* from, HeapWord* to, size_t count) {
  switch (count) {
  case 8:  to[7] = from[7];
  case 7:  to[6] = from[6];
  case 6:  to[5] = from[5];
  case 5:  to[4] = from[4];
  case 4:  to[3] = from[3];
  case 3:  to[2] = from[2];
  case 2:  to[1] = from[1];
  case 1:  to[0] = from[0];
  case 0:  break;
  default: while (count-- > 0) {
             *to++ = *from++;
           }
           break;
  }
}

static void pd_aligned_conjoint_words(HeapWord* from, HeapWord* to, size_t count) {
  (void)memmove(to, from, count * HeapWordSize);
}

static void pd_aligned_disjoint_words(HeapWord* from, HeapWord* to, size_t count) {
  pd_disjoint_words(from, to, count);
}

static void pd_conjoint_bytes(void* from, void* to, size_t count) {
  (void)memmove(to, from, count);
}

static void pd_conjoint_bytes_atomic(void* from, void* to, size_t count) {
  (void)memmove(to, from, count);
}

static void pd_conjoint_jshorts_atomic(jshort* from, jshort* to, size_t count) {
  if (from > to) {
    while (count-- > 0) {
      // Copy forwards
      *to++ = *from++;
    }
  } else {
    from += count - 1;
    to   += count - 1;
    while (count-- > 0) {
      // Copy backwards
      *to-- = *from--;
    }
  }
}

static void pd_conjoint_jints_atomic(jint* from, jint* to, size_t count) {
  if (from > to) {
    while (count-- > 0) {
      // Copy forwards
      *to++ = *from++;
    }
  } else {
    from += count - 1;
    to   += count - 1;
    while (count-- > 0) {
      // Copy backwards
      *to-- = *from--;
    }
  }
}

static void pd_conjoint_jlongs_atomic(jlong* from, jlong* to, size_t count) {
#ifdef _LP64
  assert(BytesPerLong == BytesPerOop, "jlongs and oops must be the same size");
  pd_conjoint_oops_atomic((oop*)from, (oop*)to, count);
#else
  // Guarantee use of ldd/std via some asm code, because compiler won't.
  // See solaris_sparc.il.
  _Copy_conjoint_jlongs_atomic(from, to, count);
#endif
}

static void pd_conjoint_oops_atomic(oop* from, oop* to, size_t count) {
  // Do better than this: inline memmove body  NEEDS CLEANUP
  if (from > to) {
    while (count-- > 0) {
      // Copy forwards
      *to++ = *from++;
    }
  } else {
    from += count - 1;
    to   += count - 1;
    while (count-- > 0) {
      // Copy backwards
      *to-- = *from--;
    }
  }
}

static void pd_arrayof_conjoint_bytes(HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_bytes_atomic(from, to, count);
}

static void pd_arrayof_conjoint_jshorts(HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jshorts_atomic((jshort*)from, (jshort*)to, count);
}

static void pd_arrayof_conjoint_jints(HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jints_atomic((jint*)from, (jint*)to, count);
}

static void pd_arrayof_conjoint_jlongs(HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_jlongs_atomic((jlong*)from, (jlong*)to, count);
}

static void pd_arrayof_conjoint_oops(HeapWord* from, HeapWord* to, size_t count) {
  pd_conjoint_oops_atomic((oop*)from, (oop*)to, count);
}

static void pd_fill_to_words(HeapWord* tohw, size_t count, juint value) {
#ifdef _LP64
  guarantee(mask_bits((uintptr_t)tohw, right_n_bits(LogBytesPerLong)) == 0,
         "unaligned fill words");
  julong* to = (julong*)tohw;
  julong  v  = ((julong)value << 32) | value;
  while (count-- > 0) {
    *to++ = v;
  }
#else // _LP64
  juint* to = (juint*)tohw;
  while (count-- > 0) {
    *to++ = value;
  }
#endif // _LP64
}

typedef void (*_zero_Fn)(HeapWord* to, size_t count);

static void pd_fill_to_aligned_words(HeapWord* tohw, size_t count, juint value) {
  assert(MinObjAlignmentInBytes >= BytesPerLong, "need alternate implementation");

  if (value == 0 && UseBlockZeroing &&
      (count > (size_t)(BlockZeroingLowLimit >> LogHeapWordSize))) {
   // Call it only when block zeroing is used
   ((_zero_Fn)StubRoutines::zero_aligned_words())(tohw, count);
  } else {
   julong* to = (julong*)tohw;
   julong  v  = ((julong)value << 32) | value;
   // If count is odd, odd will be equal to 1 on 32-bit platform
   // and be equal to 0 on 64-bit platform.
   size_t odd = count % (BytesPerLong / HeapWordSize) ;

   size_t aligned_count = align_object_offset(count - odd) / HeapWordsPerLong;
   julong* end = ((julong*)tohw) + aligned_count - 1;
   while (to <= end) {
     DEBUG_ONLY(count -= BytesPerLong / HeapWordSize ;)
     *to++ = v;
   }
   assert(count == odd, "bad bounds on loop filling to aligned words");
   if (odd) {
     *((juint*)to) = value;

   }
  }
}

static void pd_fill_to_bytes(void* to, size_t count, jubyte value) {
  (void)memset(to, value, count);
}

static void pd_zero_to_words(HeapWord* tohw, size_t count) {
  pd_fill_to_words(tohw, count, 0);
}

static void pd_zero_to_bytes(void* to, size_t count) {
  (void)memset(to, 0, count);
}

#endif // CPU_SPARC_VM_COPY_SPARC_HPP
