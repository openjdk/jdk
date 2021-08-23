/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.inline.hpp"
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
#include "runtime/safepoint.hpp"
#include "runtime/stackFrameStream.inline.hpp"
#include "runtime/stackWatermark.inline.hpp"
#include "runtime/stackWatermarkSet.inline.hpp"
#include "runtime/thread.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/preserveException.hpp"

#define BAD_OOP_ARG(o, p)   "Bad oop " PTR_FORMAT " found at " PTR_FORMAT, untype(o), p2i(p)

static bool z_is_null_relaxed(zpointer o) {
  const uintptr_t color_mask = ZAddressAllMetadataMask | ZAddressReservedMask;
  return (untype(o) & ~color_mask) == 0;
}

static void z_verify_old_oop(zpointer* p) {
  zpointer o = *p;
  assert(o != zpointer::null, "Old should not contain raw null");
  if (!z_is_null_relaxed(o)) {
    if (!ZPointer::is_mark_good(o)) {
      // Old to old pointers are allowed to have bad minor bits
      guarantee(ZPointer::is_marked_major(o),  BAD_OOP_ARG(o, p));
      guarantee(ZHeap::heap()->page(zaddress(uintptr_t(p)))->is_old(), BAD_OOP_ARG(o, p));
    } else {
      zaddress addr = ZPointer::uncolor(o);
      if (ZHeap::heap()->is_young(addr)) {
        assert(ZHeap::heap()->is_remembered(p) || ZStoreBarrierBuffer::is_in(p), "Must be remembered");
      } else {
        assert(ZPointer::is_store_good(o) || (uintptr_t(o) & ZAddressRememberedMask) == ZAddressRememberedMask, "Must be remembered");
      }
      guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));
    }
  }
}

static void z_verify_young_oop(zpointer* p) {
  zpointer o = *p;
  if (!z_is_null_relaxed(o)) {
    guarantee(ZHeap::heap()->page(zaddress(uintptr_t(p)))->is_young(), BAD_OOP_ARG(o, p));
    guarantee(ZPointer::is_marked_minor(o),  BAD_OOP_ARG(o, p));

    if (ZPointer::is_load_good(o)) {
      guarantee(oopDesc::is_oop(to_oop(ZPointer::uncolor(o))), BAD_OOP_ARG(o, p));
    }
  }
}

static void z_verify_root_oop_object(zaddress o, void* p) {
  guarantee(oopDesc::is_oop(to_oop(o)), BAD_OOP_ARG(o, p));
}

static void z_verify_root_oop(zpointer* p) {
  assert(!ZHeap::heap()->is_in((uintptr_t)p), "Roots shouldn't be in heap");
  zpointer o = *p;
  if (!z_is_null_relaxed(o)) {
    guarantee(ZPointer::is_marked_major(o), BAD_OOP_ARG(o, p));
    //z_verify_root_oop_object(ZPointer::uncolor(o), p);
    z_verify_root_oop_object(ZBarrier::load_barrier_on_oop_field_preloaded(NULL, o), p);
  }
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
    guarantee(ZPointer::is_marked_major(o) || ZPointer::is_marked_finalizable(o), BAD_OOP_ARG(o, p));

    const zaddress addr = ZBarrier::load_barrier_on_oop_field_preloaded(NULL, o);
    guarantee(ZHeap::heap()->is_young(addr) || ZHeap::heap()->is_object_live(addr), BAD_OOP_ARG(o, p));
    guarantee(oopDesc::is_oop(to_oop(addr)), BAD_OOP_ARG(o, p));
  }
}

class ZVerifyRootClosure : public OopClosure {
private:
  const bool _verify_fixed;

public:
  ZVerifyRootClosure(bool verify_fixed) :
      _verify_fixed(verify_fixed) {}

  bool verify_fixed() const {
    return _verify_fixed;
  }
};

class ZVerifyColoredRootClosure : public ZVerifyRootClosure {
public:
  ZVerifyColoredRootClosure(bool verify_fixed) :
      ZVerifyRootClosure(verify_fixed) {}

  virtual void do_oop(oop* p_) {
    zpointer* p = (zpointer*)p_;
    if (verify_fixed()) {
      z_verify_root_oop(p);
    } else {
      // Don't know the state of the oop.
      zpointer o = *p;
      if (!z_is_null_relaxed(o) && is_valid(o)) {
        // colored root
        oop obj = NativeAccess<AS_NO_KEEPALIVE>::oop_load(p_);
        z_verify_root_oop_object(to_zaddress(obj), p);
      }
    }
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }
};

class ZVerifyUncoloredRootClosure : public ZVerifyRootClosure {
public:
  ZVerifyUncoloredRootClosure(bool verify_fixed) :
      ZVerifyRootClosure(verify_fixed) {}

