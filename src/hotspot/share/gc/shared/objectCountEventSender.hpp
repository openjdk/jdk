/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

#ifndef SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_HPP
#define SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_HPP

#include "memory/allStatic.hpp"
#include "memory/heapInspection.hpp"
#include "jfr/jfrEvents.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/macros.hpp"
#include "utilities/ticks.hpp"
#if INCLUDE_SERVICES

class KlassInfoEntry;
class Klass;

class ObjectCountEventSender : public AllStatic {
  static bool _should_send_requestable_event;

  template <typename Event>
  static void send_event_if_enabled(Klass* klass, jlong count, julong size, const Ticks& timestamp);

 public:
  static void enable_requestable_event();
  static void disable_requestable_event();

  template <class Event>
  static void send(const KlassInfoEntry* entry, const Ticks& timestamp);

  template <class Event>
  static bool should_send_event();
};

template <class Event>
bool ObjectCountEventSender::should_send_event() {
#if INCLUDE_JFR
  return _should_send_requestable_event || Event::is_enabled();
#else
  return false;
#endif // INCLUDE_JFR
}

template <typename T>
void ObjectCountEventSender::send_event_if_enabled(Klass* klass, jlong count, julong size, const Ticks& timestamp) {
  T event(UNTIMED);
  if (event.should_commit()) {
    event.set_starttime(timestamp);
    event.set_endtime(timestamp);
    event.set_gcId(GCId::current());
    event.set_objectClass(klass);
    event.set_count(count);
    event.set_totalSize(size);
    event.commit();
  }
}

template <class Event>
void ObjectCountEventSender::send(const KlassInfoEntry* entry, const Ticks& timestamp) {
  Klass* klass = entry->klass();
  jlong count = entry->count();
  julong total_size = entry->words() * BytesPerWord;

  send_event_if_enabled<Event>(klass, count, total_size, timestamp);
  // If sending ObjectCountAfterGCEvent, check if ObjectCount is enabled and send event data to ObjectCount
  // If sending ObjectCountEvent, do not send send ObjectCountAfterGCEvent
  if (std::is_same<Event, EventObjectCountAfterGC>::value && ObjectCountEventSender::should_send_event<EventObjectCount>()) {
    send_event_if_enabled<EventObjectCount>(klass, count, total_size, timestamp);
  }
}


#endif // INCLUDE_SERVICES

#endif // SHARE_GC_SHARED_OBJECTCOUNTEVENTSENDER_HPP
