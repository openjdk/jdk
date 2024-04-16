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
#include "gc/x/xAddress.inline.hpp"
#include "gc/x/xHeap.inline.hpp"
#include "gc/x/xNMethod.hpp"
#include "gc/x/xOop.hpp"
#include "gc/x/xPageAllocator.hpp"
#include "gc/x/xResurrection.hpp"
#include "gc/x/xRootsIterator.hpp"
#include "gc/x/xStackWatermark.hpp"
#include "gc/x/xStat.hpp"
#include "gc/x/xVerify.hpp"
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

#define BAD_OOP_ARG(o, p)   "Bad oop " PTR_FORMAT " found at " PTR_FORMAT, p2i(o), p2i(p)

static void z_verify_oop(oop* p) {
  const oop o = RawAccess<>::oop_load(p);
  if (o != nullptr) {
    const uintptr_t addr = XOop::to_address(o);
    guarantee(XAddress::is_good(addr), BAD_OOP_ARG(o, p));
    guarantee(oopDesc::is_oop(XOop::from_address(addr)), BAD_OOP_ARG(o, p));
  }
}

static void z_verify_possibly_weak_oop(oop* p) {
  const oop o = RawAccess<>::oop_load(p);
  if (o != nullptr) {
    const uintptr_t addr = XOop::to_address(o);
    guarantee(XAddress::is_good(addr) || XAddress::is_finalizable_good(addr), BAD_OOP_ARG(o, p));
    guarantee(oopDesc::is_oop(XOop::from_address(XAddress::good(addr))), BAD_OOP_ARG(o, p));
  }
}

class XVerifyRootClosure : public OopClosure {
private:
  const bool _verify_fixed;

public:
  XVerifyRootClosure(bool verify_fixed) :
      _verify_fixed(verify_fixed) {}

  virtual void do_oop(oop* p) {
    if (_verify_fixed) {
      z_verify_oop(p);
    } else {
      // Don't know the state of the oop.
      oop obj = *p;
      obj = NativeAccess<AS_NO_KEEPALIVE>::oop_load(&obj);
      z_verify_oop(&obj);
    }
  }

  virtual void do_oop(narrowOop*) {
    ShouldNotReachHere();
  }

  bool verify_fixed() const {
    return _verify_fixed;
  }
};

class XVerifyStack : public OopClosure {
private:
  XVerifyRootClosure* const _cl;
  JavaThread*         const _jt;
  uint64_t                  _last_good;
  bool                      _verifying_bad_frames;

public:
  XVerifyStack(XVerifyRootClosure* cl, JavaThread* jt) :
      _cl(cl),
      _jt(jt),
      _last_good(0),
      _verifying_bad_frames(false) {
    XStackWatermark* const stack_watermark = StackWatermarkSet::get<XStackWatermark>(jt, StackWatermarkKind::gc);

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
      const oop obj = *p;
      guarantee(!XAddress::is_good(XOop::to_address(obj)), BAD_OOP_ARG(obj, p));
    }
    _cl->do_oop(p);
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
    NMethodToOopClosure nm_cl(_cl, false /* fix_relocations */);
    for (StackFrameStream frames(_jt, true /* update */, false /* process_frames */);
         !frames.is_done();
         frames.next()) {
      frame& frame = *frames.current();
      frame.oops_do(this, &nm_cl, frames.register_map(), DerivedPointerIterationMode::_ignore);
      prepare_next_frame(frame);
    }
  }
};

class XVerifyOopClosure : public ClaimMetadataVisitingOopIterateClosure {
private:
  const bool _verify_weaks;

public:
  XVerifyOopClosure(bool verify_weaks) :
      ClaimMetadataVisitingOopIterateClosure(ClassLoaderData::_claim_other),
      _verify_weaks(verify_weaks) {}

  virtual void do_oop(oop* p) {
    if (_verify_weaks) {
      z_verify_possibly_weak_oop(p);
    } else {
      // We should never encounter finalizable oops through strong
      // paths. This assumes we have only visited strong roots.
      z_verify_oop(p);
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

typedef ClaimingCLDToOopClosure<ClassLoaderData::_claim_none> XVerifyCLDClosure;

class XVerifyThreadClosure : public ThreadClosure {
private:
  XVerifyRootClosure* const _cl;

public:
  XVerifyThreadClosure(XVerifyRootClosure* cl) :
      _cl(cl) {}

  virtual void do_thread(Thread* thread) {
    thread->oops_do_no_frames(_cl, nullptr);

    JavaThread* const jt = JavaThread::cast(thread);
    if (!jt->has_last_Java_frame()) {
      return;
    }

    XVerifyStack verify_stack(_cl, jt);
    verify_stack.verify_frames();
  }
};

class XVerifyNMethodClosure : public NMethodClosure {
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
  XVerifyNMethodClosure(OopClosure* cl, bool verify_fixed) :
      _cl(cl),
      _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()),
      _verify_fixed(verify_fixed) {}

  virtual void do_nmethod(nmethod* nm) {
    assert(!trust_nmethod_state() || !_bs_nm->is_armed(nm), "Should not encounter any armed nmethods");

    XNMethod::nmethod_oops_do(nm, _cl);
  }
};

void XVerify::roots_strong(bool verify_fixed) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!XResurrection::is_blocked(), "Invalid phase");