  virtual void do_oop(oop* p_) {
    zaddress* p = (zaddress*)p_;
    if (verify_fixed()) {
      z_verify_uncolored_root_oop(p);
    } else {
      fatal("Unimplemented");
#if 0
      // Don't know the state of the oop.
      oop obj = *p;
      if (obj != NULL && ZOop::is_valid_zaddress(obj)) {
        ZUncoloredRoot::remap(&obj);
        z_verify_root_oop_object(to_zaddress(obj), p);
      }
#endif
    }
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }
};

class ZVerifyCodeBlobClosure : public CodeBlobToOopClosure {
public:
  ZVerifyCodeBlobClosure(ZVerifyRootClosure* cl) :
      CodeBlobToOopClosure(cl, false /* fix_relocations */) {}

  virtual void do_code_blob(CodeBlob* cb) {
    CodeBlobToOopClosure::do_code_blob(cb);
  }
};

class ZVerifyStack : public OopClosure {
private:
  ZVerifyRootClosure* const _cl;
  JavaThread*         const _jt;
  uint64_t                  _last_good;
  bool                      _verifying_bad_frames;

public:
  ZVerifyStack(ZVerifyRootClosure* cl, JavaThread* jt) :
      _cl(cl),
      _jt(jt),
      _last_good(0),
      _verifying_bad_frames(false) {
    ZStackWatermark* const stack_watermark = StackWatermarkSet::get<ZStackWatermark>(jt, StackWatermarkKind::gc);

    if (_cl->verify_fixed()) {
      assert(stack_watermark->processing_started(), "Should already have been fixed");
      assert(stack_watermark->processing_completed(), "Should already have been fixed");
    } else {
      // We don't really know the state of the stack, verify watermark.
      if (!stack_watermark->processing_started()) {
        _verifying_bad_frames = true;
      } else {
        // Not time yet to verify bad frames
        _last_good = stack_watermark->last_processed();
      }
    }
  }

  void do_oop(oop* p) {
    if (_verifying_bad_frames) {
      const zaddress prev = *(zaddress*)p;
      guarantee(!is_valid(prev), BAD_OOP_ARG(prev, p));
    } else {
      _cl->do_oop(p);
    }
  }

  void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }

  void prepare_next_frame(frame& frame) {
    if (_cl->verify_fixed()) {
      // All frames need to be good
      return;
    }

    // The verification has two modes, depending on whether we have reached the
    // last processed frame or not. Before it is reached, we expect everything to
    // be good. After reaching it, we expect everything to be bad.
    const uintptr_t sp = reinterpret_cast<uintptr_t>(frame.sp());

    if (!_verifying_bad_frames && sp == _last_good) {
      // Found the last good frame, now verify the bad ones
      _verifying_bad_frames = true;
    }
  }

  void verify_frames() {
    ZVerifyCodeBlobClosure cb_cl(_cl);
    for (StackFrameStream frames(_jt, true /* update */, false /* process_frames */);
         !frames.is_done();
         frames.next()) {
      frame& frame = *frames.current();
      frame.oops_do(this, &cb_cl, frames.register_map(), DerivedPointerIterationMode::_ignore);
      prepare_next_frame(frame);
    }
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
};

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> ZVerifyCLDClosure;

class ZVerifyThreadClosure : public ThreadClosure {
private:
  ZVerifyRootClosure* const _cl;

public:
  ZVerifyThreadClosure(ZVerifyRootClosure* cl) :
      _cl(cl) {}

  virtual void do_thread(Thread* thread) {
    JavaThread* const jt = JavaThread::cast(thread);
    ZStackWatermark* watermark = StackWatermarkSet::get<ZStackWatermark>(jt, StackWatermarkKind::gc);
    if (!watermark->processing_started_acquire()) {
      return;
    }

    thread->oops_do_no_frames(_cl, NULL);

    if (!jt->has_last_Java_frame()) {
      return;
    }

    if (watermark->processing_completed_acquire()) {
      ZVerifyStack verify_stack(_cl, jt);
      verify_stack.verify_frames();
    }
  }
};

class ZVerifyNMethodClosure : public NMethodClosure {
private:
  OopClosure* const        _cl;
  BarrierSetNMethod* const _bs_nm;
  const bool               _verify_fixed;

  bool trust_nmethod_state() const {
    // The root iterator will visit non-processed
    // nmethods class unloading is turned off.
    return ClassUnloading || _verify_fixed;
  }

public:
  ZVerifyNMethodClosure(OopClosure* cl, bool verify_fixed) :
      _cl(cl),
      _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()),
      _verify_fixed(verify_fixed) {}

  virtual void do_nmethod(nmethod* nm) {
    assert(!trust_nmethod_state() || !_bs_nm->is_armed(nm), "Should not encounter any armed nmethods");

    ZNMethod::nmethod_oops_do(nm, _cl);
  }
};

