/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_VM_GC_G1_G1COLLECTORSTATE_HPP
#define SHARE_VM_GC_G1_G1COLLECTORSTATE_HPP

#include "gc/g1/g1YCTypes.hpp"
#include "memory/allocation.hpp"
#include "utilities/globalDefinitions.hpp"

// Various state variables that indicate
// the phase of the G1 collection.
class G1CollectorState VALUE_OBJ_CLASS_SPEC {
  // Indicates whether we are in "full young" or "mixed" GC mode.
  bool _gcs_are_young;
  // Was the last GC "young"?
  bool _last_gc_was_young;
  // Is this the "last young GC" before we start doing mixed GCs?
  // Set after a concurrent mark has completed.
  bool _last_young_gc;

  // If initiate_conc_mark_if_possible() is set at the beginning of a
  // pause, it is a suggestion that the pause should start a marking
  // cycle by doing the initial-mark work. However, it is possible
  // that the concurrent marking thread is still finishing up the
  // previous marking cycle (e.g., clearing the next marking
  // bitmap). If that is the case we cannot start a new cycle and
  // we'll have to wait for the concurrent marking thread to finish
  // what it is doing. In this case we will postpone the marking cycle
  // initiation decision for the next pause. When we eventually decide
  // to start a cycle, we will set _during_initial_mark_pause which
  // will stay true until the end of the initial-mark pause and it's
  // the condition that indicates that a pause is doing the
  // initial-mark work.
  volatile bool _during_initial_mark_pause;

  // At the end of a pause we check the heap occupancy and we decide
  // whether we will start a marking cycle during the next pause. If
  // we decide that we want to do that, we will set this parameter to
  // true. So, this parameter will stay true between the end of a
  // pause and the beginning of a subsequent pause (not necessarily
  // the next one, see the comments on the next field) when we decide
  // that we will indeed start a marking cycle and do the initial-mark
  // work.
  volatile bool _initiate_conc_mark_if_possible;

  // NOTE: if some of these are synonyms for others,
  // the redundant fields should be eliminated. XXX
  bool _during_marking;
  bool _mark_in_progress;
  bool _in_marking_window;
  bool _in_marking_window_im;

  bool _full_collection;

  public:
    G1CollectorState() :
      _gcs_are_young(true),
      _last_gc_was_young(false),
      _last_young_gc(false),

      _during_initial_mark_pause(false),
      _initiate_conc_mark_if_possible(false),

      _during_marking(false),
      _mark_in_progress(false),
      _in_marking_window(false),
      _in_marking_window_im(false),
      _full_collection(false) {}

  // Setters
  void set_gcs_are_young(bool v) { _gcs_are_young = v; }
  void set_last_gc_was_young(bool v) { _last_gc_was_young = v; }
  void set_last_young_gc(bool v) { _last_young_gc = v; }
  void set_during_initial_mark_pause(bool v) { _during_initial_mark_pause = v; }
  void set_initiate_conc_mark_if_possible(bool v) { _initiate_conc_mark_if_possible = v; }
  void set_during_marking(bool v) { _during_marking = v; }
  void set_mark_in_progress(bool v) { _mark_in_progress = v; }
  void set_in_marking_window(bool v) { _in_marking_window = v; }
  void set_in_marking_window_im(bool v) { _in_marking_window_im = v; }
  void set_full_collection(bool v) { _full_collection = v; }

  // Getters
  bool gcs_are_young() const { return _gcs_are_young; }
  bool last_gc_was_young() const { return _last_gc_was_young; }
  bool last_young_gc() const { return _last_young_gc; }
  bool during_initial_mark_pause() const { return _during_initial_mark_pause; }
  bool initiate_conc_mark_if_possible() const { return _initiate_conc_mark_if_possible; }
  bool during_marking() const { return _during_marking; }
  bool mark_in_progress() const { return _mark_in_progress; }
  bool in_marking_window() const { return _in_marking_window; }
  bool in_marking_window_im() const { return _in_marking_window_im; }
  bool full_collection() const { return _full_collection; }

  // Composite booleans (clients worry about flickering)
  bool during_concurrent_mark() const {
    return (_in_marking_window && !_in_marking_window_im);
  }

  G1YCType yc_type() const {
    if (during_initial_mark_pause()) {
      return InitialMark;
    } else if (mark_in_progress()) {
      return DuringMark;
    } else if (gcs_are_young()) {
      return Normal;
    } else {
      return Mixed;
    }
  }
};

#endif // SHARE_VM_GC_G1_G1COLLECTORSTATE_HPP
