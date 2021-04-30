/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zStat.hpp"
#include "gc/z/zVerify.hpp"
#include "memory/iterator.inline.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/globals.hpp"
#include "runtime/handles.hpp"
#include "runtime/javaThread.hpp"
#include "runtime/safepoint.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWatermark.inline.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/preserveException.hpp"

#define BAD_OOP_ARG(o, p)   "Bad oop " PTR_FORMAT " found at " PTR_FORMAT, untype(o), p2i(p)

static bool z_is_null_relaxed(zpointer o) {
  const uintptr_t color_mask = ZPointerAllMetadataMask | ZPointerReservedMask;
  return (untype(o) & ~color_mask) == 0;
}

static void z_verify_old_oop(zpointer* p) {
  zpointer o = *p;
  assert(o != zpointer::null, "Old should not contain raw null");
  if (!z_is_null_relaxed(o)) {
    if (!ZPointer::is_mark_good(o)) {
      // Old to old pointers are allowed to have bad young bits
      guarantee(ZPointer::is_marked_old(o),  BAD_OOP_ARG(o, p));
      guarantee(ZHeap::heap()->is_old(p), BAD_OOP_ARG(o, p));
    } else {
      zaddress addr = ZPointer::uncolor(o);
      if (ZHeap::heap()->is_young(addr)) {
        // If young collection was aborted, the GC does not guarantee
        // that all old-to-young pointers have remembered set entry.
        guarantee(ZAbort::should_abort() ||
                  ZGeneration::young()->is_remembered(p) ||
                  ZStoreBarrierBuffer::is_in(p), "Must be remembered");
      } else {
        guarantee(ZPointer::is_store_good(o) || (uintptr_t(o) & ZPointerRememberedMask) == ZPointerRememberedMask, "Must be remembered");
      }
      guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));
    }
  }
}

static void z_verify_young_oop(zpointer* p) {
  zpointer o = *p;
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
  zaddress o = *p;
  if (!is_null(o)) {
    z_verify_root_oop_object(o, p);
  }
}

static void z_verify_possibly_weak_oop(zpointer* p) {
  zpointer o = *p;
  if (!z_is_null_relaxed(o)) {
    //guarantee(ZPointer::is_store_good(o) || ZPointer::is_marked_finalizable(o), BAD_OOP_ARG(o, p));
    guarantee(ZPointer::is_marked_old(o) || ZPointer::is_marked_finalizable(o), BAD_OOP_ARG(o, p));

    const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(NULL, o);
    guarantee(ZHeap::heap()->is_young(addr) || ZHeap::heap()->is_object_live(addr), BAD_OOP_ARG(o, p));
    guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));
  }
}

class ZVerifyColoredRootClosure : public OopClosure {
private:
  const bool _verify_marked_old;

public:
  ZVerifyColoredRootClosure(bool verify_marked_old) :
      OopClosure(),
      _verify_marked_old(verify_marked_old) {}

  virtual void do_oop(oop* p_) {
    zpointer* p = (zpointer*)p_;

    assert(!ZHeap::heap()->is_in((uintptr_t)p), "Roots shouldn't be in heap");

    zpointer o = *p;

    if (z_is_null_relaxed(o)) {
      // Skip verifying nulls
      return;
    }

    assert(is_valid(o), "Catch me!");

    if (_verify_marked_old) {
      guarantee(ZPointer::is_marked_old(o), BAD_OOP_ARG(o, p));

      // Minor collections could have relocated the object;
      // use load barrier to find correct object.
      zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(NULL, o);
      z_verify_root_oop_object(addr, p);
    } else {
      // Don't know the state of the oop
      if (is_valid(o)) {
        // it looks like a valid colored oop;
        // use load barrier to find correct object.
        zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(NULL, o);
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
    zaddress* p = (zaddress*)p_;
    z_verify_uncolored_root_oop(p);
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }
};

class ZVerifyCodeBlobClosure : public CodeBlobToOopClosure {
public:
  ZVerifyCodeBlobClosure(OopClosure* cl) :
      CodeBlobToOopClosure(cl, false /* fix_relocations */) {}

  virtual void do_code_blob(CodeBlob* cb) {
    CodeBlobToOopClosure::do_code_blob(cb);
  }
};

class ZVerifyOldOopClosure : public BasicOopIterateClosure {
private:
  const bool _verify_weaks;

public:
  ZVerifyOldOopClosure(bool verify_weaks) :
      _verify_weaks(verify_weaks) {}

  virtual void do_oop(oop* p_) {
    zpointer* p = (zpointer*)p_;
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
  ZVerifyYoungOopClosure(bool verify_weaks) : _verify_weaks(verify_weaks) {}

  virtual void do_oop(oop* p_) {
    zpointer* p = (zpointer*)p_;
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
  ZVerifyThreadClosure(OopClosure* verify_cl) :
      _verify_cl(verify_cl) {}

  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
    ZStackWatermark* watermark = StackWatermarkSet::get<ZStackWatermark>(jt, StackWatermarkKind::gc);
    if (watermark->processing_started_acquire()) {
      thread->oops_do_no_frames(_verify_cl, NULL);

       if (watermark->processing_completed_acquire()) {
         thread->oops_do_frames(_verify_cl, NULL);
       }
    }
  }
};

class ZVerifyNMethodClosure : public NMethodClosure {
private:
  OopClosure* const        _cl;
  BarrierSetNMethod* const _bs_nm;

public:
  ZVerifyNMethodClosure(OopClosure* cl) :
      _cl(cl),
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
  ZVerifyObjectClosure(bool verify_weaks) :
      _verify_weaks(verify_weaks),
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
  ZHeap::heap()->object_and_field_iterate(&object_cl, &object_cl, verify_weaks);
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
    // Workaround OopMapCacheAllocation_lock reordering with the StackWatermark_lock
    DisableIsGCActiveMark mark;

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
