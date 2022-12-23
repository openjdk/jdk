/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
#include "services/memJfrReporter.hpp"
#include "services/memTracker.hpp"
#include "services/nmtUsage.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/ticks.hpp"

// Helper class to avoid refreshing the NMTUsage to often and allow
// the two JFR events to use the same data.
class MemJFRCurrentUsage : public AllStatic {
private:
  // The age threshold in milliseconds. If older than this refresh the usage.
  static const uint64_t AgeThreshold = 50;

  static Ticks _timestamp;
  static NMTUsage* _usage;

public:
  static NMTUsage* get_usage();
  static Ticks get_timestamp();
};

Ticks MemJFRCurrentUsage::_timestamp;
NMTUsage* MemJFRCurrentUsage::_usage = nullptr;

NMTUsage* MemJFRCurrentUsage::get_usage() {
  Tickspan since_baselined = Ticks::now() - _timestamp;

  if (_usage == nullptr) {
    // First time, create a new NMTUsage.
    _usage = new NMTUsage(NMTUsage::OptionsNoTS);
  } else if (since_baselined.milliseconds() < AgeThreshold) {
    // There is recent enough usage information, return it.
    return _usage;
  }

  // Refresh the usage information.
  _usage->refresh();
  _timestamp.stamp();

  return _usage;
}

Ticks MemJFRCurrentUsage::get_timestamp() {
  return _timestamp;
}

void MemJFRReporter::send_total_event() {
  if (!MemTracker::enabled()) {
    return;
  }

  NMTUsage* usage = MemJFRCurrentUsage::get_usage();
  Ticks timestamp = MemJFRCurrentUsage::get_timestamp();

  EventNativeMemoryUsageTotal event(UNTIMED);
  event.set_starttime(timestamp);
  event.set_reserved(usage->total_reserved());
  event.set_committed(usage->total_committed());
  event.commit();
}

void MemJFRReporter::send_type_event(const Ticks& starttime, const char* type, size_t reserved, size_t committed) {
  EventNativeMemoryUsage event(UNTIMED);
  event.set_starttime(starttime);
  event.set_type(type);
  event.set_reserved(reserved);
  event.set_committed(committed);
  event.commit();
}

void MemJFRReporter::send_type_events() {
  if (!MemTracker::enabled()) {
    return;
  }

  NMTUsage* usage = MemJFRCurrentUsage::get_usage();
  Ticks timestamp = MemJFRCurrentUsage::get_timestamp();

  for (int index = 0; index < mt_number_of_types; index ++) {
    MEMFLAGS flag = NMTUtil::index_to_flag(index);
    if (flag == mtNone) {
      // Skip mtNone since it is not really used.
      continue;
    }
    send_type_event(timestamp, NMTUtil::flag_to_name(flag), usage->reserved(flag), usage->committed(flag));
  }
}
