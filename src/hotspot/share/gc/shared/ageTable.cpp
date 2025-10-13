/*
 * Copyright (c) 1997, 2025, Oracle and/or its affiliates. All rights reserved.
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

#include "gc/shared/ageTable.inline.hpp"
#include "gc/shared/ageTableTracer.hpp"
#include "gc/shared/collectedHeap.hpp"
#include "gc/shared/gc_globals.hpp"
#include "jvm.h"
#include "logging/log.hpp"
#include "logging/logStream.hpp"
#include "memory/resourceArea.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/perfData.hpp"
#include "utilities/copy.hpp"

/* Copyright (c) 1992, 2025, Oracle and/or its affiliates, and Stanford University.
   See the LICENSE file for license information. */

AgeTable::AgeTable(bool global) : _use_perf_data(UsePerfData && global) {

  clear();

  if (_use_perf_data) {

    ResourceMark rm;
    EXCEPTION_MARK;

    const char* agetable_ns = "generation.0.agetable";
    const char* bytes_ns = PerfDataManager::name_space(agetable_ns, "bytes");

    for(int age = 0; age < table_size; age ++) {
      char age_name[10];
      jio_snprintf(age_name, sizeof(age_name), "%2.2d", age);
      const char* cname = PerfDataManager::counter_name(bytes_ns, age_name);
      _perf_sizes[age] = PerfDataManager::create_variable(SUN_GC, cname,
                                                          PerfData::U_Bytes,
                                                          CHECK);
    }

    const char* cname = PerfDataManager::counter_name(agetable_ns, "size");
    PerfDataManager::create_constant(SUN_GC, cname, PerfData::U_None,
                                     table_size, CHECK);
  }
}

void AgeTable::clear() {
  for (size_t* p = sizes; p < sizes + table_size; ++p) {
    *p = 0;
  }
}

#ifndef PRODUCT
bool AgeTable::is_clear() const {
  for (const size_t* p = sizes; p < sizes + table_size; ++p) {
    if (*p != 0) return false;
  }
  return true;
}
#endif // !PRODUCT

void AgeTable::merge(const AgeTable* subTable) {
  for (int i = 0; i < table_size; i++) {
    sizes[i]+= subTable->sizes[i];
  }
}

uint AgeTable::compute_tenuring_threshold(size_t desired_survivor_size) {
  uint result;

  if (AlwaysTenure || NeverTenure) {
    assert(MaxTenuringThreshold == 0 || MaxTenuringThreshold == markWord::max_age + 1,
           "MaxTenuringThreshold should be 0 or markWord::max_age + 1, but is %u", MaxTenuringThreshold);
    result = MaxTenuringThreshold;
  } else {
    size_t total = 0;
    uint age = 1;
    assert(sizes[0] == 0, "no objects with age zero should be recorded");
    while (age < table_size) {
      total += sizes[age];
      // check if including objects of age 'age' made us pass the desired
      // size, if so 'age' is the new threshold
      if (total > desired_survivor_size) break;
      age++;
    }
    result = age < MaxTenuringThreshold ? age : MaxTenuringThreshold;
  }


  log_debug(gc, age)("Desired survivor size %zu bytes, new threshold %zu (max threshold %u)",
                     desired_survivor_size * oopSize, (uintx) result, MaxTenuringThreshold);

  return result;
}

void AgeTable::print_age_table() {
  LogTarget(Trace, gc, age) lt;
  if (lt.is_enabled() || _use_perf_data || AgeTableTracer::is_tenuring_distribution_event_enabled()) {
    LogStream st(lt);
    print_on(&st);
  }
}

void AgeTable::print_on(outputStream* st) {
  st->print_cr("Age table:");

  size_t total = 0;
  uint age = 1;
  while (age < table_size) {
    size_t word_size = sizes[age];
    total += word_size;
    if (word_size > 0) {
      st->print_cr("- age %3u: %10zu bytes, %10zu total",
                   age, word_size * oopSize, total * oopSize);
    }
    AgeTableTracer::send_tenuring_distribution_event(age, word_size * oopSize);
    if (_use_perf_data) {
      _perf_sizes[age]->set_value(word_size * oopSize);
    }
    age++;
  }
}
