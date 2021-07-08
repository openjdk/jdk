/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "classfile/classLoaderDataGraph.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrFinalizerEvent.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "oops/instanceKlass.hpp"
#include "runtime/mutexLocker.hpp"

// All finalizer events generated during the same pass will have the same timestamp.
static JfrTicks invocation_time;

static void finalizer_event_callback(Klass* klass) {
  assert(klass != NULL, "invariant");
  if (!klass->is_instance_klass()) {
    return;
  }
  InstanceKlass* const ik = InstanceKlass::cast(klass);
  if (ik->has_finalizer()) {
    EventFinalizer event(UNTIMED);
    event.set_endtime(invocation_time);
    event.set_overridingClass(ik);
    event.commit();
  }
}

void JfrFinalizerEvent::generate_events() {
  invocation_time = JfrTicks::now();
  MutexLocker cld_lock(ClassLoaderDataGraph_lock);
  ClassLoaderDataGraph::classes_do(&finalizer_event_callback);
}
