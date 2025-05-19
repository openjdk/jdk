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
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceIdEpoch.hpp"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "jfr/support/methodtracer/jfrTraceTagging.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "utilities/growableArray.hpp"

void JfrTraceTagging::tag_dynamic(const InstanceKlass* ik) {
  JfrTraceIdLoadBarrier::load_barrier(ik);
}

void JfrTraceTagging::tag_dynamic(const Method* method) {
  JfrTraceId::load_no_enqueue(method);
}

void JfrTraceTagging::tag_dynamic(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(methods != nullptr, "invariant");

  for (int i = 0; i < methods->length(); ++i) {
    const Method* const method = methods->at(i).method();
    assert(method != nullptr, "invariant");
    if (!method->is_old()) {
      tag_dynamic(method);
      continue;
    }
    // A redefinition / retransformation interleaved.
    // Find and tag the latest version of the method.
    tag_dynamic(ik->method_with_orig_idnum(method->orig_method_idnum()));
  }
}

void JfrTraceTagging::set_dynamic_tag(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");

  tag_dynamic(ik, methods);
  tag_dynamic(ik);
}

void JfrTraceTagging::set_dynamic_tag_for_sticky_bit(const InstanceKlass* ik) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(JfrTraceId::has_sticky_bit(ik), "invariant");

  const int length = ik->methods()->length();
  for (int i = 0; i < length; ++i) {
    const Method* const m = ik->methods()->at(i);
    if (JfrTraceId::has_sticky_bit(m)) {
      tag_dynamic(m);
    }
  }
  tag_dynamic(ik);
}

void JfrTraceTagging::tag_sticky(const InstanceKlass* ik) {
  JfrTraceId::set_sticky_bit(ik);
}

void JfrTraceTagging::tag_sticky(const Method* method) {
  JfrTraceId::set_sticky_bit(method);
}

void JfrTraceTagging::tag_sticky(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(methods != nullptr, "invariant");

  for (int i = 0; i < methods->length(); ++i) {
    const Method* const method = methods->at(i).method();
    assert(method != nullptr, "invariant");
    if (!method->is_old()) {
      tag_sticky(method);
      continue;
    }
    // A redefinition / retransformation interleaved.
    // Find and tag the latest version of the method.
    tag_sticky(ik->method_with_orig_idnum(method->orig_method_idnum()));
  }
}

void JfrTraceTagging::tag_timing(const InstanceKlass* ik) {
  JfrTraceId::set_timing_bit(ik);
}

void JfrTraceTagging::install_sticky_bit_for_retransform_klass(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods, bool timing) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");

  MutexLocker lock(ClassLoaderDataGraph_lock);
  if (JfrTraceId::has_sticky_bit(ik)) {
    clear_sticky_bit(ik);
  }
  tag_sticky(ik, methods);
  tag_sticky(ik);
  if (timing) {
    tag_timing(ik);
  }
}

void JfrTraceTagging::set_sticky_bit(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  tag_sticky(ik, methods);
  tag_sticky(ik);
}

void JfrTraceTagging::clear_sticky_bit(const InstanceKlass* ik, bool dynamic_tag /* true */) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(JfrTraceId::has_sticky_bit(ik), "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  const Array<Method*>* const methods = ik->methods();
  assert(methods != nullptr, "invariant");
  const int length = methods->length();
  for (int i = 0; i < length; ++i) {
    const Method* const m = methods->at(i);
    if (JfrTraceId::has_sticky_bit(m)) {
      if (dynamic_tag) {
        tag_dynamic(m);
      }
      JfrTraceId::clear_sticky_bit(m);
    }
  }
  if (dynamic_tag) {
    tag_dynamic(ik);
  }
  JfrTraceId::clear_sticky_bit(ik);
  if (JfrTraceId::has_timing_bit(ik)) {
    JfrTraceId::clear_timing_bit(ik);
  }
  assert(!JfrTraceId::has_timing_bit(ik), "invariant");
}
