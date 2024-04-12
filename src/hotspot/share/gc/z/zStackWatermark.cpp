/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/z/zAddress.hpp"
#include "gc/z/zBarrier.inline.hpp"
#include "gc/z/zGeneration.inline.hpp"
#include "gc/z/zStackWatermark.hpp"
#include "gc/z/zStoreBarrierBuffer.hpp"
#include "gc/z/zThreadLocalAllocBuffer.hpp"
#include "gc/z/zThreadLocalData.hpp"
#include "gc/z/zUncoloredRoot.inline.hpp"
#include "gc/z/zVerify.hpp"
#include "memory/resourceArea.inline.hpp"
#include "runtime/frame.inline.hpp"
#include "runtime/stackWatermark.hpp"
#include "runtime/thread.hpp"
#include "utilities/preserveException.hpp"

ZOnStackNMethodClosure::ZOnStackNMethodClosure()
  : _bs_nm(BarrierSet::barrier_set()->barrier_set_nmethod()) {}

void ZOnStackNMethodClosure::do_nmethod(nmethod* nm) {
  assert(nm != nullptr, "Sanity");
  const bool result = _bs_nm->nmethod_entry_barrier(nm);
  assert(result, "NMethod on-stack must be alive");
}

ThreadLocalAllocStats& ZStackWatermark::stats() {
  return _stats;
}

uint32_t ZStackWatermark::epoch_id() const {
  return *ZPointerStoreGoodMaskLowOrderBitsAddr;
}

ZStackWatermark::ZStackWatermark(JavaThread* jt)
  : StackWatermark(jt, StackWatermarkKind::gc, *ZPointerStoreGoodMaskLowOrderBitsAddr),
    // First watermark is fake and setup to be replaced at next phase shift
    _old_watermarks{{ZPointerStoreBadMask, 1}, {}, {}},
    _old_watermarks_newest(0),
    _stats() {}

bool ZColorWatermark::covers(const ZColorWatermark& other) const {
  if (_watermark == 0) {
    // This watermark was completed
    return true;
  }

  if (other._watermark == 0) {
    // The other watermark was completed
    return false;
  }

  // Compare the two
  return _watermark >= other._watermark;
}

uintptr_t ZStackWatermark::prev_head_color() const {
  return _old_watermarks[_old_watermarks_newest]._color;
}

uintptr_t ZStackWatermark::prev_frame_color(const frame& fr) const {
  for (int i = _old_watermarks_newest; i >= 0; i--) {
    const ZColorWatermark ow = _old_watermarks[i];
    if (ow._watermark == 0 || uintptr_t(fr.sp()) <= ow._watermark) {
      return ow._color;
    }
  }

  fatal("Found no matching previous color for the frame");
  return 0;
}

void ZStackWatermark::save_old_watermark() {
  assert(StackWatermarkState::epoch(_state) != ZStackWatermark::epoch_id(), "Shouldn't be here otherwise");

  // Previous color
  const uintptr_t prev_color = StackWatermarkState::epoch(_state);

  // If the prev_color is still the last saved color watermark, then processing has not started.
  const bool prev_processing_started = prev_color != prev_head_color();

  if (!prev_processing_started) {
    // Nothing was processed in the previous phase, so there's no need to save a watermark for it.
    // Must have been a remapped phase, the other phases are explicitly completed by the GC.
    assert((prev_color & ZPointerRemapped) != 0, "Unexpected color: " PTR_FORMAT, prev_color);
    return;
  }

  // Previous watermark
  const uintptr_t prev_watermark = StackWatermarkState::is_done(_state) ? 0 : last_processed_raw();

  // Create a new color watermark to describe the old watermark
  const ZColorWatermark cw = { prev_color, prev_watermark };

  // Find the location of the oldest watermark that it covers, and thus can replace
  int replace = -1;
  for (int i = 0; i <= _old_watermarks_newest; i++) {
    if (cw.covers(_old_watermarks[i])) {
      replace = i;
      break;
    }
  }

  // Update top
  if (replace != -1) {
    // Found one to replace
    _old_watermarks_newest = replace;
  } else {
    // Found none too replace - push it to the top
    _old_watermarks_newest++;
    assert(_old_watermarks_newest < _old_watermarks_max, "Unexpected amount of old watermarks");
  }

  // Install old watermark
  _old_watermarks[_old_watermarks_newest] = cw;
}

class ZStackWatermarkProcessOopClosure : public ZUncoloredRootClosure {
private:
  const ZUncoloredRoot::RootFunction _function;
  const uintptr_t                    _color;

  static ZUncoloredRoot::RootFunction select_function(void* context) {
    if (context == nullptr) {
      return ZUncoloredRoot::process;
    }

    assert(Thread::current()->is_Worker_thread(), "Unexpected thread passing in context: " PTR_FORMAT, p2i(context));
    return reinterpret_cast<ZUncoloredRoot::RootFunction>(context);
  }

public:
  ZStackWatermarkProcessOopClosure(void* context, uintptr_t color)
    : _function(select_function(context)), _color(color) {}

  virtual void do_root(zaddress_unsafe* p) {
    _function(p, _color);
  }
};

void ZStackWatermark::process_head(void* context) {
  const uintptr_t color = prev_head_color();

  ZStackWatermarkProcessOopClosure cl(context, color);
  ZOnStackNMethodClosure nm_cl;

  _jt->oops_do_no_frames(&cl, &nm_cl);

  zaddress_unsafe* const invisible_root = ZThreadLocalData::invisible_root(_jt);
  if (invisible_root != nullptr) {
    ZUncoloredRoot::process_invisible(invisible_root, color);
  }
}

void ZStackWatermark::start_processing_impl(void* context) {
  save_old_watermark();

  // Process the non-frame part of the thread
  process_head(context);

  // Verification of frames is done after processing of the "head" (no_frames).
  // The reason is that the exception oop is fiddled with during frame processing.
  // ZVerify::verify_thread_frames_bad(_jt);

  // Update thread-local masks
  ZThreadLocalData::set_load_bad_mask(_jt, ZPointerLoadBadMask);
  ZThreadLocalData::set_load_good_mask(_jt, ZPointerLoadGoodMask);
  ZThreadLocalData::set_mark_bad_mask(_jt, ZPointerMarkBadMask);
  ZThreadLocalData::set_store_bad_mask(_jt, ZPointerStoreBadMask);
  ZThreadLocalData::set_store_good_mask(_jt, ZPointerStoreGoodMask);
  ZThreadLocalData::set_nmethod_disarmed(_jt, ZPointerStoreGoodMask);

  // Retire TLAB
  if (ZGeneration::young()->is_phase_mark() || ZGeneration::old()->is_phase_mark()) {
    ZThreadLocalAllocBuffer::retire(_jt, &_stats);
  }

  // Prepare store barrier buffer for new GC phase
  ZThreadLocalData::store_barrier_buffer(_jt)->on_new_phase();

  // Publishes the processing start to concurrent threads
  StackWatermark::start_processing_impl(context);
}

void ZStackWatermark::process(const frame& fr, RegisterMap& register_map, void* context) {
  const uintptr_t color = prev_frame_color(fr);
  ZStackWatermarkProcessOopClosure cl(context, color);
  ZOnStackNMethodClosure nm_cl;

  fr.oops_do(&cl, &nm_cl, &register_map, DerivedPointerIterationMode::_directly);
}
