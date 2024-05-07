/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gc_globals.hpp"
#include "gc/shared/isGCActiveMark.hpp"
#include "gc/z/zAddress.inline.hpp"
#include "gc/z/zGenerationId.hpp"
#include "gc/z/zHeap.inline.hpp"
#include "gc/z/zNMethod.hpp"
#include "gc/z/zPageAllocator.hpp"
#include "gc/z/zResurrection.hpp"
#include "gc/z/zRootsIterator.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zStoreBarrierBuffer.inline.hpp"
#include "gc/z/zStat.hpp"
#include "gc/z/zVerify.hpp"
#include "memory/allocation.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.hpp"
#include "runtime/javaThread.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWatermark.inline.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/preserveException.hpp"
#include "utilities/resourceHash.hpp"

#ifdef ASSERT

// Used to verify that safepoints operations can't be scheduled concurrently
// with callers to this function. Typically used to verify that object oops
// and headers are safe to access.
void z_verify_safepoints_are_blocked() {
  Thread* current = Thread::current();

  if (current->is_ConcurrentGC_thread()) {
    assert(current->is_suspendible_thread(), // Thread prevents safepoints
        "Safepoints are not blocked by current thread");

  } else if (current->is_Worker_thread()) {
    assert(// Check if ...
        // the thread prevents safepoints
        current->is_suspendible_thread() ||
        // the coordinator thread is the safepointing VMThread
        current->is_indirectly_safepoint_thread() ||
        // the coordinator thread prevents safepoints
        current->is_indirectly_suspendible_thread() ||
        // the RelocateQueue prevents safepoints
        //
        // RelocateQueue acts as a pseudo STS leaver/joiner and blocks
        // safepoints. There's currently no infrastructure  to check if the
        // current thread is active or not, so check the global states instead.
        ZGeneration::young()->is_relocate_queue_active() ||
        ZGeneration::old()->is_relocate_queue_active(),
        "Safepoints are not blocked by current thread");

  } else if (current->is_Java_thread()) {
    JavaThreadState state = JavaThread::cast(current)->thread_state();
    assert(state == _thread_in_Java || state == _thread_in_vm || state == _thread_new,
        "Safepoints are not blocked by current thread from state: %d", state);

  } else if (current->is_JfrSampler_thread()) {
    // The JFR sampler thread blocks out safepoints with this lock.
    assert_lock_strong(Threads_lock);

  } else if (current->is_VM_thread()) {
    // The VM Thread doesn't schedule new safepoints while executing
    // other safepoint or handshake operations.

  } else {
    fatal("Unexpected thread type");
  }
}

#endif

#define BAD_OOP_ARG(o, p)   "Bad oop " PTR_FORMAT " found at " PTR_FORMAT, untype(o), p2i(p)

static bool z_is_null_relaxed(zpointer o) {
  const uintptr_t color_mask = ZPointerAllMetadataMask | ZPointerReservedMask;
  return (untype(o) & ~color_mask) == 0;
}

static void z_verify_old_oop(zpointer* p) {
  const zpointer o = *p;
  assert(o != zpointer::null, "Old should not contain raw null");
  if (!z_is_null_relaxed(o)) {
    if (ZPointer::is_mark_good(o)) {
      // Even though the pointer is mark good, we can't verify that it should
      // be in the remembered set in old mark end. We have to wait to the verify
      // safepoint after reference processing, where we hold the driver lock and
      // know there is no concurrent remembered set processing in the young generation.
      const zaddress addr = ZPointer::uncolor(o);
      guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));
    } else {
      const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(nullptr, o);
      // Old to young pointers might not be mark good if the young
      // marking has not finished, which is responsible for coloring
      // these pointers.
      if (ZHeap::heap()->is_old(addr) || !ZGeneration::young()->is_phase_mark()) {
        // Old to old pointers are allowed to have bad young bits
        guarantee(ZPointer::is_marked_old(o),  BAD_OOP_ARG(o, p));
        guarantee(ZHeap::heap()->is_old(p), BAD_OOP_ARG(o, p));
      }
    }
  }
}

