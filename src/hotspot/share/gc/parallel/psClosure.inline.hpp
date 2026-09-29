/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_PARALLEL_PSCLOSURE_INLINE_HPP
#define SHARE_GC_PARALLEL_PSCLOSURE_INLINE_HPP

// No psClosure.hpp

#include "gc/parallel/psPromotionManager.inline.hpp"
#include "gc/parallel/psScavenge.hpp"
#include "memory/iterator.hpp"
#include "oops/access.inline.hpp"
#include "oops/oop.inline.hpp"
#include "utilities/globalDefinitions.hpp"

class PSAdjustWeakRootsClosure final: public OopClosure {
public:
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }

  virtual void do_oop(oop* p)       {
    oop o = RawAccess<>::oop_load(p);
    if (PSScavenge::is_obj_in_young(o)) {
      assert(!PSScavenge::is_obj_in_to_space(o), "Revisiting roots?");
      assert(o->is_forwarded(), "Objects are already forwarded before weak processing");
      oop new_obj = o->forwardee();
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
    }
  }
};

template <bool promote_immediately>
class PSRootsClosure: public OopClosure {
private:
  PSPromotionManager* _promotion_manager;

  template <class T> void do_oop_work(T *p) {
    assert(!ParallelScavengeHeap::heap()->is_in_reserved(p), "roots should be outside of heap");
    oop o = RawAccess<>::oop_load(p);
    if (PSScavenge::is_obj_in_young(o)) {
      assert(!PSScavenge::is_obj_in_to_space(o), "Revisiting roots?");
      oop new_obj = _promotion_manager->copy_to_survivor_space<promote_immediately>(o);
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);
    }
  }
public:
  PSRootsClosure(PSPromotionManager* pm) : _promotion_manager(pm) { }
  void do_oop(oop* p)       { PSRootsClosure::do_oop_work(p); }
  void do_oop(narrowOop* p) { PSRootsClosure::do_oop_work(p); }
};

typedef PSRootsClosure</*promote_immediately=*/false> PSScavengeRootsClosure;
typedef PSRootsClosure</*promote_immediately=*/true> PSPromoteRootsClosure;

// Scavenges a single oop in a ClassLoaderData.
class PSScavengeCLDOopClosure : public OopClosure {
  PSPromotionManager* _pm;

public:
  // Records whether this CLD contains oops pointing into young-gen after scavenging.
  bool _has_oops_into_young_gen;

  PSScavengeCLDOopClosure(PSPromotionManager* pm) : _pm(pm), _has_oops_into_young_gen(false) {}

  void do_oop(narrowOop* p) { ShouldNotReachHere(); }
  void do_oop(oop* p) {
    assert(!ParallelScavengeHeap::heap()->is_in_reserved(p), "GC barrier needed");

    oop o = RawAccess<>::oop_load(p);
    if (PSScavenge::is_obj_in_young(o)) {
      assert(!PSScavenge::is_obj_in_to_space(o), "Revisiting roots?");
      oop new_obj = _pm->copy_to_survivor_space</*promote_immediately=*/false>(o);
      RawAccess<IS_NOT_NULL>::oop_store(p, new_obj);

      if (!_has_oops_into_young_gen && PSScavenge::is_obj_in_young(new_obj)) {
        _has_oops_into_young_gen = true;
      }
    }
  }
};

// Scavenges the oop in a ClassLoaderData.
class PSScavengeCLDClosure: public CLDClosure {
  PSPromotionManager* _pm;

public:
  PSScavengeCLDClosure(PSPromotionManager* pm) : _pm(pm) { }

  void do_cld(ClassLoaderData* cld) {
    // If the cld has not been dirtied we know that there are
    // no references into the young gen, so we can skip it.
    if (!cld->has_modified_oops()) {
      return;
    }

    PSScavengeCLDOopClosure oop_closure{_pm};
    // Clean the cld since we're going to scavenge all the metadata.
    cld->oops_do(&oop_closure, ClassLoaderData::_claim_none, /*clear_modified_oops*/true);

    if (oop_closure._has_oops_into_young_gen) {
      cld->record_modified_oops();
    }
  }
};

#endif // SHARE_GC_PARALLEL_PSCLOSURE_INLINE_HPP