void ZVerify::roots_strong(bool verify_fixed) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  {
    ZVerifyColoredRootClosure cl(verify_fixed);
    ZVerifyCLDClosure cld_cl(&cl);

    ZColoredRootsStrongIterator iter;
    iter.apply(&cl,
               &cld_cl);
  }
  // FIXME: Only verify_fixed == true supported right now
  if (verify_fixed) {
    ZVerifyUncoloredRootClosure cl(verify_fixed);
    ZVerifyThreadClosure thread_cl(&cl);
    ZVerifyNMethodClosure nm_cl(&cl, verify_fixed);

    ZUncoloredRootsStrongIterator iter;
    iter.apply(&thread_cl,
               &nm_cl);
  }
}

void ZVerify::roots_weak() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  ZVerifyColoredRootClosure cl(true /* verify_fixed */);
  ZWeakRootsIterator iter;
  iter.apply(&cl);
}

zaddress zverify_broken_object = zaddress::null;

class ZVerifyObjectClosure : public ObjectClosure, public OopFieldClosure {
private:
  const bool         _verify_weaks;

  zaddress           _visited_base;
  volatile zpointer* _visited_p;
  zpointer           _visited_p_pre_loaded;

public:
  ZVerifyObjectClosure(bool verify_weaks) :
      _verify_weaks(verify_weaks),
      _visited_base(),
      _visited_p(),
      _visited_p_pre_loaded() {}

  bool check_object(zaddress addr) {
    if (ZHeap::heap()->is_object_live(addr)) {
      return true;
    }

    tty->print_cr("ZVerify found dead object: " PTR_FORMAT " at p: " PTR_FORMAT " ptr: " PTR_FORMAT, untype(addr), p2i((void*)_visited_p), untype(_visited_p_pre_loaded));
    to_oop(addr)->print();
    tty->print_cr("--- From --- ");
    if (_visited_base != zaddress::null) {
      to_oop(_visited_base)->print();
    }
    tty->cr();

    if (zverify_broken_object == zaddress::null) {
      zverify_broken_object = addr;
    }

    return false;
  }

  virtual void do_object(oop obj) {
    const zaddress addr = to_zaddress(obj);
    if (ZHeap::heap()->is_old(addr)) {
      if (check_object(addr)) {
        ZVerifyOldOopClosure cl(_verify_weaks);
        ZIterator::oop_iterate_safe(obj, &cl);
      }
    } else {

    }
  }

  virtual void do_field(oop base, oop* p) {
    _visited_base = to_zaddress(base);
    _visited_p = (volatile zpointer*)p;
    _visited_p_pre_loaded = Atomic::load(_visited_p);
  }
};

void ZVerify::objects(bool verify_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(ZHeap::heap()->minor_collector()->phase() == ZPhase::MarkComplete ||
         ZHeap::heap()->major_collector()->phase() == ZPhase::MarkComplete, "Invalid phase");
  assert(!ZResurrection::is_blocked(), "Invalid phase");

  ZVerifyObjectClosure object_cl(verify_weaks);
  ZHeap::heap()->object_and_field_iterate(&object_cl, &object_cl, verify_weaks);
}

void ZVerify::before_zoperation() {
  // Verify strong roots
  ZStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(false /* verify_fixed */);
  }
}

void ZVerify::after_mark() {
  // Verify all strong roots and strong references
  ZStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(true /* verify_fixed */);
  }
  if (ZVerifyObjects) {
    objects(false /* verify_weaks */);
    guarantee(zverify_broken_object == zaddress::null, "Verification failed");
  }
}

void ZVerify::after_weak_processing() {
  // Verify all roots and all references
  ZStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(true /* verify_fixed */);
    roots_weak();
  }
  if (ZVerifyObjects) {
    objects(true /* verify_weaks */);
  }
}

#ifdef ASSERT

class ZVerifyBadOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    const oop o = *p;
    // Can't verify much more than this
    assert(is_valid(to_zaddress(o)), "Not a valid uncolored pointer");
  }

  virtual void do_oop(narrowOop* p) {
    ShouldNotReachHere();
  }
};

// This class encapsulates various marks we need to deal with calling the
// frame iteration code from arbitrary points in the runtime. It is mostly
// due to problems that we might want to eventually clean up inside of the
// frame iteration code, such as creating random handles even though there
// is no safepoint to protect against, and fiddling around with exceptions.
class StackWatermarkProcessingMark {
  ResetNoHandleMark     _rnhm;
  HandleMark            _hm;
  PreserveExceptionMark _pem;
  ResourceMark          _rm;

public:
  StackWatermarkProcessingMark(Thread* thread) :
      _rnhm(),
      _hm(thread),
      _pem(thread),
      _rm(thread) {}
};

void ZVerify::verify_frame_bad(const frame& fr, RegisterMap& register_map) {
  ZVerifyBadOopClosure verify_cl;
  fr.oops_do(&verify_cl, NULL, &register_map, DerivedPointerIterationMode::_ignore);
}

void ZVerify::verify_thread_head_bad(JavaThread* jt) {
  ZVerifyBadOopClosure verify_cl;
  jt->oops_do_no_frames(&verify_cl, NULL);
}

#endif // ASSERT
