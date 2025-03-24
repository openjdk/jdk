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
#include "jfr/support/methodtracer/jfrClassFilterClosure.hpp"
#include "jfr/support/methodtracer/jfrFilter.hpp"
#include "oops/instanceKlass.inline.hpp"
#include "utilities/growableArray.hpp"

// Previous_filter can be set to null if there is no instrumentation to remove.
JfrFilterClassClosure::JfrFilterClassClosure(const JfrFilter* previous_filter, const JfrFilter* new_filter, JavaThread* thread) :
  _previous_filter(previous_filter),
  _new_filter(new_filter),
  _thread(thread),
  _classes_to_modify(new (mtTracing) GrowableArray<jclass>(32, mtTracing)) {
}

JfrFilterClassClosure::~JfrFilterClassClosure() {
  // Contents of _classes_to_modify are local JNI handles
  // which will be reclaimed by other means.
  delete _classes_to_modify;
}

void JfrFilterClassClosure::do_klass(Klass* k) {
  assert(k != nullptr, "invariant");
  if (k->is_instance_klass()) {
    const InstanceKlass* const ik = InstanceKlass::cast(k);
    if (match(ik)) {
      assert(ik->is_loader_alive(), "invariant");
      _classes_to_modify->append(((jclass)JfrJavaSupport::local_jni_handle(ik->java_mirror(), _thread)));
    }
  }
}

GrowableArray<jclass>* JfrFilterClassClosure::classes_to_modify() const {
  assert(_classes_to_modify != nullptr, "invariant");
  return _classes_to_modify;
}

bool JfrFilterClassClosure::match(const InstanceKlass* ik) const {
  if (_previous_filter != nullptr) {
    if (_previous_filter->can_instrument_class(ik) && _previous_filter->match(ik)) {
      return true;
    }
  }
  return _new_filter->can_instrument_class(ik) && _new_filter->match(ik);
}

void JfrFilterClassClosure::iterate_all_classes() {
  ClassLoaderDataGraph::loaded_classes_do_keepalive(this);
}
