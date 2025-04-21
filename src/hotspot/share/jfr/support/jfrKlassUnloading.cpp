/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrFinalizerStatisticsEvent.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/utilities/jfrSet.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/macros.hpp"

static const int initial_size = 1009;

static JfrCHeapTraceIdSet* c_heap_allocate_set(int size = initial_size) {
  return new JfrCHeapTraceIdSet(size);
}

// Track the set of unloaded klasses during a chunk / epoch.
static JfrCHeapTraceIdSet* _unload_set_epoch_0 = nullptr;
static JfrCHeapTraceIdSet* _unload_set_epoch_1 = nullptr;

static s8 event_klass_unloaded_count = 0;

static JfrCHeapTraceIdSet* unload_set_epoch_0() {
  if (_unload_set_epoch_0 == nullptr) {
    _unload_set_epoch_0 = c_heap_allocate_set();
  }
  return _unload_set_epoch_0;
}

static JfrCHeapTraceIdSet* unload_set_epoch_1() {
  if (_unload_set_epoch_1 == nullptr) {
    _unload_set_epoch_1 = c_heap_allocate_set();
  }
  return _unload_set_epoch_1;
}

static JfrCHeapTraceIdSet* get_unload_set(u1 epoch) {
  return epoch == 0 ? unload_set_epoch_0() : unload_set_epoch_1();
}

static JfrCHeapTraceIdSet* get_unload_set() {
  return get_unload_set(JfrTraceIdEpoch::current());
}

static JfrCHeapTraceIdSet* get_unload_set_previous_epoch() {
  return get_unload_set(JfrTraceIdEpoch::previous());
}

static bool is_nonempty_set(u1 epoch) {
  if (epoch == 0) {
    return _unload_set_epoch_0 != nullptr && _unload_set_epoch_0->is_nonempty();
  }
  return _unload_set_epoch_1 != nullptr && _unload_set_epoch_1->is_nonempty();
}

void JfrKlassUnloading::clear() {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (is_nonempty_set(JfrTraceIdEpoch::previous())) {
    get_unload_set_previous_epoch()->clear();
  }
}

static void add_to_unloaded_klass_set(traceid klass_id) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  JfrCHeapTraceIdSet* const unload_set = get_unload_set();
  assert(unload_set != nullptr, "invariant");
  unload_set->add(klass_id);
}

#if INCLUDE_MANAGEMENT
static void send_finalizer_event(const Klass* k) {
  if (!k->is_instance_klass()) {
    return;
  }
  const InstanceKlass* const ik = InstanceKlass::cast(k);
  if (ik->has_finalizer()) {
    JfrFinalizerStatisticsEvent::send_unload_event(ik);
  }
}
#endif

bool JfrKlassUnloading::on_unload(const Klass* k) {
  assert(k != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  MANAGEMENT_ONLY(send_finalizer_event(k);)
  if (IS_JDK_JFR_EVENT_SUBKLASS(k)) {
    ++event_klass_unloaded_count;
  }
  add_to_unloaded_klass_set(JfrTraceId::load_raw(k));
  return USED_THIS_EPOCH(k);
}

static inline bool is_unloaded(const JfrCHeapTraceIdSet* set, const traceid& id) {
  assert(set != nullptr, "invariant");
  return set->contains(id);
}

bool JfrKlassUnloading::is_unloaded(traceid klass_id, bool previous_epoch /* false */) {
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  if (previous_epoch) {
    if (::is_unloaded(get_unload_set_previous_epoch(), klass_id)) {
      return true;
    }
  }
  return ::is_unloaded(get_unload_set(), klass_id);
}

int64_t JfrKlassUnloading::event_class_count() {
  return event_klass_unloaded_count;
}
