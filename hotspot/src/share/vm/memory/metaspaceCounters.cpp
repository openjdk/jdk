/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "memory/metaspaceCounters.hpp"
#include "memory/resourceArea.hpp"
#include "utilities/exceptions.hpp"

MetaspaceCounters* MetaspaceCounters::_metaspace_counters = NULL;

MetaspaceCounters::MetaspaceCounters() :
    _capacity(NULL),
    _used(NULL),
    _max_capacity(NULL) {
  if (UsePerfData) {
    size_t min_capacity = MetaspaceAux::min_chunk_size();
    size_t max_capacity = MetaspaceAux::reserved_in_bytes();
    size_t curr_capacity = MetaspaceAux::capacity_in_bytes();
    size_t used = MetaspaceAux::used_in_bytes();

    initialize(min_capacity, max_capacity, curr_capacity, used);
  }
}

static PerfVariable* create_ms_variable(const char *ns,
                                        const char *name,
                                        size_t value,
                                        TRAPS) {
  const char *path = PerfDataManager::counter_name(ns, name);
  PerfVariable *result =
      PerfDataManager::create_variable(SUN_GC, path, PerfData::U_Bytes, value,
                                       CHECK_NULL);
  return result;
}

static void create_ms_constant(const char *ns,
                               const char *name,
                               size_t value,
                               TRAPS) {
  const char *path = PerfDataManager::counter_name(ns, name);
  PerfDataManager::create_constant(SUN_GC, path, PerfData::U_Bytes, value, CHECK);
}

void MetaspaceCounters::initialize(size_t min_capacity,
                                   size_t max_capacity,
                                   size_t curr_capacity,
                                   size_t used) {

  if (UsePerfData) {
    EXCEPTION_MARK;
    ResourceMark rm;

    const char *ms = "metaspace";

    create_ms_constant(ms, "minCapacity", min_capacity, CHECK);
    _max_capacity = create_ms_variable(ms, "maxCapacity", max_capacity, CHECK);
    _capacity = create_ms_variable(ms, "capacity", curr_capacity, CHECK);
    _used = create_ms_variable(ms, "used", used, CHECK);
  }
}

void MetaspaceCounters::update_capacity() {
  assert(UsePerfData, "Should not be called unless being used");
  assert(_capacity != NULL, "Should be initialized");
  size_t capacity_in_bytes = MetaspaceAux::capacity_in_bytes();
  _capacity->set_value(capacity_in_bytes);
}

void MetaspaceCounters::update_used() {
  assert(UsePerfData, "Should not be called unless being used");
  assert(_used != NULL, "Should be initialized");
  size_t used_in_bytes = MetaspaceAux::used_in_bytes();
  _used->set_value(used_in_bytes);
}

void MetaspaceCounters::update_max_capacity() {
  assert(UsePerfData, "Should not be called unless being used");
  assert(_max_capacity != NULL, "Should be initialized");
  size_t reserved_in_bytes = MetaspaceAux::reserved_in_bytes();
  _max_capacity->set_value(reserved_in_bytes);
}

void MetaspaceCounters::update_all() {
  if (UsePerfData) {
    update_used();
    update_capacity();
    update_max_capacity();
  }
}

void MetaspaceCounters::initialize_performance_counters() {
  if (UsePerfData) {
    assert(_metaspace_counters == NULL, "Should only be initialized once");
    _metaspace_counters = new MetaspaceCounters();
  }
}

void MetaspaceCounters::update_performance_counters() {
  if (UsePerfData) {
    assert(_metaspace_counters != NULL, "Should be initialized");
    _metaspace_counters->update_all();
  }
}

