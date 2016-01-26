/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1HEAPVERIFIER_HPP
#define SHARE_VM_GC_G1_G1HEAPVERIFIER_HPP

#include "gc/g1/heapRegionSet.hpp"
#include "memory/allocation.hpp"
#include "memory/universe.hpp"

class G1CollectedHeap;

class G1HeapVerifier : public CHeapObj<mtGC> {
private:
  G1CollectedHeap* _g1h;

  // verify_region_sets() performs verification over the region
  // lists. It will be compiled in the product code to be used when
  // necessary (i.e., during heap verification).
  void verify_region_sets();

public:

  G1HeapVerifier(G1CollectedHeap* heap) : _g1h(heap) { }

  // Perform verification.

  // vo == UsePrevMarking  -> use "prev" marking information,
  // vo == UseNextMarking -> use "next" marking information
  // vo == UseMarkWord    -> use the mark word in the object header
  //
  // NOTE: Only the "prev" marking information is guaranteed to be
  // consistent most of the time, so most calls to this should use
  // vo == UsePrevMarking.
  // Currently, there is only one case where this is called with
  // vo == UseNextMarking, which is to verify the "next" marking
  // information at the end of remark.
  // Currently there is only one place where this is called with
  // vo == UseMarkWord, which is to verify the marking during a
  // full GC.
  void verify(VerifyOption vo);

  // verify_region_sets_optional() is planted in the code for
  // list verification in non-product builds (and it can be enabled in
  // product builds by defining HEAP_REGION_SET_FORCE_VERIFY to be 1).
#if HEAP_REGION_SET_FORCE_VERIFY
  void verify_region_sets_optional() {
    verify_region_sets();
  }
#else // HEAP_REGION_SET_FORCE_VERIFY
  void verify_region_sets_optional() { }
#endif // HEAP_REGION_SET_FORCE_VERIFY

  void prepare_for_verify();
  double verify(bool guard, const char* msg);
  void verify_before_gc();
  void verify_after_gc();

#ifndef PRODUCT
  // Make sure that the given bitmap has no marked objects in the
  // range [from,limit). If it does, print an error message and return
  // false. Otherwise, just return true. bitmap_name should be "prev"
  // or "next".
  bool verify_no_bits_over_tams(const char* bitmap_name, CMBitMapRO* bitmap,
                                HeapWord* from, HeapWord* limit);

  // Verify that the prev / next bitmap range [tams,end) for the given
  // region has no marks. Return true if all is well, false if errors
  // are detected.
  bool verify_bitmaps(const char* caller, HeapRegion* hr);
#endif // PRODUCT

  // If G1VerifyBitmaps is set, verify that the marking bitmaps for
  // the given region do not have any spurious marks. If errors are
  // detected, print appropriate error messages and crash.
  void check_bitmaps(const char* caller, HeapRegion* hr) PRODUCT_RETURN;

  // If G1VerifyBitmaps is set, verify that the marking bitmaps do not
  // have any spurious marks. If errors are detected, print
  // appropriate error messages and crash.
  void check_bitmaps(const char* caller) PRODUCT_RETURN;

  // Do sanity check on the contents of the in-cset fast test table.
  bool check_cset_fast_test() PRODUCT_RETURN_( return true; );

  void verify_card_table_cleanup() PRODUCT_RETURN;

  void verify_not_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_young_list(HeapRegion* head) PRODUCT_RETURN;
  void verify_dirty_young_regions() PRODUCT_RETURN;
};

#endif // SHARE_VM_GC_G1_G1HEAPVERIFIER_HPP
