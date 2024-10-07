/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/x/xAddress.hpp"
#include "gc/x/xBarrier.inline.hpp"
#include "gc/x/xStackWatermark.hpp"
#include "gc/x/xThread.inline.hpp"
#include "gc/x/xThreadLocalAllocBuffer.hpp"
#include "gc/x/xThreadLocalData.hpp"
#include "gc/x/xVerify.hpp"
#include "memory/resourceArea.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "utilities/preserveException.hpp"

XOnStackNMethodClosure::XOnStackNMethodClosure() :
    _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

void XOnStackNMethodClosure::do_nmethod(nmethod* nm) {
  const bool result = _bs_nm->nmethod_entry_barrier(nm);
  assert(result, "NMethod on-stack must be alive");
}

ThreadLocalAllocStats& XStackWatermark::stats() {
  return _stats;
}

uint32_t XStackWatermark::epoch_id() const {
  return *XAddressBadMaskHighOrderBitsAddr;
}

XStackWatermark::XStackWatermark(JavaThread* jt) :
    StackWatermark(jt, StackWatermarkKind::gc, *XAddressBadMaskHighOrderBitsAddr),
    _jt_cl(),
    _nm_cl(),
    _stats() {}

OopClosure* XStackWatermark::closure_from_context(void* context) {
  if (context != nullptr) {
    assert(XThread::is_worker(), "Unexpected thread passing in context: " PTR_FORMAT, p2i(context));
    return reinterpret_cast<OopClosure*>(context);
  } else {
    return &_jt_cl;
  }
}

void XStackWatermark::start_processing_impl(void* context) {
  // Verify the head (no_frames) of the thread is bad before fixing it.
  XVerify::verify_thread_head_bad(_jt);

  // Process the non-frame part of the thread
  _jt->oops_do_no_frames(closure_from_context(context), &_nm_cl);
  XThreadLocalData::do_invisible_root(_jt, XBarrier::load_barrier_on_invisible_root_oop_field);

  // Verification of frames is done after processing of the "head" (no_frames).
  // The reason is that the exception oop is fiddled with during frame processing.
  XVerify::verify_thread_frames_bad(_jt);

  // Update thread local address bad mask
  XThreadLocalData::set_address_bad_mask(_jt, XAddressBadMask);

  // Retire TLAB
  if (XGlobalPhase == XPhaseMark) {
    XThreadLocalAllocBuffer::retire(_jt, &_stats);
  } else {
    XThreadLocalAllocBuffer::remap(_jt);
  }

  // Publishes the processing start to concurrent threads
  StackWatermark::start_processing_impl(context);
}

void XStackWatermark::process(const frame& fr, RegisterMap& register_map, void* context) {
  XVerify::verify_frame_bad(fr, register_map);
  fr.oops_do(closure_from_context(context), &_nm_cl, &register_map, DerivedPointerIterationMode::_directly);
}
