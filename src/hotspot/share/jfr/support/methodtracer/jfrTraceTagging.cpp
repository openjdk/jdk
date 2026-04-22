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
#include "jfr/support/methodtracer/jfrMethodTracer.hpp"
#include "jfr/support/methodtracer/jfrTraceTagging.hpp"
#include "oops/instanceKlass.hpp"
#include "oops/method.hpp"
#include "runtime/safepoint.hpp"
#include "utilities/growableArray.hpp"

void JfrTraceTagging::tag_dynamic(const InstanceKlass* ik) {
  JfrTraceIdLoadBarrier::load_barrier(ik);
}

void JfrTraceTagging::tag_dynamic(const Method* method) {
  JfrTraceId::load_no_enqueue(method);
}

void JfrTraceTagging::tag_sticky(const InstanceKlass* ik) {
  JfrTraceId::set_sticky_bit(ik);
}

void JfrTraceTagging::tag_sticky_enqueue(const InstanceKlass* ik) {
  tag_sticky(ik);
  JfrTraceIdLoadBarrier::enqueue(ik);
}

void JfrTraceTagging::tag_sticky(const Method* method) {
  JfrTraceId::set_sticky_bit(method);
}

void JfrTraceTagging::tag_sticky(const GrowableArray<JfrTracedMethod>* methods) {
  assert(methods != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  for (int i = 0; i < methods->length(); ++i) {
    const Method* const method = methods->at(i).method();
    assert(method != nullptr, "invariant");
    tag_sticky(method);
  }
}

void JfrTraceTagging::tag_sticky(const InstanceKlass* ik, const GrowableArray<JfrTracedMethod>* methods) {
  assert(ik != nullptr, "invariant");
  assert(!ik->is_scratch_class(), "invariant");
  assert(methods != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);
  tag_sticky(methods);
  tag_sticky_enqueue(ik);
}

void JfrTraceTagging::clear_sticky(const InstanceKlass* ik, bool dynamic_tag /* true */) {
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
}

void JfrTraceTagging::tag_sticky_for_retransform_klass(const InstanceKlass* existing_klass, const InstanceKlass* scratch_klass, const GrowableArray<JfrTracedMethod>* methods, bool timing) {
  assert(existing_klass != nullptr, "invariant");
  assert(scratch_klass != nullptr, "invariant");
  // The scratch class has not yet received its official status.
  // assert(scratch_klass->is_scratch_class(), "invariant");
  if (timing) {
    // Can be done outside lock because it is a scratch klass.
    // Visibility guaranteed by upcoming safepoint.
    JfrTraceId::set_timing_bit(scratch_klass);
  }
  MutexLocker lock(ClassLoaderDataGraph_lock);
  if (JfrTraceId::has_sticky_bit(existing_klass)) {
    clear_sticky(existing_klass);
  }
  tag_sticky(methods);
  tag_sticky(existing_klass);
}

void JfrTraceTagging::on_klass_redefinition(const InstanceKlass* ik, const InstanceKlass* scratch_klass) {
  assert(ik != nullptr, "invariant");
  assert(ik->has_been_redefined(), "invariant");
  assert(scratch_klass != nullptr, "invariant");
  assert(scratch_klass->is_scratch_class(), "invariant");
  assert(SafepointSynchronize::is_at_safepoint(), "invariant");

  const bool klass_has_sticky_bit = JfrTraceId::has_sticky_bit(ik);
  if (klass_has_sticky_bit) {
    JfrTraceIdLoadBarrier::enqueue(ik);
  }

  const Array<Method*>* new_methods = ik->methods();
  assert(new_methods != nullptr, "invariant");

  const int len = new_methods->length(); // Can be shorter, equal to, or longer than old methods length.

  for (int i = 0; i < len; ++i) {
    const Method* const nm = new_methods->at(i);
    assert(nm != nullptr, "invariant");
    const Method* const om = scratch_klass->method_with_orig_idnum(nm->orig_method_idnum());
    if (om == nullptr) {
      assert(AllowRedefinitionToAddDeleteMethods, "invariant");
      // nm is a newly added Method.
      continue;
    }
    assert(nm != om, "invariant");
    assert(om->is_old(), "invariant");
    assert(nm->orig_method_idnum() == om->orig_method_idnum(), "invariant");
    assert(nm->name() == om->name() && nm->signature() == om->signature(), "invariant");

    if (nm->trace_flags() == om->trace_flags()) {
      continue;
    }

    const bool is_blessed = IS_METHOD_BLESSED(nm);

    // Copy the old method trace flags under a safepoint.
    nm->copy_trace_flags(om);

    assert(nm->trace_flags() == om->trace_flags(), "invariant");

    if (is_blessed) {
      BLESS_METHOD(nm);
      assert(IS_METHOD_BLESSED(nm), "invariant");
    }
  }

  // A retransformed/redefined klass carrying the sticky bit
  // needs additional processing by the JfrMethodTracer subsystem.
  if (klass_has_sticky_bit) {
    assert(JfrMethodTracer::in_use(), "invariant");
    JfrMethodTracer::on_klass_redefinition(ik, JfrTraceId::has_timing_bit(scratch_klass));
  }
}
