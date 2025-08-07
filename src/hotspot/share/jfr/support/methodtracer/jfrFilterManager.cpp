/*
* Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/service/jfrOptionSet.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "jfr/support/methodtracer/jfrFilterManager.hpp"
#include "logging/log.hpp"
#include "oops/symbol.hpp"
#include "oops/typeArrayOop.inline.hpp"
#include "runtime/handles.inline.hpp"
#include "utilities/growableArray.hpp"

const JfrFilter* JfrFilterManager::_current = nullptr;

// Track the set of previous filters during a chunk / epoch.
static constexpr const int initial_array_size = 4;
static GrowableArray<const JfrFilter*>* _previous_filters_epoch_0 = nullptr;
static GrowableArray<const JfrFilter*>* _previous_filters_epoch_1 = nullptr;

static GrowableArray<const JfrFilter*>* c_heap_allocate_array(int size = initial_array_size) {
  return new (mtTracing) GrowableArray<const JfrFilter*>(size, mtTracing);
}

static GrowableArray<const JfrFilter*>* previous_filters_epoch_0() {
  if (_previous_filters_epoch_0 == nullptr) {
    _previous_filters_epoch_0 = c_heap_allocate_array(initial_array_size);
  }
  return _previous_filters_epoch_0;
}

static GrowableArray<const JfrFilter*>* previous_filters_epoch_1() {
  if (_previous_filters_epoch_1 == nullptr) {
    _previous_filters_epoch_1 = c_heap_allocate_array(initial_array_size);
  }
  return _previous_filters_epoch_1;
}

static GrowableArray<const JfrFilter*>* get_previous_filters(u1 epoch) {
  return epoch == 0 ? previous_filters_epoch_0() : previous_filters_epoch_1();
}

static GrowableArray<const JfrFilter*>* get_previous_filters() {
  return get_previous_filters(JfrTraceIdEpoch::current());
}

static GrowableArray<const JfrFilter*>* get_previous_filters_previous_epoch() {
  return get_previous_filters(JfrTraceIdEpoch::previous());
}

static void add_previous_filter(const JfrFilter* previous_filter) {
  if (previous_filter != nullptr) {
    get_previous_filters()->append(previous_filter);
  }
}

const JfrFilter* JfrFilterManager::current() {
  return Atomic::load_acquire(&_current);
}

void JfrFilterManager::install(const JfrFilter* new_filter) {
  assert(new_filter != nullptr, "invariant");
  add_previous_filter(Atomic::xchg(&_current, new_filter));
  new_filter->log("New filter installed");
}

static void delete_filters(GrowableArray<const JfrFilter*>* filters) {
  assert(filters != nullptr, "invariant");
  for (int i = 0; i < filters->length(); ++i) {
    delete filters->at(i);
  }
  filters->clear();
}

void JfrFilterManager::clear_previous_filters() {
  delete_filters(get_previous_filters_previous_epoch());
}

bool JfrFilterManager::install(jobjectArray classes, jobjectArray methods, jobjectArray annotations, jintArray modification_array, JavaThread* jt) {
  assert(classes != nullptr, "invariant");
  assert(methods != nullptr, "invariant");
  assert(annotations != nullptr, "invariant");
  assert(modification_array != nullptr, "invariant");

  if (!JfrOptionSet::can_retransform()) {
    log_info(jfr, methodtrace)("Flight Recorder retransform has been set to false. New method filter is ignored.");
    return false;
  }

  intptr_t class_size = 0;
  Symbol** class_names = JfrJavaSupport::symbol_array(classes, jt, &class_size, true);
  assert(class_names != nullptr, "invariant");

  intptr_t method_size = 0;
  Symbol** method_names = JfrJavaSupport::symbol_array(methods, jt, &method_size, true);
  assert(method_names != nullptr, "invariant");

  intptr_t annotation_size = 0;
  Symbol** annotation_names = JfrJavaSupport::symbol_array(annotations, jt, &annotation_size, true);
  assert(annotation_names != nullptr, "invariant");

  typeArrayOop ta = typeArrayOop(JfrJavaSupport::resolve_non_null(modification_array));
  const typeArrayHandle modification_tah(jt, ta);
  const int modification_size = modification_tah->length();
  int* const modifications = NEW_C_HEAP_ARRAY(int, modification_size, mtTracing);
  for (int i = 0; i < modification_size; i++) {
    modifications[i] = modification_tah->int_at(i);
  }
  if (class_size != method_size || class_size != annotation_size || class_size != modification_size) {
    FREE_C_HEAP_ARRAY(Symbol*, class_names);
    FREE_C_HEAP_ARRAY(Symbol*, method_names);
    FREE_C_HEAP_ARRAY(Symbol*, annotation_names);
    FREE_C_HEAP_ARRAY(int, modifications);
    JfrJavaSupport::throw_internal_error("Method array sizes don't match", jt);
    return false;
  }
  const JfrFilter* const new_filter = new JfrFilter(class_names, method_names, annotation_names, modifications, modification_size);
  assert(new_filter != nullptr, "invariant");
  install(new_filter);
  return true;
}
