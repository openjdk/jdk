/*
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

#define METASPACE_NAME "perm"

MetaspaceCounters* MetaspaceCounters::_metaspace_counters = NULL;

MetaspaceCounters::MetaspaceCounters() {
  if (UsePerfData) {
    size_t min_capacity = MetaspaceAux::min_chunk_size();
    size_t max_capacity = MetaspaceAux::reserved_in_bytes();
    size_t curr_capacity = MetaspaceAux::capacity_in_bytes();
    size_t used = MetaspaceAux::used_in_bytes();

    initialize(min_capacity, max_capacity, curr_capacity, used);
  }
}

void MetaspaceCounters::initialize(size_t min_capacity,
                                   size_t max_capacity,
                                   size_t curr_capacity,
                                   size_t used) {

  if (UsePerfData) {
    EXCEPTION_MARK;
    ResourceMark rm;

    // Create a name that will be recognized by jstat tools as
    // the perm gen.  Change this to a Metaspace name when the
    // tools are fixed.
    // name to recognize "sun.gc.generation.2.*"

    const char* name = METASPACE_NAME;
    const int ordinal = 2;
    const int spaces = 1;

    const char* cns = PerfDataManager::name_space("generation", ordinal);

    _name_space = NEW_C_HEAP_ARRAY(char, strlen(cns)+1, mtClass);
    strcpy(_name_space, cns);

    const char* cname = PerfDataManager::counter_name(_name_space, "name");
    PerfDataManager::create_string_constant(SUN_GC, cname, name, CHECK);

    // End of perm gen like name creation

    cname = PerfDataManager::counter_name(_name_space, "spaces");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None,
                                     spaces, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "minCapacity");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_Bytes,
                                     min_capacity, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "maxCapacity");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_Bytes,
                                     max_capacity, CHECK);

    cname = PerfDataManager::counter_name(_name_space, "capacity");
    _current_size =
      PerfDataManager::create_variable(SUN_GC, cname, PerfData::U_Bytes,
                                       curr_capacity, CHECK);

    // SpaceCounter like counters
    // name to recognize "sun.gc.generation.2.space.0.*"
    {
      const int space_ordinal = 0;
      const char* cns = PerfDataManager::name_space(_name_space, "space",
                                                    space_ordinal);

      char* space_name_space = NEW_C_HEAP_ARRAY(char, strlen(cns)+1, mtClass);
      strcpy(space_name_space, cns);

      const char* cname = PerfDataManager::counter_name(space_name_space, "name");
      PerfDataManager::create_string_constant(SUN_GC, cname, name, CHECK);

      cname = PerfDataManager::counter_name(space_name_space, "maxCapacity");
      _max_capacity = PerfDataManager::create_variable(SUN_GC, cname,
                                                       PerfData::U_Bytes,
                                                       (jlong)max_capacity, CHECK);

      cname = PerfDataManager::counter_name(space_name_space, "capacity");
      _capacity = PerfDataManager::create_variable(SUN_GC, cname,
                                                   PerfData::U_Bytes,
                                                   curr_capacity, CHECK);

      cname = PerfDataManager::counter_name(space_name_space, "used");
      _used = PerfDataManager::create_variable(SUN_GC,
                                               cname,
                                               PerfData::U_Bytes,
                                               used,
                                               CHECK);

    cname = PerfDataManager::counter_name(space_name_space, "initCapacity");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_Bytes,
                                     min_capacity, CHECK);
    }
  }
}

void MetaspaceCounters::update_capacity() {
  assert(UsePerfData, "Should not be called unless being used");
  size_t capacity_in_bytes = MetaspaceAux::capacity_in_bytes();
  _capacity->set_value(capacity_in_bytes);
}

void MetaspaceCounters::update_used() {
  assert(UsePerfData, "Should not be called unless being used");
  size_t used_in_bytes = MetaspaceAux::used_in_bytes();
  _used->set_value(used_in_bytes);
}

void MetaspaceCounters::update_max_capacity() {
  assert(UsePerfData, "Should not be called unless being used");
  size_t reserved_in_bytes = MetaspaceAux::reserved_in_bytes();
  _max_capacity->set_value(reserved_in_bytes);
}

void MetaspaceCounters::update_all() {
  if (UsePerfData) {
    update_used();
    update_capacity();
    update_max_capacity();
    _current_size->set_value(MetaspaceAux::reserved_in_bytes());
  }
}

void MetaspaceCounters::initialize_performance_counters() {
  if (UsePerfData) {
    _metaspace_counters = new MetaspaceCounters();
  }
}

void MetaspaceCounters::update_performance_counters() {
  if (UsePerfData) {
    _metaspace_counters->update_all();
  }
}

