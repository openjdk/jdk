/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
#include "logging/log.hpp"
#include "jfr/jfrEvents.hpp"
#include "jfr/metadata/jfrSerializer.hpp"
#include "jfr/periodic/jfrNetworkUtilization.hpp"
#include "jfr/periodic/jfrOSInterface.hpp"
#include "jfr/utilities/jfrTime.hpp"
#include "jfr/utilities/jfrTypes.hpp"
#include "runtime/os_perf.hpp"
#include "utilities/globalDefinitions.hpp"
#include "utilities/growableArray.hpp"

struct InterfaceEntry {
  char* name;
  traceid id;
  uint64_t bytes_in;
  uint64_t bytes_out;
  bool in_use;
};

static GrowableArray<InterfaceEntry>* _interfaces = NULL;

void JfrNetworkUtilization::destroy() {
  if (_interfaces != NULL) {
    for (int i = 0; i < _interfaces->length(); ++i) {
      FREE_C_HEAP_ARRAY(char, _interfaces->at(i).name);
    }
    delete _interfaces;
    _interfaces = NULL;
  }
}

static InterfaceEntry& new_entry(const NetworkInterface* iface, GrowableArray<InterfaceEntry>* interfaces) {
  assert(iface != NULL, "invariant");
  assert(interfaces != NULL, "invariant");

  // single threaded premise
  static traceid interface_id = 0;

  const char* name = iface->get_name();
  assert(name != NULL, "invariant");

  InterfaceEntry entry;
  const size_t length = strlen(name);
  entry.name = NEW_C_HEAP_ARRAY(char, length + 1, mtInternal);
  strncpy(entry.name, name, length + 1);
  entry.id = ++interface_id;
  entry.bytes_in = iface->get_bytes_in();
  entry.bytes_out = iface->get_bytes_out();
  entry.in_use = false;
  return _interfaces->at(_interfaces->append(entry));
}

static GrowableArray<InterfaceEntry>* get_interfaces() {
  if (_interfaces == NULL) {
    _interfaces = new(ResourceObj::C_HEAP, mtTracing) GrowableArray<InterfaceEntry>(10, true, mtTracing);
  }
  return _interfaces;
}

static InterfaceEntry& get_entry(const NetworkInterface* iface) {
  // Remember the index we started at last time, since we're most likely looking at them
  // in the same order every time.
  static int saved_index = -1;

  GrowableArray<InterfaceEntry>* interfaces = get_interfaces();
  assert(interfaces != NULL, "invariant");
  for (int i = 0; i < _interfaces->length(); ++i) {
    saved_index = (saved_index + 1) % _interfaces->length();
    if (strcmp(_interfaces->at(saved_index).name, iface->get_name()) == 0) {
      return _interfaces->at(saved_index);
    }
  }
  return new_entry(iface, interfaces);
}

// If current counters are less than previous we assume the interface has been reset
// If no bytes have been either sent or received, we'll also skip the event
static uint64_t rate_per_second(uint64_t current, uint64_t old, const JfrTickspan& interval) {
  assert(interval.value() > 0, "invariant");
  if (current <= old) {
    return 0;
  }
  return ((current - old) * NANOSECS_PER_SEC) / interval.nanoseconds();
}

static bool get_interfaces(NetworkInterface** network_interfaces) {
  const int ret_val = JfrOSInterface::network_utilization(network_interfaces);
  if (ret_val == OS_ERR) {
    log_debug(jfr, system)("Unable to generate network utilization events");
    return false;
  }
  return ret_val != FUNCTIONALITY_NOT_IMPLEMENTED;
}

class JfrNetworkInterfaceName : public JfrSerializer {
 public:
  void serialize(JfrCheckpointWriter& writer) {
    assert(_interfaces != NULL, "invariant");
    const JfrCheckpointContext ctx = writer.context();
    const intptr_t count_offset = writer.reserve(sizeof(u4)); // Don't know how many yet
    int active_interfaces = 0;
    for (int i = 0; i < _interfaces->length(); ++i) {
      InterfaceEntry& entry = _interfaces->at(i);
      if (entry.in_use) {
        entry.in_use = false;
        writer.write_key(entry.id);
        writer.write(entry.name);
        ++active_interfaces;
      }
    }
    if (active_interfaces == 0) {
      // nothing to write, restore context
      writer.set_context(ctx);
      return;
    }
    writer.write_count(active_interfaces, count_offset);
  }
};

static bool register_network_interface_name_serializer() {
  assert(_interfaces != NULL, "invariant");
  return JfrSerializer::register_serializer(TYPE_NETWORKINTERFACENAME,
                                            false, // require safepoint
                                            false, // disallow caching; we want a callback every rotation
                                            new JfrNetworkInterfaceName());
}

void JfrNetworkUtilization::send_events() {
  ResourceMark rm;
  NetworkInterface* network_interfaces;
  if (!get_interfaces(&network_interfaces)) {
    return;
  }
  log_trace(jfr, event)("Reporting network utilization");
  static JfrTicks last_sample_instant;
  const JfrTicks cur_time = JfrTicks::now();
  const JfrTickspan interval = last_sample_instant == 0 ? cur_time - cur_time : cur_time - last_sample_instant;
  last_sample_instant = cur_time;
  for (NetworkInterface *cur = network_interfaces; cur != NULL; cur = cur->next()) {
    InterfaceEntry& entry = get_entry(cur);
    if (interval.value() > 0) {
      const uint64_t current_bytes_in = cur->get_bytes_in();
      const uint64_t current_bytes_out = cur->get_bytes_out();
      const uint64_t read_rate = rate_per_second(current_bytes_in, entry.bytes_in, interval);
      const uint64_t write_rate = rate_per_second(current_bytes_out, entry.bytes_out, interval);
      if (read_rate > 0 || write_rate > 0) {
        entry.in_use = true;
        EventNetworkUtilization event(UNTIMED);
        event.set_starttime(cur_time);
        event.set_endtime(cur_time);
        event.set_networkInterface(entry.id);
        event.set_readRate(8 * read_rate);
        event.set_writeRate(8 * write_rate);
        event.commit();
      }
      // update existing entry with new values
      entry.bytes_in = current_bytes_in;
      entry.bytes_out = current_bytes_out;
    }
  }

  static bool is_serializer_registered = false;
  if (!is_serializer_registered) {
    is_serializer_registered = register_network_interface_name_serializer();
  }
}
