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

#include "classfile/classLoaderDataGraph.hpp"
#include "jfr/jni/jfrJavaSupport.hpp"
#include "jfr/recorder/checkpoint/types/traceid/jfrTraceId.inline.hpp"
#include "jfr/support/jfrKlassUnloading.hpp"
#include "jfr/support/methodtracer/jfrClassFilterClosure.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "jfr/support/methodtracer/jfrFilterManager.hpp"
#include "jfr/support/methodtracer/jfrInstrumentedClass.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "runtime/mutexLocker.hpp"
#include "utilities/growableArray.hpp"
#include "utilities/resizeableResourceHash.hpp"

constexpr static unsigned int TABLE_SIZE = 1009;
constexpr static unsigned int MAX_TABLE_SIZE = 0x3fffffff;

JfrFilterClassClosure::JfrFilterClassClosure(JavaThread* thread) :
  _new_filter(JfrFilterManager::current()),
  _classes_to_modify(new ClosureSet(TABLE_SIZE, MAX_TABLE_SIZE)),
  _thread(thread) {
  assert(_new_filter != nullptr, "invariant");
}

static inline jclass mirror_as_local_jni_handle(const InstanceKlass* ik, JavaThread* thread) {
  assert(ik != nullptr, "invariant");
  assert(thread != nullptr, "invariant");
  return reinterpret_cast<jclass>(JfrJavaSupport::local_jni_handle(ik->java_mirror(), thread));
}

inline bool JfrFilterClassClosure::match(const InstanceKlass* ik) const {
  assert(_new_filter != nullptr, "invariant");
  return _new_filter->can_instrument_class(ik) && _new_filter->match(ik);
}

void JfrFilterClassClosure::do_klass(Klass* k) {
  assert(k != nullptr, "invariant");
  if (k->is_instance_klass()) {
    const InstanceKlass* const ik = InstanceKlass::cast(k);
    if (match(ik)) {
      assert(ik->is_loader_alive(), "invariant");
      const traceid klass_id = JfrTraceId::load_raw(ik);
      if (!_classes_to_modify->contains(klass_id)) {
        jclass mirror = mirror_as_local_jni_handle(ik, _thread);
        _classes_to_modify->put(klass_id, mirror);
      }
    }
  }
}

ClosureSet* JfrFilterClassClosure::to_modify() const {
  assert(_classes_to_modify != nullptr, "invariant");
  return _classes_to_modify;
}

int JfrFilterClassClosure::number_of_classes() const {
  assert(_classes_to_modify != nullptr, "invariant");
  return _classes_to_modify->number_of_entries();
}

void JfrFilterClassClosure::iterate_all_classes(GrowableArray<JfrInstrumentedClass>* instrumented_klasses) {
  assert(instrumented_klasses != nullptr, "invariant");
  assert_locked_or_safepoint(ClassLoaderDataGraph_lock);

  // First we process the instrumented_klasses list. The fact that a klass is on that list implies
  // it matched _some_ previous filter, but we don't know which one. The nice thing is we don't need to know,
  // because a klass has the STICKY_BIT set for those methods that matched _some_ previous filter.
  // We, therefore, put these klasses directly into the classes_to_modify set. We also need to do this
  // because some klasses on the instrumented_klasses list may not have reached the point of add_to_hierarchy yet.
  // For those klasses, the ClassLoaderDataGraph iterator would not deliver them on iteration.

  if (instrumented_klasses->is_nonempty()) {
    for (int i = 0; i < instrumented_klasses->length(); ++i) {
      if (JfrKlassUnloading::is_unloaded(instrumented_klasses->at(i).trace_id())) {
        continue;
      }
      const InstanceKlass* const ik = instrumented_klasses->at(i).instance_klass();
      assert(ik != nullptr, "invariant");
      assert(ik->is_loader_alive(), "invariant");
      assert(JfrTraceId::has_sticky_bit(ik), "invariant");
      const traceid klass_id = JfrTraceId::load_raw(ik);
      assert(!_classes_to_modify->contains(klass_id), "invariant");
      jclass mirror = mirror_as_local_jni_handle(ik, _thread);
      _classes_to_modify->put(klass_id, mirror);
    }
  }
  ClassLoaderDataGraph::loaded_classes_do_keepalive(this);
}
