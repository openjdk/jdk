/*
 * Copyright (c) 1998, 2005, Oracle and/or its affiliates. All rights reserved.
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

public:
  // Java Thread  - force inlining
  static inline Thread* thread() ;

private:
  static Thread* _get_thread_cache[];  // index by [(raw_id>>9)^(raw_id>>20) % _pd_cache_size]
  static Thread* get_thread_via_cache_slowly(uintptr_t raw_id, int index);

  NOT_PRODUCT(static int _tcacheHit;)
  NOT_PRODUCT(static int _tcacheMiss;)

public:

  // Print cache hit/miss statistics
  static void print_statistics() PRODUCT_RETURN;

  enum Constants {
    _pd_cache_size         =  256*2  // projected typical # of threads * 2
  };

  static void set_thread_in_slot (Thread *) ;

  static uintptr_t pd_raw_thread_id() {
    return _raw_thread_id();
  }

  static int pd_cache_index(uintptr_t raw_id) {
    // Hash function: From email from Dave:
    // The hash function deserves an explanation.  %g7 points to libthread's
    // "thread" structure.  On T1 the thread structure is allocated on the
    // user's stack (yes, really!) so the ">>20" handles T1 where the JVM's
    // stack size is usually >= 1Mb.  The ">>9" is for T2 where Roger allocates
    // globs of thread blocks contiguously.  The "9" has to do with the
    // expected size of the T2 thread structure.  If these constants are wrong
    // the worst thing that'll happen is that the hit rate for heavily threaded
    // apps won't be as good as it could be.  If you want to burn another
    // shift+xor you could mix together _all of the %g7 bits to form the hash,
    // but I think that's excessive.  Making the change above changed the
    // T$ miss rate on SpecJBB (on a 16X system) from about 3% to imperceptible.
    uintptr_t ix = (int) (((raw_id >> 9) ^ (raw_id >> 20)) % _pd_cache_size);
    return ix;
  }
