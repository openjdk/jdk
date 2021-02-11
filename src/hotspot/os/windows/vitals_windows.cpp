/*
 * Copyright (c) 2019, 2021 SAP SE. All rights reserved.
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 *
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

#include "runtime/os.hpp"
#include "utilities/debug.hpp"
#include "utilities/globalDefinitions.hpp"
#include "vitals/vitals_internals.hpp"

#include <psapi.h>

namespace sapmachine_vitals {

static Column* g_col_system_memoryload = NULL;
static Column* g_col_system_avail_phys = NULL;
static Column* g_col_process_working_set_size = NULL;
static Column* g_col_process_commit_charge = NULL;

bool platform_columns_initialize() {
  g_col_system_memoryload = new PlainValueColumn("system", NULL, "mload",
      "Approximate percentage of physical memory that is in use.");

  // MEMORYSTATUSEX ullAvailPhys
  g_col_system_avail_phys = new MemorySizeColumn("system", NULL, "avail-phys",
      "Amount of physical memory currently available.");

  // PROCESS_MEMORY_COUNTERS_EX WorkingSetSize
  g_col_process_working_set_size = new MemorySizeColumn("process", NULL, "wset",
      "Working set size");

  // PROCESS_MEMORY_COUNTERS_EX PrivateUsage
  g_col_process_commit_charge = new MemorySizeColumn("process", NULL, "comch",
      "Commit charge");


  return true;
}

static void set_value_in_sample(Column* col, Sample* sample, value_t val) {
  if (col != NULL) {
    int index = col->index();
    sample->set_value(index, val);
  }
}

void sample_platform_values(Sample* sample) {

  MEMORYSTATUSEX mse;
  mse.dwLength = sizeof(mse);
  if (::GlobalMemoryStatusEx(&mse)) {
    set_value_in_sample(g_col_system_memoryload, sample, mse.dwMemoryLoad);
    set_value_in_sample(g_col_system_avail_phys, sample, mse.ullAvailPhys);
  }

  PROCESS_MEMORY_COUNTERS cnt;
  cnt.cb = sizeof(cnt);
  if (::GetProcessMemoryInfo(::GetCurrentProcess(), &cnt, sizeof(cnt))) {
    set_value_in_sample(g_col_process_working_set_size, sample, cnt.WorkingSetSize);
    set_value_in_sample(g_col_process_commit_charge, sample, cnt.PagefileUsage);
  }

}

} // namespace sapmachine_vitals
