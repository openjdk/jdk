/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 */

#include "precompiled.hpp"
#include "classfile/classLoaderData.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "gc/z/zAddress.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zOop.hpp"
#include "gc/z/zResurrection.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zVerify.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "oops/oop.inline.hpp"

#define BAD_OOP_REPORT(addr)                                                \
    "Bad oop " PTR_FORMAT " found at " PTR_FORMAT ", expected " PTR_FORMAT, \
    addr, p2i(p), ZAddress::good(addr)

class ZVerifyRootsClosure : public ZRootsIteratorClosure {
public:
  virtual void do_oop(oop* p) {
    uintptr_t value = ZOop::to_address(*p);

    if (value == 0) {
      return;
    }

    guarantee(!ZAddress::is_finalizable(value), BAD_OOP_REPORT(value));
    guarantee(ZAddress::is_good(value), BAD_OOP_REPORT(value));
    guarantee(oopDesc::is_oop(ZOop::from_address(value)), BAD_OOP_REPORT(value));
  }
  virtual void do_oop(narrowOop*) { ShouldNotReachHere(); }
};

template <bool VisitReferents>
class ZVerifyOopClosure : public ClaimMetadataVisitingOopIterateClosure, public ZRootsIteratorClosure  {
public:
  ZVerifyOopClosure() :
      ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_other) {}

  virtual void do_oop(oop* p);
  virtual void do_oop(narrowOop* p) { ShouldNotReachHere(); }

  virtual ReferenceIterationMode reference_iteration_mode() {
    return VisitReferents ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }

#ifdef ASSERT
  // Verification handled by the closure itself
  virtual bool should_verify_oops() {
    return false;
  }
#endif
};

class ZVerifyObjectClosure : public ObjectClosure {
private:
  bool _visit_referents;

public:
  ZVerifyObjectClosure(bool visit_referents) : _visit_referents(visit_referents) {}
  virtual void do_object(oop o);
};

template <typename RootsIterator>
void ZVerify::roots_impl() {
  if (ZVerifyRoots) {
    ZVerifyRootsClosure cl;
    RootsIterator iter;
    iter.oops_do(&cl);
  }
}

void ZVerify::roots_strong() {
  roots_impl<ZRootsIterator>();
}

class ZVerifyConcurrentRootsIterator : public ZConcurrentRootsIterator {
public:
  ZVerifyConcurrentRootsIterator()
      : ZConcurrentRootsIterator(ClassLoaderData::_claim_none) {}
};

void ZVerify::roots_concurrent() {
  roots_impl<ZVerifyConcurrentRootsIterator>();
}

void ZVerify::roots_weak() {
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  roots_impl<ZWeakRootsIterator>();
}

void ZVerify::roots(bool verify_weaks) {
  roots_strong();
  roots_concurrent();
  if (verify_weaks) {
    roots_weak();
    roots_concurrent_weak();
  }
}

void ZVerify::objects(bool verify_weaks) {
  if (ZVerifyObjects) {
    ZVerifyObjectClosure cl(verify_weaks);
    ZHeap::heap()->object_iterate(&cl, verify_weaks);
  }
}

void ZVerify::roots_concurrent_weak() {
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  roots_impl<ZConcurrentWeakRootsIterator>();
}

void ZVerify::roots_and_objects(bool verify_weaks) {
  ZStatTimerDisable  _disable;

  roots(verify_weaks);
  objects(verify_weaks);
}

void ZVerify::before_zoperation() {
  // Verify strong roots
  ZStatTimerDisable disable;
  roots_strong();
}

void ZVerify::after_mark() {
  // Only verify strong roots and references.
  roots_and_objects(false /* verify_weaks */);
}

void ZVerify::after_weak_processing() {
  // Also verify weaks - all should have been processed at this point.
  roots_and_objects(true /* verify_weaks */);
}

template <bool VisitReferents>
void ZVerifyOopClosure<VisitReferents>::do_oop(oop* p) {
  guarantee(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  guarantee(ZGlobalPhase == ZPhaseMarkCompleted, "Invalid phase");
  guarantee(!ZResurrection::is_blocked(), "Invalid phase");

  const oop o = RawAccess<>::oop_load(p);
  if (o == NULL) {
    return;
  }

  const uintptr_t addr = ZOop::to_address(o);
  if (VisitReferents) {
    guarantee(ZAddress::is_good(addr) || ZAddress::is_finalizable_good(addr), BAD_OOP_REPORT(addr));
  } else {
    // Should not encounter finalizable oops through strong-only paths. Assumes only strong roots are visited.
    guarantee(ZAddress::is_good(addr), BAD_OOP_REPORT(addr));
  }

  const uintptr_t good_addr = ZAddress::good(addr);
  guarantee(oopDesc::is_oop(ZOop::from_address(good_addr)), BAD_OOP_REPORT(addr));
}

void ZVerifyObjectClosure::do_object(oop o) {
  if (_visit_referents) {
    ZVerifyOopClosure<true /* VisitReferents */> cl;
    o->oop_iterate((OopIterateClosure*)&cl);
  } else {
    ZVerifyOopClosure<false /* VisitReferents */> cl;
    o->oop_iterate(&cl);
  }
}
