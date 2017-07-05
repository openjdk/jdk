/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
#include "gc/shared/gcId.hpp"
#include "gc/shared/objectCountEventSender.hpp"
#include "memory/heapInspection.hpp"
#include "trace/tracing.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"
#if INCLUDE_SERVICES

void ObjectCountEventSender::send(const KlassInfoEntry* entry, const Ticks& timestamp) {
#if INCLUDE_TRACE
  assert(Tracing::is_event_enabled(EventObjectCountAfterGC::eventId),
         "Only call this method if the event is enabled");

  EventObjectCountAfterGC event(UNTIMED);
  event.set_gcId(GCId::current());
  event.set_class(entry->klass());
  event.set_count(entry->count());
  event.set_totalSize(entry->words() * BytesPerWord);
  event.set_endtime(timestamp);
  event.commit();
#endif // INCLUDE_TRACE
}

bool ObjectCountEventSender::should_send_event() {
#if INCLUDE_TRACE
  return Tracing::is_event_enabled(EventObjectCountAfterGC::eventId);
#else
  return false;
#endif // INCLUDE_TRACE
}

#endif // INCLUDE_SERVICES
