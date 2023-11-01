/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
#include "jfr/jfrEvents.hpp"
#include "jfr/periodic/jfrNativeMemoryEvent.hpp"
#include "nmt/memTracker.hpp"
#include "nmt/nmtUsage.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

static NMTUsage* get_usage(const Ticks& timestamp) {
  static Ticks last_timestamp;
  static NMTUsage* usage = nullptr;

  if (usage == nullptr) {
    // First time, create a new NMTUsage.
    usage = new NMTUsage(NMTUsage::OptionsNoTS);
    usage->refresh();
    last_timestamp = timestamp;
  }

  if (timestamp != last_timestamp) {
    // Refresh usage if new timestamp.
    usage->refresh();
    last_timestamp = timestamp;
  }
  return usage;
}

void JfrNativeMemoryEvent::send_total_event(const Ticks& timestamp) {
  if (!MemTracker::enabled()) {
    return;
  }

  NMTUsage* usage = get_usage(timestamp);

  EventNativeMemoryUsageTotal event(UNTIMED);
  event.set_starttime(timestamp);
  event.set_reserved(usage->total_reserved());
  event.set_committed(usage->total_committed());
  event.commit();
}

void JfrNativeMemoryEvent::send_type_event(const Ticks& starttime, MEMFLAGS flag, size_t reserved, size_t committed) {
  EventNativeMemoryUsage event(UNTIMED);
  event.set_starttime(starttime);
  event.set_type(NMTUtil::flag_to_index(flag));
  event.set_reserved(reserved);
  event.set_committed(committed);
  event.commit();
}

void JfrNativeMemoryEvent::send_type_events(const Ticks& timestamp) {
  if (!MemTracker::enabled()) {
    return;
  }

  NMTUsage* usage = get_usage(timestamp);

  for (int index = 0; index < mt_number_of_types; index ++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(index);
    if (flag == mtNone) {
      // Skip mtNone since it is not really used.
      continue;
    }
    send_type_event(timestamp, flag, usage->reserved(flag), usage->committed(flag));
  }
}