static void z_verify_young_oop(zpointer* p) {
  const zpointer o = *p;
  if (!z_is_null_relaxed(o)) {
    guarantee(ZHeap::heap()->is_young(p), BAD_OOP_ARG(o, p));
    guarantee(ZPointer::is_marked_young(o),  BAD_OOP_ARG(o, p));

    if (ZPointer::is_load_good(o)) {
      guarantee(oopDesc::is_oop(to_oop(ZPointer::uncolor(o))), BAD_OOP_ARG(o, p));
    }
  }
}

static void z_verify_root_oop_object(zaddress o, void* p) {
  guarantee(oopDesc::is_oop(to_oop(o)), BAD_OOP_ARG(o, p));
}

static void z_verify_uncolored_root_oop(zaddress* p) {
  assert(!ZHeap::heap()->is_in((uintptr_t)p), "Roots shouldn't be in heap");
  const zaddress o = *p;
  if (!is_null(o)) {
    z_verify_root_oop_object(o, p);
  }
}

static void z_verify_possibly_weak_oop(zpointer* p) {
  const zpointer o = *p;
  if (!z_is_null_relaxed(o)) {
    guarantee(ZPointer::is_marked_old(o) || ZPointer::is_marked_finalizable(o), BAD_OOP_ARG(o, p));

    const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(nullptr, o);
    guarantee(ZHeap::heap()->is_old(addr) || ZPointer::is_marked_young(o), BAD_OOP_ARG(o, p));
    guarantee(ZHeap::heap()->is_young(addr) || ZHeap::heap()->is_object_live(addr), BAD_OOP_ARG(o, p));
    guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));

    // Verify no missing remset entries. We are holding the driver lock here and that
    // allows us to more precisely verify the remembered set, as there is no concurrent
    // young generation collection going on at this point.
    const uintptr_t remset_bits = untype(o) & ZPointerRememberedMask;
    const uintptr_t prev_remembered = ZPointerRemembered ^ ZPointerRememberedMask;
    guarantee(remset_bits != prev_remembered, BAD_OOP_ARG(o, p));
    guarantee(remset_bits == ZPointerRememberedMask ||
              ZGeneration::young()->is_remembered(p) ||
              ZStoreBarrierBuffer::is_in(p), BAD_OOP_ARG(o, p));
  }
}

class ZVerifyColoredRootClosure : public OopClosure {
private:
  const bool _verify_marked_old;

public:
  ZVerifyColoredRootClosure(bool verify_marked_old)
    : OopClosure(),
      _verify_marked_old(verify_marked_old) {}

  virtual void do_oop(oop* p_) {
    zpointer* const p = (zpointer*)p_;

    assert(!ZHeap::heap()->is_in((uintptr_t)p), "Roots shouldn't be in heap");

    const zpointer o = *p;

    if (z_is_null_relaxed(o)) {
      // Skip verifying nulls
      return;
    }

    assert(is_valid(o), "Catch me!");

    if (_verify_marked_old) {
      guarantee(ZPointer::is_marked_old(o), BAD_OOP_ARG(o, p));

      // Minor collections could have relocated the object;
      // use load barrier to find correct object.
      const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(nullptr, o);
      z_verify_root_oop_object(addr, p);
    } else {
      // Don't know the state of the oop
      if (is_valid(o)) {
        // it looks like a valid colored oop;
        // use load barrier to find correct object.
        const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(nullptr, o);
        z_verify_root_oop_object(addr, p);
      }
    }
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }
};

class ZVerifyUncoloredRootClosure : public OopClosure {
public:
  virtual void do_oop(oop* p_) {
    zaddress* const p = (zaddress*)p_;
    z_verify_uncolored_root_oop(p);
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }
};

class ZVerifyOldOopClosure : public BasicOopIterateClosure {
private:
  const bool _verify_weaks;

public:
  ZVerifyOldOopClosure(bool verify_weaks)
    : _verify_weaks(verify_weaks) {}