  XVerifyRootClosure cl(verify_fixed);
  XVerifyCLDClosure cld_cl(&cl);
  XVerifyThreadClosure thread_cl(&cl);
  XVerifyNMethodClosure nm_cl(&cl, verify_fixed);

  XRootsIterator iter(ClassLoaderData::_claim_none);
  iter.apply(&cl,
             &cld_cl,
             &thread_cl,
             &nm_cl);
}

void XVerify::roots_weak() {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(!XResurrection::is_blocked(), "Invalid phase");

  XVerifyRootClosure cl(true /* verify_fixed */);
  XWeakRootsIterator iter;
  iter.apply(&cl);
}

void XVerify::objects(bool verify_weaks) {
  assert(SafepointSynchronize::is_at_safepoint(), "Must be at a safepoint");
  assert(XGlobalPhase == XPhaseMarkCompleted, "Invalid phase");
  assert(!XResurrection::is_blocked(), "Invalid phase");

  XVerifyOopClosure cl(verify_weaks);
  ObjectToOopClosure object_cl(&cl);
  XHeap::heap()->object_iterate(&object_cl, verify_weaks);
}

void XVerify::before_zoperation() {
  // Verify strong roots
  XStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(false /* verify_fixed */);
  }
}

void XVerify::after_mark() {
  // Verify all strong roots and strong references
  XStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(true /* verify_fixed */);
  }
  if (ZVerifyObjects) {
    objects(false /* verify_weaks */);
  }
}

void XVerify::after_weak_processing() {
  // Verify all roots and all references
  XStatTimerDisable disable;
  if (ZVerifyRoots) {
    roots_strong(true /* verify_fixed */);
    roots_weak();
  }
  if (ZVerifyObjects) {
    objects(true /* verify_weaks */);
  }
}

template <bool Map>
class XPageDebugMapOrUnmapClosure : public XPageClosure {
private:
  const XPageAllocator* const _allocator;

public:
  XPageDebugMapOrUnmapClosure(const XPageAllocator* allocator) :
      _allocator(allocator) {}

  void do_page(const XPage* page) {
    if (Map) {
      _allocator->debug_map_page(page);
    } else {
      _allocator->debug_unmap_page(page);
    }
  }
};

XVerifyViewsFlip::XVerifyViewsFlip(const XPageAllocator* allocator) :
    _allocator(allocator) {
  if (ZVerifyViews) {
    // Unmap all pages
    XPageDebugMapOrUnmapClosure<false /* Map */> cl(_allocator);
    XHeap::heap()->pages_do(&cl);
  }
}

XVerifyViewsFlip::~XVerifyViewsFlip() {
  if (ZVerifyViews) {
    // Map all pages
    XPageDebugMapOrUnmapClosure<true /* Map */> cl(_allocator);
    XHeap::heap()->pages_do(&cl);
  }
}

#ifdef ASSERT

class XVerifyBadOopClosure : public OopClosure {
public:
  virtual void do_oop(oop* p) {
    const oop o = *p;
    assert(!XAddress::is_good(XOop::to_address(o)), "Should not be good: " PTR_FORMAT, p2i(o));
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

void XVerify::verify_frame_bad(const frame& fr, RegisterMap& register_map) {
  XVerifyBadOopClosure verify_cl;
  fr.oops_do(&verify_cl, nullptr, &register_map, DerivedPointerIterationMode::_ignore);
}

void XVerify::verify_thread_head_bad(JavaThread* jt) {
  XVerifyBadOopClosure verify_cl;
  jt->oops_do_no_frames(&verify_cl, nullptr);
}

void XVerify::verify_thread_frames_bad(JavaThread* jt) {
  if (jt->has_last_Java_frame()) {
    XVerifyBadOopClosure verify_cl;
    StackWatermarkProcessingMark swpm(Thread::current());
    // Traverse the execution stack
    for (StackFrameStream fst(jt, true /* update */, false /* process_frames */); !fst.is_done(); fst.next()) {
      fst.current()->oops_do(&verify_cl, nullptr /* code_cl */, fst.register_map(), DerivedPointerIterationMode::_ignore);
    }
  }
}

#endif // ASSERT
