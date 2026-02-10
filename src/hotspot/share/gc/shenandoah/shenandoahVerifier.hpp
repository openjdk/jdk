/*
 * Copyright (c) 2017, 2020, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#ifndef SHARE_GC_SHENANDOAH_SHENANDOAHVERIFIER_HPP
#define SHARE_GC_SHENANDOAH_SHENANDOAHVERIFIER_HPP

#include "gc/shared/markBitMap.hpp"
#include "gc/shenandoah/shenandoahRootVerifier.hpp"
#include "memory/allocation.hpp"
#include "oops/oopsHierarchy.hpp"
#include "utilities/stack.hpp"

class ShenandoahHeap;
class ShenandoahMarkingContext;

#ifdef _WINDOWS
#pragma warning( disable : 4522 )
#endif

class ShenandoahVerifierTask {
public:
  ShenandoahVerifierTask(oop o = nullptr, int idx = 0): _obj(o) { }
  ShenandoahVerifierTask(oop o, size_t idx): _obj(o) { }
  // Trivially copyable.

  inline oop obj()  const { return _obj; }

private:
  oop _obj;
};

typedef Stack<ShenandoahVerifierTask, mtGC> ShenandoahVerifierStack;
typedef Atomic<juint> ShenandoahLivenessData;

class ShenandoahVerifier : public CHeapObj<mtGC> {
private:
  ShenandoahHeap* _heap;
  MarkBitMap* _verification_bit_map;
public:
  typedef enum {
    // Disable remembered set verification.
    _verify_remembered_disable,

    // Old objects should be registered and RS cards within *read-only* RS are dirty for all
    // inter-generational pointers.
    _verify_remembered_before_marking,

    // Old objects should be registered and RS cards within *read-write* RS are dirty for all
    // inter-generational pointers.
    _verify_remembered_before_updating_references,

    // Old objects should be registered and RS cards within *read-write* RS are dirty for all
    // inter-generational pointers. Differs from previous verification modes by using top instead
    // of update watermark and not using the marking context.
    _verify_remembered_after_full_gc
  } VerifyRememberedSet;

  typedef enum {
    // Disable marked objects verification.
    _verify_marked_disable,

    // Objects should be marked in "next" bitmap.
    _verify_marked_incomplete,

    // Objects should be marked in "complete" bitmap.
    _verify_marked_complete,

    // Objects should be marked in "complete" bitmap, except j.l.r.Reference referents, which
    // may be dangling after marking but before conc-weakrefs-processing.
    _verify_marked_complete_except_references,

    // Objects should be marked in "complete" bitmap, except j.l.r.Reference referents, which
    // may be dangling after marking but before conc-weakrefs-processing. All SATB buffers must
    // be empty.
    _verify_marked_complete_satb_empty,
  } VerifyMarked;

  typedef enum {
    // Disable forwarded objects verification.
    _verify_forwarded_disable,

    // Objects should not have forwardees.
    _verify_forwarded_none,

    // Objects may have forwardees.
    _verify_forwarded_allow
  } VerifyForwarded;

  typedef enum {
    // Disable collection set verification.
    _verify_cset_disable,

    // Should have no references to cset.
    _verify_cset_none,

    // May have references to cset, all should be forwarded.
    // Note: Allowing non-forwarded references to cset is equivalent
    // to _verify_cset_disable.
    _verify_cset_forwarded
  } VerifyCollectionSet;

  typedef enum {
    // Disable liveness verification
    _verify_liveness_disable,

    // All objects should belong to live regions
    _verify_liveness_conservative,

    // All objects should belong to live regions,
    // and liveness data should be accurate
    _verify_liveness_complete
  } VerifyLiveness;

  typedef enum {
    // Disable region verification
    _verify_regions_disable,

    // No trash regions allowed
    _verify_regions_notrash,

    // No collection set regions allowed
    _verify_regions_nocset,

    // No trash and no cset regions allowed
    _verify_regions_notrash_nocset
  } VerifyRegions;

  typedef enum {
    // Disable size verification
    _verify_size_disable,

    // Enforce exact consistency
    _verify_size_exact,

    // Expect promote-in-place adjustments: padding inserted to temporarily prevent further allocation in regular regions
    _verify_size_adjusted_for_padding,

    // Expected heap size should not include
    _verify_size_exact_including_trash
  } VerifySize;

  typedef enum {
    // Disable gc-state verification
    _verify_gcstate_disable,

    // Nothing is in progress, no forwarded objects
    _verify_gcstate_stable,

    // Nothing is in progress, no forwarded objects, weak roots handling
    _verify_gcstate_stable_weakroots,

    // Nothing is in progress, some objects are forwarded
    _verify_gcstate_forwarded,

    // Evacuation is done, some objects are forwarded, updating is in progress
    _verify_gcstate_updating
  } VerifyGCState;

  struct VerifyOptions {
    VerifyForwarded     _verify_forwarded;
    VerifyMarked        _verify_marked;
    VerifyCollectionSet _verify_cset;
    VerifyLiveness      _verify_liveness;
    VerifyRegions       _verify_regions;
    VerifyGCState       _verify_gcstate;

    VerifyOptions(VerifyForwarded verify_forwarded,
                  VerifyMarked verify_marked,
                  VerifyCollectionSet verify_collection_set,
                  VerifyLiveness verify_liveness,
                  VerifyRegions verify_regions,
                  VerifyGCState verify_gcstate) :
            _verify_forwarded(verify_forwarded), _verify_marked(verify_marked),
            _verify_cset(verify_collection_set),
            _verify_liveness(verify_liveness), _verify_regions(verify_regions),
            _verify_gcstate(verify_gcstate) {}
  };

private:
  void verify_at_safepoint(ShenandoahGeneration* generation,
                           const char* label,
                           VerifyRememberedSet remembered,
                           VerifyForwarded forwarded,
                           VerifyMarked marked,
                           VerifyCollectionSet cset,
                           VerifyLiveness liveness,
                           VerifyRegions regions,
                           VerifySize sizeness,
                           VerifyGCState gcstate);

public:
  ShenandoahVerifier(ShenandoahHeap* heap, MarkBitMap* verification_bitmap) :
          _heap(heap), _verification_bit_map(verification_bitmap) {};

  void verify_before_concmark(ShenandoahGeneration* generation);
  void verify_after_concmark(ShenandoahGeneration* generation);
  void verify_after_concmark_with_promotions(ShenandoahGeneration* generation);
  void verify_before_evacuation(ShenandoahGeneration* generation);
  void verify_before_update_refs(ShenandoahGeneration* generation);
  void verify_after_update_refs(ShenandoahGeneration* generation);
  void verify_before_fullgc(ShenandoahGeneration* generation);
  void verify_after_fullgc(ShenandoahGeneration* generation);
  void verify_after_degenerated(ShenandoahGeneration* generation);
  void verify_generic(ShenandoahGeneration* generation, VerifyOption option);

  // Roots should only contain to-space oops
  void verify_roots_in_to_space(ShenandoahGeneration* generation);
  void verify_roots_no_forwarded(ShenandoahGeneration* generation);

  // Check that generation usages are accurate before rebuilding free set
  void verify_before_rebuilding_free_set();
private:
  template<typename Scanner>
  void help_verify_region_rem_set(Scanner* scanner, ShenandoahHeapRegion* r,
                                  HeapWord* update_watermark, const char* message);

  void verify_rem_set_before_mark();
  void verify_rem_set_before_update_ref();
  void verify_rem_set_after_full_gc();
};

#endif // SHARE_GC_SHENANDOAH_SHENANDOAHVERIFIER_HPP
