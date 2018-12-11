/*
 * Copyright (c) 2018, Red Hat, Inc. All rights reserved.
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

#ifndef SHARE_VM_GC_SHENANDOAH_SHENANDOAHPACER_INLINE_HPP
#define SHARE_VM_GC_SHENANDOAH_SHENANDOAHPACER_INLINE_HPP

#include "gc/shenandoah/shenandoahPacer.hpp"
#include "runtime/atomic.hpp"

inline void ShenandoahPacer::report_mark(size_t words) {
  report_internal(words);
  report_progress_internal(words);
}

inline void ShenandoahPacer::report_evac(size_t words) {
  report_internal(words);
}

inline void ShenandoahPacer::report_updaterefs(size_t words) {
  report_internal(words);
}

inline void ShenandoahPacer::report_alloc(size_t words) {
  report_internal(words);
}

inline void ShenandoahPacer::report_internal(size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");
  STATIC_ASSERT(sizeof(size_t) <= sizeof(intptr_t));
  Atomic::add((intptr_t)words, &_budget);
}

inline void ShenandoahPacer::report_progress_internal(size_t words) {
  assert(ShenandoahPacing, "Only be here when pacing is enabled");
  STATIC_ASSERT(sizeof(size_t) <= sizeof(intptr_t));
  Atomic::add((intptr_t)words, &_progress);
}

#endif //SHARE_VM_GC_SHENANDOAH_SHENANDOAHPACER_INLINE_HPP
