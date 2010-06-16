/*
 * Copyright (c) 1998, 2007, Oracle and/or its affiliates. All rights reserved.
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

// Processor dependent parts of ThreadLocalStorage

private:
  static Thread* _get_thread_cache[];  // index by [(raw_id>>9)^(raw_id>>20) % _pd_cache_size]
  static Thread* get_thread_via_cache_slowly(uintptr_t raw_id, int index);

  NOT_PRODUCT(static int _tcacheHit;)
  NOT_PRODUCT(static int _tcacheMiss;)

public:
  // Cache hit/miss statistics
  static void print_statistics() PRODUCT_RETURN;

  enum Constants {
#ifdef AMD64
    _pd_cache_size         =  256*2   // projected typical # of threads * 2
#else
    _pd_cache_size         =  128*2   // projected typical # of threads * 2
#endif // AMD64
  };

  enum pd_tlsAccessMode {
     pd_tlsAccessUndefined      = -1,
     pd_tlsAccessSlow           = 0,
     pd_tlsAccessIndirect       = 1,
     pd_tlsAccessDirect         = 2
  } ;

  static void set_thread_in_slot (Thread *) ;

  static pd_tlsAccessMode pd_getTlsAccessMode () ;
  static ptrdiff_t pd_getTlsOffset () ;

  static uintptr_t pd_raw_thread_id() {
#ifdef _GNU_SOURCE
#ifdef AMD64
    uintptr_t rv;
    __asm__ __volatile__ ("movq %%fs:0, %0" : "=r"(rv));
    return rv;
#else
    return gs_thread();
#endif // AMD64
#else  //_GNU_SOURCE
    return _raw_thread_id();
#endif //_GNU_SOURCE
  }

  static int pd_cache_index(uintptr_t raw_id) {
    // Copied from the sparc version. Dave said it should also work fine
    // for solx86.
    int ix = (int) (((raw_id >> 9) ^ (raw_id >> 20)) % _pd_cache_size);
    return ix;
  }

  // Java Thread
  static inline Thread* thread();