  virtual void do_oop(oop* p_) {
    zpointer* const p = (zpointer*)p_;
    if (_verify_weaks) {
      z_verify_possibly_weak_oop(p);
    } else {
      // We should never encounter finalizable oops through strong
      // paths. This assumes we have only visited strong roots.
      z_verify_old_oop(p);
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual ReferenceIterationMode reference_iteration_mode() {
    return _verify_weaks ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }
};

class ZVerifyYoungOopClosure : public BasicOopIterateClosure {
private:
  const bool _verify_weaks;

public:
  ZVerifyYoungOopClosure(bool verify_weaks)
    : _verify_weaks(verify_weaks) {}

  virtual void do_oop(oop* p_) {
    zpointer* const p = (zpointer*)p_;
    if (_verify_weaks) {
      //z_verify_possibly_weak_oop(p);
      z_verify_young_oop(p);
    } else {
      // We should never encounter finalizable oops through strong
      // paths. This assumes we have only visited strong roots.
      z_verify_young_oop(p);
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual ReferenceIterationMode reference_iteration_mode() {
    return _verify_weaks ? DO_FIELDS : DO_FIELDS_EXCEPT_REFERENT;
  }

  // Don't follow this metadata when verifying oops
  virtual void do_method(Method* m) {}
  virtual void do_nmethod(nmethod* nm) {}
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> ZVerifyCLDClosure;

class ZVerifyThreadClosure : public ThreadClosure {
private:
  OopClosure* const _verify_cl;

public:
  ZVerifyThreadClosure(OopClosure* verify_cl)
    : _verify_cl(verify_cl) {}

  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
    const ZStackWatermark* const watermark = StackWatermarkSet::get<ZStackWatermark>(jt, StackWatermarkKind::gc);
    if (watermark->processing_started_acquire()) {
      thread->oops_do_no_frames(_verify_cl, nullptr);

       if (watermark->processing_completed_acquire()) {
         thread->oops_do_frames(_verify_cl, nullptr);
       }
    }
  }
};

class ZVerifyNMethodClosure : public NMethodClosure {
private:
  OopClosure* const        _cl;
  BarrierSetNMethod* const _bs_nm;

public:
  ZVerifyNMethodClosure(OopClosure* cl)
    : _cl(cl),
      _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

  virtual void do_nmethod(nmethod* nm) {
    if (_bs_nm->is_armed(nm)) {
      // Can't verify
      return;
    }

    ZNMethod::nmethod_oops_do(nm, _cl);
  }
};

void ZVerify::roots_strong(bool verify_after_old_mark) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");

  {
    ZVerifyColoredRootClosure cl(verify_after_old_mark);
    ZVerifyCLDClosure cld_cl(&cl);

    ZRootsIteratorStrongColored roots_strong_colored(ZGenerationIdOptional::none);
    roots_strong_colored.apply(&cl,
                               &cld_cl);
  }

  {
    ZVerifyUncoloredRootClosure cl;
    ZVerifyThreadClosure thread_cl(&cl);
    ZVerifyNMethodClosure nm_cl(&cl);

    ZRootsIteratorStrongUncolored roots_strong_uncolored(ZGenerationIdOptional::none);
    roots_strong_uncolored.apply(&thread_cl,
                                 &nm_cl);
  }
}

void ZVerify::roots_weak() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  ZVerifyColoredRootClosure cl(true /* verify_after_old_mark*/);
  ZRootsIteratorWeakColored roots_weak_colored(ZGenerationIdOptional::none);
  roots_weak_colored.apply(&cl);
}

zaddress zverify_broken_object = zaddress::null;

class ZVerifyObjectClosure : public ObjectClosure, public OopFieldClosure {
private:
  const bool         _verify_weaks;

  zaddress           _visited_base;
  volatile zpointer* _visited_p;
  zpointer           _visited_ptr_pre_loaded;

public:
  ZVerifyObjectClosure(bool verify_weaks)
    : _verify_weaks(verify_weaks),
      _visited_base(),
      _visited_p(),
      _visited_ptr_pre_loaded() {}

  void log_dead_object(zaddress addr) {
    tty->print_cr("ZVerify found dead object: " PTR_FORMAT " at p: " PTR_FORMAT " ptr: " PTR_FORMAT, untype(addr), p2i((void*)_visited_p), untype(_visited_ptr_pre_loaded));
    to_oop(addr)->print();
    tty->print_cr("--- From --- ");
    if (_visited_base != zaddress::null) {
      to_oop(_visited_base)->print();
    }
    tty->cr();

    if (zverify_broken_object == zaddress::null) {
      zverify_broken_object = addr;
    }
  }

  void verify_live_object(oop obj) {
    // Verify that its pointers are sane
    ZVerifyOldOopClosure cl(_verify_weaks);
    ZIterator::oop_iterate_safe(obj, &cl);
  }

  virtual void do_object(oop obj) {
    guarantee(oopDesc::is_oop_or_null(obj), "Must be");

    const zaddress addr = to_zaddress(obj);
    if (ZHeap::heap()->is_old(addr)) {
      if (ZHeap::heap()->is_object_live(addr)) {
        verify_live_object(obj);
      } else {
        log_dead_object(addr);
      }
    } else {
      // Young object - no verification
    }
  }

  virtual void do_field(oop base, oop* p) {
    _visited_base = to_zaddress(base);
    _visited_p = (volatile zpointer*)p;
    _visited_ptr_pre_loaded = Atomic::load(_visited_p);
  }
};

void ZVerify::threads_start_processing() {
  class StartProcessingClosure : public ThreadClosure {
  public:
    void do_thread(Thread* thread) {
      StackWatermarkSet::start_processing(JavaThread::cast(thread), StackWatermarkKind::gc);
    }
  };

  ZJavaThreadsIterator threads_iterator(ZGenerationIdOptional::none);
  StartProcessingClosure cl;
  threads_iterator.apply(&cl);
}

void ZVerify::objects(bool verify_weaks) {
  if (ZAbort::should_abort()) {
    // Invariants might be a bit mushy if the young generation
    // collection was forced to shut down. So let's be a bit forgiving here.
    return;
  }
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(ZGeneration::young()->is_phase_mark_complete() ||
         ZGeneration::old()->is_phase_mark_complete(), "Invalid phase");
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  // Note that object verification will fix the pointers and
  // only verify that the resulting objects are sane.

  // The verification VM_Operation doesn't start the thread processing.
  // Do it here, after the roots have been verified.
  threads_start_processing();

  ZVerifyObjectClosure object_cl(verify_weaks);
  ZHeap::heap()->object_and_field_iterate_for_verify(&object_cl, &object_cl, verify_weaks);
}

void ZVerify::before_zoperation() {
  // Verify strong roots
  if (ZVerifyRoots) {
    roots_strong(false /* verify_after_old_mark */);
  }
}

void ZVerify::after_mark() {
  // Verify all strong roots and strong references
  if (ZVerifyRoots) {
    roots_strong(true /* verify_after_old_mark */);
  }
  if (ZVerifyObjects) {
    objects(false /* verify_weaks */);
    guarantee(zverify_broken_object == zaddress::null, "Verification failed");
  }
}

void ZVerify::after_weak_processing() {
  // Verify all roots and all references
  if (ZVerifyRoots) {
    roots_strong(true /* verify_after_old_mark */);
    roots_weak();
  }
  if (ZVerifyObjects) {
    objects(true /* verify_weaks */);
  }
}

//
// Remembered set verification
//

typedef ResourceHashtable<volatile zpointer*, bool, 1009, AnyObj::C_HEAP, mtGC> ZStoreBarrierBufferTable;

static ZStoreBarrierBufferTable* z_verify_store_barrier_buffer_table = nullptr;

#define BAD_REMSET_ARG(p, ptr, addr) \
  "Missing remembered set at " PTR_FORMAT " pointing at " PTR_FORMAT \
  " (" PTR_FORMAT " + " INTX_FORMAT ")" \
  , p2i(p), untype(ptr), untype(addr), p2i(p) - untype(addr)

class ZVerifyRemsetBeforeOopClosure : public BasicOopIterateClosure {
private:
  ZForwarding*    _forwarding;
  zaddress_unsafe _from_addr;

public:
  ZVerifyRemsetBeforeOopClosure(ZForwarding* forwarding)
    : _forwarding(forwarding),
      _from_addr(zaddress_unsafe::null) {}

  void set_from_addr(zaddress_unsafe addr) {
    _from_addr = addr;
  }

  virtual void do_oop(oop* p_) {
    volatile zpointer* const p = (volatile zpointer*)p_;
    const zpointer ptr = *p;

    if (ZPointer::is_remembered_exact(ptr)) {
      // When the remembered bits are 11, it means that it is intentionally
      // not part of the remembered set
      return;
    }

    if (ZBufferStoreBarriers && z_verify_store_barrier_buffer_table->get(p) != nullptr) {
      // If this oop location is in the store barrier buffer, we can't assume
      // that it should have a remset entry
      return;
    }

    if (_forwarding->find(_from_addr) != zaddress::null) {
      // If the mutator has already relocated the object to to-space, we defer
      // and do to-space verification afterwards instead, because store barrier
      // buffers could have installed the remembered set entry in to-space and
      // then flushed the store barrier buffer, and then start young marking
      return;
    }

    ZPage* page = _forwarding->page();

    if (ZGeneration::old()->active_remset_is_current()) {
      guarantee(page->is_remembered(p), BAD_REMSET_ARG(p, ptr, _from_addr));
    } else {
      guarantee(page->was_remembered(p), BAD_REMSET_ARG(p, ptr, _from_addr));
    }
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual ReferenceIterationMode reference_iteration_mode() {
    return DO_FIELDS;
  }
};

void ZVerify::on_color_flip() {
  if (!ZVerifyRemembered || !ZBufferStoreBarriers) {
    return;
  }

  // Reset the table tracking the stale stores of the store barrier buffer
  delete z_verify_store_barrier_buffer_table;
  z_verify_store_barrier_buffer_table = new (mtGC) ZStoreBarrierBufferTable();

  // Gather information from store barrier buffers as we currently can't verify
  // remset entries for oop locations touched by the store barrier buffer

  for (JavaThreadIteratorWithHandle jtiwh; JavaThread* const jt = jtiwh.next(); ) {
    const ZStoreBarrierBuffer* const buffer = ZThreadLocalData::store_barrier_buffer(jt);

    for (int i = buffer->current(); i < (int)ZStoreBarrierBuffer::_buffer_length; ++i) {
      volatile zpointer* const p = buffer->_buffer[i]._p;
      bool created = false;
      z_verify_store_barrier_buffer_table->put_if_absent(p, true, &created);
    }
  }
}

void ZVerify::before_relocation(ZForwarding* forwarding) {
  if (!ZVerifyRemembered) {
    return;
  }

  if (forwarding->from_age() != ZPageAge::old) {
    // Only supports verification of old-to-old relocations now
    return;
  }

  // Verify that the inactive remset is cleared
  if (ZGeneration::old()->active_remset_is_current()) {
    forwarding->page()->verify_remset_cleared_previous();
  } else {
    forwarding->page()->verify_remset_cleared_current();
  }

  ZVerifyRemsetBeforeOopClosure cl(forwarding);

  forwarding->object_iterate([&](oop obj) {
    const zaddress_unsafe addr = to_zaddress_unsafe(cast_from_oop<uintptr_t>(obj));
    cl.set_from_addr(addr);
    obj->oop_iterate(&cl);
  });
}

class ZVerifyRemsetAfterOopClosure : public BasicOopIterateClosure {
private:
  ZForwarding* const _forwarding;
  zaddress_unsafe    _from_addr;
  zaddress           _to_addr;

public:
  ZVerifyRemsetAfterOopClosure(ZForwarding* forwarding)
    : _forwarding(forwarding),
      _from_addr(zaddress_unsafe::null),
      _to_addr(zaddress::null) {}

  void set_from_addr(zaddress_unsafe addr) {
    _from_addr = addr;
  }

  void set_to_addr(zaddress addr) {
    _to_addr = addr;
  }

  virtual void do_oop(oop* p_) {
    volatile zpointer* const p = (volatile zpointer*)p_;
    const zpointer ptr = Atomic::load(p);

    // Order this load w.r.t. the was_remembered load which can race when
    // the remset scanning of the to-space object is concurrently forgetting
    // an entry.
    OrderAccess::loadload();

    if (ZPointer::is_remembered_exact(ptr)) {
      // When the remembered bits are 11, it means that it is intentionally
      // not part of the remembered set
      return;
    }

    if (ZPointer::is_store_good(ptr)) {
      // In to-space, there could be stores racing with the verification.
      // Such stores may not have reliably manifested in the remembered
      // sets yet.
      return;
    }

    if (ZBufferStoreBarriers && z_verify_store_barrier_buffer_table->get(p) != nullptr) {
      // If this to-space oop location is in the store barrier buffer, we
      // can't assume that it should have a remset entry
      return;
    }

    const uintptr_t p_offset = uintptr_t(p) - untype(_to_addr);
    volatile zpointer* const fromspace_p = (volatile zpointer*)(untype(_from_addr) + p_offset);

    if (ZBufferStoreBarriers && z_verify_store_barrier_buffer_table->get(fromspace_p) != nullptr) {
      // If this from-space oop location is in the store barrier buffer, we
      // can't assume that it should have a remset entry
      return;
    }

    ZPage* page = ZHeap::heap()->page(p);

    if (page->is_remembered(p) || page->was_remembered(p)) {
      // No missing remembered set entry
      return;
    }

    OrderAccess::loadload();
    if (Atomic::load(p) != ptr) {
      // Order the was_remembered bitmap load w.r.t. the reload of the zpointer.
      // Sometimes the was_remembered() call above races with clearing of the
      // previous bits, when the to-space object is concurrently forgetting
      // remset entries because they were not so useful. When that happens,
      // we have already self healed the pointers to have 11 in the remset
      // bits.
      return;
    }

    guarantee(ZGeneration::young()->is_phase_mark(), "Should be in the mark phase " BAD_REMSET_ARG(p, ptr, _to_addr));
    guarantee(_forwarding->relocated_remembered_fields_published_contains(p), BAD_REMSET_ARG(p, ptr, _to_addr));
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  virtual ReferenceIterationMode reference_iteration_mode() {
    return DO_FIELDS;
  }
};

void ZVerify::after_relocation_internal(ZForwarding* forwarding) {
  ZVerifyRemsetAfterOopClosure cl(forwarding);

  forwarding->address_unsafe_iterate_via_table([&](zaddress_unsafe from_addr) {
    // If no field in this object was in the store barrier buffer
    // when relocation started, we should be able to verify trivially
    ZGeneration* const from_generation = forwarding->from_age() == ZPageAge::old ? (ZGeneration*)ZGeneration::old()
                                                                           : (ZGeneration*)ZGeneration::young();
    const zaddress to_addr = from_generation->remap_object(from_addr);

    cl.set_from_addr(from_addr);
    cl.set_to_addr(to_addr);
    const oop to_obj = to_oop(to_addr);
    to_obj->oop_iterate(&cl);
  });
}

void ZVerify::after_relocation(ZForwarding* forwarding) {
  if (!ZVerifyRemembered) {
    return;
  }

  if (forwarding->to_age() != ZPageAge::old) {
    // No remsets to verify in the young gen
    return;
  }

  if (ZGeneration::young()->is_phase_mark() &&
      forwarding->relocated_remembered_fields_is_concurrently_scanned()) {
    // Can't verify to-space objects if concurrent YC rejected published
    // remset information, because that data is incomplete. The YC might
    // not have finished scanning the forwarding, and might be about to
    // insert required remembered set entries.
    return;
  }

  after_relocation_internal(forwarding);
}

void ZVerify::after_scan(ZForwarding* forwarding) {
  if (!ZVerifyRemembered) {
    return;
  }

  if (ZAbort::should_abort()) {
    // We can't verify remembered set accurately when shutting down the VM
    return;
  }

  if (!ZGeneration::old()->is_phase_relocate() ||
      !forwarding->relocated_remembered_fields_is_concurrently_scanned()) {
    // Only verify remembered set from remembered set scanning, when the
    // remembered set scanning rejected the publishing information of concurrent
    // old generation relocation
    return;
  }

  after_relocation_internal(forwarding);
}
