/*
 * Copyright (c) 2018, 2023, Red Hat, Inc. All rights reserved.
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

#include "gc/shenandoah/mode/shenandoahMode.hpp"
#include "gc/shenandoah/shenandoahEvacTracker.hpp"
#include "gc/shenandoah/shenandoahGenerationalHeap.hpp"
#include "gc/shenandoah/shenandoahHeap.inline.hpp"
#include "gc/shenandoah/shenandoahThreadLocalData.hpp"

ShenandoahThreadLocalData::ShenandoahThreadLocalData() :
  _gc_state(0),
  _oom_scope_nesting_level(0),
  _oom_during_evac(false),
  _satb_mark_queue(&ShenandoahBarrierSet::satb_mark_queue_set()),
  _card_table(nullptr),
  _gclab(nullptr),
  _gclab_size(0),
  _paced_time(0),
  _plab(nullptr),
  _plab_desired_size(0),
  _plab_actual_size(0),
  _plab_promoted(0),
  _plab_allows_promotion(true),
  _plab_retries_enabled(true),
  _evacuation_stats(new ShenandoahEvacuationStats()) {
}

ShenandoahThreadLocalData::~ShenandoahThreadLocalData() {
  if (_gclab != nullptr) {
    delete _gclab;
  }
  if (_plab != nullptr) {
    ShenandoahGenerationalHeap::heap()->retire_plab(_plab);
    delete _plab;
  }

  delete _evacuation_stats;
}
