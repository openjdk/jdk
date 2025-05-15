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

#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdBits.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdMacros.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/support/methodtracer/jfrTracedMethod.hpp"
#include "jfr/support/methodtracer/jfrTraceTagging.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"

void JfrTraceTagging::set_dynamic_tag(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");

  tag_dynamic(methods);
  tag_dynamic(ik);
}

void JfrTraceTagging::set_dynamic_tag_for_sticky_bit(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(HAS_STICKY_BIT(ik), "invariant");

  const int length = ik->methods()->length();
  for (int i = 0; i < length; ++i) {
    const Method* const m = ik->methods()->at(i);
    if (METHOD_HAS_STICKY_BIT(m)) {
      tag_dynamic(m);
    }
  }
  tag_dynamic(ik);
}

void JfrTraceTagging::install_sticky_bit_for_retransform_klass(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods, bool timing) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  MutexLocker lock(ClassLoaderDataGraph_lock);

  tag_sticky(methods);
  tag_sticky(ik);
  if (timing) {
    tag_timing(ik);
  }
}

void JfrTraceTagging::set_sticky_bit(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  tag_sticky(methods);
  tag_sticky(ik);
}

void JfrTraceTagging::clear_sticky_bit(const InstanceKlass* ik, bool dynamic_tag) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(HAS_STICKY_BIT(ik), "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  const Array<Method*>* const methods = ik->methods();
  assert(methods != nullptr, "invariant");
  const int length = methods->length();
  for (int i = 0; i < length; ++i) {
    const Method* const m = methods->at(i);
    if (METHOD_HAS_STICKY_BIT(m)) {
      if (dynamic_tag) {
        tag_dynamic(m);
      }
      CLEAR_STICKY_BIT_METHOD(m);
      assert(METHOD_HAS_NOT_STICKY_BIT(m), "invariant");
    }
  }
  if (dynamic_tag) {
    tag_dynamic(ik);
  }
  CLEAR_STICKY_BIT(ik);
  assert(HAS_NOT_STICKY_BIT(ik), "invariant");
  if (HAS_TIMING_BIT(ik)) {
    CLEAR_TIMING_BIT(ik);
  }
  assert(HAS_NOT_TIMING_BIT(ik), "invariant");
}

void JfrTraceTagging::clear(GrowableArray<JfrInstrumentedClass>* instrumented, bool dynamic_tag) {
  assert(instrumented != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  if (instrumented->is_empty()) {
    assert(!JfrTraceIdEpoch::has_method_tracer_changed_tag_state(), "invariant");
    return;
  }
  JfrTraceIdEpoch::reset_method_tracer_tag_state();
  JfrKlassUnloading::sort(true);
  const int length = instrumented->length();
  assert(length > 0, "invariant");
  for (int i = 0; i < length; ++i) {
    if (!JfrKlassUnloading::is_unloaded(instrumented->at(i).trace_id(), true)) {
      const InstanceKlass* ik = instrumented->at(i).instance_klass();
      assert(instrumented->at(i).trace_id() == JfrTraceId::load_raw(ik), "invariant");
      clear_sticky_bit(ik, dynamic_tag);
      if (log_is_enabled(Debug, jfr, methodtrace)) {
        ResourceMark rm;
        log_debug(jfr, methodtrace)("Removing class %s from instrumented list", ik->external_name());
      }
    }
  }
  instrumented->clear();
}

// PRIVATE METHODS

void JfrTraceTagging::tag_dynamic(const InstanceKlass* ik) {
  JfrTraceIdLoadBarrier::load_barrier(ik);
}

void JfrTraceTagging::tag_dynamic(const GrowableArray<JfrTracedMethod>* methods) {
  for (int i = 0; i < methods->length(); ++i) {
    tag_dynamic(methods->at(i).method());
  }
}

void JfrTraceTagging::tag_dynamic(const Method* method) {
  JfrTraceId::load_no_enqueue(method);
}

void JfrTraceTagging::tag_sticky(const InstanceKlass* ik) {
  assert(HAS_NOT_STICKY_BIT(ik), "invariant");
  SET_STICKY_BIT(ik);
  assert(HAS_STICKY_BIT(ik), "invariant");
}

void JfrTraceTagging::tag_sticky(const GrowableArray<JfrTracedMethod>* methods) {
  for (int i = 0; i < methods->length(); ++i) {
    tag_sticky(methods->at(i).method());
  }
}

void JfrTraceTagging::tag_sticky(const Method* method) {
  assert(METHOD_HAS_NOT_STICKY_BIT(method), "invariant");
  SET_METHOD_STICKY_BIT(method);
  assert(METHOD_HAS_STICKY_BIT(method), "invariant");
}

void JfrTraceTagging::tag_timing(const InstanceKlass* ik) {
  assert(HAS_NOT_TIMING_BIT(ik), "invariant");
  SET_TIMING_BIT(ik);
  assert(HAS_TIMING_BIT(ik), "invariant");
}
