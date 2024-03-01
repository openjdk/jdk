/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_G1_G1HEAPVERIFIER_HPP
#define SHARE_GC_G1_G1HEAPVERIFIER_HPP

#include "gc/g1/g1HeapRegionSet.hpp"
#include "gc/shared/verifyOption.hpp"
#include "memory/allocation.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"

class G1CollectedHeap;

class G1HeapVerifier : public CHeapObj<mtGC> {
private:
  static int _enabled_verification_types;

  G1CollectedHeap* _g1h;

  void verify_region_sets();

public:
  enum G1VerifyType {
    G1VerifyYoungNormal     =  1, // -XX:VerifyGCType=young-normal
    G1VerifyConcurrentStart =  2, // -XX:VerifyGCType=concurrent-start
    G1VerifyMixed           =  4, // -XX:VerifyGCType=mixed
    G1VerifyYoungEvacFail   =  8, // -XX:VerifyGCType=young-evac-fail
    G1VerifyRemark          = 16, // -XX:VerifyGCType=remark
    G1VerifyCleanup         = 32, // -XX:VerifyGCType=cleanup
    G1VerifyFull            = 64, // -XX:VerifyGCType=full
    G1VerifyAll             = -1
  };

  G1HeapVerifier(G1CollectedHeap* heap) : _g1h(heap) {}

  static void enable_verification_type(G1VerifyType type);
  static bool should_verify(G1VerifyType type);

  // Perform verification.
  void verify(VerifyOption vo);

  // verify_region_sets_optional() is planted in the code for
  // list verification in debug builds.
  void verify_region_sets_optional() { DEBUG_ONLY(verify_region_sets();) }

  void prepare_for_verify();
  void verify(VerifyOption vo, const char* msg);
  void verify_before_gc();
  void verify_after_gc();

  // Verify that marking state is set up correctly after a concurrent start pause.
  void verify_marking_state();

  void verify_bitmap_clear(bool above_tams_only);

  // Do sanity check on the contents of the in-cset fast test table.
  bool check_region_attr_table() PRODUCT_RETURN_( return true; );

  void verify_card_table_cleanup() PRODUCT_RETURN;

  void verify_not_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_region(HeapRegion* hr) PRODUCT_RETURN;
  void verify_dirty_young_regions() PRODUCT_RETURN;
};

#endif // SHARE_GC_G1_G1HEAPVERIFIER_HPP
